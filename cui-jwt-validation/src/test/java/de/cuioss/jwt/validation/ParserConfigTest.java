/**
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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldImplementEqualsAndHashCode;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldImplementToString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link ParserConfig}.
 * 
 * @author Oliver Wolff
 */
@EnableGeneratorController
class ParserConfigTest implements ShouldImplementEqualsAndHashCode<ParserConfig>, ShouldImplementToString<ParserConfig> {

    @Override
    public ParserConfig getUnderTest() {
        return ParserConfig.builder().build();
    }

    @Test
    @DisplayName("Should use default values when created with builder")
    void shouldUseDefaultValues() {
        var config = ParserConfig.builder().build();

        assertEquals(ParserConfig.DEFAULT_MAX_TOKEN_SIZE, config.getMaxTokenSize());
        assertEquals(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE, config.getMaxPayloadSize());
        assertEquals(ParserConfig.DEFAULT_MAX_STRING_SIZE, config.getMaxStringSize());
        assertEquals(ParserConfig.DEFAULT_MAX_ARRAY_SIZE, config.getMaxArraySize());
        assertEquals(ParserConfig.DEFAULT_MAX_DEPTH, config.getMaxDepth());
    }

    @Test
    @DisplayName("Should accept custom values through builder")
    void shouldAcceptCustomValues() {
        var config = ParserConfig.builder()
                .maxTokenSize(16384)
                .maxPayloadSize(32768)
                .maxStringSize(8192)
                .maxArraySize(128)
                .maxDepth(20)
                .build();

        assertEquals(16384, config.getMaxTokenSize());
        assertEquals(32768, config.getMaxPayloadSize());
        assertEquals(8192, config.getMaxStringSize());
        assertEquals(128, config.getMaxArraySize());
        assertEquals(20, config.getMaxDepth());
    }

    @Test
    @DisplayName("Should provide consistent default constants")
    void shouldProvideConsistentDefaults() {
        assertEquals(ParserConfig.DEFAULT_MAX_TOKEN_SIZE, 8 * 1024);
        assertEquals(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE, 8 * 1024);
        assertEquals(ParserConfig.DEFAULT_MAX_STRING_SIZE, 4 * 1024);
        assertEquals(64, ParserConfig.DEFAULT_MAX_ARRAY_SIZE);
        assertEquals(10, ParserConfig.DEFAULT_MAX_DEPTH);
    }

    @Test
    @DisplayName("Should create JsonReaderFactory with security settings")
    void shouldCreateJsonReaderFactoryWithSecuritySettings() {
        var config = ParserConfig.builder()
                .maxStringSize(2048)
                .maxArraySize(32)
                .maxDepth(5)
                .build();

        var factory = config.getJsonReaderFactory();
        assertNotNull(factory);

        // JsonReaderFactory should be lazily created and cached
        assertSame(factory, config.getJsonReaderFactory());
    }

    @Test
    @DisplayName("Should handle boundary values")
    void shouldHandleBoundaryValues() {
        var config = ParserConfig.builder()
                .maxTokenSize(1)
                .maxPayloadSize(1)
                .maxStringSize(1)
                .maxArraySize(1)
                .maxDepth(1)
                .build();

        assertEquals(1, config.getMaxTokenSize());
        assertEquals(1, config.getMaxPayloadSize());
        assertEquals(1, config.getMaxStringSize());
        assertEquals(1, config.getMaxArraySize());
        assertEquals(1, config.getMaxDepth());
    }

    @Test
    @DisplayName("Should handle zero values")
    void shouldHandleZeroValues() {
        var config = ParserConfig.builder()
                .maxTokenSize(0)
                .maxPayloadSize(0)
                .maxStringSize(0)
                .maxArraySize(0)
                .maxDepth(0)
                .build();

        assertEquals(0, config.getMaxTokenSize());
        assertEquals(0, config.getMaxPayloadSize());
        assertEquals(0, config.getMaxStringSize());
        assertEquals(0, config.getMaxArraySize());
        assertEquals(0, config.getMaxDepth());
    }

    @Test
    @DisplayName("Should handle large values")
    void shouldHandleLargeValues() {
        var config = ParserConfig.builder()
                .maxTokenSize(Integer.MAX_VALUE)
                .maxPayloadSize(Integer.MAX_VALUE)
                .maxStringSize(Integer.MAX_VALUE)
                .maxArraySize(Integer.MAX_VALUE)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        assertEquals(Integer.MAX_VALUE, config.getMaxTokenSize());
        assertEquals(Integer.MAX_VALUE, config.getMaxPayloadSize());
        assertEquals(Integer.MAX_VALUE, config.getMaxStringSize());
        assertEquals(Integer.MAX_VALUE, config.getMaxArraySize());
        assertEquals(Integer.MAX_VALUE, config.getMaxDepth());
    }

    @Test
    @DisplayName("Should maintain immutability")
    void shouldMaintainImmutability() {
        var config = ParserConfig.builder()
                .maxTokenSize(1024)
                .build();

        // Accessing the same value multiple times should return the same result
        assertEquals(1024, config.getMaxTokenSize());
        assertEquals(1024, config.getMaxTokenSize());

        // JsonReaderFactory should be cached (lazy initialization)
        var factory1 = config.getJsonReaderFactory();
        var factory2 = config.getJsonReaderFactory();
        assertSame(factory1, factory2);
    }
}