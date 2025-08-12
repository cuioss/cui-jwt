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
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Generates structured JSON metrics from benchmark results.
 * <p>
 * Creates API-consumable metrics data including performance scores,
 * throughput measurements, and historical tracking information.
 * </p>
 * 
 * @since 1.0
 */
public final class MetricsGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsGenerator.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generates structured metrics JSON from benchmark results.
     *
     * @param results   the benchmark results
     * @param outputDir the output directory for metrics files
     * @throws IOException if file operations fail
     */
    public void generateMetricsJson(Collection<RunResult> results, String outputDir) throws IOException {
        var metrics = createMetrics(results);
        
        var filePath = outputDir + "/metrics.json";
        Files.createDirectories(Paths.get(outputDir));
        var json = GSON.toJson(metrics);
        Files.writeString(Paths.get(filePath), json);
        
        LOGGER.info("Generated metrics JSON: %s", filePath);
    }

    private BenchmarkMetrics createMetrics(Collection<RunResult> results) {
        var timestamp = Instant.now();
        var benchmarkResults = results.stream()
            .map(this::convertToMetricResult)
            .toList();
        
        var summary = createSummary(results);
        
        return new BenchmarkMetrics(
            timestamp.toString(),
            benchmarkResults.size(),
            summary,
            benchmarkResults
        );
    }

    private MetricResult convertToMetricResult(RunResult result) {
        var primaryResult = result.getPrimaryResult();
        
        return new MetricResult(
            result.getParams().getBenchmark(),
            primaryResult.getScore(),
            primaryResult.getScoreError(),
            primaryResult.getScoreUnit(),
            primaryResult.getSampleCount(),
            createStatistics(primaryResult)
        );
    }

    private MetricStatistics createStatistics(org.openjdk.jmh.results.Result result) {
        var statistics = result.getStatistics();
        
        return new MetricStatistics(
            statistics.getMin(),
            statistics.getMax(),
            statistics.getMean(),
            statistics.getStandardDeviation(),
            calculatePercentile(statistics, 50.0),
            calculatePercentile(statistics, 95.0),
            calculatePercentile(statistics, 99.0)
        );
    }

    private double calculatePercentile(org.openjdk.jmh.util.Statistics stats, double percentile) {
        // Simplified percentile calculation - in real implementation would use proper algorithm
        return stats.getMean(); // Placeholder
    }

    private MetricSummary createSummary(Collection<RunResult> results) {
        if (results.isEmpty()) {
            return new MetricSummary(0.0, 0.0, 0.0, 0.0, 0);
        }

        var scores = results.stream()
            .mapToDouble(r -> r.getPrimaryResult().getScore())
            .toArray();

        var avgThroughput = java.util.Arrays.stream(scores).average().orElse(0.0);
        var maxThroughput = java.util.Arrays.stream(scores).max().orElse(0.0);
        var minThroughput = java.util.Arrays.stream(scores).min().orElse(0.0);
        var avgLatency = avgThroughput > 0 ? 1.0 / avgThroughput : 0.0;
        var totalOperations = results.stream()
            .mapToLong(r -> r.getPrimaryResult().getSampleCount())
            .sum();

        return new MetricSummary(
            avgThroughput,
            maxThroughput,
            minThroughput,
            avgLatency,
            totalOperations
        );
    }

    /**
     * Complete benchmark metrics structure.
     */
    private record BenchmarkMetrics(
        String timestamp,
        int benchmarkCount,
        MetricSummary summary,
        List<MetricResult> results
    ) {}

    /**
     * Summary metrics across all benchmarks.
     */
    private record MetricSummary(
        double averageThroughput,
        double maxThroughput,
        double minThroughput,
        double averageLatency,
        long totalOperations
    ) {}

    /**
     * Individual benchmark result metrics.
     */
    private record MetricResult(
        String benchmarkName,
        double score,
        double scoreError,
        String unit,
        long sampleCount,
        MetricStatistics statistics
    ) {}

    /**
     * Statistical analysis of benchmark results.
     */
    private record MetricStatistics(
        double min,
        double max,
        double mean,
        double standardDeviation,
        double p50,
        double p95,
        double p99
    ) {}
}