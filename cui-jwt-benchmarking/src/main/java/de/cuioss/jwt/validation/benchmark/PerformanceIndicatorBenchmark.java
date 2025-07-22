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
 * Consolidated core validation benchmark that measures essential JWT validation performance metrics.
 * <p>
 * This benchmark combines the most critical performance measurements:
 * <ul>
 *   <li><strong>Average Time</strong>: Single-threaded validation latency</li>
 *   <li><strong>Throughput</strong>: Operations per second under concurrent load</li>
 *   <li><strong>Concurrent Validation</strong>: Multi-threaded validation performance</li>
 * </ul>
 * <p>
 * Performance expectations:
 * <ul>
 *   <li>Access token validation: &lt; 100 μs per operation</li>
 *   <li>Concurrent throughput: Linear scalability up to 8 threads</li>
 *   <li>Throughput: &gt; 10,000 operations/second</li>
 * </ul>
 * <p>
 * This benchmark is optimized for fast execution while retaining essential performance insights.
 * It replaces the functionality of TokenValidatorBenchmark, ConcurrentTokenValidationBenchmark,
 * and PerformanceIndicatorBenchmark with streamlined implementations.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class PerformanceIndicatorBenchmark {

    private TokenValidator tokenValidator;
    private String validAccessToken;

    /**
     * Shared token pool for reduced setup overhead.
     * Pre-generated tokens reduce benchmark setup time.
     */
    private static final int TOKEN_POOL_SIZE = 20;
    private String[] tokenPool;
    private int tokenIndex = 0;

    @Setup(Level.Trial)
    public void setup() {
        // Generate single issuer config and validator
        TestTokenHolder baseTokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig issuerConfig = baseTokenHolder.getIssuerConfig();
        tokenValidator = new TokenValidator(issuerConfig);

        // Generate primary validation token
        validAccessToken = baseTokenHolder.getRawToken();

        // Generate token pool for concurrent benchmarks
        tokenPool = new String[TOKEN_POOL_SIZE];
        for (int i = 0; i < TOKEN_POOL_SIZE; i++) {
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            tokenPool[i] = tokenHolder.getRawToken();
        }
    }

    /**
     * Measures average validation time for single-threaded token validation.
     * <p>
     * This benchmark measures the baseline latency for validating a single
     * access token without concurrent load. Lower values indicate better performance.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTime() {
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during average time measurement", e);
        }
    }

    /**
     * Measures token validation throughput under concurrent load.
     * <p>
     * This benchmark uses 8 threads to measure how many token validations
     * can be performed per second under concurrent load. Higher values indicate better throughput.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughput() {
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during throughput measurement", e);
        }
    }

    /**
     * Measures concurrent validation performance with token rotation.
     * <p>
     * This benchmark tests validation performance using a pool of different tokens
     * to simulate real-world scenarios where multiple different tokens are validated
     * concurrently. This provides insight into caching behavior and token diversity impact.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureConcurrentValidation() {
        try {
            // Rotate through token pool to simulate different tokens
            String token = tokenPool[tokenIndex++ % TOKEN_POOL_SIZE];
            return tokenValidator.createAccessToken(token);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during concurrent validation", e);
        }
    }

}