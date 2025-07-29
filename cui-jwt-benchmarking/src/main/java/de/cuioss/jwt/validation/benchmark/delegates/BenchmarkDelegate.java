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

/**
 * Base class for benchmark delegates that encapsulate the core benchmark logic.
 * This allows the same logic to be used by both regular and JFR-instrumented benchmarks.
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
public abstract class BenchmarkDelegate {

    protected final TokenValidator tokenValidator;
    protected final TokenRepository tokenRepository;

    protected BenchmarkDelegate(TokenValidator tokenValidator, TokenRepository tokenRepository) {
        this.tokenValidator = tokenValidator;
        this.tokenRepository = tokenRepository;
    }

    /**
     * Validates a token and returns the result.
     * 
     * @param token the token to validate
     * @return the validated access token content
     * @throws TokenValidationException if validation fails
     */
    protected AccessTokenContent validateToken(String token) throws TokenValidationException {
        return tokenValidator.createAccessToken(token);
    }

    /**
     * Gets metadata about a token for instrumentation purposes.
     * 
     * @param token the token to get metadata for
     * @return token metadata containing size and issuer information
     */
    public TokenMetadata getTokenMetadata(String token) {
        TokenRepository.TokenMetadata repoMetadata = tokenRepository.getTokenMetadata(token);
        return new TokenMetadata(
                token.length(),
                repoMetadata != null ? repoMetadata.getIssuerIdentifier() : "unknown"
        );
    }

    /**
     * Simple metadata class for token information.
     */
    public static class TokenMetadata {
        public final int tokenSize;
        public final String issuer;

        public TokenMetadata(int tokenSize, String issuer) {
            this.tokenSize = tokenSize;
            this.issuer = issuer;
        }
    }
}