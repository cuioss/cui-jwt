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

import static de.cuioss.benchmarking.common.repository.TokenRepositoryConfig.requireProperty;

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

    private final String serviceUrl = requireProperty(
            System.getProperty("integration.service.url"),
            "Integration service URL",
            "integration.service.url"
    );
    private final String quarkusMetricsUrl = requireProperty(
            System.getProperty("quarkus.metrics.url"),
            "Quarkus metrics URL",
            "quarkus.metrics.url"
    );
    // Get token configuration
    private final TokenRepositoryConfig tokenConfig = TokenRepositoryConfig.fromProperties();
    // Get Keycloak URL from properties - checks both "token.keycloak.url" and "keycloak.url"
    private final String keycloakUrl = tokenConfig.getKeycloakBaseUrl();

    @Override protected BenchmarkConfiguration createConfiguration() {
        // Create integration configuration with required URLs
        IntegrationConfiguration integrationConfig = new IntegrationConfiguration(
                serviceUrl,
                keycloakUrl,
                quarkusMetricsUrl
        );

        return BenchmarkConfiguration.builder()
                .withBenchmarkType(BenchmarkType.INTEGRATION)
                .withThroughputBenchmarkName("validateJwtThroughput")
                .withLatencyBenchmarkName("validateJwtThroughput")
                .withIntegrationConfig(integrationConfig)
                .build();
    }

    @Override protected void beforeBenchmarks() {
        BenchmarkLoggingSetup.configureLogging("target/benchmark-results");

        LOGGER.info("Quarkus JWT integration benchmarks starting - Service: {}, Keycloak: {}",
                serviceUrl, keycloakUrl);
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
        String outputDirectory = config.resultsDirectory();

        LOGGER.debug("Processing metrics from {} to {}", serviceUrl, outputDirectory);

        // Use QuarkusMetricsFetcher to download metrics (this also saves raw metrics)
        QuarkusMetricsFetcher metricsFetcher = new QuarkusMetricsFetcher(serviceUrl);

        // Use SimpleMetricsExporter to export JWT validation metrics
        SimpleMetricsExporter exporter = new SimpleMetricsExporter(outputDirectory, metricsFetcher);
        exporter.exportJwtValidationMetrics("JwtValidation", Instant.now());

        // Process JMH benchmark results to create both http-metrics.json and quarkus-metrics.json
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
        new QuarkusIntegrationRunner().run();
    }
}