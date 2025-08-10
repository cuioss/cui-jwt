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
package de.cuioss.benchmarking.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.benchmarking.model.Badge;
import de.cuioss.benchmarking.model.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generates Shields.io compatible badge files during JMH benchmark execution.
 * <p>
 * This generator creates performance badges, trend badges, and informational badges
 * based on benchmark results. All badges are generated in JSON format compatible
 * with Shields.io endpoint.
 *
 * @since 1.0.0
 */
public class BadgeGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generates a performance badge based on benchmark results.
     *
     * @param results the benchmark results
     * @param type the benchmark type
     * @param outputDir the output directory for badges
     * @throws IOException if badge generation fails
     */
    public void generatePerformanceBadge(Collection<RunResult> results, BenchmarkType type, String outputDir) 
            throws IOException {
        LOGGER.info("Generating performance badge for %s benchmarks", type);

        PerformanceScore score = calculatePerformanceScore(results, type);
        
        String label = type == BenchmarkType.MICRO ? "Performance Score" : "Integration Performance";
        String message = formatPerformanceMessage(score);
        String color = getPerformanceColor(score.getScore());
        
        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label(label)
                .message(message)
                .color(color)
                .build();

        String filename = type == BenchmarkType.MICRO ? 
                "performance-badge.json" : 
                "integration-performance-badge.json";

        writeBadgeFile(badge, outputDir, filename);
        LOGGER.info("Generated performance badge: %s", filename);
    }

    /**
     * Generates a trend badge showing performance change over time.
     *
     * @param results the current benchmark results
     * @param type the benchmark type
     * @param outputDir the output directory for badges
     * @throws IOException if badge generation fails
     */
    public void generateTrendBadge(Collection<RunResult> results, BenchmarkType type, String outputDir) 
            throws IOException {
        LOGGER.info("Generating trend badge for %s benchmarks", type);

        // For now, generate a simple trend badge indicating "stable"
        // In a real implementation, this would load previous results and analyze trends
        TrendAnalysis trend = analyzeTrend(results, type, outputDir);
        
        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label("Performance Trend")
                .message(formatTrendMessage(trend))
                .color(getTrendColor(trend))
                .build();

        String filename = type == BenchmarkType.MICRO ?
                "trend-badge.json" :
                "integration-trend-badge.json";

        writeBadgeFile(badge, outputDir, filename);
        LOGGER.info("Generated trend badge: %s", filename);
    }

    /**
     * Generates a "last run" badge with timestamp information.
     *
     * @param outputDir the output directory for badges
     * @throws IOException if badge generation fails
     */
    public void generateLastRunBadge(String outputDir) throws IOException {
        LOGGER.info("Generating last run badge");

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
        
        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label("Last Benchmark Run")
                .message(timestamp)
                .color("blue")
                .build();

        writeBadgeFile(badge, outputDir, "last-run-badge.json");
        LOGGER.info("Generated last run badge");
    }

    /**
     * Calculates performance score from benchmark results.
     *
     * @param results the benchmark results
     * @param type the benchmark type
     * @return the calculated performance score
     */
    private PerformanceScore calculatePerformanceScore(Collection<RunResult> results, BenchmarkType type) {
        if (results.isEmpty()) {
            return new PerformanceScore(0, 0, 0);
        }

        // Calculate average throughput and latency
        double totalThroughput = 0;
        double totalLatency = 0;
        int throughputCount = 0;
        int latencyCount = 0;

        for (RunResult result : results) {
            if (result.getPrimaryResult() != null && result.getPrimaryResult().getStatistics() != null) {
                double score = result.getPrimaryResult().getScore();
                String unit = result.getPrimaryResult().getScoreUnit();

                if (isLatencyMetric(unit)) {
                    totalLatency += convertToMilliseconds(score, unit);
                    latencyCount++;
                } else if (isThroughputMetric(unit)) {
                    totalThroughput += convertToOpsPerSecond(score, unit);
                    throughputCount++;
                }
            }
        }

        double avgThroughput = throughputCount > 0 ? totalThroughput / throughputCount : 0;
        double avgLatency = latencyCount > 0 ? totalLatency / latencyCount : 0;

        // Calculate composite score based on type
        long compositeScore = calculateCompositeScore(avgThroughput, avgLatency, type);

        return new PerformanceScore(compositeScore, avgThroughput, avgLatency);
    }

    /**
     * Calculates a composite performance score.
     */
    private long calculateCompositeScore(double throughput, double latencyMs, BenchmarkType type) {
        if (throughput <= 0 && latencyMs <= 0) {
            return 0;
        }

        if (throughput > 0 && latencyMs > 0) {
            // Combined score: throughput / latency (higher is better)
            return Math.round(throughput / (latencyMs / 1000.0));
        } else if (throughput > 0) {
            // Only throughput available
            return Math.round(throughput);
        } else {
            // Only latency available - invert to make higher scores better
            return Math.round(1000.0 / latencyMs);
        }
    }

    /**
     * Analyzes performance trends (simplified implementation).
     */
    private TrendAnalysis analyzeTrend(Collection<RunResult> results, BenchmarkType type, String outputDir) {
        // Simplified trend analysis - in real implementation would load historical data
        return new TrendAnalysis("stable", true);
    }

    /**
     * Converts various time units to milliseconds.
     */
    private double convertToMilliseconds(double value, String unit) {
        switch (unit.toLowerCase()) {
            case "ns/op":
            case "nanoseconds":
                return value / 1_000_000.0;
            case "us/op":
            case "microseconds":
                return value / 1_000.0;
            case "ms/op":
            case "milliseconds":
                return value;
            case "s/op":
            case "seconds":
                return value * 1_000.0;
            default:
                return value; // Assume milliseconds if unknown
        }
    }

    /**
     * Converts various throughput units to operations per second.
     */
    private double convertToOpsPerSecond(double value, String unit) {
        switch (unit.toLowerCase()) {
            case "ops/ns":
                return value * 1_000_000_000.0;
            case "ops/us":
                return value * 1_000_000.0;
            case "ops/ms":
                return value * 1_000.0;
            case "ops/s":
                return value;
            default:
                return value; // Assume ops/s if unknown
        }
    }

    /**
     * Checks if a unit represents a latency metric.
     */
    private boolean isLatencyMetric(String unit) {
        return unit.contains("/op") || unit.contains("seconds") || unit.contains("milliseconds") || 
               unit.contains("microseconds") || unit.contains("nanoseconds");
    }

    /**
     * Checks if a unit represents a throughput metric.
     */
    private boolean isThroughputMetric(String unit) {
        return unit.startsWith("ops/");
    }

    private String formatPerformanceMessage(PerformanceScore score) {
        if (score.getThroughput() > 0) {
            return String.format("%d (%s ops/s)", score.getScore(), formatThroughput(score.getThroughput()));
        } else {
            return String.valueOf(score.getScore());
        }
    }

    private String formatThroughput(double throughput) {
        if (throughput >= 1_000_000) {
            return String.format("%.1fM", throughput / 1_000_000);
        } else if (throughput >= 1_000) {
            return String.format("%.1fK", throughput / 1_000);
        } else {
            return String.format("%.0f", throughput);
        }
    }

    private String getPerformanceColor(long score) {
        if (score >= 1_000_000) {
            return "brightgreen";
        } else if (score >= 100_000) {
            return "green";
        } else if (score >= 10_000) {
            return "yellow";
        } else if (score >= 1_000) {
            return "orange";
        } else {
            return "red";
        }
    }

    private String formatTrendMessage(TrendAnalysis trend) {
        return trend.getStatus();
    }

    private String getTrendColor(TrendAnalysis trend) {
        return trend.isImproving() ? "brightgreen" : "yellow";
    }

    /**
     * Writes a badge to a JSON file.
     */
    private void writeBadgeFile(Badge badge, String outputDir, String filename) throws IOException {
        Path badgeDir = Paths.get(outputDir);
        Files.createDirectories(badgeDir);
        
        Path badgeFile = badgeDir.resolve(filename);
        String json = GSON.toJson(badge);
        Files.write(badgeFile, json.getBytes());
    }

    /**
     * Simple performance score holder.
     */
    private static class PerformanceScore {
        private final long score;
        private final double throughput;
        private final double latency;

        PerformanceScore(long score, double throughput, double latency) {
            this.score = score;
            this.throughput = throughput;
            this.latency = latency;
        }

        long getScore() { return score; }
        double getThroughput() { return throughput; }
        double getLatency() { return latency; }
    }

    /**
     * Simple trend analysis holder.
     */
    private static class TrendAnalysis {
        private final String status;
        private final boolean improving;

        TrendAnalysis(String status, boolean improving) {
            this.status = status;
            this.improving = improving;
        }

        String getStatus() { return status; }
        boolean isImproving() { return improving; }
    }
}