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
package de.cuioss.jwt.validation.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class SimplifiedMetricsExporterTest {

    @TempDir
    Path tempDir;

    private final Gson gson = new GsonBuilder().create();

    @Test void shouldExportMetricsToJsonFile() throws IOException {
        // Given
        TokenValidatorMonitor monitor = TokenValidatorMonitorConfig.builder()
                .measurementType(MeasurementType.TOKEN_PARSING)
                .measurementType(MeasurementType.HEADER_VALIDATION)
                .build()
                .createMonitor();

        // Simulate some measurements with actual time delay
        long parseStart = System.nanoTime();

        // Wait a bit to simulate parsing time
        await()
                .atMost(50, TimeUnit.MILLISECONDS)
                .pollDelay(10, TimeUnit.MILLISECONDS)
                .until(() -> true);

        long parseDuration = System.nanoTime() - parseStart;
        monitor.recordMeasurement(MeasurementType.TOKEN_PARSING, parseDuration);

        long headerStart = System.nanoTime();

        // Wait a bit to simulate header validation time
        await()
                .atMost(20, TimeUnit.MILLISECONDS)
                .pollDelay(5, TimeUnit.MILLISECONDS)
                .until(() -> true);

        long headerDuration = System.nanoTime() - headerStart;
        monitor.recordMeasurement(MeasurementType.HEADER_VALIDATION, headerDuration);

        // Set up test directory
        System.setProperty("benchmark.results.dir", tempDir.toString());

        // When
        SimplifiedMetricsExporter.exportMetrics(monitor);

        // Then
        Path[] jsonFiles = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".json"))
                .toArray(Path[]::new);

        assertEquals(1, jsonFiles.length);
        assertEquals("jwt-validation-metrics.json", jsonFiles[0].getFileName().toString());

        String jsonContent = Files.readString(jsonFiles[0]);
        TypeToken<Map<String, Object>> typeToken = new TypeToken<>() {
        };
        Map<String, Object> allMetrics = gson.fromJson(jsonContent, typeToken.getType());

        // Verify we have at least one benchmark entry (unknown_benchmark since no specific benchmark name is set)
        assertFalse(allMetrics.isEmpty());

        // Get the first benchmark entry (should be "unknown_benchmark")
        String benchmarkName = allMetrics.keySet().iterator().next();
        Map<String, Object> benchmarkMetrics = (Map<String, Object>) allMetrics.get(benchmarkName);

        // Verify JSON structure for the benchmark
        assertNotNull(benchmarkMetrics.get("timestamp"));
        assertNotNull(benchmarkMetrics.get("steps"));

        Map<String, Map<String, Object>> steps = (Map<String, Map<String, Object>>) benchmarkMetrics.get("steps");
        assertTrue(steps.containsKey("token_parsing"));
        assertTrue(steps.containsKey("header_validation"));

        // Verify metrics exist
        Map<String, Object> parseMetrics = steps.get("token_parsing");
        assertNotNull(parseMetrics.get("sample_count"));
        assertNotNull(parseMetrics.get("p50_us"));
        assertNotNull(parseMetrics.get("p95_us"));
        assertNotNull(parseMetrics.get("p99_us"));
    }

    @Test void shouldHandleNullMonitor() {
        assertDoesNotThrow(() -> SimplifiedMetricsExporter.exportMetrics(null));
    }

    @Test void shouldFormatNumbersCorrectly() throws IOException {
        // Given
        TokenValidatorMonitor monitor = TokenValidatorMonitorConfig.builder()
                .measurementType(MeasurementType.TOKEN_PARSING)
                .measurementType(MeasurementType.SIGNATURE_VALIDATION)
                .build()
                .createMonitor();

        // Simulate measurements with values that would result in < 10 and >= 10 microseconds
        // Small measurement < 10 microseconds (e.g., 7.6)
        monitor.recordMeasurement(MeasurementType.TOKEN_PARSING, 7600); // 7.6 microseconds

        // Large measurement >= 10 microseconds (e.g., 13)
        monitor.recordMeasurement(MeasurementType.SIGNATURE_VALIDATION, 13000); // 13 microseconds

        // Set up test directory
        System.setProperty("benchmark.results.dir", tempDir.toString());

        // When
        SimplifiedMetricsExporter.exportMetrics(monitor);

        // Then
        Path jsonFile = tempDir.resolve("jwt-validation-metrics.json");
        String jsonContent = Files.readString(jsonFile);

        // Verify that values < 10 have one decimal place
        assertTrue(jsonContent.contains("7.6"), "Values < 10 should have one decimal place");

        // Verify that values >= 10 are integers without decimal point
        assertTrue(jsonContent.contains("\"p50_us\": 13"), "Values >= 10 should be integers without decimal");
        assertFalse(jsonContent.contains("13.0"), "Values >= 10 should not have .0 suffix");
    }

    @Test void shouldUseSystemPropertyForBenchmarkContext() throws Exception {
        // Given - set system property
        String originalProperty = System.getProperty("benchmark.context");
        System.setProperty("benchmark.context", "custom-benchmark-test");

        try {
            // When - call the private method via reflection
            Method method = SimplifiedMetricsExporter.class.getDeclaredMethod("getCurrentBenchmarkName");
            method.setAccessible(true);
            String result = (String) method.invoke(null);

            // Then
            assertEquals("custom-benchmark-test", result);
        } finally {
            // Cleanup
            if (originalProperty != null) {
                System.setProperty("benchmark.context", originalProperty);
            } else {
                System.clearProperty("benchmark.context");
            }
        }
    }

    @Test void shouldFallbackToBenchmarkClassDetection() throws Exception {
        // Given - clear system property and create stack with benchmark class
        String originalProperty = System.getProperty("benchmark.context");
        System.clearProperty("benchmark.context");

        try {
            // Create a mock benchmark class scenario by calling from a class with "Benchmark" in name
            String result = new MockJwtValidationBenchmark().getBenchmarkNameFromExporter();

            // Then - should extract class name without "Benchmark" suffix
            assertEquals("simplifiedmetricsexportertest$mockjwtvalidation", result);
        } finally {
            // Cleanup
            if (originalProperty != null) {
                System.setProperty("benchmark.context", originalProperty);
            }
        }
    }

    @Test void shouldReturnNullWhenNoBenchmarkContextFound() throws Exception {
        // Given - clear system property
        String originalProperty = System.getProperty("benchmark.context");
        System.clearProperty("benchmark.context");

        try {
            // When - call from test context (no benchmark patterns)
            Method method = SimplifiedMetricsExporter.class.getDeclaredMethod("getCurrentBenchmarkName");
            method.setAccessible(true);
            String result = (String) method.invoke(null);

            // Then - should return null when no patterns match
            assertNull(result);
        } finally {
            // Cleanup
            if (originalProperty != null) {
                System.setProperty("benchmark.context", originalProperty);
            }
        }
    }

    @Test void shouldHandleEmptySystemProperty() throws Exception {
        // Given - set empty system property
        String originalProperty = System.getProperty("benchmark.context");
        System.setProperty("benchmark.context", "  ");

        try {
            // When
            Method method = SimplifiedMetricsExporter.class.getDeclaredMethod("getCurrentBenchmarkName");
            method.setAccessible(true);
            String result = (String) method.invoke(null);

            // Then - should fallback to other detection methods when property is empty/whitespace
            assertNull(result); // In test context, should return null
        } finally {
            // Cleanup
            if (originalProperty != null) {
                System.setProperty("benchmark.context", originalProperty);
            } else {
                System.clearProperty("benchmark.context");
            }
        }
    }

    /**
     * Mock benchmark class to test class name detection
     */
    private static class MockJwtValidationBenchmark {
        String getBenchmarkNameFromExporter() throws Exception {
            Method method = SimplifiedMetricsExporter.class.getDeclaredMethod("getCurrentBenchmarkName");
            method.setAccessible(true);
            return (String) method.invoke(null);
        }
    }
}