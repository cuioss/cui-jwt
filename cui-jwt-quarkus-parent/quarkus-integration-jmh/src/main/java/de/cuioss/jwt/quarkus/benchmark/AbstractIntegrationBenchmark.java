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
import de.cuioss.jwt.quarkus.benchmark.metrics.BenchmarkMetrics;
import de.cuioss.jwt.quarkus.benchmark.metrics.MetricsExporter;
import de.cuioss.jwt.quarkus.benchmark.repository.TokenRepository;
import de.cuioss.tools.logging.CuiLogger;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.specification.RequestSpecification;
import org.openjdk.jmh.annotations.*;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for all Quarkus integration benchmarks.
 * Provides common setup, configuration, and utility methods.
 * 
 * @author Generated
 * @since 1.0
 */
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(10)
public abstract class AbstractIntegrationBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(AbstractIntegrationBenchmark.class);

    protected String serviceUrl;
    protected String keycloakUrl;
    protected String quarkusMetricsUrl;
    protected TokenRepository tokenRepository;
    protected MetricsExporter metricsExporter;
    protected String benchmarkResultsDir;

    /**
     * Setup method called once before all benchmark iterations.
     * Initializes RestAssured configuration, token repository, and metrics exporter.
     */
    @Setup(Level.Trial)
    public void setupBenchmark() {
        LOGGER.info("Setting up integration benchmark");

        // Get configuration from system properties with correct docker-compose ports
        serviceUrl = BenchmarkOptionsHelper.getIntegrationServiceUrl("https://localhost:10443");
        keycloakUrl = BenchmarkOptionsHelper.getKeycloakUrl("https://localhost:1443");
        quarkusMetricsUrl = BenchmarkOptionsHelper.getQuarkusMetricsUrl("https://localhost:10443");
        benchmarkResultsDir = System.getProperty("benchmark.results.dir", "target/benchmark-results");

        LOGGER.info("Service URL: {}", serviceUrl);
        LOGGER.info("Keycloak URL: {}", keycloakUrl);
        LOGGER.info("Quarkus Metrics URL: {}", quarkusMetricsUrl);

        // Configure RestAssured for high-throughput testing
        configureRestAssured();

        // Initialize token repository
        initializeTokenRepository();

        // Initialize metrics exporter
        metricsExporter = new MetricsExporter(quarkusMetricsUrl, benchmarkResultsDir);

        LOGGER.info("Benchmark setup completed");
    }

    /**
     * Teardown method called once after all benchmark iterations.
     * Exports final metrics for analysis.
     */
    @TearDown(Level.Trial)
    public void teardownBenchmark() {
        LOGGER.info("Tearing down integration benchmark");

        try {
            // Export final metrics
            exportMetrics();
        } catch (Exception e) {
            LOGGER.error("Failed to export metrics during teardown", e);
        }

        LOGGER.info("Benchmark teardown completed");
    }

    /**
     * Configures RestAssured with optimized settings for high-throughput testing.
     * Includes connection pooling and SSL configuration for self-signed certificates.
     */
    private void configureRestAssured() {
        LOGGER.debug("Configuring RestAssured for high-throughput testing");

        RestAssuredConfig config = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        // Connection pool configuration for high-throughput
                        .setParam("http.connection-manager.max-total", 200)
                        .setParam("http.connection-manager.max-per-route", 100)
                        // Connection timeouts
                        .setParam("http.connection.timeout", 5000)
                        .setParam("http.socket.timeout", 30000)
                        // Keep-alive strategy
                        .setParam("http.connection.stalecheck", true)
                        .setParam("http.keepalive.timeout", 30000))
                // SSL configuration for self-signed certificates
                .sslConfig(SSLConfig.sslConfig().relaxedHTTPSValidation());

        RestAssured.config = config;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        LOGGER.debug("RestAssured configured successfully");
    }

    /**
     * Initializes the token repository with configuration for testing scenarios.
     */
    private void initializeTokenRepository() {
        LOGGER.debug("Initializing token repository");

        TokenRepositoryConfig config = TokenRepositoryConfig.builder()
                .keycloakBaseUrl(keycloakUrl)
                .realm("benchmark")
                .clientId("benchmark-client")
                .clientSecret("benchmark-secret")
                .username("benchmark-user")
                .password("benchmark-password")
                .tokenPoolSize(100)  // Configure for ~10% cache hit ratio
                .connectionTimeoutMs(5000)
                .requestTimeoutMs(10000)
                .verifySsl(false)
                .tokenRefreshThresholdSeconds(300)
                .build();

        tokenRepository = new TokenRepository(config);

        LOGGER.info("Token repository initialized with {} tokens", tokenRepository.getTokenPoolSize());
    }

    /**
     * Creates a basic REST request specification with common headers.
     * 
     * @return configured request specification
     */
    protected RequestSpecification createBaseRequest() {
        return RestAssured
                .given()
                .baseUri(serviceUrl)
                .contentType("application/json")
                .accept("application/json");
    }

    /**
     * Creates an authenticated REST request with a JWT token.
     * 
     * @param token the JWT token to use for authorization
     * @return configured request specification with Authorization header
     */
    protected RequestSpecification createAuthenticatedRequest(String token) {
        return createBaseRequest()
                .header("Authorization", "Bearer " + token);
    }

    /**
     * Creates an authenticated REST request using the next token from the pool.
     * 
     * @return configured request specification with Authorization header
     */
    protected RequestSpecification createAuthenticatedRequest() {
        return createAuthenticatedRequest(tokenRepository.getNextToken());
    }

    /**
     * Exports metrics for the current benchmark.
     * Subclasses should override getBenchmarkName() to provide specific names.
     */
    protected void exportMetrics() {
        try {
            String benchmarkName = getBenchmarkName();
            BenchmarkMetrics.BenchmarkMetadata metadata = createBenchmarkMetadata();
            
            metricsExporter.exportMetrics(benchmarkName, metadata);
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
     * Utility method to handle common error scenarios in benchmarks.
     * 
     * @param response the response to check
     * @param expectedStatus the expected HTTP status code
     * @throws RuntimeException if the response status doesn't match expected
     */
    protected void validateResponse(io.restassured.response.Response response, int expectedStatus) {
        if (response.getStatusCode() != expectedStatus) {
            throw new RuntimeException(String.format(
                    "Expected status %d but got %d. Response: %s", 
                    expectedStatus, response.getStatusCode(), response.getBody().asString()));
        }
    }
}