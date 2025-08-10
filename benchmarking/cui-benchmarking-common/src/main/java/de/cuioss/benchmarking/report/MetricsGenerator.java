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
package de.cuioss.benchmarking.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates performance metrics in JSON format for consumption by reports and visualizations.
 * <p>
 * This generator extracts key performance indicators from JMH results and formats them
 * in a standardized JSON structure for use in dashboards and trend analysis.
 *
 * @since 1.0.0
 */
public class MetricsGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generates a comprehensive metrics JSON file from benchmark results.
     *
     * @param results the JMH benchmark results
     * @param outputDir the output directory for metrics files
     * @throws IOException if file generation fails
     */
    public void generateMetricsJson(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating performance metrics JSON for %d results", results.size());

        // Create output directory
        Path metricsDir = Paths.get(outputDir);
        Files.createDirectories(metricsDir);

        // Build metrics structure
        Map<String, Object> metrics = buildMetricsStructure(results);

        // Write metrics file
        Path metricsFile = metricsDir.resolve("metrics.json");
        String json = GSON.toJson(metrics);
        Files.write(metricsFile, json.getBytes());

        LOGGER.info("Generated metrics file: %s", metricsFile);
    }

    /**
     * Builds the comprehensive metrics data structure.
     *
     * @param results the benchmark results
     * @return the metrics data structure
     */
    private Map<String, Object> buildMetricsStructure(Collection<RunResult> results) {
        Map<String, Object> metrics = new HashMap<>();
        
        // Add metadata
        metrics.put("timestamp", Instant.now().toString());
        metrics.put("benchmarkCount", results.size());
        
        // Add summary statistics
        Map<String, Object> summary = buildSummaryMetrics(results);
        metrics.put("summary", summary);
        
        // Add detailed benchmark results
        Map<String, Object> benchmarks = buildDetailedMetrics(results);
        metrics.put("benchmarks", benchmarks);
        
        return metrics;
    }

    /**
     * Builds summary metrics across all benchmarks.
     *
     * @param results the benchmark results
     * @return summary metrics map
     */
    private Map<String, Object> buildSummaryMetrics(Collection<RunResult> results) {
        Map<String, Object> summary = new HashMap<>();
        
        double totalThroughput = 0;
        double totalLatency = 0;
        int throughputCount = 0;
        int latencyCount = 0;
        double minThroughput = Double.MAX_VALUE;
        double maxThroughput = Double.MIN_VALUE;
        double minLatency = Double.MAX_VALUE;
        double maxLatency = Double.MIN_VALUE;

        for (RunResult result : results) {
            if (result.getPrimaryResult() != null && result.getPrimaryResult().getStatistics() != null) {
                double score = result.getPrimaryResult().getScore();
                String unit = result.getPrimaryResult().getScoreUnit();

                if (isLatencyMetric(unit)) {
                    double latencyMs = convertToMilliseconds(score, unit);
                    totalLatency += latencyMs;
                    latencyCount++;
                    minLatency = Math.min(minLatency, latencyMs);
                    maxLatency = Math.max(maxLatency, latencyMs);
                } else if (isThroughputMetric(unit)) {
                    double throughputOps = convertToOpsPerSecond(score, unit);
                    totalThroughput += throughputOps;
                    throughputCount++;
                    minThroughput = Math.min(minThroughput, throughputOps);
                    maxThroughput = Math.max(maxThroughput, throughputOps);
                }
            }
        }

        // Add throughput summary
        if (throughputCount > 0) {
            Map<String, Object> throughputSummary = new HashMap<>();
            throughputSummary.put("average", totalThroughput / throughputCount);
            throughputSummary.put("min", minThroughput);
            throughputSummary.put("max", maxThroughput);
            throughputSummary.put("unit", "ops/s");
            summary.put("throughput", throughputSummary);
        }

        // Add latency summary
        if (latencyCount > 0) {
            Map<String, Object> latencySummary = new HashMap<>();
            latencySummary.put("average", totalLatency / latencyCount);
            latencySummary.put("min", minLatency);
            latencySummary.put("max", maxLatency);
            latencySummary.put("unit", "ms");
            summary.put("latency", latencySummary);
        }

        return summary;
    }

    /**
     * Builds detailed metrics for each individual benchmark.
     *
     * @param results the benchmark results
     * @return detailed metrics map
     */
    private Map<String, Object> buildDetailedMetrics(Collection<RunResult> results) {
        Map<String, Object> benchmarks = new HashMap<>();
        
        for (RunResult result : results) {
            String benchmarkName = extractBenchmarkName(result.getParams().getBenchmark());
            Map<String, Object> benchmarkData = buildBenchmarkData(result);
            benchmarks.put(benchmarkName, benchmarkData);
        }
        
        return benchmarks;
    }

    /**
     * Builds detailed data for a single benchmark.
     *
     * @param result the benchmark result
     * @return benchmark data map
     */
    private Map<String, Object> buildBenchmarkData(RunResult result) {
        Map<String, Object> data = new HashMap<>();
        
        // Add basic information
        data.put("fullName", result.getParams().getBenchmark());
        data.put("mode", result.getParams().getMode().shortLabel());
        
        if (result.getPrimaryResult() != null) {
            // Add primary result
            Map<String, Object> primaryResult = new HashMap<>();
            primaryResult.put("score", result.getPrimaryResult().getScore());
            primaryResult.put("unit", result.getPrimaryResult().getScoreUnit());
            
            if (result.getPrimaryResult().getStatistics() != null) {
                primaryResult.put("samples", result.getPrimaryResult().getStatistics().getN());
                primaryResult.put("mean", result.getPrimaryResult().getStatistics().getMean());
                primaryResult.put("stddev", result.getPrimaryResult().getStatistics().getStandardDeviation());
                primaryResult.put("min", result.getPrimaryResult().getStatistics().getMin());
                primaryResult.put("max", result.getPrimaryResult().getStatistics().getMax());
            }
            
            data.put("primaryResult", primaryResult);
            
            // Add normalized metrics for easier comparison
            Map<String, Object> normalized = new HashMap<>();
            String unit = result.getPrimaryResult().getScoreUnit();
            double score = result.getPrimaryResult().getScore();
            
            if (isLatencyMetric(unit)) {
                normalized.put("latencyMs", convertToMilliseconds(score, unit));
            } else if (isThroughputMetric(unit)) {
                normalized.put("throughputOpsPerSec", convertToOpsPerSecond(score, unit));
            }
            
            data.put("normalized", normalized);
        }
        
        return data;
    }

    /**
     * Extracts a simple benchmark name from the full class path.
     *
     * @param fullName the full benchmark class and method name
     * @return the simplified name
     */
    private String extractBenchmarkName(String fullName) {
        if (fullName == null) {
            return "unknown";
        }
        
        // Extract just the class and method name
        String[] parts = fullName.split("\\.");
        if (parts.length >= 2) {
            String className = parts[parts.length - 2];
            String methodName = parts[parts.length - 1];
            return className + "." + methodName;
        }
        
        return fullName;
    }

    /**
     * Converts various time units to milliseconds.
     */
    private double convertToMilliseconds(double value, String unit) {
        switch (unit.toLowerCase()) {
            case "ns/op":
            case "nanoseconds":
                return value / 1_000_000.0;
            case "us/op":
            case "microseconds":
                return value / 1_000.0;
            case "ms/op":
            case "milliseconds":
                return value;
            case "s/op":
            case "seconds":
                return value * 1_000.0;
            default:
                return value; // Assume milliseconds if unknown
        }
    }

    /**
     * Converts various throughput units to operations per second.
     */
    private double convertToOpsPerSecond(double value, String unit) {
        switch (unit.toLowerCase()) {
            case "ops/ns":
                return value * 1_000_000_000.0;
            case "ops/us":
                return value * 1_000_000.0;
            case "ops/ms":
                return value * 1_000.0;
            case "ops/s":
                return value;
            default:
                return value; // Assume ops/s if unknown
        }
    }

    /**
     * Checks if a unit represents a latency metric.
     */
    private boolean isLatencyMetric(String unit) {
        return unit.contains("/op") || unit.contains("seconds") || unit.contains("milliseconds") || 
               unit.contains("microseconds") || unit.contains("nanoseconds");
    }

    /**
     * Checks if a unit represents a throughput metric.
     */
    private boolean isThroughputMetric(String unit) {
        return unit.startsWith("ops/");
    }
}