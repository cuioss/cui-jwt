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
package de.cuioss.jwt.validation.pipeline;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TokenBuilder}.
 */
@EnableGeneratorController
@DisplayName("Tests TokenBuilder functionality")
class TokenBuilderTest {

    private TokenBuilder tokenBuilder;

    @BeforeEach
    void setUp() {
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier("test-issuer")
                .jwksContent("{\"keys\":[]}")
                .build();

        tokenBuilder = new TokenBuilder(issuerConfig);
    }

    @Nested
    @DisplayName("AccessToken Tests")
    class AccessTokenTests {

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("createAccessToken should create AccessTokenContent from DecodedJwt")
        void createAccessTokenShouldCreateAccessTokenContent(TestTokenHolder tokenHolder) {
            DecodedJwt decodedJwt = tokenHolder.asDecodedJwt();

            Optional<AccessTokenContent> result = tokenBuilder.createAccessToken(decodedJwt);
            assertTrue(result.isPresent(), "Should return AccessTokenContent");
            AccessTokenContent accessTokenContent = result.get();

            assertEquals(TokenType.ACCESS_TOKEN, accessTokenContent.getTokenType(), "Token type should be ACCESS_TOKEN");
            assertEquals(decodedJwt.rawToken(), accessTokenContent.getRawToken(), "Raw token should match");
            assertFalse(accessTokenContent.getClaims().isEmpty(), "Claims should not be empty");
            assertTrue(accessTokenContent.getClaims().containsKey(ClaimName.SUBJECT.getName()),
                    "Claims should contain subject");
            assertTrue(accessTokenContent.getClaims().containsKey(ClaimName.ISSUER.getName()),
                    "Claims should contain issuer");
        }

        @Test
        @DisplayName("createAccessToken should handle DecodedJwt with missing body")
        void createAccessTokenShouldHandleDecodedJwtWithMissingBody() {
            DecodedJwt decodedJwt = new DecodedJwt(null, null, null, new String[]{"", "", ""}, "test-validation");

            Optional<AccessTokenContent> result = tokenBuilder.createAccessToken(decodedJwt);
            assertTrue(result.isEmpty(), "Should return empty Optional when body is missing");
        }
    }

    @Nested
    @DisplayName("IdToken Tests")
    class IdTokenTests {

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 2)
        @DisplayName("createIdToken should create IdTokenContent from DecodedJwt")
        void createIdTokenShouldCreateIdTokenContent(TestTokenHolder tokenHolder) {
            DecodedJwt decodedJwt = tokenHolder.asDecodedJwt();

            Optional<IdTokenContent> result = tokenBuilder.createIdToken(decodedJwt);
            assertTrue(result.isPresent(), "Should return IdTokenContent");
            IdTokenContent idTokenContent = result.get();

            assertEquals(TokenType.ID_TOKEN, idTokenContent.getTokenType(), "Token type should be ID_TOKEN");
            assertEquals(decodedJwt.rawToken(), idTokenContent.getRawToken(), "Raw token should match");
            assertFalse(idTokenContent.getClaims().isEmpty(), "Claims should not be empty");
            assertTrue(idTokenContent.getClaims().containsKey(ClaimName.SUBJECT.getName()),
                    "Claims should contain subject");
            assertTrue(idTokenContent.getClaims().containsKey(ClaimName.ISSUER.getName()),
                    "Claims should contain issuer");
            assertTrue(idTokenContent.getClaims().containsKey(ClaimName.AUDIENCE.getName()),
                    "Claims should contain audience");
        }

        @Test
        @DisplayName("createIdToken should handle DecodedJwt with missing body")
        void createIdTokenShouldHandleDecodedJwtWithMissingBody() {
            DecodedJwt decodedJwt = new DecodedJwt(null, null, null, new String[]{"", "", ""}, "test-validation");

            Optional<IdTokenContent> result = tokenBuilder.createIdToken(decodedJwt);
            assertTrue(result.isEmpty(), "Should return empty Optional when body is missing");
        }
    }

    @Nested
    @DisplayName("RefreshToken Claims Tests")
    class RefreshTokenClaimsTests {

        @Test
        @DisplayName("extractClaimsForRefreshToken should extract claims from JsonObject")
        void extractClaimsForRefreshTokenShouldExtractClaims() {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("sub", "test-subject");
            builder.add("iss", "test-issuer");
            builder.add("custom-claim", "custom-value");
            JsonObject jsonObject = builder.build();

            Map<String, ClaimValue> claims = TokenBuilder.extractClaimsForRefreshToken(jsonObject);
            assertNotNull(claims, "Claims should not be null");
            assertFalse(claims.isEmpty(), "Claims should not be empty");
            assertEquals(3, claims.size(), "Should extract all claims");

            assertTrue(claims.containsKey("sub"), "Claims should contain subject");
            assertEquals("test-subject", claims.get("sub").getOriginalString(), "Subject claim value should match");

            assertTrue(claims.containsKey("iss"), "Claims should contain issuer");
            assertEquals("test-issuer", claims.get("iss").getOriginalString(), "Issuer claim value should match");

            assertTrue(claims.containsKey("custom-claim"), "Claims should contain custom claim");
            assertEquals("custom-value", claims.get("custom-claim").getOriginalString(), "Custom claim value should match");
        }

        @Test
        @DisplayName("extractClaimsForRefreshToken should handle empty JsonObject")
        void extractClaimsForRefreshTokenShouldHandleEmptyJsonObject() {
            JsonObject jsonObject = Json.createObjectBuilder().build();

            Map<String, ClaimValue> claims = TokenBuilder.extractClaimsForRefreshToken(jsonObject);
            assertNotNull(claims, "Claims should not be null");
            assertTrue(claims.isEmpty(), "Claims should be empty");
        }
    }

}
