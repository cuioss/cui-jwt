/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.validation.jwks.http;

import de.cuioss.http.client.LoaderStatus;
import de.cuioss.http.client.LoadingStatusProvider;
import de.cuioss.http.client.ResilientHttpHandler;
import de.cuioss.http.client.result.HttpResultObject;
import de.cuioss.jwt.validation.json.Jwks;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.JwksType;
import de.cuioss.jwt.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.well_known.HttpWellKnownResolver;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.cuioss.jwt.validation.JWTValidationLogMessages.ERROR;
import static de.cuioss.jwt.validation.JWTValidationLogMessages.INFO;
import static de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;

/**
 * JWKS loader that loads from HTTP endpoint with caching and background refresh support.
 * Supports both direct HTTP endpoints and well-known discovery.
 * Uses ResilientHttpHandler for stateful HTTP caching with optional scheduled background refresh.
 * Background refresh is automatically started after the first successful key load.
 * <p>
 * This implementation follows a clean architecture with:
 * <ul>
 *   <li>Simple constructor with no I/O operations</li>
 *   <li>Async initialization via CompletableFuture</li>
 *   <li>Lock-free status checks using AtomicReference</li>
 *   <li>Key rotation grace period support for Issue #110</li>
 *   <li>Proper separation of concerns</li>
 * </ul>
 * <p>
 * Implements Requirement CUI-JWT-4.5: Key Rotation Grace Period
 *
 * @author Oliver Wolff
 * @see HttpJwksLoaderConfig#keyRotationGracePeriod
 * @see <a href="https://github.com/cuioss/cui-jwt/issues/110">Issue #110: Key rotation grace period</a>
 * @since 1.0
 */
public class HttpJwksLoader implements JwksLoader, LoadingStatusProvider, AutoCloseable {

    private static final CuiLogger LOGGER = new CuiLogger(HttpJwksLoader.class);
    private static final String ISSUER_NOT_CONFIGURED = "not-configured";
    private static final String ISSUER_MUST_BE_RESOLVED = "Issuer identifier must be resolved at this point";

    private final HttpJwksLoaderConfig config;
    private final AtomicReference<LoaderStatus> status = new AtomicReference<>(LoaderStatus.UNDEFINED);
    private final AtomicReference<JWKSKeyLoader> currentKeys = new AtomicReference<>();
    private final ConcurrentLinkedDeque<RetiredKeySet> retiredKeys = new ConcurrentLinkedDeque<>();
    private final AtomicReference<ResilientHttpHandler<Jwks>> httpHandler = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    private SecurityEventCounter securityEventCounter;
    private final AtomicReference<String> resolvedIssuerIdentifier = new AtomicReference<>();
    private final AtomicReference<Jwks> currentJwksContent = new AtomicReference<>();

    /**
     * Constructor using HttpJwksLoaderConfig.
     * Simple constructor with no I/O operations - all loading happens asynchronously in initJWKSLoader.
     *
     * @param config the configuration for this loader
     */
    public HttpJwksLoader(@NonNull HttpJwksLoaderConfig config) {
        this.config = config;
    }

