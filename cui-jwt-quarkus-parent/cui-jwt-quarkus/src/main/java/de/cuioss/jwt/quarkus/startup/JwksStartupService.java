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

        // Trigger async JWKS loading for all issuers
        CompletableFuture.runAsync(() -> loadAllJwksAsync(configs))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error(ERROR.BACKGROUND_JWKS_INITIALIZATION_ERROR.format(throwable.getMessage()));
                    } else {
                        LOGGER.info(INFO.BACKGROUND_JWKS_INITIALIZATION_COMPLETED.format());
                    }
                });
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
     * Loads JWKS asynchronously for a single issuer configuration.
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
            return httpLoader.loadAsync()
                    .handle((loaderStatus, throwable) -> {
                        if (throwable != null) {
                            LOGGER.warn(WARN.BACKGROUND_JWKS_LOADING_FAILED_FOR_ISSUER.format(issuerId, throwable.getMessage()));
                        } else {
                            LOGGER.info(INFO.BACKGROUND_JWKS_LOADING_COMPLETED_FOR_ISSUER.format(issuerId, loaderStatus));
                        }
                        return null; // Return Void
                    });
        } else {
            LOGGER.debug("JWKS loader for issuer {} is not HTTP-based - skipping background loading", issuerId);
            return CompletableFuture.completedFuture(null);
        }
    }
}