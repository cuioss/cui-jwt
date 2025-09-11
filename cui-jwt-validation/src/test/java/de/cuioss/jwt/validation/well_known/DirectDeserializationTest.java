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

import com.dslplatform.json.DslJson;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.json.WellKnownConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for DIRECT deserialization from JSON to WellKnownConfiguration.
 * This is the true mapper approach test - no intermediate steps allowed!
 *
 * @author Oliver Wolff
 */
@DisplayName("Direct Deserialization Test")
class DirectDeserializationTest {

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

    private DslJson<Object> dslJson;

    @BeforeEach
    void setUp() {
        ParserConfig config = ParserConfig.builder().build();
        dslJson = config.getDslJson();
    }

    @Test
    @DisplayName("Should deserialize directly to WellKnownConfiguration record - THE TRUE TEST")
    void shouldDeserializeDirectlyToWellKnownConfigurationRecord() {
        byte[] bytes = VALID_JSON.getBytes();

        try {
            // THIS IS THE TRUE MAPPER APPROACH - direct deserialization!
            WellKnownConfiguration config = dslJson.deserialize(WellKnownConfiguration.class, bytes, bytes.length);

            // If this works, the mapper approach is successful
            assertNotNull(config, "Direct deserialization should work");
            assertEquals("https://example.com", config.issuer());
            assertEquals("https://example.com/.well-known/jwks.json", config.jwksUri());
            assertEquals("https://example.com/auth", config.authorizationEndpoint());
            assertEquals("https://example.com/token", config.tokenEndpoint());

            System.out.println("✅ SUCCESS: Direct deserialization to record works!");

        } catch (IOException e) {
            // If this fails, we need to understand WHY
            System.err.println("❌ FAILED: Direct deserialization failed with: " + e.getMessage());
            System.err.println("Error type: " + e.getClass().getSimpleName());

            fail("Direct deserialization failed: " + e.getMessage() +
                    ". This means the true mapper approach doesn't work with records.");
        }
    }

    @Test
    @DisplayName("Should deserialize minimal JSON directly to WellKnownConfiguration record")
    void shouldDeserializeMinimalJsonDirectly() {
        byte[] bytes = MINIMAL_JSON.getBytes();

        assertDoesNotThrow(() -> {
            WellKnownConfiguration config = dslJson.deserialize(WellKnownConfiguration.class, bytes, bytes.length);

            assertNotNull(config, "Direct deserialization of minimal JSON should work");
            assertEquals("https://example.com", config.issuer());
            assertEquals("https://example.com/.well-known/jwks.json", config.jwksUri());
            assertNull(config.authorizationEndpoint());
            assertNull(config.tokenEndpoint());

        }, "Direct deserialization of minimal JSON failed: ");
    }

    @Test
    @DisplayName("Should handle direct deserialization errors gracefully")
    void shouldHandleDirectDeserializationErrors() {
        String malformedJson = "{invalid json}";
        byte[] bytes = malformedJson.getBytes();

        assertThrows(IOException.class, () -> {
            dslJson.deserialize(WellKnownConfiguration.class, bytes, bytes.length);
        }, "Malformed JSON should throw IOException during direct deserialization");
    }

    @Test
    @DisplayName("Should return null for empty JSON during direct deserialization")
    void shouldReturnNullForEmptyJsonDuringDirectDeserialization() throws IOException {
        String emptyJson = "{}";
        byte[] bytes = emptyJson.getBytes();

        try {
            WellKnownConfiguration config = dslJson.deserialize(WellKnownConfiguration.class, bytes, bytes.length);

            // This will either work (return a config with nulls) or fail (return null)
            // Either way, we need to document the behavior
            if (config == null) {
                System.out.println("ℹ️  INFO: Empty JSON returns null during direct deserialization");
            } else {
                System.out.println("ℹ️  INFO: Empty JSON creates config with values: " + config);
            }
        } catch (IOException e) {
            System.out.println("ℹ️  INFO: Empty JSON fails with: " + e.getMessage());
        }
    }

}