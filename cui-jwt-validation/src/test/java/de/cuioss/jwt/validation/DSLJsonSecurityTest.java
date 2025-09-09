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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        JsonObject result = converter.convert(validJson);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("test", result.getString("name"));
        assertEquals(123, result.getInt("value"));
        assertTrue(result.getBoolean("active"));
    }

    @Test
    @DisplayName("Should handle empty JSON object")
    void shouldHandleEmptyJsonObject() {
        ParserConfig config = ParserConfig.builder().build();
        DslJson<Object> dslJson = config.getDslJson();
        JsonContentConverter converter = new JsonContentConverter(dslJson);

        String emptyJson = "{}";

        JsonObject result = converter.convert(emptyJson);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void shouldHandleMalformedJsonGracefully() {
        ParserConfig config = ParserConfig.builder().build();
        DslJson<Object> dslJson = config.getDslJson();
        JsonContentConverter converter = new JsonContentConverter(dslJson);

        String malformedJson = "{invalid json}";

        JsonObject result = converter.convert(malformedJson);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}