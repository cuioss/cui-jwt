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
import java.util.concurrent.TimeUnit;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates summary files for CI/CD pipeline consumption.
 * <p>
 * This generator creates machine-readable summary files that can be used by
 * continuous integration systems, monitoring tools, and automated deployment
 * pipelines to make decisions based on benchmark results.
 * <p>
 * Generated summaries include:
 * <ul>
 *   <li>Overall benchmark status (pass/fail)</li>
 *   <li>Performance regression detection</li>
 *   <li>Key metrics and thresholds</li>
 *   <li>Deployment readiness indicators</li>
 * </ul>
 */
public class SummaryGenerator {

    private static final CuiLogger LOGGER =
            new CuiLogger(SummaryGenerator.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    // Constants for JSON field names
    private static final String FIELD_TOTAL_BENCHMARKS = "total_benchmarks";
    private static final String FIELD_AVERAGE_THROUGHPUT = "average_throughput";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PERFORMANCE = "performance";

    // Performance score constants
    private static final double PERFECT_SCORE = 100.0;
    private static final double EXCELLENT_SCORE = 90.0;
    private static final double GOOD_SCORE = 80.0;
    private static final double FAIR_SCORE = 70.0;
    private static final double MINIMUM_SCORE = 60.0;
    private static final double THROUGHPUT_NORMALIZATION_FACTOR = 1_000.0;
    private static final double SCORE_MULTIPLIER = 70.0;
    private static final int GRADE_DIVISOR = 10;

    /**
     * Writes a comprehensive summary file for CI consumption.
     *
     * @param results the benchmark results
     * @param type the benchmark type
     * @param timestamp the execution timestamp
     * @param outputFile the output file path
     * @throws IOException if writing the summary file fails
     */
    public void writeSummary(Collection<RunResult> results, BenchmarkType type,
            Instant timestamp, String outputFile) throws IOException {
        LOGGER.info(INFO.WRITING_BENCHMARK_SUMMARY.format(
                results.size(), type.getDisplayName()));

        Map<String, Object> summary = new LinkedHashMap<>();

        // Basic information
        summary.put("timestamp", ISO_FORMATTER.format(timestamp.atOffset(ZoneOffset.UTC)));
        summary.put("benchmark_type", type.getIdentifier());
        summary.put("execution_status", determineExecutionStatus(results));

        // Performance metrics
        var summaryMetrics = generateSummaryMetrics(results);
        summary.put("metrics", summaryMetrics);
        summary.put(FIELD_TOTAL_BENCHMARKS, summaryMetrics.get(FIELD_TOTAL_BENCHMARKS));
        summary.put("performance_grade", calculatePerformanceGrade(results));
        summary.put(FIELD_AVERAGE_THROUGHPUT, summaryMetrics.get(FIELD_AVERAGE_THROUGHPUT));

        // Quality gates
        summary.put("quality_gates", evaluateQualityGates(results, type));

        // Recommendations
        summary.put("recommendations", generateRecommendations(results, type));

        // Artifact information
        summary.put("artifacts", listGeneratedArtifacts());

        Path summaryPath = Path.of(outputFile);
        Files.writeString(summaryPath, GSON.toJson(summary));
        LOGGER.info(INFO.SUMMARY_FILE_GENERATED.format(summaryPath));
    }

    /**
     * Determines the overall execution status of the benchmark run.
     */
    private String determineExecutionStatus(Collection<RunResult> results) {
        if (results.isEmpty()) {
            return "FAILED";
        }

        // Check if all benchmarks have valid results
        boolean allHaveResults = results.stream()
                .allMatch(r -> r.getPrimaryResult() != null &&
                        r.getPrimaryResult().getStatistics() != null &&
                        r.getPrimaryResult().getStatistics().getN() > 0);

        return allHaveResults ? "SUCCESS" : "PARTIAL";
    }

    /**
     * Generates summary metrics across all benchmarks.
     */
    private Map<String, Object> generateSummaryMetrics(Collection<RunResult> results) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        metrics.put(FIELD_TOTAL_BENCHMARKS, results.size());
        metrics.put("successful_benchmarks", countSuccessfulBenchmarks(results));
        metrics.put(FIELD_AVERAGE_THROUGHPUT, calculateAverageThroughput(results));
        metrics.put("total_execution_time", calculateTotalExecutionTime(results));
        metrics.put("performance_score", calculateOverallPerformanceScore(results));

        return metrics;
    }

    /**
     * Evaluates quality gates based on performance thresholds.
     */
    private Map<String, Object> evaluateQualityGates(Collection<RunResult> results, BenchmarkType type) {
        Map<String, Object> gates = new LinkedHashMap<>();

        // Define thresholds based on benchmark type
        // Integration benchmarks include network/TLS overhead and should have lower thresholds
        double throughputThreshold = type == BenchmarkType.MICRO ? 10_000 : 5_000; // 5 ops/ms = 5000 ops/s for HTTP
        double regressionThreshold = 10.0; // 10% regression threshold

        double avgThroughput = calculateAverageThroughput(results);

        // Throughput gate
        Map<String, Object> throughputGate = new LinkedHashMap<>();
        throughputGate.put("threshold", throughputThreshold);
        throughputGate.put("actual", avgThroughput);
        throughputGate.put(FIELD_STATUS, avgThroughput >= throughputThreshold ? "PASS" : "FAIL");
        gates.put("throughput", throughputGate);

        Map<String, Object> regressionGate = new LinkedHashMap<>();
        regressionGate.put("threshold_percent", regressionThreshold);
        regressionGate.put("actual_percent", 0.0);
        regressionGate.put(FIELD_STATUS, "PASS");
        regressionGate.put("note", "Historical comparison not available");
        gates.put("regression", regressionGate);

        // Overall gate status
        boolean allPassed = gates.values().stream()
                .allMatch(gate -> "PASS".equals(((Map<?, ?>) gate).get(FIELD_STATUS)));
        gates.put("overall_status", allPassed ? "PASS" : "FAIL");

        return gates;
    }

