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

import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.JsonReaderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link ParserConfig}.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests for ParserConfig")
class ParserConfigTest {

    @Test
    @DisplayName("Should create config with default values")
    void shouldCreateConfigWithDefaultValues() {

        ParserConfig config = ParserConfig.builder().build();
        assertEquals(ParserConfig.DEFAULT_MAX_TOKEN_SIZE, config.getMaxTokenSize());
        assertEquals(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE, config.getMaxPayloadSize());
        assertEquals(ParserConfig.DEFAULT_MAX_STRING_SIZE, config.getMaxStringSize());
        assertEquals(ParserConfig.DEFAULT_MAX_ARRAY_SIZE, config.getMaxArraySize());
        assertEquals(ParserConfig.DEFAULT_MAX_DEPTH, config.getMaxDepth());
    }

    @Test
    @DisplayName("Should create config with custom values")
    void shouldCreateConfigWithCustomValues() {

        int customMaxTokenSize = 4096;
        int customMaxPayloadSize = 2048;
        int customMaxStringSize = 1024;
        int customMaxArraySize = 32;
        int customMaxDepth = 5;
        ParserConfig config = ParserConfig.builder()
                .maxTokenSize(customMaxTokenSize)
                .maxPayloadSize(customMaxPayloadSize)
                .maxStringSize(customMaxStringSize)
                .maxArraySize(customMaxArraySize)
                .maxDepth(customMaxDepth)
                .build();
        assertEquals(customMaxTokenSize, config.getMaxTokenSize());
        assertEquals(customMaxPayloadSize, config.getMaxPayloadSize());
        assertEquals(customMaxStringSize, config.getMaxStringSize());
        assertEquals(customMaxArraySize, config.getMaxArraySize());
        assertEquals(customMaxDepth, config.getMaxDepth());
    }

    @Test
    @DisplayName("Should create JsonReaderFactory with security settings")
    void shouldCreateJsonReaderFactoryWithSecuritySettings() {

        ParserConfig config = ParserConfig.builder().build();
        JsonReaderFactory factory = config.getJsonReaderFactory();
        assertNotNull(factory, "JsonReaderFactory should not be null");
    }

    @Test
    @DisplayName("Should create JsonReaderFactory with custom security settings")
    void shouldCreateJsonReaderFactoryWithCustomSecuritySettings() {

        int customMaxStringSize = 1024;
        int customMaxArraySize = 32;
        int customMaxDepth = 5;

        ParserConfig config = ParserConfig.builder()
                .maxStringSize(customMaxStringSize)
                .maxArraySize(customMaxArraySize)
                .maxDepth(customMaxDepth)
                .build();
        JsonReaderFactory factory = config.getJsonReaderFactory();
        assertNotNull(factory, "JsonReaderFactory with custom settings should not be null");
        // Note: We can't directly verify the configuration of the factory
        // as there's no public API to access its configuration
    }
}