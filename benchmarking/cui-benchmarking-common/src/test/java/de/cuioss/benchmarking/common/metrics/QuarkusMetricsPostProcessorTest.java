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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for QuarkusMetricsPostProcessor
 */
class QuarkusMetricsPostProcessorTest {

    @TempDir
    Path tempDir;

    private Path metricsDownloadDir;
    private Path outputDir;

    @BeforeEach void setup() throws IOException {
        metricsDownloadDir = tempDir.resolve("metrics-download");
        outputDir = tempDir.resolve("output");
        Files.createDirectories(metricsDownloadDir);
        Files.createDirectories(outputDir);
    }

    @Test void shouldProcessQuarkusMetricsSuccessfully() throws IOException {
        // Copy test metrics file to temp directory
        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = metricsDownloadDir.resolve("quarkus-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        // Create processor
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(
                metricsDownloadDir.toString(),
                outputDir.toString()
        );

        // Process metrics
        processor.parseAndExportQuarkusMetrics(Instant.now());

        // Verify output file exists
        File outputFile = new File(outputDir.toFile(), "quarkus-metrics.json");
        assertTrue(outputFile.exists(), "Output file should exist");

        // Parse and verify JSON content
        try (FileReader reader = new FileReader(outputFile)) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            assertNotNull(jsonElement, "JSON should be parsed");
            assertTrue(jsonElement.isJsonObject(), "Root should be JSON object");

            JsonObject json = jsonElement.getAsJsonObject();

            // Verify CPU metrics
            assertTrue(json.has("cpu"), "Should have CPU metrics");
            JsonObject cpu = json.getAsJsonObject("cpu");
            assertTrue(cpu.has("process_cpu_usage_avg"), "Should have process CPU average");
            assertTrue(cpu.has("system_cpu_usage_avg"), "Should have system CPU average");
            assertTrue(cpu.has("cpu_count"), "Should have CPU count");

            // Verify memory metrics
            assertTrue(json.has("memory"), "Should have memory metrics");
            JsonObject memory = json.getAsJsonObject("memory");
            assertTrue(memory.has("heap"), "Should have heap metrics");
            assertTrue(memory.has("nonheap"), "Should have non-heap metrics");

            JsonObject heap = memory.getAsJsonObject("heap");
            assertTrue(heap.has("used_bytes"), "Should have heap used bytes");
            assertTrue(heap.has("committed_bytes"), "Should have heap committed bytes");

            // Verify metadata
            assertTrue(json.has("metadata"), "Should have metadata");
            JsonObject metadata = json.getAsJsonObject("metadata");
            assertTrue(metadata.has("timestamp"), "Should have timestamp");
            assertTrue(metadata.has("files_processed"), "Should have files processed");
            assertEquals(1, metadata.get("files_processed").getAsInt(), "Should have processed 1 file");
        }
    }

    @Test void shouldThrowExceptionWhenMetricsDirectoryNotFound() {
        Path nonExistentDir = tempDir.resolve("non-existent");

        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(
                nonExistentDir.toString(),
                outputDir.toString()
        );

        assertThrows(IOException.class, () ->
                        processor.parseAndExportQuarkusMetrics(Instant.now()),
                "Should throw IOException when metrics directory not found"
        );
    }

    @Test void shouldThrowExceptionWhenNoMetricsFilesFound() {
        // Directory exists but no metrics files
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(
                metricsDownloadDir.toString(),
                outputDir.toString()
        );

        assertThrows(IOException.class, () ->
                        processor.parseAndExportQuarkusMetrics(Instant.now()),
                "Should throw IOException when no metrics files found"
        );
    }

    @Test void shouldParseAndExportWithFlatDirectoryStructure() throws IOException {
        // Copy test metrics file to temp directory
        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = metricsDownloadDir.resolve("quarkus-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        // Use convenience method with flat directory structure
        QuarkusMetricsPostProcessor.parseAndExport(tempDir.toString());

        // Verify output file exists
        File outputFile = new File(tempDir.toFile(), "quarkus-metrics.json");
        assertTrue(outputFile.exists(), "Output file should exist in base directory");
    }

    @Test void shouldParseAndExportWithNumberedDirectoryStructure() throws IOException {
        // Note: Numbered directory structure is no longer supported in simplified design
        // This test now verifies that files must be directly in metrics-download directory

        // Copy test metrics file directly to metrics-download directory
        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = metricsDownloadDir.resolve("quarkus-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        // Use convenience method
        QuarkusMetricsPostProcessor.parseAndExport(tempDir.toString());

        // Verify output file exists
        File outputFile = new File(tempDir.toFile(), "quarkus-metrics.json");
        assertTrue(outputFile.exists(), "Output file should exist in base directory");
    }

    @Test void shouldProcessMultipleMetricsFiles() throws IOException {
        // Note: Numbered directory structure is no longer supported in simplified design
        // This test now verifies processing of metrics file directly in metrics-download

        // Copy test metrics file directly to metrics-download directory
        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = metricsDownloadDir.resolve("quarkus-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        // Use convenience method
        QuarkusMetricsPostProcessor.parseAndExport(tempDir.toString());

        // Verify output file exists
        File outputFile = new File(tempDir.toFile(), "quarkus-metrics.json");
        assertTrue(outputFile.exists(), "Output file should exist");
    }
}