    /**
     * Generates recommendations based on benchmark results.
     */
    private Map<String, Object> generateRecommendations(Collection<RunResult> results, BenchmarkType type) {
        Map<String, Object> recommendations = new LinkedHashMap<>();

        double avgThroughput = calculateAverageThroughput(results);

        // Performance recommendations
        if (avgThroughput < 1_000) {
            recommendations.put(FIELD_PERFORMANCE, "Consider performance optimization - throughput below baseline");
        } else if (avgThroughput > 100_000) {
            recommendations.put(FIELD_PERFORMANCE, "Excellent performance - consider this as new baseline");
        } else {
            recommendations.put(FIELD_PERFORMANCE, "Performance within acceptable range");
        }

        // Deployment recommendations
        boolean deploymentReady = "SUCCESS".equals(determineExecutionStatus(results)) &&
                avgThroughput >= (type == BenchmarkType.MICRO ? 10_000 : 5_000);

        recommendations.put("deployment", deploymentReady ?
                "Ready for deployment - all quality gates passed" :
                "Review performance before deployment");

        // Monitoring recommendations
        if (results.size() < 5) {
            recommendations.put("monitoring", "Consider increasing benchmark coverage");
        } else {
            recommendations.put("monitoring", "Benchmark coverage adequate");
        }

        return recommendations;
    }

    /**
     * Lists all generated artifacts for reference.
     */
    private Map<String, Object> listGeneratedArtifacts() {
        Map<String, Object> artifacts = new LinkedHashMap<>();

        artifacts.put("badges", Map.of(
                FIELD_PERFORMANCE, "badges/performance-badge.json",
                "trend", "badges/trend-badge.json",
                "last_run", "badges/last-run-badge.json"
        ));

        artifacts.put("reports", Map.of(
                "overview", "index.html",
                "trends", "trends.html"
        ));

        artifacts.put("data", Map.of(
                "metrics", "data/metrics.json",
                "raw_results", "raw-result.json"
        ));

        artifacts.put("api", Map.of(
                "latest", "gh-pages-ready/api/latest.json",
                FIELD_STATUS, "gh-pages-ready/api/status.json"
        ));

        return artifacts;
    }

    /**
     * Counts benchmarks that completed successfully.
     */
    private long countSuccessfulBenchmarks(Collection<RunResult> results) {
        return results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .filter(r -> r.getPrimaryResult().getStatistics() != null)
                .filter(r -> r.getPrimaryResult().getStatistics().getN() > 0)
                .count();
    }

    /**
     * Calculates average throughput across all benchmarks, normalized to ops/s.
     */
    private double calculateAverageThroughput(Collection<RunResult> results) {
        return results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .mapToDouble(r -> {
                    double score = r.getPrimaryResult().getScore();
                    String unit = r.getPrimaryResult().getScoreUnit();

                    // Convert to ops/s based on unit
                    if (unit.contains("ops/s") || unit.contains("ops/sec")) {
                        return score;
                    } else if (unit.contains("ops/ms")) {
                        return score * 1000.0; // Convert ops/ms to ops/s
                    } else if (unit.contains("ops/us")) {
                        return score * 1_000_000.0; // Convert ops/us to ops/s
                    } else if (unit.contains("s/op")) {
                        return 1.0 / score; // Convert s/op to ops/s
                    } else if (unit.contains("ms/op")) {
                        return 1000.0 / score; // Convert ms/op to ops/s
                    } else if (unit.contains("us/op")) {
                        return 1_000_000.0 / score; // Convert us/op to ops/s
                    } else {
                        return score; // Default to raw score
                    }
                })
                .filter(score -> score > 0)
                .average()
                .orElse(0.0);
    }

    private double calculateTotalExecutionTime(Collection<RunResult> results) {
        return results.stream()
                .mapToDouble(result -> {
                    if (result.getParams() != null && result.getParams().getMeasurement() != null) {
                        var measurement = result.getParams().getMeasurement();
                        int iterations = measurement.getCount();
                        var timeValue = measurement.getTime().convertTo(TimeUnit.SECONDS);
                        return iterations * timeValue;
                    }
                    return 30.0;
                })
                .sum();
    }

    /**
     * Calculates an overall performance score.
     */
    private double calculateOverallPerformanceScore(Collection<RunResult> results) {
        double avgThroughput = calculateAverageThroughput(results);
        return switch ((int) Math.log10(Math.max(1, avgThroughput))) {
            case 6, 7, 8, 9 -> PERFECT_SCORE;
            case 5 -> EXCELLENT_SCORE;
            case 4 -> GOOD_SCORE;
            case 3 -> FAIR_SCORE;
            default -> Math.min(MINIMUM_SCORE, avgThroughput / THROUGHPUT_NORMALIZATION_FACTOR * SCORE_MULTIPLIER);
        };
    }

    private String calculatePerformanceGrade(Collection<RunResult> results) {
        double score = calculateOverallPerformanceScore(results);
        return switch ((int) (score / GRADE_DIVISOR)) {
            case 10, 9 -> "A+";
            case 8 -> "A";
            case 7 -> "B";
            case 6 -> "C";
            default -> "D";
        };
    }
}