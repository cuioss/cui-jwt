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
import de.cuioss.http.client.ResilientHttpHandler;
import de.cuioss.http.client.result.HttpResultObject;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.json.Jwks;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.JwksType;
import de.cuioss.jwt.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.uimodel.result.ResultState;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static de.cuioss.jwt.validation.JWTValidationLogMessages.INFO;
import static de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;

/**
 * JWKS loader that loads from HTTP endpoint with caching and background refresh support.
 * Supports both direct HTTP endpoints and well-known discovery.
 * Uses ResilientHttpHandler for stateful HTTP caching with optional scheduled background refresh.
 * Background refresh is automatically started after the first successful key load.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class HttpJwksLoader implements JwksLoader {

    private static final CuiLogger LOGGER = new CuiLogger(HttpJwksLoader.class);

    private SecurityEventCounter securityEventCounter;
    private final HttpJwksLoaderConfig config;
    private final AtomicReference<JWKSKeyLoader> keyLoader = new AtomicReference<>();
    private final AtomicReference<ResilientHttpHandler<Jwks>> httpCache = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    private final AtomicBoolean schedulerStarted = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final JwksHttpContentConverter contentConverter = new JwksHttpContentConverter();

    /**
     * Constructor using HttpJwksLoaderConfig.
     * Supports both direct HTTP handlers and WellKnownConfig configurations.
     * The SecurityEventCounter must be set via initJWKSLoader() before use.
     */
    public HttpJwksLoader(@NonNull HttpJwksLoaderConfig config) {
        this.config = config;
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        ensureLoaded();
        JWKSKeyLoader loader = keyLoader.get();
        return loader != null ? loader.getKeyInfo(kid) : Optional.empty();
    }


    @Override
    public JwksType getJwksType() {
        // Distinguish between direct HTTP and well-known discovery based on configuration
        if (config.getWellKnownConfig() != null) {
            return JwksType.WELL_KNOWN;
        } else {
            return JwksType.HTTP;
        }
    }

    @Override
    public LoaderStatus getCurrentStatus() {
        ResilientHttpHandler<Jwks> cache = httpCache.get();
        if (cache == null) {
            return LoaderStatus.UNDEFINED;
        }

        LoaderStatus cacheStatus = cache.getLoaderStatus();
        // Override ERROR status to OK if we have cached keys
        // This maintains service availability even when refresh fails
        if (cacheStatus == LoaderStatus.ERROR && keyLoader.get() != null) {
            return LoaderStatus.OK;
        }

        return cacheStatus;
    }

    @Override
    public LoaderStatus getLoaderStatus() {
        return getCurrentStatus();
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        // Return issuer identifier from WellKnownConfig if configured
        if (config.getWellKnownConfig() != null) {
            // Use HttpWellKnownResolver to load issuer
            var resolver = config.getWellKnownConfig().createResolver();
            return resolver.getIssuer();
        }

        return Optional.empty();
    }


    /**
     * Shuts down the background refresh scheduler if running.
     * Package-private for testing purposes only.
     */
    void shutdown() {
        ScheduledFuture<?> task = refreshTask.get();
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
            LOGGER.debug("Background refresh task cancelled");
        }
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

    private void ensureLoaded() {
        if (!initialized.get()) {
            throw new IllegalStateException("HttpJwksLoader not initialized. Call initJWKSLoader() first.");
        }
        if (keyLoader.get() == null) {
            loadKeysIfNeeded();
        }
    }

    private void loadKeysIfNeeded() {
        // Double-checked locking pattern with AtomicReference
        if (keyLoader.get() == null) {
            synchronized (this) {
                if (keyLoader.get() == null) {
                    loadKeys();
                }
            }
        }
    }

    private void loadKeys() {
        // Ensure we have a healthy ResilientHttpHandler
        Optional<ResilientHttpHandler<Jwks>> cacheOpt = ensureHttpCache();
        if (cacheOpt.isEmpty()) {
            LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_LOAD_FAILED.format("Unable to establish healthy HTTP connection for JWKS loading"));
            startBackgroundRefreshIfNeeded();
            return;
        }

        ResilientHttpHandler<Jwks> cache = cacheOpt.get();
        HttpResultObject<Jwks> result = cache.load();

        // Acknowledge error details if result is not valid
        if (!result.isValid()) {
            result.getResultDetail();
            result.getHttpErrorCategory();
        }

        // Update key loader if we have valid content and either:
        // 1. Fresh data from server (200 status)
        // 2. We don't have a key loader yet
        boolean shouldUpdateKeys = result.getResult() != null &&
                (result.getHttpStatus().map(s -> s == 200).orElse(false) || keyLoader.get() == null);

        if (shouldUpdateKeys) {
            updateKeyLoader(result);
            LOGGER.info(INFO.JWKS_KEYS_UPDATED.format(result.getState()));
        }

        // Start background refresh after any load attempt
        startBackgroundRefreshIfNeeded();

        // Log appropriate message based on load state
        if (result.isValid()) {
            if (result.getHttpStatus().map(s -> s == 200).orElse(false)) {
                LOGGER.info(INFO.JWKS_HTTP_LOADED::format);
            } else if (result.getHttpStatus().map(s -> s == 304).orElse(false)) {
                LOGGER.debug("JWKS content validated via ETag (304 Not Modified)");
            } else {
                LOGGER.debug("Using cached JWKS content");
            }
        } else {
            if (result.getResult() != null && !result.getResult().isEmpty()) {
                LOGGER.warn(WARN.JWKS_LOAD_FAILED_CACHED_CONTENT::format);
            } else {
                LOGGER.warn(WARN.JWKS_LOAD_FAILED_NO_CACHE::format);
                LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_LOAD_FAILED.format("Failed to load JWKS and no cached content available"));
            }
        }
    }

    /**
     * Triggers asynchronous loading of JWKS without blocking.
     * Updates internal status when loading completes.
     * <p>
     * This method provides non-blocking JWKS initialization suitable for
     * startup services and background loading scenarios. The returned
     * CompletableFuture completes with the final LoaderStatus.
     * </p>
     *
     * @return CompletableFuture that completes with the LoaderStatus after loading
     * @since 1.1
     */
    public CompletableFuture<LoaderStatus> loadAsync() {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized.get()) {
                LOGGER.warn("HttpJwksLoader not initialized during async loading - initializing with empty SecurityEventCounter");
                // Handle case where async loading is called before initialization
                return LoaderStatus.ERROR;
            }

            LOGGER.debug("Starting asynchronous JWKS loading");
            loadKeys(); // Existing synchronous method

            // Return the updated status
            LoaderStatus currentStatus = getCurrentStatus();
            LOGGER.debug("Asynchronous JWKS loading completed with status: {}", currentStatus);
            return currentStatus;
        });
    }

    private void updateKeyLoader(HttpResultObject<Jwks> result) {
        JWKSKeyLoader newLoader = JWKSKeyLoader.builder()
                .jwksContent(result.getResult())
                .jwksType(getJwksType())
                .build();
        // Initialize the JWKSKeyLoader with the SecurityEventCounter
        // Since JWKSKeyLoader initialization is synchronous, we can safely call join()
        newLoader.initJWKSLoader(securityEventCounter).join();
        keyLoader.set(newLoader);
    }

    private void startBackgroundRefreshIfNeeded() {
        if (config.getScheduledExecutorService() != null && config.getRefreshIntervalSeconds() > 0 && schedulerStarted.compareAndSet(false, true)) {

            ScheduledExecutorService executor = config.getScheduledExecutorService();
            int intervalSeconds = config.getRefreshIntervalSeconds();

            ScheduledFuture<?> task = executor.scheduleAtFixedRate(
                    this::backgroundRefresh,
                    intervalSeconds,
                    intervalSeconds,
                    TimeUnit.SECONDS
            );
            refreshTask.set(task);

            LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_STARTED.format(intervalSeconds));
        }
    }

    private void backgroundRefresh() {
        LOGGER.debug("Starting background JWKS refresh");
        ResilientHttpHandler<Jwks> cache = httpCache.get();
        if (cache == null) {
            LOGGER.warn(JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_SKIPPED::format);
            return;
        }

        HttpResultObject<Jwks> result = cache.load();

        // Handle error states
        if (!result.isValid() && result.getState() != ResultState.WARNING) {
            LOGGER.warn(JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_FAILED.format(result.getState()));
            return;
        }

        // Update keys only if we got fresh data from server (200 status)
        if (result.getResult() != null && result.getHttpStatus().map(s -> s == 200).orElse(false)) {
            updateKeyLoader(result);
            LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_UPDATED.format(result.getState()));
        } else {
            LOGGER.debug("Background refresh completed, no changes detected: %s", result.getState());
        }
    }

    /**
     * Ensures that we have a healthy ResilientHttpHandler based on configuration.
     * Creates the handler dynamically based on whether we have a direct HTTP handler
     * or need to resolve via WellKnownConfig.
     *
     * @return Optional containing the ResilientHttpHandler if healthy, empty if sources are not healthy
     */
    private Optional<ResilientHttpHandler<Jwks>> ensureHttpCache() {
        // Fast path - already have a cache
        ResilientHttpHandler<Jwks> cache = httpCache.get();
        if (cache != null) {
            return Optional.of(cache);
        }

        // Slow path - need to create cache based on configuration
        synchronized (this) {
            // Double-check after acquiring lock
            cache = httpCache.get();
            if (cache != null) {
                return Optional.of(cache);
            }

            HttpHandler httpHandler;

            switch (getJwksType()) {
                case HTTP:
                    // Direct HTTP handler configuration
                    httpHandler = config.getHttpHandler();
                    LOGGER.debug("Creating ResilientHttpHandler from direct HTTP configuration for URI: %s",
                            httpHandler.getUri());
                    break;

                case WELL_KNOWN:
                    // Well-known configuration - create handler from discovered JWKS URI
                    LOGGER.debug("Getting JWKS URI from WellKnownConfig");

                    var resolver = config.getWellKnownConfig().createResolver();
                    Optional<String> jwksUriResult = resolver.getJwksUri();

                    if (jwksUriResult.isEmpty()) {
                        LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_URI_RESOLUTION_FAILED::format);
                        return Optional.empty();
                    }

                    String jwksUri = jwksUriResult.get();
                    LOGGER.debug("Creating ResilientHttpHandler from discovered JWKS URI: %s", jwksUri);

                    try {
                        httpHandler = HttpHandler.builder()
                                .url(jwksUri)
                                .connectionTimeoutSeconds(5)
                                .readTimeoutSeconds(10)
                                .build();
                        LOGGER.info(JWTValidationLogMessages.INFO.JWKS_URI_RESOLVED.format("discovered URI: " + jwksUri));
                    } catch (IllegalArgumentException e) {
                        LOGGER.error("Invalid JWKS URI discovered from well-known config: %s", jwksUri, e);
                        return Optional.empty();
                    }
                    break;

                default:
                    LOGGER.error(JWTValidationLogMessages.ERROR.UNSUPPORTED_JWKS_TYPE.format(getJwksType()));
                    return Optional.empty();
            }

            // Create ResilientHttpHandler with the unified JwksHttpContentConverter
            if (httpHandler != null) {
                cache = new ResilientHttpHandler<>(httpHandler, contentConverter);
                httpCache.set(cache);
                return Optional.of(cache);
            }

            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<LoaderStatus> initJWKSLoader(@NonNull SecurityEventCounter securityEventCounter) {
        if (initialized.compareAndSet(false, true)) {
            this.securityEventCounter = securityEventCounter;
            LOGGER.debug("HttpJwksLoader initialized with SecurityEventCounter");

            // Trigger async loading and return a CompletableFuture
            return CompletableFuture.supplyAsync(() -> {
                loadKeysIfNeeded();
                return getCurrentStatus();
            });
        }
        // Already initialized, return current status immediately
        return CompletableFuture.completedFuture(getCurrentStatus());
    }

}
