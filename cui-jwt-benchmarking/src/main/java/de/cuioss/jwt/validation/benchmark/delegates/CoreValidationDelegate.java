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
package de.cuioss.jwt.validation.benchmark.delegates;

import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.benchmark.TokenRepository;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delegate for core validation benchmarks including average time, throughput, and concurrent validation.
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
public class CoreValidationDelegate extends BenchmarkDelegate {

    private final AtomicInteger tokenIndex = new AtomicInteger(0);

    public CoreValidationDelegate(TokenValidator tokenValidator, TokenRepository tokenRepository) {
        super(tokenValidator, tokenRepository);
    }

    /**
     * Performs a single token validation using the primary token.
     * Used for average time and throughput benchmarks.
     * 
     * @return the validated access token content
     * @throws RuntimeException if validation fails unexpectedly
     */
    public AccessTokenContent validatePrimaryToken() {
        try {
            String token = tokenRepository.getPrimaryToken();
            return validateToken(token);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during primary token validation", e);
        }
    }

    /**
     * Performs concurrent validation with token rotation.
     * Rotates through the token pool to simulate different tokens being validated.
     * 
     * @return the validated access token content
     * @throws RuntimeException if validation fails unexpectedly
     */
    public AccessTokenContent validateWithRotation() {
        try {
            // Rotate through token pool to simulate different tokens
            String token = tokenRepository.getToken(tokenIndex.getAndIncrement());
            return validateToken(token);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during concurrent validation", e);
        }
    }

    /**
     * Gets the current token for the given operation type.
     * 
     * @param operationType the type of operation (e.g., "primary", "rotation")
     * @return the token to use for validation
     */
    public String getCurrentToken(String operationType) {
        if ("rotation".equals(operationType)) {
            return tokenRepository.getToken(tokenIndex.get());
        }
        return tokenRepository.getPrimaryToken();
    }
}