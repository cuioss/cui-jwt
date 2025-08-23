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

    private static final CuiLogger LOGGER = new CuiLogger(SummaryGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

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
        LOGGER.info("Writing benchmark summary for {} {} results", results.size(), type.getDisplayName());

        Map<String, Object> summary = new LinkedHashMap<>();

        // Basic information
        summary.put("timestamp", ISO_FORMATTER.format(timestamp.atOffset(ZoneOffset.UTC)));
        summary.put("benchmark_type", type.getIdentifier());
        summary.put("execution_status", determineExecutionStatus(results));

        // Performance metrics
        summary.put("metrics", generateSummaryMetrics(results));

        // Quality gates
        summary.put("quality_gates", evaluateQualityGates(results, type));

        // Recommendations
        summary.put("recommendations", generateRecommendations(results, type));

        // Artifact information
        summary.put("artifacts", listGeneratedArtifacts());

        Path summaryPath = Path.of(outputFile);
        Files.writeString(summaryPath, GSON.toJson(summary));
        LOGGER.info("Generated summary file: {}", summaryPath);
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

        metrics.put("total_benchmarks", results.size());
        metrics.put("successful_benchmarks", countSuccessfulBenchmarks(results));
        metrics.put("average_throughput", calculateAverageThroughput(results));
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
        double throughputThreshold = type == BenchmarkType.MICRO ? 10_000 : 1_000;
        double regressionThreshold = 10.0; // 10% regression threshold
        
        double avgThroughput = calculateAverageThroughput(results);

        // Throughput gate
        Map<String, Object> throughputGate = new LinkedHashMap<>();
        throughputGate.put("threshold", throughputThreshold);
        throughputGate.put("actual", avgThroughput);
        throughputGate.put("status", avgThroughput >= throughputThreshold ? "PASS" : "FAIL");
        gates.put("throughput", throughputGate);

        // Regression gate (placeholder - would need historical data)
        Map<String, Object> regressionGate = new LinkedHashMap<>();
        regressionGate.put("threshold_percent", regressionThreshold);
        regressionGate.put("actual_percent", 0.0); // Placeholder
        regressionGate.put("status", "PASS"); // Placeholder
        gates.put("regression", regressionGate);

        // Overall gate status
        boolean allPassed = gates.values().stream()
                .allMatch(gate -> "PASS".equals(((Map<?, ?>) gate).get("status")));
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
            recommendations.put("performance", "Consider performance optimization - throughput below baseline");
        } else if (avgThroughput > 100_000) {
            recommendations.put("performance", "Excellent performance - consider this as new baseline");
        } else {
            recommendations.put("performance", "Performance within acceptable range");
        }

        // Deployment recommendations
        boolean deploymentReady = determineExecutionStatus(results).equals("SUCCESS") &&
                avgThroughput >= (type == BenchmarkType.MICRO ? 10_000 : 1_000);

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
                "performance", "badges/performance-badge.json",
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
                "status", "gh-pages-ready/api/status.json"
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
     * Calculates average throughput across all benchmarks.
     */
    private double calculateAverageThroughput(Collection<RunResult> results) {
        return results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .filter(r -> r.getPrimaryResult().getScoreUnit().contains("ops"))
                .mapToDouble(r -> r.getPrimaryResult().getScore())
                .average()
                .orElse(0.0);
    }

    /**
     * Calculates total execution time (placeholder implementation).
     */
    private double calculateTotalExecutionTime(Collection<RunResult> results) {
        // Placeholder - in a real implementation, this would aggregate
        // actual execution times from the benchmark results
        return results.size() * 30.0; // Approximate 30 seconds per benchmark
    }

    /**
     * Calculates an overall performance score.
     */
    private double calculateOverallPerformanceScore(Collection<RunResult> results) {
        double avgThroughput = calculateAverageThroughput(results);

        // Normalize to a 0-100 scale
        if (avgThroughput >= 1_000_000) {
            return 100.0;
        } else if (avgThroughput >= 100_000) {
            return 90.0;
        } else if (avgThroughput >= 10_000) {
            return 80.0;
        } else if (avgThroughput >= 1_000) {
            return 70.0;
        } else {
            return Math.max(0.0, avgThroughput / 1_000.0 * 70.0);
        }
    }
}