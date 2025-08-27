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

import com.google.gson.*;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static de.cuioss.benchmarking.common.report.ReportConstants.FIELD_BENCHMARK;
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
     * @param jsonFile the path to the benchmark JSON results file
     * @param outputDir the output directory for metrics files
     * @throws IOException if reading JSON or writing metrics files fails
     */
    public void generateMetricsJson(Path jsonFile, String outputDir) throws IOException {
        // Parse JSON file
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);

        if (benchmarks == null || benchmarks.isEmpty()) {
            throw new IllegalArgumentException("No benchmark data found in JSON file: " + jsonFile);
        }

        LOGGER.info(INFO.GENERATING_METRICS_JSON.format(benchmarks.size()));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));
        metrics.put("benchmarks", processBenchmarkResults(benchmarks));
        metrics.put("summary", generateSummaryMetrics(benchmarks));

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);
        Path metricsFile = outputPath.resolve("metrics.json");
        Files.writeString(metricsFile, GSON.toJson(metrics));
        LOGGER.info(INFO.METRICS_FILE_GENERATED.format(metricsFile));

        // Also generate individual benchmark files for detailed analysis
        generateIndividualMetrics(benchmarks, outputDir);
    }

    private Map<String, Object> processBenchmarkResults(JsonArray results) {
        Map<String, Object> benchmarks = new LinkedHashMap<>();
        for (JsonElement element : results) {
            JsonObject benchmark = element.getAsJsonObject();
            String fullName = benchmark.get(FIELD_BENCHMARK).getAsString();
            String benchmarkName = extractBenchmarkName(fullName);
            benchmarks.put(benchmarkName, processSingleBenchmark(benchmark));
        }
        return benchmarks;
    }

    private Map<String, Object> processSingleBenchmark(JsonObject benchmark) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // Extract primary metric
        if (benchmark.has("primaryMetric")) {
            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            double score = primaryMetric.get("score").getAsDouble();
            String unit = primaryMetric.get("scoreUnit").getAsString();
            String mode = benchmark.get("mode").getAsString();

            metrics.put("score", score);
            metrics.put("unit", unit);
            metrics.put("mode", mode);

            // Extract raw data statistics if available
            if (primaryMetric.has("rawData")) {
                JsonArray rawData = primaryMetric.getAsJsonArray("rawData");
                if (rawData.size() > 0 && rawData.get(0).isJsonArray()) {
                    JsonArray dataPoints = rawData.get(0).getAsJsonArray();
                    Map<String, Object> stats = calculateStatistics(dataPoints);
                    metrics.put("statistics", stats);
                }
            }

            // Extract percentiles if available
            if (primaryMetric.has("scorePercentiles")) {
                JsonObject percentiles = primaryMetric.getAsJsonObject("scorePercentiles");
                Map<String, Object> stats = new LinkedHashMap<>();
                if (percentiles.has("50.0")) stats.put("p50", percentiles.get("50.0").getAsDouble());
                if (percentiles.has("95.0")) stats.put("p95", percentiles.get("95.0").getAsDouble());
                if (percentiles.has("99.0")) stats.put("p99", percentiles.get("99.0").getAsDouble());
                if (!stats.isEmpty()) {
                    metrics.put("percentiles", stats);
                }
            }

            metrics.put("normalized", normalizeMetrics(score, unit));
        }

        // Extract secondary metrics
        if (benchmark.has("secondaryMetrics")) {
            JsonObject secondaryMetrics = benchmark.getAsJsonObject("secondaryMetrics");
            Map<String, Object> secondary = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : secondaryMetrics.entrySet()) {
                JsonObject metric = entry.getValue().getAsJsonObject();
                Map<String, Object> secondaryMetric = new LinkedHashMap<>();
                secondaryMetric.put("score", metric.get("score").getAsDouble());
                secondaryMetric.put("unit", metric.get("scoreUnit").getAsString());
                secondary.put(entry.getKey(), secondaryMetric);
            }
            if (!secondary.isEmpty()) {
                metrics.put("secondary_results", secondary);
            }
        }

        return metrics;
    }

    private Map<String, Object> calculateStatistics(JsonArray dataPoints) {
        Map<String, Object> stats = new LinkedHashMap<>();

        if (dataPoints.size() == 0) {
            return stats;
        }

        double[] values = new double[dataPoints.size()];
        for (int i = 0; i < dataPoints.size(); i++) {
            values[i] = dataPoints.get(i).getAsDouble();
        }

        // Calculate basic statistics
        double sum = 0;
        double min = values[0];
        double max = values[0];

        for (double v : values) {
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }

        double mean = sum / values.length;

        // Calculate standard deviation
        double sumSquaredDiff = 0;
        for (double v : values) {
            double diff = v - mean;
            sumSquaredDiff += diff * diff;
        }
        double stddev = Math.sqrt(sumSquaredDiff / values.length);

        stats.put("mean", mean);
        stats.put("stddev", stddev);
        stats.put("min", min);
        stats.put("max", max);
        stats.put("n", values.length);

        return stats;
    }

    private Map<String, Object> normalizeMetrics(double score, String unit) {
        record MetricConversion(double throughputOpsPerSec, double latencyMsPerOp) {
        }

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

    private Map<String, Object> generateSummaryMetrics(JsonArray benchmarks) {
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("total_benchmarks", benchmarks.size());
        summary.put("total_score", calculateTotalScore(benchmarks));
        summary.put("average_throughput", calculateAverageThroughput(benchmarks));
        summary.put("performance_grade", calculatePerformanceGrade(benchmarks));

        return summary;
    }

    private void generateIndividualMetrics(JsonArray benchmarks, String outputDir) throws IOException {
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String fullName = benchmark.get(FIELD_BENCHMARK).getAsString();
            String benchmarkName = extractBenchmarkName(fullName);
            Map<String, Object> benchmarkMetrics = processSingleBenchmark(benchmark);

            Path individualFile = outputPath.resolve(benchmarkName + "-metrics.json");
            Files.writeString(individualFile, GSON.toJson(benchmarkMetrics));
        }

        LOGGER.info(INFO.INDIVIDUAL_METRICS_GENERATED.format(benchmarks.size()));
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

    private double calculateTotalScore(JsonArray benchmarks) {
        double total = 0;
        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            if (benchmark.has("primaryMetric")) {
                JsonObject metric = benchmark.getAsJsonObject("primaryMetric");
                total += metric.get("score").getAsDouble();
            }
        }
        return total;
    }

    private double calculateAverageThroughput(JsonArray benchmarks) {
        double sum = 0;
        int count = 0;

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            if (benchmark.has("primaryMetric")) {
                JsonObject metric = benchmark.getAsJsonObject("primaryMetric");
                String unit = metric.get("scoreUnit").getAsString();
                if (unit.contains("ops")) {
                    double score = metric.get("score").getAsDouble();
                    // Convert to ops/s if needed
                    if (unit.contains("ops/ms")) {
                        score *= 1000;
                    } else if (unit.contains("ops/us")) {
                        score *= 1_000_000;
                    } else if (unit.contains("ops/ns")) {
                        score *= 1_000_000_000;
                    }
                    sum += score;
                    count++;
                }
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    private String calculatePerformanceGrade(JsonArray benchmarks) {
        double avgThroughput = calculateAverageThroughput(benchmarks);
        return switch ((int) Math.log10(Math.max(1, avgThroughput))) {
            case 6, 7, 8, 9 -> "A+";
            case 5 -> "A";
            case 4 -> "B";
            case 3 -> "C";
            default -> "D";
        };
    }
}