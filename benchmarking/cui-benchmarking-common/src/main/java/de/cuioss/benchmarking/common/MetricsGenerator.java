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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates structured performance metrics in JSON format from JMH benchmark results.
 * <p>
 * This generator creates machine-readable metrics that can be consumed by CI/CD pipelines,
 * monitoring systems, and performance analysis tools.
 * <p>
 * Generated metrics include:
 * <ul>
 *   <li>Throughput measurements (operations per second)</li>
 *   <li>Latency measurements (percentiles)</li>
 *   <li>Statistical summaries (mean, std dev, min, max)</li>
 *   <li>Historical tracking data</li>
 * </ul>
 */
public class MetricsGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Generates comprehensive metrics JSON from benchmark results.
     *
     * @param results the JMH benchmark results
     * @param outputDir the output directory for metrics files
     * @throws IOException if writing metrics files fails
     */
    public void generateMetricsJson(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating metrics JSON for {} benchmark results", results.size());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));
        metrics.put("benchmarks", processBenchmarkResults(results));
        metrics.put("summary", generateSummaryMetrics(results));

        Path metricsFile = Path.of(outputDir, "metrics.json");
        Files.writeString(metricsFile, GSON.toJson(metrics));
        LOGGER.info("Generated metrics file: {}", metricsFile);

        // Also generate individual benchmark files for detailed analysis
        generateIndividualMetrics(results, outputDir);
    }

    /**
     * Processes individual benchmark results into structured metrics.
     */
    private Map<String, Object> processBenchmarkResults(Collection<RunResult> results) {
        Map<String, Object> benchmarks = new LinkedHashMap<>();

        for (RunResult result : results) {
            String benchmarkName = extractBenchmarkName(result.getParams().getBenchmark());
            Map<String, Object> benchmarkMetrics = processSingleBenchmark(result);
            benchmarks.put(benchmarkName, benchmarkMetrics);
        }

        return benchmarks;
    }

    /**
     * Processes a single benchmark result into metrics.
     */
    private Map<String, Object> processSingleBenchmark(RunResult result) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        if (result.getPrimaryResult() != null) {
            var primaryResult = result.getPrimaryResult();
            var statistics = primaryResult.getStatistics();

            metrics.put("score", primaryResult.getScore());
            metrics.put("unit", primaryResult.getScoreUnit());
            metrics.put("mode", result.getParams().getMode().toString());

            if (statistics != null) {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("mean", statistics.getMean());
                stats.put("stddev", statistics.getStandardDeviation());
                stats.put("min", statistics.getMin());
                stats.put("max", statistics.getMax());
                stats.put("n", statistics.getN());

                // Add percentiles if available
                stats.put("p50", statistics.getPercentile(50.0));
                stats.put("p95", statistics.getPercentile(95.0));
                stats.put("p99", statistics.getPercentile(99.0));

                metrics.put("statistics", stats);
            }

            // Add normalized metrics for comparison
            metrics.put("normalized", normalizeMetrics(primaryResult));
        }

        // Add secondary results if available
        if (!result.getSecondaryResults().isEmpty()) {
            Map<String, Object> secondary = new LinkedHashMap<>();
            result.getSecondaryResults().forEach((key, value) -> {
                Map<String, Object> secondaryMetric = new LinkedHashMap<>();
                secondaryMetric.put("score", value.getScore());
                secondaryMetric.put("unit", value.getScoreUnit());
                secondary.put(key, secondaryMetric);
            });
            metrics.put("secondary_results", secondary);
        }

        return metrics;
    }

    /**
     * Normalizes metrics for cross-benchmark comparison.
     */
    private Map<String, Object> normalizeMetrics(Result primaryResult) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        double score = primaryResult.getScore();
        String unit = primaryResult.getScoreUnit();

        // Convert to standard units
        if (unit.contains("ops/s") || unit.contains("ops/sec")) {
            normalized.put("throughput_ops_per_sec", score);
            normalized.put("latency_ms_per_op", 1000.0 / score);
        } else if (unit.contains("s/op")) {
            normalized.put("throughput_ops_per_sec", 1.0 / score);
            normalized.put("latency_ms_per_op", score * 1000.0);
        } else if (unit.contains("ms/op")) {
            normalized.put("throughput_ops_per_sec", 1000.0 / score);
            normalized.put("latency_ms_per_op", score);
        } else if (unit.contains("us/op")) {
            normalized.put("throughput_ops_per_sec", 1_000_000.0 / score);
            normalized.put("latency_ms_per_op", score / 1000.0);
        }

        return normalized;
    }

    /**
     * Generates summary metrics across all benchmarks.
     */
    private Map<String, Object> generateSummaryMetrics(Collection<RunResult> results) {
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("total_benchmarks", results.size());
        summary.put("total_score", calculateTotalScore(results));
        summary.put("average_throughput", calculateAverageThroughput(results));
        summary.put("performance_grade", calculatePerformanceGrade(results));

        return summary;
    }

    /**
     * Generates individual metric files for each benchmark.
     */
    private void generateIndividualMetrics(Collection<RunResult> results, String outputDir) throws IOException {
        for (RunResult result : results) {
            String benchmarkName = extractBenchmarkName(result.getParams().getBenchmark());
            Map<String, Object> benchmarkMetrics = processSingleBenchmark(result);

            Path individualFile = Path.of(outputDir, benchmarkName + "-metrics.json");
            Files.writeString(individualFile, GSON.toJson(benchmarkMetrics));
        }

        LOGGER.info("Generated {} individual metric files", results.size());
    }

    /**
     * Extracts a clean benchmark name from the full benchmark class path.
     */
    private String extractBenchmarkName(String fullBenchmarkName) {
        if (fullBenchmarkName == null) {
            return "unknown";
        }

        // Extract class name and method
        String[] parts = fullBenchmarkName.split("\\.");
        if (parts.length >= 2) {
            String className = parts[parts.length - 2];
            String methodName = parts[parts.length - 1];

            // Remove "Benchmark" suffix from class name if present
            if (className.endsWith("Benchmark")) {
                className = className.substring(0, className.length() - 9);
            }

            return className + "_" + methodName;
        }

        return fullBenchmarkName.replaceAll("\\.", "_");
    }

    /**
     * Calculates total score across all benchmarks.
     */
    private double calculateTotalScore(Collection<RunResult> results) {
        return results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .mapToDouble(r -> r.getPrimaryResult().getScore())
                .sum();
    }

    /**
     * Calculates average throughput across benchmarks.
     */
    private double calculateAverageThroughput(Collection<RunResult> results) {
        return results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .filter(r -> r.getPrimaryResult().getScoreUnit().contains("ops"))
                .mapToDouble(r -> r.getPrimaryResult().getScore())
                .average()
                .orElse(0.0);
    }

    /**
     * Calculates a performance grade based on results.
     */
    private String calculatePerformanceGrade(Collection<RunResult> results) {
        double avgThroughput = calculateAverageThroughput(results);

        if (avgThroughput >= 1_000_000) {
            return "A+";
        } else if (avgThroughput >= 100_000) {
            return "A";
        } else if (avgThroughput >= 10_000) {
            return "B";
        } else if (avgThroughput >= 1_000) {
            return "C";
        } else {
            return "D";
        }
    }
}