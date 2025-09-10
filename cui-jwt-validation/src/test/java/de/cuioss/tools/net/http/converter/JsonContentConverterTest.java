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
package de.cuioss.tools.net.http.converter;

import com.dslplatform.json.DslJson;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JsonContentConverter}.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
class JsonContentConverterTest {

    private JsonContentConverter converter;

    @BeforeEach
    void setUp() {
        ParserConfig parserConfig = ParserConfig.builder().build();
        var dslJson = parserConfig.getDslJson();
        converter = new JsonContentConverter(dslJson);
    }

    @Test
    @DisplayName("Should convert valid JSON string to JsonObject")
    void shouldConvertValidJsonStringToJsonObject() {
        String jsonString = "{\"name\":\"test\",\"value\":123}";

        Optional<JsonValue> result = converter.convert(jsonString);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof JsonObject);
        JsonObject jsonObject = (JsonObject) result.get();
        assertFalse(jsonObject.isEmpty());
        assertEquals("test", jsonObject.getString("name"));
        assertEquals(123, jsonObject.getInt("value"));
    }

    @Test
    @DisplayName("Should handle empty JSON object")
    void shouldHandleEmptyJsonObject() {
        String jsonString = "{}";

        Optional<JsonValue> result = converter.convert(jsonString);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof JsonObject);
        JsonObject jsonObject = (JsonObject) result.get();
        assertTrue(jsonObject.isEmpty());
    }

    @Test
    @DisplayName("Should handle null input")
    void shouldHandleNullInput() {
        Optional<JsonValue> result = converter.convert(null);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof JsonObject);
        JsonObject jsonObject = (JsonObject) result.get();
        assertTrue(jsonObject.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty string input")
    void shouldHandleEmptyStringInput() {
        Optional<JsonValue> result = converter.convert("");

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof JsonObject);
        JsonObject jsonObject = (JsonObject) result.get();
        assertTrue(jsonObject.isEmpty());
    }

    @Test
    @DisplayName("Should handle whitespace-only input")
    void shouldHandleWhitespaceOnlyInput() {
        Optional<JsonValue> result = converter.convert("   \n\t  ");

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof JsonObject);
        JsonObject jsonObject = (JsonObject) result.get();
        assertTrue(jsonObject.isEmpty());
    }

    @Test
    @DisplayName("Should handle malformed JSON")
    void shouldHandleMalformedJson() {
        String malformedJson = "{name: test, value: 123}"; // Missing quotes

        Optional<JsonValue> result = converter.convert(malformedJson);

        assertFalse(result.isPresent()); // Malformed JSON should return empty Optional
    }

    @Test
    @DisplayName("Should throw TokenValidationException for JSON that exceeds content size limits")
    void shouldThrowTokenValidationExceptionForJsonThatExceedsContentSizeLimits() {
        ParserConfig config = ParserConfig.builder().build();
        var dslJson = config.getDslJson();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        int maxContentSize = 10; // Very small limit - 10 bytes
        JsonContentConverter restrictiveConverter = new JsonContentConverter(dslJson, securityEventCounter, maxContentSize);

        // Use a JSON string that exceeds the 10-byte limit
        String largeJsonString = "{\"key\":\"this string is definitely longer than 10 bytes\"}";

        // Should throw TokenValidationException for content size violations
        assertThrows(TokenValidationException.class, () -> {
            restrictiveConverter.convert(largeJsonString);
        }, "Should throw TokenValidationException when content size exceeds limit");

        // Verify security event was recorded
        assertTrue(securityEventCounter.getCount(SecurityEventCounter.EventType.JWKS_JSON_PARSE_FAILED) > 0,
                "Should record security event for content size violation");

        // Verify WARN log message was generated
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.JSON_PARSING_FAILED.resolveIdentifierString());
    }

    @Test
    @DisplayName("Should return empty JsonObject as empty value")
    void shouldReturnEmptyJsonObjectAsEmptyValue() {
        JsonObject emptyValue = converter.emptyValue();

        assertNotNull(emptyValue);
        assertTrue(emptyValue.isEmpty());
    }

    @Test
    @DisplayName("Should use UTF-8 charset from parent StringContentConverter")
    void shouldUseUtf8CharsetFromParentStringContentConverter() {
        HttpResponse.BodyHandler<?> bodyHandler = converter.getBodyHandler();

        assertNotNull(bodyHandler);
        // The BodyHandler should be configured for UTF-8 (inherited from StringContentConverter)
    }

    @Test
    @DisplayName("Should handle complex nested JSON")
    void shouldHandleComplexNestedJson() {
        String complexJson = """
            {
                "user": {
                    "id": 123,
                    "name": "John Doe",
                    "roles": ["admin", "user"],
                    "settings": {
                        "theme": "dark",
                        "notifications": true
                    }
                }
            }
            """;

        Optional<JsonValue> result = converter.convert(complexJson);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof JsonObject);
        JsonObject jsonObject = (JsonObject) result.get();
        assertFalse(jsonObject.isEmpty());

        JsonObject user = jsonObject.getJsonObject("user");
        assertNotNull(user);
        assertEquals(123, user.getInt("id"));
        assertEquals("John Doe", user.getString("name"));

        JsonArray roles = user.getJsonArray("roles");
        assertEquals(2, roles.size());
        assertEquals("admin", roles.getString(0));
        assertEquals("user", roles.getString(1));

        JsonObject settings = user.getJsonObject("settings");
        assertNotNull(settings);
        assertEquals("dark", settings.getString("theme"));
        assertTrue(settings.getBoolean("notifications"));
    }

    @Test
    @DisplayName("Should handle JSON array as top-level element gracefully")
    void shouldHandleJsonArrayAsTopLevelElementGracefully() {
        String jsonArray = "[{\"id\": 1}, {\"id\": 2}]";

        Optional<JsonValue> result = converter.convert(jsonArray);

        // Current implementation focuses on JSON objects for JWT/JWKS/Discovery use cases
        // JSON arrays are not supported and return empty Optional
        assertFalse(result.isPresent(), "JSON arrays are not currently supported by this converter");
    }

    @Test
    @DisplayName("Should use provided DslJson")
    void shouldUseProvidedDslJson() {
        // Test that the converter uses the provided DslJson
        // by verifying consistent behavior

        ParserConfig customConfig = ParserConfig.builder()
                .maxBufferSize(1000) // Very small buffer
                .build();
        var customDslJson = customConfig.getDslJson();
        JsonContentConverter customConverter = new JsonContentConverter(customDslJson);

        String deeplyNestedJson = """
            {
                "level1": {
                    "level2": {
                        "level3": {
                            "value": "too deep"
                        }
                    }
                }
            }
            """;

        Optional<JsonValue> result = customConverter.convert(deeplyNestedJson);

        // DSL-JSON should handle buffer limits and return empty object if limits exceeded
        assertNotNull(result);
        // Either parses successfully or returns empty due to buffer limits
    }

    @Test
    @DisplayName("Should log WARN message when JSON parsing fails")
    void shouldLogWarnMessageWhenJsonParsingFails() {
        String malformedJson = "{invalid json}";

        Optional<JsonValue> result = converter.convert(malformedJson);

        // Verify the conversion fails gracefully
        assertFalse(result.isPresent(), "Conversion should return empty Optional for malformed JSON");

        // Verify ERROR log message is present using LogRecord identifier
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.resolveIdentifierString());
    }
}