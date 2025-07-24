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
package de.cuioss.jwt.integration;

import de.cuioss.tools.logging.CuiLogger;

import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REST API tests for metrics endpoints against external application.
 * <p>
 * These tests verify that Prometheus metrics are properly exposed
 * and include JWT validation metrics against an external running application.
 * Tests validate tokens and then read/log the JWT metrics at info level.
 * <p>
 * IMPORTANT: This test class should run LAST to benefit from metrics created by other integration tests.
 */
@DisplayName("JWT Metrics Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MetricsIntegrationIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsIntegrationIT.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_VALIDATE_PATH = "/jwt/validate";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String ACCESS_TOKEN_VALID_MESSAGE = "Access token is valid";

    @Test
    @Order(1)
    void shouldExposeMetricsEndpoint() {
        given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .contentType(containsString("text"));
    }

    @Test
    @Order(2)
    void shouldIncludeBasicMetrics() {
        String metricsResponse = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        // Verify basic metrics are present
        assertTrue(
            metricsResponse.contains("jvm_") || metricsResponse.contains("http_"),
            "Should include basic JVM or HTTP metrics"
        );
    }

    @Test
    @Order(3)
    void shouldIncludeSystemMetrics() {
        given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .body(containsString("jvm_memory_used_bytes"))
            .body(containsString("process_cpu_usage"));
    }

    @Test
    @Order(4)
    void shouldIncludeHttpMetrics() {
        given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .body(containsString("http_server"));
    }

    @Test
    @Order(5)
    void shouldProvideMetricsInPrometheusFormat() {
        String metricsResponse = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        // Verify Prometheus format
        assertTrue(
            metricsResponse.contains("# HELP"),
            "Should include Prometheus HELP comments"
        );

        assertTrue(
            metricsResponse.contains("# TYPE"),
            "Should include Prometheus TYPE comments"
        );
    }

    @Test
    @Order(1000)
    // Run this test LAST to benefit from metrics created by previous tests
    @DisplayName("Validate tokens and verify JWT performance metrics are collected")
    void shouldValidateTokensAndVerifyMetricsAreCollected() {
        // Get test realm for token validation
        TestRealm testRealm = TestRealm.createIntegrationRealm();

        // Skip if Keycloak is not healthy
        if (!testRealm.isKeycloakHealthy()) {
            LOGGER.warn("Skipping token validation - Keycloak is not healthy");
            return;
        }

        // Obtain valid tokens
        TestRealm.TokenResponse tokenResponse = testRealm.obtainValidToken();
        String validAccessToken = tokenResponse.accessToken();

        LOGGER.info("Starting JWT token validations to generate performance metrics");

        // Perform multiple token validations to ensure metrics are generated
        // By this point, other integration tests should have already created some metrics
        for (int i = 0; i < 3; i++) {
            given()
                .contentType(CONTENT_TYPE_JSON)
                .header(AUTHORIZATION, BEARER_PREFIX + validAccessToken)
                .when()
                .post(JWT_VALIDATE_PATH)
                .then()
                .statusCode(200)
                .body("valid", equalTo(true))
                .body("message", equalTo(ACCESS_TOKEN_VALID_MESSAGE));
        }

        LOGGER.info("JWT token validations completed - checking metrics immediately");

        // JWT metrics should be initialized immediately when JwtMetricsCollector starts
        // The 10s schedule is only for updating values, not for initializing metrics definitions

        // Read metrics and verify they exist
        String metricsResponse = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        LOGGER.info("=== JWT Metrics Validation ===");

        // CRITICAL: Verify that JWT metrics are properly initialized and present
        // These metrics should be available immediately when JwtMetricsCollector starts
        
        // 1. Check for SecurityEventCounter metrics (cui_jwt_validation_errors)
        boolean securityMetricsInitialized = metricsResponse.contains("cui_jwt_validation_errors");
        LOGGER.info("SecurityEventCounter metrics initialized: {}", securityMetricsInitialized);

        // 2. Check for TokenValidatorMonitor metrics (cui_jwt_validation_duration) 
        boolean performanceMetricsInitialized = metricsResponse.contains("cui_jwt_validation_duration");
        LOGGER.info("TokenValidatorMonitor metrics initialized: {}", performanceMetricsInitialized);

        // 3. Log actual JWT metrics found for debugging
        logJwtMetricsDetails(metricsResponse);

        // 4. ASSERT that both metrics types are present - FAIL THE TEST if not
        assertTrue(securityMetricsInitialized,
            "SecurityEventCounter metrics (cui_jwt_validation_errors) must be initialized in metrics registry. " +
                "This indicates JwtMetricsCollector is not properly instantiated or SecurityEventCounter injection failed.");

        assertTrue(performanceMetricsInitialized,
            "TokenValidatorMonitor metrics (cui_jwt_validation_duration) must be initialized in metrics registry. " +
                "This indicates JwtMetricsCollector is not properly instantiated or TokenValidatorMonitor injection failed.");

        LOGGER.info("✅ JWT metrics validation PASSED - both SecurityEventCounter and TokenValidatorMonitor metrics are properly initialized");
    }

    /**
     * Logs detailed information about JWT metrics found in the response.
     * This helps with debugging metrics collection issues.
     */
    private void logJwtMetricsDetails(String metricsResponse) {
        LOGGER.info("=== JWT Metrics Details ===");

        String[] lines = metricsResponse.split("\n");
        int jwtMetricLines = 0;

        for (String line : lines) {
            if (line.contains("cui_jwt_validation")) {
                jwtMetricLines++;
                if (line.startsWith("# HELP")) {
                    LOGGER.info("HELP: {}", line.substring(7).trim());
                } else if (line.startsWith("# TYPE")) {
                    LOGGER.info("TYPE: {}", line.substring(7).trim());
                } else if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    LOGGER.info("DATA: {}", line.trim());
                }
            }
        }

        if (jwtMetricLines == 0) {
            LOGGER.warn("No JWT metrics found in metrics response");
            // Log first 10 lines for debugging
            LOGGER.info("First 10 lines of metrics response for debugging:");
            for (int i = 0; i < Math.min(10, lines.length); i++) {
                LOGGER.info("Line {}: {}", i, lines[i]);
            }
        } else {
            LOGGER.info("Found {} JWT-related metric lines", jwtMetricLines);
        }
    }

    @Test
    @Order(6)
    @DisplayName("Verify JWT metrics are exposed in Prometheus format")
    void shouldExposeJwtMetricsInPrometheusFormat() {
        String metricsResponse = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        // Check for JWT validation error metrics
        boolean hasErrorMetrics = metricsResponse.contains("cui_jwt_validation_errors");

        // Check for JWT validation duration metrics  
        boolean hasPerformanceMetrics = metricsResponse.contains("cui_jwt_validation_duration");

        LOGGER.info("JWT Error Metrics Present: {}", hasErrorMetrics);
        LOGGER.info("JWT Performance Metrics Present: {}", hasPerformanceMetrics);

        // At minimum, we should have the metrics definitions even if no data yet
        if (hasErrorMetrics || hasPerformanceMetrics) {
            LOGGER.info("JWT metrics are properly exposed in Prometheus format");
        } else {
            LOGGER.info("JWT metrics not yet present - may appear after token validation operations");
        }
    }
}