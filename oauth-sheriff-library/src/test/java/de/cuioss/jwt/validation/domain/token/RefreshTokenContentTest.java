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
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.ClaimValueGenerator;
import de.cuioss.jwt.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RefreshTokenContent}.
 */
@EnableGeneratorController
@DisplayName("Tests RefreshTokenContent functionality")
class RefreshTokenContentTest implements ShouldHandleObjectContracts<RefreshTokenContent> {

    @ParameterizedTest
    @TestTokenSource(value = TokenType.REFRESH_TOKEN, count = 3)
    @DisplayName("Should create RefreshTokenContent with valid validation")
    void shouldCreateRefreshTokenContentWithValidToken(TestTokenHolder tokenHolder) {
        var refreshTokenContent = new RefreshTokenContent(tokenHolder.getRawToken(), tokenHolder.getClaims());

        assertNotNull(refreshTokenContent, "RefreshTokenContent should not be null");
        assertEquals(tokenHolder.getRawToken(), refreshTokenContent.getRawToken(), "Raw validation should match");
        assertEquals(TokenType.REFRESH_TOKEN, refreshTokenContent.getTokenType(), "Token type should be REFRESH_TOKEN");
        assertNotNull(refreshTokenContent.getClaims(), "Claims should not be null");
        assertEquals(tokenHolder.getClaims(), refreshTokenContent.getClaims(), "Claims should match");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.REFRESH_TOKEN, count = 2)
    @DisplayName("Should create RefreshTokenContent with claims")
    void shouldCreateRefreshTokenContentWithClaims(TestTokenHolder tokenHolder) {
        String testValue = "test-value";
        tokenHolder.withClaim("test-claim", ClaimValue.forPlainString(testValue));

        var refreshTokenContent = new RefreshTokenContent(tokenHolder.getRawToken(), tokenHolder.getClaims());

        assertNotNull(refreshTokenContent, "RefreshTokenContent should not be null");
        assertEquals(tokenHolder.getRawToken(), refreshTokenContent.getRawToken(), "Raw validation should match");
        assertEquals(TokenType.REFRESH_TOKEN, refreshTokenContent.getTokenType(), "Token type should be REFRESH_TOKEN");
        assertNotNull(refreshTokenContent.getClaims(), "Claims should not be null");
        assertFalse(refreshTokenContent.getClaims().isEmpty(), "Claims should not be empty");
        assertTrue(refreshTokenContent.getClaims().containsKey("test-claim"), "Claims should contain test-claim");
        assertEquals(testValue, refreshTokenContent.getClaims().get("test-claim").getOriginalString(), "Claim value should match");
    }

    @Override
    public RefreshTokenContent getUnderTest() {
        Map<String, ClaimValue> anyClaims = new HashMap<>();
        for (int i = 0; i < Generators.integers(0, 5).next(); i++) {
            anyClaims.put(Generators.nonEmptyStrings().next(), new ClaimValueGenerator().next());
        }
        return new RefreshTokenContent(Generators.nonEmptyStrings().next(), anyClaims);
    }
}
