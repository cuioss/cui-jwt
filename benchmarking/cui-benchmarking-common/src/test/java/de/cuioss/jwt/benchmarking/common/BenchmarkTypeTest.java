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
package de.cuioss.jwt.benchmarking.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BenchmarkType}.
 * 
 * @since 1.0
 */
class BenchmarkTypeTest {

    @Test
    void testMicroBenchmarkProperties() {
        // Act & Assert
        assertEquals("Performance Score", BenchmarkType.MICRO.getLabelText());
        assertEquals("performance-badge.json", BenchmarkType.MICRO.getPerformanceBadgeFilename());
        assertEquals("trend-badge.json", BenchmarkType.MICRO.getTrendBadgeFilename());
    }

    @Test
    void testIntegrationBenchmarkProperties() {
        // Act & Assert
        assertEquals("Integration Performance", BenchmarkType.INTEGRATION.getLabelText());
        assertEquals("integration-performance-badge.json", BenchmarkType.INTEGRATION.getPerformanceBadgeFilename());
        assertEquals("integration-trend-badge.json", BenchmarkType.INTEGRATION.getTrendBadgeFilename());
    }

    @Test
    void testEnumValues() {
        // Test that enum contains expected values
        var values = BenchmarkType.values();
        assertEquals(2, values.length);
        
        assertTrue(java.util.Arrays.asList(values).contains(BenchmarkType.MICRO));
        assertTrue(java.util.Arrays.asList(values).contains(BenchmarkType.INTEGRATION));
    }

    @Test
    void testEnumValueOf() {
        // Test valueOf method
        assertEquals(BenchmarkType.MICRO, BenchmarkType.valueOf("MICRO"));
        assertEquals(BenchmarkType.INTEGRATION, BenchmarkType.valueOf("INTEGRATION"));
        
        // Test invalid value throws exception
        assertThrows(IllegalArgumentException.class, () -> BenchmarkType.valueOf("INVALID"));
    }

    @Test
    void testUniqueFilenames() {
        // Verify that each benchmark type has unique filenames
        assertNotEquals(BenchmarkType.MICRO.getPerformanceBadgeFilename(), 
                       BenchmarkType.INTEGRATION.getPerformanceBadgeFilename());
        
        assertNotEquals(BenchmarkType.MICRO.getTrendBadgeFilename(),
                       BenchmarkType.INTEGRATION.getTrendBadgeFilename());
    }

    @Test
    void testNonNullProperties() {
        // Verify all properties return non-null values
        for (var type : BenchmarkType.values()) {
            assertNotNull(type.getLabelText());
            assertNotNull(type.getPerformanceBadgeFilename());
            assertNotNull(type.getTrendBadgeFilename());
            
            assertFalse(type.getLabelText().isEmpty());
            assertFalse(type.getPerformanceBadgeFilename().isEmpty());
            assertFalse(type.getTrendBadgeFilename().isEmpty());
        }
    }
}