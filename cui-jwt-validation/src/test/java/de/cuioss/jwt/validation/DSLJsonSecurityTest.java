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
package de.cuioss.jwt.validation;

import com.dslplatform.json.DslJson;
import de.cuioss.tools.net.http.converter.JsonContentConverter;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify DSL-JSON behavior with security configurations.
 */
class DSLJsonSecurityTest {

    @Test
    @DisplayName("Should parse valid JSON successfully")
    void shouldParseValidJsonSuccessfully() {
        ParserConfig config = ParserConfig.builder()
                .maxStringLength(1000)
                .maxBufferSize(2048)
                .build();

        DslJson<Object> dslJson = config.getDslJson();
        JsonContentConverter converter = new JsonContentConverter(dslJson);

        String validJson = "{\"name\":\"test\",\"value\":123,\"active\":true}";

        Optional<JsonValue> result = converter.convert(validJson);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof JsonObject);
        JsonObject jsonObject = (JsonObject) result.get();
        assertFalse(jsonObject.isEmpty());
        assertEquals("test", jsonObject.getString("name"));
        assertEquals(123, jsonObject.getInt("value"));
        assertTrue(jsonObject.getBoolean("active"));
    }

    @Test
    @DisplayName("Should handle empty JSON object")
    void shouldHandleEmptyJsonObject() {
        ParserConfig config = ParserConfig.builder().build();
        DslJson<Object> dslJson = config.getDslJson();
        JsonContentConverter converter = new JsonContentConverter(dslJson);

        String emptyJson = "{}";

        Optional<JsonValue> result = converter.convert(emptyJson);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof JsonObject);
        JsonObject jsonObject = (JsonObject) result.get();
        assertTrue(jsonObject.isEmpty());
    }

    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void shouldHandleMalformedJsonGracefully() {
        ParserConfig config = ParserConfig.builder().build();
        DslJson<Object> dslJson = config.getDslJson();
        JsonContentConverter converter = new JsonContentConverter(dslJson);

        String malformedJson = "{invalid json}";

        Optional<JsonValue> result = converter.convert(malformedJson);

        assertFalse(result.isPresent()); // Malformed JSON should return empty Optional
    }
}