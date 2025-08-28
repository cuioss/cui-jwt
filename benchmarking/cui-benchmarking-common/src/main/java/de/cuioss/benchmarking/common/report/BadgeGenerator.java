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
import com.google.gson.JsonArray;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static de.cuioss.benchmarking.common.report.ReportConstants.BADGE;
import static de.cuioss.benchmarking.common.report.ReportConstants.FILES;
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

    /**
     * Generates a performance badge showing current benchmark performance.
     *
     * @param metrics the pre-computed benchmark metrics
     * @param type the benchmark type
     * @param outputDir the output directory for the badge file
     * @throws IOException if writing the badge file fails
     */
    public void generatePerformanceBadge(BenchmarkMetrics metrics, BenchmarkType type, String outputDir)
            throws IOException {

        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put(BADGE.SCHEMA_VERSION, 1);
        badge.put(BADGE.LABEL, type.getBadgeLabel());
        badge.put(BADGE.MESSAGE, formatPerformanceMessage(metrics));
        badge.put(BADGE.COLOR, getColorForScore(metrics.performanceScore()));

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
        badge.put(BADGE.SCHEMA_VERSION, 1);
        badge.put(BADGE.LABEL, BADGE.LABELS.PERFORMANCE_TREND);
        badge.put(BADGE.MESSAGE, formatTrendMessage(trend));
        badge.put(BADGE.COLOR, getTrendColor(trend));

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
        badge.put(BADGE.SCHEMA_VERSION, 1);
        badge.put(BADGE.LABEL, BADGE.LABELS.LAST_RUN);
        badge.put(BADGE.MESSAGE, formatTimestamp(timestamp));
        badge.put(BADGE.COLOR, BADGE.COLORS.BLUE);

        Path badgeFile = Path.of(outputDir, FILES.LAST_RUN_BADGE_JSON);
        Files.writeString(badgeFile, GSON.toJson(badge));
        LOGGER.info(INFO.BADGE_GENERATED.format("last run", badgeFile));
    }


    private String formatPerformanceMessage(BenchmarkMetrics metrics) {
        // Format: "Score Grade (throughput, latency)"
        // Example: "91 A (5.8K ops/s, 1.17ms)"
        String scoreFormatted = "%d %s".formatted(
                Math.round(metrics.performanceScore()),
                metrics.performanceGrade()
        );
        return "%s (%s, %s)".formatted(
                scoreFormatted,
                MetricConversionUtil.formatThroughput(metrics.throughput()),
                MetricConversionUtil.formatLatency(metrics.latency())
        );
    }


    private String getColorForScore(double score) {
        // Color based on the 0-100 performance score
        if (score >= 90) return BADGE.COLORS.BRIGHT_GREEN;  // Grade A
        if (score >= 75) return BADGE.COLORS.GREEN;         // Grade B
        if (score >= 60) return BADGE.COLORS.YELLOW;        // Grade C
        if (score >= 40) return BADGE.COLORS.ORANGE;        // Grade D
        return BADGE.COLORS.RED;                             // Grade F
    }

    private TrendAnalysis analyzeTrend(JsonArray currentResults, PerformanceHistory history) {
        // For now, return stable trend as we don't have historical data
        return new TrendAnalysis(TrendDirection.STABLE, 0.0);
    }

    private String formatTrendMessage(TrendAnalysis trend) {
        return switch (trend.direction()) {
            case IMPROVING -> String.format(Locale.US, BADGE.TRENDS.IMPROVING_FORMAT, trend.percentChange());
            case DEGRADING -> String.format(Locale.US, BADGE.TRENDS.DEGRADING_FORMAT, trend.percentChange());
            case STABLE -> BADGE.TRENDS.STABLE;
        };
    }

    private String getTrendColor(TrendAnalysis trend) {
        return switch (trend.direction()) {
            case IMPROVING -> BADGE.COLORS.GREEN;
            case DEGRADING -> BADGE.COLORS.RED;
            case STABLE -> BADGE.COLORS.BLUE;
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