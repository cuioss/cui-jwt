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
 * Unit tests for {@link BenchmarkResultProcessor} using JUnit Jupiter API.
 * <p>
 * Tests comprehensive artifact generation pipeline including badges, reports,
 * metrics, and GitHub Pages structure creation.
 */
class BenchmarkResultProcessorTest {

    @Test
    void completeArtifactGeneration(@TempDir Path tempDir) throws Exception {
        // Run minimal benchmark for testing
        var options = new OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(1)
                .forks(1)
                .build();

        Collection<RunResult> results = new Runner(options).run();

        // Process results
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(BenchmarkType.MICRO);
        String outputDir = tempDir.toString();
        processor.processResults(results, outputDir);

        // Verify all artifacts were created using JUnit Jupiter assertions
        assertTrue(Files.exists(Path.of(outputDir, "badges/performance-badge.json")),
                "Performance badge should be generated");
        assertTrue(Files.exists(Path.of(outputDir, "badges/trend-badge.json")),
                "Trend badge should be generated");
        assertTrue(Files.exists(Path.of(outputDir, "badges/last-run-badge.json")),
                "Last run badge should be generated");
        assertTrue(Files.exists(Path.of(outputDir, "data/metrics.json")),
                "Metrics file should be generated");
        assertTrue(Files.exists(Path.of(outputDir, "index.html")),
                "Index HTML report should be generated");
        assertTrue(Files.exists(Path.of(outputDir, "trends.html")),
                "Trends HTML report should be generated");
        assertTrue(Files.isDirectory(Path.of(outputDir, "gh-pages-ready")),
                "GitHub Pages directory should be created");
        assertTrue(Files.exists(Path.of(outputDir, "benchmark-summary.json")),
                "Benchmark summary should be generated");

        // Verify badge content structure
        String badgeContent = Files.readString(Path.of(outputDir, "badges/performance-badge.json"));
        assertNotNull(badgeContent, "Badge content should not be null");
        assertFalse(badgeContent.isEmpty(), "Badge content should not be empty");
        assertTrue(badgeContent.contains("\"schemaVersion\""), "Badge should have schema version");
        assertTrue(badgeContent.contains("\"label\""), "Badge should have label");
        assertTrue(badgeContent.contains("\"message\""), "Badge should have message");
        assertTrue(badgeContent.contains("\"color\""), "Badge should have color");
    }

    @Test
    void emptyResultsHandling(@TempDir Path tempDir) {
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(BenchmarkType.MICRO);
        List<RunResult> emptyResults = List.of();

        String outputDir = tempDir.toString();

        // Should handle empty results gracefully
        assertDoesNotThrow(() -> processor.processResults(emptyResults, outputDir),
                "Processing empty results should not throw exception");

        // Basic directory structure should still be created
        assertTrue(Files.exists(Path.of(outputDir, "badges")),
                "Badges directory should be created even with empty results");
        assertTrue(Files.exists(Path.of(outputDir, "data")),
                "Data directory should be created even with empty results");
    }

    @Test
    void directoryCreation(@TempDir Path tempDir) throws Exception {
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(BenchmarkType.MICRO);
        String outputDir = tempDir.resolve("nested/benchmark/results").toString();

        // Use empty results to test directory creation
        processor.processResults(List.of(), outputDir);

        // Verify nested directories are created
        assertTrue(Files.exists(Path.of(outputDir, "badges")),
                "Nested badges directory should be created");
        assertTrue(Files.exists(Path.of(outputDir, "data")),
                "Nested data directory should be created");
        assertTrue(Files.exists(Path.of(outputDir, "reports")),
                "Nested reports directory should be created");
        assertTrue(Files.exists(Path.of(outputDir, "gh-pages-ready")),
                "Nested GitHub Pages directory should be created");
    }
}