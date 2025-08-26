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
package de.cuioss.jwt.quarkus.benchmark.metrics;

import com.google.gson.*;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Comprehensive metrics post-processor that generates both HTTP roundtrip metrics
 * and Quarkus resource usage metrics from benchmark results.
 * <p>
 * This class combines:
 * - HTTP metrics: HTTP roundtrip percentiles from JMH benchmark results (external view)
 * - Quarkus metrics: CPU and RAM usage from Prometheus format metrics files
 * <p>
 * Generates http-metrics.json and quarkus-metrics.json files for performance analysis.
 *
 * @since 1.0
 */
public class MetricsPostProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsPostProcessor.class);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
            .create();

    // Constants for magic numbers
    private static final double MICROSECONDS_PER_MILLISECOND = 1000.0;
    private static final double DECIMAL_THRESHOLD = 10.0;
    private static final double DECIMAL_PRECISION = 10.0;

    private final String benchmarkResultsFile;
    private final String outputDirectory;

    public MetricsPostProcessor(String benchmarkResultsFile, String outputDirectory) {
        this.benchmarkResultsFile = benchmarkResultsFile;
        this.outputDirectory = outputDirectory;
        File dir = new File(outputDirectory);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory: " + dir.getAbsolutePath());
        }
        LOGGER.debug("MetricsPostProcessor initialized with benchmark file: {} and output directory: {}",
                benchmarkResultsFile, dir.getAbsolutePath());
    }

    /**
     * Parse JMH benchmark results and generate http-metrics.json with roundtrip times
     * for JWT validation and health endpoints.
     *
     * @param timestamp Timestamp for the metrics output
     * @throws IOException if file operations fail
     */
    public void parseAndExportHttpMetrics(Instant timestamp) throws IOException {
        LOGGER.debug("Parsing JMH benchmark results from: {}", benchmarkResultsFile);

        File inputFile = new File(benchmarkResultsFile);
        if (!inputFile.exists()) {
            throw new IOException("Benchmark results file not found: " + benchmarkResultsFile);
        }

        Map<String, HttpEndpointMetrics> endpointMetrics;
        try (FileReader reader = new FileReader(inputFile)) {
            endpointMetrics = parseBenchmarkResults(reader);
        }

        // Generate output file
        generateHttpMetricsFile(endpointMetrics, timestamp);

        LOGGER.debug("Successfully exported HTTP metrics for {} endpoints", endpointMetrics.size());
    }

    /**
     * Parse benchmark results from a Reader - useful for testing with different input sources
     *
     * @param reader Reader containing JMH benchmark JSON data
     * @return Map of endpoint metrics
     */
    public Map<String, HttpEndpointMetrics> parseBenchmarkResults(FileReader reader) {
        return parseBenchmarkResultsInternal(GSON.fromJson(reader, JsonArray.class));
    }

    /**
     * Parse benchmark results from a JSON string - useful for testing
     *
     * @param jsonContent JSON content as string
     * @return Map of endpoint metrics
     */
    public Map<String, HttpEndpointMetrics> parseBenchmarkResults(String jsonContent) {
        return parseBenchmarkResultsInternal(GSON.fromJson(jsonContent, JsonArray.class));
    }

    private Map<String, HttpEndpointMetrics> parseBenchmarkResultsInternal(JsonArray benchmarks) {
        Map<String, HttpEndpointMetrics> endpointMetrics = new LinkedHashMap<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            processBenchmark(benchmark, endpointMetrics);
        }

        return endpointMetrics;
    }

    private void processBenchmark(JsonObject benchmark, Map<String, HttpEndpointMetrics> endpointMetrics) {
        String benchmarkName = benchmark.get("benchmark").getAsString();
        String mode = benchmark.get("mode").getAsString();

        String endpointType = determineEndpointType(benchmarkName);
        if (endpointType == null) {
            return;
        }

        HttpEndpointMetrics metrics = endpointMetrics.computeIfAbsent(endpointType,
                k -> new HttpEndpointMetrics(getEndpointDisplayName(k), benchmarkName));

        // Process throughput mode benchmarks
        if ("thrpt".equals(mode)) {
            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            if (primaryMetric != null) {
                double score = primaryMetric.get("score").getAsDouble();
                String scoreUnit = primaryMetric.get("scoreUnit").getAsString();

                // Convert to ops/s if necessary
                double throughputOpsPerSec = score;
                if ("ops/ms".equals(scoreUnit)) {
                    throughputOpsPerSec = score * 1000; // Convert ops/ms to ops/s
                } else if ("ops/us".equals(scoreUnit)) {
                    throughputOpsPerSec = score * 1000000; // Convert ops/us to ops/s
                }

                metrics.updateThroughput(throughputOpsPerSec);
                LOGGER.debug("Processed {} throughput: {} ops/s", endpointType, throughputOpsPerSec);
            }
        } else if ("sample".equals(mode)) {
            // Extract percentiles from primaryMetric scorePercentiles
            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            if (primaryMetric == null) {
                return;
            }

            JsonObject scorePercentiles = primaryMetric.getAsJsonObject("scorePercentiles");
            if (scorePercentiles == null) {
                return;
            }

            // Extract sample count from raw data histogram
            int sampleCount = extractSampleCount(primaryMetric);

            // Update metrics with percentile data (convert from ms to ms, keep as ms)
            double p50 = scorePercentiles.get("50.0").getAsDouble();
            double p95 = scorePercentiles.get("95.0").getAsDouble();
            double p99 = scorePercentiles.get("99.0").getAsDouble();

            metrics.updateMetrics(sampleCount, p50, p95, p99);

            LOGGER.debug("Processed {} - samples: {}, p50: {}ms, p95: {}ms, p99: {}ms",
                    endpointType, sampleCount, p50, p95, p99);
        }
    }

    private String determineEndpointType(String benchmarkName) {
        if (benchmarkName.contains("JwtValidationBenchmark")) {
            return "jwt_validation";
        } else if (benchmarkName.contains("JwtHealthBenchmark")) {
            return "health";
        }
        return null;
    }

    private String getEndpointDisplayName(String endpointType) {
        return switch (endpointType) {
            case "jwt_validation" -> "JWT Validation";
            case "health" -> "Health Check";
            default -> endpointType;
        };
    }

    private int extractSampleCount(JsonObject primaryMetric) {
        JsonArray rawDataHistogram = primaryMetric.getAsJsonArray("rawDataHistogram");
        if (rawDataHistogram == null || rawDataHistogram.isEmpty()) {
            return 0;
        }

        int totalCount = 0;
        for (JsonElement forkElement : rawDataHistogram) {
            totalCount += processFork(forkElement);
        }
        return totalCount;
    }

    private int processFork(JsonElement forkElement) {
        JsonArray fork = forkElement.getAsJsonArray();
        if (fork == null) {
            return 0;
        }

        int forkCount = 0;
        for (JsonElement iterationElement : fork) {
            forkCount += processIteration(iterationElement);
        }
        return forkCount;
    }

    private int processIteration(JsonElement iterationElement) {
        JsonArray iteration = iterationElement.getAsJsonArray();
        if (iteration == null) {
            return 0;
        }

        int iterationCount = 0;
        for (JsonElement measurement : iteration) {
            JsonArray pair = measurement.getAsJsonArray();
            if (pair != null && pair.size() >= 2) {
                iterationCount += pair.get(1).getAsInt();
            }
        }
        return iterationCount;
    }

    private void generateHttpMetricsFile(Map<String, HttpEndpointMetrics> endpointMetrics, Instant timestamp) throws IOException {
        Map<String, Object> output = new LinkedHashMap<>();

        for (Map.Entry<String, HttpEndpointMetrics> entry : endpointMetrics.entrySet()) {
            String endpointType = entry.getKey();
            HttpEndpointMetrics metrics = entry.getValue();

            Map<String, Object> endpointData = new LinkedHashMap<>();
            endpointData.put("name", metrics.displayName);
            endpointData.put("timestamp", timestamp.toString());
            endpointData.put("sample_count", metrics.sampleCount);

            // Add throughput if available
            if (metrics.throughput > 0) {
                endpointData.put("throughput_ops_per_sec", formatNumber(metrics.throughput));
            }

            Map<String, Object> percentiles = new LinkedHashMap<>();
            percentiles.put("p50_us", formatNumber(metrics.p50 * MICROSECONDS_PER_MILLISECOND));
            percentiles.put("p95_us", formatNumber(metrics.p95 * MICROSECONDS_PER_MILLISECOND));
            percentiles.put("p99_us", formatNumber(metrics.p99 * MICROSECONDS_PER_MILLISECOND));
            endpointData.put("percentiles", percentiles);

            endpointData.put("source", "JMH benchmark - " + metrics.sourceBenchmark + " sample mode");

            output.put(endpointType, endpointData);
        }

        String outputFilePath = outputDirectory + "/http-metrics.json";
        File outputFile = new File(outputFilePath);

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(output, writer);
            writer.flush();
            LOGGER.debug("Generated http-metrics.json at: {}", outputFile.getAbsolutePath());
        }
    }

    /**
     * Format number according to rules: 1 decimal for <10, no decimal for >=10
     */
    private Object formatNumber(double value) {
        if (value < DECIMAL_THRESHOLD) {
            // Round to 1 decimal place for values < 10
            return Math.round(value * DECIMAL_PRECISION) / DECIMAL_PRECISION;
        } else {
            // Return as integer for values >= 10
            return Math.round(value);
        }
    }

    /**
     * Class to track metrics for an HTTP endpoint - made public for testing
     */
    @Getter
    public static class HttpEndpointMetrics {
        final String displayName;
        final String sourceBenchmark;
        int sampleCount;
        double p50;
        double p95;
        double p99;
        double throughput; // ops/s

        HttpEndpointMetrics(String displayName, String sourceBenchmark) {
            this.displayName = displayName;
            this.sourceBenchmark = sourceBenchmark;
        }

        void updateMetrics(int samples, double p50, double p95, double p99) {
            // Accumulate sample counts and use latest percentiles
            // (could be enhanced to average multiple benchmark methods)
            this.sampleCount += samples;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
        }

        void updateThroughput(double throughput) {
            this.throughput = throughput;
        }

    }

    /**
     * Parse and export both HTTP metrics and Quarkus metrics
     *
     * @param timestamp Timestamp for the metrics output
     */
    public void parseAndExportAllMetrics(Instant timestamp) {
        LOGGER.debug("Processing comprehensive metrics - HTTP roundtrip and Quarkus resource usage");

        // Process HTTP roundtrip metrics
        try {
            parseAndExportHttpMetrics(timestamp);
            LOGGER.debug("Successfully processed HTTP roundtrip metrics");
        } catch (IOException e) {
            LOGGER.warn("Failed to process HTTP metrics: {}", e.getMessage());
        }

        // Process Quarkus resource usage metrics
        try {
            // Metrics are in target/metrics-download, not in outputDirectory/metrics-download
            File outputDir = new File(outputDirectory);
            File targetDir = outputDir.getParentFile(); // Go up to target directory
            String metricsDownloadDir = new File(targetDir, "metrics-download").getAbsolutePath();
            QuarkusMetricsPostProcessor quarkusProcessor = new QuarkusMetricsPostProcessor(metricsDownloadDir, outputDirectory);
            quarkusProcessor.parseAndExportQuarkusMetrics(timestamp);
            LOGGER.debug("Successfully processed Quarkus resource usage metrics");
        } catch (IOException e) {
            LOGGER.warn("Failed to process Quarkus metrics: {}", e.getMessage());
        }

        LOGGER.debug("Comprehensive metrics processing completed");
    }

    /**
     * Convenience method to parse and export using default file locations
     * Processes both HTTP roundtrip metrics and Quarkus resource metrics
     */
    public static void parseAndExport(String resultsDirectory) {
        // Handle case where we're called from target directory
        String benchmarkFile;
        String outputDir;

        if (".".equals(resultsDirectory)) {
            // Called from target directory - look for benchmark-results subdirectory
            benchmarkFile = "benchmark-results/integration-benchmark-result.json";
            outputDir = ".";
        } else {
            // Called with specific results directory
            benchmarkFile = resultsDirectory + "/integration-benchmark-result.json";
            outputDir = resultsDirectory;
        }

        try {
            MetricsPostProcessor parser = new MetricsPostProcessor(benchmarkFile, outputDir);
            parser.parseAndExportAllMetrics(Instant.now());
        } catch (Exception e) {
            LOGGER.error("Failed to parse and export metrics", e);
        }
    }

    /**
     * Method for HTTP-only metrics processing
     */
    public static void parseAndExportHttpOnly(String resultsDirectory) throws IOException {
        String benchmarkFile = resultsDirectory + "/integration-benchmark-result.json";
        MetricsPostProcessor parser = new MetricsPostProcessor(benchmarkFile, resultsDirectory);
        parser.parseAndExportHttpMetrics(Instant.now());
    }

}