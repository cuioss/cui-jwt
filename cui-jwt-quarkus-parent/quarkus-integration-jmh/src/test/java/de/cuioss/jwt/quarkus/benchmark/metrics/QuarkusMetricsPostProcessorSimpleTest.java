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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified tests for QuarkusMetricsPostProcessor that just verify basic functionality
 */
class QuarkusMetricsPostProcessorSimpleTest {

    @TempDir
    Path tempDir;

    private Gson gson;

    @BeforeEach
    void setUp() throws IOException {
        gson = new GsonBuilder().create();

        // Create test metrics directory structure
        Path testMetricsDir = tempDir.resolve("metrics-download");
        Path testSubDir = testMetricsDir.resolve("1-test");
        Files.createDirectories(testSubDir);

        // Copy sample metrics file to test directory with expected naming pattern
        Path sampleMetrics = Path.of("src/test/resources/quarkus-metrics-test.txt");
        if (Files.exists(sampleMetrics)) {
            Files.copy(sampleMetrics, testSubDir.resolve("jwt-validation-metrics.txt"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void shouldParseMetricsAndCreateOutputFile() throws IOException {
        // Given - processor with the subdirectory containing the metrics file
        String testSubDir = tempDir.resolve("metrics-download").resolve("1-test").toString();
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(testSubDir, tempDir.toString());

        // When - parse metrics data
        Instant testTimestamp = Instant.parse("2025-08-01T14:00:00.000Z");
        processor.parseAndExportQuarkusMetrics(testTimestamp);

        // Then - should create quarkus-metrics.json
        File outputFile = new File(tempDir.toFile(), "quarkus-metrics.json");
        assertTrue(outputFile.exists(), "Output file should exist");

        // Basic validation that file contains expected structure
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            // Should have main sections
            assertTrue(metrics.containsKey("cpu"), "Should contain CPU metrics");
            assertTrue(metrics.containsKey("memory"), "Should contain memory metrics");
            assertTrue(metrics.containsKey("metadata"), "Should contain metadata");

            // Just verify sections exist and are not null
            assertNotNull(metrics.get("cpu"), "CPU metrics should not be null");
            assertNotNull(metrics.get("memory"), "Memory metrics should not be null");
            assertNotNull(metrics.get("metadata"), "Metadata should not be null");
        }
    }

    @Test
    void shouldUseConvenienceMethod() throws IOException {
        // Given - test directory structure with metrics
        String baseDir = tempDir.toString();

        // When - use convenience method
        QuarkusMetricsPostProcessor.parseAndExport(baseDir);

        // Then - should create quarkus-metrics.json in base directory
        File outputFile = new File(baseDir, "quarkus-metrics.json");
        assertTrue(outputFile.exists(), "Should create quarkus-metrics.json in base directory");

        // Verify basic structure
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            assertFalse(metrics.isEmpty(), "Should have parsed metrics");
        }
    }
}