package de.cuioss.benchmarking.common.detector;

/**
 * An enum to represent the type of benchmark being run.
 */
public enum BenchmarkType {
    /**
     * Micro benchmarks, typically focusing on a single component in isolation.
     */
    MICRO,

    /**
     * Integration benchmarks, testing the performance of multiple components working together.
     */
    INTEGRATION;
}
