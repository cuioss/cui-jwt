/**
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
package de.cuioss.jwt.quarkus.integration.config;

import de.cuioss.tools.logging.CuiLogger;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Configuration class for integration benchmark settings.
 * Provides centralized configuration for token repository and benchmark parameters.
 */
@UtilityClass
public class BenchmarkConfiguration {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkConfiguration.class);

    // Token Repository Configuration
    public static final int DEFAULT_TOKEN_POOL_SIZE = 100;
    public static final String DEFAULT_KEYCLOAK_URL = "https://keycloak:8443";

    // Benchmark Realm Configuration
    public static final String KEYCLOAK_REALM = "benchmark";
    public static final String KEYCLOAK_CLIENT_ID = "benchmark-client";
    public static final String KEYCLOAK_CLIENT_SECRET = "benchmark-secret";
    public static final String KEYCLOAK_USERNAME = "benchmark-user";
    public static final String KEYCLOAK_PASSWORD = "benchmark-password";

    // Integration Realm Configuration
    public static final String INTEGRATION_REALM = "integration";
    public static final String INTEGRATION_CLIENT_ID = "integration-client";
    public static final String INTEGRATION_CLIENT_SECRET = "integration-secret";
    public static final String INTEGRATION_USERNAME = "integration-user";
    public static final String INTEGRATION_PASSWORD = "integration-password";

    // Benchmark Runtime Configuration
    public static final int WARMUP_TOKEN_REQUESTS = 5;
    public static final int MAX_TOKEN_FETCH_RETRIES = 3;
    public static final int TOKEN_FETCH_RETRY_DELAY_MS = 1000;


    /**
     * Gets the token pool size from system properties or default value.
     *
     * @return Number of tokens to pre-load for each type
     */
    public static int getTokenPoolSize() {
        String size = System.getProperty("benchmark.token.pool.size", String.valueOf(DEFAULT_TOKEN_POOL_SIZE));
        try {
            int poolSize = Integer.parseInt(size);
            if (poolSize <= 0) {
                LOGGER.warn("Invalid token pool size: %s. Using default: %s", poolSize, DEFAULT_TOKEN_POOL_SIZE);
                return DEFAULT_TOKEN_POOL_SIZE;
            }
            return poolSize;
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid token pool size format: %s. Using default: %s", size, DEFAULT_TOKEN_POOL_SIZE);
            return DEFAULT_TOKEN_POOL_SIZE;
        }
    }

    /**
     * Gets the Keycloak URL from system properties or default value.
     *
     * @return Keycloak server URL
     */
    public static String getKeycloakUrl() {
        return System.getProperty("benchmark.keycloak.url", DEFAULT_KEYCLOAK_URL);
    }

    /**
     * Gets the application URL from system properties or default value.
     *
     * @return Application server URL
     */
    public static String getApplicationUrl() {
        String port = System.getProperty("test.https.port", "10443");
        return System.getProperty("benchmark.application.url", "https://localhost:" + port);
    }

    /**
     * Gets the Keycloak realm name.
     *
     * @return Keycloak realm name
     */
    public static String getKeycloakRealm() {
        return System.getProperty("benchmark.keycloak.realm", KEYCLOAK_REALM);
    }

    /**
     * Gets the Keycloak client ID.
     *
     * @return Keycloak client ID
     */
    public static String getKeycloakClientId() {
        return System.getProperty("benchmark.keycloak.client.id", KEYCLOAK_CLIENT_ID);
    }

    /**
     * Gets the Keycloak client secret for token requests.
     *
     * @return Keycloak client secret
     */
    public static String getKeycloakClientSecret() {
        return System.getProperty("benchmark.keycloak.client.secret", KEYCLOAK_CLIENT_SECRET);
    }

    /**
     * Gets the Keycloak username for token requests.
     *
     * @return Keycloak username
     */
    public static String getKeycloakUsername() {
        return System.getProperty("benchmark.keycloak.username", KEYCLOAK_USERNAME);
    }

    /**
     * Gets the Keycloak password for token requests.
     *
     * @return Keycloak password
     */
    public static String getKeycloakPassword() {
        return System.getProperty("benchmark.keycloak.password", KEYCLOAK_PASSWORD);
    }

    /**
     * Gets the maximum number of retries for token fetching.
     *
     * @return Maximum retry count
     */
    public static int getMaxTokenFetchRetries() {
        String retries = System.getProperty("benchmark.token.fetch.retries", String.valueOf(MAX_TOKEN_FETCH_RETRIES));
        try {
            return Integer.parseInt(retries);
        } catch (NumberFormatException e) {
            return MAX_TOKEN_FETCH_RETRIES;
        }
    }

    /**
     * Gets the delay between token fetch retries in milliseconds.
     *
     * @return Retry delay in milliseconds
     */
    public static int getTokenFetchRetryDelay() {
        String delay = System.getProperty("benchmark.token.fetch.retry.delay", String.valueOf(TOKEN_FETCH_RETRY_DELAY_MS));
        try {
            return Integer.parseInt(delay);
        } catch (NumberFormatException e) {
            return TOKEN_FETCH_RETRY_DELAY_MS;
        }
    }

    /**
     * Creates a list of realm configurations for multi-realm token fetching.
     * 
     * Design Note: This method encapsulates the realm configurations to avoid
     * code duplication while allowing the TokenRepository to work with multiple realms.
     * Half the tokens will be fetched from each realm to provide balanced testing
     * of both well-known discovery (benchmark) and direct JWKS URL (integration) configurations.
     * 
     * @return List of configured realms
     */
    public static List<RealmConfig> getRealmConfigurations() {
        RealmConfig benchmarkRealm = RealmConfig.builder()
                .realmName(getKeycloakRealm())
                .clientId(getKeycloakClientId())
                .clientSecret(getKeycloakClientSecret())
                .username(getKeycloakUsername())
                .password(getKeycloakPassword())
                .displayName("Benchmark Realm (Well-Known Discovery)")
                .build();

        RealmConfig integrationRealm = RealmConfig.builder()
                .realmName(INTEGRATION_REALM)
                .clientId(INTEGRATION_CLIENT_ID)
                .clientSecret(INTEGRATION_CLIENT_SECRET)
                .username(INTEGRATION_USERNAME)
                .password(INTEGRATION_PASSWORD)
                .displayName("Integration Realm (Direct JWKS)")
                .build();

        return List.of(benchmarkRealm, integrationRealm);
    }

    /**
     * Gets the benchmark realm configuration.
     * 
     * @return Benchmark realm configuration
     */
    public static RealmConfig getBenchmarkRealmConfig() {
        return RealmConfig.builder()
                .realmName(getKeycloakRealm())
                .clientId(getKeycloakClientId())
                .clientSecret(getKeycloakClientSecret())
                .username(getKeycloakUsername())
                .password(getKeycloakPassword())
                .displayName("Benchmark Realm")
                .build();
    }

    /**
     * Gets the integration realm configuration.
     * 
     * @return Integration realm configuration
     */
    public static RealmConfig getIntegrationRealmConfig() {
        return RealmConfig.builder()
                .realmName(INTEGRATION_REALM)
                .clientId(INTEGRATION_CLIENT_ID)
                .clientSecret(INTEGRATION_CLIENT_SECRET)
                .username(INTEGRATION_USERNAME)
                .password(INTEGRATION_PASSWORD)
                .displayName("Integration Realm")
                .build();
    }

    /**
     * Logs the current configuration settings.
     */
    public static void logConfiguration() {
        LOGGER.info("📋 Benchmark Configuration:");
        LOGGER.info("  Token Pool Size: %s", getTokenPoolSize());
        LOGGER.info("  Keycloak URL: %s", getKeycloakUrl());
        LOGGER.info("  Application URL: %s", getApplicationUrl());
        LOGGER.info("  Keycloak Realm: %s", getKeycloakRealm());
        LOGGER.info("  Keycloak Client: %s", getKeycloakClientId());
        LOGGER.info("  Max Retries: %s", getMaxTokenFetchRetries());
        LOGGER.info("  Retry Delay: %s ms", getTokenFetchRetryDelay());
        LOGGER.info("  Available Realms: %s", getRealmConfigurations().size());

        // Log realm details
        getRealmConfigurations().forEach(realm ->
                LOGGER.info("    - %s: %s", realm.getEffectiveDisplayName(), realm.getRealmName()));
    }
}