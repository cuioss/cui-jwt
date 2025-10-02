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
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.Arrows;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.Colors.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.FileNames;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.Labels;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.Messages;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Badge.TrendDirection;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Grades;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates shields.io compatible JSON badge files for benchmark metrics.
 * <p>
 * This generator creates three types of badges:
 * <ul>
 *   <li>Performance badge - Shows the current performance grade</li>
 *   <li>Trend badge - Shows the trend direction with percentage change</li>
 *   <li>Last run badge - Shows when the benchmarks were last executed</li>
 * </ul>
 * <p>
 * All badges follow the shields.io JSON endpoint schema for easy integration
 * with GitHub README files and documentation.
 */
public class BadgeGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Generates a performance badge JSON based on the benchmark metrics.
     * Format: "Grade A (45k ops/s, 0.15ms)"
     *
     * @param metrics the current benchmark metrics
     * @return JSON string for the performance badge
     */
    public String generatePerformanceBadge(BenchmarkMetrics metrics) {
        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put(SCHEMA_VERSION, 1);
        badge.put(LABEL, Labels.PERFORMANCE);

        String formattedThroughput = formatThroughput(metrics.throughput());
        String formattedLatency = formatLatency(metrics.latency());
        String message = String.format(Locale.US, "Grade %s (%s ops/s, %sms)",
                metrics.performanceGrade(), formattedThroughput, formattedLatency);

        badge.put(MESSAGE, message);
        badge.put(COLOR, getGradeColor(metrics.performanceGrade()));

        return gson.toJson(badge);
    }

    /**
     * Generates a trend badge JSON based on the trend metrics.
     *
     * @param trendMetrics the calculated trend metrics
     * @return JSON string for the trend badge
     */
    public String generateTrendBadge(TrendDataProcessor.TrendMetrics trendMetrics) {
        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put(SCHEMA_VERSION, 1);
        badge.put(LABEL, Labels.TREND);

        String arrow = getTrendArrow(trendMetrics.direction());
        String percentage = String.format(Locale.US, "%.1f%%", Math.abs(trendMetrics.changePercentage()));
        badge.put(MESSAGE, arrow + " " + percentage);
        badge.put(COLOR, getTrendColor(trendMetrics.direction()));

        return gson.toJson(badge);
    }

    /**
     * Generates a last run timestamp badge.
     *
     * @param timestamp the benchmark run timestamp
     * @return JSON string for the last run badge
     */
    public String generateLastRunBadge(Instant timestamp) {
        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put(SCHEMA_VERSION, 1);
        badge.put(LABEL, Labels.LAST_RUN);
        badge.put(MESSAGE, DATE_FORMAT.format(timestamp));
        badge.put(COLOR, BLUE);

        return gson.toJson(badge);
    }

    /**
     * Generates a default trend badge when no historical data is available.
     *
     * @return JSON string for the default trend badge
     */
    public String generateDefaultTrendBadge() {
        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put(SCHEMA_VERSION, 1);
        badge.put(LABEL, Labels.TREND);
        badge.put(MESSAGE, Messages.NO_HISTORY);
        badge.put(COLOR, LIGHT_GRAY);

        return gson.toJson(badge);
    }

    /**
     * Writes all badge files to the specified directory using default MICRO benchmark type.
     * This method is provided for backward compatibility with tests.
     *
     * @param metrics the current benchmark metrics
     * @param trendMetrics the trend metrics (can be null)
     * @param outputDir the output directory path
     * @throws IOException if writing badge files fails
     */
    public void writeBadgeFiles(BenchmarkMetrics metrics,
            TrendDataProcessor.TrendMetrics trendMetrics,
            String outputDir) throws IOException {
        writeBadgeFiles(metrics, trendMetrics, BenchmarkType.MICRO, outputDir);
    }

    /**
     * Writes all badge files to the specified directory.
     *
     * @param metrics the current benchmark metrics
     * @param trendMetrics the trend metrics (can be null)
     * @param type the benchmark type (MICRO or INTEGRATION)
     * @param outputDir the output directory path
     * @throws IOException if writing badge files fails
     */
    public void writeBadgeFiles(BenchmarkMetrics metrics,
            TrendDataProcessor.TrendMetrics trendMetrics,
            BenchmarkType type,
            String outputDir) throws IOException {
        Path badgesDir = Path.of(outputDir, "badges");
        Files.createDirectories(badgesDir);

        // Write performance badge with appropriate file name based on benchmark type
        String perfBadge = generatePerformanceBadge(metrics);
        Path perfBadgePath = badgesDir.resolve(type.getPerformanceBadgeFileName());
        Files.writeString(perfBadgePath, perfBadge);
        LOGGER.info(INFO.GENERATING_REPORTS.format("Performance badge written to " + perfBadgePath));

        // Write trend badge with appropriate file name based on benchmark type
        String trendBadge = trendMetrics != null
                ? generateTrendBadge(trendMetrics)
                : generateDefaultTrendBadge();
        Path trendBadgePath = badgesDir.resolve(type.getTrendBadgeFileName());
        Files.writeString(trendBadgePath, trendBadge);
        LOGGER.info(INFO.GENERATING_REPORTS.format("Trend badge written to " + trendBadgePath));

        // Write last run badge (same for both types)
        String lastRunBadge = generateLastRunBadge(Instant.now());
        Path lastRunBadgePath = badgesDir.resolve(FileNames.LAST_RUN_BADGE_JSON);
        Files.writeString(lastRunBadgePath, lastRunBadge);
        LOGGER.info(INFO.GENERATING_REPORTS.format("Last run badge written to " + lastRunBadgePath));
    }

    /**
     * Determines the color for a performance grade.
     */
    private String getGradeColor(String grade) {
        return switch (grade) {
            case Grades.A_PLUS -> BRIGHT_GREEN;
            case Grades.A -> GREEN;
            case Grades.B -> YELLOW_GREEN;
            case Grades.C -> YELLOW;
            case Grades.D -> ORANGE;
            case Grades.F -> RED;
            default -> LIGHT_GRAY;
        };
    }

    /**
     * Determines the color for a trend direction.
     */
    private String getTrendColor(String direction) {
        return switch (direction) {
            case TrendDirection.UP -> GREEN;
            case TrendDirection.DOWN -> RED;
            case TrendDirection.STABLE -> BLUE;
            default -> LIGHT_GRAY;
        };
    }

    /**
     * Gets the arrow symbol for a trend direction.
     */
    private String getTrendArrow(String direction) {
        return switch (direction) {
            case TrendDirection.UP -> Arrows.UP;
            case TrendDirection.DOWN -> Arrows.DOWN;
            case TrendDirection.STABLE -> Arrows.RIGHT;
            default -> Arrows.BULLET;
        };
    }

    /**
     * Formats throughput value with appropriate unit (k for thousands).
     * Examples: 500 -> "500", 1500 -> "1.5k", 45000 -> "45k"
     */
    private String formatThroughput(double throughput) {
        if (throughput >= 1000) {
            double kThroughput = throughput / 1000.0;
            if (kThroughput >= 10) {
                // For values >= 10k, no decimal places
                return String.format(Locale.US, "%.0fk", kThroughput);
            } else {
                // For values < 10k, show one decimal if not .0
                String formatted = String.format(Locale.US, "%.1fk", kThroughput);
                return formatted.endsWith(".0k") ?
                        formatted.substring(0, formatted.length() - 3) + "k" : formatted;
            }
        } else {
            // For values < 1000, show as integer
            return String.format(Locale.US, "%.0f", throughput);
        }
    }

    /**
     * Formats latency value with appropriate precision.
     * For values < 1ms: 2 decimals, for values >= 1ms: 2 decimals with trailing zeros
     */
    private String formatLatency(double latency) {
        return String.format(Locale.US, "%.2f", latency);
    }
}