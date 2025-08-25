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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReportGenerator}.
 */
class ReportGeneratorTest {

    @Test
    void generateIndexPageWithResults(@TempDir Path tempDir) throws Exception {
        // Run benchmark to get results
        var options = new OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(1)
                .forks(1)
                .build();

        Collection<RunResult> results = new Runner(options).run();

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(results, outputDir);

        // Verify index.html was created
        Path indexFile = Path.of(outputDir, "index.html");
        assertTrue(Files.exists(indexFile), "Index page should be created");

        // Verify HTML content
        String content = Files.readString(indexFile);
        assertTrue(content.contains("<!DOCTYPE html>"), "Should be valid HTML");
        assertTrue(content.contains("<html"), "Should have HTML tag");
        assertTrue(content.contains("Benchmark Results"), "Should have title");
        assertTrue(content.contains("<style>"), "Should have embedded CSS");
    }

    @Test
    void generateIndexPageWithEmptyResults(@TempDir Path tempDir) throws Exception {
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        List<RunResult> emptyResults = List.of();

        generator.generateIndexPage(emptyResults, outputDir);

        // Should still create index page
        Path indexFile = Path.of(outputDir, "index.html");
        assertTrue(Files.exists(indexFile), "Index page should be created with empty results");

        // Verify basic HTML structure
        String content = Files.readString(indexFile);
        assertTrue(content.contains("<!DOCTYPE html>"), "Should be valid HTML");
        assertTrue(content.contains("<html"), "Should have HTML tag");
    }

    @Test
    void generateTrendsPage(@TempDir Path tempDir) throws Exception {
        // Run benchmark to get results
        var options = new OptionsBuilder()
                .include(TestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(1)
                .forks(1)
                .build();

        Collection<RunResult> results = new Runner(options).run();

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateTrendsPage(results, outputDir);

        // Verify trends.html was created
        Path trendsFile = Path.of(outputDir, "trends.html");
        assertTrue(Files.exists(trendsFile), "Trends page should be created");

        // Verify HTML content
        String content = Files.readString(trendsFile);
        assertTrue(content.contains("<!DOCTYPE html>"), "Should be valid HTML");
        assertTrue(content.contains("Performance Trends"), "Should have trends title");
        assertTrue(content.contains("<style>"), "Should have embedded CSS");
    }

    @Test
    void generatePagesWithNestedDirectory(@TempDir Path tempDir) {
        ReportGenerator generator = new ReportGenerator();
        String nestedDir = tempDir.resolve("reports/html/output").toString();

        List<RunResult> emptyResults = List.of();

        // Should create nested directories
        assertDoesNotThrow(() -> {
            generator.generateIndexPage(emptyResults, nestedDir);
            generator.generateTrendsPage(emptyResults, nestedDir);
        }, "Should create nested directories as needed");

        assertTrue(Files.exists(Path.of(nestedDir, "index.html")),
                "Index page should be created in nested directory");
        assertTrue(Files.exists(Path.of(nestedDir, "trends.html")),
                "Trends page should be created in nested directory");
    }

    @Test
    void generateResponsiveHtml(@TempDir Path tempDir) throws Exception {
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        List<RunResult> emptyResults = List.of();

        generator.generateIndexPage(emptyResults, outputDir);

        Path indexFile = Path.of(outputDir, "index.html");
        String content = Files.readString(indexFile);

        // Verify responsive design elements
        assertTrue(content.contains("viewport"), "Should have viewport meta tag for responsive design");
        assertTrue(content.contains("max-width"), "Should have max-width for responsive layout");
    }

    @Test
    void generateHtmlWithValidCss(@TempDir Path tempDir) throws Exception {
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        List<RunResult> emptyResults = List.of();

        generator.generateIndexPage(emptyResults, outputDir);

        Path indexFile = Path.of(outputDir, "index.html");
        String content = Files.readString(indexFile);

        // Verify CSS is embedded and structured
        assertTrue(content.contains("<style>"), "Should have opening style tag");
        assertTrue(content.contains("</style>"), "Should have closing style tag");
        assertTrue(content.contains("font-family"), "Should define font family");
        assertTrue(content.contains("margin"), "Should have margin styles");
        assertTrue(content.contains("padding"), "Should have padding styles");
    }
}