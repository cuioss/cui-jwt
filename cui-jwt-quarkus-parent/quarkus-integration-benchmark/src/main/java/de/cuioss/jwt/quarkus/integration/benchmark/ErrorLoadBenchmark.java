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
 * Error load benchmark for integration testing with various error rates.
 * This benchmark measures how efficiently the system handles different 
 * percentages of invalid/expired tokens in the request stream.
 * 
 * Matches the structure of ErrorLoadBenchmark from the micro-benchmark module
 * to ensure compatible scoring metrics.
 * 
 * Containers are managed by Maven lifecycle via exec-maven-plugin.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ErrorLoadBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(ErrorLoadBenchmark.class);

    private TokenRepositoryManager tokenManager;

    @Setup(Level.Trial)
    @SuppressWarnings("java:S2696") // Static field update is safe in JMH @Setup context
    public void setupEnvironment() throws TokenFetchException {
        LOGGER.info("🚀 Setting up error load benchmark...");

        // Container is already started by Maven exec-maven-plugin
        // Configure REST Assured to use the running application
        String baseUrl = BenchmarkConfiguration.getApplicationUrl();

        RestAssured.baseURI = baseUrl;
        RestAssured.useRelaxedHTTPSValidation();

        // Initialize token repository with real Keycloak tokens
        tokenManager = TokenRepositoryManager.getInstance();
        tokenManager.initialize();

        LOGGER.info("✅ Error load benchmark ready");
    }

    @TearDown(Level.Trial)
    public void teardownEnvironment() {
        // Container will be stopped by Maven exec-maven-plugin
        LOGGER.info("🛑 Error load benchmark completed");
    }

    /**
     * Benchmark with parameterized error rates.
     * This is used for error resilience scoring and testing various error conditions.
     * Must match the benchmark name pattern for performance score extraction.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void validateMixedTokens(Blackhole bh) {
        // Default to 0% error rate for baseline resilience measurement
        String token = tokenManager.getTokenByErrorRate(0);
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Benchmark with 10% error rate.
     * Tests system resilience under light error conditions.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void validateMixedTokens10PercentError(Blackhole bh) {
        String token = tokenManager.getTokenByErrorRate(10);
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Benchmark with 50% error rate.
     * Tests system resilience under moderate error conditions.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void validateMixedTokens50PercentError(Blackhole bh) {
        String token = tokenManager.getTokenByErrorRate(50);
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Benchmark with 90% error rate.
     * Tests system resilience under heavy error conditions.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void validateMixedTokens90PercentError(Blackhole bh) {
        String token = tokenManager.getTokenByErrorRate(90);
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Benchmark with 100% error rate.
     * Tests system resilience under complete error conditions.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void validateMixedTokens100PercentError(Blackhole bh) {
        String token = tokenManager.getTokenByErrorRate(100);
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }
}