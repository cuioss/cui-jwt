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
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.ClaimControlParameter;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Optimized benchmark for measuring error scenario performance with reduced execution time.
 * <p>
 * This benchmark focuses on the most critical error scenarios:
 * <ul>
 *   <li><strong>Basic Error Scenarios</strong>: Expired, malformed, invalid signature</li>
 *   <li><strong>Mixed Error Load</strong>: Baseline (0% errors) and balanced (50% errors)</li>
 * </ul>
 * <p>
 * Optimizations applied:
 * <ul>
 *   <li>Reduced token count from 100 to 20 per category</li>
 *   <li>Reduced error percentages from 5 to 2 variants (0%, 50%)</li>
 *   <li>Streamlined error types to 3 essential categories</li>
 *   <li>Shared token generation setup</li>
 * </ul>
 * <p>
 * This benchmark replaces the functionality of FailureScenarioBenchmark and ErrorLoadBenchmark
 * with faster execution while maintaining essential error handling performance insights.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ErrorLoadBenchmark {

    private TokenValidator tokenValidator;
    private String validAccessToken;
    private String expiredToken;
    private String malformedToken;
    private String invalidSignatureToken;

    // Optimized token pools for mixed error testing
    private List<String> validTokens;
    private List<String> invalidTokens;

    // Reduced token count for faster setup
    private static final int TOKEN_COUNT = 20;

    @Setup(Level.Trial)
    public void setup() {
        // Create base token holder and validator
        TestTokenHolder baseTokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig issuerConfig = baseTokenHolder.getIssuerConfig();
        tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();

        // Generate primary tokens for basic error scenarios
        validAccessToken = baseTokenHolder.getRawToken();

        // Generate expired token
        ClaimControlParameter expiredParams = ClaimControlParameter.builder()
                .expiredToken(true)
                .build();
        expiredToken = new TestTokenHolder(baseTokenHolder.getTokenType(), expiredParams).getRawToken();

        // Generate malformed token
        malformedToken = "invalid.jwt.token";

        // Generate invalid signature token
        TestTokenHolder invalidSignatureHolder = baseTokenHolder.regenerateClaims()
                .withKeyId("invalid-key-id");
        invalidSignatureToken = invalidSignatureHolder.getRawToken();

        // Generate token pools for mixed error testing
        setupTokenPools(baseTokenHolder);
    }

    /**
     * Sets up token pools for mixed error testing with optimized token count.
     */
    private void setupTokenPools(TestTokenHolder baseTokenHolder) {
        validTokens = new ArrayList<>(TOKEN_COUNT);
        invalidTokens = new ArrayList<>(TOKEN_COUNT);

        // Generate valid tokens
        for (int i = 0; i < TOKEN_COUNT; i++) {
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            tokenHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString("test-subject-" + i));
            validTokens.add(tokenHolder.getRawToken());
        }

        // Generate invalid tokens (3 types distributed evenly)
        for (int i = 0; i < TOKEN_COUNT; i++) {
            String invalidToken;
            int errorType = i % 3;

            switch (errorType) {
                case 0: // Expired token
                    ClaimControlParameter expiredParams = ClaimControlParameter.builder()
                            .expiredToken(true)
                            .build();
                    TestTokenHolder expiredHolder = new TestTokenHolder(baseTokenHolder.getTokenType(), expiredParams);
                    expiredHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString("test-subject-" + i));
                    invalidToken = expiredHolder.getRawToken();
                    break;

                case 1: // Invalid signature
                    TestTokenHolder invalidSigHolder = baseTokenHolder.regenerateClaims()
                            .withKeyId("invalid-key-id-" + i)
                            .withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString("test-subject-" + i));
                    invalidToken = invalidSigHolder.getRawToken();
                    break;

                case 2: // Malformed token
                default:
                    invalidToken = "malformed.token." + i;
                    break;
            }

            invalidTokens.add(invalidToken);
        }
    }

    /**
     * Benchmarks validation of valid tokens (baseline performance).
     */
    @Benchmark
    public AccessTokenContent validateValidToken() {
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure for valid token", e);
        }
    }

    /**
     * Benchmarks validation of expired tokens.
     */
    @Benchmark
    public Object validateExpiredToken() {
        try {
            return tokenValidator.createAccessToken(expiredToken);
        } catch (TokenValidationException e) {
            return e; // Expected failure
        }
    }

    /**
     * Benchmarks validation of malformed tokens.
     */
    @Benchmark
    public Object validateMalformedToken() {
        try {
            return tokenValidator.createAccessToken(malformedToken);
        } catch (TokenValidationException e) {
            return e; // Expected failure
        }
    }

    /**
     * Benchmarks validation of tokens with invalid signatures.
     */
    @Benchmark
    public Object validateInvalidSignatureToken() {
        try {
            return tokenValidator.createAccessToken(invalidSignatureToken);
        } catch (TokenValidationException e) {
            return e; // Expected failure
        }
    }

    /**
     * Benchmarks mixed error load scenarios with 0% error rate (baseline performance).
     */
    @Benchmark
    public Object validateMixedTokens0(Blackhole blackhole) {
        String token = selectValidToken();
        try {
            AccessTokenContent result = tokenValidator.createAccessToken(token);
            blackhole.consume(result);
            return result;
        } catch (TokenValidationException e) {
            blackhole.consume(e);
            return e;
        }
    }

    /**
     * Benchmarks mixed error load scenarios with 50% error rate (balanced mix).
     */
    @Benchmark
    public Object validateMixedTokens50(Blackhole blackhole) {
        String token = selectMixedToken();
        try {
            AccessTokenContent result = tokenValidator.createAccessToken(token);
            blackhole.consume(result);
            return result;
        } catch (TokenValidationException e) {
            blackhole.consume(e);
            return e;
        }
    }

    /**
     * Selects a valid token for 0% error rate benchmarks.
     */
    @SuppressWarnings("java:S2245") // Random usage is acceptable for benchmarks
    private String selectValidToken() {
        int index = ThreadLocalRandom.current().nextInt(validTokens.size());
        return validTokens.get(index);
    }

    /**
     * Selects a token with 50% error rate for mixed error benchmarks.
     */
    @SuppressWarnings("java:S2245") // Random usage is acceptable for benchmarks
    private String selectMixedToken() {
        int randomValue = ThreadLocalRandom.current().nextInt(100);
        if (randomValue < 50) {
            int index = ThreadLocalRandom.current().nextInt(invalidTokens.size());
            return invalidTokens.get(index);
        } else {
            int index = ThreadLocalRandom.current().nextInt(validTokens.size());
            return validTokens.get(index);
        }
    }
}