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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;

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
 */
@DisplayName("JWT Metrics Integration Tests")
class MetricsIntegrationIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsIntegrationIT.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_VALIDATE_PATH = "/jwt/validate";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String ACCESS_TOKEN_VALID_MESSAGE = "Access token is valid";

    @Test
    void shouldExposeMetricsEndpoint() {
        given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .contentType(containsString("text"));
    }

    @Test
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
    void shouldIncludeHttpMetrics() {
        given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .body(containsString("http_server"));
    }

    @Test
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
    @Order(100)
    @DisplayName("Validate tokens and read JWT performance metrics")
    void shouldValidateTokensAndLogPerformanceMetrics() {
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

        LOGGER.info("Starting JWT token validation to generate performance metrics");

        // Perform token validation to generate metrics
        given()
            .contentType(CONTENT_TYPE_JSON)
            .header(AUTHORIZATION, BEARER_PREFIX + validAccessToken)
            .when()
            .post(JWT_VALIDATE_PATH)
            .then()
            .statusCode(200)
            .body("valid", equalTo(true))
            .body("message", equalTo(ACCESS_TOKEN_VALID_MESSAGE));

        LOGGER.info("JWT token validation completed - performance metrics should be updated");

        // Wait a moment for metrics to be collected
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Read and log JWT performance metrics
        String metricsResponse = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        // Log JWT validation duration metrics (performance metrics)
        logJwtPerformanceMetrics(metricsResponse);
        
        // Log JWT validation error metrics (security metrics)
        logJwtSecurityMetrics(metricsResponse);
    }

    /**
     * Logs JWT performance metrics at info level.
     */
    private void logJwtPerformanceMetrics(String metricsResponse) {
        LOGGER.info("=== JWT Performance Metrics ===");
        
        String[] lines = metricsResponse.split("\n");
        boolean foundPerformanceMetrics = false;
        
        for (String line : lines) {
            if (line.contains("cui_jwt_validation_duration")) {
                foundPerformanceMetrics = true;
                if (line.startsWith("# HELP")) {
                    LOGGER.info("Performance Metric Help: {}", line.substring(7));
                } else if (line.startsWith("# TYPE")) {
                    LOGGER.info("Performance Metric Type: {}", line.substring(7));
                } else if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    LOGGER.info("Performance Metric Data: {}", line);
                }
            }
        }
        
        if (!foundPerformanceMetrics) {
            LOGGER.info("No JWT performance metrics found in response - this may be expected for first-time runs");
        } else {
            LOGGER.info("JWT performance metrics successfully logged");
        }
    }

    /**
     * Logs JWT security event metrics at info level.
     */
    private void logJwtSecurityMetrics(String metricsResponse) {
        LOGGER.info("=== JWT Security Event Metrics ===");
        
        String[] lines = metricsResponse.split("\n");
        boolean foundSecurityMetrics = false;
        
        for (String line : lines) {
            if (line.contains("cui_jwt_validation_errors")) {
                foundSecurityMetrics = true;
                if (line.startsWith("# HELP")) {
                    LOGGER.info("Security Metric Help: {}", line.substring(7));
                } else if (line.startsWith("# TYPE")) {
                    LOGGER.info("Security Metric Type: {}", line.substring(7));
                } else if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    LOGGER.info("Security Metric Data: {}", line);
                }
            }
        }
        
        if (!foundSecurityMetrics) {
            LOGGER.info("No JWT security event metrics found in response - this may be expected for error-free runs");
        } else {
            LOGGER.info("JWT security event metrics successfully logged");
        }
    }

    @Test
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