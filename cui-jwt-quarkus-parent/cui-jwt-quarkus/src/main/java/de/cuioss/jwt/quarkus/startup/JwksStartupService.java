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

import de.cuioss.http.client.LoaderStatus;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoader;
import de.cuioss.tools.logging.CuiLogger;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;

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

    private final List<IssuerConfig> issuerConfigs;
    private final ManagedExecutor managedExecutor;

    @Inject
    public JwksStartupService(@NonNull List<IssuerConfig> issuerConfigs, @NonNull ManagedExecutor managedExecutor) {
        this.issuerConfigs = issuerConfigs;
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

        // Use the injected issuer configurations (same instances as TokenValidatorProducer)
        // Handle case where TokenValidatorProducer failed to initialize
        try {
            int configCount = issuerConfigs.size();
            LOGGER.info(INFO.STARTING_ASYNCHRONOUS_JWKS_INITIALIZATION.format(configCount));

            if (configCount == 0) {
                LOGGER.info(INFO.NO_ISSUER_CONFIGURATIONS_FOUND.format());
                return;
            }
        } catch (Exception e) {
            LOGGER.info(INFO.NO_ISSUER_CONFIGURATIONS_FOUND.format());
            LOGGER.debug("IssuerConfig injection failed: %s", e.getMessage());
            return;
        }

        // Start immediate JWKS loading - JWT validation will handle failures gracefully
        LOGGER.info(INFO.JWKS_STARTUP_SERVICE_INITIALIZED.format("Starting immediate background JWKS loading"));

        managedExecutor.runAsync(() -> loadAllJwksAsync(issuerConfigs))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        LOGGER.warn(WARN.BACKGROUND_JWKS_ISSUES_WARNING.format(throwable.getMessage()));
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
        LOGGER.debug("Beginning background JWKS loading for %d issuer configuration(s)", configs.size());

        if (configs.isEmpty()) {
            LOGGER.debug("No issuer configurations to load - background loading complete");
            return;
        }

        // Use ManagedExecutor to execute individual JWKS loading tasks
        configs.forEach(issuerConfig -> {
            String issuerId = issuerConfig.getIssuerIdentifier();

            managedExecutor.runAsync(() -> loadIssuerJwks(issuerConfig))
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
     * Loads JWKS for a single issuer configuration.
     * This method runs within a ManagedExecutor task and relies on HttpJwksLoader's
     * built-in retry and refresh mechanisms for robustness.
     *
     * @param issuerConfig the issuer configuration to load JWKS for
     */
    private void loadIssuerJwks(IssuerConfig issuerConfig) {
        String issuerId = issuerConfig.getIssuerIdentifier();
        LOGGER.debug("Starting JWKS loading for issuer: %s", issuerId);

        JwksLoader jwksLoader = issuerConfig.getJwksLoader();

        // Only trigger loading for HTTP-based loaders that support loading
        if (jwksLoader instanceof HttpJwksLoader httpLoader) {
            try {
                // Trigger JWKS loading - HttpJwksLoader handles retries internally
                httpLoader.getKeyInfo("startup-trigger");
                LoaderStatus status = httpLoader.getCurrentStatus();

                if (status == LoaderStatus.OK) {
                    LOGGER.info(INFO.BACKGROUND_JWKS_LOADING_COMPLETED_FOR_ISSUER.format(issuerId, status));
                } else {
                    LOGGER.debug("JWKS loading returned status %s for issuer: %s - will retry via background refresh", status, issuerId);
                }
            } catch (Exception e) {
                LOGGER.warn(WARN.JWKS_LOADING_RETRY_WARNING.format(issuerId, e.getMessage()));
                // Don't re-throw - let background refresh handle retries
            }
        } else {
            LOGGER.debug("JWKS loader for issuer %s is not HTTP-based - skipping background loading", issuerId);
        }
    }

}