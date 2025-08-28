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
package de.cuioss.benchmarking.common;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.report.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ReportGenerator}.
 */
class ReportGeneratorTest {

    private static final Gson gson = new Gson();

    @Test void generateDataJsonWithResults(@TempDir Path tempDir) throws Exception {
        // Use real micro benchmark test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);

        // Verify benchmark-data.json was created with correct structure
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        assertTrue(Files.exists(dataFile), "Data JSON file should be created");

        // Parse and verify JSON structure
        String jsonContent = Files.readString(dataFile);
        JsonObject dataJson = gson.fromJson(jsonContent, JsonObject.class);

        assertNotNull(dataJson.get("metadata"), "Should have metadata section");
        assertNotNull(dataJson.get("overview"), "Should have overview section");
        assertNotNull(dataJson.get("benchmarks"), "Should have benchmarks array");
        assertNotNull(dataJson.get("chartData"), "Should have chartData section");
        assertNotNull(dataJson.get("percentilesData"), "Should have percentilesData section");
        assertNotNull(dataJson.get("trends"), "Should have trends section");

        // Verify benchmarks were processed
        JsonArray benchmarks = dataJson.getAsJsonArray("benchmarks");
        assertEquals(5, benchmarks.size(), "Should have 5 benchmarks from test data");
    }

    @Test void generateDataJsonWithEmptyResults(@TempDir Path tempDir) throws Exception {
        // Create an empty JSON file
        Path jsonFile = tempDir.resolve("empty-benchmark-result.json");
        Files.writeString(jsonFile, "[]");

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.INTEGRATION, outputDir);

        // Should still create data JSON with empty results
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        assertTrue(Files.exists(dataFile), "Data JSON file should be created with empty results");

        // Parse and verify JSON structure with empty data
        String jsonContent = Files.readString(dataFile);
        JsonObject dataJson = gson.fromJson(jsonContent, JsonObject.class);

        // Should have all sections even with empty data
        assertNotNull(dataJson.get("metadata"), "Should have metadata section");
        assertNotNull(dataJson.get("overview"), "Should have overview section");
        assertNotNull(dataJson.get("benchmarks"), "Should have benchmarks array");

        // Verify benchmarks array is empty
        JsonArray benchmarks = dataJson.getAsJsonArray("benchmarks");
        assertEquals(0, benchmarks.size(), "Should have no benchmarks with empty input");
    }

    @Test void generateDataJsonWithIntegrationBenchmarks(@TempDir Path tempDir) throws Exception {
        // Use integration benchmark test data
        Path sourceJson = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        Path jsonFile = tempDir.resolve("integration-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.INTEGRATION, outputDir);

        // Verify data JSON was created
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        assertTrue(Files.exists(dataFile), "Data JSON file should be created");

        // Parse and verify it's marked as integration
        String jsonContent = Files.readString(dataFile);
        JsonObject dataJson = gson.fromJson(jsonContent, JsonObject.class);

        JsonObject metadata = dataJson.getAsJsonObject("metadata");
        assertEquals("Integration Performance", metadata.get("benchmarkType").getAsString(),
                "Should be marked as Integration Performance");
        assertEquals("INTEGRATION", metadata.get("benchmarkTypeId").getAsString(),
                "Should have INTEGRATION type ID");
    }

    @Test void generateDataJsonInNestedDirectory(@TempDir Path tempDir) throws Exception {
        // Test that generator creates directories as needed
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String nestedDir = tempDir.resolve("reports/html/output").toString();

        // Should create nested directories for data JSON
        assertDoesNotThrow(() -> {
            generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, nestedDir);
        }, "Should create nested directories as needed");

        // Verify data JSON was created in nested structure
        assertTrue(Files.exists(Path.of(nestedDir, "data/benchmark-data.json")),
                "Data JSON should be created in nested directory structure");
        assertTrue(Files.exists(Path.of(nestedDir, "data/benchmark-result.json")),
                "Original JSON should be copied to nested data directory");
    }

    @Test void verifyDataJsonContainsAllRequiredSections(@TempDir Path tempDir) throws Exception {
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);

        // Verify all required JSON sections are present and populated
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        String jsonContent = Files.readString(dataFile);
        JsonObject dataJson = gson.fromJson(jsonContent, JsonObject.class);

        // Verify metadata
        JsonObject metadata = dataJson.getAsJsonObject("metadata");
        assertNotNull(metadata.get("timestamp"), "Should have timestamp");
        assertNotNull(metadata.get("version"), "Should have version");

        // Verify overview metrics
        JsonObject overview = dataJson.getAsJsonObject("overview");
        assertTrue(overview.has("avgThroughput"), "Should have average throughput");
        assertTrue(overview.has("avgLatency"), "Should have average latency");
        assertTrue(overview.has("performanceScore"), "Should have performance score");
        assertTrue(overview.has("performanceGrade"), "Should have performance grade");
    }

    @Test void verifyPercentilesDataOnlyIncludesLatencyBenchmarks(@TempDir Path tempDir) throws Exception {
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);

        // Verify percentiles data only includes latency benchmarks (not throughput)
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        String jsonContent = Files.readString(dataFile);
        JsonObject dataJson = gson.fromJson(jsonContent, JsonObject.class);

        JsonObject percentilesData = dataJson.getAsJsonObject("percentilesData");
        JsonArray benchmarkNames = percentilesData.getAsJsonArray("benchmarks");

        // Should only include avgt benchmarks (latency), not thrpt (throughput)
        for (JsonElement nameElement : benchmarkNames) {
            String name = nameElement.getAsString();
            // The test data has measureAverageTime and measureConcurrentValidation as latency benchmarks
            assertTrue(name.contains("measure"),
                    "Percentiles should only include latency benchmarks: " + name);
        }
    }

    @Test void correctLatencyDisplayInGeneratedReport(@TempDir Path tempDir) throws Exception {
        // Regression test for bug where us/op was incorrectly converted (multiplied by 1000 instead of divided)
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);

        // Parse the generated JSON data
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        String jsonContent = Files.readString(dataFile);
        JsonObject dataJson = gson.fromJson(jsonContent, JsonObject.class);

        // Verify the overview section has correct average latency
        JsonObject overview = dataJson.getAsJsonObject("overview");
        assertNotNull(overview, "Overview section must exist");

        // The average latency should be around 0.35 ms (calculated from test data)
        // Test data has: 0.00967, 0.00812, 0.00525, 0.8029, 0.9273 ms
        // Average: (0.00967 + 0.00812 + 0.00525 + 0.8029 + 0.9273) / 5 = 0.35064 ms
        double avgLatency = overview.get("avgLatency").getAsDouble();
        assertEquals(0.35, avgLatency, 0.01,
                "Average latency should be correctly calculated as ~0.35 ms");

        // Verify individual benchmark latencies are correctly converted from us/op to ms
        JsonArray benchmarks = dataJson.getAsJsonArray("benchmarks");
        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String mode = benchmark.get("mode").getAsString();

            // For latency benchmarks (avgt mode), verify latency is in reasonable range
            if ("avgt".equals(mode)) {
                double latency = benchmark.get("latency").getAsDouble();
                assertTrue(latency > 0 && latency < 10,
                        "Latency should be positive and less than 10ms (not in seconds): " + latency);
            }
        }
    }
}