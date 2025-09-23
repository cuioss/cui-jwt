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
package de.cuioss.jwt.wrk.benchmark;

import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.converter.WrkBenchmarkConverter;
import de.cuioss.benchmarking.common.metrics.QuarkusMetricsPostProcessor;
import de.cuioss.benchmarking.common.model.BenchmarkData;
import de.cuioss.benchmarking.common.report.BadgeGenerator;
import de.cuioss.benchmarking.common.report.GitHubPagesGenerator;
import de.cuioss.benchmarking.common.report.ReportGenerator;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Post-processor for WRK benchmark results.
 * <p>
 * This class converts WRK output files to the central BenchmarkData format
 * and uses the unified report generation infrastructure from cui-benchmarking-common.
 */
public class WrkResultPostProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(WrkResultPostProcessor.class);

    private final WrkBenchmarkConverter converter = new WrkBenchmarkConverter();
    private final ReportGenerator reportGenerator = new ReportGenerator();
    private final BadgeGenerator badgeGenerator = new BadgeGenerator();
    private final GitHubPagesGenerator gitHubPagesGenerator = new GitHubPagesGenerator();

    /**
     * Main entry point for processing WRK benchmark results.
     *
     * @param args Command line arguments:
     *             args[0] - Input directory containing WRK output files
     *             args[1] - (Optional) Output directory for reports (defaults to input + "/reports")
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            LOGGER.error("Usage: WrkResultPostProcessor <input-dir> [output-dir]");
            System.exit(1);
        }

        try {
            Path inputDir = Path.of(args[0]);
            Path outputDir = args.length > 1 ?
                    Path.of(args[1]) :
                    inputDir.getParent().resolve("benchmark-results");

            WrkResultPostProcessor processor = new WrkResultPostProcessor();
            processor.process(inputDir, outputDir);

            LOGGER.info("WRK benchmark processing completed successfully");
            LOGGER.info("Results available at: " + outputDir);

        } catch (IOException e) {
            LOGGER.error("Failed to process WRK benchmark results", e);
            System.exit(1);
        }
    }

    /**
     * Processes WRK output files and generates reports.
     *
     * @param inputDir Directory containing WRK output files
     * @param outputDir Directory to write reports to
     * @throws IOException if processing fails
     */
    public void process(Path inputDir, Path outputDir) throws IOException {
        LOGGER.info("Processing WRK results from: " + inputDir);
        LOGGER.info("Output directory: " + outputDir);

        // Validate input directory
        if (!Files.exists(inputDir)) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDir);
        }

        if (!Files.isDirectory(inputDir)) {
            throw new IllegalArgumentException("Input path is not a directory: " + inputDir);
        }

        // Check for WRK output files
        boolean hasWrkFiles = false;
        try (Stream<Path> files = Files.list(inputDir)) {
            hasWrkFiles = files.anyMatch(p ->
                    p.getFileName().toString().contains("wrk") &&
                            p.getFileName().toString().endsWith(".txt")
            );
        }

        if (!hasWrkFiles) {
            LOGGER.warn("No WRK output files found in: " + inputDir);
            LOGGER.info("Looking for files matching pattern: *wrk*.txt");

            // List available files for debugging
            try (Stream<Path> files = Files.list(inputDir)) {
                files.forEach(f -> LOGGER.info("  Found: " + f.getFileName()));
            }

            throw new IllegalArgumentException("No WRK output files found in directory");
        }

        // Convert WRK output to BenchmarkData
        LOGGER.info("Converting WRK output to benchmark data format...");
        BenchmarkData benchmarkData = converter.convert(inputDir);

        if (benchmarkData.getBenchmarks() == null || benchmarkData.getBenchmarks().isEmpty()) {
            LOGGER.warn("No benchmark data extracted from WRK output files");
        } else {
            LOGGER.info("Extracted " + benchmarkData.getBenchmarks().size() + " benchmark results");
        }

        // Generate reports using refactored infrastructure
        LOGGER.info("Generating benchmark reports...");
        Files.createDirectories(outputDir);

        // Generate HTML reports
        reportGenerator.generateIndexPage(benchmarkData, BenchmarkType.INTEGRATION, outputDir.toString());
        reportGenerator.generateTrendsPage(outputDir.toString());
        reportGenerator.generateDetailedPage(outputDir.toString());
        reportGenerator.copySupportFiles(outputDir.toString());

        // Generate GitHub Pages structure
        Path ghPagesDir = outputDir.resolve("gh-pages-ready");
        gitHubPagesGenerator.prepareDeploymentStructure(outputDir.toString(), ghPagesDir.toString());

        LOGGER.info("Generated complete benchmark reports in: " + outputDir);

        // Process Quarkus metrics if available
        processQuarkusMetrics(inputDir.getParent());

        // Log summary
        logSummary(benchmarkData, outputDir);
    }

    /**
     * Logs a summary of the processed benchmark data.
     */
    private void logSummary(BenchmarkData data, Path outputDir) {
        LOGGER.info("=== Benchmark Processing Summary ===");

        if (data.getOverview() != null) {
            BenchmarkData.Overview overview = data.getOverview();
            LOGGER.info("Performance Grade: " + overview.getPerformanceGrade());
            LOGGER.info("Performance Score: " + overview.getPerformanceScore());
            LOGGER.info("Throughput: " + overview.getThroughput());
            LOGGER.info("Latency: " + overview.getLatency());
        }

        if (data.getBenchmarks() != null && !data.getBenchmarks().isEmpty()) {
            LOGGER.info("Benchmarks processed:");
            for (BenchmarkData.Benchmark benchmark : data.getBenchmarks()) {
                LOGGER.info("  - " + benchmark.getName() + ": " + benchmark.getScore());
            }
        }

        LOGGER.info("Reports generated in: " + outputDir);
        LOGGER.info("  - HTML Reports: " + outputDir.resolve("index.html"));
        LOGGER.info("  - Badges: " + outputDir.resolve("badges"));
        LOGGER.info("  - API Endpoints: " + outputDir.resolve("api"));
        LOGGER.info("  - GitHub Pages: " + outputDir.resolve("gh-pages-ready"));
    }

    /**
     * Process Quarkus metrics if they were downloaded during the benchmark run.
     *
     * @param targetDir The target directory containing metrics-download subdirectory
     */
    private void processQuarkusMetrics(Path targetDir) {
        Path metricsDownloadDir = targetDir.resolve("metrics-download");

        if (!Files.exists(metricsDownloadDir)) {
            LOGGER.info("No Quarkus metrics found (directory not present: " + metricsDownloadDir + ")");
            return;
        }

        try {
            // Check if metrics files exist
            boolean hasMetricsFiles = false;
            try (Stream<Path> files = Files.list(metricsDownloadDir)) {
                hasMetricsFiles = files.anyMatch(p ->
                        p.getFileName().toString().endsWith(".txt") &&
                                (p.getFileName().toString().contains("before") ||
                                        p.getFileName().toString().contains("after") ||
                                        p.getFileName().toString().contains("quarkus"))
                );
            }

            if (!hasMetricsFiles) {
                LOGGER.info("No Quarkus metrics files found in: " + metricsDownloadDir);
                return;
            }

            LOGGER.info("Processing Quarkus metrics from: " + metricsDownloadDir);

            // Process metrics using the common processor
            QuarkusMetricsPostProcessor metricsProcessor = new QuarkusMetricsPostProcessor(
                    metricsDownloadDir.toString(),
                    targetDir.toString()
            );
            metricsProcessor.parseAndExportQuarkusMetrics(Instant.now());

            LOGGER.info("Successfully processed Quarkus resource usage metrics");
            LOGGER.info("Metrics output available at: " + targetDir.resolve("quarkus-metrics.json"));

        } catch (IOException e) {
            LOGGER.warn("Failed to process Quarkus metrics: " + e.getMessage());
            // Don't fail the build, just log the warning
        }
    }
}