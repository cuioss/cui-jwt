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
package de.cuioss.benchmarking.common.repository;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for TokenRepository to connect to Keycloak and fetch tokens
 * for benchmark testing.
 *
 * @since 1.0
 */
@Value @Builder public class TokenRepositoryConfig {

    /**
     * System property keys for token repository configuration.
     */
    public static final class Properties {
        public static final String KEYCLOAK_URL = "token.keycloak.url";
        public static final String REALM = "token.keycloak.realm";
        public static final String CLIENT_ID = "token.keycloak.clientId";
        public static final String CLIENT_SECRET = "token.keycloak.clientSecret";
        public static final String USERNAME = "token.keycloak.username";
        public static final String PASSWORD = "token.keycloak.password";
        public static final String POOL_SIZE = "token.pool.size";
        public static final String CONNECTION_TIMEOUT_MS = "token.connection.timeoutMs";
        public static final String REQUEST_TIMEOUT_MS = "token.request.timeoutMs";
        public static final String VERIFY_SSL = "token.verifySsl";
        public static final String REFRESH_THRESHOLD_SECONDS = "token.refreshThresholdSeconds";

        private Properties() {
        }
    }

    /**
     * The base URL of the Keycloak server.
     * Example: https://localhost:1443
     */
    @Builder.Default
    String keycloakBaseUrl = "https://localhost:1443";

    /**
     * The Keycloak realm name.
     * Default: benchmark (matches docker-compose setup)
     */
    @Builder.Default
    String realm = "benchmark";

    /**
     * The client ID for token requests.
     * Default: benchmark-client (matches docker-compose setup)
     */
    @Builder.Default
    String clientId = "benchmark-client";

    /**
     * The client secret for token requests.
     * Default: benchmark-secret (should match realm configuration)
     */
    @Builder.Default
    String clientSecret = "benchmark-secret";

    /**
     * The username for token requests.
     * Default: benchmark-user (matches realm configuration)
     */
    @Builder.Default
    String username = "benchmark-user";

    /**
     * The password for token requests.
     * Default: benchmark-password (matches realm configuration)
     */
    @Builder.Default
    String password = "benchmark-password";

    /**
     * Number of tokens to fetch and cache for rotation.
     * This should be configured to achieve approximately 10% cache hit ratio
     * based on the expected number of benchmark requests.
     * Default: 5000 (10x the default cache size of 500)
     */
    @Builder.Default
    int tokenPoolSize = 100;

    /**
     * Connection timeout in milliseconds for Keycloak requests.
     * Default: 5000ms (5 seconds)
     */
    @Builder.Default
    int connectionTimeoutMs = 5000;

    /**
     * Request timeout in milliseconds for Keycloak requests.
     * Default: 10000ms (10 seconds)
     */
    @Builder.Default
    int requestTimeoutMs = 10000;

    /**
     * Whether to verify SSL certificates when connecting to Keycloak.
     * Should be false for local testing with self-signed certificates.
     * Default: false
     */
    @Builder.Default
    boolean verifySsl = false;

    /**
     * Token refresh threshold - tokens will be refreshed when they have less
     * than this many seconds left before expiration.
     * Default: 180 seconds (3 minutes) - safe margin for 15-minute tokens
     */
    @Builder.Default
    int tokenRefreshThresholdSeconds = 180;

    /**
     * Creates a configuration from system properties with defaults.
     * All properties can be overridden via system properties or Maven -D arguments.
     * 
     * @return TokenRepositoryConfig with values from properties or defaults
     */
    public static TokenRepositoryConfig fromProperties() {
        // Check both token.keycloak.url and keycloak.url for compatibility
        // Prefer token.keycloak.url if both are set
        String keycloakUrl = System.getProperty(Properties.KEYCLOAK_URL);
        if (keycloakUrl == null) {
            keycloakUrl = System.getProperty("keycloak.url", "https://localhost:1443");
        }
        
        return TokenRepositoryConfig.builder()
                .keycloakBaseUrl(keycloakUrl)
                .realm(System.getProperty(Properties.REALM, "benchmark"))
                .clientId(System.getProperty(Properties.CLIENT_ID, "benchmark-client"))
                .clientSecret(System.getProperty(Properties.CLIENT_SECRET, "benchmark-secret"))
                .username(System.getProperty(Properties.USERNAME, "benchmark-user"))
                .password(System.getProperty(Properties.PASSWORD, "benchmark-password"))
                .tokenPoolSize(Integer.parseInt(System.getProperty(Properties.POOL_SIZE, "100")))
                .connectionTimeoutMs(Integer.parseInt(System.getProperty(Properties.CONNECTION_TIMEOUT_MS, "5000")))
                .requestTimeoutMs(Integer.parseInt(System.getProperty(Properties.REQUEST_TIMEOUT_MS, "10000")))
                .verifySsl(Boolean.parseBoolean(System.getProperty(Properties.VERIFY_SSL, "false")))
                .tokenRefreshThresholdSeconds(Integer.parseInt(System.getProperty(Properties.REFRESH_THRESHOLD_SECONDS, "180")))
                .build();
    }
}