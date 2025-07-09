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
package de.cuioss.jwt.quarkus.producer;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.tools.base.Preconditions;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class MockTokenValidator extends TokenValidator {

    private static final IssuerConfig ISSUER_CONFIG = IssuerConfig.builder().issuerIdentifier("mock").jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks()).build();

    public MockTokenValidator() {
        super(ISSUER_CONFIG);
    }

    @Getter
    @Setter
    private boolean shouldFail = false;

    @Setter
    @Getter
    private AccessTokenContent accessTokenContent;

    @Override
    public @NonNull AccessTokenContent createAccessToken(@NonNull String tokenString) {
        if (shouldFail) {
            throw new TokenValidationException(SecurityEventCounter.EventType.TOKEN_EMPTY, "boom");
        }
        Preconditions.checkState(null != accessTokenContent, "Setting token is required");
        return accessTokenContent;
    }
}
