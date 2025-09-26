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
import de.cuioss.benchmarking.common.metrics.PrometheusMetricsManager;
import de.cuioss.benchmarking.common.model.BenchmarkData;
import de.cuioss.benchmarking.common.report.GitHubPagesGenerator;
import de.cuioss.benchmarking.common.report.ReportGenerator;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Post-processor for WRK benchmark results.
 * <p>
 * This class converts WRK output files to the central BenchmarkData format
 * and uses the unified report generation infrastructure from cui-benchmarking-common.
 */
public class WrkResultPostProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(WrkResultPostProcessor.class);

    // File naming constants
    public static final String WRK_OUTPUT_FILE_SUFFIX = "-results.txt";
    public static final String WRK_HEALTH_OUTPUT_FILE = "wrk-health-results.txt";
    public static final String WRK_JWT_OUTPUT_FILE = "wrk-jwt-results.txt";
    public static final String BENCHMARK_NAME_HEALTH = "healthCheck";
    public static final String BENCHMARK_NAME_JWT = "jwtValidation";
    public static final String PROMETHEUS_METRICS_DIR = "prometheus";
    public static final String GH_PAGES_DATA_DIR = "gh-pages-ready/data";
    public static final String HEALTH_METRICS_FILE = BENCHMARK_NAME_HEALTH + "-metrics.json";
    public static final String JWT_METRICS_FILE = BENCHMARK_NAME_JWT + "-metrics.json";

    private final WrkBenchmarkConverter converter = new WrkBenchmarkConverter();
    private final ReportGenerator reportGenerator = new ReportGenerator();
    private final GitHubPagesGenerator gitHubPagesGenerator = new GitHubPagesGenerator();
    private final PrometheusMetricsManager prometheusMetricsManager = new PrometheusMetricsManager();

    // Map to store benchmark metadata (name -> timestamps)
    private final Map<String, BenchmarkMetadata> benchmarkMetadataMap = new HashMap<>();

    /**
         * Holds metadata for a benchmark execution.
         */
        private record BenchmarkMetadata(String name, Instant startTime, Instant endTime) {
    }

    /**
     * Main entry point for processing WRK benchmark results.
     * Usage:
     *   - args[0]=inputDir, args[1]=outputDir (optional)
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            LOGGER.error("Usage: WrkResultPostProcessor <input-dir> [output-dir]");
            System.exit(1);
        }

        try {
            Path inputDir = Path.of(args[0]);
            WrkResultPostProcessor processor = new WrkResultPostProcessor();

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

        // Parse all WRK result files to extract metadata
        parseBenchmarkMetadata(inputDir);

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

        // Collect real-time Prometheus metrics for the benchmark execution BEFORE GitHub Pages generation
        collectPrometheusMetrics(benchmarkData, outputDir);

        // Generate GitHub Pages structure (now includes Prometheus metrics copying)
        Path ghPagesDir = outputDir.resolve("gh-pages-ready");
        gitHubPagesGenerator.prepareDeploymentStructure(outputDir.toString(), ghPagesDir.toString());

        LOGGER.info("Generated complete benchmark reports in: " + outputDir);

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
     * Collect real-time metrics from Prometheus for the benchmark execution.
     * This captures time-series data during the actual benchmark run.
     *
     * @param benchmarkData The benchmark data containing benchmark names
     * @param targetDir The target directory to store metrics
     */
    private void collectPrometheusMetrics(BenchmarkData benchmarkData, Path targetDir) {
        // Skip if no metadata available
        if (benchmarkMetadataMap.isEmpty()) {
            LOGGER.warn("Cannot collect Prometheus metrics: no benchmark metadata available");
            return;
        }

        // Process each benchmark result
        if (benchmarkData.getBenchmarks() != null) {
            for (BenchmarkData.Benchmark benchmark : benchmarkData.getBenchmarks()) {
                // Try to find metadata by matching the benchmark name
                BenchmarkMetadata metadata = findMetadataForBenchmark(benchmark.getName());

                if (metadata == null) {
                    LOGGER.warn("No metadata found for benchmark: {}", benchmark.getName());
                    continue;
                }

                // Use centralized PrometheusMetricsManager to collect metrics
                prometheusMetricsManager.collectMetricsForWrkBenchmark(
                        metadata.name,
                        metadata.startTime,
                        metadata.endTime,
                        targetDir.toString()
                );
            }
        }
    }

    /**
     * Parse benchmark metadata from WRK result files.
     * Each result file contains embedded metadata including name, start time, and end time.
     *
     * @param inputDir The directory containing benchmark results
     * @throws IllegalStateException if no valid benchmark metadata is found
     */
    private void parseBenchmarkMetadata(Path inputDir) throws IOException {
        // Find all WRK result files
        try (Stream<Path> files = Files.list(inputDir)) {
            List<Path> wrkFiles = files
                    .filter(p -> p.getFileName().toString().endsWith(WRK_OUTPUT_FILE_SUFFIX))
                    .toList();

            if (wrkFiles.isEmpty()) {
                String message = """
                        CRITICAL: No WRK result files (*%s) found in %s
                        Ensure benchmarks were run successfully.""".formatted(
                        WRK_OUTPUT_FILE_SUFFIX, inputDir
                );
                LOGGER.error(message);
                throw new IllegalStateException(message);
            }

            // Parse metadata from each file
            for (Path wrkFile : wrkFiles) {
                parseSingleBenchmarkMetadata(wrkFile);
            }

            if (benchmarkMetadataMap.isEmpty()) {
                String message = "CRITICAL: No valid benchmark metadata found in any result files";
                LOGGER.error(message);
                throw new IllegalStateException(message);
            }

            LOGGER.info("Successfully parsed metadata for {} benchmarks", benchmarkMetadataMap.size());
        }
    }

    /**
     * Parse metadata from a single WRK result file.
     *
     * @param resultFile Path to the WRK result file
     */
    private void parseSingleBenchmarkMetadata(Path resultFile) {
        try {
            List<String> lines = Files.readAllLines(resultFile);
            String benchmarkName = null;
            Instant startTime = null;
            Instant endTime = null;
            boolean inMetadata = false;

            for (String line : lines) {
                if ("=== BENCHMARK METADATA ===".equals(line)) {
                    inMetadata = true;
                } else if ("=== WRK OUTPUT ===".equals(line)) {
                    inMetadata = false;
                } else if (inMetadata || line.startsWith("end_time:")) {
                    if (line.startsWith("benchmark_name: ")) {
                        benchmarkName = line.substring(16).trim();
                    } else if (line.startsWith("start_time: ")) {
                        long epochSeconds = Long.parseLong(line.substring(12).trim());
                        startTime = Instant.ofEpochSecond(epochSeconds);
                    } else if (line.startsWith("end_time: ")) {
                        long epochSeconds = Long.parseLong(line.substring(10).trim());
                        endTime = Instant.ofEpochSecond(epochSeconds);
                    }
                }
            }

            // Validate we have all required metadata
            if (benchmarkName == null || startTime == null || endTime == null) {
                String message = "WARNING: Incomplete metadata in file %s (name=%s, start=%s, end=%s)".formatted(
                        resultFile, benchmarkName, startTime, endTime
                );
                LOGGER.warn(message);
                return;
            }

            // Store the metadata
            BenchmarkMetadata metadata = new BenchmarkMetadata(benchmarkName, startTime, endTime);
            benchmarkMetadataMap.put(benchmarkName, metadata);

            LOGGER.info("Parsed metadata for benchmark '{}': start={}, end={}, duration={}s",
                    benchmarkName, startTime, endTime,
                    endTime.getEpochSecond() - startTime.getEpochSecond());

        } catch (IOException | NumberFormatException e) {
            LOGGER.warn("Failed to parse metadata from {}: {}", resultFile, e.getMessage());
        }
    }

    /**
     * Find metadata for a benchmark based on its result file name.
     *
     * @param benchmarkFileName The benchmark file name from BenchmarkData
     * @return The matching metadata or null if not found
     */
    private BenchmarkMetadata findMetadataForBenchmark(String benchmarkFileName) {
        // First try exact match
        if (benchmarkMetadataMap.containsKey(benchmarkFileName)) {
            return benchmarkMetadataMap.get(benchmarkFileName);
        }

        // Try to extract base name and match
        String baseName = benchmarkFileName;
        if (baseName.endsWith(".txt")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        if (baseName.endsWith("-results")) {
            baseName = baseName.substring(0, baseName.length() - 8);
        }

        // Try to find by matching base name
        for (Map.Entry<String, BenchmarkMetadata> entry : benchmarkMetadataMap.entrySet()) {
            if (entry.getKey().equals(baseName) ||
                    entry.getKey().contains(baseName) ||
                    baseName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // If still not found and we only have one benchmark, use it
        if (benchmarkMetadataMap.size() == 1) {
            LOGGER.info("Using single available benchmark metadata for: {}", benchmarkFileName);
            return benchmarkMetadataMap.values().iterator().next();
        }

        return null;
    }
}