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

import java.util.concurrent.TimeUnit;

import static de.cuioss.jwt.quarkus.integration.benchmark.BenchmarkConstants.*;

/**
 * Integration benchmark for JWT token validation using containerized Quarkus application.
 * This benchmark measures end-to-end performance including HTTP communication,
 * container networking, and real JWT validation scenarios.
 *
 * Containers are managed by Maven lifecycle via exec-maven-plugin, similar to integration tests.
 */
@BenchmarkMode(Mode.All)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class IntegrationTokenValidationBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(IntegrationTokenValidationBenchmark.class);
    public static final String APPLICATION_JSON = "application/json";
    public static final String TOKEN_TEMPLATE = "{\"token\":\"%s\"}";

    private TokenRepositoryManager tokenManager;


    @Setup(Level.Trial)
    @SuppressWarnings("java:S2696") // Static field update is safe in JMH @Setup context
    public void setupEnvironment() throws TokenFetchException {
        String baseUrl;
        LOGGER.info("🚀 Setting up integration benchmark environment...");

        // Container is already started by Maven exec-maven-plugin
        // Configure REST Assured to use the running application
        baseUrl = BenchmarkConfiguration.getApplicationUrl();

        RestAssured.baseURI = baseUrl;
        RestAssured.useRelaxedHTTPSValidation();

        // Initialize token repository with real Keycloak tokens
        tokenManager = TokenRepositoryManager.getInstance();
        tokenManager.initialize();

        LOGGER.info("📊 %s", tokenManager.getStatistics());

        // Warmup - ensure services are responsive
        warmupServices();

        LOGGER.info("✅ Integration benchmark environment ready");
        LOGGER.info("📱 Application URL: " + baseUrl);
    }

    @TearDown(Level.Trial)
    public void teardownEnvironment() {
        // Container will be stopped by Maven exec-maven-plugin
        LOGGER.info("🛑 Integration benchmark completed");
    }

    private void warmupServices() throws TokenFetchException {
        LOGGER.info("🔥 Warming up services...");

        // Warmup application
        for (int i = 0; i < BenchmarkConfiguration.WARMUP_TOKEN_REQUESTS; i++) {
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

        // Warmup benchmark endpoints with real tokens
        for (int i = 0; i < 3; i++) {
            try {
                // Warmup blocking access token validation
                String warmupToken = tokenManager.getValidToken();
                RestAssured.given()
                        .header("Authorization", "Bearer " + warmupToken)
                        .when()
                        .post("/jwt/validate");

                // Warmup reactive access token validation
                RestAssured.given()
                        .header("Authorization", "Bearer " + warmupToken)
                        .when()
                        .post("/jwt/reactive/validate");

                // Warmup blocking ID token validation
                String warmupIdToken = tokenManager.getValidIdToken();
                RestAssured.given()
                        .contentType(APPLICATION_JSON)
                        .body(TOKEN_TEMPLATE.formatted(warmupIdToken))
                        .when()
                        .post("/jwt/validate/id-token");

                // Warmup reactive ID token validation
                RestAssured.given()
                        .contentType(APPLICATION_JSON)
                        .body(TOKEN_TEMPLATE.formatted(warmupIdToken))
                        .when()
                        .post("/jwt/reactive/validate/id-token");

                // Warmup blocking refresh token validation
                String warmupRefreshToken = tokenManager.getValidRefreshToken();
                RestAssured.given()
                        .contentType(APPLICATION_JSON)
                        .body(TOKEN_TEMPLATE.formatted(warmupRefreshToken))
                        .when()
                        .post("/jwt/validate/refresh-token");

                // Warmup reactive refresh token validation
                RestAssured.given()
                        .contentType(APPLICATION_JSON)
                        .body(TOKEN_TEMPLATE.formatted(warmupRefreshToken))
                        .when()
                        .post("/jwt/reactive/validate/refresh-token");
            } catch (Exception e) {
                LOGGER.debug("Warmup request failed (expected during startup): %s", e.getMessage());
            }
        }

        LOGGER.info("✅ Services warmed up");
    }

    /**
     * Benchmark valid token validation - primary performance metric.
     * This simulates the most common scenario of validating legitimate tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkValidTokenValidation() {
        String token = tokenManager.getValidToken();
        return RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
    }

    /**
     * Benchmark invalid token handling - error path performance.
     * This measures how efficiently the system handles validation failures.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkInvalidTokenValidation() {
        String token = tokenManager.getInvalidToken();
        return RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
    }

    /**
     * Benchmark expired token handling - time-based validation performance.
     * This measures how efficiently the system handles expired token validation.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkExpiredTokenValidation() {
        String token = tokenManager.getExpiredToken();
        return RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
    }

    /**
     * Benchmark average response time for valid tokens.
     * This measures latency characteristics under normal load.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Response benchmarkValidTokenLatency() {
        String token = tokenManager.getValidToken();
        return RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_VALIDATE_PATH);
    }

    /**
     * Benchmark health check endpoint - baseline performance.
     * This provides a reference point for container and network overhead.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkHealthCheck() {
        return RestAssured.given()
                .when()
                .get(HEALTH_CHECK_PATH);
    }

    /**
     * Benchmark valid ID token validation - ID token performance.
     * This measures performance of validating OpenID Connect ID tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkValidIdTokenValidation() {
        String idToken = tokenManager.getValidIdToken();
        return RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body(TOKEN_TEMPLATE.formatted(idToken))
                .when()
                .post(JWT_VALIDATE_ID_TOKEN_PATH);
    }

    /**
     * Benchmark valid refresh token validation - refresh token performance.
     * This measures performance of validating OAuth2 refresh tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkValidRefreshTokenValidation() {
        String refreshToken = tokenManager.getValidRefreshToken();
        return RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body(TOKEN_TEMPLATE.formatted(refreshToken))
                .when()
                .post(JWT_VALIDATE_REFRESH_TOKEN_PATH);
    }

    /**
     * Benchmark ID token latency - average response time for ID tokens.
     * This measures latency characteristics for ID token validation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Response benchmarkIdTokenLatency() {
        String idToken = tokenManager.getValidIdToken();
        return RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body(TOKEN_TEMPLATE.formatted(idToken))
                .when()
                .post(JWT_VALIDATE_ID_TOKEN_PATH);
    }

    /**
     * Benchmark refresh token latency - average response time for refresh tokens.
     * This measures latency characteristics for refresh token validation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Response benchmarkRefreshTokenLatency() {
        String refreshToken = tokenManager.getValidRefreshToken();
        return RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body(TOKEN_TEMPLATE.formatted(refreshToken))
                .when()
                .post(JWT_VALIDATE_REFRESH_TOKEN_PATH);
    }

    /**
     * Benchmark missing authorization header handling.
     * This measures error handling performance for malformed requests.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkMissingAuthHeader() {
        return RestAssured.given()
                .when()
                .post(JWT_VALIDATE_PATH);
    }

    // ===== REACTIVE ENDPOINT BENCHMARKS =====

    /**
     * Benchmark reactive valid token validation - primary reactive performance metric.
     * This tests the Mutiny-based reactive approach against virtual threads.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkReactiveValidTokenValidation() {
        String token = tokenManager.getValidToken();
        return RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_REACTIVE_VALIDATE_PATH);
    }

    /**
     * Benchmark reactive invalid token handling - reactive error path performance.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkReactiveInvalidTokenValidation() {
        String token = tokenManager.getInvalidToken();
        return RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_REACTIVE_VALIDATE_PATH);
    }

    /**
     * Benchmark reactive expired token handling - reactive time-based validation.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkReactiveExpiredTokenValidation() {
        String token = tokenManager.getExpiredToken();
        return RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_REACTIVE_VALIDATE_PATH);
    }

    /**
     * Benchmark reactive average response time for valid tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Response benchmarkReactiveValidTokenLatency() {
        String token = tokenManager.getValidToken();
        return RestAssured.given()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .when()
                .post(JWT_REACTIVE_VALIDATE_PATH);
    }

    /**
     * Benchmark reactive ID token validation - reactive ID token performance.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkReactiveValidIdTokenValidation() {
        String idToken = tokenManager.getValidIdToken();
        return RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body(TOKEN_TEMPLATE.formatted(idToken))
                .when()
                .post(JWT_REACTIVE_VALIDATE_ID_TOKEN_PATH);
    }

    /**
     * Benchmark reactive refresh token validation - reactive refresh token performance.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Response benchmarkReactiveValidRefreshTokenValidation() {
        String refreshToken = tokenManager.getValidRefreshToken();
        return RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body(TOKEN_TEMPLATE.formatted(refreshToken))
                .when()
                .post(JWT_REACTIVE_VALIDATE_REFRESH_TOKEN_PATH);
    }

    /**
     * Benchmark reactive ID token latency - reactive average response time for ID tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Response benchmarkReactiveIdTokenLatency() {
        String idToken = tokenManager.getValidIdToken();
        return RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body(TOKEN_TEMPLATE.formatted(idToken))
                .when()
                .post(JWT_REACTIVE_VALIDATE_ID_TOKEN_PATH);
    }

    /**
     * Benchmark reactive refresh token latency - reactive average response time for refresh tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Response benchmarkReactiveRefreshTokenLatency() {
        String refreshToken = tokenManager.getValidRefreshToken();
        return RestAssured.given()
                .contentType(APPLICATION_JSON)
                .body(TOKEN_TEMPLATE.formatted(refreshToken))
                .when()
                .post(JWT_REACTIVE_VALIDATE_REFRESH_TOKEN_PATH);
    }
}
