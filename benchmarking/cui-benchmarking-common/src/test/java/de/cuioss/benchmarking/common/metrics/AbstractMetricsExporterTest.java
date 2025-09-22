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
package de.cuioss.benchmarking.common.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AbstractMetricsExporter number formatting behavior.
 */
class AbstractMetricsExporterTest {

    @TempDir
    Path tempDir;

    @Test void shouldFormatNumbersCorrectlyWithoutDecimalForLargeValues() throws IOException {
        // Given - a test implementation of AbstractMetricsExporter
        TestMetricsExporter exporter = new TestMetricsExporter(tempDir.toString());

        // When - format various numbers
        Object smallValue = exporter.formatNumberPublic(7.6);
        Object exactTen = exporter.formatNumberPublic(10.0);
        Object largeValue = exporter.formatNumberPublic(13.0);
        Object veryLargeValue = exporter.formatNumberPublic(150.7);

        // Then - verify formatting rules
        // Values < 10 should be Double with one decimal place
        assertTrue(smallValue instanceof Double, "Values < 10 should be Double");
        assertEquals(7.6, (Double) smallValue, 0.01, "Small value should maintain decimal");

        // Values >= 10 should be Long without decimal
        assertTrue(exactTen instanceof Long, "Value 10 should be Long");
        assertEquals(10L, exactTen, "Value 10 should be exactly 10");

        assertTrue(largeValue instanceof Long, "Value 13 should be Long");
        assertEquals(13L, largeValue, "Value 13 should be exactly 13");

        assertTrue(veryLargeValue instanceof Long, "Large values should be Long");
        assertEquals(151L, veryLargeValue, "Value 150.7 should round to 151");

        // Verify JSON serialization doesn't add .0
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, Object> testMap = new LinkedHashMap<>();
        testMap.put("small", smallValue);
        testMap.put("exact_ten", exactTen);
        testMap.put("large", largeValue);
        testMap.put("very_large", veryLargeValue);

        String json = gson.toJson(testMap);

        // Verify JSON format (checking for the patterns regardless of whitespace)
        assertTrue(json.contains("\"small\": 7.6") || json.contains("\"small\":7.6"),
                "Small values should have decimal in JSON");
        assertTrue(json.contains("\"exact_ten\": 10") || json.contains("\"exact_ten\":10"),
                "Value 10 should not have .0 in JSON");
        assertTrue(json.contains("\"large\": 13") || json.contains("\"large\":13"),
                "Value 13 should not have .0 in JSON");
        assertTrue(json.contains("\"very_large\": 151") || json.contains("\"very_large\":151"),
                "Large values should not have .0 in JSON");

        // Ensure no .0 suffix for integer values >= 10
        assertFalse(json.contains("10.0"), "Should not contain 10.0");
        assertFalse(json.contains("13.0"), "Should not contain 13.0");
        assertFalse(json.contains("151.0"), "Should not contain 151.0");
    }

    /**
     * Test implementation to access protected formatNumber method.
     */
    static class TestMetricsExporter extends AbstractMetricsExporter {
        TestMetricsExporter(String outputDirectory) {
            super(outputDirectory);
        }

        @Override public void exportMetrics(String benchmarkMethodName, Instant timestamp, Object metricsData) {
            // Not needed for this test
        }

        public Object formatNumberPublic(double value) {
            return formatNumber(value);
        }
    }
}