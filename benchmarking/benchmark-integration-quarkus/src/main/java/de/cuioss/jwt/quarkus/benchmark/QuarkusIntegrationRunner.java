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
package de.cuioss.jwt.quarkus.benchmark;

import de.cuioss.benchmarking.common.config.BenchmarkConfiguration;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.config.IntegrationConfiguration;
import de.cuioss.benchmarking.common.metrics.MetricsOrchestrator;
import de.cuioss.benchmarking.common.metrics.PrometheusClient;
import de.cuioss.benchmarking.common.runner.AbstractBenchmarkRunner;
import de.cuioss.benchmarking.common.util.BenchmarkLoggingSetup;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Main class for running Quarkus integration benchmarks.
 * <p>
 * This class runs benchmarks against a live Quarkus application to measure
 * real-world performance characteristics including network overhead and
 * containerized deployment effects.
 * <p>
 * Benchmark classes:
 * <ul>
 *   <li><strong>JwtValidationBenchmark</strong>: JWT validation endpoint performance</li>
 *   <li><strong>JwtHealthBenchmark</strong>: Health check endpoint baseline</li>
 * </ul>
 */
public class QuarkusIntegrationRunner extends AbstractBenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(QuarkusIntegrationRunner.class);

    // Timestamps for benchmark execution tracking
    private Instant benchmarkStartTime;
    private Instant benchmarkEndTime;

    @Override protected BenchmarkConfiguration createConfiguration() {
        // Load integration configuration from system properties
        IntegrationConfiguration integrationConfig = IntegrationConfiguration.fromProperties();

        return BenchmarkConfiguration.builder()
                .withBenchmarkType(BenchmarkType.INTEGRATION)
                .withThroughputBenchmarkName("validateJwtThroughput")
                .withLatencyBenchmarkName("validateJwtThroughput")
                .withIntegrationConfig(integrationConfig)
                .build();
    }

    @Override protected void prepareBenchmark(BenchmarkConfiguration config) throws IOException {
        BenchmarkLoggingSetup.configureLogging("target/benchmark-results");

        // Get configuration to log the URLs
        IntegrationConfiguration integrationConfig = config.integrationConfig();
        LOGGER.info(INFO.QUARKUS_BENCHMARKS_STARTING.format(
                integrationConfig.integrationServiceUrl(), integrationConfig.keycloakUrl()));

        // Record benchmark start time for real-time metrics collection
        benchmarkStartTime = Instant.now();
        LOGGER.info("Benchmark execution started at: {}", benchmarkStartTime);
    }

    @Override protected void processResults(Collection<RunResult> results, BenchmarkConfiguration config) throws IOException {
        LOGGER.info(INFO.PROCESSING_RESULTS_STARTING.format(results.size()));

        // Record benchmark end time for real-time metrics collection
        benchmarkEndTime = Instant.now();
        LOGGER.info("Benchmark execution completed at: {}", benchmarkEndTime);

        // Call parent implementation for standard processing
        super.processResults(results, config);

        // Collect real-time Prometheus metrics for each benchmark
        collectPrometheusMetrics(results, config);
    }

    @Override protected void afterBenchmark(Collection<RunResult> results, BenchmarkConfiguration config) {
        // Validate results
        if (results.isEmpty()) {
            throw new IllegalStateException("Benchmark execution failed: No results produced");
        }

        LOGGER.debug("Found {} benchmark results", results.size());

        // Check if all benchmarks have valid primary results
        long benchmarksWithoutResults = results.stream()
                .filter(result -> result.getPrimaryResult() == null ||
                        result.getPrimaryResult().getStatistics() == null ||
                        result.getPrimaryResult().getStatistics().getN() == 0)
                .count();

        if (benchmarksWithoutResults > 0) {
            throw new IllegalStateException("Benchmark execution failed: " + benchmarksWithoutResults +
                    " out of " + results.size() + " benchmarks produced no valid results");
        }
    }

    @Override protected void cleanup(BenchmarkConfiguration config) throws IOException {
        // Clean up any integration test resources if needed
        LOGGER.debug("Integration benchmark cleanup completed");
    }

    @Override protected OptionsBuilder buildCommonOptions(BenchmarkConfiguration config) {
        OptionsBuilder builder = super.buildCommonOptions(config);

        // Add profilers for CPU and memory metrics collection
        // GC profiler: Tracks garbage collection statistics
        builder.addProfiler("gc");

        // Stack profiler: Shows hot methods and call stacks
        builder.addProfiler("stack");

        // Compiler profiler: Tracks JIT compilation activity
        builder.addProfiler("comp");

        LOGGER.info("JMH profilers enabled: gc, stack, comp");
        return builder;
    }

    /**
     * Collects real-time metrics from Prometheus for each benchmark execution.
     * Uses the captured start and end timestamps to fetch time-series data.
     *
     * @param results the benchmark results
     * @param config the benchmark configuration
     */
    private void collectPrometheusMetrics(Collection<RunResult> results, BenchmarkConfiguration config) {
        // Skip if timestamps are not available
        if (benchmarkStartTime == null || benchmarkEndTime == null) {
            LOGGER.warn("Cannot collect Prometheus metrics: timestamps not captured");
            return;
        }

        try {
            // Get Prometheus URL from system property or use default
            String prometheusUrl = System.getProperty("prometheus.url", "http://localhost:9090");
            String outputDirectory = config.resultsDirectory();
            IntegrationConfiguration integrationConfig = config.integrationConfig();

            Path prometheusDir = Path.of(outputDirectory, "prometheus");
            Files.createDirectories(prometheusDir);

            LOGGER.info("Collecting real-time metrics from Prometheus at: {}", prometheusUrl);

            // Create orchestrator with Prometheus client
            MetricsOrchestrator orchestrator = new MetricsOrchestrator(
                    integrationConfig.integrationServiceUrl(),
                    Path.of(outputDirectory, "metrics-download"),
                    Path.of(outputDirectory),
                    new PrometheusClient(prometheusUrl)
            );

            // Process each benchmark result
            for (RunResult result : results) {
                String benchmarkName = extractBenchmarkName(result);

                LOGGER.info("Collecting Prometheus metrics for benchmark '{}'", benchmarkName);

                // Collect real-time metrics for this benchmark
                orchestrator.collectBenchmarkMetrics(
                        benchmarkName,
                        benchmarkStartTime,
                        benchmarkEndTime,
                        prometheusDir
                );

                LOGGER.info("Prometheus metrics saved to: {}/{}-metrics.json",
                        prometheusDir, benchmarkName);
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to collect Prometheus metrics: {}", e.getMessage());
            // Don't fail the build, just log the warning
        }
    }

    /**
     * Extracts a clean benchmark name from the JMH RunResult.
     *
     * @param result the JMH run result
     * @return a clean benchmark name suitable for file naming
     */
    private String extractBenchmarkName(RunResult result) {
        // Get the benchmark label which contains the full benchmark method name
        String label = result.getPrimaryResult().getLabel();

        // Extract just the method name (e.g., "validateJwtThroughput" from full class path)
        int lastDot = label.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < label.length() - 1) {
            return label.substring(lastDot + 1);
        }

        return label;
    }

    /**
     * Main method to run all integration benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws IOException if I/O operations fail
     * @throws RunnerException if benchmark execution fails
     */
    public static void main(String[] args) throws IOException, RunnerException {
        new QuarkusIntegrationRunner().runBenchmark();
    }
}