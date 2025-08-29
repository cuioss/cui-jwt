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

import static de.cuioss.benchmarking.common.report.ReportConstants.*;
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
    private static final String DATA_FILE_NAME = FILES.BENCHMARK_DATA_JSON;

    private final TrendDataProcessor trendProcessor = new TrendDataProcessor();
    private final BadgeGenerator badgeGenerator = new BadgeGenerator();
    private final HistoricalDataManager historyManager = new HistoricalDataManager();

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
        data.put(JSON_FIELDS.METADATA, createMetadata(type));

        // Add overview using pre-computed metrics
        data.put(JSON_FIELDS.OVERVIEW, createOverview(metrics, benchmarks.size()));

        // Add detailed results
        data.put(JSON_FIELDS.BENCHMARKS, createBenchmarkResults(benchmarks));

        // Add chart data (JavaScript expects "chartData" not "charts")
        data.put(JSON_FIELDS.CHART_DATA, createChartData(benchmarks));

        // Add trend data with real processing
        data.put(JSON_FIELDS.TRENDS, createTrendData(outputDir, metrics));

        // Write data file to data subdirectory
        Path dataDir = Path.of(outputDir, FILES.DATA_DIR);
        Files.createDirectories(dataDir);
        Path dataFile = dataDir.resolve(DATA_FILE_NAME);
        Files.writeString(dataFile, GSON.toJson(data));

        LOGGER.info(INFO.METRICS_FILE_GENERATED.format(dataFile));

        // Archive current run to history
        String commitSha = System.getenv("GITHUB_SHA");
        if (commitSha == null || commitSha.isEmpty()) {
            commitSha = "local-run";
        }
        historyManager.archiveCurrentRun(data, outputDir, commitSha);

        // Enforce retention policy
        Path historyDir = Path.of(outputDir, "history");
        historyManager.enforceRetentionPolicy(historyDir);

        // Generate badges
        TrendDataProcessor.TrendMetrics trendMetrics = null;
        if (historyManager.hasHistoricalData(outputDir)) {
            List<TrendDataProcessor.HistoricalDataPoint> historicalData =
                    trendProcessor.loadHistoricalData(historyDir);
            if (!historicalData.isEmpty()) {
                trendMetrics = trendProcessor.calculateTrends(metrics, historicalData);
            }
        }
        badgeGenerator.writeBadgeFiles(metrics, trendMetrics, outputDir);
    }

    private Map<String, Object> createMetadata(BenchmarkType type) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Instant now = Instant.now();
        metadata.put(JSON_FIELDS.TIMESTAMP, ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)));
        metadata.put(JSON_FIELDS.DISPLAY_TIMESTAMP, DateTimeFormatter.ofPattern(DATE_FORMATS.DISPLAY_TIMESTAMP_PATTERN)
                .format(now.atOffset(ZoneOffset.UTC)));
        metadata.put(JSON_FIELDS.BENCHMARK_TYPE, type.getDisplayName());
        metadata.put(JSON_FIELDS.REPORT_VERSION, VERSIONS.REPORT_VERSION);
        return metadata;
    }

    private Map<String, Object> createOverview(BenchmarkMetrics metrics, int totalBenchmarks) {
        Map<String, Object> overview = new LinkedHashMap<>();

        // Using pre-computed metrics directly
        overview.put(JSON_FIELDS.THROUGHPUT, MetricConversionUtil.formatThroughput(metrics.throughput()));
        overview.put(JSON_FIELDS.LATENCY, MetricConversionUtil.formatLatency(metrics.latency()));
        overview.put(JSON_FIELDS.THROUGHPUT_BENCHMARK_NAME, metrics.throughputBenchmarkName());
        overview.put(JSON_FIELDS.LATENCY_BENCHMARK_NAME, metrics.latencyBenchmarkName());

        // Performance metrics (performanceScore is already rounded)
        overview.put(JSON_FIELDS.PERFORMANCE_SCORE, metrics.performanceScore());
        overview.put(JSON_FIELDS.PERFORMANCE_GRADE, metrics.performanceGrade());
        overview.put(JSON_FIELDS.PERFORMANCE_GRADE_CLASS, getGradeClass(metrics.performanceGrade()));

        return overview;
    }

    private String getGradeClass(String grade) {
        return switch (grade) {
            case "A+" -> GRADES.CSS_CLASSES.GRADE_A_PLUS;
            case "A" -> GRADES.CSS_CLASSES.GRADE_A;
            case "B" -> GRADES.CSS_CLASSES.GRADE_B;
            case "C" -> GRADES.CSS_CLASSES.GRADE_C;
            case "D" -> GRADES.CSS_CLASSES.GRADE_D;
            case "F" -> GRADES.CSS_CLASSES.GRADE_F;
            default -> GRADES.CSS_CLASSES.GRADE_UNKNOWN;
        };
    }

    private List<Map<String, Object>> createBenchmarkResults(JsonArray benchmarks) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            Map<String, Object> result = new LinkedHashMap<>();

            // Basic info
            String fullName = benchmark.get(JSON_FIELDS.BENCHMARK).getAsString();
            String simpleName = extractSimpleName(fullName);
            result.put(JSON_FIELDS.NAME, simpleName);
            result.put(JSON_FIELDS.FULL_NAME, fullName);

            // Mode and metrics
            String mode = benchmark.get(JSON_FIELDS.MODE).getAsString();
            result.put(JSON_FIELDS.MODE, mode);

            JsonObject primaryMetric = benchmark.getAsJsonObject(JSON_FIELDS.PRIMARY_METRIC);
            double score = primaryMetric.get(JSON_FIELDS.SCORE).getAsDouble();
            String unit = primaryMetric.get(JSON_FIELDS.SCORE_UNIT).getAsString();

            result.put(JSON_FIELDS.SCORE, formatScore(score, unit));
            result.put(JSON_FIELDS.SCORE_UNIT, unit);

            // Add throughput or latency fields based on mode
            if (MODES.THROUGHPUT.equals(mode) || unit.contains(UNITS.OPS)) {
                double throughputOps = MetricConversionUtil.convertToOpsPerSecond(score, unit);
                result.put(JSON_FIELDS.THROUGHPUT, MetricConversionUtil.formatThroughput(throughputOps));
            } else if (MODES.AVERAGE_TIME.equals(mode) || MODES.SAMPLE.equals(mode) || unit.contains(UNITS.SUFFIX_OP)) {
                double latencyMs = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                result.put(JSON_FIELDS.LATENCY, MetricConversionUtil.formatLatency(latencyMs));
            }

            // Error margin
            double error = primaryMetric.get(JSON_FIELDS.SCORE_ERROR).getAsDouble();
            result.put(JSON_FIELDS.ERROR, error);
            result.put(JSON_FIELDS.ERROR_PERCENTAGE, calculateErrorPercentage(score, error));

            // Confidence intervals
            JsonArray confidence = primaryMetric.getAsJsonArray(JSON_FIELDS.SCORE_CONFIDENCE);
            if (confidence != null && confidence.size() == 2) {
                result.put(JSON_FIELDS.CONFIDENCE_LOW, confidence.get(0).getAsDouble());
                result.put(JSON_FIELDS.CONFIDENCE_HIGH, confidence.get(1).getAsDouble());
            }

            // Percentiles (if available)
            JsonObject percentiles = primaryMetric.getAsJsonObject(JSON_FIELDS.SCORE_PERCENTILES);
            if (percentiles != null) {
                Map<String, Double> percentileMap = new LinkedHashMap<>();
                percentiles.entrySet().forEach(entry ->
                        percentileMap.put(entry.getKey(), entry.getValue().getAsDouble()));
                result.put(JSON_FIELDS.PERCENTILES, percentileMap);
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
        if (unit.contains(UNITS.OPS)) {
            double opsPerSec = MetricConversionUtil.convertToOpsPerSecond(score, unit);
            if (opsPerSec >= 1_000_000) {
                return MetricConversionUtil.formatForDisplay(opsPerSec / 1_000_000) + UNITS.M_OPS_S;
            } else if (opsPerSec >= 1000) {
                return MetricConversionUtil.formatForDisplay(opsPerSec / 1000) + UNITS.K_OPS_S;
            } else {
                return MetricConversionUtil.formatForDisplay(opsPerSec) + UNITS.SPACE_OPS_S;
            }
        } else if (unit.contains(UNITS.SUFFIX_OP)) {
            double msPerOp = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
            if (msPerOp >= 1000) {
                return MetricConversionUtil.formatForDisplay(msPerOp / 1000) + UNITS.SUFFIX_S;
            } else {
                return MetricConversionUtil.formatForDisplay(msPerOp) + UNITS.SUFFIX_MS;
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
            String name = extractSimpleName(benchmark.get(JSON_FIELDS.BENCHMARK).getAsString());
            String mode = benchmark.get(JSON_FIELDS.MODE).getAsString();

            JsonObject primaryMetric = benchmark.getAsJsonObject(JSON_FIELDS.PRIMARY_METRIC);
            double score = primaryMetric.get(JSON_FIELDS.SCORE).getAsDouble();
            String unit = primaryMetric.get(JSON_FIELDS.SCORE_UNIT).getAsString();

            labels.add(name);

            if (MODES.THROUGHPUT.equals(mode) || unit.contains(UNITS.OPS)) {
                throughput.add(MetricConversionUtil.convertToOpsPerSecond(score, unit));
                latency.add(null);  // No latency for throughput benchmark
            } else if (MODES.AVERAGE_TIME.equals(mode) || MODES.SAMPLE.equals(mode) || unit.contains(UNITS.SUFFIX_OP)) {
                throughput.add(null);  // No throughput for latency benchmark
                latency.add(MetricConversionUtil.convertToMillisecondsPerOp(score, unit));
            } else {
                // Unknown benchmark type, add nulls
                throughput.add(null);
                latency.add(null);
            }
        }

        // Direct structure matching JavaScript expectations
        chartData.put(JSON_FIELDS.LABELS, labels);
        chartData.put(JSON_FIELDS.THROUGHPUT, throughput);
        chartData.put(JSON_FIELDS.LATENCY, latency);

        // Add percentiles data separately
        chartData.put(JSON_FIELDS.PERCENTILES_DATA, createPercentilesChartData(benchmarks));

        return chartData;
    }

    private Map<String, Object> createPercentilesChartData(JsonArray benchmarks) {
        Map<String, Object> percentilesChart = new LinkedHashMap<>();
        List<String> percentileLabels = Arrays.asList(PERCENTILES.LABEL_P0, PERCENTILES.LABEL_P50, PERCENTILES.LABEL_P90, PERCENTILES.LABEL_P95, PERCENTILES.LABEL_P99, PERCENTILES.LABEL_P99_9, PERCENTILES.LABEL_P99_99, PERCENTILES.LABEL_P100);
        List<String> benchmarkNames = new ArrayList<>();
        Map<String, List<Double>> dataByBenchmark = new LinkedHashMap<>();

        // Standard percentile keys mapping to labels
        String[] percentileKeys = {PERCENTILES.P_0, PERCENTILES.P_50, PERCENTILES.P_90, PERCENTILES.P_95, PERCENTILES.P_99, PERCENTILES.P_99_9, PERCENTILES.P_99_99, PERCENTILES.P_100};

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String mode = benchmark.get(JSON_FIELDS.MODE).getAsString();

            // Only process latency benchmarks for percentiles
            if (MODES.AVERAGE_TIME.equals(mode) || MODES.SAMPLE.equals(mode)) {
                String name = extractSimpleName(benchmark.get(JSON_FIELDS.BENCHMARK).getAsString());
                benchmarkNames.add(name);

                List<Double> benchmarkData = new ArrayList<>();
                JsonObject primaryMetric = benchmark.getAsJsonObject(JSON_FIELDS.PRIMARY_METRIC);
                JsonObject percentiles = primaryMetric.getAsJsonObject(JSON_FIELDS.SCORE_PERCENTILES);

                if (percentiles != null) {
                    String unit = primaryMetric.get(JSON_FIELDS.SCORE_UNIT).getAsString();
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
        percentilesChart.put(JSON_FIELDS.PERCENTILE_LABELS, percentileLabels);
        percentilesChart.put(JSON_FIELDS.BENCHMARKS, benchmarkNames);
        percentilesChart.put(JSON_FIELDS.DATA, dataByBenchmark);

        // Also keep the old structure for backward compatibility
        percentilesChart.put(JSON_FIELDS.LABELS, benchmarkNames);
        Map<String, List<Double>> datasets = new LinkedHashMap<>();
        for (int i = 0; i < percentileKeys.length; i++) {
            String percentileLabel = percentileKeys[i] + PERCENTILES.SUFFIX_TH;
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
        percentilesChart.put(JSON_FIELDS.DATASETS, datasets);

        return percentilesChart;
    }

    private Map<String, Object> createTrendData(String outputDir, BenchmarkMetrics metrics) {
        Path historyDir = Path.of(outputDir, "history");

        if (!Files.exists(historyDir)) {
            // First run, no history available
            return createNoHistoryResponse();
        }

        List<TrendDataProcessor.HistoricalDataPoint> historicalData =
                trendProcessor.loadHistoricalData(historyDir);

        if (historicalData.isEmpty()) {
            return createNoHistoryResponse();
        }

        TrendDataProcessor.TrendMetrics trendMetrics =
                trendProcessor.calculateTrends(metrics, historicalData);

        Map<String, Object> trendData = new LinkedHashMap<>();
        trendData.put(JSON_FIELDS.AVAILABLE, true);
        trendData.put("direction", trendMetrics.getDirection());
        trendData.put("changePercentage", trendMetrics.getChangePercentage());
        trendData.put("movingAverage", trendMetrics.getMovingAverage());
        trendData.put("throughputTrend", trendMetrics.getThroughputTrend());
        trendData.put("latencyTrend", trendMetrics.getLatencyTrend());
        trendData.put("chartData", trendProcessor.generateTrendChartData(historicalData, metrics));
        trendData.put("summary", generateTrendSummary(trendMetrics));

        return trendData;
    }

    private Map<String, Object> createNoHistoryResponse() {
        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put(JSON_FIELDS.AVAILABLE, false);
        trends.put(BADGE.MESSAGE, MESSAGES.HISTORICAL_DATA_NOT_AVAILABLE);
        return trends;
    }

    private String generateTrendSummary(TrendDataProcessor.TrendMetrics trendMetrics) {
        String direction = trendMetrics.getDirection();
        double change = Math.abs(trendMetrics.getChangePercentage());

        if ("stable".equals(direction)) {
            return "Performance is stable (%.1f%% change)".formatted(change);
        } else if ("up".equals(direction)) {
            return "Performance improved by %.1f%%".formatted(change);
        } else {
            return "Performance decreased by %.1f%%".formatted(change);
        }
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