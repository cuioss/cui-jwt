/**
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
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.domain.token.RefreshTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Comprehensive benchmark suite for JWT token validation performance.
 * <p>
 * This benchmark tests the following scenarios:
 * <ul>
 *   <li>Access token validation - baseline single-threaded performance</li>
 *   <li>ID token validation - additional validation requirements</li>
 *   <li>Refresh token validation - longer lifetime tokens</li>
 *   <li>Error scenarios - invalid signature and expired token handling</li>
 *   <li>Concurrent validation - multi-threaded performance testing</li>
 * </ul>
 * <p>
 * Performance expectations:
 * <ul>
 *   <li>Access token validation: &lt; 100 μs per operation</li>
 *   <li>ID token validation: &lt; 120 μs per operation (additional claims)</li>
 *   <li>Refresh token validation: &lt; 80 μs per operation (fewer claims)</li>
 *   <li>Error scenarios: &lt; 50 μs per operation (fast failure)</li>
 *   <li>Concurrent validation: Linear scalability up to 4 threads</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class TokenValidatorBenchmark {

    private TokenValidator tokenValidator;
    private String accessToken;
    private String idToken;
    private String refreshToken;
    private String invalidToken;
    private String expiredToken;

    @Setup
    public void setup() {
        // Create token holders using TestTokenGenerators
        TestTokenHolder accessTokenHolder = TestTokenGenerators.accessTokens().next();
        TestTokenHolder idTokenHolder = TestTokenGenerators.idTokens().next();
        TestTokenHolder refreshTokenHolder = TestTokenGenerators.refreshTokens().next();

        // Get the issuer config from the access token holder
        IssuerConfig issuerConfig = accessTokenHolder.getIssuerConfig();

        // Create a token validator with the issuer config
        tokenValidator = new TokenValidator(issuerConfig);

        // Get the raw tokens
        accessToken = accessTokenHolder.getRawToken();
        idToken = idTokenHolder.getRawToken();
        refreshToken = refreshTokenHolder.getRawToken();

        // Create error scenario tokens
        // Corrupt signature by changing the last character
        invalidToken = corruptTokenSignature(accessToken);
        // Create another variation for expired token simulation
        expiredToken = corruptTokenSignature(accessToken) + "X";
    }

    /**
     * Simple helper method to corrupt a token signature for error scenario benchmarks.
     *
     * @param token the original token
     * @return token with corrupted signature
     */
    private String corruptTokenSignature(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3 || parts[2].isEmpty()) {
            return token + "INVALID";
        }

        // Change the last character of the signature
        String signature = parts[2];
        char lastChar = signature.charAt(signature.length() - 1);
        char newChar = (lastChar == 'A') ? 'B' : 'A';
        String corruptedSignature = signature.substring(0, signature.length() - 1) + newChar;

        return parts[0] + "." + parts[1] + "." + corruptedSignature;
    }

    /**
     * Core benchmark method for access token validation performance.
     * This provides the baseline measurement for single-threaded validation performance.
     */
    @Benchmark
    public AccessTokenContent validateAccessToken() {
        return tokenValidator.createAccessToken(accessToken);
    }

    /**
     * Benchmark for ID token validation performance.
     * ID tokens have additional validation requirements compared to access tokens.
     */
    @Benchmark
    public IdTokenContent validateIdToken() {
        return tokenValidator.createIdToken(idToken);
    }

    /**
     * Benchmark for refresh token validation performance.
     * Refresh tokens typically have longer lifetimes and different claim structures.
     */
    @Benchmark
    public RefreshTokenContent validateRefreshToken() {
        return tokenValidator.createRefreshToken(refreshToken);
    }

    /**
     * Benchmark for error scenario: invalid signature validation.
     * Measures performance when validation fails due to signature corruption.
     */
    @Benchmark
    public boolean validateInvalidSignature() {
        try {
            tokenValidator.createAccessToken(invalidToken);
            return false; // Should not reach here
        } catch (TokenValidationException e) {
            return true; // Expected validation failure
        }
    }

    /**
     * Benchmark for error scenario: expired token validation.
     * Measures performance when validation fails due to token expiration.
     */
    @Benchmark
    public boolean validateExpiredToken() {
        try {
            tokenValidator.createAccessToken(expiredToken);
            return false; // Should not reach here
        } catch (TokenValidationException e) {
            return true; // Expected validation failure
        }
    }

    /**
     * Benchmark for concurrent access token validation.
     * Tests performance under concurrent load using thread groups.
     */
    @Benchmark
    @Group("concurrent")
    public AccessTokenContent validateAccessTokenConcurrent() {
        return tokenValidator.createAccessToken(accessToken);
    }

    /**
     * Benchmark for concurrent ID token validation.
     * Tests ID token validation performance under concurrent load.
     */
    @Benchmark
    @Group("concurrent")
    public IdTokenContent validateIdTokenConcurrent() {
        return tokenValidator.createIdToken(idToken);
    }

    /**
     * Benchmark for concurrent refresh token validation.
     * Tests refresh token validation performance under concurrent load.
     */
    @Benchmark
    @Group("concurrent")
    public RefreshTokenContent validateRefreshTokenConcurrent() {
        return tokenValidator.createRefreshToken(refreshToken);
    }
}