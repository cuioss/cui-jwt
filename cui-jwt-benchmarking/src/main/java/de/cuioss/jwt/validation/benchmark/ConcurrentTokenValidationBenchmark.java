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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for testing concurrent token validation performance.
 * <p>
 * This benchmark measures the throughput of token validation operations when executed
 * concurrently by multiple threads. It uses the maximum available threads on the system
 * to simulate high concurrency scenarios.
 * <p>
 * The benchmark setup creates a token validator and a valid access token once,
 * then reuses them across all benchmark iterations and threads to focus on measuring
 * the validation performance rather than token generation overhead.
 * <p>
 * JMH annotations configure the benchmark to:
 * <ul>
 *   <li>Share state across all benchmark threads ({@code @State(Scope.Benchmark)})</li>
 *   <li>Measure throughput in operations per second ({@code @BenchmarkMode(Mode.Throughput)})</li>
 *   <li>Run with 1 fork and 1 warmup fork</li>
 *   <li>Perform 5 warmup iterations of 1 second each</li>
 *   <li>Perform 5 measurement iterations of 1 second each</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ConcurrentTokenValidationBenchmark {

    /**
     * The token validator instance used for validating tokens in the benchmark.
     * Created once during setup and reused across all benchmark iterations.
     */
    private TokenValidator tokenValidator;

    /**
     * A valid access token string used for validation in the benchmark.
     * Created once during setup and reused across all benchmark iterations.
     */
    private String validAccessToken;

    /**
     * Sets up the benchmark environment.
     * <p>
     * This method is executed once before all benchmark iterations start.
     * It initializes the token validator and creates a valid access token
     * that will be reused across all benchmark iterations.
     * <p>
     * The setup process:
     * <ol>
     *   <li>Creates a token holder using TestTokenGenerators</li>
     *   <li>Extracts the issuer configuration from the token holder</li>
     *   <li>Initializes a token validator with the issuer configuration</li>
     *   <li>Extracts the raw token string for validation</li>
     * </ol>
     */
    @Setup(Level.Trial)
    public void setup() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig issuerConfig = tokenHolder.getIssuerConfig();
        tokenValidator = new TokenValidator(issuerConfig);
        validAccessToken = tokenHolder.getRawToken();
    }

    /**
     * Benchmark method that validates an access token concurrently.
     * <p>
     * This method is executed by multiple threads concurrently to measure
     * the throughput of token validation operations under high concurrency.
     * It uses the maximum number of threads available on the system to
     * simulate a high-load scenario.
     * <p>
     * The method attempts to create an access token from the raw token string
     * using the token validator. If validation fails unexpectedly, it throws
     * a RuntimeException with the original exception as the cause.
     *
     * @return the validated access token content
     * @throws RuntimeException if token validation fails unexpectedly
     */
    @Benchmark
    @SuppressWarnings({"cui-jwt-quarkus-deployment", "java:S112"}) // ok for testing
    public AccessTokenContent validateAccessTokenConcurrently() {
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected TokenValidationException during concurrent benchmark", e);
        }
    }

}
