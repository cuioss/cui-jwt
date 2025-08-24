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
import de.cuioss.benchmarking.common.BenchmarkType;
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
    private static final String DEFAULT_KEYCLOAK_URL = "http://localhost:8080";
    private static final String DEFAULT_KEYCLOAK_SECURE_URL = "https://localhost:1443";
    private static final String BENCHMARK_RESULT_FILENAME = "/integration-benchmark-result.json";
    private static final String BENCHMARK_RESULTS_DIR_PROPERTY = "benchmark.results.dir";
    private static final String DEFAULT_BENCHMARK_RESULTS_DIR = "target/benchmark-results";

    // Benchmark configuration constants
    private static final int BENCHMARK_FORKS = 1;
    private static final int WARMUP_ITERATIONS = 1;
    private static final int MEASUREMENT_ITERATIONS = 2;
    private static final int MEASUREMENT_TIME_SECONDS = 5;
    private static final int WARMUP_TIME_SECONDS = 1;
    private static final int THREAD_COUNT = 10;

    // Token repository configuration constants
    private static final String KEYCLOAK_REALM = "benchmark";
    private static final String KEYCLOAK_CLIENT_ID = "benchmark-client";
    private static final String KEYCLOAK_CLIENT_SECRET = "benchmark-secret";
    private static final String KEYCLOAK_USERNAME = "benchmark-user";
    private static final String KEYCLOAK_PASSWORD = "benchmark-password";
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int REQUEST_TIMEOUT_MS = 10000;
    private static final int TOKEN_REFRESH_THRESHOLD_SECONDS = 300;

    /**
     * Main method to run all integration benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {

        // Configure logging to write to benchmark-results directory
        // This captures all console output (System.out/err and JMH) to both console and file
        String benchmarkResultsDir = getBenchmarkResultsDir();
        BenchmarkLoggingSetup.configureLogging(benchmarkResultsDir);

        // Create configuration from system properties
        var config = BenchmarkConfiguration.fromSystemProperties()
                .withResultsDirectory(benchmarkResultsDir)
                .withIntegrationServiceUrl(DEFAULT_SERVICE_URL)
                .withKeycloakUrl(DEFAULT_KEYCLOAK_URL)
                .build();

        LOGGER.info("Starting Quarkus JWT integration benchmarks - Service: {}, Keycloak: {}, Output: {}",
                config.integrationServiceUrl().orElse(DEFAULT_SERVICE_URL),
                config.keycloakUrl().orElse(DEFAULT_KEYCLOAK_URL),
                getBenchmarkResultsDir());

        // Pre-initialize the shared TokenRepository instance
        initializeSharedTokenRepository();

        // Configure JMH options using modern configuration API
        config = config.toBuilder()
                .withIncludePattern(System.getProperty("jmh.include", "de\\.cuioss\\.jwt\\.quarkus\\.benchmark\\.benchmarks\\..*"))
                .withForks(BENCHMARK_FORKS)
                .withWarmupIterations(WARMUP_ITERATIONS)
                .withMeasurementIterations(MEASUREMENT_ITERATIONS)
                .withMeasurementTime(TimeValue.seconds(MEASUREMENT_TIME_SECONDS))
                .withWarmupTime(TimeValue.seconds(WARMUP_TIME_SECONDS))
                .withThreads(THREAD_COUNT)
                .withResultFile(getBenchmarkResultsDir() + BENCHMARK_RESULT_FILENAME)
                .withMetricsUrl(config.integrationServiceUrl().orElse(DEFAULT_SERVICE_URL))
                .build();

        Options options = config.toJmhOptions();

        // Run the benchmarks
        Collection<RunResult> results = new Runner(options).run();

        // Check if any benchmarks actually ran
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

        // Generate artifacts (badges, reports, metrics, GitHub Pages structure)
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        processor.processResults(results, getBenchmarkResultsDir(), BenchmarkType.INTEGRATION);

        // Process and download final metrics after successful benchmark execution
        processMetrics();

        LOGGER.info("Benchmarks completed successfully: {} benchmarks executed, artifacts generated in {}",
                results.size(), getBenchmarkResultsDir());
    }

    /**
     * Downloads and processes final cumulative metrics from Quarkus after benchmarks complete.
     * Uses QuarkusMetricsFetcher to download metrics and SimpleMetricsExporter to export them.
     * Also processes JMH benchmark results to create http-metrics.json.
     * 
     * @throws IOException if metrics processing fails
     */
    private static void processMetrics() throws IOException {
        var config = BenchmarkConfiguration.fromSystemProperties().build();
        String quarkusMetricsUrl = config.metricsUrl().orElse(DEFAULT_SERVICE_URL);
        String outputDirectory = getBenchmarkResultsDir();

        LOGGER.debug("Processing metrics from {} to {}", quarkusMetricsUrl, outputDirectory);

        // Use QuarkusMetricsFetcher to download metrics (this also saves raw metrics)
        QuarkusMetricsFetcher metricsFetcher = new QuarkusMetricsFetcher(quarkusMetricsUrl);

        // Use SimpleMetricsExporter to export JWT validation metrics
        SimpleMetricsExporter exporter = new SimpleMetricsExporter(outputDirectory, metricsFetcher);
        exporter.exportJwtValidationMetrics("JwtValidation", Instant.now());

        // Process JMH benchmark results to create http-metrics.json
        String benchmarkResultsFile = config.resultFile();
        MetricsPostProcessor metricsPostProcessor = new MetricsPostProcessor(benchmarkResultsFile, outputDirectory);
        metricsPostProcessor.parseAndExportHttpMetrics(Instant.now());
    }

    /**
     * Gets the benchmark results directory from system property or defaults to target/benchmark-results.
     *
     * @return the benchmark results directory path
     */
    private static String getBenchmarkResultsDir() {
        return System.getProperty(BENCHMARK_RESULTS_DIR_PROPERTY, DEFAULT_BENCHMARK_RESULTS_DIR);
    }

    /**
     * Initializes the shared TokenRepository instance that will be used by all benchmarks.
     * This avoids multiple initializations during benchmark setup phases.
     */
    private static void initializeSharedTokenRepository() {
        var benchConfig = BenchmarkConfiguration.fromSystemProperties().build();
        String keycloakUrl = benchConfig.keycloakUrl().orElse(DEFAULT_KEYCLOAK_SECURE_URL);

        TokenRepositoryConfig config = TokenRepositoryConfig.builder()
                .keycloakBaseUrl(keycloakUrl)
                .realm(KEYCLOAK_REALM)
                .clientId(KEYCLOAK_CLIENT_ID)
                .clientSecret(KEYCLOAK_CLIENT_SECRET)
                .username(KEYCLOAK_USERNAME)
                .password(KEYCLOAK_PASSWORD)
                .connectionTimeoutMs(CONNECTION_TIMEOUT_MS)
                .requestTimeoutMs(REQUEST_TIMEOUT_MS)
                .verifySsl(false)
                .tokenRefreshThresholdSeconds(TOKEN_REFRESH_THRESHOLD_SECONDS)
                .build();

        TokenRepository.initializeSharedInstance(config);
        LOGGER.debug("TokenRepository initialized with Keycloak at {}", keycloakUrl);
    }
}