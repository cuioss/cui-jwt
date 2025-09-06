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
package de.cuioss.jwt.quarkus.startup;

import de.cuioss.jwt.quarkus.config.IssuerConfigResolver;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoader;
import de.cuioss.tools.logging.CuiLogger;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.NonNull;
import org.eclipse.microprofile.config.Config;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.ERROR;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN;

/**
 * Startup service that triggers asynchronous JWKS loading for all configured issuers.
 * <p>
 * This service uses the modern Quarkus {@link Startup} annotation to initiate background loading
 * of JWKS for each configured issuer. This ensures that JWKS loading does not block
 * application startup while still making the keys available as soon as possible.
 * </p>
 * <p>
 * The service operates independently of health checks, providing proper separation
 * of concerns between initialization and health monitoring.
 * </p>
 *
 * @since 1.0
 * @see HttpJwksLoader#loadAsync()
 * @see Startup
 */
@ApplicationScoped
@Startup
public class JwksStartupService {

    private static final CuiLogger LOGGER = new CuiLogger(JwksStartupService.class);

    private final Config config;

    @Inject
    public JwksStartupService(@NonNull Config config) {
        this.config = config;
    }

    /**
     * Initializes asynchronous JWKS loading on application startup.
     * This method is called automatically when the application starts up due to the @Startup annotation.
     * Includes startup delay to allow external services (like Keycloak) to fully initialize.
     */
    @PostConstruct
    public void initializeJwks() {
        LOGGER.info(INFO.JWKS_STARTUP_SERVICE_INITIALIZED.format());
        
        // Resolve configurations independently to avoid circular dependency
        IssuerConfigResolver resolver = new IssuerConfigResolver(config);
        List<IssuerConfig> configs;
        
        try {
            configs = resolver.resolveIssuerConfigs();
        } catch (IllegalStateException | IllegalArgumentException e) {
            // No issuer configurations found - this is expected in some scenarios
            LOGGER.info(INFO.STARTING_ASYNCHRONOUS_JWKS_INITIALIZATION.format(0));
            LOGGER.info(INFO.NO_ISSUER_CONFIGURATIONS_FOUND.format());
            LOGGER.debug("Issuer configuration resolution failed: {}", e.getMessage());
            return;
        }
        
        LOGGER.info(INFO.STARTING_ASYNCHRONOUS_JWKS_INITIALIZATION.format(configs.size()));

        if (configs.isEmpty()) {
            LOGGER.info(INFO.NO_ISSUER_CONFIGURATIONS_FOUND.format());
            return;
        }

        // Trigger async JWKS loading for all issuers with startup delay
        CompletableFuture.runAsync(() -> loadAllJwksAsyncWithStartupDelay(configs))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error(ERROR.BACKGROUND_JWKS_INITIALIZATION_ERROR.format(throwable.getMessage()));
                    } else {
                        LOGGER.info(INFO.BACKGROUND_JWKS_INITIALIZATION_COMPLETED.format());
                    }
                });
    }

    /**
     * Loads JWKS asynchronously for all configured issuers with startup delay.
     * This method includes a startup delay to allow external services to fully initialize,
     * then delegates to the standard async loading method.
     *
     * @param configs the issuer configurations to load JWKS for
     */
    private void loadAllJwksAsyncWithStartupDelay(List<IssuerConfig> configs) {
        try {
            // Add startup delay to allow external services (like Keycloak) to initialize
            LOGGER.info(INFO.JWKS_STARTUP_SERVICE_INITIALIZED.format("Adding 10-second startup delay for external service readiness"));
            Thread.sleep(10000);
            LOGGER.info(INFO.JWKS_STARTUP_SERVICE_INITIALIZED.format("Startup delay completed, beginning JWKS loading"));
            
            // Delegate to existing async loading method
            loadAllJwksAsync(configs);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Startup delay interrupted - proceeding with immediate JWKS loading");
            loadAllJwksAsync(configs);
        }
    }

    /**
     * Loads JWKS asynchronously for all configured issuers.
     * This method runs in a background thread and does not block application startup.
     *
     * @param configs the issuer configurations to load JWKS for
     */
    private void loadAllJwksAsync(List<IssuerConfig> configs) {
        LOGGER.debug("Beginning background JWKS loading for {} issuer configuration(s)", configs.size());

        if (configs.isEmpty()) {
            LOGGER.debug("No issuer configurations to load - background loading complete");
            return;
        }

        // Create async loading tasks for each issuer
        CompletableFuture<?>[] loadingTasks = configs.stream()
                .map(this::loadIssuerJwksAsync)
                .toArray(CompletableFuture[]::new);

        try {
            // Wait for all loading tasks to complete with timeout
            CompletableFuture.allOf(loadingTasks)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            LOGGER.warn(WARN.JWKS_BACKGROUND_LOADING_COMPLETED_WITH_ERRORS.format(throwable.getMessage()));
                        } else {
                            LOGGER.info(INFO.BACKGROUND_JWKS_INITIALIZATION_COMPLETED.format());
                        }
                    });
        } catch (Exception e) {
            LOGGER.error(ERROR.JWKS_BACKGROUND_LOADING_COORDINATION_ERROR.format(e.getMessage()));
        }
    }

    /**
     * Loads JWKS asynchronously for a single issuer configuration with retry logic.
     *
     * @param issuerConfig the issuer configuration to load JWKS for
     * @return CompletableFuture representing the loading operation
     */
    private CompletableFuture<Void> loadIssuerJwksAsync(IssuerConfig issuerConfig) {
        String issuerId = issuerConfig.getIssuerIdentifier();
        LOGGER.debug("Starting background JWKS loading for issuer: {}", issuerId);

        JwksLoader jwksLoader = issuerConfig.getJwksLoader();

        // Only trigger loading for HTTP-based loaders that support async loading
        if (jwksLoader instanceof HttpJwksLoader httpLoader) {
            return loadWithRetry(httpLoader, issuerId, 3, 2000);
        } else {
            LOGGER.debug("JWKS loader for issuer {} is not HTTP-based - skipping background loading", issuerId);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Loads JWKS with exponential backoff retry logic.
     *
     * @param httpLoader the HTTP JWKS loader
     * @param issuerId   the issuer identifier for logging
     * @param maxRetries maximum number of retry attempts
     * @param baseDelayMs base delay in milliseconds
     * @return CompletableFuture representing the loading operation
     */
    private CompletableFuture<Void> loadWithRetry(HttpJwksLoader httpLoader, String issuerId, int maxRetries, long baseDelayMs) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;
            
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    CompletableFuture<de.cuioss.jwt.validation.jwks.LoaderStatus> loadFuture = httpLoader.loadAsync();
                    de.cuioss.jwt.validation.jwks.LoaderStatus status = loadFuture.get(10, TimeUnit.SECONDS);
                    
                    if (status != de.cuioss.jwt.validation.jwks.LoaderStatus.ERROR) {
                        LOGGER.info(INFO.BACKGROUND_JWKS_LOADING_COMPLETED_FOR_ISSUER.format(issuerId, status));
                        return null;
                    } else {
                        lastException = new RuntimeException("JWKS loading returned ERROR status");
                    }
                } catch (Exception e) {
                    lastException = e;
                    LOGGER.debug("JWKS loading attempt {} failed for issuer {}: {}", attempt, issuerId, e.getMessage());
                }
                
                // If not the last attempt, wait before retrying
                if (attempt < maxRetries) {
                    try {
                        long delay = baseDelayMs * (1L << (attempt - 1)); // Exponential backoff
                        LOGGER.debug("Retrying JWKS loading for issuer {} in {}ms (attempt {} of {})", issuerId, delay, attempt + 1, maxRetries);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            // All retry attempts failed
            LOGGER.warn(WARN.BACKGROUND_JWKS_LOADING_FAILED_FOR_ISSUER.format(issuerId, 
                lastException != null ? lastException.getMessage() : "Unknown error after " + maxRetries + " attempts"));
            return null;
        });
    }
}