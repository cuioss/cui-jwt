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
import de.cuioss.benchmarking.common.metrics.QuarkusMetricsFetcher;
import de.cuioss.benchmarking.common.runner.AbstractBenchmarkRunner;
import de.cuioss.benchmarking.common.util.BenchmarkLoggingSetup;
import de.cuioss.jwt.quarkus.benchmark.metrics.MetricsPostProcessor;
import de.cuioss.jwt.quarkus.benchmark.metrics.SimpleMetricsExporter;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
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
    }

    @Override protected void processResults(Collection<RunResult> results, BenchmarkConfiguration config) throws IOException {
        LOGGER.info(INFO.PROCESSING_RESULTS_STARTING.format(results.size()));

        // Call parent implementation for standard processing
        super.processResults(results, config);

        // Additional Quarkus-specific metrics processing
        LOGGER.info(INFO.CALLING_PROCESS_QUARKUS_METRICS.format());
        processQuarkusMetrics(config);
        LOGGER.info(INFO.PROCESS_QUARKUS_METRICS_COMPLETED.format());
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
     * Downloads and processes final cumulative metrics from Quarkus after benchmarks complete.
     * Uses QuarkusMetricsFetcher to download metrics and SimpleMetricsExporter to export them.
     * Also processes JMH benchmark results to create http-metrics.json.
     *
     * @param config the benchmark configuration
     */
    private void processQuarkusMetrics(BenchmarkConfiguration config) {
        LOGGER.info(INFO.PROCESS_QUARKUS_METRICS_ENTRY.format());
        String outputDirectory = config.resultsDirectory();

        // Get integration configuration to access URLs
        IntegrationConfiguration integrationConfig = config.integrationConfig();

        LOGGER.info(INFO.PROCESSING_METRICS_FROM_URL.format(
                integrationConfig.integrationServiceUrl(), outputDirectory));

        // Use QuarkusMetricsFetcher to download metrics (this also saves raw metrics)
        LOGGER.info(INFO.CREATING_METRICS_FETCHER.format());
        QuarkusMetricsFetcher metricsFetcher = new QuarkusMetricsFetcher(integrationConfig.integrationServiceUrl());

        // Use SimpleMetricsExporter to export JWT validation metrics
        LOGGER.info(INFO.CREATING_METRICS_EXPORTER.format());
        SimpleMetricsExporter exporter = new SimpleMetricsExporter(outputDirectory, metricsFetcher);
        LOGGER.info(INFO.CALLING_EXPORT_JWT_METRICS.format());
        exporter.exportJwtValidationMetrics("JwtValidation", Instant.now());
        LOGGER.info(INFO.EXPORT_JWT_METRICS_COMPLETED.format());

        // Process JMH benchmark results to create both http-metrics.json and quarkus-metrics.json
        // MetricsPostProcessor uses synchronous file I/O, so no delay is needed
        String benchmarkResultsFile = config.reportConfig().getOrCreateResultFile();
        MetricsPostProcessor metricsPostProcessor = new MetricsPostProcessor(benchmarkResultsFile, outputDirectory);
        metricsPostProcessor.parseAndExportAllMetrics(Instant.now());
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