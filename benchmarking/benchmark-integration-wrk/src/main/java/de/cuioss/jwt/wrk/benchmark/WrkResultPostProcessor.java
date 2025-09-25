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
import de.cuioss.benchmarking.common.metrics.MetricsOrchestrator;
import de.cuioss.benchmarking.common.model.BenchmarkData;
import de.cuioss.benchmarking.common.report.BadgeGenerator;
import de.cuioss.benchmarking.common.report.GitHubPagesGenerator;
import de.cuioss.benchmarking.common.report.ReportGenerator;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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

    // Timestamps for benchmark execution tracking
    private Instant benchmarkStartTime;
    private Instant benchmarkEndTime;

    /**
     * Main entry point for processing WRK benchmark results.
     * Usage:
     *   - args[0]=inputDir, args[1]=outputDir (optional)
     *   - args[0]=inputDir, args[1]="download-after" (metrics download only)
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            LOGGER.error("Usage: WrkResultPostProcessor <input-dir> [output-dir|download-after]");
            System.exit(1);
        }

        try {
            Path inputDir = Path.of(args[0]);
            WrkResultPostProcessor processor = new WrkResultPostProcessor();

            // Check if this is metrics-only mode
            if (args.length > 1 && "download-after".equals(args[1])) {
                LOGGER.info("Running in metrics-download-only mode");
                processor.processQuarkusMetrics(inputDir.getParent());
                LOGGER.info("Metrics download completed successfully");
                return;
            }

            // Normal processing mode
            Path outputDir = args.length > 1 ?
                    Path.of(args[1]) :
                    inputDir.getParent().resolve("benchmark-results");

            processor.process(inputDir, outputDir);

            LOGGER.info("WRK benchmark processing completed successfully");
            LOGGER.info("Results available at: " + outputDir);

        } catch (IOException e) {
            LOGGER.error("Failed to execute WRK result processor", e);
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

        // Try to read actual benchmark timestamps from file
        readBenchmarkTimestamps(inputDir);

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

        // Process Quarkus metrics if available - use outputDir since gh-pages-ready directory was created there
        processQuarkusMetrics(outputDir);

        // Collect real-time Prometheus metrics for the benchmark execution
        collectPrometheusMetrics(benchmarkData, outputDir);

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
     * Download and process Quarkus metrics from the running service.
     *
     * @param targetDir The target directory to store metrics
     */
    private void processQuarkusMetrics(Path targetDir) {
        String metricsUrl = System.getProperty("quarkus.metrics.url", "https://localhost:10443");
        Path downloadsDir = targetDir.resolve("metrics-download");

        try {
            // Create downloads directory if it doesn't exist
            if (!Files.exists(downloadsDir)) {
                Files.createDirectories(downloadsDir);
                LOGGER.info("Created metrics download directory: " + downloadsDir);
            }

            LOGGER.info("Downloading and processing Quarkus metrics from: " + metricsUrl);

            // Create orchestrator and download metrics
            MetricsOrchestrator orchestrator = new MetricsOrchestrator(
                    metricsUrl,
                    downloadsDir,
                    targetDir
            );

            // This will download metrics from the URL and process them, including creating quarkus-metrics.json
            orchestrator.processQuarkusMetrics("WrkBenchmark");

            LOGGER.info("Successfully downloaded and processed Quarkus metrics");
            LOGGER.info("Metrics output available at: " + targetDir.resolve("jwt-validation-metrics.json"));

        } catch (IOException e) {
            LOGGER.warn("Failed to download/process Quarkus metrics: " + e.getMessage());
            LOGGER.warn("Make sure the Quarkus service is running at: " + metricsUrl);
            // Don't fail the build, just log the warning
        }
    }

    /**
     * Collect real-time metrics from Prometheus for the benchmark execution.
     * This captures time-series data during the actual benchmark run.
     *
     * @param benchmarkData The benchmark data containing benchmark names
     * @param targetDir The target directory to store metrics
     */
    private void collectPrometheusMetrics(BenchmarkData benchmarkData, Path targetDir) {
        // Skip if timestamps are not properly set
        if (benchmarkStartTime == null || benchmarkEndTime == null) {
            LOGGER.warn("Cannot collect Prometheus metrics: timestamps not captured during benchmark execution");
            return;
        }

        try {
            // Get Prometheus URL from system property or use default
            String prometheusUrl = System.getProperty("prometheus.url", "http://localhost:9090");
            String metricsUrl = System.getProperty("quarkus.metrics.url", "https://localhost:10443");
            Path downloadsDir = targetDir.resolve("metrics-download");
            Path prometheusDir = targetDir.resolve("prometheus");

            // Create Prometheus output directory
            Files.createDirectories(prometheusDir);

            LOGGER.info("Collecting real-time metrics from Prometheus at: {}", prometheusUrl);

            // Create orchestrator with Prometheus client
            MetricsOrchestrator orchestrator = new MetricsOrchestrator(
                    metricsUrl,
                    downloadsDir,
                    targetDir,
                    new de.cuioss.benchmarking.common.metrics.PrometheusClient(prometheusUrl)
            );

            // Process each benchmark result
            if (benchmarkData.getBenchmarks() != null) {
                for (BenchmarkData.Benchmark benchmark : benchmarkData.getBenchmarks()) {
                    String benchmarkName = extractBenchmarkName(benchmark.getName());

                    LOGGER.info("Collecting Prometheus metrics for benchmark: {}", benchmarkName);

                    // Collect real-time metrics for this benchmark
                    orchestrator.collectBenchmarkMetrics(
                            benchmarkName,
                            benchmarkStartTime,
                            benchmarkEndTime,
                            prometheusDir
                    );

                    LOGGER.info("Prometheus metrics saved to: {}/{}-metrics.json",
                            prometheusDir, benchmarkName);
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to collect Prometheus metrics: {}", e.getMessage());
            // Don't fail the build, just log the warning
        }
    }

    /**
     * Read benchmark timestamps from the timestamp file created by run_benchmark.sh.
     *
     * @param inputDir The directory containing benchmark results
     * @throws IllegalStateException if timestamps cannot be read
     */
    private void readBenchmarkTimestamps(Path inputDir) {
        Path timestampFile = inputDir.resolve("benchmark-timestamps.txt");

        if (!Files.exists(timestampFile)) {
            // Try parent directory (common location for benchmark-results)
            timestampFile = inputDir.getParent().resolve("benchmark-results").resolve("benchmark-timestamps.txt");
        }

        if (!Files.exists(timestampFile)) {
            String message = String.format(
                "CRITICAL: Benchmark timestamp file not found at: %s\n" +
                "Real-time metrics collection requires actual benchmark timestamps.\n" +
                "Ensure the benchmark was run with the updated run_benchmark.sh script that captures timestamps.",
                timestampFile
            );
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }

        try {
            List<String> lines = Files.readAllLines(timestampFile);
            boolean foundStartTime = false;
            boolean foundEndTime = false;

            for (String line : lines) {
                if (line.startsWith("benchmark_start_time=")) {
                    long epochSeconds = Long.parseLong(line.substring(21));
                    benchmarkStartTime = Instant.ofEpochSecond(epochSeconds);
                    foundStartTime = true;
                    LOGGER.info("Read benchmark start time: {}", benchmarkStartTime);
                } else if (line.startsWith("benchmark_end_time=")) {
                    long epochSeconds = Long.parseLong(line.substring(19));
                    benchmarkEndTime = Instant.ofEpochSecond(epochSeconds);
                    foundEndTime = true;
                    LOGGER.info("Read benchmark end time: {}", benchmarkEndTime);
                }
            }

            if (!foundStartTime || !foundEndTime) {
                String message = String.format(
                    "CRITICAL: Incomplete timestamp data in file %s\n" +
                    "Found start time: %s, Found end time: %s\n" +
                    "Both timestamps are required for real-time metrics collection.",
                    timestampFile, foundStartTime, foundEndTime
                );
                LOGGER.error(message);
                throw new IllegalStateException(message);
            }

        } catch (IOException e) {
            String message = String.format(
                "CRITICAL: Failed to read benchmark timestamps from file %s: %s",
                timestampFile, e.getMessage()
            );
            LOGGER.error(message, e);
            throw new IllegalStateException(message, e);
        } catch (NumberFormatException e) {
            String message = String.format(
                "CRITICAL: Invalid timestamp format in file %s: %s",
                timestampFile, e.getMessage()
            );
            LOGGER.error(message, e);
            throw new IllegalStateException(message, e);
        }
    }

    /**
     * Extract a clean benchmark name from the WRK output file path.
     *
     * @param fullPath The full benchmark name/path from WRK output
     * @return A clean benchmark name suitable for file naming
     */
    private String extractBenchmarkName(String fullPath) {
        // Extract just the benchmark name from the full path
        // Example: "wrk-benchmark-results.txt" -> "wrk-benchmark"
        String name = fullPath;

        // Remove file extension
        int extIndex = name.lastIndexOf('.');
        if (extIndex > 0) {
            name = name.substring(0, extIndex);
        }

        // Remove common suffixes
        if (name.endsWith("-results")) {
            name = name.substring(0, name.length() - 8);
        }

        // Remove path if present
        int pathIndex = name.lastIndexOf('/');
        if (pathIndex >= 0) {
            name = name.substring(pathIndex + 1);
        }

        return name;
    }
}