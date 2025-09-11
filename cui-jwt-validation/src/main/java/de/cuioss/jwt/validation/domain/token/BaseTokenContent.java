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
package de.cuioss.jwt.validation.domain.token;

import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.Map;

/**
 * Base implementation of {@link TokenContent}.
 * <p>
 * This abstract class provides common functionality for token content implementations,
 * including storage of claims, the raw token string, and token type. It serves as
 * the foundation for specific token type implementations like:
 * <ul>
 *   <li>{@link AccessTokenContent} - For OAuth2/OIDC access tokens</li>
 *   <li>{@link IdTokenContent} - For OIDC ID tokens</li>
 *   <li>{@link RefreshTokenContent} - For OAuth2 refresh tokens</li>
 * </ul>
 * <p>
 * The class is immutable and thread-safe, implementing equality and string representation
 * via Lombok's {@code @EqualsAndHashCode} and {@code @ToString}.
 * <p>
 * For more details on token structure, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#token-structure">Token Structure</a>
 * specification.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ToString
@EqualsAndHashCode
@SuperBuilder
public abstract class BaseTokenContent implements TokenContent {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    @NonNull
    private final Map<String, ClaimValue> claims;

    @Getter
    @NonNull
    private final String rawToken;

    @Getter
    @NonNull
    private final TokenType tokenType;

    /**
     * Gets the token claims.
     *
     * @return the token claims map
     */
    @Override
    @NonNull
    public Map<String, ClaimValue> getClaims() {
        return claims;
    }

    /**
     * Gets the raw token string.
     *
     * @return the raw token string
     */
    @Override
    @NonNull
    public String getRawToken() {
        return rawToken;
    }

    /**
     * Gets the token type.
     *
     * @return the token type
     */
    @Override
    @NonNull
    public TokenType getTokenType() {
        return tokenType;
    }

    /**
     * Constructor for BaseTokenContent.
     *
     * @param claims    the token claims
     * @param rawToken  the raw token string
     * @param tokenType the token type
     */
    protected BaseTokenContent(@NonNull Map<String, ClaimValue> claims, @NonNull String rawToken, @NonNull TokenType tokenType) {
        this.claims = claims;
        this.rawToken = rawToken;
        this.tokenType = tokenType;
    }
}
