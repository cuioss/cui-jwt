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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.json.WellKnownConfiguration;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for direct String → WellKnownConfiguration converter.
 *
 * @author Oliver Wolff
 */
@DisplayName("WellKnownConfigurationConverter")
class WellKnownConfigurationConverterTest {

    private static final String VALID_JSON = """
            {
                "issuer": "https://example.com",
                "jwks_uri": "https://example.com/.well-known/jwks.json",
                "authorization_endpoint": "https://example.com/auth",
                "token_endpoint": "https://example.com/token"
            }
            """;

    private static final String MINIMAL_JSON = """
            {
                "issuer": "https://example.com",
                "jwks_uri": "https://example.com/.well-known/jwks.json"
            }
            """;

    private WellKnownConfigurationConverter converter;

    @BeforeEach
    void setUp() {
        ParserConfig config = ParserConfig.builder().build();
        converter = new WellKnownConfigurationConverter(config.getDslJson());
    }

    @Test
    @DisplayName("Should convert valid JSON string to WellKnownConfiguration")
    void shouldConvertValidJsonStringToWellKnownConfiguration() {
        Optional<WellKnownConfiguration> result = converter.convert(VALID_JSON);

        assertTrue(result.isPresent());
        WellKnownConfiguration config = result.get();
        assertEquals("https://example.com", config.issuer());
        assertEquals("https://example.com/.well-known/jwks.json", config.jwksUri());
        assertEquals("https://example.com/auth", config.authorizationEndpoint());
        assertEquals("https://example.com/token", config.tokenEndpoint());
        assertTrue(config.supportsFullOAuthFlows());
        assertFalse(config.isMinimal());
        assertFalse(config.isEmpty());
    }

    @Test
    @DisplayName("Should convert minimal JSON to WellKnownConfiguration")
    void shouldConvertMinimalJsonToWellKnownConfiguration() {
        Optional<WellKnownConfiguration> result = converter.convert(MINIMAL_JSON);

        assertTrue(result.isPresent());
        WellKnownConfiguration config = result.get();
        assertEquals("https://example.com", config.issuer());
        assertEquals("https://example.com/.well-known/jwks.json", config.jwksUri());
        assertNull(config.authorizationEndpoint());
        assertNull(config.tokenEndpoint());
        assertFalse(config.supportsFullOAuthFlows());
        assertTrue(config.isMinimal());
        assertFalse(config.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty JSON response")
    void shouldHandleEmptyJsonResponse() {
        Optional<WellKnownConfiguration> result = converter.convert("");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should handle null input")
    void shouldHandleNullInput() {
        Optional<WellKnownConfiguration> result = converter.convert(null);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should handle non-String input")
    void shouldHandleNonStringInput() {
        Optional<WellKnownConfiguration> result = converter.convert(12345);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should handle malformed JSON")
    void shouldHandleMalformedJson() {
        String malformedJson = "{invalid json}";

        Optional<WellKnownConfiguration> result = converter.convert(malformedJson);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should handle missing required issuer field")
    void shouldHandleMissingRequiredIssuerField() {
        String invalidJson = """
            {
                "jwks_uri": "https://example.com/.well-known/jwks.json"
            }
            """;

        Optional<WellKnownConfiguration> result = converter.convert(invalidJson);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should handle missing required jwks_uri field")
    void shouldHandleMissingRequiredJwksUriField() {
        String invalidJson = """
            {
                "issuer": "https://example.com"
            }
            """;

        Optional<WellKnownConfiguration> result = converter.convert(invalidJson);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should throw TokenValidationException for content size violations")
    void shouldThrowTokenValidationExceptionForContentSizeViolations() {
        ParserConfig config = ParserConfig.builder().build();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        int maxContentSize = 10; // Very small limit
        WellKnownConfigurationConverter restrictiveConverter =
                new WellKnownConfigurationConverter(config.getDslJson(), securityEventCounter, maxContentSize);

        String largeJson = """
            {
                "issuer": "https://example.com/with/very/long/path/that/exceeds/limit",
                "jwks_uri": "https://example.com/.well-known/jwks.json"
            }
            """;

        assertThrows(TokenValidationException.class, () ->
                restrictiveConverter.convert(largeJson));

        assertTrue(securityEventCounter.getCount(SecurityEventCounter.EventType.JWKS_JSON_PARSE_FAILED) > 0);
    }

    @Test
    @DisplayName("Should provide appropriate BodyHandler")
    void shouldProvideAppropriateBodyHandler() {
        var bodyHandler = converter.getBodyHandler();

        assertNotNull(bodyHandler);
        // Should be a String BodyHandler - we can't easily test the exact type
        // but we can verify it's not null and works as expected
        assertSame(HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8).getClass(),
                bodyHandler.getClass());
    }

    @Test
    @DisplayName("Should provide empty value sentinel")
    void shouldProvideEmptyValueSentinel() {
        WellKnownConfiguration emptyValue = converter.emptyValue();

        assertNotNull(emptyValue);
        assertSame(WellKnownConfiguration.EMPTY, emptyValue);
        assertTrue(emptyValue.isEmpty());
        assertEquals("about:empty", emptyValue.issuer());
        assertEquals("about:empty", emptyValue.jwksUri());
        assertNull(emptyValue.authorizationEndpoint());
        assertNull(emptyValue.tokenEndpoint());
    }
}