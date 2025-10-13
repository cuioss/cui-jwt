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
package de.cuioss.sheriff.oauth.core.domain.token;

import de.cuioss.sheriff.oauth.core.TokenType;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link MinimalTokenContent} interface.
 * <p>
 * Tests the interface contract using concrete implementations.
 * 
 * @author Oliver Wolff
 */
@EnableGeneratorController
class MinimalTokenContentTest {

    @Test
    @DisplayName("Should provide raw token access")
    void shouldProvideRawTokenAccess() {
        MinimalTokenContent content = new TestMinimalTokenContent("test-token", TokenType.ACCESS_TOKEN);

        assertEquals("test-token", content.getRawToken());
    }

    @Test
    @DisplayName("Should provide token type access")
    void shouldProvideTokenTypeAccess() {
        MinimalTokenContent accessToken = new TestMinimalTokenContent("token1", TokenType.ACCESS_TOKEN);
        assertEquals(TokenType.ACCESS_TOKEN, accessToken.getTokenType());

        MinimalTokenContent idToken = new TestMinimalTokenContent("token2", TokenType.ID_TOKEN);
        assertEquals(TokenType.ID_TOKEN, idToken.getTokenType());

        MinimalTokenContent refreshToken = new TestMinimalTokenContent("token3", TokenType.REFRESH_TOKEN);
        assertEquals(TokenType.REFRESH_TOKEN, refreshToken.getTokenType());
    }

    @Test
    @DisplayName("Should handle null raw token")
    void shouldHandleNullRawToken() {
        MinimalTokenContent content = new TestMinimalTokenContent(null, TokenType.ACCESS_TOKEN);

        assertNull(content.getRawToken());
        assertEquals(TokenType.ACCESS_TOKEN, content.getTokenType());
    }

    @Test
    @DisplayName("Should handle empty raw token")
    void shouldHandleEmptyRawToken() {
        MinimalTokenContent content = new TestMinimalTokenContent("", TokenType.ID_TOKEN);

        assertEquals("", content.getRawToken());
        assertEquals(TokenType.ID_TOKEN, content.getTokenType());
    }

    @Test
    @DisplayName("Should handle long raw token")
    void shouldHandleLongRawToken() {
        String longToken = """
                eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRlc3Qta2V5LWlkIn0\
                .eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwic3ViIjoidGVzdC11c2VyIiwiYXVkIjoiY2xpZW50LWlkIiwi\
                ZXhwIjoxNjQwOTk1MjAwLCJpYXQiOjE2NDA5OTE2MDBdfQ\
                .signature-part-here""";

        MinimalTokenContent content = new TestMinimalTokenContent(longToken, TokenType.ACCESS_TOKEN);

        assertEquals(longToken, content.getRawToken());
        assertEquals(TokenType.ACCESS_TOKEN, content.getTokenType());
    }

    @Test
    @DisplayName("Should be serializable")
    void shouldBeSerializable() throws Exception {
        TestMinimalTokenContent original = new TestMinimalTokenContent("test-token", TokenType.ID_TOKEN);

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        TestMinimalTokenContent deserialized = (TestMinimalTokenContent) ois.readObject();
        ois.close();

        // Verify
        assertEquals(original.getRawToken(), deserialized.getRawToken());
        assertEquals(original.getTokenType(), deserialized.getTokenType());
    }

    @Test
    @DisplayName("Should maintain consistency across calls")
    void shouldMaintainConsistencyAcrossCalls() {
        MinimalTokenContent content = new TestMinimalTokenContent("consistent-token", TokenType.REFRESH_TOKEN);

        // Multiple calls should return the same values
        assertEquals("consistent-token", content.getRawToken());
        assertEquals("consistent-token", content.getRawToken());
        assertEquals("consistent-token", content.getRawToken());

        assertEquals(TokenType.REFRESH_TOKEN, content.getTokenType());
        assertEquals(TokenType.REFRESH_TOKEN, content.getTokenType());
        assertEquals(TokenType.REFRESH_TOKEN, content.getTokenType());
    }

    @Test
    @DisplayName("Should work with all token types")
    void shouldWorkWithAllTokenTypes() {
        for (TokenType tokenType : TokenType.values()) {
            MinimalTokenContent content = new TestMinimalTokenContent("token-for-" + tokenType, tokenType);

            assertEquals("token-for-" + tokenType, content.getRawToken());
            assertEquals(tokenType, content.getTokenType());
        }
    }

    @Test
    @DisplayName("Should support different token implementations")
    void shouldSupportDifferentTokenImplementations() {
        // Different implementations should work the same way
        MinimalTokenContent impl1 = new TestMinimalTokenContent("token1", TokenType.ACCESS_TOKEN);
        MinimalTokenContent impl2 = new AlternativeTestMinimalTokenContent("token2", TokenType.ID_TOKEN);

        assertNotNull(impl1.getRawToken());
        assertNotNull(impl1.getTokenType());
        assertNotNull(impl2.getRawToken());
        assertNotNull(impl2.getTokenType());

        assertNotEquals(impl1.getRawToken(), impl2.getRawToken());
        assertNotEquals(impl1.getTokenType(), impl2.getTokenType());
    }

    // Test implementations

    private static class TestMinimalTokenContent implements MinimalTokenContent {
        private static final long serialVersionUID = 1L;

        private final String rawToken;
        private final TokenType tokenType;

        public TestMinimalTokenContent(String rawToken, TokenType tokenType) {
            this.rawToken = rawToken;
            this.tokenType = tokenType;
        }

        @Override
        public String getRawToken() {
            return rawToken;
        }

        @Override
        public TokenType getTokenType() {
            return tokenType;
        }
    }

    private static class AlternativeTestMinimalTokenContent implements MinimalTokenContent {
        private static final long serialVersionUID = 1L;

        private final String token;
        private final TokenType type;

        public AlternativeTestMinimalTokenContent(String token, TokenType type) {
            this.token = token;
            this.type = type;
        }

        @Override
        public String getRawToken() {
            return token;
        }

        @Override
        public TokenType getTokenType() {
            return type;
        }
    }
}