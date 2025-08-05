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

import de.cuioss.jwt.quarkus.benchmark.http.HttpClientFactory;
import de.cuioss.jwt.quarkus.benchmark.metrics.QuarkusMetricsFetcher;
import de.cuioss.jwt.quarkus.benchmark.metrics.SimpleMetricsExporter;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for all Quarkus benchmarks (with or without authentication).
 * Provides common setup, configuration, and utility methods.
 *
 * <p>For benchmarks that require JWT authentication, use {@link AbstractIntegrationBenchmark} instead.</p>
 *
 * <p>Benchmark execution parameters (iterations, threads, warmup, etc.) are configured dynamically
 * via {@link BenchmarkRunner} and {@link BenchmarkOptionsHelper} using system properties.</p>
 *
 * @since 1.0
 */
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public abstract class AbstractBaseBenchmark {
    
    static {
        // Set the logging manager to prevent JBoss LogManager errors in forked JVMs
        System.setProperty("java.util.logging.manager", "java.util.logging.LogManager");
    }

    private static final CuiLogger LOGGER = new CuiLogger(AbstractBaseBenchmark.class);

    protected String serviceUrl;
    protected String quarkusMetricsUrl;
    protected SimpleMetricsExporter metricsExporter;
    protected String benchmarkResultsDir;
    private HttpClient httpClient;

    /**
     * Setup method called once before all benchmark iterations.
     * Initializes HttpClient and metrics exporter.
     */
    @Setup(Level.Trial)
    public void setupBenchmark() {
        LOGGER.info("Setting up base benchmark");

        // Get configuration from system properties with correct docker-compose ports
        serviceUrl = BenchmarkOptionsHelper.getIntegrationServiceUrl("https://localhost:10443");
        quarkusMetricsUrl = BenchmarkOptionsHelper.getQuarkusMetricsUrl("https://localhost:10443");
        benchmarkResultsDir = System.getProperty("benchmark.results.dir", "target/benchmark-results");

        LOGGER.info("Service URL: {}", serviceUrl);
        LOGGER.info("Quarkus Metrics URL: {}", quarkusMetricsUrl);

        // Get cached HttpClient from factory (insecure for self-signed certs)
        httpClient = HttpClientFactory.getInsecureClient();
        LOGGER.debug("Using cached HttpClient from factory");

        // Initialize metrics exporter
        metricsExporter = new SimpleMetricsExporter(benchmarkResultsDir,
                new QuarkusMetricsFetcher(quarkusMetricsUrl));

        LOGGER.info("Base benchmark setup completed");
    }


    /**
     * Creates a basic HTTP request builder with common headers.
     *
     * @param path the URI to send the request to
     * @return configured request builder
     */
    protected HttpRequest.Builder createBaseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(serviceUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));
    }

    /**
     * Sends an HTTP request and returns the response.
     *
     * @param request the request to send
     * @return the HTTP response
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the operation is interrupted
     */
    protected HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }


    /**
     * Utility method to handle common error scenarios in benchmarks.
     *
     * @param response the response to check
     * @param expectedStatus the expected HTTP status code
     * @throws RuntimeException if the response status doesn't match expected
     */
    protected void validateResponse(HttpResponse<String> response, int expectedStatus) {
        if (response.statusCode() != expectedStatus) {
            throw new RuntimeException("Expected status %d but got %d. Response: %s".formatted(
                    expectedStatus, response.statusCode(), response.body()));
        }
    }

}