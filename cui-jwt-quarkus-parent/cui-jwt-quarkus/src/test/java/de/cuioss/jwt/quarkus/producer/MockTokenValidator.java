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
package de.cuioss.jwt.quarkus.producer;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.domain.token.RefreshTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.tools.base.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class MockTokenValidator {

    private static final IssuerConfig ISSUER_CONFIG = IssuerConfig.builder().issuerIdentifier("mock").jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks()).build();

    private final TokenValidator delegate;

    public MockTokenValidator() {
        this.delegate = TokenValidator.builder()
                .issuerConfig(ISSUER_CONFIG)
                .build();
    }

    // Method to get the underlying TokenValidator for CDI
    public TokenValidator getDelegate() {
        return delegate;
    }

    @Getter
    @Setter
    private boolean shouldFail = false;

    @Setter
    @Getter
    private AccessTokenContent accessTokenContent;

    public @NonNull AccessTokenContent createAccessToken(@NonNull String tokenString) {
        if (shouldFail) {
            throw new TokenValidationException(SecurityEventCounter.EventType.TOKEN_EMPTY, "boom");
        }
        Preconditions.checkState(null != accessTokenContent, "Setting token is required");
        return accessTokenContent;
    }

    // Delegate methods to the actual TokenValidator
    public SecurityEventCounter getSecurityEventCounter() {
        return delegate.getSecurityEventCounter();
    }

    public TokenValidatorMonitor getPerformanceMonitor() {
        return delegate.getPerformanceMonitor();
    }

    // Other delegation methods as needed by tests
    public IdTokenContent createIdToken(@NonNull String tokenString) {
        return delegate.createIdToken(tokenString);
    }

    public RefreshTokenContent createRefreshToken(@NonNull String tokenString) {
        return delegate.createRefreshToken(tokenString);
    }
}
