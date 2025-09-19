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
package de.cuioss.jwt.validation.jwks.http;

import de.cuioss.jwt.validation.json.Jwks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for JwksHttpContentConverter.
 */
class JwksHttpContentConverterTest {

    private final JwksHttpContentConverter converter = new JwksHttpContentConverter();

    @Test
    void shouldReturnCorrectBodyHandler() {
        HttpResponse.BodyHandler<?> handler = converter.getBodyHandler();
        assertNotNull(handler);
        // Should be String body handler with UTF-8
        assertEquals(HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8).getClass(), handler.getClass());
    }

    @Test
    void shouldReturnEmptyJwksForEmptyValue() {
        Jwks empty = converter.emptyValue();
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
    }

    @Test
    void shouldParseValidJwks() {
        String validJwks = """
            {
              "keys": [
                {
                  "kty": "RSA",
                  "use": "sig",
                  "kid": "test-key-1",
                  "n": "xGOr-H0A-bJYznUBCUb6NmKqTYIbL7tzFKbCH7L0MnJqGzjKsNpBn95aL-dVh7Vk3USW0fvOi8TvvD6ne8tVlL",
                  "e": "AQAB"
                }
              ]
            }
            """;

        Optional<Jwks> result = converter.convert(validJwks);
        assertTrue(result.isPresent());
        assertNotNull(result.get().keys());
        assertEquals(1, result.get().keys().size());
        assertEquals("test-key-1", result.get().keys().getFirst().kid());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   \n\t   "})
    void shouldReturnEmptyForInvalidContent(String content) {
        Optional<Jwks> result = converter.convert(content);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    void shouldReturnEmptyOptionalForInvalidJson() {
        String invalidJson = "not valid json";
        Optional<Jwks> result = converter.convert(invalidJson);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyOptionalForMalformedJwks() {
        String malformedJwks = """
            {
              "not_keys": "invalid structure"
            }
            """;
        Optional<Jwks> result = converter.convert(malformedJwks);
        // DSL-JSON should still parse this, but keys will be null
        assertTrue(result.isPresent());
        assertNull(result.get().keys());
    }

    @Test
    void shouldHandleEmptyKeysArray() {
        String emptyKeys = """
            {
              "keys": []
            }
            """;
        Optional<Jwks> result = converter.convert(emptyKeys);
        assertTrue(result.isPresent());
        assertNotNull(result.get().keys());
        assertTrue(result.get().keys().isEmpty());
    }
}