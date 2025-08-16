package de.cuioss.benchmarking.common.metrics;

import java.time.Instant;
import java.util.List;

/**
 * Represents the complete performance metrics for a benchmark run.
 *
 * @param timestamp         when metrics were generated
 * @param benchmarkCount    number of benchmarks executed
 * @param averageThroughput average throughput across all benchmarks
 * @param averageLatency    average latency across all benchmarks
 * @param benchmarks        individual benchmark metrics
 */
public record PerformanceMetrics(
        Instant timestamp,
        int benchmarkCount,
        double averageThroughput,
        double averageLatency,
        List<BenchmarkMetric> benchmarks
) {
}
