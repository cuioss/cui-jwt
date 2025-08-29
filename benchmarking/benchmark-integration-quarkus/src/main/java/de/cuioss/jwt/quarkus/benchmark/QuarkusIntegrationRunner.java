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


    private static final CuiLogger LOGGER = new CuiLogger(QuarkusIntegrationRunner.class);

    private static final String DEFAULT_SERVICE_URL = "https://localhost:8443";
    private static final String DEFAULT_KEYCLOAK_SECURE_URL = "https://localhost:1443";

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
        // Configuration from Maven system properties:
        // - jmh.include: Pattern for benchmark classes to include
        // - jmh.forks, jmh.iterations, jmh.time, etc.: JMH execution parameters
        // - integration.service.url, keycloak.url: Service URLs
        // Output directory is fixed: target/benchmark-results
        // Result file is auto-generated as: target/benchmark-results/integration-result.json
        
        return BenchmarkConfiguration.builder()
                .withBenchmarkType(BenchmarkType.INTEGRATION)
                .withThroughputBenchmarkName("validateJwtThroughput")  // Explicit benchmark name
                .withLatencyBenchmarkName("validateJwtThroughput")     // Same benchmark has both modes
                .withIntegrationServiceUrl(getServiceUrl())
                .withKeycloakUrl(getKeycloakUrl())
                // Metrics URL is always the same as service URL
                .build();
    }

    @Override protected void beforeBenchmarks() {
        // Configure centralized logging to write to fixed benchmark-results directory
        BenchmarkLoggingSetup.configureLogging("target/benchmark-results");

        // Pre-initialize the shared TokenRepository instance
        initializeSharedTokenRepository();

        LOGGER.info("Quarkus JWT integration benchmarks starting - Service: {}, Keycloak: {}",
                getServiceUrl(), getKeycloakUrl());
    }

    @Override protected void afterBenchmarks(Collection<RunResult> results, BenchmarkConfiguration config) {
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
     */
    private void processMetrics(BenchmarkConfiguration config) {
        String quarkusMetricsUrl = getServiceUrl();  // Metrics always at same URL as service
        String outputDirectory = config.resultsDirectory();  // Fixed: target/benchmark-results

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
        String keycloakUrl = getKeycloakUrl();

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
        // Always use secure URL for Keycloak
        return System.getProperty("keycloak.url", DEFAULT_KEYCLOAK_SECURE_URL);
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