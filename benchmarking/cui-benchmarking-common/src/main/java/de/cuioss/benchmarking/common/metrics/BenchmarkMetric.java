package de.cuioss.benchmarking.common.metrics;

import java.util.Map;

/**
 * Represents the metrics for an individual benchmark.
 *
 * @param name       benchmark name
 * @param throughput operations per second
 * @param latency    average latency in seconds
 * @param details    additional benchmark details
 */
public record BenchmarkMetric(
        String name,
        double throughput,
        double latency,
        Map<String, Object> details
) {
}
