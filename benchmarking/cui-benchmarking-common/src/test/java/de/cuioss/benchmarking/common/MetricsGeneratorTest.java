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
import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.report.MetricsGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetricsGenerator}.
 */
class MetricsGeneratorTest {

    private final Gson gson = new Gson();

    @Test void generateMetricsJsonWithResults(@TempDir Path tempDir) throws Exception {
        // Use real micro benchmark test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        MetricsGenerator generator = new MetricsGenerator();
        String outputDir = tempDir.toString();

        generator.generateMetricsJson(jsonFile, outputDir);

        // Verify metrics.json was created
        Path metricsFile = Path.of(outputDir, "metrics.json");
        assertTrue(Files.exists(metricsFile), "Metrics file should be created");

        // Verify content structure
        String content = Files.readString(metricsFile);
        JsonObject metrics = gson.fromJson(content, JsonObject.class);

        assertNotNull(metrics.get("timestamp"), "Timestamp should be present");
        assertNotNull(metrics.get("benchmarks"), "Benchmarks should be present");
        assertNotNull(metrics.get("summary"), "Summary should be present");

        // Verify timestamp is ISO formatted
        String timestamp = metrics.get("timestamp").getAsString();
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z"),
                "Timestamp should be ISO formatted");
    }

    @Test void generateMetricsJsonWithEmptyResults(@TempDir Path tempDir) throws Exception {
        // Create an empty JSON file
        Path jsonFile = tempDir.resolve("empty-benchmark-result.json");
        Files.writeString(jsonFile, "[]");

        MetricsGenerator generator = new MetricsGenerator();
        String outputDir = tempDir.toString();

        // Should throw since there's no data
        assertThrows(IllegalArgumentException.class,
                () -> generator.generateMetricsJson(jsonFile, outputDir),
                "Should fail fast with empty benchmark data");
    }

    @Test void verifyMetricsStructure(@TempDir Path tempDir) throws Exception {
        // Use real micro benchmark test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        MetricsGenerator generator = new MetricsGenerator();
        String outputDir = tempDir.toString();

        generator.generateMetricsJson(jsonFile, outputDir);

        // Verify metrics file was created
        Path metricsFile = Path.of(outputDir, "metrics.json");
        String content = Files.readString(metricsFile);
        JsonObject metrics = gson.fromJson(content, JsonObject.class);

        // Check structure
        assertTrue(metrics.get("benchmarks").isJsonObject(),
                "Benchmarks should be an object");
        assertFalse(metrics.get("benchmarks").getAsJsonObject().entrySet().isEmpty(),
                "Benchmarks object should contain data");
        assertNotNull(metrics.get("timestamp"), "Timestamp should be present");
        assertNotNull(metrics.get("summary"), "Summary should be present");

        // Check summary has expected fields
        JsonObject summary = metrics.get("summary").getAsJsonObject();
        assertTrue(summary.has("total_benchmarks"));
        assertTrue(summary.has("average_throughput"));
        assertTrue(summary.has("performance_grade"));
    }

    @Test void ensureDirectoryCreation(@TempDir Path tempDir) throws Exception {
        // Use real test data
        Path sourceJson = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        Path jsonFile = tempDir.resolve("integration-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        MetricsGenerator generator = new MetricsGenerator();
        String nestedDir = tempDir.resolve("nested/deep/path").toString();

        // Should create nested directories
        assertDoesNotThrow(() -> generator.generateMetricsJson(jsonFile, nestedDir),
                "Should create nested directories as needed");

        assertTrue(Files.exists(Path.of(nestedDir, "metrics.json")),
                "Metrics file should be created in nested directory");
    }
}