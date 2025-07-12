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
 * Benchmark that measures system performance under high rates of validation errors.
 * This benchmark simulates scenarios with varying percentages of invalid tokens
 * and measures the impact on overall system throughput.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ErrorLoadBenchmark {

    public static final String TEST_SUBJECT = "test-subject-";
    private TokenValidator tokenValidator;
    private List<String> validTokens;
    private List<String> invalidTokens;

    @Param({"0", "10", "50", "90", "100"})
    private int errorPercentage;

    // Number of tokens to generate for each category
    private static final int TOKEN_COUNT = 100;

    @Setup
    public void setup() {
        // Create a base token holder using TestTokenGenerators
        TestTokenHolder baseTokenHolder = TestTokenGenerators.accessTokens().next();

        // Get the issuer config from the token holder
        IssuerConfig issuerConfig = baseTokenHolder.getIssuerConfig();

        // Create a token validator with the issuer config
        tokenValidator = new TokenValidator(issuerConfig);

        // Generate Token Lists
        validTokens = new ArrayList<>(TOKEN_COUNT);
        invalidTokens = new ArrayList<>(TOKEN_COUNT);

        // Generate valid tokens
        for (int i = 0; i < TOKEN_COUNT; i++) {
            // Create a new token holder for each valid token
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            // Add a unique subject to each token
            tokenHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(TEST_SUBJECT + i));
            validTokens.add(tokenHolder.getRawToken());
        }

        // Generate different types of invalid tokens
        for (int i = 0; i < TOKEN_COUNT; i++) {
            // Distribute invalid tokens across different error types
            int errorType = i % 5;
            String invalidToken;

            switch (errorType) {
                case 0: // Expired token
                    ClaimControlParameter expiredParams = ClaimControlParameter.builder()
                            .expiredToken(true)
                            .build();
                    TestTokenHolder expiredTokenHolder = new TestTokenHolder(baseTokenHolder.getTokenType(), expiredParams);
                    expiredTokenHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(TEST_SUBJECT + i));
                    invalidToken = expiredTokenHolder.getRawToken();
                    break;

                case 1: // Wrong issuer
                    TestTokenHolder wrongIssuerTokenHolder = baseTokenHolder.regenerateClaims()
                            .withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("rogue-issuer-" + i))
                            .withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(TEST_SUBJECT + i));
                    invalidToken = wrongIssuerTokenHolder.getRawToken();
                    break;

                case 2: // Wrong audience
                    TestTokenHolder wrongAudienceTokenHolder = baseTokenHolder.regenerateClaims()
                            .withClaim(ClaimName.AUDIENCE.getName(), ClaimValue.forList("rogue-audience-" + i, List.of("rogue-audience-" + i)))
                            .withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(TEST_SUBJECT + i));
                    invalidToken = wrongAudienceTokenHolder.getRawToken();
                    break;

                case 3: // Invalid signature
                    TestTokenHolder invalidSignatureTokenHolder = baseTokenHolder.regenerateClaims()
                            .withKeyId("invalid-key-id-" + i)
                            .withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(TEST_SUBJECT + i));
                    invalidToken = invalidSignatureTokenHolder.getRawToken();
                    break;

                case 4: // Malformed token
                default:
                    invalidToken = "this.is.not.a.valid.jwt-" + i;
                    break;
            }

            invalidTokens.add(invalidToken);
        }
    }

    /**
     * Selects a token based on the configured error percentage.
     *
     * @return A token string that has the probability of being invalid
     * according to the errorPercentage parameter
     */
    @SuppressWarnings("java:S2245") // owolff: ok for test purposes
    private String selectToken() {
        // If errorPercentage is 0, always return valid token
        if (errorPercentage == 0) {
            int index = ThreadLocalRandom.current().nextInt(validTokens.size());
            return validTokens.get(index);
        }

        // If errorPercentage is 100, always return invalid token
        if (errorPercentage == 100) {
            int index = ThreadLocalRandom.current().nextInt(invalidTokens.size());
            return invalidTokens.get(index);
        }

        // Otherwise, select based on probability
        int randomValue = ThreadLocalRandom.current().nextInt(100);
        if (randomValue < errorPercentage) {
            // Return an invalid token
            int index = ThreadLocalRandom.current().nextInt(invalidTokens.size());
            return invalidTokens.get(index);
        } else {
            // Return a valid token
            int index = ThreadLocalRandom.current().nextInt(validTokens.size());
            return validTokens.get(index);
        }
    }

    @Benchmark
    public Object validateMixedTokens(Blackhole blackhole) {
        // Select token based on current iteration and errorPercentage
        String token = selectToken();
        try {
            AccessTokenContent result = tokenValidator.createAccessToken(token);
            blackhole.consume(result);
            return result;
        } catch (TokenValidationException e) {
            blackhole.consume(e);
            return e;
        }
    }
}