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

import de.cuioss.benchmarking.common.BenchmarkConfiguration;
import de.cuioss.benchmarking.common.BenchmarkLoggingSetup;
import de.cuioss.benchmarking.common.BenchmarkResultProcessor;
import de.cuioss.jwt.quarkus.benchmark.config.TokenRepositoryConfig;
import de.cuioss.jwt.quarkus.benchmark.metrics.MetricsPostProcessor;
import de.cuioss.jwt.quarkus.benchmark.metrics.QuarkusMetricsFetcher;
import de.cuioss.jwt.quarkus.benchmark.metrics.SimpleMetricsExporter;
import de.cuioss.jwt.quarkus.benchmark.repository.TokenRepository;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.TimeValue;

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

        // Create configuration from system properties
        var config = BenchmarkConfiguration.fromSystemProperties()
                .withResultsDirectory(benchmarkResultsDir)
                .withIntegrationServiceUrl(DEFAULT_SERVICE_URL)
                .withKeycloakUrl("http://localhost:8080")
                .build();
        
        LOGGER.info("BenchmarkRunner.main() invoked - starting Quarkus JWT integration benchmarks...");
        LOGGER.info("Service URL: {}", config.integrationServiceUrl().orElse(DEFAULT_SERVICE_URL));
        LOGGER.info("Keycloak URL: {}", config.keycloakUrl().orElse("http://localhost:8080"));
        LOGGER.info("Results file: {}", config.resultFile());

        // Pre-initialize the shared TokenRepository instance
        initializeSharedTokenRepository();

        // Configure JMH options using modern configuration API
        config = config.toBuilder()
                .withIncludePattern(System.getProperty("jmh.include", "de\\.cuioss\\.jwt\\.quarkus\\.benchmark\\.benchmarks\\..*"))
                .withForks(1)
                .withWarmupIterations(1)
                .withMeasurementIterations(2)
                .withMeasurementTime(TimeValue.seconds(5))
                .withWarmupTime(TimeValue.seconds(1))
                .withThreads(10)
                .withResultFile(getBenchmarkResultsDir() + BENCHMARK_RESULT_FILENAME)
                .withMetricsUrl(config.integrationServiceUrl().orElse(DEFAULT_SERVICE_URL))
                .build();
                
        Options options = config.toJmhOptions();

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

            LOGGER.info("Results should be written to: {}", config.resultFile());

            // Generate artifacts (badges, reports, metrics, GitHub Pages structure)
            try {
                LOGGER.info("Generating benchmark artifacts...");
                BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
                processor.processResults(results, getBenchmarkResultsDir());
                LOGGER.info("Benchmark artifacts generated successfully in: {}", getBenchmarkResultsDir());
            } catch (Exception e) {
                LOGGER.error("Failed to generate benchmark artifacts", e);
                // Don't fail the benchmark run if artifact generation fails
            }

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
        var config = BenchmarkConfiguration.fromSystemProperties().build();
        String quarkusMetricsUrl = config.metricsUrl().orElse(DEFAULT_SERVICE_URL);
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
            String benchmarkResultsFile = config.resultFile();
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

        var benchConfig = BenchmarkConfiguration.fromSystemProperties().build();
        String keycloakUrl = benchConfig.keycloakUrl().orElse("https://localhost:1443");

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