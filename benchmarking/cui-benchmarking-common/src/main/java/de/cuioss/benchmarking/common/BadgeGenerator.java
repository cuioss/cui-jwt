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
package de.cuioss.benchmarking.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.tools.logging.CuiLogger;
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

/**
 * Generates shields.io compatible badges for benchmark performance metrics.
 * <p>
 * This generator creates JSON badge files that can be used directly with shields.io
 * endpoint badges or converted to SVG badges for display in documentation.
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Generates a performance badge showing current benchmark performance.
     *
     * @param results the benchmark results
     * @param type the benchmark type
     * @param outputDir the output directory for the badge file
     * @throws IOException if writing the badge file fails
     */
    public void generatePerformanceBadge(Collection<RunResult> results, BenchmarkType type, String outputDir)
            throws IOException {
        PerformanceScore score = calculatePerformanceScore(results);

        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put("schemaVersion", 1);
        badge.put("label", type.getBadgeLabel());
        badge.put("message", formatPerformanceMessage(score));
        badge.put("color", getColorForScore(score));

        String filename = type.getPerformanceBadgeFileName();
        Path badgeFile = Path.of(outputDir, filename);

        Files.writeString(badgeFile, GSON.toJson(badge));
        LOGGER.info("Generated performance badge: {}", badgeFile);
    }

    /**
     * Generates a trend badge showing performance trends over time.
     *
     * @param results the current benchmark results
     * @param type the benchmark type
     * @param outputDir the output directory for the badge file
     * @throws IOException if writing the badge file fails
     */
    public void generateTrendBadge(Collection<RunResult> results, BenchmarkType type, String outputDir)
            throws IOException {
        // Load previous results for trend analysis
        PerformanceHistory history = loadPerformanceHistory(outputDir + "/../history");
        TrendAnalysis trend = analyzeTrend(results, history);

        Map<String, Object> badge = new LinkedHashMap<>();
        badge.put("schemaVersion", 1);
        badge.put("label", "Performance Trend");
        badge.put("message", formatTrendMessage(trend));
        badge.put("color", getTrendColor(trend));

        String filename = type.getTrendBadgeFileName();
        Path badgeFile = Path.of(outputDir, filename);

        Files.writeString(badgeFile, GSON.toJson(badge));
        LOGGER.info("Generated trend badge: {}", badgeFile);
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
        badge.put("schemaVersion", 1);
        badge.put("label", "Last Run");
        badge.put("message", formatTimestamp(timestamp));
        badge.put("color", "blue");

        Path badgeFile = Path.of(outputDir, "last-run-badge.json");
        Files.writeString(badgeFile, GSON.toJson(badge));
        LOGGER.info("Generated last run badge: {}", badgeFile);
    }

    /**
     * Calculates a composite performance score from benchmark results.
     */
    private PerformanceScore calculatePerformanceScore(Collection<RunResult> results) {
        if (results.isEmpty()) {
            return new PerformanceScore(0, 0, 0);
        }

        // Calculate average throughput and latency across all benchmarks
        double totalThroughput = 0;
        double totalLatency = 0;
        int count = 0;

        for (RunResult result : results) {
            if (result.getPrimaryResult() != null && result.getPrimaryResult().getStatistics() != null) {
                double score = result.getPrimaryResult().getScore();
                String unit = result.getPrimaryResult().getScoreUnit();

                // Convert to common units for scoring
                if (unit.contains("ops/s") || unit.contains("ops/sec")) {
                    totalThroughput += score;
                } else if (unit.contains("s/op") || unit.contains("ms/op") || unit.contains("us/op")) {
                    // Convert to operations per second for consistency
                    if (unit.contains("s/op")) {
                        totalThroughput += 1.0 / score;
                    } else if (unit.contains("ms/op")) {
                        totalThroughput += 1000.0 / score;
                    } else if (unit.contains("us/op")) {
                        totalThroughput += 1_000_000.0 / score;
                    }
                    totalLatency += score;
                }
                count++;
            }
        }

        if (count == 0) {
            return new PerformanceScore(0, 0, 0);
        }

        double avgThroughput = totalThroughput / count;
        double avgLatency = totalLatency / count;

        // Calculate composite score (higher is better)
        long compositeScore = Math.round(avgThroughput);

        return new PerformanceScore(compositeScore, avgThroughput, avgLatency);
    }

    /**
     * Formats the performance score for display in badges.
     */
    private String formatPerformanceMessage(PerformanceScore score) {
        if (score.getCompositeScore() == 0) {
            return "No Data";
        }

        // Format with appropriate units
        if (score.getCompositeScore() >= 1_000_000) {
            return "%.1fM ops/s".formatted(score.getCompositeScore() / 1_000_000.0);
        } else if (score.getCompositeScore() >= 1_000) {
            return "%.1fK ops/s".formatted(score.getCompositeScore() / 1_000.0);
        } else {
            return "%d ops/s".formatted(score.getCompositeScore());
        }
    }

    /**
     * Gets the badge color based on performance score.
     */
    private String getColorForScore(PerformanceScore score) {
        long composite = score.getCompositeScore();

        if (composite >= 1_000_000) {
            return "brightgreen";
        } else if (composite >= 100_000) {
            return "green";
        } else if (composite >= 10_000) {
            return "yellow";
        } else if (composite >= 1_000) {
            return "orange";
        } else {
            return "red";
        }
    }

    /**
     * Loads performance history for trend analysis.
     */
    private PerformanceHistory loadPerformanceHistory(String historyDir) {
        // Placeholder implementation - in a real scenario, this would load
        // historical performance data from previous benchmark runs
        return new PerformanceHistory();
    }

    /**
     * Analyzes performance trends compared to historical data.
     */
    private TrendAnalysis analyzeTrend(Collection<RunResult> results, PerformanceHistory history) {
        // Placeholder implementation - in a real scenario, this would compare
        // current results with historical data to determine trends
        return new TrendAnalysis(TrendDirection.STABLE, 0.0);
    }

    /**
     * Formats trend information for badge display.
     */
    private String formatTrendMessage(TrendAnalysis trend) {
        switch (trend.getDirection()) {
            case IMPROVING:
                return "↗ +%.1f%%".formatted(Math.abs(trend.getPercentageChange()));
            case DECLINING:
                return "↘ -%.1f%%".formatted(Math.abs(trend.getPercentageChange()));
            case STABLE:
            default:
                return "→ Stable";
        }
    }

    /**
     * Gets the badge color for trend analysis.
     */
    private String getTrendColor(TrendAnalysis trend) {
        switch (trend.getDirection()) {
            case IMPROVING:
                return "brightgreen";
            case DECLINING:
                return "red";
            case STABLE:
            default:
                return "blue";
        }
    }

    /**
     * Formats timestamp for display in badges.
     */
    private String formatTimestamp(String isoTimestamp) {
        // Extract date part for badge display
        if (isoTimestamp.length() >= 10) {
            return isoTimestamp.substring(0, 10);
        }
        return isoTimestamp;
    }

    // Helper classes for data structures
    private static class PerformanceScore {
        private final long compositeScore;
        private final double throughput;
        private final double latency;

        PerformanceScore(long compositeScore, double throughput, double latency) {
            this.compositeScore = compositeScore;
            this.throughput = throughput;
            this.latency = latency;
        }

        long getCompositeScore() {
            return compositeScore;
        }

        double getThroughput() {
            return throughput;
        }

        double getLatency() {
            return latency;
        }
    }

    private static class PerformanceHistory {
        // Placeholder for historical performance data
    }

    private static class TrendAnalysis {
        private final TrendDirection direction;
        private final double percentageChange;

        TrendAnalysis(TrendDirection direction, double percentageChange) {
            this.direction = direction;
            this.percentageChange = percentageChange;
        }

        TrendDirection getDirection() {
            return direction;
        }

        double getPercentageChange() {
            return percentageChange;
        }
    }

    private enum TrendDirection {
        IMPROVING, DECLINING, STABLE
    }
}