    @Override
    @SuppressWarnings("java:S3776") // Cognitive complexity - initialization logic requires these checks
    public CompletableFuture<LoaderStatus> initJWKSLoader(@NonNull SecurityEventCounter counter) {
        this.securityEventCounter = counter;

        // Execute initialization asynchronously
        return CompletableFuture.supplyAsync(() -> {
            status.set(LoaderStatus.LOADING);

            // Resolve the handler (may involve well-known discovery)
            Optional<ResilientHttpHandler<Jwks>> handlerOpt = resolveJWKSHandler();
            if (handlerOpt.isEmpty()) {
                status.set(LoaderStatus.ERROR);
                String errorDetail = config.getWellKnownConfig() != null
                        ? "Well-known discovery failed"
                        : "No HTTP handler configured";

                // Log appropriate message based on failure type
                if (config.getWellKnownConfig() != null) {
                    LOGGER.warn(WARN.JWKS_URI_RESOLUTION_FAILED.format());
                }
                LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED.format(errorDetail, getIssuerIdentifier().orElse(ISSUER_NOT_CONFIGURED)));
                return LoaderStatus.ERROR;
            }

            ResilientHttpHandler<Jwks> handler = handlerOpt.get();
            httpHandler.set(handler);

            // Load JWKS via ResilientHttpHandler
            HttpResultObject<Jwks> result = handler.load();

            // Start background refresh if configured (regardless of initial load status to enable retries)
            boolean backgroundRefreshEnabled = config.isBackgroundRefreshEnabled();
            if (backgroundRefreshEnabled) {
                startBackgroundRefresh();
            }

            if (result.isValid()) {
                updateKeys(result.getResult());

                // Log successful HTTP load
                LOGGER.info(INFO.JWKS_LOADED.format(getIssuerIdentifier().orElseThrow(() -> new IllegalStateException(ISSUER_MUST_BE_RESOLVED))));

                status.set(LoaderStatus.OK);
                return LoaderStatus.OK;
            }

            // Log appropriate warning if no cached content
            if (result.getResultDetail().isPresent()) {
                String detailMessage = result.getResultDetail().get().getDetail().toString();
                if (detailMessage.contains("no cached content")) {
                    LOGGER.warn(WARN.JWKS_LOAD_FAILED_NO_CACHE.format());
                }
            }

            LOGGER.error(ERROR.JWKS_LOAD_FAILED.format(result.getResultDetail(), getIssuerIdentifier().orElseThrow(() -> new IllegalStateException(ISSUER_MUST_BE_RESOLVED))));

            // If background refresh is enabled, keep status as UNDEFINED to allow retries
            // Otherwise set to ERROR for permanent failure
            if (backgroundRefreshEnabled) {
                status.set(LoaderStatus.UNDEFINED);
                return LoaderStatus.UNDEFINED;
            } else {
                status.set(LoaderStatus.ERROR);
                return LoaderStatus.ERROR;
            }
        });
    }

    /**
     * Resolves the JWKS handler based on configuration.
     * For well-known: performs discovery to get JWKS URL and validates issuer
     * For direct: uses the configured HTTP handler
     *
     * @return Optional containing the handler, or empty if resolution failed
     */
    @SuppressWarnings("java:S3776") // Cognitive complexity - issuer resolution requires these checks
    private Optional<ResilientHttpHandler<Jwks>> resolveJWKSHandler() {
        HttpHandler handler;

        if (config.getWellKnownConfig() != null) {
            // Well-known discovery - the resolver itself uses ResilientHttpHandler for retry!
            HttpWellKnownResolver resolver = config.getWellKnownConfig().createResolver();

            // This call may block but we're in async context
            Optional<String> jwksUri = resolver.getJwksUri();
            if (jwksUri.isEmpty()) {
                return Optional.empty();
            }

            // Resolve issuer from well-known document
            Optional<String> discoveredIssuer = resolver.getIssuer();
            String configuredIssuer = config.getIssuerIdentifier();

            if (discoveredIssuer.isPresent()) {
                if (configuredIssuer != null) {
                    // Configured issuer takes precedence, but validate against discovered
                    if (!configuredIssuer.equals(discoveredIssuer.get())) {
                        LOGGER.warn(WARN.ISSUER_MISMATCH.format(configuredIssuer, discoveredIssuer.get()));
                        securityEventCounter.increment(SecurityEventCounter.EventType.ISSUER_MISMATCH);
                    }
                    resolvedIssuerIdentifier.set(configuredIssuer);
                } else {
                    // No configured issuer - use discovered issuer from well-known
                    resolvedIssuerIdentifier.set(discoveredIssuer.get());
                }
            } else {
                // No issuer in well-known document
                if (configuredIssuer != null) {
                    // Use configured issuer if available
                    resolvedIssuerIdentifier.set(configuredIssuer);
                } else {
                    // No issuer available at all - fail
                    LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED.format("No issuer identifier found", "well-known"));
                    return Optional.empty();
                }
            }

            // Use overloaded method to create handler for discovered JWKS URL
            handler = config.getHttpHandler(jwksUri.get());
        } else {
            // Direct HTTP configuration - use existing handler from config
            handler = config.getHttpHandler();
            resolvedIssuerIdentifier.set(config.getIssuerIdentifier());
        }

        return Optional.of(new ResilientHttpHandler<>(handler, new JwksHttpContentConverter()));
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        // Check current keys
        JWKSKeyLoader current = currentKeys.get();
        if (current != null) {
            Optional<KeyInfo> key = current.getKeyInfo(kid);
            if (key.isPresent()) return key;
        }

        // Check retired keys (grace period for Issue #110)
        // Skip checking retired keys if grace period is zero
        if (!config.getKeyRotationGracePeriod().isZero()) {
            Instant cutoff = Instant.now().minus(config.getKeyRotationGracePeriod());
            for (RetiredKeySet retired : retiredKeys) {
                if (retired.retiredAt.isAfter(cutoff)) {
                    Optional<KeyInfo> key = retired.loader.getKeyInfo(kid);
                    if (key.isPresent()) return key;
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public LoaderStatus getLoaderStatus() {
        return status.get(); // Pure atomic read
    }

    @Override
    public JwksType getJwksType() {
        return config.getJwksType(); // Delegate to config
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        // Return resolved issuer if available, otherwise fall back to config
        String resolved = resolvedIssuerIdentifier.get();
        if (resolved != null) {
            return Optional.of(resolved);
        }
        return Optional.ofNullable(config.getIssuerIdentifier());
    }

    private void updateKeys(Jwks newJwks) {
        // Check if content has actually changed (Issue #110)
        Jwks currentJwks = currentJwksContent.get();
        if (currentJwks != null && currentJwks.equals(newJwks)) {
            LOGGER.debug("JWKS content unchanged, skipping key rotation");
            return; // Content unchanged, no need to update
        }

        // Content has changed, update the reference
        currentJwksContent.set(newJwks);

        JWKSKeyLoader newLoader = JWKSKeyLoader.builder()
                .jwksContent(newJwks)
                .jwksType(config.getJwksType())
                .build();
        newLoader.initJWKSLoader(securityEventCounter);

        // Use a single timestamp to avoid timing issues (Issue #110)
        Instant now = Instant.now();

        // Retire old keys with grace period
        JWKSKeyLoader oldLoader = currentKeys.getAndSet(newLoader);
        if (oldLoader != null) {
            // Special handling for zero grace period - don't retain retired keys
            if (config.getKeyRotationGracePeriod().isZero()) {
                // With zero grace period, immediately discard old keys
                // Don't add to retiredKeys at all
            } else {
                retiredKeys.addFirst(new RetiredKeySet(oldLoader, now));

                // Clean up expired retired keys
                Instant cutoff = now.minus(config.getKeyRotationGracePeriod());
                retiredKeys.removeIf(retired -> retired.retiredAt.isBefore(cutoff));

                // Keep max N retired sets
                while (retiredKeys.size() > config.getMaxRetiredKeySets()) {
                    retiredKeys.removeLast();
                }
            }
        }

        // Log keys update
        LOGGER.info(INFO.JWKS_KEYS_UPDATED.format(status.get()));
    }

    private void startBackgroundRefresh() {
        refreshTask.set(config.getScheduledExecutorService().scheduleAtFixedRate(() -> {
                    try {
                        ResilientHttpHandler<Jwks> handler = httpHandler.get();
                        if (handler == null) {
                            LOGGER.warn(WARN.BACKGROUND_REFRESH_NO_HANDLER.format());
                            return;
                        }

                        HttpResultObject<Jwks> result = handler.load();

                        if (result.isValid() && result.getHttpStatus().map(s -> s == 200).orElse(false)) {
                            updateKeys(result.getResult());
                            LOGGER.debug("Background refresh updated keys");
                        } else if (result.getHttpStatus().map(s -> s == 304).orElse(false)) {
                            LOGGER.debug("Background refresh: keys unchanged (304)");
                        } else {
                            LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED.format(result.getState()));
                        }
                    } catch (IllegalArgumentException e) {
                        // JSON parsing or validation errors
                        LOGGER.warn(WARN.BACKGROUND_REFRESH_PARSE_ERROR.format(e.getMessage(), getIssuerIdentifier().orElseThrow(() -> new IllegalStateException(ISSUER_MUST_BE_RESOLVED))));
                    } catch (RuntimeException e) {
                        // Catch any other runtime exceptions
                        LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED.format(e.getMessage()));
                    }
                },
                config.getRefreshIntervalSeconds(),
                config.getRefreshIntervalSeconds(),
                TimeUnit.SECONDS));

        LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_STARTED.format(config.getRefreshIntervalSeconds()));
    }

    @Override
    public void close() {
        ScheduledFuture<?> task = refreshTask.get();
        if (task != null) {
            task.cancel(false);
        }
        currentKeys.set(null);
        retiredKeys.clear();
        httpHandler.set(null);
        currentJwksContent.set(null);
        status.set(LoaderStatus.UNDEFINED);
    }

    /**
     * Checks if background refresh is enabled and running.
     * Package-private for testing purposes only.
     *
     * @return true if background refresh is active, false otherwise
     */
    boolean isBackgroundRefreshActive() {
        ScheduledFuture<?> task = refreshTask.get();
        return task != null && !task.isCancelled() && !task.isDone();
    }


    /**
     * Private record to hold retired key sets with their retirement timestamp.
     */
    private record RetiredKeySet(JWKSKeyLoader loader, Instant retiredAt) {
    }
}