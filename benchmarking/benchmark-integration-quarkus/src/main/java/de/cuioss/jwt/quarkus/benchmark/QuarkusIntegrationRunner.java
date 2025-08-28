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
import de.cuioss.benchmarking.common.metrics.QuarkusMetricsFetcher;
import de.cuioss.benchmarking.common.repository.TokenRepository;
import de.cuioss.benchmarking.common.repository.TokenRepositoryConfig;
import de.cuioss.benchmarking.common.runner.AbstractBenchmarkRunner;
import de.cuioss.benchmarking.common.util.BenchmarkLoggingSetup;
import de.cuioss.jwt.quarkus.benchmark.metrics.MetricsPostProcessor;
import de.cuioss.jwt.quarkus.benchmark.metrics.SimpleMetricsExporter;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.RunnerException;
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
public class QuarkusIntegrationRunner extends AbstractBenchmarkRunner {

    static {
        // Set the logging manager as early as possible to prevent JBoss LogManager errors
        System.setProperty("java.util.logging.manager", "java.util.logging.LogManager");
    }

    private static final CuiLogger LOGGER = new CuiLogger(QuarkusIntegrationRunner.class);

    private static final String DEFAULT_SERVICE_URL = "https://localhost:8443";
    private static final String DEFAULT_KEYCLOAK_URL = "http://localhost:8080";
    private static final String DEFAULT_KEYCLOAK_SECURE_URL = "https://localhost:1443";

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

    @Override protected BenchmarkConfiguration createConfiguration() {
        String outputDir = System.getProperty("benchmark.results.dir", "target/benchmark-results");
        String includePattern = System.getProperty("jmh.include", "de\\.cuioss\\.jwt\\.quarkus\\.benchmark\\.benchmarks\\..*");

        return BenchmarkConfiguration.builder()
                .withBenchmarkType(BenchmarkType.INTEGRATION)
                .withIncludePattern(includePattern)
                .withResultsDirectory(outputDir)
                .withResultFile(outputDir + "/integration-benchmark-result.json")
                .withThroughputBenchmarkName("validateJwtThroughput")  // Explicit benchmark name
                .withLatencyBenchmarkName("validateJwtThroughput")     // Same benchmark has both modes
                .withIntegrationServiceUrl(getServiceUrl())
                .withKeycloakUrl(getKeycloakUrl())
                .withMetricsUrl(getServiceUrl())
                .withForks(BENCHMARK_FORKS)
                .withWarmupIterations(WARMUP_ITERATIONS)
                .withMeasurementIterations(MEASUREMENT_ITERATIONS)
                .withMeasurementTime(TimeValue.seconds(MEASUREMENT_TIME_SECONDS))
                .withWarmupTime(TimeValue.seconds(WARMUP_TIME_SECONDS))
                .withThreads(THREAD_COUNT)
                .build();
    }

    @Override protected void beforeBenchmarks() {
        // Configure logging to write to benchmark-results directory
        // This captures all console output (System.out/err and JMH) to both console and file
        String outputDir = System.getProperty("benchmark.results.dir", "target/benchmark-results");
        BenchmarkLoggingSetup.configureLogging(outputDir);

        // Pre-initialize the shared TokenRepository instance
        initializeSharedTokenRepository();

        LOGGER.info("Quarkus JWT integration benchmarks starting - Service: {}, Keycloak: {}",
                getServiceUrl(), getKeycloakUrl());
    }

    @Override protected void afterBenchmarks(Collection<RunResult> results, BenchmarkConfiguration config) throws IOException {
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

        // Process and download final metrics after successful benchmark execution
        processMetrics(config);
    }


    /**
     * Downloads and processes final cumulative metrics from Quarkus after benchmarks complete.
     * Uses QuarkusMetricsFetcher to download metrics and SimpleMetricsExporter to export them.
     * Also processes JMH benchmark results to create http-metrics.json.
     *
     * @param config the benchmark configuration
     * @throws IOException if metrics processing fails
     */
    private void processMetrics(BenchmarkConfiguration config) {
        String quarkusMetricsUrl = config.metricsUrl().orElse(getServiceUrl());
        String outputDirectory = config.resultsDirectory();

        LOGGER.debug("Processing metrics from {} to {}", quarkusMetricsUrl, outputDirectory);

        // Use QuarkusMetricsFetcher to download metrics (this also saves raw metrics)
        QuarkusMetricsFetcher metricsFetcher = new QuarkusMetricsFetcher(quarkusMetricsUrl);

        // Use SimpleMetricsExporter to export JWT validation metrics
        SimpleMetricsExporter exporter = new SimpleMetricsExporter(outputDirectory, metricsFetcher);
        exporter.exportJwtValidationMetrics("JwtValidation", Instant.now());

        // Process JMH benchmark results to create both http-metrics.json and quarkus-metrics.json
        String benchmarkResultsFile = config.resultFile();
        MetricsPostProcessor metricsPostProcessor = new MetricsPostProcessor(benchmarkResultsFile, outputDirectory);
        metricsPostProcessor.parseAndExportAllMetrics(Instant.now());
    }

    /**
     * Initializes the shared TokenRepository instance that will be used by all benchmarks.
     * This avoids multiple initializations during benchmark setup phases.
     */
    private void initializeSharedTokenRepository() {
        String keycloakUrl = getKeycloakSecureUrl();

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

    private String getServiceUrl() {
        return System.getProperty("integration.service.url", DEFAULT_SERVICE_URL);
    }

    private String getKeycloakUrl() {
        return System.getProperty("keycloak.url", DEFAULT_KEYCLOAK_URL);
    }

    private String getKeycloakSecureUrl() {
        // Use secure URL if provided, otherwise use regular URL
        String url = System.getProperty("keycloak.url", DEFAULT_KEYCLOAK_SECURE_URL);
        // If it's the default insecure URL, use the secure version
        if (DEFAULT_KEYCLOAK_URL.equals(url)) {
            return DEFAULT_KEYCLOAK_SECURE_URL;
        }
        return url;
    }

    /**
     * Main method to run all integration benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws IOException if I/O operations fail
     * @throws RunnerException if benchmark execution fails
     */
    public static void main(String[] args) throws IOException, RunnerException {
        new QuarkusIntegrationRunner().run();
    }
}