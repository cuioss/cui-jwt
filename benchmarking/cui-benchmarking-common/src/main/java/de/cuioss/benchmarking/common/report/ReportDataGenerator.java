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
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates a standardized JSON data file containing all report data
 * that can be consumed by HTML templates via JavaScript.
 * <p>
 * This generator consolidates all benchmark data and metrics into a single
 * JSON file named "benchmark-data.json" that templates load and process.
 * <p>
 * The generated JSON structure includes:
 * <ul>
 *   <li>Metadata (timestamp, benchmark type, etc.)</li>
 *   <li>Overview metrics (totals, averages, grades)</li>
 *   <li>Detailed benchmark results</li>
 *   <li>Chart data for visualizations</li>
 *   <li>Trend data for historical analysis</li>
 * </ul>
 */
public class ReportDataGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(ReportDataGenerator.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String DATA_FILE_NAME = "benchmark-data.json";

    /**
     * Generates a comprehensive data file for report templates using pre-computed metrics.
     *
     * @param jsonFile the path to the benchmark JSON results
     * @param metrics the pre-computed benchmark metrics
     * @param type the benchmark type
     * @param outputDir the output directory
     * @throws IOException if writing fails
     */
    public void generateDataFile(Path jsonFile, BenchmarkMetrics metrics, BenchmarkType type, String outputDir)
            throws IOException {

        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);

        Map<String, Object> data = new LinkedHashMap<>();

        // Add metadata
        data.put("metadata", createMetadata(type));

        // Add overview using pre-computed metrics
        data.put("overview", createOverview(metrics, benchmarks.size()));

        // Add detailed results
        data.put("benchmarks", createBenchmarkResults(benchmarks));

        // Add chart data (JavaScript expects "chartData" not "charts")
        data.put("chartData", createChartData(benchmarks));

        // Add trend data (placeholder for now)
        data.put("trends", createTrendData());

        // Write data file to data subdirectory
        Path dataDir = Path.of(outputDir, "data");
        Files.createDirectories(dataDir);
        Path dataFile = dataDir.resolve(DATA_FILE_NAME);
        Files.writeString(dataFile, GSON.toJson(data));

        LOGGER.info(INFO.METRICS_FILE_GENERATED.format(dataFile));
    }

    private Map<String, Object> createMetadata(BenchmarkType type) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Instant now = Instant.now();
        metadata.put("timestamp", ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)));
        metadata.put("displayTimestamp", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                .format(now.atOffset(ZoneOffset.UTC)));
        metadata.put("benchmarkType", type.getDisplayName());
        metadata.put("reportVersion", "2.0.0");
        return metadata;
    }

    private Map<String, Object> createOverview(BenchmarkMetrics metrics, int totalBenchmarks) {
        Map<String, Object> overview = new LinkedHashMap<>();

        // Using pre-computed metrics directly
        overview.put("throughput", MetricConversionUtil.formatThroughput(metrics.throughput()));
        overview.put("latency", MetricConversionUtil.formatLatency(metrics.latency()));
        overview.put("throughputBenchmarkName", metrics.throughputBenchmarkName());
        overview.put("latencyBenchmarkName", metrics.latencyBenchmarkName());

        // Performance metrics (performanceScore is already rounded)
        overview.put("performanceScore", metrics.performanceScore());
        overview.put("performanceGrade", metrics.performanceGrade());
        overview.put("performanceGradeClass", getGradeClass(metrics.performanceGrade()));

        return overview;
    }

    private String getGradeClass(String grade) {
        return switch (grade) {
            case "A+" -> "grade-a-plus";
            case "A" -> "grade-a";
            case "B" -> "grade-b";
            case "C" -> "grade-c";
            case "D" -> "grade-d";
            case "F" -> "grade-f";
            default -> "grade-unknown";
        };
    }

    private List<Map<String, Object>> createBenchmarkResults(JsonArray benchmarks) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            Map<String, Object> result = new LinkedHashMap<>();

            // Basic info
            String fullName = benchmark.get("benchmark").getAsString();
            String simpleName = extractSimpleName(fullName);
            result.put("name", simpleName);
            result.put("fullName", fullName);

            // Mode and metrics
            String mode = benchmark.get("mode").getAsString();
            result.put("mode", mode);

            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            double score = primaryMetric.get("score").getAsDouble();
            String unit = primaryMetric.get("scoreUnit").getAsString();

            result.put("score", formatScore(score, unit));
            result.put("unit", unit);
            
            // Add throughput or latency fields based on mode
            if ("thrpt".equals(mode) || unit.contains("ops")) {
                double throughputOps = MetricConversionUtil.convertToOpsPerSecond(score, unit);
                result.put("throughput", MetricConversionUtil.formatThroughput(throughputOps));
            } else if ("avgt".equals(mode) || "sample".equals(mode) || unit.contains("/op")) {
                double latencyMs = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                result.put("latency", MetricConversionUtil.formatLatency(latencyMs));
            }

            // Error margin
            double error = primaryMetric.get("scoreError").getAsDouble();
            result.put("error", error);
            result.put("errorPercentage", calculateErrorPercentage(score, error));

            // Confidence intervals
            JsonArray confidence = primaryMetric.getAsJsonArray("scoreConfidence");
            if (confidence != null && confidence.size() == 2) {
                result.put("confidenceLow", confidence.get(0).getAsDouble());
                result.put("confidenceHigh", confidence.get(1).getAsDouble());
            }

            // Percentiles (if available)
            JsonObject percentiles = primaryMetric.getAsJsonObject("scorePercentiles");
            if (percentiles != null) {
                Map<String, Double> percentileMap = new LinkedHashMap<>();
                percentiles.entrySet().forEach(entry ->
                        percentileMap.put(entry.getKey(), entry.getValue().getAsDouble()));
                result.put("percentiles", percentileMap);
            }

            results.add(result);
        }

        return results;
    }

    private String extractSimpleName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    private String formatScore(double score, String unit) {
        if (unit.contains("ops")) {
            double opsPerSec = MetricConversionUtil.convertToOpsPerSecond(score, unit);
            if (opsPerSec >= 1_000_000) {
                return MetricConversionUtil.formatForDisplay(opsPerSec / 1_000_000) + "M ops/s";
            } else if (opsPerSec >= 1000) {
                return MetricConversionUtil.formatForDisplay(opsPerSec / 1000) + "K ops/s";
            } else {
                return MetricConversionUtil.formatForDisplay(opsPerSec) + " ops/s";
            }
        } else if (unit.contains("/op")) {
            double msPerOp = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
            if (msPerOp >= 1000) {
                return MetricConversionUtil.formatForDisplay(msPerOp / 1000) + "s";
            } else {
                return MetricConversionUtil.formatForDisplay(msPerOp) + "ms";
            }
        }
        return MetricConversionUtil.formatForDisplay(score) + " " + unit;
    }

    private double calculateErrorPercentage(double score, double error) {
        if (score == 0) return 0;
        return (error / score) * 100;
    }

    private Map<String, Object> createChartData(JsonArray benchmarks) {
        Map<String, Object> chartData = new LinkedHashMap<>();

        // Create data for overview chart - matching JavaScript expectations
        List<String> labels = new ArrayList<>();
        List<Double> throughput = new ArrayList<>();
        List<Double> latency = new ArrayList<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String name = extractSimpleName(benchmark.get("benchmark").getAsString());
            String mode = benchmark.get("mode").getAsString();

            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            double score = primaryMetric.get("score").getAsDouble();
            String unit = primaryMetric.get("scoreUnit").getAsString();

            labels.add(name);
            
            if ("thrpt".equals(mode) || unit.contains("ops")) {
                throughput.add(MetricConversionUtil.convertToOpsPerSecond(score, unit));
                latency.add(null);  // No latency for throughput benchmark
            } else if ("avgt".equals(mode) || "sample".equals(mode) || unit.contains("/op")) {
                throughput.add(null);  // No throughput for latency benchmark
                latency.add(MetricConversionUtil.convertToMillisecondsPerOp(score, unit));
            } else {
                // Unknown benchmark type, add nulls
                throughput.add(null);
                latency.add(null);
            }
        }

        // Direct structure matching JavaScript expectations
        chartData.put("labels", labels);
        chartData.put("throughput", throughput);
        chartData.put("latency", latency);
        
        // Add percentiles data separately
        chartData.put("percentilesData", createPercentilesChartData(benchmarks));

        return chartData;
    }

    private Map<String, Object> createPercentilesChartData(JsonArray benchmarks) {
        Map<String, Object> percentilesChart = new LinkedHashMap<>();
        List<String> percentileLabels = Arrays.asList("P0", "P50", "P90", "P95", "P99", "P99.9", "P99.99", "P100");
        List<String> benchmarkNames = new ArrayList<>();
        Map<String, List<Double>> dataByBenchmark = new LinkedHashMap<>();

        // Standard percentile keys mapping to labels
        String[] percentileKeys = {"0.0", "50.0", "90.0", "95.0", "99.0", "99.9", "99.99", "100.0"};

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String mode = benchmark.get("mode").getAsString();

            // Only process latency benchmarks for percentiles
            if ("avgt".equals(mode) || "sample".equals(mode)) {
                String name = extractSimpleName(benchmark.get("benchmark").getAsString());
                benchmarkNames.add(name);
                
                List<Double> benchmarkData = new ArrayList<>();
                JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
                JsonObject percentiles = primaryMetric.getAsJsonObject("scorePercentiles");

                if (percentiles != null) {
                    String unit = primaryMetric.get("scoreUnit").getAsString();
                    for (String key : percentileKeys) {
                        if (percentiles.has(key)) {
                            double value = percentiles.get(key).getAsDouble();
                            double msValue = MetricConversionUtil.convertToMillisecondsPerOp(value, unit);
                            benchmarkData.add(msValue);
                        } else {
                            benchmarkData.add(null);
                        }
                    }
                } else {
                    // No percentiles data, fill with nulls
                    for (int i = 0; i < percentileKeys.length; i++) {
                        benchmarkData.add(null);
                    }
                }
                
                dataByBenchmark.put(name, benchmarkData);
            }
        }

        // Structure matching JavaScript expectations
        percentilesChart.put("percentileLabels", percentileLabels);
        percentilesChart.put("benchmarks", benchmarkNames);
        percentilesChart.put("data", dataByBenchmark);
        
        // Also keep the old structure for backward compatibility
        percentilesChart.put("labels", benchmarkNames);
        Map<String, List<Double>> datasets = new LinkedHashMap<>();
        for (int i = 0; i < percentileKeys.length; i++) {
            String percentileLabel = percentileKeys[i] + "th";
            List<Double> percentileValues = new ArrayList<>();
            for (String benchmarkName : benchmarkNames) {
                List<Double> benchmarkData = dataByBenchmark.get(benchmarkName);
                if (benchmarkData != null && i < benchmarkData.size()) {
                    percentileValues.add(benchmarkData.get(i));
                } else {
                    percentileValues.add(null);
                }
            }
            datasets.put(percentileLabel, percentileValues);
        }
        percentilesChart.put("datasets", datasets);

        return percentilesChart;
    }

    private Map<String, Object> createTrendData() {
        // Placeholder for trend data - will be implemented when historical data is available
        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put("available", false);
        trends.put("message", "Historical data not yet available");
        return trends;
    }

    /**
     * Copies the JSON result file to the output directory.
     */
    public void copyJsonFile(Path jsonFile, String outputDir) throws IOException {
        Path destination = Path.of(outputDir, jsonFile.getFileName().toString());
        Files.copy(jsonFile, destination, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info(INFO.METRICS_FILE_GENERATED.format(destination));
    }
}