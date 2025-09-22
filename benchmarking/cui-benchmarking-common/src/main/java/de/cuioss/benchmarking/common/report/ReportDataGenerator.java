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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.constants.BenchmarkConstants;
import de.cuioss.benchmarking.common.util.JsonSerializationHelper;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Data.BENCHMARK_DATA_JSON;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Directories.DATA_DIR;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Metrics.Modes.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Metrics.Percentiles.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Metrics.Units.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.MESSAGE;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.DateFormats.DISPLAY_TIMESTAMP_PATTERN;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Grades.CssClasses.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.JsonFields.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Messages.HISTORICAL_DATA_NOT_AVAILABLE;
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
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String DATA_FILE_NAME = BENCHMARK_DATA_JSON;

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
        JsonArray benchmarks = JsonSerializationHelper.fromJson(jsonContent, JsonArray.class);

        Map<String, Object> data = new LinkedHashMap<>();

        // Add metadata
        data.put(METADATA, createMetadata(type));

        // Add overview using pre-computed metrics
        data.put(OVERVIEW, createOverview(metrics));

        // Add detailed results
        data.put(BENCHMARKS, createBenchmarkResults(benchmarks));

        // Add chart data (JavaScript expects "chartData" not "charts")
        data.put(CHART_DATA, createChartData(benchmarks));

        // Add trend data with real processing
        data.put(TRENDS, createTrendData(outputDir, metrics));

        // Write data file to data subdirectory
        Path dataDir = Path.of(outputDir, DATA_DIR);
        Files.createDirectories(dataDir);
        Path dataFile = dataDir.resolve(DATA_FILE_NAME);
        Files.writeString(dataFile, JsonSerializationHelper.toJson(data));

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
        badgeGenerator.writeBadgeFiles(metrics, trendMetrics, type, outputDir);
    }

    private Map<String, Object> createMetadata(BenchmarkType type) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Instant now = Instant.now();
        metadata.put(TIMESTAMP, ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)));
        metadata.put(DISPLAY_TIMESTAMP, DateTimeFormatter.ofPattern(DISPLAY_TIMESTAMP_PATTERN)
                .format(now.atOffset(ZoneOffset.UTC)));
        metadata.put(BENCHMARK_TYPE, type.getDisplayName());
        metadata.put(BenchmarkConstants.Report.JsonFields.REPORT_VERSION, BenchmarkConstants.Report.Versions.REPORT_VERSION);
        return metadata;
    }

    private Map<String, Object> createOverview(BenchmarkMetrics metrics) {
        Map<String, Object> overview = new LinkedHashMap<>();

        // Using pre-computed metrics directly
        overview.put(BenchmarkConstants.Report.JsonFields.THROUGHPUT, MetricConversionUtil.formatThroughput(metrics.throughput()));
        overview.put(LATENCY, MetricConversionUtil.formatLatency(metrics.latency()));
        overview.put(THROUGHPUT_BENCHMARK_NAME, metrics.throughputBenchmarkName());
        overview.put(LATENCY_BENCHMARK_NAME, metrics.latencyBenchmarkName());

        // Performance metrics (performanceScore is already rounded)
        overview.put(PERFORMANCE_SCORE, metrics.performanceScore());
        overview.put(PERFORMANCE_GRADE, metrics.performanceGrade());
        overview.put(PERFORMANCE_GRADE_CLASS, getGradeClass(metrics.performanceGrade()));

        return overview;
    }

    private String getGradeClass(String grade) {
        return switch (grade) {
            case "A+" -> GRADE_A_PLUS;
            case "A" -> GRADE_A;
            case "B" -> GRADE_B;
            case "C" -> GRADE_C;
            case "D" -> GRADE_D;
            case "F" -> GRADE_F;
            default -> GRADE_UNKNOWN;
        };
    }

    private List<Map<String, Object>> createBenchmarkResults(JsonArray benchmarks) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            Map<String, Object> result = new LinkedHashMap<>();

            // Basic info
            String fullName = benchmark.get(BENCHMARK).getAsString();
            String simpleName = extractSimpleName(fullName);
            result.put(NAME, simpleName);
            result.put(FULL_NAME, fullName);

            // Mode and metrics
            String mode = benchmark.get(MODE).getAsString();
            result.put(MODE, mode);

            JsonObject primaryMetric = benchmark.getAsJsonObject(PRIMARY_METRIC);
            double score = primaryMetric.get(SCORE).getAsDouble();
            String unit = primaryMetric.get(SCORE_UNIT).getAsString();

            result.put(SCORE, formatScore(score, unit));
            result.put(SCORE_UNIT, unit);

            // Add throughput or latency fields based on mode
            if (BenchmarkConstants.Metrics.Modes.THROUGHPUT.equals(mode) || unit.contains(OPS)) {
                double throughputOps = MetricConversionUtil.convertToOpsPerSecond(score, unit);
                result.put(BenchmarkConstants.Report.JsonFields.THROUGHPUT, MetricConversionUtil.formatThroughput(throughputOps));
            } else if (AVERAGE_TIME.equals(mode) || SAMPLE.equals(mode) || unit.contains(SUFFIX_OP)) {
                double latencyMs = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                result.put(BenchmarkConstants.Report.JsonFields.LATENCY, MetricConversionUtil.formatLatency(latencyMs));
            }

            // Error margin
            double error = primaryMetric.get(SCORE_ERROR).getAsDouble();
            result.put(ERROR, error);
            result.put(ERROR_PERCENTAGE, calculateErrorPercentage(score, error));

            // Confidence intervals
            JsonArray confidence = primaryMetric.getAsJsonArray(SCORE_CONFIDENCE);
            if (confidence != null && confidence.size() == 2) {
                result.put(CONFIDENCE_LOW, confidence.get(0).getAsDouble());
                result.put(CONFIDENCE_HIGH, confidence.get(1).getAsDouble());
            }

            // Percentiles (if available)
            JsonObject percentiles = primaryMetric.getAsJsonObject(SCORE_PERCENTILES);
            if (percentiles != null) {
                Map<String, Double> percentileMap = new LinkedHashMap<>();
                percentiles.entrySet().forEach(entry ->
                        percentileMap.put(entry.getKey(), entry.getValue().getAsDouble()));
                result.put(PERCENTILES, percentileMap);
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
        if (unit.contains(OPS)) {
            double opsPerSec = MetricConversionUtil.convertToOpsPerSecond(score, unit);
            if (opsPerSec >= 1_000_000) {
                return MetricConversionUtil.formatForDisplay(opsPerSec / 1_000_000) + M_OPS_S;
            } else if (opsPerSec >= 1000) {
                return MetricConversionUtil.formatForDisplay(opsPerSec / 1000) + K_OPS_S;
            } else {
                return MetricConversionUtil.formatForDisplay(opsPerSec) + SPACE_OPS_S;
            }
        } else if (unit.contains(SUFFIX_OP)) {
            double msPerOp = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
            if (msPerOp >= 1000) {
                return MetricConversionUtil.formatForDisplay(msPerOp / 1000) + SUFFIX_S;
            } else {
                return MetricConversionUtil.formatForDisplay(msPerOp) + SUFFIX_MS;
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
            String name = extractSimpleName(benchmark.get(BENCHMARK).getAsString());
            String mode = benchmark.get(MODE).getAsString();

            JsonObject primaryMetric = benchmark.getAsJsonObject(PRIMARY_METRIC);
            double score = primaryMetric.get(SCORE).getAsDouble();
            String unit = primaryMetric.get(SCORE_UNIT).getAsString();

            labels.add(name);

            if (BenchmarkConstants.Metrics.Modes.THROUGHPUT.equals(mode) || unit.contains(OPS)) {
                throughput.add(MetricConversionUtil.convertToOpsPerSecond(score, unit));
                latency.add(null);  // No latency for throughput benchmark
            } else if (AVERAGE_TIME.equals(mode) || SAMPLE.equals(mode) || unit.contains(SUFFIX_OP)) {
                throughput.add(null);  // No throughput for latency benchmark
                latency.add(MetricConversionUtil.convertToMillisecondsPerOp(score, unit));
            } else {
                // Unknown benchmark type, add nulls
                throughput.add(null);
                latency.add(null);
            }
        }

        // Direct structure matching JavaScript expectations
        chartData.put(LABELS, labels);
        chartData.put(BenchmarkConstants.Report.JsonFields.THROUGHPUT, throughput);
        chartData.put(BenchmarkConstants.Report.JsonFields.LATENCY, latency);

        // Add percentiles data separately
        chartData.put(PERCENTILES_DATA, createPercentilesChartData(benchmarks));

        return chartData;
    }

    private Map<String, Object> createPercentilesChartData(JsonArray benchmarks) {
        Map<String, Object> percentilesChart = new LinkedHashMap<>();
        List<String> percentileLabels = Arrays.asList(LABEL_P0, LABEL_P50, LABEL_P90, LABEL_P95, LABEL_P99, LABEL_P99_9, LABEL_P99_99, LABEL_P100);
        List<String> benchmarkNames = new ArrayList<>();
        Map<String, List<Double>> dataByBenchmark = new LinkedHashMap<>();

        // Standard percentile keys mapping to labels
        String[] percentileKeys = {P_0, P_50, P_90, P_95, P_99, P_99_9, P_99_99, P_100};

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String mode = benchmark.get(MODE).getAsString();

            // Only process latency benchmarks for percentiles
            if (AVERAGE_TIME.equals(mode) || SAMPLE.equals(mode)) {
                String name = extractSimpleName(benchmark.get(BENCHMARK).getAsString());
                benchmarkNames.add(name);

                List<Double> benchmarkData = new ArrayList<>();
                JsonObject primaryMetric = benchmark.getAsJsonObject(PRIMARY_METRIC);
                JsonObject percentiles = primaryMetric.getAsJsonObject(SCORE_PERCENTILES);

                if (percentiles != null) {
                    String unit = primaryMetric.get(SCORE_UNIT).getAsString();
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
        percentilesChart.put(PERCENTILE_LABELS, percentileLabels);
        percentilesChart.put(BenchmarkConstants.Report.JsonFields.BENCHMARKS, benchmarkNames);
        percentilesChart.put(DATA, dataByBenchmark);
        percentilesChart.put(LABELS, benchmarkNames);

        Map<String, List<Double>> datasets = new LinkedHashMap<>();
        for (int i = 0; i < percentileKeys.length; i++) {
            String percentileLabel = percentileKeys[i] + SUFFIX_TH;
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
        percentilesChart.put(DATASETS, datasets);

        return percentilesChart;
    }

    private Map<String, Object> createTrendData(String outputDir, BenchmarkMetrics metrics) {
        // Check if external history directory is provided via system property
        String externalHistoryPath = System.getProperty("benchmark.history.dir");
        Path historyDir;

        if (externalHistoryPath != null && !externalHistoryPath.isEmpty()) {
            // Use external history directory (e.g., for CI/CD workflows)
            historyDir = Path.of(externalHistoryPath);
            LOGGER.info(INFO.PROCESSING_METRICS.format("Using external history directory: " + historyDir));
        } else {
            // Default to output directory/history (for local runs and tests)
            historyDir = Path.of(outputDir, "history");
        }

        if (!Files.exists(historyDir)) {
            // First run, no history available
            LOGGER.info(INFO.PROCESSING_METRICS.format("History directory not found: " + historyDir));
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
        trendData.put(AVAILABLE, true);
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
        trends.put(AVAILABLE, false);
        trends.put(MESSAGE, HISTORICAL_DATA_NOT_AVAILABLE);
        return trends;
    }

    private String generateTrendSummary(TrendDataProcessor.TrendMetrics trendMetrics) {
        String direction = trendMetrics.getDirection();
        double change = Math.abs(trendMetrics.getChangePercentage());

        if (BenchmarkConstants.Report.Badge.TrendDirection.STABLE.equals(direction)) {
            return BenchmarkConstants.Report.Messages.PERFORMANCE_STABLE_FORMAT.formatted(change);
        } else if (BenchmarkConstants.Report.Badge.TrendDirection.UP.equals(direction)) {
            return BenchmarkConstants.Report.Messages.PERFORMANCE_IMPROVED_FORMAT.formatted(change);
        } else {
            return BenchmarkConstants.Report.Messages.PERFORMANCE_DECREASED_FORMAT.formatted(change);
        }
    }

}