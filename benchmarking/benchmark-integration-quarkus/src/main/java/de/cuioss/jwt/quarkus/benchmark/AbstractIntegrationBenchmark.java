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

import de.cuioss.benchmarking.common.repository.TokenRepository;
import de.cuioss.benchmarking.common.repository.TokenRepositoryConfig;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

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

    protected TokenRepository tokenRepository;

    /**
     * Setup method called once before all benchmark iterations.
     * Extends parent setup and initializes or reuses the shared token repository.
     */
    @Override @Setup(Level.Trial) public void setupBenchmark() {
        // Call parent setup first
        super.setupBenchmark();

        LOGGER.info("Setting up integration benchmark with token repository");

        // Initialize token repository using shared instance if available
        initializeTokenRepository();

        LOGGER.info("Integration benchmark setup completed");
    }

    /**
     * Initializes the token repository with property-based configuration.
     */
    private void initializeTokenRepository() {
        TokenRepositoryConfig config = TokenRepositoryConfig.fromProperties();
        tokenRepository = new TokenRepository(config);
        
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