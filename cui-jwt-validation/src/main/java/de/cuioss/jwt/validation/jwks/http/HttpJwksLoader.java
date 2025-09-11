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

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.json.Jwks;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.JwksType;
import de.cuioss.jwt.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.client.ETagAwareHttpHandler;
import de.cuioss.tools.net.http.client.LoaderStatus;
import de.cuioss.tools.net.http.converter.HttpContentConverter;
import de.cuioss.tools.net.http.result.HttpResultObject;
import de.cuioss.uimodel.result.ResultState;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * Uses ETagAwareHttpHandler for stateful HTTP caching with optional scheduled background refresh.
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
    private final AtomicReference<ETagAwareHttpHandler<Jwks>> httpCache = new AtomicReference<>();
    private volatile LoaderStatus status = LoaderStatus.UNDEFINED;
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    private final AtomicBoolean schedulerStarted = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Constructor using HttpJwksLoaderConfig.
     * Supports both direct HTTP handlers and WellKnownResolver configurations.
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
        if (config.getWellKnownResolver() != null) {
            return JwksType.WELL_KNOWN;
        } else {
            return JwksType.HTTP;
        }
    }

    @Override
    public LoaderStatus getCurrentStatus() {
        return status;
    }

    @Override
    public LoaderStatus isHealthy() {
        return getCurrentStatus();
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        // Return issuer identifier from well-known resolver if configured
        if (config.getWellKnownResolver() != null && config.getWellKnownResolver().isHealthy() == LoaderStatus.OK) {
            Optional<String> issuerResult = config.getWellKnownResolver().getIssuer();
            if (issuerResult.isPresent()) {
                return issuerResult;
            } else {
                LOGGER.debug("Failed to retrieve issuer identifier from well-known resolver: issuer not available");
            }
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
        // Ensure we have a healthy ETagAwareHttpHandler
        Optional<ETagAwareHttpHandler<Jwks>> cacheOpt = ensureHttpCache();
        if (cacheOpt.isEmpty()) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_LOAD_FAILED.format("Unable to establish healthy HTTP connection for JWKS loading"));

            // Start background refresh even when HTTP cache setup fails
            // This ensures that failed initial loads will be retried
            startBackgroundRefreshIfNeeded();
            return;
        }

        ETagAwareHttpHandler<Jwks> cache = cacheOpt.get();

        HttpResultObject<Jwks> result = cache.load();

        // Only update key loader if data has changed and we have content
        boolean dataChanged = isDataChanged(result);

        // Acknowledge error details before accessing result if not valid
        if (!result.isValid()) {
            result.getResultDetail();
            result.getHttpErrorCategory();
        }

        if (result.getResult() != null && (dataChanged || keyLoader.get() == null)) {
            updateKeyLoader(result);
            LOGGER.info(INFO.JWKS_KEYS_UPDATED.format(result.getState()));
        }

        // Start background refresh after any load attempt (success or failure)
        // This ensures that failed initial loads will be retried
        startBackgroundRefreshIfNeeded();

        // Log appropriate message based on load state and handle error states
        if (result.isValid()) {
            // Determine type of successful load
            if (result.getHttpStatus().map(s -> s == 200).orElse(false)) {
                // Fresh content from server
                LOGGER.info(INFO.JWKS_HTTP_LOADED::format);
            } else if (result.getHttpStatus().map(s -> s == 304).orElse(false)) {
                // Content validated via ETag (304 Not Modified)
                LOGGER.debug("JWKS content validated via ETag (304 Not Modified)");
            } else {
                // Local cache content
                LOGGER.debug("Using cached JWKS content");
            }
        } else {
            // Handle error states - acknowledge error details before accessing result
            result.getResultDetail();
            result.getHttpErrorCategory();

            if (result.getResult() != null && !result.getResult().isEmpty()) {
                // Error with cached content available
                LOGGER.warn(WARN.JWKS_LOAD_FAILED_CACHED_CONTENT::format);
            } else {
                // Error with no cached content (null or empty string)
                LOGGER.warn(WARN.JWKS_LOAD_FAILED_NO_CACHE::format);
                this.status = LoaderStatus.ERROR;
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
                this.status = LoaderStatus.ERROR;
                return LoaderStatus.ERROR;
            }

            LOGGER.debug("Starting asynchronous JWKS loading");
            loadKeys(); // Existing synchronous method

            // Return the updated status
            LoaderStatus currentStatus = this.status;
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
        newLoader.initJWKSLoader(securityEventCounter);
        keyLoader.set(newLoader);
        this.status = LoaderStatus.OK;
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
        Optional<ETagAwareHttpHandler<Jwks>> cacheOpt = Optional.ofNullable(httpCache.get());
        if (cacheOpt.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_SKIPPED::format);
            return;
        }

        ETagAwareHttpHandler<Jwks> cache = cacheOpt.get();

        HttpResultObject<Jwks> result = cache.load();

        // Handle error states
        if (!result.isValid()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_FAILED.format(result.getState()));
            return;
        }

        // Handle warning states (error with cached content)
        if (result.getState() == ResultState.WARNING) {
            LOGGER.warn(JWTValidationLogMessages.WARN.BACKGROUND_REFRESH_FAILED.format(result.getState()));
            // Continue processing as we have cached content
        }

        // Only update keys if data has actually changed
        boolean dataChanged = isDataChanged(result);
        if (result.getResult() != null && dataChanged) {
            updateKeyLoader(result);
            LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_UPDATED.format(result.getState()));
        } else {
            LOGGER.debug("Background refresh completed, no changes detected: %s", result.getState());
        }
    }

    /**
     * Ensures that we have a healthy ETagAwareHttpHandler based on configuration.
     * Creates the handler dynamically based on whether we have a direct HTTP handler
     * or need to resolve via WellKnownResolver.
     *
     * @return Optional containing the ETagAwareHttpHandler if healthy, empty if sources are not healthy
     */
    private Optional<ETagAwareHttpHandler<Jwks>> ensureHttpCache() {
        // Fast path - already have a cache
        ETagAwareHttpHandler<Jwks> cache = httpCache.get();
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

            switch (getJwksType()) {
                case HTTP:
                    // Direct HTTP handler configuration
                    // HttpHandler is guaranteed non-null by HttpJwksLoaderConfig.build() validation
                    LOGGER.debug("Creating ETagAwareHttpHandler from direct HTTP configuration for URI: %s",
                            config.getHttpHandler().getUri());
                    // Create a simple HttpContentConverter for Jwks using DSL-JSON directly
                    ParserConfig parserConfig = ParserConfig.builder().build();
                    var dslJson = parserConfig.getDslJson();
                    HttpContentConverter<Jwks> httpContentConverter = new HttpContentConverter<Jwks>() {
                        @Override
                        public Optional<Jwks> convert(Object rawContent) {
                            String body = (rawContent instanceof String s) ? s :
                                    (rawContent != null) ? rawContent.toString() : null;
                            if (body == null || body.trim().isEmpty()) {
                                return Optional.of(emptyValue());
                            }
                            try {
                                // Use DSL-JSON to parse to Jwks
                                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                                Jwks jwks = dslJson.deserialize(Jwks.class, bodyBytes, bodyBytes.length);
                                return Optional.ofNullable(jwks);
                            } catch (IOException | IllegalArgumentException e) {
                                return Optional.empty();
                            }
                        }

                        @Override
                        public HttpResponse.BodyHandler<?> getBodyHandler() {
                            return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
                        }

                        @Override
                        public Jwks emptyValue() {
                            return Jwks.empty();
                        }
                    };
                    cache = new ETagAwareHttpHandler<>(config.getHttpHandler(), httpContentConverter);
                    httpCache.set(cache);
                    return Optional.of(cache);

                case WELL_KNOWN:
                    // Well-known resolver configuration - use direct ETag-aware handler
                    LOGGER.debug("Getting ETag-aware JWKS handler from WellKnownResolver");

                    // Check if well-known resolver is healthy
                    if (config.getWellKnownResolver().isHealthy() != LoaderStatus.OK) {
                        LOGGER.debug("WellKnownResolver is not healthy, cannot create HTTP cache");
                        return Optional.empty();
                    }

                    // Get the pre-configured ETag-aware handler directly
                    Optional<ETagAwareHttpHandler<Jwks>> etagResult = config.getWellKnownResolver().getJwksETagHandler();
                    if (etagResult.isEmpty()) {
                        LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_URI_RESOLUTION_FAILED::format);
                        return Optional.empty();
                    }

                    cache = etagResult.get();
                    LOGGER.info(JWTValidationLogMessages.INFO.JWKS_URI_RESOLVED.format("ETag-aware handler from well-known resolver"));
                    httpCache.set(cache);
                    return Optional.of(cache);

                default:
                    LOGGER.error(JWTValidationLogMessages.ERROR.UNSUPPORTED_JWKS_TYPE.format(getJwksType()));
                    return Optional.empty();
            }
        }
    }

    @Override
    public void initJWKSLoader(@NonNull SecurityEventCounter securityEventCounter) {
        if (initialized.compareAndSet(false, true)) {
            this.securityEventCounter = securityEventCounter;
            LOGGER.debug("HttpJwksLoader initialized with SecurityEventCounter");
        }
    }

    /**
     * Determines if data has changed based on HttpResultObject state.
     * Data is considered changed for:
     * - Fresh content from server (HTTP 200 status)
     * - Error with no cached content (unknown state)
     *
     * @param result the HttpResultObject to check
     * @return true if data has changed and keys need reevaluation
     */
    private boolean isDataChanged(HttpResultObject<Jwks> result) {
        // Fresh content from server - data definitely changed
        if (result.isValid() && result.getHttpStatus().map(s -> s == 200).orElse(false)) {
            return true;
        }

        // Error with no cached content - state unknown, assume data changed
        if (!result.isValid()) {
            // Acknowledge error details before accessing result
            result.getResultDetail();
            result.getHttpErrorCategory();
            if (result.getResult() == null) {
                return true;
            }
        }

        // All other cases: cached content, 304 not modified, error with cache - no change
        return false;
    }
}
