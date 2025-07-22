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
 * ES256 validation pipeline benchmark to reproduce ConcurrentHashMap contention issues.
 * 
 * <p>This benchmark specifically targets ES256 (ECDSA with P-256) validation performance
 * under high concurrent load (200 threads configured in pom.xml) to reproduce the contention
 * issues identified in JFR profiling:</p>
 * 
 * <p><strong>JFR Analysis Results:</strong></p>
 * <ul>
 *   <li>ConcurrentHashMap contention in JWKSKeyLoader.keyInfoMap:353 (avg 24.3ms delay)</li>
 *   <li>ES256 showing 3x worse performance than RS256 (587ms overhead vs 170ms)</li>
 *   <li>23 contention events recorded during integration test</li>
 *   <li>Contention only visible under high thread count (200 threads)</li>
 * </ul>
 * 
 * <p><strong>Benchmark Design:</strong></p>
 * <ul>
 *   <li>Uses single shared TokenValidator instance to reproduce keyInfoMap contention</li>
 *   <li>200 concurrent threads configured globally in pom.xml</li>
 *   <li>Token pool with different key IDs to stress ConcurrentHashMap buckets</li>
 *   <li>Comparison with RS256 to show algorithm-specific contention patterns</li>
 * </ul>
 * 
 * <p><strong>Expected Results:</strong></p>
 * <ul>
 *   <li>Single-thread ES256: ~2-5ms (baseline without contention)</li>
 *   <li>200-thread ES256: >400ms P95 (reproducing JFR contention)</li>
 *   <li>RS256 comparison: Better scaling under same thread count</li>
 * </ul>
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class ES256ValidationPipelineBenchmark {

    // Single shared validator instance - critical for reproducing contention
    private TokenValidator sharedEs256Validator;
    private TokenValidator sharedRs256Validator;

    private String es256AccessToken;
    private String rs256AccessToken;

    /**
     * Token pools with different key IDs to stress ConcurrentHashMap.
     * Using smaller pool size (5) to increase contention on same keys.
     */
    private static final int TOKEN_POOL_SIZE = 5;
    private String[] es256TokenPool;
    private String[] rs256TokenPool;
    private int tokenIndex = 0;

    @Setup(Level.Trial)
    public void setup() {
        // Setup ES256 validation pipeline with shared validator
        TestTokenHolder baseEs256Token = TestTokenGenerators.accessTokens().next()
                .withES256IeeeP1363Format();
        IssuerConfig es256IssuerConfig = baseEs256Token.getIssuerConfig();

        // Create single shared validator - critical for contention
        sharedEs256Validator = new TokenValidator(es256IssuerConfig);
        es256AccessToken = baseEs256Token.getRawToken();

        // Generate ES256 token pool
        es256TokenPool = new String[TOKEN_POOL_SIZE];
        for (int i = 0; i < TOKEN_POOL_SIZE; i++) {
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next()
                    .withES256IeeeP1363Format();
            es256TokenPool[i] = tokenHolder.getRawToken();
        }

        // Setup RS256 for comparison
        TestTokenHolder baseRs256Token = TestTokenGenerators.accessTokens().next();
        IssuerConfig rs256IssuerConfig = baseRs256Token.getIssuerConfig();
        sharedRs256Validator = new TokenValidator(rs256IssuerConfig);
        rs256AccessToken = baseRs256Token.getRawToken();

        // Generate RS256 token pool
        rs256TokenPool = new String[TOKEN_POOL_SIZE];
        for (int i = 0; i < TOKEN_POOL_SIZE; i++) {
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            rs256TokenPool[i] = tokenHolder.getRawToken();
        }
    }


    /**
     * Baseline: Single-threaded ES256 validation performance.
     * <p>
     * Expected: ~2-5ms without contention
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureES256SingleThread() {
        try {
            return sharedEs256Validator.createAccessToken(es256AccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("ES256 validation failure in single thread", e);
        }
    }

    /**
     * Reproduces contention: ES256 validation with 200 concurrent threads.
     * <p>
     * Uses the global thread configuration (200 threads) from pom.xml.
     * Expected: High latency due to ConcurrentHashMap contention on JWKSKeyLoader.keyInfoMap.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureES256Throughput() {
        try {
            return sharedEs256Validator.createAccessToken(es256AccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("ES256 validation failure in throughput test", e);
        }
    }

    /**
     * Stress test: ES256 validation with token rotation.
     * <p>
     * Rotates through tokens with different key IDs to maximize
     * ConcurrentHashMap contention on different hash buckets.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureES256ConcurrentWithRotation() {
        try {
            String token = es256TokenPool[tokenIndex++ % TOKEN_POOL_SIZE];
            return sharedEs256Validator.createAccessToken(token);
        } catch (TokenValidationException e) {
            throw new RuntimeException("ES256 validation failure in rotation test", e);
        }
    }

    /**
     * Comparison baseline: RS256 throughput with same setup.
     * <p>
     * Shows that RS256 doesn't suffer from the same contention issues.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureRS256Throughput() {
        try {
            return sharedRs256Validator.createAccessToken(rs256AccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("RS256 validation failure in throughput test", e);
        }
    }

    /**
     * Comparison: RS256 with token rotation.
     * <p>
     * Shows RS256 scaling behavior under same conditions.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureRS256ConcurrentWithRotation() {
        try {
            String token = rs256TokenPool[tokenIndex++ % TOKEN_POOL_SIZE];
            return sharedRs256Validator.createAccessToken(token);
        } catch (TokenValidationException e) {
            throw new RuntimeException("RS256 validation failure in rotation test", e);
        }
    }
}