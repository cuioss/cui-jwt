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
package de.cuioss.jwt.validation.benchmark.standard;

import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.benchmark.BenchmarkMetricsAggregator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.jwt.validation.test.generator.ClaimControlParameter;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import io.jsonwebtoken.Jwts;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simplified error load benchmark - split from ErrorLoadBenchmark.
 * Contains only mixed token validation benchmarks (2 methods maximum).
 * Designed to eliminate JMH threading contention by removing @Param annotations.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Thread)
@SuppressWarnings("java:S112")
public class SimpleErrorLoadBenchmark extends de.cuioss.jwt.validation.benchmark.base.AbstractBenchmark {

    private static final String[] BENCHMARK_NAMES = {
        "validateMixedTokens0", "validateMixedTokens50"
    };
    
    // Pre-generated tokens for specific error scenarios
    private String validAccessToken;
    private String expiredToken;
    private String malformedToken;
    private String invalidSignatureToken;

    // Optimized token pools for mixed error testing
    private List<String> validTokens;
    private List<String> invalidTokens;

    // Reduced token count for faster setup
    private static final int TOKEN_COUNT = 20;

    @Override
    protected String[] getBenchmarkMethodNames() {
        return BENCHMARK_NAMES;
    }

    @Setup(Level.Trial)
    public void setup() {
        // Create base token holder and validator
        TestTokenHolder baseTokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig issuerConfig = baseTokenHolder.getIssuerConfig();
        tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();

        // Generate primary tokens for basic error scenarios
        validAccessToken = baseTokenHolder.getRawToken();
        expiredToken = createExpiredToken();
        malformedToken = "invalid.jwt.token";
        invalidSignatureToken = createInvalidSignatureToken(validAccessToken);

        // Pre-generate token pools for mixed scenarios
        validTokens = new ArrayList<>(TOKEN_COUNT);
        invalidTokens = new ArrayList<>(TOKEN_COUNT);

        // Generate valid token pool
        for (int i = 0; i < TOKEN_COUNT; i++) {
            TestTokenHolder validTokenHolder = TestTokenGenerators.accessTokens().next();
            validTokens.add(validTokenHolder.getRawToken());
        }

        // Generate invalid token pool (mixed error types)
        for (int i = 0; i < TOKEN_COUNT; i++) {
            if (i < TOKEN_COUNT / 3) {
                // Expired tokens
                invalidTokens.add(createExpiredToken());
            } else if (i < 2 * TOKEN_COUNT / 3) {
                // Invalid signature tokens
                invalidTokens.add(createInvalidSignatureToken(validAccessToken));
            } else {
                // Malformed tokens
                invalidTokens.add("malformed.token." + i);
            }
        }
        
        // Register benchmarks for metrics collection
        BenchmarkMetricsAggregator.registerBenchmarks(BENCHMARK_NAMES);
    }

    // ========== Simple Error Load Benchmarks ==========

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
    
    /**
     * Creates an expired JWT token for testing.
     */
    private String createExpiredToken() {
        Instant past = Instant.now().minusSeconds(3600);
        
        return Jwts.builder()
                .issuer("benchmark-issuer")
                .subject("benchmark-user")
                .audience().add("benchmark-client").and()
                .expiration(Date.from(past)) // Already expired
                .notBefore(Date.from(past.minusSeconds(60)))
                .issuedAt(Date.from(past.minusSeconds(120)))
                .id(UUID.randomUUID().toString())
                .signWith(Jwts.SIG.HS256.key().build())
                .compact();
    }
    
    /**
     * Creates a token with invalid signature for testing.
     */
    private String createInvalidSignatureToken(String validToken) {
        // Take the valid token and corrupt the signature
        String[] parts = validToken.split("\\.");
        if (parts.length == 3) {
            // Modify the signature part
            return parts[0] + "." + parts[1] + ".invalidSignature123";
        }
        return "invalid.signature.token";
    }
}