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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates shields.io compatible badges for benchmark performance metrics.
 * <p>
 * This generator creates JSON badge files from benchmark JSON results that can be used 
 * directly with shields.io endpoint badges or converted to SVG badges for display in documentation.
 * <p>
 * Badge types:
 * <ul>
 *   <li>Performance badges with throughput and scoring</li>
 *   <li>Trend analysis badges showing performance changes</li>
 *   <li>Last run timestamp badges</li>
 * </ul>
 */
public class BadgeGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    // Badge JSON field constants
    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String LABEL = "label";
    private static final String MESSAGE = "message";
    private static final String COLOR = "color";

    // Color constants
    private static final String COLOR_BRIGHT_GREEN = "brightgreen";
    private static final String COLOR_GREEN = "green";
    private static final String COLOR_YELLOW = "yellow";
    private static final String COLOR_ORANGE = "orange";
    private static final String COLOR_RED = "red";
    private static final String COLOR_BLUE = "blue";

    /**
     * Generates a performance badge showing current benchmark performance.
     *
     * @param jsonFile the path to the benchmark JSON results file
     * @param type the benchmark type
     * @param outputDir the output directory for the badge file
     * @throws IOException if writing the badge file fails
     */
    public void generatePerformanceBadge(Path jsonFile, BenchmarkType type, String outputDir)
            throws IOException {

        // Parse JSON file
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);

        PerformanceScore score = calculatePerformanceScore(benchmarks);

        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put(SCHEMA_VERSION, 1);
        badge.put(LABEL, type.getBadgeLabel());
        badge.put(MESSAGE, formatPerformanceMessage(score));
        badge.put(COLOR, getColorForScore(score));

        String filename = type.getPerformanceBadgeFileName();
        Path badgeFile = Path.of(outputDir, filename);

        Files.writeString(badgeFile, GSON.toJson(badge));
        LOGGER.info(INFO.BADGE_GENERATED.format("performance", badgeFile));
    }

    /**
     * Generates a trend badge showing performance trends over time.
     *
     * @param jsonFile the path to the benchmark JSON results file
     * @param type the benchmark type
     * @param outputDir the output directory for the badge file
     * @throws IOException if writing the badge file fails
     */
    public void generateTrendBadge(Path jsonFile, BenchmarkType type, String outputDir)
            throws IOException {
        // Load previous results for trend analysis
        PerformanceHistory history = loadPerformanceHistory(outputDir + "/../history");

        // Parse current results
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);
        TrendAnalysis trend = analyzeTrend(benchmarks, history);

        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put(SCHEMA_VERSION, 1);
        badge.put(LABEL, "Performance Trend");
        badge.put(MESSAGE, formatTrendMessage(trend));
        badge.put(COLOR, getTrendColor(trend));

        String filename = type.getTrendBadgeFileName();
        Path badgeFile = Path.of(outputDir, filename);

        Files.writeString(badgeFile, GSON.toJson(badge));
        LOGGER.info(INFO.BADGE_GENERATED.format("trend", badgeFile));
    }

    /**
     * Generates a last run badge showing when benchmarks were last executed.
     *
     * @param outputDir the output directory for the badge file
     * @throws IOException if writing the badge file fails
     */
    public void generateLastRunBadge(String outputDir) throws IOException {
        String timestamp = ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC));

        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put(SCHEMA_VERSION, 1);
        badge.put(LABEL, "Last Run");
        badge.put(MESSAGE, formatTimestamp(timestamp));
        badge.put(COLOR, COLOR_BLUE);

        Path badgeFile = Path.of(outputDir, "last-run-badge.json");
        Files.writeString(badgeFile, GSON.toJson(badge));
        LOGGER.info(INFO.BADGE_GENERATED.format("last run", badgeFile));
    }

    private PerformanceScore calculatePerformanceScore(JsonArray benchmarks) {
        if (benchmarks == null || benchmarks.isEmpty()) {
            throw new IllegalArgumentException("No benchmark results found in JSON. Cannot generate badge.");
        }

        // Group benchmarks by name to pair throughput and latency
        Map<String, Double> throughputByBenchmark = new LinkedHashMap<>();
        Map<String, Double> latencyByBenchmark = new LinkedHashMap<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();

            String benchmarkName = benchmark.get("benchmark").getAsString();
            String mode = benchmark.get("mode").getAsString();

            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            double score = primaryMetric.get("score").getAsDouble();
            String unit = primaryMetric.get("scoreUnit").getAsString();

            // Process throughput results (mode == "thrpt" or unit contains "ops")
            if ("thrpt".equals(mode) || unit.contains("ops")) {
                double opsPerSec = MetricConversionUtil.convertToOpsPerSecond(score, unit);
                throughputByBenchmark.put(benchmarkName, opsPerSec);
                // Also calculate latency from throughput
                if (opsPerSec > 0) {
                    double msPerOp = 1000.0 / opsPerSec; // Convert ops/s to ms/op
                    latencyByBenchmark.put(benchmarkName, msPerOp);
                }
            } else if ("avgt".equals(mode) || "sample".equals(mode) || unit.contains("/op")) {
                double msPerOp = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                latencyByBenchmark.put(benchmarkName, msPerOp);
                // Also calculate throughput from latency if not already present
                if (!throughputByBenchmark.containsKey(benchmarkName) && msPerOp > 0) {
                    double opsPerSec = 1000.0 / msPerOp; // Convert ms/op to ops/s
                    throughputByBenchmark.put(benchmarkName, opsPerSec);
                }
            }
        }

        // Calculate averages
        double avgThroughput = throughputByBenchmark.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double avgLatency = latencyByBenchmark.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Calculate performance score using the new formula from SummaryGenerator
        SummaryGenerator summaryGen = new SummaryGenerator();
        double performanceScore = summaryGen.calculatePerformanceScore(avgThroughput, avgLatency);

        return new PerformanceScore(performanceScore, avgThroughput, avgLatency);
    }

    // All metric conversions now use the centralized MetricConversionUtil

    private String formatPerformanceMessage(PerformanceScore score) {
        // Format: "Score Grade (throughput, latency)"
        // Example: "91 A (5.8K ops/s, 1.17ms)"
        
        SummaryGenerator summaryGen = new SummaryGenerator();
        String grade = summaryGen.getPerformanceGrade(score.score());
        String scoreFormatted = "%s %s".formatted(MetricConversionUtil.formatForDisplay(score.score()), grade);
        String throughputFormatted = formatNumber(score.throughput()) + " ops/s";
        String latencyFormatted = formatLatency(score.latency());

        return "%s (%s, %s)".formatted(scoreFormatted, throughputFormatted, latencyFormatted);
    }

    private String formatNumber(double value) {
        if (value >= 1_000_000) {
            double scaledValue = value / 1_000_000;
            return "%sM".formatted(MetricConversionUtil.formatForDisplay(scaledValue));
        } else if (value >= 1000) {
            double scaledValue = value / 1000;
            return "%sK".formatted(MetricConversionUtil.formatForDisplay(scaledValue));
        } else {
            return MetricConversionUtil.formatForDisplay(value);
        }
    }

    private String formatLatency(double ms) {
        if (ms >= 1000) {
            double seconds = ms / 1000;
            return "%ss".formatted(MetricConversionUtil.formatForDisplay(seconds));
        } else {
            return "%sms".formatted(MetricConversionUtil.formatForDisplay(ms));
        }
    }

    private String getColorForScore(PerformanceScore score) {
        // Color based on the 0-100 performance score
        double value = score.score();
        if (value >= 90) return COLOR_BRIGHT_GREEN;  // Grade A
        if (value >= 75) return COLOR_GREEN;         // Grade B
        if (value >= 60) return COLOR_YELLOW;        // Grade C
        if (value >= 40) return COLOR_ORANGE;        // Grade D
        return COLOR_RED;                             // Grade F
    }

    private TrendAnalysis analyzeTrend(JsonArray currentResults, PerformanceHistory history) {
        // For now, return stable trend as we don't have historical data
        return new TrendAnalysis(TrendDirection.STABLE, 0.0);
    }

    private String formatTrendMessage(TrendAnalysis trend) {
        return switch (trend.direction()) {
            case IMPROVING -> "↑ +%.1f%%".formatted(trend.percentChange());
            case DEGRADING -> "↓ %.1f%%".formatted(trend.percentChange());
            case STABLE -> "→ stable";
        };
    }

    private String getTrendColor(TrendAnalysis trend) {
        return switch (trend.direction()) {
            case IMPROVING -> COLOR_GREEN;
            case DEGRADING -> COLOR_RED;
            case STABLE -> COLOR_BLUE;
        };
    }

    private String formatTimestamp(String timestamp) {
        // Format as readable date
        return timestamp.substring(0, 10); // YYYY-MM-DD
    }

    private PerformanceHistory loadPerformanceHistory(String historyPath) {
        // TODO: Implement loading of historical data from JSON files
        return new PerformanceHistory(Collections.emptyList());
    }

    // Value classes for structured data
    private record PerformanceScore(double score, double throughput, double latency) {
    }

    private record TrendAnalysis(TrendDirection direction, double percentChange) {
    }

    private record PerformanceHistory(List<HistoricalResult> results) {
    }

    private record HistoricalResult(Instant timestamp, double score) {
    }

    private enum TrendDirection {
        IMPROVING, STABLE, DEGRADING
    }
}