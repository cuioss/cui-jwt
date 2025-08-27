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

    @Test void generateIndexPageWithResults(@TempDir Path tempDir) throws Exception {
        // Use real micro benchmark test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);
        generator.copySupportFiles(outputDir);

        // Verify benchmark-data.json was created in data subdirectory
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        assertTrue(Files.exists(dataFile), "Data JSON file should be created");

        // Verify index.html was created
        Path indexFile = Path.of(outputDir, "index.html");
        assertTrue(Files.exists(indexFile), "Index page should be created");

        // Verify HTML content
        String content = Files.readString(indexFile);
        assertTrue(content.contains("<!DOCTYPE html>"), "Should be valid HTML");
        assertTrue(content.contains("<html"), "Should have HTML tag");
        assertTrue(content.contains("CUI JWT Benchmarking Results"), "Should have title");
        assertTrue(content.contains("data-loader.js"), "Should reference data loader script");
    }

    @Test void generateIndexPageWithEmptyResults(@TempDir Path tempDir) throws Exception {
        // Create an empty JSON file
        Path jsonFile = tempDir.resolve("empty-benchmark-result.json");
        Files.writeString(jsonFile, "[]");

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.INTEGRATION, outputDir);
        generator.copySupportFiles(outputDir);

        // Should still create data and index files
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        assertTrue(Files.exists(dataFile), "Data JSON file should be created with empty results");

        Path indexFile = Path.of(outputDir, "index.html");
        assertTrue(Files.exists(indexFile), "Index page should be created with empty results");

        // Verify basic HTML structure
        String content = Files.readString(indexFile);
        assertTrue(content.contains("<!DOCTYPE html>"), "Should be valid HTML");
        assertTrue(content.contains("<html"), "Should have HTML tag");
    }

    @Test void generateTrendsPage(@TempDir Path tempDir) throws Exception {
        // First generate the data file
        Path sourceJson = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        Path jsonFile = tempDir.resolve("integration-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        // Generate data and trends page
        generator.generateIndexPage(jsonFile, BenchmarkType.INTEGRATION, outputDir);
        generator.generateTrendsPage(outputDir);
        generator.copySupportFiles(outputDir);

        // Verify trends.html was created
        Path trendsFile = Path.of(outputDir, "trends.html");
        assertTrue(Files.exists(trendsFile), "Trends page should be created");

        // Verify HTML content
        String content = Files.readString(trendsFile);
        assertTrue(content.contains("<!DOCTYPE html>"), "Should be valid HTML");
        assertTrue(content.contains("Performance Trends"), "Should have trends title");
        assertTrue(content.contains("data-loader.js"), "Should reference data loader script");
    }

    @Test void generatePagesWithNestedDirectory(@TempDir Path tempDir) throws Exception {
        // Use real test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String nestedDir = tempDir.resolve("reports/html/output").toString();

        // Should create nested directories
        assertDoesNotThrow(() -> {
            generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, nestedDir);
            generator.generateTrendsPage(nestedDir);
            generator.copySupportFiles(nestedDir);
        }, "Should create nested directories as needed");

        assertTrue(Files.exists(Path.of(nestedDir, "index.html")),
                "Index page should be created in nested directory");
        assertTrue(Files.exists(Path.of(nestedDir, "trends.html")),
                "Trends page should be created in nested directory");
    }

    @Test void generateResponsiveHtml(@TempDir Path tempDir) throws Exception {
        // Use real test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);
        generator.copySupportFiles(outputDir);

        Path indexFile = Path.of(outputDir, "index.html");
        String content = Files.readString(indexFile);

        // Verify responsive design elements
        assertTrue(content.contains("viewport"), "Should have viewport meta tag for responsive design");

        Path cssFile = Path.of(outputDir, "report-styles.css");
        String css = Files.readString(cssFile);
        assertTrue(css.contains("max-width"), "Should have max-width for responsive layout");
    }

    @Test void generateHtmlWithValidCss(@TempDir Path tempDir) throws Exception {
        // Use real test data
        Path sourceJson = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        Path jsonFile = tempDir.resolve("integration-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.INTEGRATION, outputDir);
        generator.copySupportFiles(outputDir);

        Path indexFile = Path.of(outputDir, "index.html");
        String content = Files.readString(indexFile);

        // Verify CSS is referenced
        assertTrue(content.contains("report-styles.css"), "Should reference CSS file");

        Path cssFile = Path.of(outputDir, "report-styles.css");
        assertTrue(Files.exists(cssFile), "CSS file should be copied");
        String css = Files.readString(cssFile);
        assertTrue(css.contains("font-family"), "Should define font family");
        assertTrue(css.contains("margin"), "Should have margin styles");
        assertTrue(css.contains("padding"), "Should have padding styles");
    }

    @Test void correctLatencyDisplayInGeneratedReport(@TempDir Path tempDir) throws Exception {
        // Regression test for bug where us/op was incorrectly converted (multiplied by 1000 instead of divided)
        // The test data micro-benchmark-result.json has specific known values that should produce exact output
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();

        generator.generateIndexPage(jsonFile, BenchmarkType.MICRO, outputDir);

        // Read generated data JSON from data subdirectory
        Path dataFile = Path.of(outputDir, "data", "benchmark-data.json");
        String jsonData = Files.readString(dataFile);

        // The exact expected average latency for micro-benchmark-result.json test data is:
        // (0.00967 + 0.00812 + 0.00525 + 0.8029 + 0.9273) / 5 = 0.3507 ms
        // Formatted with %.1f this becomes "0.4 ms" or "0,4 ms" depending on locale
        // But the actual test shows it's formatted as "0.35 ms" with %.2f for values < 1
        // So we need to check for the locale-independent number in the JSON
        assertTrue(jsonData.contains("0.35 ms") || jsonData.contains("0,35 ms"),
                "JSON data must contain the average latency '0.35 ms' (locale-dependent decimal separator)");

        // Most importantly, ensure it's NOT showing incorrect values like 865.1 seconds
        assertFalse(jsonData.contains("865") || jsonData.contains("346"),
                "Latency must not show incorrect values like 865.1s or 346s from the conversion bug");
    }
}