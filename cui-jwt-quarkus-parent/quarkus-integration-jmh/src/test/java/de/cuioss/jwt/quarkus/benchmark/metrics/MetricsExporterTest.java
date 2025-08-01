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
package de.cuioss.jwt.quarkus.benchmark.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MetricsExporter
 */
class MetricsExporterTest {

    @TempDir
    Path tempDir;

    private MetricsExporter exporter;
    private Gson gson;

    @BeforeEach
    void setUp() {
        exporter = new MetricsExporter("https://localhost:10443", tempDir.toString());
        gson = new GsonBuilder().create();
    }

    @Test
    void shouldExportMetricsWithSyntheticData() throws Exception {
        // Given
        BenchmarkMetrics.BenchmarkMetadata metadata = BenchmarkMetrics.BenchmarkMetadata.builder()
                .threadCount(1)
                .iterations(1)
                .warmupDurationSeconds(0)
                .measurementDurationSeconds(1)
                .serviceUrl("https://localhost:10443")
                .jvmInfo("Test JVM")
                .systemInfo("Test System")
                .build();

        // When
        BenchmarkMetrics metrics = exporter.exportMetrics("JwtValidation", metadata);

        // Then
        assertNotNull(metrics);

        // Check that individual metrics files were created
        File[] metricFiles = tempDir.toFile().listFiles((dir, name) -> 
                name.startsWith("metrics-JwtValidation-") && name.endsWith(".json"));
        assertNotNull(metricFiles);
        assertEquals(1, metricFiles.length);

        // Verify the content of the metrics file
        try (FileReader reader = new FileReader(metricFiles[0])) {
            Map<String, Object> jsonData = gson.fromJson(reader, Map.class);
            
            // Should have the benchmark key
            assertTrue(jsonData.containsKey("validateValidationThroughput"));
            
            Map<String, Object> benchmarkData = (Map<String, Object>) jsonData.get("validateValidationThroughput");
            assertNotNull(benchmarkData);
            
            // Should have timestamp and steps
            assertTrue(benchmarkData.containsKey("timestamp"));
            assertTrue(benchmarkData.containsKey("steps"));
            
            Map<String, Object> steps = (Map<String, Object>) benchmarkData.get("steps");
            assertNotNull(steps);
            
            // Should have all the synthetic steps
            assertTrue(steps.containsKey("cache_lookup"));
            assertTrue(steps.containsKey("token_parsing"));
            assertTrue(steps.containsKey("signature_validation"));
            assertTrue(steps.containsKey("complete_validation"));
            
            // Verify structure of a step
            Map<String, Object> cacheLookup = (Map<String, Object>) steps.get("cache_lookup");
            assertEquals(1000.0, cacheLookup.get("sample_count"));
            assertEquals(0.3, cacheLookup.get("p50_us"));
            assertEquals(0.5, cacheLookup.get("p95_us"));
            assertEquals(0.7, cacheLookup.get("p99_us"));
        }

        // Check aggregated jwt-validation-metrics.json
        File aggregatedFile = new File(tempDir.toFile(), "jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            assertTrue(aggregatedData.containsKey("validateValidationThroughput"));
        }
    }

    @Test
    void shouldFormatNumbersCorrectly() throws Exception {
        // Given
        BenchmarkMetrics.BenchmarkMetadata metadata = BenchmarkMetrics.BenchmarkMetadata.builder()
                .threadCount(1)
                .iterations(1)
                .warmupDurationSeconds(0)
                .measurementDurationSeconds(1)
                .serviceUrl("https://localhost:10443")
                .jvmInfo("Test JVM")
                .systemInfo("Test System")
                .build();

        // When
        BenchmarkMetrics metrics = exporter.exportMetrics("JwtValidation", metadata);

        // Then - check the aggregated file
        File aggregatedFile = new File(tempDir.toFile(), "jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        // Read the JSON content as a string to verify number formatting
        String jsonContent = java.nio.file.Files.readString(aggregatedFile.toPath());

        // Verify that values < 10 have one decimal place
        assertTrue(jsonContent.contains("\"p50_us\": 7.6"), "Values < 10 should have one decimal place");
        assertTrue(jsonContent.contains("\"p50_us\": 0.3"), "Values < 10 should have one decimal place");

        // Verify that values >= 10 are integers without decimal point
        assertTrue(jsonContent.contains("\"p50_us\": 70"), "Values >= 10 should be integers without decimal");
        assertTrue(jsonContent.contains("\"p99_us\": 150"), "Values >= 10 should be integers without decimal");
        
        // Ensure no .0 suffix for integers
        assertFalse(jsonContent.contains("13.0"), "Values >= 10 should not have .0 suffix");
        assertFalse(jsonContent.contains("70.0"), "Values >= 10 should not have .0 suffix");
    }

    @Test
    void shouldHandleMultipleBenchmarks() throws Exception {
        // Given
        BenchmarkMetrics.BenchmarkMetadata metadata = BenchmarkMetrics.BenchmarkMetadata.builder()
                .threadCount(1)
                .iterations(1)
                .warmupDurationSeconds(0)
                .measurementDurationSeconds(1)
                .serviceUrl("https://localhost:10443")
                .jvmInfo("Test JVM")
                .systemInfo("Test System")
                .build();

        // When - export metrics for multiple benchmarks
        exporter.exportMetrics("JwtValidation", metadata);
        Thread.sleep(10); // Ensure different timestamp
        exporter.exportMetrics("JwtHealth", metadata);
        Thread.sleep(10); // Ensure different timestamp
        exporter.exportMetrics("JwtEcho", metadata);

        // Then - check aggregated file contains all benchmarks
        File aggregatedFile = new File(tempDir.toFile(), "jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            
            // Should have entries for all three benchmarks
            assertTrue(aggregatedData.containsKey("validateValidationThroughput"));
            assertTrue(aggregatedData.containsKey("validateHealthThroughput"));
            assertTrue(aggregatedData.containsKey("validateEchoThroughput"));
            
            // Each should have the expected structure
            for (String benchmarkKey : aggregatedData.keySet()) {
                Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get(benchmarkKey);
                assertTrue(benchmarkData.containsKey("timestamp"));
                assertTrue(benchmarkData.containsKey("steps"));
            }
        }
    }
}