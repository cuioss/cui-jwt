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
import de.cuioss.benchmarking.common.report.BadgeGenerator;
import de.cuioss.benchmarking.common.report.BenchmarkMetrics;
import de.cuioss.benchmarking.common.report.ReportGenerator;
import de.cuioss.benchmarking.common.runner.BenchmarkResultProcessor;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import static de.cuioss.benchmarking.common.TestHelper.createTestMetrics;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Local test runner for generating and viewing benchmark reports in a browser.
 * This class generates HTML reports and badges from test JSON data and prepares
 * them for viewing with the serve-reports.sh script.
 */
class LocalReportGeneratorTest {

    private static final CuiLogger LOGGER = new CuiLogger(LocalReportGeneratorTest.class);

    @Test void generateReports() throws IOException {
        generateLocalPreviewReports();

        // Verify that reports were generated
        Path outputPath = Path.of(TARGET_DIR);
        assertTrue(Files.exists(outputPath.resolve("index.html")), "Index page should be created");
        assertTrue(Files.exists(outputPath.resolve("micro/index.html")), "Micro benchmark index should be created");
        assertTrue(Files.exists(outputPath.resolve("integration/index.html")), "Integration benchmark index should be created");
    }

    private static final String TARGET_DIR = "target/benchmark-reports-preview";
    private static final String MICRO_JSON = "src/test/resources/library-benchmark-results/micro-result.json";
    private static final String INTEGRATION_JSON = "src/test/resources/integration-benchmark-results/integration-result.json";
    private static final String INDEX_TEMPLATE = "/templates/local-preview-index.html";

    private void generateLocalPreviewReports() throws IOException {
        LOGGER.info("Generating benchmark reports for local preview");

        // Create output directory
        Path outputPath = Path.of(TARGET_DIR);
        if (Files.exists(outputPath)) {
            LOGGER.debug("Cleaning existing output directory");
            deleteDirectory(outputPath);
        }
        Files.createDirectories(outputPath);

        // Generate reports for micro benchmarks
        LOGGER.info("Generating micro benchmark reports");
        generateMicroBenchmarkReports(outputPath);

        // Generate reports for integration benchmarks
        LOGGER.info("Generating integration benchmark reports");
        generateIntegrationBenchmarkReports(outputPath);

        // Create a combined index page
        LOGGER.info("Creating combined index page");
        createCombinedIndex(outputPath);

        LOGGER.info("Report generation complete: {}", outputPath.toAbsolutePath());
    }

    private void generateMicroBenchmarkReports(Path outputPath) throws IOException {
        Path microOutputDir = outputPath.resolve("micro");
        Files.createDirectories(microOutputDir);

        // Copy JSON file to expected location (root for processor)
        Path sourceJson = Path.of(MICRO_JSON);
        Path targetJson = microOutputDir.resolve("micro-result.json");
        Files.copy(sourceJson, targetJson);
        LOGGER.debug("Copied micro benchmark JSON data");

        // Generate all reports using the processor
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(
                BenchmarkType.MICRO,
                "measureThroughput",  // Actual benchmark in test data
                "measureAverageTime");  // Actual benchmark in test data
        List<RunResult> emptyResults = List.of(); // Processor will read from JSON file
        processor.processResults(emptyResults, microOutputDir.toString());

        // Also generate individual components for testing (JSON stays in root)
        generateIndividualReports(targetJson, BenchmarkType.MICRO, microOutputDir);
    }

    private void generateIntegrationBenchmarkReports(Path outputPath) throws IOException {
        Path integrationOutputDir = outputPath.resolve("integration");
        Files.createDirectories(integrationOutputDir);

        // Copy JSON file to expected location
        Path sourceJson = Path.of(INTEGRATION_JSON);
        Path targetJson = integrationOutputDir.resolve("integration-result.json");
        Files.copy(sourceJson, targetJson);
        LOGGER.debug("Copied integration benchmark JSON data");

        // Generate all reports using the processor
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(
                BenchmarkType.INTEGRATION,
                "validateJwtThroughput",  // Actual benchmark in test data
                "validateJwtThroughput");  // Integration test has sample mode too
        List<RunResult> emptyResults = List.of(); // Processor will read from JSON file
        processor.processResults(emptyResults, integrationOutputDir.toString());

        // Also generate individual components for testing (JSON stays in root)
        generateIndividualReports(targetJson, BenchmarkType.INTEGRATION, integrationOutputDir);
    }

