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
package de.cuioss.jwt.benchmarking.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generates shields.io compatible badges for benchmark results.
 * <p>
 * Creates performance badges with scoring, trend badges with analysis,
 * and last-run timestamp badges for CI integration.
 * </p>
 * 
 * @since 1.0
 */
public final class BadgeGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generates a performance badge with score and metrics.
     *
     * @param results       the benchmark results
     * @param benchmarkType the type of benchmark
     * @param outputDir     the output directory for the badge
     * @throws IOException if file writing fails
     */
    public void generatePerformanceBadge(Collection<RunResult> results, BenchmarkType benchmarkType, String outputDir) throws IOException {
        var score = calculatePerformanceScore(results);
        var throughputOpsPerSec = calculateAverageThroughput(results);
        
        var badge = new Badge(
            1,
            benchmarkType.getLabelText(),
            formatPerformanceMessage(score, throughputOpsPerSec),
            getColorForScore(score)
        );

        var filePath = outputDir + "/" + benchmarkType.getPerformanceBadgeFilename();
        writeBadgeFile(badge, filePath);
        
        LOGGER.info("Generated %s performance badge: %s (score: %d)", benchmarkType, filePath, score);
    }

    /**
     * Generates a trend badge showing performance trends over time.
     *
     * @param results       the benchmark results
     * @param benchmarkType the type of benchmark
     * @param outputDir     the output directory for the badge
     * @throws IOException if file writing fails
     */
    public void generateTrendBadge(Collection<RunResult> results, BenchmarkType benchmarkType, String outputDir) throws IOException {
        // Load previous results for trend analysis
        var trend = analyzeTrend(results, outputDir);
        
        var badge = new Badge(
            1,
            "Performance Trend",
            formatTrendMessage(trend),
            getTrendColor(trend)
        );

        var filePath = outputDir + "/" + benchmarkType.getTrendBadgeFilename();
        writeBadgeFile(badge, filePath);
        
        LOGGER.info("Generated %s trend badge: %s (trend: %s)", benchmarkType, filePath, trend);
    }

    /**
     * Generates a last-run timestamp badge.
     *
     * @param outputDir the output directory for the badge
     * @throws IOException if file writing fails
     */
    public void generateLastRunBadge(String outputDir) throws IOException {
        var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        
        var badge = new Badge(
            1,
            "Last Run",
            timestamp,
            "blue"
        );

        var filePath = outputDir + "/last-run-badge.json";
        writeBadgeFile(badge, filePath);
        
        LOGGER.info("Generated last run badge: %s", filePath);
    }

    private long calculatePerformanceScore(Collection<RunResult> results) {
        if (results.isEmpty()) {
            return 0;
        }

        // Calculate composite score based on throughput and latency
        var avgThroughput = calculateAverageThroughput(results);
        var avgLatency = calculateAverageLatency(results);
        
        // Score formula: throughput (ops/μs) * 1000 / latency (μs)
        return Math.round(avgThroughput * 1000.0 / Math.max(avgLatency, 0.001));
    }

    private double calculateAverageThroughput(Collection<RunResult> results) {
        return results.stream()
            .mapToDouble(r -> r.getPrimaryResult().getScore())
            .average()
            .orElse(0.0);
    }

    private double calculateAverageLatency(Collection<RunResult> results) {
        // For throughput benchmarks, latency = 1/throughput
        var avgThroughput = calculateAverageThroughput(results);
        return avgThroughput > 0 ? 1.0 / avgThroughput : Double.MAX_VALUE;
    }

    private String formatPerformanceMessage(long score, double throughputOpsPerSec) {
        return String.format("%d (%,.0f ops/s)", score, throughputOpsPerSec);
    }

    private String getColorForScore(long score) {
        if (score >= 1000000) return "brightgreen";
        if (score >= 500000) return "green"; 
        if (score >= 100000) return "yellow";
        if (score >= 50000) return "orange";
        return "red";
    }

    private TrendAnalysis analyzeTrend(Collection<RunResult> results, String outputDir) {
        // Simplified trend analysis - in real implementation would compare with historical data
        var currentScore = calculatePerformanceScore(results);
        
        // For now, return stable trend - would implement historical comparison
        return new TrendAnalysis(TrendDirection.STABLE, 0.0, currentScore);
    }

    private String formatTrendMessage(TrendAnalysis trend) {
        return switch (trend.direction()) {
            case IMPROVING -> String.format("↗ +%.1f%%", trend.changePercent());
            case DECLINING -> String.format("↘ %.1f%%", trend.changePercent());
            case STABLE -> "→ Stable";
        };
    }

    private String getTrendColor(TrendAnalysis trend) {
        return switch (trend.direction()) {
            case IMPROVING -> "brightgreen";
            case STABLE -> "yellow";
            case DECLINING -> "red";
        };
    }

    private void writeBadgeFile(Badge badge, String filePath) throws IOException {
        Files.createDirectories(Paths.get(filePath).getParent());
        var json = GSON.toJson(badge);
        Files.writeString(Paths.get(filePath), json);
    }

    /**
     * Represents a shields.io badge.
     */
    private record Badge(int schemaVersion, String label, String message, String color) {}

    /**
     * Represents trend analysis results.
     */
    private record TrendAnalysis(TrendDirection direction, double changePercent, long currentScore) {}

    /**
     * Direction of performance trend.
     */
    private enum TrendDirection {
        IMPROVING, STABLE, DECLINING
    }
}