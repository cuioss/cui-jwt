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

import de.cuioss.jwt.quarkus.benchmark.config.TokenRepositoryConfig;
import de.cuioss.jwt.quarkus.benchmark.logging.BenchmarkLoggingSetup;
import de.cuioss.jwt.quarkus.benchmark.metrics.MetricsPostProcessor;
import de.cuioss.jwt.quarkus.benchmark.metrics.QuarkusMetricsFetcher;
import de.cuioss.jwt.quarkus.benchmark.metrics.SimpleMetricsExporter;
import de.cuioss.jwt.quarkus.benchmark.repository.TokenRepository;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;

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
 *   <li><strong>JwtEchoBenchmark</strong>: Echo endpoint for network baseline</li>
 * </ul>
 */
public class BenchmarkRunner {

    static {
        // Set the logging manager as early as possible to prevent JBoss LogManager errors
        System.setProperty("java.util.logging.manager", "java.util.logging.LogManager");
    }

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkRunner.class);

    private static final String DEFAULT_SERVICE_URL = "https://localhost:8443";
    private static final String BENCHMARK_RESULT_FILENAME = "/integration-benchmark-result.json";

    /**
     * Main method to run all integration benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {
        // Set the logging manager explicitly before anything else to prevent the error
        System.setProperty("java.util.logging.manager", "java.util.logging.LogManager");

        // Configure logging to write to benchmark-results directory
        // This captures all console output (System.out/err and JMH) to both console and file
        String benchmarkResultsDir = getBenchmarkResultsDir();
        BenchmarkLoggingSetup.configureLogging(benchmarkResultsDir);

        LOGGER.info("BenchmarkRunner.main() invoked - starting Quarkus JWT integration benchmarks...");
        LOGGER.info("Service URL: {}", BenchmarkOptionsHelper.getIntegrationServiceUrl(DEFAULT_SERVICE_URL));
        LOGGER.info("Keycloak URL: {}", BenchmarkOptionsHelper.getKeycloakUrl("http://localhost:8080"));
        LOGGER.info("Results file: {}", BenchmarkOptionsHelper.getResultFile(benchmarkResultsDir + BENCHMARK_RESULT_FILENAME));

        // Pre-initialize the shared TokenRepository instance
        initializeSharedTokenRepository();

        // Configure JMH options using system properties passed from Maven
        Options options = new OptionsBuilder()
                // Include all benchmark classes in this package - can be overridden with system property
                .include(System.getProperty("jmh.include", "de\\.cuioss\\.jwt\\.quarkus\\.benchmark\\.benchmarks\\..*"))
                // Set number of forks
                .forks(BenchmarkOptionsHelper.getForks(1))
                // Minimal warmup for external services
                .warmupIterations(BenchmarkOptionsHelper.getWarmupIterations(1))
                // Set measurement iterations
                .measurementIterations(BenchmarkOptionsHelper.getMeasurementIterations(2))
                // Set measurement time based on profile
                .measurementTime(BenchmarkOptionsHelper.getMeasurementTime("5s"))
                // Set warmup time
                .warmupTime(BenchmarkOptionsHelper.getWarmupTime("1s"))
                // Set number of threads
                .threads(BenchmarkOptionsHelper.getThreadCount(10))
                // Configure result output
                .resultFormat(BenchmarkOptionsHelper.getResultFormat())
                .result(BenchmarkOptionsHelper.getResultFile(getBenchmarkResultsDir() + BENCHMARK_RESULT_FILENAME))
                // Add JVM arguments
                .jvmArgs("-Djava.util.logging.manager=java.util.logging.LogManager",
                        "-Djava.util.logging.config.file=src/main/resources/benchmark-logging.properties",
                        "-Dbenchmark.results.dir=" + getBenchmarkResultsDir(),
                        "-Dintegration.service.url=" + BenchmarkOptionsHelper.getIntegrationServiceUrl(DEFAULT_SERVICE_URL),
                        "-Dkeycloak.url=" + BenchmarkOptionsHelper.getKeycloakUrl("http://localhost:8080"),
                        "-Dquarkus.metrics.url=" + BenchmarkOptionsHelper.getQuarkusMetricsUrl(DEFAULT_SERVICE_URL))
                .build();

        try {
            LOGGER.info("Starting JMH runner...");
            // Run the benchmarks
            Collection<RunResult> results = new Runner(options).run();

            LOGGER.info("JMH runner completed, checking results...");

            // Check if any benchmarks actually ran
            if (results.isEmpty()) {
                LOGGER.error("No benchmark results were produced - all benchmarks failed");
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
                LOGGER.error("Benchmark execution failed: {} out of {} benchmarks produced no valid results",
                        benchmarksWithoutResults, results.size());
                throw new IllegalStateException("Benchmark execution failed: " + benchmarksWithoutResults + " benchmarks produced no valid results");
            }

            LOGGER.info("Benchmarks completed successfully: {} benchmarks executed", results.size());

            LOGGER.info("Results should be written to: {}", BenchmarkOptionsHelper.getResultFile(getBenchmarkResultsDir() + BENCHMARK_RESULT_FILENAME));

            // Process and download final metrics after successful benchmark execution
            processMetrics();
        } catch (RuntimeException e) {
            LOGGER.error("Benchmark execution failed", e);
            throw e;
        }
    }

    /**
     * Downloads and processes final cumulative metrics from Quarkus after benchmarks complete.
     * Uses QuarkusMetricsFetcher to download metrics and SimpleMetricsExporter to export them.
     * Also processes JMH benchmark results to create http-metrics.json.
     */
    private static void processMetrics() {
        String quarkusMetricsUrl = BenchmarkOptionsHelper.getQuarkusMetricsUrl(DEFAULT_SERVICE_URL);
        String outputDirectory = getBenchmarkResultsDir();

        LOGGER.info("Processing final cumulative metrics from Quarkus...");
        LOGGER.info("Metrics URL: {}", quarkusMetricsUrl);
        LOGGER.info("Output directory: {}", outputDirectory);

        try {
            // Use QuarkusMetricsFetcher to download metrics (this also saves raw metrics)
            QuarkusMetricsFetcher metricsFetcher = new QuarkusMetricsFetcher(quarkusMetricsUrl);

            // Use SimpleMetricsExporter to export JWT validation metrics
            SimpleMetricsExporter exporter = new SimpleMetricsExporter(outputDirectory, metricsFetcher);

            // Export the JWT validation metrics with current timestamp
            exporter.exportJwtValidationMetrics("JwtValidation", Instant.now());

            // Process JMH benchmark results to create http-metrics.json
            String benchmarkResultsFile = BenchmarkOptionsHelper.getResultFile(outputDirectory + BENCHMARK_RESULT_FILENAME);
            LOGGER.info("Processing JMH benchmark results from: {}", benchmarkResultsFile);

            MetricsPostProcessor metricsPostProcessor = new MetricsPostProcessor(benchmarkResultsFile, outputDirectory);
            metricsPostProcessor.parseAndExportHttpMetrics(Instant.now());

            LOGGER.info("Final metrics processing completed successfully");
        } catch (IOException e) {
            // Log the error but don't fail the entire benchmark run
            LOGGER.error("Failed to process final metrics due to I/O error - continuing without metrics", e);
        }
    }

    /**
     * Gets the benchmark results directory from system property or defaults to target/benchmark-results.
     *
     * @return the benchmark results directory path
     */
    private static String getBenchmarkResultsDir() {
        return System.getProperty("benchmark.results.dir", "target/benchmark-results");
    }

    /**
     * Initializes the shared TokenRepository instance that will be used by all benchmarks.
     * This avoids multiple initializations during benchmark setup phases.
     */
    private static void initializeSharedTokenRepository() {
        LOGGER.info("Pre-initializing shared TokenRepository instance for benchmarks");

        String keycloakUrl = BenchmarkOptionsHelper.getKeycloakUrl("https://localhost:1443");

        TokenRepositoryConfig config = TokenRepositoryConfig.builder()
                .keycloakBaseUrl(keycloakUrl)
                .realm("benchmark")
                .clientId("benchmark-client")
                .clientSecret("benchmark-secret")
                .username("benchmark-user")
                .password("benchmark-password")
                .connectionTimeoutMs(5000)
                .requestTimeoutMs(10000)
                .verifySsl(false)
                .tokenRefreshThresholdSeconds(300)
                .build();

        TokenRepository.initializeSharedInstance(config);
        LOGGER.info("Shared TokenRepository instance pre-initialized successfully");
    }
}