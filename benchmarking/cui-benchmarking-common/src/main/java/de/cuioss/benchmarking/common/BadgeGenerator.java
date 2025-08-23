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

import static de.cuioss.benchmarking.common.BenchmarkingLogMessages.INFO;

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

    private static final CuiLogger LOGGER =
            new CuiLogger(BadgeGenerator.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();
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
        LOGGER.info(INFO.BADGE_GENERATED.format("performance", badgeFile));
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
        badge.put("schemaVersion", 1);
        badge.put("label", "Last Run");
        badge.put("message", formatTimestamp(timestamp));
        badge.put("color", "blue");

        Path badgeFile = Path.of(outputDir, "last-run-badge.json");
        Files.writeString(badgeFile, GSON.toJson(badge));
        LOGGER.info(INFO.BADGE_GENERATED.format("last run", badgeFile));
    }

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

    private String formatPerformanceMessage(PerformanceScore score) {
        if (score.compositeScore() == 0) {
            return "No Data";
        }

        return switch ((int) Math.log10(Math.max(1, score.compositeScore()))) {
            case 6, 7, 8, 9 -> "%.1fM ops/s".formatted(score.compositeScore() / 1_000_000.0);
            case 3, 4, 5 -> "%.1fK ops/s".formatted(score.compositeScore() / 1_000.0);
            default -> "%d ops/s".formatted(score.compositeScore());
        };
    }

    private String getColorForScore(PerformanceScore score) {
        return switch ((int) Math.log10(Math.max(1, score.compositeScore()))) {
            case 6, 7, 8, 9 -> "brightgreen";
            case 5 -> "green";
            case 4 -> "yellow";
            case 3 -> "orange";
            default -> "red";
        };
    }

    private PerformanceHistory loadPerformanceHistory(String historyDir) {
        Path historyPath = Path.of(historyDir);
        if (Files.exists(historyPath)) {
            try {
                Path latestHistory = Files.list(historyPath)
                        .filter(path -> path.toString().endsWith(".json"))
                        .sorted((a, b) -> b.getFileName().compareTo(a.getFileName()))
                        .findFirst()
                        .orElse(null);

                if (latestHistory != null) {
                    String content = Files.readString(latestHistory);
                    var data = GSON.fromJson(content, Map.class);
                    return new PerformanceHistory(
                            ((Number) data.getOrDefault("averageThroughput", 0.0)).doubleValue(),
                            ((Number) data.getOrDefault("totalScore", 0.0)).doubleValue()
                    );
                }
            } catch (IOException e) {
                LOGGER.debug("Failed to load performance history", e);
            }
        }
        return new PerformanceHistory(0.0, 0.0);
    }

    private TrendAnalysis analyzeTrend(Collection<RunResult> results, PerformanceHistory history) {
        if (history.averageThroughput() == 0) {
            return new TrendAnalysis(TrendDirection.STABLE, 0.0);
        }

        double currentThroughput = results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .filter(r -> r.getPrimaryResult().getScoreUnit().contains("ops"))
                .mapToDouble(r -> r.getPrimaryResult().getScore())
                .average()
                .orElse(0.0);

        double percentageChange = ((currentThroughput - history.averageThroughput()) / history.averageThroughput()) * 100;

        TrendDirection direction = switch (Double.compare(Math.abs(percentageChange), 5.0)) {
            case -1, 0 -> TrendDirection.STABLE;
            default -> percentageChange > 0 ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
        };

        return new TrendAnalysis(direction, percentageChange);
    }

    private String formatTrendMessage(TrendAnalysis trend) {
        return switch (trend.direction()) {
            case IMPROVING -> "↗ +%.1f%%".formatted(Math.abs(trend.percentageChange()));
            case DECLINING -> "↘ -%.1f%%".formatted(Math.abs(trend.percentageChange()));
            case STABLE -> "→ Stable";
        };
    }

    private String getTrendColor(TrendAnalysis trend) {
        return switch (trend.direction()) {
            case IMPROVING -> "brightgreen";
            case DECLINING -> "red";
            case STABLE -> "blue";
        };
    }

    private String formatTimestamp(String isoTimestamp) {
        return isoTimestamp.length() >= 10 ? isoTimestamp.substring(0, 10) : isoTimestamp;
    }

    private record PerformanceScore(long compositeScore, double throughput, double latency) {
    }

    private record PerformanceHistory(double averageThroughput, double totalScore) {
    }

    private record TrendAnalysis(TrendDirection direction, double percentageChange) {
    }

    private enum TrendDirection {
        IMPROVING, DECLINING, STABLE
    }
}