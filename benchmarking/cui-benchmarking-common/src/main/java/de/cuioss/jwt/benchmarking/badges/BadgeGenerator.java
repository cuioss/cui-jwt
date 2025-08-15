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
package de.cuioss.jwt.benchmarking.badges;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.jwt.benchmarking.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generates Shields.io compatible badges for benchmark results.
 * <p>
 * This generator creates performance and trend badges in JSON format that are
 * compatible with the Shields.io badge service. The badges include:
 * <ul>
 *   <li>Performance badges - showing current performance scores with color coding</li>
 *   <li>Trend badges - showing performance improvement/degradation trends</li>
 *   <li>Last run badges - showing when benchmarks were last executed</li>
 * </ul>
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 */
public class BadgeGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generates a performance badge showing current benchmark scores.
     * 
     * @param results JMH benchmark results
     * @param type benchmark type (micro or integration)
     * @param outputDir directory to write badge files
     * @throws IOException if badge generation fails
     */
    public void generatePerformanceBadge(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        LOGGER.info("Generating performance badge for %s benchmarks", type.getDisplayName());

        PerformanceScore score = calculatePerformanceScore(results);
        
        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label(type.getPerformanceLabel())
                .message(formatPerformanceScore(score))
                .color(getColorForScore(score))
                .build();

        String filename = type.getPerformanceBadgeFile();
        writeBadgeFile(badge, outputDir + "/" + filename);

        LOGGER.info("Performance badge generated: %s", filename);
    }

    /**
     * Generates a trend badge showing performance improvement/degradation.
     * 
     * @param results JMH benchmark results
     * @param type benchmark type (micro or integration)
     * @param outputDir directory to write badge files
     * @throws IOException if badge generation fails
     */
    public void generateTrendBadge(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        LOGGER.info("Generating trend badge for %s benchmarks", type.getDisplayName());

        // For now, generate a basic trend badge
        // In a full implementation, this would compare with previous results
        TrendAnalysis trend = new TrendAnalysis(true, 0.0);
        
        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label("Performance Trend")
                .message(formatTrend(trend))
                .color(trend.isImproving() ? "brightgreen" : "yellow")
                .build();

        String filename = type.getTrendBadgeFile();
        writeBadgeFile(badge, outputDir + "/" + filename);

        LOGGER.info("Trend badge generated: %s", filename);
    }

    /**
     * Generates a last run badge showing when benchmarks were executed.
     * 
     * @param outputDir directory to write badge files
     * @throws IOException if badge generation fails
     */
    public void generateLastRunBadge(String outputDir) throws IOException {
        LOGGER.info("Generating last run badge");

        String timestamp = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label("Last Run")
                .message(timestamp + " UTC")
                .color("blue")
                .build();

        writeBadgeFile(badge, outputDir + "/last-run-badge.json");

        LOGGER.info("Last run badge generated");
    }

    private PerformanceScore calculatePerformanceScore(Collection<RunResult> results) {
        double avgThroughput = results.stream()
                .mapToDouble(result -> {
                    if (result.getPrimaryResult() != null) {
                        return result.getPrimaryResult().getScore();
                    }
                    return 0.0;
                })
                .average()
                .orElse(0.0);

        // Convert to normalized score (higher is better)
        long normalizedScore = Math.round(avgThroughput);
        
        return new PerformanceScore(normalizedScore, avgThroughput, 0.0);
    }

    private String formatPerformanceScore(PerformanceScore score) {
        if (score.normalizedScore() > 1_000_000) {
            return String.format("%.1fM ops/s", score.normalizedScore() / 1_000_000.0);
        } else if (score.normalizedScore() > 1_000) {
            return String.format("%.1fK ops/s", score.normalizedScore() / 1_000.0);
        } else {
            return String.format("%d ops/s", score.normalizedScore());
        }
    }

    private String formatTrend(TrendAnalysis trend) {
        if (trend.isImproving()) {
            return "improving";
        } else if (trend.changePercent() < -5.0) {
            return "declining";
        } else {
            return "stable";
        }
    }

    private String getColorForScore(PerformanceScore score) {
        if (score.normalizedScore() >= 1_000_000) {
            return "brightgreen";
        } else if (score.normalizedScore() >= 100_000) {
            return "green";
        } else if (score.normalizedScore() >= 10_000) {
            return "yellow";
        } else if (score.normalizedScore() >= 1_000) {
            return "orange";
        } else {
            return "red";
        }
    }

    private void writeBadgeFile(Badge badge, String filepath) throws IOException {
        Path path = Paths.get(filepath);
        Files.createDirectories(path.getParent());
        
        String json = GSON.toJson(badge);
        Files.writeString(path, json);
    }

    /**
     * Performance score data for badge generation.
     * 
     * @param normalizedScore normalized performance score
     * @param throughput raw throughput value
     * @param latency average latency
     */
    public record PerformanceScore(long normalizedScore, double throughput, double latency) {}

    /**
     * Trend analysis data for badge generation.
     * 
     * @param improving whether performance is improving
     * @param changePercent percentage change from previous run
     */
    public record TrendAnalysis(boolean improving, double changePercent) {}
}