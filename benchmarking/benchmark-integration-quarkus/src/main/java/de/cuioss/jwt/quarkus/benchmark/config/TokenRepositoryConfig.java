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
package de.cuioss.jwt.quarkus.benchmark.config;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for TokenRepository to connect to Keycloak and fetch tokens
 * for benchmark testing.
 *
 * @since 1.0
 */
@Value
@Builder
public class TokenRepositoryConfig {

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
    int tokenPoolSize = 500;

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
}