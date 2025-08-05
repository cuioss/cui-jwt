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
package de.cuioss.jwt.quarkus.benchmark;

import de.cuioss.jwt.quarkus.benchmark.config.TokenRepositoryConfig;
import de.cuioss.jwt.quarkus.benchmark.repository.TokenRepository;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.annotations.*;

import java.net.http.HttpRequest;

/**
 * Abstract base class for Quarkus integration benchmarks that require JWT authentication.
 * Extends {@link AbstractBaseBenchmark} and adds token repository support.
 *
 * <p>Use this class for benchmarks that need authenticated requests.
 * For benchmarks that don't require authentication, use {@link AbstractBaseBenchmark} directly.</p>
 *
 * @since 1.0
 */
@State(Scope.Benchmark)
public abstract class AbstractIntegrationBenchmark extends AbstractBaseBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(AbstractIntegrationBenchmark.class);

    protected String keycloakUrl;
    protected TokenRepository tokenRepository;

    /**
     * Setup method called once before all benchmark iterations.
     * Extends parent setup and initializes or reuses the shared token repository.
     */
    @Override
    @Setup(Level.Trial)
    public void setupBenchmark() {
        // Call parent setup first
        super.setupBenchmark();

        LOGGER.info("Setting up integration benchmark with token repository");

        // Get Keycloak configuration
        keycloakUrl = BenchmarkOptionsHelper.getKeycloakUrl("https://localhost:1443");

        // Initialize token repository using shared instance if available
        initializeTokenRepository();

        LOGGER.info("Integration benchmark setup completed");
    }

    /**
     * Initializes the token repository, using the shared instance if available
     * or creating a new one if needed (for forked JVM processes).
     */
    private void initializeTokenRepository() {
        if (TokenRepository.isSharedInstanceInitialized()) {
            LOGGER.debug("Using existing shared TokenRepository instance");
            tokenRepository = TokenRepository.getSharedInstance();
        } else {
            LOGGER.debug("Initializing new TokenRepository for forked benchmark process");

            TokenRepositoryConfig config = TokenRepositoryConfig.builder()
                    .keycloakBaseUrl(keycloakUrl)
                    .realm("benchmark")
                    .clientId("benchmark-client")
                    .clientSecret("benchmark-secret")
                    .username("benchmark-user")
                    .password("benchmark-password")
                    .connectionTimeoutMs(5000)
                    .requestTimeoutMs(10000)
                    .verifySsl(false)
                    .tokenRefreshThresholdSeconds(300)
                    .build();

            TokenRepository.initializeSharedInstance(config);
            tokenRepository = TokenRepository.getSharedInstance();
        }

        LOGGER.info("Token repository initialized with {} tokens", tokenRepository.getTokenPoolSize());
    }


    /**
     * Creates an authenticated HTTP request builder with a JWT token.
     *
     * @param path the URI path to send the request to
     * @param token the JWT token to use for authorization
     * @return configured request builder with Authorization header
     */
    protected HttpRequest.Builder createAuthenticatedRequest(String path, String token) {
        return createBaseRequest(path)
                .header("Authorization", "Bearer " + token);
    }

    /**
     * Creates an authenticated HTTP request builder using the next token from the pool.
     *
     * @param path the URI path to send the request to
     * @return configured request builder with Authorization header
     */
    protected HttpRequest.Builder createAuthenticatedRequest(String path) {
        return createAuthenticatedRequest(path, tokenRepository.getNextToken());
    }

}