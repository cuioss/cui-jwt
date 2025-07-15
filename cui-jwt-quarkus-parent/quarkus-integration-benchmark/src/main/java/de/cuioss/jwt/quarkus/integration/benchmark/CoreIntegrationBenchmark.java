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
package de.cuioss.jwt.quarkus.integration.benchmark;

import de.cuioss.jwt.quarkus.integration.config.BenchmarkConfiguration;
import de.cuioss.jwt.quarkus.integration.token.TokenFetchException;
import de.cuioss.jwt.quarkus.integration.token.TokenRepositoryManager;
import de.cuioss.tools.logging.CuiLogger;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

import static de.cuioss.jwt.quarkus.integration.benchmark.BenchmarkConstants.*;

/**
 * Consolidated core integration benchmark that measures essential JWT validation performance
 * in a containerized Quarkus environment with real Keycloak integration.
 * <p>
 * This benchmark combines the most critical integration performance measurements:
 * <ul>
 *   <li><strong>Throughput</strong>: Operations per second under concurrent load</li>
 *   <li><strong>Average Time</strong>: End-to-end latency including network overhead</li>
 *   <li><strong>Error Handling</strong>: Performance under error conditions</li>
 *   <li><strong>Mixed Load</strong>: Resilience testing with error percentage scenarios</li>
 * </ul>
 * <p>
 * Optimizations applied:
 * <ul>
 *   <li>Reduced from 11 to 6 essential benchmark methods</li>
 *   <li>Simplified token management with efficient caching</li>
 *   <li>Streamlined error scenarios (0% and 50% error rates)</li>
 *   <li>Consolidated concurrent and sequential testing</li>
 * </ul>
 * <p>
 * This benchmark replaces the functionality of IntegrationTokenValidationBenchmark,
 * ConcurrentIntegrationBenchmark, and PerformanceIndicatorBenchmark with faster execution
 * while maintaining essential integration performance insights.
 * <p>
 * Containers are managed by Maven lifecycle via exec-maven-plugin.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class CoreIntegrationBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(CoreIntegrationBenchmark.class);
    public static final String APPLICATION_JSON = "application/json";
    public static final String TOKEN_TEMPLATE = "{\"token\":\"%s\"}";

    private TokenRepositoryManager tokenManager;

    @Setup(Level.Trial)
    @SuppressWarnings("java:S2696") // Static field update is safe in JMH @Setup context
    public void setupEnvironment() throws TokenFetchException {
        LOGGER.info("🚀 Setting up core integration benchmark environment...");

        // Container is already started by Maven exec-maven-plugin
        String baseUrl = BenchmarkConfiguration.getApplicationUrl();

        RestAssured.baseURI = baseUrl;
        RestAssured.useRelaxedHTTPSValidation();

        // Initialize token repository with real Keycloak tokens
        tokenManager = TokenRepositoryManager.getInstance();
        tokenManager.initialize();

        LOGGER.info("📊 %s", tokenManager.getStatistics());

        // Streamlined warmup - only essential endpoints
        warmupServices();

        LOGGER.info("✅ Core integration benchmark ready");
        LOGGER.info("📱 Application URL: " + baseUrl);
    }

    @TearDown(Level.Trial)
    public void teardownEnvironment() {
        LOGGER.info("🛑 Core integration benchmark completed");
    }

    /**
     * Streamlined warmup focusing on essential endpoints.
     */
    private void warmupServices() throws TokenFetchException {
        LOGGER.info("🔥 Warming up core services...");

        // Warmup application health check
        for (int i = 0; i < 3; i++) {
            try {
                Response response = RestAssured.given()
                        .when()
                        .get("/q/health/live");
                if (response.statusCode() == 200) {
                    break;
                }
            } catch (RuntimeException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TokenFetchException("Warmup interrupted", ie);
                }
            }
        }

        // Warmup main validation endpoint
        for (int i = 0; i < 2; i++) {
            try {
                String warmupToken = tokenManager.getValidToken();
                RestAssured.given()
                        .header("Authorization", "Bearer " + warmupToken)
                        .when()
                        .post("/jwt/validate");
            } catch (Exception e) {
                LOGGER.debug("Warmup request failed (expected during startup): %s", e.getMessage());
            }
        }

        LOGGER.info("✅ Core services warmed up");
    }

    /**
     * Measures token validation throughput under concurrent load.
     * Primary performance indicator (57% weight in scoring).
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void measureThroughput(Blackhole bh) {
        String token = tokenManager.getValidToken();
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Measures average response time for token validation.
     * Latency performance indicator (40% weight in scoring).
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void measureAverageTime(Blackhole bh) {
        String token = tokenManager.getValidToken();
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Measures error handling performance with invalid tokens.
     * Tests system resilience under error conditions.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void measureErrorHandling(Blackhole bh) {
        String token = tokenManager.getInvalidToken();
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Measures mixed load performance with baseline (0% errors).
     * Error resilience performance indicator (3% weight in scoring).
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void measureErrorResilience(Blackhole bh) {
        // Use 0% error rate for baseline resilience measurement
        String token = tokenManager.getTokenByErrorRate(0);
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Measures mixed load performance with balanced errors (50% error rate).
     * Tests system stability under moderate error conditions.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void measureMixedLoad(Blackhole bh) {
        // Use 50% error rate for balanced error testing
        String token = tokenManager.getTokenByErrorRate(50);
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Measures health check baseline performance.
     * Provides reference point for container and network overhead.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void measureBaselinePerformance(Blackhole bh) {
        Response response = RestAssured.given()
                .when()
                .get(HEALTH_CHECK_PATH);
        bh.consume(response);
    }

    /**
     * Calculates the weighted performance score using the same formula as micro-benchmarks.
     * <p>
     * Formula: Performance Score = (Throughput × 0.57) + (Latency_Inverted × 0.40) + (Error_Resilience × 0.03)
     * <p>
     * Note: Integration benchmarks use milliseconds vs microseconds in micro-benchmarks.
     *
     * @param throughputOpsPerSec Throughput in operations per second
     * @param avgTimeInMillis Average time in milliseconds
     * @param errorResilienceOpsPerSec Error resilience throughput in operations per second
     * @return Weighted performance score
     */
    public static double calculatePerformanceScore(double throughputOpsPerSec, double avgTimeInMillis, double errorResilienceOpsPerSec) {
        // Convert average time to operations per second (inverted metric)
        double latencyOpsPerSec = 1_000.0 / avgTimeInMillis;
        
        // Weighted score: 57% throughput, 40% latency, 3% error resilience
        return (throughputOpsPerSec * 0.57) + (latencyOpsPerSec * 0.40) + (errorResilienceOpsPerSec * 0.03);
    }
}