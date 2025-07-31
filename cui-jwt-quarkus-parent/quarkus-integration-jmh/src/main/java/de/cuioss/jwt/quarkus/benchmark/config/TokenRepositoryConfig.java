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
 * @author Generated
 * @since 1.0
 */
@Value
@Builder
public class TokenRepositoryConfig {

    /**
     * The base URL of the Keycloak server.
     * Example: http://localhost:8080
     */
    @Builder.Default
    String keycloakBaseUrl = "http://localhost:8080";

    /**
     * The Keycloak realm name.
     * Default: cuijwt-realm
     */
    @Builder.Default
    String realm = "cuijwt-realm";

    /**
     * The client ID for token requests.
     * Default: cuijwt-client
     */
    @Builder.Default
    String clientId = "cuijwt-client";

    /**
     * The client secret for token requests.
     * Default: client-secret (should be configurable in real deployments)
     */
    @Builder.Default
    String clientSecret = "client-secret";

    /**
     * The username for token requests.
     * Default: testuser
     */
    @Builder.Default
    String username = "testuser";

    /**
     * The password for token requests.
     * Default: testpass
     */
    @Builder.Default
    String password = "testpass";

    /**
     * Number of tokens to fetch and cache for rotation.
     * This should be configured to achieve approximately 10% cache hit ratio
     * based on the expected number of benchmark requests.
     * Default: 100
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
     * Default: 300 seconds (5 minutes)
     */
    @Builder.Default
    int tokenRefreshThresholdSeconds = 300;
}