    private void generateIndividualReports(Path jsonFile, BenchmarkType type, Path outputDir) throws IOException {
        // Generate reports using individual generators for testing
        BenchmarkMetrics metrics;
        if (type == BenchmarkType.INTEGRATION) {
            // For integration tests, use the specific validateJwtThroughput benchmark
            metrics = createTestMetrics(jsonFile, "validateJwtThroughput", "validateJwtThroughput");
        } else {
            // For micro benchmarks, auto-detect
            metrics = createTestMetrics(jsonFile);
        }
        ReportGenerator reportGen = new ReportGenerator(metrics);
        BadgeGenerator badgeGen = new BadgeGenerator();

        String outputDirStr = outputDir.toString();

        // Generate HTML reports
        reportGen.generateIndexPage(jsonFile, type, outputDirStr);
        reportGen.generateTrendsPage(outputDirStr);
        reportGen.generateDetailedPage(outputDirStr);
        reportGen.copySupportFiles(outputDirStr);

        // Generate badges using new API
        Path badgesDir = outputDir.resolve("badges");
        Files.createDirectories(badgesDir);

        // Write performance badge
        String perfBadge = badgeGen.generatePerformanceBadge(metrics);
        Files.writeString(badgesDir.resolve("performance-badge.json"), perfBadge);

        // Write trend badge (no history for test)
        String trendBadge = badgeGen.generateDefaultTrendBadge();
        Files.writeString(badgesDir.resolve("trend-badge.json"), trendBadge);

        // Write last run badge
        String lastRunBadge = badgeGen.generateLastRunBadge(Instant.now());
        Files.writeString(badgesDir.resolve("last-run-badge.json"), lastRunBadge);

        // Ensure data directory exists for other files
        Path dataDir = outputDir.resolve("data");
        Files.createDirectories(dataDir);

        LOGGER.debug("Generated individual report components for {}", type.getDisplayName());
    }

    private void createCombinedIndex(Path outputPath) throws IOException {
        // Load template from resources
        String indexHtml = loadResourceTemplate(INDEX_TEMPLATE);

        // Load CSS from main resources
        String css = loadCss();
        indexHtml = indexHtml.replace("${css}", css);

        // Replace timestamp
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                .format(Instant.now().atOffset(ZoneOffset.UTC));
        indexHtml = indexHtml.replace("${timestamp}", timestamp);

        Files.writeString(outputPath.resolve("index.html"), indexHtml);
        LOGGER.debug("Created combined index page");
    }

    private String loadResourceTemplate(String resourcePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String loadCss() throws IOException {
        // Load the existing report CSS and add custom styles for local preview
        String reportCss = loadMainResourceTemplate("/templates/report-styles.css");
        String customCss = """
            body {
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                min-height: 100vh;
                padding: 20px;
            }
            .container {
                background: white;
                border-radius: 10px;
                padding: 30px;
                box-shadow: 0 10px 40px rgba(0,0,0,0.1);
                max-width: 1200px;
                margin: 0 auto;
            }
            h1 {
                border-bottom: 3px solid #667eea;
                padding-bottom: 10px;
            }
            .report-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                gap: 20px;
                margin-top: 30px;
            }
            .report-card {
                border: 1px solid #e0e0e0;
                border-radius: 8px;
                padding: 20px;
                transition: transform 0.2s, box-shadow 0.2s;
            }
            .report-card:hover {
                transform: translateY(-5px);
                box-shadow: 0 5px 20px rgba(0,0,0,0.1);
            }
            .report-card h2 {
                color: #667eea;
                margin-top: 0;
            }
            .report-links {
                display: flex;
                flex-direction: column;
                gap: 10px;
                margin-top: 15px;
            }
            .report-links a {
                color: #764ba2;
                text-decoration: none;
                padding: 8px 12px;
                border: 1px solid #764ba2;
                border-radius: 5px;
                text-align: center;
                transition: background 0.2s, color 0.2s;
            }
            .report-links a:hover {
                background: #764ba2;
                color: white;
            }
            .info {
                background: #f5f5f5;
                padding: 15px;
                border-radius: 5px;
                margin-top: 20px;
            }
            .timestamp {
                color: #666;
                font-size: 0.9em;
                text-align: center;
                margin-top: 30px;
            }
            """;
        return reportCss + "\n" + customCss;
    }

    private String loadMainResourceTemplate(String resourcePath) throws IOException {
        try (InputStream is = LocalReportGeneratorTest.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Main resource template not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var pathStream = Files.walk(directory)) {
                pathStream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                LOGGER.warn("Failed to delete: {}", file.getAbsolutePath());
                            }
                        });
            }
        }
    }
}