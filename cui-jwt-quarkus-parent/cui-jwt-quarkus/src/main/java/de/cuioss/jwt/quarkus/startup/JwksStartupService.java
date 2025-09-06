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
import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoader;
import de.cuioss.tools.logging.CuiLogger;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;

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
    private final ManagedExecutor managedExecutor;

    @Inject
    public JwksStartupService(@NonNull Config config, @NonNull ManagedExecutor managedExecutor) {
        this.config = config;
        this.managedExecutor = managedExecutor;
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

        // Trigger async JWKS loading for all issuers with startup delay using ManagedExecutor
        managedExecutor.runAsync(() -> loadAllJwksAsyncWithStartupDelay(configs))
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
        } catch (IllegalStateException | IllegalArgumentException e) {
            LOGGER.error("Configuration error in loadAllJwksAsyncWithStartupDelay: %s", e.getMessage());
        }
    }

    /**
     * Loads JWKS asynchronously for all configured issuers.
     * This method runs in a background thread and does not block application startup.
     *
     * @param configs the issuer configurations to load JWKS for
     */
    private void loadAllJwksAsync(List<IssuerConfig> configs) {
        LOGGER.debug("Beginning background JWKS loading for %d issuer configuration(s)", configs.size());

        if (configs.isEmpty()) {
            LOGGER.debug("No issuer configurations to load - background loading complete");
            return;
        }

        // Use ManagedExecutor to execute individual JWKS loading tasks
        configs.forEach(issuerConfig -> {
            String issuerId = issuerConfig.getIssuerIdentifier();

            managedExecutor.runAsync(() -> loadIssuerJwksSynchronously(issuerConfig))
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            LOGGER.warn(WARN.BACKGROUND_JWKS_LOADING_FAILED_FOR_ISSUER.format(issuerId, throwable.getMessage()));
                        } else {
                            LOGGER.debug("Loading completed for issuer: %s", issuerId);
                        }
                    });
        });
    }

    /**
     * Loads JWKS synchronously for a single issuer configuration.
     * This method runs within a ManagedExecutor task to ensure proper context propagation
     * and native image compatibility.
     *
     * @param issuerConfig the issuer configuration to load JWKS for
     */
    private void loadIssuerJwksSynchronously(IssuerConfig issuerConfig) {
        String issuerId = issuerConfig.getIssuerIdentifier();
        LOGGER.debug("Starting synchronous JWKS loading for issuer: %s", issuerId);

        JwksLoader jwksLoader = issuerConfig.getJwksLoader();

        // Only trigger loading for HTTP-based loaders that support loading
        if (jwksLoader instanceof HttpJwksLoader httpLoader) {
            try {
                // Perform synchronous JWKS loading with simple retry
                LoaderStatus status = loadWithSimpleRetry(httpLoader, issuerId, 3);
                if (status == LoaderStatus.OK) {
                    LOGGER.debug("Successfully loaded JWKS for issuer: %s", issuerId);
                } else {
                    LOGGER.debug("JWKS loading returned status %s for issuer: %s", status, issuerId);
                }
            } catch (Exception e) {
                LOGGER.error("Exception during JWKS loading for issuer %s: %s", issuerId, e.getMessage());
                throw e; // Re-throw so ManagedExecutor whenComplete handles it
            }
        } else {
            LOGGER.debug("JWKS loader for issuer %s is not HTTP-based - skipping background loading", issuerId);
        }
    }

    /**
     * Loads JWKS with simple retry logic for synchronous execution.
     * This method is designed to work reliably in native image environments.
     *
     * @param httpLoader the HTTP JWKS loader
     * @param issuerId   the issuer identifier for logging
     * @param maxRetries maximum number of retry attempts
     * @return LoaderStatus representing the final loading result
     */
    private LoaderStatus loadWithSimpleRetry(HttpJwksLoader httpLoader, String issuerId, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                LOGGER.debug("Attempt %d of %d for issuer: %s", attempt, maxRetries, issuerId);

                // Trigger loading by calling getKeyInfo (which calls ensureLoaded internally)
                httpLoader.getKeyInfo("trigger-loading");

                // Check the status after triggering loading
                LoaderStatus status = httpLoader.getCurrentStatus();

                if (status == LoaderStatus.OK) {
                    LOGGER.info(INFO.BACKGROUND_JWKS_LOADING_COMPLETED_FOR_ISSUER.format(issuerId, status));
                    return status;
                } else {
                    LOGGER.debug("Loading returned status %s on attempt %d for issuer: %s", status, attempt, issuerId);
                    lastException = new RuntimeException("JWKS loading returned status: " + status);
                }
            } catch (Exception e) {
                lastException = e;
                LOGGER.debug("Loading attempt %d failed for issuer %s: %s", attempt, issuerId, e.getMessage());
            }

            // If not the last attempt, wait before retrying
            if (attempt < maxRetries) {
                try {
                    long delay = 2000L * attempt; // Simple linear backoff: 2s, 4s, 6s...
                    LOGGER.debug("Retrying JWKS loading for issuer %s in %dms (attempt %d of %d)",
                            issuerId, delay, attempt + 1, maxRetries);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Retry interrupted for issuer: %s", issuerId);
                    break;
                }
            }
        }

        // All retry attempts failed
        String errorMessage = lastException != null ? lastException.getMessage() : "Unknown error after " + maxRetries + " attempts";
        LOGGER.warn(WARN.BACKGROUND_JWKS_LOADING_FAILED_FOR_ISSUER.format(issuerId, errorMessage));
        return LoaderStatus.ERROR;
    }
}