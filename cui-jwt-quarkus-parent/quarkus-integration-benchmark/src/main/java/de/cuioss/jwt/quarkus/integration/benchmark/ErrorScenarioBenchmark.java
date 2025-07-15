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
 * Optimized error scenario benchmark for integration testing with reduced execution time.
 * <p>
 * This benchmark focuses on the most critical error scenarios in a containerized environment:
 * <ul>
 *   <li><strong>Basic Error Types</strong>: Invalid, expired, and malformed tokens</li>
 *   <li><strong>Mixed Error Load</strong>: Baseline (0% errors) and balanced (50% errors)</li>
 *   <li><strong>Missing Headers</strong>: Request validation edge cases</li>
 * </ul>
 * <p>
 * Optimizations applied:
 * <ul>
 *   <li>Reduced error percentages from 5 to 2 variants (0%, 50%)</li>
 *   <li>Streamlined error types to 3 essential categories</li>
 *   <li>Efficient token management with caching</li>
 *   <li>Single setup for all error scenarios</li>
 * </ul>
 * <p>
 * This benchmark replaces the functionality of the original ErrorLoadBenchmark
 * with faster execution while maintaining essential error handling performance insights.
 * <p>
 * Containers are managed by Maven lifecycle via exec-maven-plugin.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ErrorScenarioBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(ErrorScenarioBenchmark.class);

    private TokenRepositoryManager tokenManager;

    // Reduced error percentage variants: baseline (0%) and balanced (50%)
    @Param({"0", "50"})
    private int errorPercentage;

    @Setup(Level.Trial)
    @SuppressWarnings("java:S2696") // Static field update is safe in JMH @Setup context
    public void setupEnvironment() throws TokenFetchException {
        LOGGER.info("🚀 Setting up optimized error scenario benchmark...");

        // Container is already started by Maven exec-maven-plugin
        String baseUrl = BenchmarkConfiguration.getApplicationUrl();

        RestAssured.baseURI = baseUrl;
        RestAssured.useRelaxedHTTPSValidation();

        // Initialize token repository with real Keycloak tokens
        tokenManager = TokenRepositoryManager.getInstance();
        tokenManager.initialize();

        LOGGER.info("✅ Optimized error scenario benchmark ready");
    }

    @TearDown(Level.Trial)
    public void teardownEnvironment() {
        LOGGER.info("🛑 Optimized error scenario benchmark completed");
    }

    /**
     * Benchmarks validation of valid tokens (baseline performance).
     */
    @Benchmark
    public void validateValidToken(Blackhole bh) {
        String token = tokenManager.getValidToken();
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Benchmarks validation of invalid tokens.
     */
    @Benchmark
    public void validateInvalidToken(Blackhole bh) {
        String token = tokenManager.getInvalidToken();
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Benchmarks validation of expired tokens.
     */
    @Benchmark
    public void validateExpiredToken(Blackhole bh) {
        String token = tokenManager.getExpiredToken();
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Benchmarks missing authorization header handling.
     */
    @Benchmark
    public void validateMissingAuthHeader(Blackhole bh) {
        Response response = RestAssured.given()
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }

    /**
     * Benchmarks mixed error load scenarios with optimized error percentages.
     * <p>
     * Tests two scenarios:
     * <ul>
     *   <li><strong>0% errors</strong>: Baseline performance with only valid tokens</li>
     *   <li><strong>50% errors</strong>: Balanced mix of valid and invalid tokens</li>
     * </ul>
     */
    @Benchmark
    public void validateMixedErrorLoad(Blackhole bh) {
        String token = tokenManager.getTokenByErrorRate(errorPercentage);
        Response response = RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
        bh.consume(response);
    }
}