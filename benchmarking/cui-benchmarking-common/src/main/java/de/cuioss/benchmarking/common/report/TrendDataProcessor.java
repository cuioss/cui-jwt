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
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.TrendDirection.STABLE;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.WARN;

/**
 * Processes historical benchmark data for time-series analysis and trend visualization.
 * <p>
 * This processor specializes in time-series analysis of benchmark results:
 * <ul>
 *   <li>Loading and managing historical benchmark data</li>
 *   <li>Detecting performance trends over time</li>
 *   <li>Preparing data for trend visualization charts</li>
 *   <li>Tracking changes between benchmark runs</li>
 * </ul>
 * <p>
 * Use this class when you need to analyze benchmark performance over time.
 * For pure statistical computations, use {@link StatisticsCalculator}.
 * For processing individual benchmark results, use {@link MetricsComputer}.
 * 
 * @see StatisticsCalculator for statistical computations (mean, median, std dev, etc.)
 * @see MetricsComputer for processing individual benchmark results
 */
public class TrendDataProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(TrendDataProcessor.class);
    private static final int MAX_HISTORY_ENTRIES = 10;
    private static final double STABILITY_THRESHOLD = 0.02; // 2% change threshold for "stable"
    
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Represents a single historical data point.
     */
    @Getter @RequiredArgsConstructor public static class HistoricalDataPoint {
        private final String timestamp;
        private final double throughput;
        private final double latency;
        private final double performanceScore;
        private final String commitSha;
    }

    /**
     * Represents calculated trend metrics.
     */
    @Getter @RequiredArgsConstructor public static class TrendMetrics {
        private final String direction; // "up", "down", or "stable"
        private final double changePercentage;
        private final double movingAverage;
        private final double throughputTrend;
        private final double latencyTrend;
    }

    /**
     * Loads historical data files from the history directory.
     * 
     * @param historyDir path to the history directory
     * @return list of historical benchmark data points, sorted by timestamp (newest first)
     */
    public List<HistoricalDataPoint> loadHistoricalData(Path historyDir) {
        if (!Files.exists(historyDir)) {
            return List.of();
        }

        List<HistoricalDataPoint> dataPoints = new ArrayList<>();

        try (Stream<Path> pathStream = Files.list(historyDir)) {
            List<Path> historyFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder()) // Newest first
                    .limit(MAX_HISTORY_ENTRIES)
                    .toList();

            for (Path file : historyFiles) {
                try {
                    String jsonContent = Files.readString(file);
                    JsonObject data = gson.fromJson(jsonContent, JsonObject.class);

                    HistoricalDataPoint point = extractDataPoint(data, file.getFileName().toString());
                    if (point != null) {
                        dataPoints.add(point);
                    }
                } catch (IOException | JsonSyntaxException e) {
                    LOGGER.warn(WARN.ISSUE_DURING_INDEX_GENERATION.format("parsing history file: " + file), e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn(WARN.ISSUE_DURING_INDEX_GENERATION.format("loading historical data"), e);
        }

        return dataPoints;
    }

    /**
     * Calculates trend metrics from historical data.
     * 
     * @param currentMetrics current benchmark metrics
     * @param historicalData previous benchmark results
     * @return trend metrics with analysis
     */
    public TrendMetrics calculateTrends(BenchmarkMetrics currentMetrics,
            List<HistoricalDataPoint> historicalData) {
        if (historicalData.isEmpty()) {
            return new TrendMetrics(STABLE, 0.0, currentMetrics.performanceScore(), 0.0, 0.0);
        }

        // Compare with most recent historical data
        HistoricalDataPoint previousRun = historicalData.getFirst();
        double changePercentage = StatisticsCalculator.calculatePercentageChange(
                previousRun.getPerformanceScore(),
                currentMetrics.performanceScore());

        // Determine trend direction
        String direction = StatisticsCalculator.determineTrendDirection(
                changePercentage, STABILITY_THRESHOLD * 100);

        // Calculate moving average (last 5 runs)
        List<Double> recentScores = new ArrayList<>();
        recentScores.add(currentMetrics.performanceScore());
        historicalData.stream()
                .limit(4)
                .map(HistoricalDataPoint::getPerformanceScore)
                .forEach(recentScores::add);
        double movingAverage = StatisticsCalculator.calculateMovingAverage(recentScores, 5);

        // Calculate throughput and latency trends
        double throughputTrend = StatisticsCalculator.calculatePercentageChange(
                previousRun.getThroughput(),
                currentMetrics.throughput()
        );
        double latencyTrend = StatisticsCalculator.calculatePercentageChange(
                previousRun.getLatency(),
                currentMetrics.latency()
        );

        return new TrendMetrics(direction, changePercentage, movingAverage,
                throughputTrend, latencyTrend);
    }

    /**
     * Generates chart-ready trend data for visualization.
     * 
     * @param historicalData historical benchmark results
     * @param currentMetrics current benchmark metrics (optional)
     * @return map containing chart labels and datasets
     */
    public Map<String, Object> generateTrendChartData(List<HistoricalDataPoint> historicalData,
            BenchmarkMetrics currentMetrics) {
        Map<String, Object> chartData = new LinkedHashMap<>();

        // Prepare data lists
        List<String> timestamps = new ArrayList<>();
        List<Double> throughputValues = new ArrayList<>();
        List<Double> latencyValues = new ArrayList<>();
        List<Double> performanceScores = new ArrayList<>();

        // Add historical data (reversed to show oldest first in chart)
        List<HistoricalDataPoint> reversedData = new ArrayList<>(historicalData);
        Collections.reverse(reversedData);

        for (HistoricalDataPoint point : reversedData) {
            timestamps.add(point.getTimestamp());
            throughputValues.add(point.getThroughput());
            latencyValues.add(point.getLatency());
            performanceScores.add(point.getPerformanceScore());
        }

        // Add current data if provided
        if (currentMetrics != null) {
            timestamps.add("Current");
            throughputValues.add(currentMetrics.throughput());
            latencyValues.add(currentMetrics.latency());
            performanceScores.add(currentMetrics.performanceScore());
        }

        chartData.put("timestamps", timestamps);
        chartData.put("throughput", throughputValues);
        chartData.put("latency", latencyValues);
        chartData.put("performanceScores", performanceScores);

        // Add statistical analysis
        Map<String, Object> statistics = new LinkedHashMap<>();
        StatisticsCalculator.Statistics throughputStats = StatisticsCalculator.computeStatistics(throughputValues);
        StatisticsCalculator.Statistics latencyStats = StatisticsCalculator.computeStatistics(latencyValues);

        statistics.put("throughputMin", throughputStats.getMin());
        statistics.put("throughputMax", throughputStats.getMax());
        statistics.put("throughputAvg", throughputStats.getMean());
        statistics.put("latencyMin", latencyStats.getMin());
        statistics.put("latencyMax", latencyStats.getMax());
        statistics.put("latencyAvg", latencyStats.getMean());

        chartData.put("statistics", statistics);

        return chartData;
    }

    /**
     * Extracts a data point from JSON data.
     */
    private HistoricalDataPoint extractDataPoint(JsonObject data, String filename) {
        try {
            JsonObject metadata = data.getAsJsonObject("metadata");
            JsonObject overview = data.getAsJsonObject("overview");

            String timestamp = metadata.has("timestamp")
                    ? metadata.get("timestamp").getAsString()
                    : extractTimestampFromFilename(filename);

            double throughput = overview.has("throughput")
                    ? parseMetricValue(overview.get("throughput").getAsString())
                    : 0.0;

            double latency = overview.has("latency")
                    ? parseMetricValue(overview.get("latency").getAsString())
                    : 0.0;

            double performanceScore = overview.has("performanceScore")
                    ? overview.get("performanceScore").getAsDouble()
                    : 0.0;

            String commitSha = extractCommitFromFilename(filename);

            return new HistoricalDataPoint(timestamp, throughput, latency, performanceScore, commitSha);
        } catch (NullPointerException | ClassCastException | IllegalStateException | UnsupportedOperationException e) {
            LOGGER.warn(WARN.ISSUE_DURING_INDEX_GENERATION.format("extracting data point"), e);
            return null;
        }
    }

    /**
     * Parses a metric value from a formatted string (e.g., "1.5K ops/s" -> 1500.0).
     */
    private double parseMetricValue(String value) {
        if (value == null || "N/A".equals(value)) {
            return 0.0;
        }

        // Remove units and parse
        String cleanValue = value.replaceAll("[^0-9.KMG]", "").trim();
        if (cleanValue.isEmpty()) {
            return 0.0;
        }

        double multiplier = 1.0;
        if (cleanValue.contains("K")) {
            multiplier = 1000.0;
            cleanValue = cleanValue.replace("K", "");
        } else if (cleanValue.contains("M")) {
            multiplier = 1_000_000.0;
            cleanValue = cleanValue.replace("M", "");
        } else if (cleanValue.contains("G")) {
            multiplier = 1_000_000_000.0;
            cleanValue = cleanValue.replace("G", "");
        }

        try {
            return Double.parseDouble(cleanValue) * multiplier;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }


    /**
     * Extracts timestamp from filename.
     */
    private String extractTimestampFromFilename(String filename) {
        int dashIndex = filename.lastIndexOf('-');
        if (dashIndex > 0) {
            return filename.substring(0, dashIndex);
        }
        return filename;
    }

    /**
     * Extracts commit SHA from filename.
     */
    private String extractCommitFromFilename(String filename) {
        int dashIndex = filename.lastIndexOf('-');
        int dotIndex = filename.lastIndexOf('.');
        if (dashIndex > 0 && dotIndex > dashIndex) {
            return filename.substring(dashIndex + 1, dotIndex);
        }
        return "unknown";
    }
}