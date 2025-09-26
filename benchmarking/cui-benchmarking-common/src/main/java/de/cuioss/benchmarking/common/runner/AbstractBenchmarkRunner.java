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
package de.cuioss.benchmarking.common.runner;

import de.cuioss.benchmarking.common.config.BenchmarkConfiguration;
import de.cuioss.benchmarking.common.metrics.PrometheusMetricsManager;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Abstract base class for JMH benchmark runners that integrates with CUI benchmarking infrastructure.
 * <p>
 * This runner uses the Template Method pattern to orchestrate benchmark execution while
 * allowing concrete implementations to customize specific steps.
 * <p>
 * The template method {@link #runBenchmark()} defines the benchmark execution flow:
 * <ol>
 *   <li>Validation of configuration</li>
 *   <li>Preparation phase ({@link #prepareBenchmark(BenchmarkConfiguration)})</li>
 *   <li>Benchmark execution ({@link #executeBenchmark(Options)})</li>
 *   <li>Results processing ({@link #processResults(Collection, BenchmarkConfiguration)})</li>
 *   <li>Cleanup phase ({@link #cleanup(BenchmarkConfiguration)})</li>
 * </ol>
 * <p>
 * Features:
 * <ul>
 *   <li>Smart benchmark type detection (micro vs integration)</li>
 *   <li>Automatic badge generation with performance scoring</li>
 *   <li>Self-contained HTML reports with embedded CSS</li>
 *   <li>GitHub Pages ready deployment structure</li>
 *   <li>Structured metrics in JSON format</li>
 * </ul>
 */
public abstract class AbstractBenchmarkRunner {

    private static final CuiLogger LOGGER =
            new CuiLogger(AbstractBenchmarkRunner.class);

    private final PrometheusMetricsManager prometheusMetricsManager;
    private Instant benchmarkStartTime;
    private Instant benchmarkEndTime;

    /**
     * Default constructor that initializes the Prometheus metrics manager.
     */
    protected AbstractBenchmarkRunner() {
        this.prometheusMetricsManager = new PrometheusMetricsManager();
    }

    /**
     * Creates the benchmark configuration for this runner.
     * This method must provide a complete configuration including:
     * - Benchmark type
     * - Include pattern
     * - Result file name and directory
     * - Throughput and latency benchmark names
     * - Any other specific configuration
     * 
     * @return the complete benchmark configuration
     */
    protected abstract BenchmarkConfiguration createConfiguration();

    /**
     * Prepares the benchmark environment.
     * This method is called after configuration validation and before benchmark execution.
     * Use this for initialization tasks like:
     * - Setting up resources
     * - Initializing caches
     * - Configuring logging
     * 
     * @param config the benchmark configuration
     * @throws IOException if preparation fails
     */
    protected abstract void prepareBenchmark(BenchmarkConfiguration config) throws IOException;

    /**
     * Executes the benchmark with the given options.
     * Default implementation uses JMH Runner, but can be overridden for custom execution.
     * 
     * @param options the JMH options
     * @return collection of benchmark results
     * @throws RunnerException if benchmark execution fails
     */
    protected Collection<RunResult> executeBenchmark(Options options) throws RunnerException {
        return new Runner(options).run();
    }

    /**
     * Processes the benchmark results.
     * Default implementation uses BenchmarkResultProcessor and collects Prometheus metrics.
     *
     * @param results the benchmark results
     * @param config the benchmark configuration
     * @throws IOException if processing fails
     */
    protected void processResults(Collection<RunResult> results, BenchmarkConfiguration config) throws IOException {
        // Collect Prometheus metrics BEFORE processing results
        // This ensures metrics exist when GitHubPagesGenerator runs
        prometheusMetricsManager.collectMetricsForResults(results, config);

        // Process results (generates reports and GitHub Pages)
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(
                config.reportConfig().benchmarkType(),
                config.reportConfig().throughputBenchmarkName(),
                config.reportConfig().latencyBenchmarkName()
        );
        processor.processResults(results, config.resultsDirectory());
    }

    /**
     * Performs cleanup after benchmark execution.
     * This method is called after results processing, regardless of success or failure.
     * Use this for:
     * - Releasing resources
     * - Final metrics collection
     * - Post-processing tasks
     * 
     * @param config the benchmark configuration
     * @throws IOException if cleanup fails
     */
    protected abstract void cleanup(BenchmarkConfiguration config) throws IOException;

    /**
     * Hook method called before benchmark execution starts.
     * Override to add custom pre-benchmark logic.
     * Default implementation records benchmark start time for Prometheus metrics.
     *
     * @param config the benchmark configuration
     */
    protected void beforeBenchmark(BenchmarkConfiguration config) {
        // Record benchmark start time for real-time metrics collection
        benchmarkStartTime = Instant.now();
        LOGGER.debug("Benchmark execution started at: {}", benchmarkStartTime);

        // Record start time for each benchmark if we know the names
        if (config.throughputBenchmarkName() != null) {
            prometheusMetricsManager.recordBenchmarkStart(config.throughputBenchmarkName());
        }
        if (config.latencyBenchmarkName() != null &&
            !config.latencyBenchmarkName().equals(config.throughputBenchmarkName())) {
            prometheusMetricsManager.recordBenchmarkStart(config.latencyBenchmarkName());
        }
    }

    /**
     * Hook method called after benchmark execution completes.
     * Override to add custom post-benchmark logic.
     * Default implementation logs completion.
     *
     * @param results the benchmark results
     * @param config the benchmark configuration
     */
    protected void afterBenchmark(Collection<RunResult> results, BenchmarkConfiguration config) {
        LOGGER.debug("Benchmark execution completed at: {}", benchmarkEndTime);
    }


    /**
     * Extracts a clean benchmark name from the JMH RunResult.
     *
     * @param result the JMH run result
     * @return a clean benchmark name suitable for file naming
     */
    private String extractBenchmarkName(RunResult result) {
        String label = result.getPrimaryResult().getLabel();
        int lastDot = label.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < label.length() - 1) {
            return label.substring(lastDot + 1);
        }
        return label;
    }

    /**
     * Builds JMH options from the benchmark configuration.
     * This method extracts common option building logic that can be reused or extended.
     * 
     * @param config the benchmark configuration
     * @return JMH options builder with common settings applied
     */
    protected OptionsBuilder buildCommonOptions(BenchmarkConfiguration config) {
        var builder = new OptionsBuilder();

        builder.include(config.includePattern())
                .resultFormat(config.reportConfig().resultFormat())
                .result(config.reportConfig().getOrCreateResultFile())
                .forks(config.forks())
                .warmupIterations(config.warmupIterations())
                .measurementIterations(config.measurementIterations())
                .measurementTime(config.measurementTime())
                .warmupTime(config.warmupTime())
                .threads(config.threads());

        // Pass all system properties from parent JVM to forked JVMs
        // This ensures all Maven-provided properties are available in benchmark processes
        var systemProperties = System.getProperties().entrySet().stream()
                .map(e -> "-D" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);

        builder.jvmArgsPrepend(systemProperties);

        return builder;
    }

    /**
     * Validates the benchmark configuration.
     * Throws IllegalArgumentException if configuration is invalid.
     * 
     * @param config the configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    protected void validateConfiguration(BenchmarkConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("Benchmark configuration cannot be null");
        }

        if (config.benchmarkType() == null) {
            throw new IllegalArgumentException("Benchmark type must be specified");
        }

        if (config.includePattern() == null || config.includePattern().isEmpty()) {
            throw new IllegalArgumentException("Include pattern must be specified");
        }

        if (config.resultsDirectory() == null || config.resultsDirectory().isEmpty()) {
            throw new IllegalArgumentException("Results directory must be specified");
        }

        if (config.forks() < 0) {
            throw new IllegalArgumentException("Forks must be non-negative");
        }

        if (config.warmupIterations() < 0) {
            throw new IllegalArgumentException("Warmup iterations must be non-negative");
        }

        if (config.measurementIterations() <= 0) {
            throw new IllegalArgumentException("Measurement iterations must be positive");
        }

        if (config.threads() <= 0) {
            throw new IllegalArgumentException("Threads must be positive");
        }
    }

    /**
     * Template method that defines the benchmark execution flow.
     * This is the main entry point that orchestrates all phases of benchmark execution.
     *
     * @throws IOException if I/O operations fail
     * @throws RunnerException if benchmark execution fails
     */
    public final void runBenchmark() throws IOException, RunnerException {
        // Step 1: Create and validate configuration
        BenchmarkConfiguration config = createConfiguration();
        validateConfiguration(config);

        String outputDir = config.resultsDirectory();
        LOGGER.info(INFO.BENCHMARK_RUNNER_STARTING.format() + " - Type: " + config.benchmarkType() + ", Output: " + outputDir);

        // Step 2: Ensure output directory exists
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        try {
            // Step 3: Preparation phase
            prepareBenchmark(config);

            // Step 4: Pre-benchmark hook
            beforeBenchmark(config);

            // Step 5: Build options
            Options options = buildCommonOptions(config).build();

            // Step 6: Execute benchmarks
            Collection<RunResult> results = executeBenchmark(options);

            if (results.isEmpty()) {
                throw new IllegalStateException("No benchmark results produced");
            }

            // Step 7: Record session-wide timestamps for JMH benchmarks BEFORE processing results
            benchmarkEndTime = Instant.now();
            for (RunResult result : results) {
                String benchmarkName = extractBenchmarkName(result);
                prometheusMetricsManager.recordBenchmarkTimestamps(
                    benchmarkName, benchmarkStartTime, benchmarkEndTime);
            }

            // Step 8: Process results (including Prometheus metrics collection)
            processResults(results, config);

            // Step 9: Post-benchmark hook
            afterBenchmark(results, config);

            LOGGER.info(INFO.BENCHMARKS_COMPLETED.format(results.size()) + ", artifacts in " + outputDir);

        } finally {
            // Step 9: Cleanup (always executed)
            cleanup(config);
        }
    }

    /**
     * Gets the Prometheus metrics manager instance.
     * This allows subclasses to access the manager for custom metrics collection.
     *
     * @return the Prometheus metrics manager
     */
    protected PrometheusMetricsManager getPrometheusMetricsManager() {
        return prometheusMetricsManager;
    }

    /**
     * Gets the recorded benchmark start time.
     *
     * @return the benchmark start time, or null if not yet started
     */
    protected Instant getBenchmarkStartTime() {
        return benchmarkStartTime;
    }

    /**
     * Gets the recorded benchmark end time.
     *
     * @return the benchmark end time, or null if not yet completed
     */
    protected Instant getBenchmarkEndTime() {
        return benchmarkEndTime;
    }

}