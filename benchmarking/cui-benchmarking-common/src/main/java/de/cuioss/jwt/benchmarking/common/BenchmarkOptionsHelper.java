/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.benchmarking.common;

/**
 * Helper class for configuring JMH benchmark options through system properties.
 * Provides standardized configuration across all benchmark modules.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public final class BenchmarkOptionsHelper {

    private static final String INCLUDE_PROPERTY = "benchmark.include";
    private static final String FORKS_PROPERTY = "benchmark.forks";
    private static final String WARMUP_ITERATIONS_PROPERTY = "benchmark.warmup.iterations";
    private static final String MEASUREMENT_ITERATIONS_PROPERTY = "benchmark.measurement.iterations";

    // Default values optimized for CI/CD pipelines
    private static final String DEFAULT_INCLUDE = ".*Benchmark.*";
    private static final int DEFAULT_FORKS = 1;
    private static final int DEFAULT_WARMUP_ITERATIONS = 2;
    private static final int DEFAULT_MEASUREMENT_ITERATIONS = 3;

    private BenchmarkOptionsHelper() {
        // Utility class
    }

    /**
     * Get the benchmark include pattern from system properties.
     * 
     * @return include pattern for JMH benchmarks
     */
    public static String getInclude() {
        return System.getProperty(INCLUDE_PROPERTY, DEFAULT_INCLUDE);
    }

    /**
     * Get the number of forks from system properties.
     * 
     * @return number of forks for JMH benchmarks
     */
    public static int getForks() {
        return Integer.parseInt(System.getProperty(FORKS_PROPERTY, String.valueOf(DEFAULT_FORKS)));
    }

    /**
     * Get the number of warmup iterations from system properties.
     * 
     * @return number of warmup iterations for JMH benchmarks
     */
    public static int getWarmupIterations() {
        return Integer.parseInt(System.getProperty(WARMUP_ITERATIONS_PROPERTY, String.valueOf(DEFAULT_WARMUP_ITERATIONS)));
    }

    /**
     * Get the number of measurement iterations from system properties.
     * 
     * @return number of measurement iterations for JMH benchmarks
     */
    public static int getMeasurementIterations() {
        return Integer.parseInt(System.getProperty(MEASUREMENT_ITERATIONS_PROPERTY, String.valueOf(DEFAULT_MEASUREMENT_ITERATIONS)));
    }

    /**
     * Print current configuration for debugging purposes.
     */
    public static void printConfiguration() {
        System.out.println("📋 JMH Configuration:");
        System.out.println("  Include pattern: " + getInclude());
        System.out.println("  Forks: " + getForks());
        System.out.println("  Warmup iterations: " + getWarmupIterations());
        System.out.println("  Measurement iterations: " + getMeasurementIterations());
    }
}