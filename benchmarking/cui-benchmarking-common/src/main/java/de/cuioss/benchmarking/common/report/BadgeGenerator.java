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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
     * 
     * @param metrics the current benchmark metrics
     * @return JSON string for the performance badge
     */
    public String generatePerformanceBadge(BenchmarkMetrics metrics) {
        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put("schemaVersion", 1);
        badge.put("label", "Performance");
        badge.put("message", "Grade " + metrics.performanceGrade());
        badge.put("color", getGradeColor(metrics.performanceGrade()));

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
        badge.put("schemaVersion", 1);
        badge.put("label", "Trend");

        String arrow = getTrendArrow(trendMetrics.getDirection());
        String percentage = String.format(Locale.US, "%.1f%%", Math.abs(trendMetrics.getChangePercentage()));
        badge.put("message", arrow + " " + percentage);
        badge.put("color", getTrendColor(trendMetrics.getDirection()));

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
        badge.put("schemaVersion", 1);
        badge.put("label", "Last Run");
        badge.put("message", DATE_FORMAT.format(timestamp));
        badge.put("color", "blue");

        return gson.toJson(badge);
    }

    /**
     * Generates a default trend badge when no historical data is available.
     * 
     * @return JSON string for the default trend badge
     */
    public String generateDefaultTrendBadge() {
        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put("schemaVersion", 1);
        badge.put("label", "Trend");
        badge.put("message", "No history");
        badge.put("color", "lightgray");

        return gson.toJson(badge);
    }

    /**
     * Writes all badge files to the specified directory.
     * 
     * @param metrics the current benchmark metrics
     * @param trendMetrics the trend metrics (can be null)
     * @param outputDir the output directory path
     * @throws IOException if writing badge files fails
     */
    public void writeBadgeFiles(BenchmarkMetrics metrics,
            TrendDataProcessor.TrendMetrics trendMetrics,
            String outputDir) throws IOException {
        Path badgesDir = Path.of(outputDir, "badges");
        Files.createDirectories(badgesDir);

        // Write performance badge
        String perfBadge = generatePerformanceBadge(metrics);
        Path perfBadgePath = badgesDir.resolve("performance-badge.json");
        Files.writeString(perfBadgePath, perfBadge);
        LOGGER.info(INFO.GENERATING_REPORTS.format("Performance badge written to " + perfBadgePath));

        // Write trend badge
        String trendBadge = trendMetrics != null
                ? generateTrendBadge(trendMetrics)
                : generateDefaultTrendBadge();
        Path trendBadgePath = badgesDir.resolve("trend-badge.json");
        Files.writeString(trendBadgePath, trendBadge);
        LOGGER.info(INFO.GENERATING_REPORTS.format("Trend badge written to " + trendBadgePath));

        // Write last run badge
        String lastRunBadge = generateLastRunBadge(Instant.now());
        Path lastRunBadgePath = badgesDir.resolve("last-run-badge.json");
        Files.writeString(lastRunBadgePath, lastRunBadge);
        LOGGER.info(INFO.GENERATING_REPORTS.format("Last run badge written to " + lastRunBadgePath));
    }

    /**
     * Determines the color for a performance grade.
     */
    private String getGradeColor(String grade) {
        return switch (grade) {
            case "A+" -> "brightgreen";
            case "A" -> "green";
            case "B" -> "yellowgreen";
            case "C" -> "yellow";
            case "D" -> "orange";
            case "F" -> "red";
            default -> "lightgray";
        };
    }

    /**
     * Determines the color for a trend direction.
     */
    private String getTrendColor(String direction) {
        return switch (direction) {
            case "up" -> "green";
            case "down" -> "red";
            case "stable" -> "blue";
            default -> "lightgray";
        };
    }

    /**
     * Gets the arrow symbol for a trend direction.
     */
    private String getTrendArrow(String direction) {
        return switch (direction) {
            case "up" -> "↑";
            case "down" -> "↓";
            case "stable" -> "→";
            default -> "•";
        };
    }
}