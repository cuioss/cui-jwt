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

import de.cuioss.jwt.quarkus.benchmark.metrics.BenchmarkMetrics;
import de.cuioss.jwt.quarkus.benchmark.metrics.QuarkusMetricsFetcher;
import de.cuioss.jwt.quarkus.benchmark.metrics.SimpleMetricsExporter;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.annotations.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for all Quarkus benchmarks (with or without authentication).
 * Provides common setup, configuration, and utility methods.
 * 
 * <p>For benchmarks that require JWT authentication, use {@link AbstractIntegrationBenchmark} instead.</p>
 * 
 * @since 1.0
 */
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(10)
public abstract class AbstractBaseBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(AbstractBaseBenchmark.class);

    protected String serviceUrl;
    protected String quarkusMetricsUrl;
    protected SimpleMetricsExporter metricsExporter;
    protected String benchmarkResultsDir;
    protected HttpClient httpClient;

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

        // Configure HttpClient for high-throughput testing
        configureHttpClient();

        // Initialize metrics exporter
        metricsExporter = new SimpleMetricsExporter(benchmarkResultsDir,
                new QuarkusMetricsFetcher(quarkusMetricsUrl));

        // Clear metrics once before starting the benchmark
        clearMetrics();

        LOGGER.info("Base benchmark setup completed");
    }

    /**
     * Teardown method called once after all benchmark iterations.
     * Exports final metrics for analysis.
     */
    @TearDown(Level.Trial)
    public void teardownBenchmark() {
        LOGGER.info("Tearing down base benchmark");

        try {
            // Export final metrics
            exportMetrics();
        } catch (Exception e) {
            LOGGER.error("Failed to export metrics during teardown", e);
        }

        LOGGER.info("Base benchmark teardown completed");
    }

    /**
     * Configures HttpClient with optimized settings for high-throughput testing.
     * Includes SSL configuration for self-signed certificates.
     */
    private void configureHttpClient() {
        LOGGER.debug("Configuring HttpClient for high-throughput testing");

        try {
            // Create SSL context that trusts all certificates (for self-signed)
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllManager()}, null);

            // Build HttpClient with optimized settings
            httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .sslContext(sslContext)
                    .build();

            LOGGER.debug("HttpClient configured successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure HttpClient", e);
        }
    }

    /**
     * Creates a basic HTTP request builder with common headers.
     * 
     * @param uri the URI to send the request to
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
     * Exports metrics for the current benchmark.
     * Subclasses should override getBenchmarkName() to provide specific names.
     */
    protected void exportMetrics() {
        try {
            String benchmarkName = getBenchmarkName();

            metricsExporter.exportJwtValidationMetrics(benchmarkName, Instant.now());
            LOGGER.info("Metrics exported for benchmark: {}", benchmarkName);
        } catch (Exception e) {
            LOGGER.error("Failed to export metrics", e);
        }
    }

    /**
     * Returns the name of this benchmark for metrics identification.
     * Subclasses should override this method.
     * 
     * @return the benchmark name
     */
    protected String getBenchmarkName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Creates benchmark metadata for metrics export.
     * 
     * @return metadata about the benchmark execution
     */
    private BenchmarkMetrics.BenchmarkMetadata createBenchmarkMetadata() {
        return BenchmarkMetrics.BenchmarkMetadata.builder()
                .threadCount(BenchmarkOptionsHelper.getThreadCount(10))
                .warmupDurationSeconds(1) // Based on @Warmup annotation
                .measurementDurationSeconds(5) // Based on @Measurement annotation
                .iterations(2) // Based on @Measurement annotation
                .serviceUrl(serviceUrl)
                .jvmInfo(System.getProperty("java.version") + " " +
                        System.getProperty("java.vm.name") + " " +
                        System.getProperty("java.vm.version"))
                .systemInfo(System.getProperty("os.name") + " " +
                        System.getProperty("os.version") + " " +
                        System.getProperty("os.arch") +
                        " (Processors: " + Runtime.getRuntime().availableProcessors() + ")")
                .build();
    }

    /**
     * Clears all JWT metrics by calling the metric_clear endpoint.
     * This ensures each benchmark run starts with a clean state.
     */
    protected void clearMetrics() {
        try {
            HttpRequest request = createBaseRequest("/jwt/metric_clear")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            
            HttpResponse<String> response = sendRequest(request);

            if (response.statusCode() != 200) {
                LOGGER.warn("Failed to clear metrics - Status: {}, Response: {}",
                        response.statusCode(), response.body());
            } else {
                LOGGER.debug("Metrics cleared successfully");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to clear metrics", e);
            // Don't fail the benchmark if metrics clearing fails
        }
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
    
    /**
     * Trust manager that accepts all certificates (for testing only).
     */
    private static class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Accept all
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Accept all
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}