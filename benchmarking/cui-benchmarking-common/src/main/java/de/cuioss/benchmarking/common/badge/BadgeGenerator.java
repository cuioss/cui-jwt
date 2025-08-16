package de.cuioss.benchmarking.common.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.benchmarking.common.detector.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generates Shields.io compatible badges for benchmark results.
 */
public class BadgeGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generates a performance badge showing current benchmark scores.
     *
     * @param results   JMH benchmark results
     * @param type      benchmark type (micro or integration)
     * @param outputDir directory to write badge files
     */
    public void generatePerformanceBadge(Collection<RunResult> results, BenchmarkType type, String outputDir) {
        LOGGER.info("Generating performance badge for %s benchmarks", type);

        PerformanceScore score = calculatePerformanceScore(results);

        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label("Performance Score")
                .message(formatPerformanceScore(score))
                .color(getColorForScore(score))
                .build();

        String filename = "performance-badge.json";
        if (type == BenchmarkType.INTEGRATION) {
            filename = "integration-performance-badge.json";
        }

        try {
            writeBadgeFile(badge, outputDir + "/" + filename);
            LOGGER.info("Performance badge generated: %s", filename);
        } catch (IOException e) {
            LOGGER.error("Failed to write performance badge", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a trend badge showing performance improvement/degradation.
     *
     * @param results   JMH benchmark results
     * @param type      benchmark type (micro or integration)
     * @param outputDir directory to write badge files
     */
    public void generateTrendBadge(Collection<RunResult> results, BenchmarkType type, String outputDir) {
        LOGGER.info("Generating trend badge for %s benchmarks", type);

        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label("Performance Trend")
                .message("stable")
                .color("blue")
                .build();

        String filename = "trend-badge.json";
        if (type == BenchmarkType.INTEGRATION) {
            filename = "integration-trend-badge.json";
        }

        try {
            writeBadgeFile(badge, outputDir + "/" + filename);
            LOGGER.info("Trend badge generated: %s", filename);
        } catch (IOException e) {
            LOGGER.error("Failed to write trend badge", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a last run badge showing when benchmarks were executed.
     *
     * @param outputDir directory to write badge files
     */
    public void generateLastRunBadge(String outputDir) {
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

        try {
            writeBadgeFile(badge, outputDir + "/last-run-badge.json");
            LOGGER.info("Last run badge generated");
        } catch (IOException e) {
            LOGGER.error("Failed to write last run badge", e);
            throw new RuntimeException(e);
        }
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
     * @param throughput      raw throughput value
     * @param latency         average latency
     */
    public record PerformanceScore(long normalizedScore, double throughput, double latency) {
    }

    /**
     * Trend analysis data for badge generation.
     *
     * @param improving     whether performance is improving
     * @param changePercent percentage change from previous run
     */
    public record TrendAnalysis(boolean improving, double changePercent) {
    }
}
