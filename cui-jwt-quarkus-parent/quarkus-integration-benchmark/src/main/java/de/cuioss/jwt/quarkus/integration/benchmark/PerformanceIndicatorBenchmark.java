/**
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
 * Performance indicator benchmark for integration testing.
 * This benchmark provides the same performance categories and scoring as micro-benchmarks
 * but measures them in an end-to-end integration context.
 *
 * Uses the same weighted scoring formula:
 * Performance Score = (Throughput × 0.57) + (Latency_Inverted × 0.40) + (Error_Resilience × 0.03)
 *
 * Containers are managed by Maven lifecycle via exec-maven-plugin.
 */
@State(Scope.Benchmark)
public class PerformanceIndicatorBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(PerformanceIndicatorBenchmark.class);

    private TokenRepositoryManager tokenManager;


    @Setup(Level.Trial)
    @SuppressWarnings("java:S2696") // Static field update is safe in JMH @Setup context
    public void setupEnvironment() throws TokenFetchException {
        String baseUrl;
        LOGGER.info("🚀 Setting up performance indicator benchmark...");

        // Container is already started by Maven exec-maven-plugin
        // Configure REST Assured to use the running application
        baseUrl = BenchmarkConfiguration.getApplicationUrl();

        RestAssured.baseURI = baseUrl;
        RestAssured.useRelaxedHTTPSValidation();

        // Initialize token repository with real Keycloak tokens
        tokenManager = TokenRepositoryManager.getInstance();
        tokenManager.initialize();

        LOGGER.info("✅ Performance indicator benchmark ready");
    }

    @TearDown(Level.Trial)
    public void teardownEnvironment() {
        // Container will be stopped by Maven exec-maven-plugin
        LOGGER.info("🛑 Performance indicator benchmark completed");
    }

    /**
     * Throughput measurement - requests per second under maximum concurrent load.
     * Primary performance indicator (57% weight in scoring formula).
     * Must match: de.cuioss.jwt.validation.benchmark.PerformanceIndicatorBenchmark.measureThroughput
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
     * Average time measurement - single-threaded latency.
     * Latency performance indicator (40% weight in scoring formula).
     * Must match: de.cuioss.jwt.validation.benchmark.PerformanceIndicatorBenchmark.measureAverageTime
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
     * Sample time measurement - percentile analysis.
     * Provides detailed latency distribution for performance analysis.
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void measureSampleTime(Blackhole bh) {
        String token = tokenManager.getValidToken();
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Error resilience measurement - 0% error rate for baseline.
     * Error resilience performance indicator (3% weight in scoring formula).
     * This measures performance under ideal conditions (0% errors) as baseline.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void measureErrorResilience(Blackhole bh) {
        // Use 0% error rate for baseline resilience measurement (all valid tokens)
        String token = tokenManager.getTokenByErrorRate(0);

        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }


    /**
     * Calculates the weighted performance score using the same formula as the micro-benchmark module.
     * 
     * Formula: Performance Score = (Throughput × 0.57) + (Latency_Inverted × 0.40) + (Error_Resilience × 0.03)
     * 
     * @param throughputOpsPerSec Throughput in operations per second
     * @param avgTimeInMillis Average time in milliseconds (will be converted to ops/sec)
     * @param errorResilienceOpsPerSec Error resilience throughput in operations per second
     * @return Weighted performance score
     */
    public static double calculatePerformanceScore(double throughputOpsPerSec, double avgTimeInMillis, double errorResilienceOpsPerSec) {
        // Convert average time to operations per second (inverted metric)
        // Note: Integration benchmarks use milliseconds vs microseconds in micro-benchmarks
        double latencyOpsPerSec = 1_000.0 / avgTimeInMillis;

        // Weighted score: 57% throughput, 40% latency, 3% error resilience
        return (throughputOpsPerSec * 0.57) + (latencyOpsPerSec * 0.40) + (errorResilienceOpsPerSec * 0.03);
    }
}