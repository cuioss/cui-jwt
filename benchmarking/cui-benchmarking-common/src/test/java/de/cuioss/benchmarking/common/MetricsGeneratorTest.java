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

import de.cuioss.benchmarking.common.report.MetricsGenerator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetricsGenerator}.
 */
class MetricsGeneratorTest {

    private final Gson gson = new Gson();

    @Test
    void generateMetricsJsonWithResults(@TempDir Path tempDir) throws Exception {
        // Run a minimal benchmark to get real results
        var options = new OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(1)
                .forks(1)
                .build();

        Collection<RunResult> results = new Runner(options).run();

        MetricsGenerator generator = new MetricsGenerator();
        String outputDir = tempDir.toString();

        generator.generateMetricsJson(results, outputDir);

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

    @Test
    void generateMetricsJsonWithEmptyResults(@TempDir Path tempDir) throws Exception {
        MetricsGenerator generator = new MetricsGenerator();
        String outputDir = tempDir.toString();

        List<RunResult> emptyResults = List.of();

        generator.generateMetricsJson(emptyResults, outputDir);

        // Should still create metrics file
        Path metricsFile = Path.of(outputDir, "metrics.json");
        assertTrue(Files.exists(metricsFile), "Metrics file should be created even with empty results");

        // Verify basic structure
        String content = Files.readString(metricsFile);
        JsonObject metrics = gson.fromJson(content, JsonObject.class);

        assertNotNull(metrics.get("timestamp"), "Timestamp should be present");
        assertTrue(metrics.get("benchmarks").getAsJsonObject().entrySet().isEmpty(),
                "Benchmarks object should be empty");
    }

    @Test
    void verifyMetricsStructure(@TempDir Path tempDir) throws Exception {
        // Run benchmark to get results
        var options = new OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(1)
                .forks(1)
                .build();

        Collection<RunResult> results = new Runner(options).run();

        MetricsGenerator generator = new MetricsGenerator();
        String outputDir = tempDir.toString();

        generator.generateMetricsJson(results, outputDir);

        // Verify metrics file has expected structure
        Path metricsFile = Path.of(outputDir, "metrics.json");
        String content = Files.readString(metricsFile);
        JsonObject metrics = gson.fromJson(content, JsonObject.class);

        // Check benchmarks object
        assertTrue(metrics.get("benchmarks").isJsonObject(),
                "Benchmarks should be an object");
        assertFalse(metrics.get("benchmarks").getAsJsonObject().entrySet().isEmpty(),
                "Benchmarks object should not be empty with results");
    }

    @Test
    void ensureDirectoryCreation(@TempDir Path tempDir) {
        MetricsGenerator generator = new MetricsGenerator();
        String nestedDir = tempDir.resolve("nested/deep/path").toString();

        List<RunResult> emptyResults = List.of();

        // Should create nested directories
        assertDoesNotThrow(() -> generator.generateMetricsJson(emptyResults, nestedDir),
                "Should create nested directories as needed");

        assertTrue(Files.exists(Path.of(nestedDir, "metrics.json")),
                "Metrics file should be created in nested directory");
    }
}