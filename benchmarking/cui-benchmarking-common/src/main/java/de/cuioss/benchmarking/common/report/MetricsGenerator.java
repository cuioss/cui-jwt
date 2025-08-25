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
package de.cuioss.benchmarking.common.report;

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

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

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

    private static final CuiLogger LOGGER =
            new CuiLogger(MetricsGenerator.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Generates comprehensive metrics JSON from benchmark results.
     *
     * @param results the JMH benchmark results
     * @param outputDir the output directory for metrics files
     * @throws IOException if writing metrics files fails
     */
    public void generateMetricsJson(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info(INFO.GENERATING_METRICS_JSON.format(results.size()));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));
        metrics.put("benchmarks", processBenchmarkResults(results));
        metrics.put("summary", generateSummaryMetrics(results));

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);
        Path metricsFile = outputPath.resolve("metrics.json");
        Files.writeString(metricsFile, GSON.toJson(metrics));
        LOGGER.info(INFO.METRICS_FILE_GENERATED.format(metricsFile));

        // Also generate individual benchmark files for detailed analysis
        generateIndividualMetrics(results, outputDir);
    }

    private Map<String, Object> processBenchmarkResults(Collection<RunResult> results) {
        return results.stream()
                .collect(LinkedHashMap::new,
                        (map, result) -> {
                            String benchmarkName = extractBenchmarkName(result.getParams().getBenchmark());
                            map.put(benchmarkName, processSingleBenchmark(result));
                        },
                        LinkedHashMap::putAll);
    }

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

                stats.put("p50", statistics.getPercentile(50.0));
                stats.put("p95", statistics.getPercentile(95.0));
                stats.put("p99", statistics.getPercentile(99.0));

                metrics.put("statistics", stats);
            }

            metrics.put("normalized", normalizeMetrics(primaryResult));
        }

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

    private Map<String, Object> normalizeMetrics(Result<?> primaryResult) {
        record MetricConversion(double throughputOpsPerSec, double latencyMsPerOp) {
        }

        double score = primaryResult.getScore();
        String unit = primaryResult.getScoreUnit();

        MetricConversion conversion = switch (unit) {
            case String u when u.contains("ops/s") || u.contains("ops/sec") ->
                new MetricConversion(score, 1000.0 / score);
            case String u when u.contains("s/op") ->
                new MetricConversion(1.0 / score, score * 1000.0);
            case String u when u.contains("ms/op") ->
                new MetricConversion(1000.0 / score, score);
            case String u when u.contains("us/op") || u.contains("µs/op") ->
                new MetricConversion(1_000_000.0 / score, score / 1000.0);
            case String u when u.contains("ns/op") ->
                new MetricConversion(1_000_000_000.0 / score, score / 1_000_000.0);
            default -> new MetricConversion(0.0, 0.0);
        };

        Map<String, Object> normalized = new LinkedHashMap<>();
        if (conversion.throughputOpsPerSec > 0) {
            normalized.put("throughput_ops_per_sec", conversion.throughputOpsPerSec);
            normalized.put("latency_ms_per_op", conversion.latencyMsPerOp);
        }
        return normalized;
    }

    private Map<String, Object> generateSummaryMetrics(Collection<RunResult> results) {
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("total_benchmarks", results.size());
        summary.put("total_score", calculateTotalScore(results));
        summary.put("average_throughput", calculateAverageThroughput(results));
        summary.put("performance_grade", calculatePerformanceGrade(results));

        return summary;
    }

    private void generateIndividualMetrics(Collection<RunResult> results, String outputDir) throws IOException {
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        for (RunResult result : results) {
            String benchmarkName = extractBenchmarkName(result.getParams().getBenchmark());
            Map<String, Object> benchmarkMetrics = processSingleBenchmark(result);

            Path individualFile = outputPath.resolve(benchmarkName + "-metrics.json");
            Files.writeString(individualFile, GSON.toJson(benchmarkMetrics));
        }

        LOGGER.info(INFO.INDIVIDUAL_METRICS_GENERATED.format(results.size()));
    }

    private String extractBenchmarkName(String fullBenchmarkName) {
        if (fullBenchmarkName == null) {
            return "unknown";
        }

        String[] parts = fullBenchmarkName.split("\\.");
        if (parts.length >= 2) {
            String className = parts[parts.length - 2];
            String methodName = parts[parts.length - 1];

            if (className.endsWith("Benchmark")) {
                className = className.substring(0, className.length() - 9);
            }

            return className + "_" + methodName;
        }

        return fullBenchmarkName.replace(".", "_");
    }

    private double calculateTotalScore(Collection<RunResult> results) {
        return results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .mapToDouble(r -> r.getPrimaryResult().getScore())
                .sum();
    }

    private double calculateAverageThroughput(Collection<RunResult> results) {
        return results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .filter(r -> r.getPrimaryResult().getScoreUnit().contains("ops"))
                .mapToDouble(r -> r.getPrimaryResult().getScore())
                .average()
                .orElse(0.0);
    }

    private String calculatePerformanceGrade(Collection<RunResult> results) {
        double avgThroughput = calculateAverageThroughput(results);
        return switch ((int) Math.log10(Math.max(1, avgThroughput))) {
            case 6, 7, 8, 9 -> "A+";
            case 5 -> "A";
            case 4 -> "B";
            case 3 -> "C";
            default -> "D";
        };
    }
}