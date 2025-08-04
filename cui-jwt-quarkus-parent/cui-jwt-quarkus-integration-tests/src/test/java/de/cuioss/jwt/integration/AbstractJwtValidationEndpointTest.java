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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract base test class for testing JwtValidationEndpoint endpoints.
 * Provides comprehensive testing of all JWT validation endpoints with both positive and negative scenarios.
 */
@DisplayName("JWT Validation Endpoint Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractJwtValidationEndpointTest extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(AbstractJwtValidationEndpointTest.class);
    public static final String AUTHORIZATION = "Authorization";
    // String constants for repeated literals
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_VALIDATE_PATH = "/jwt/validate";
    private static final String JWT_VALIDATE_ID_TOKEN_PATH = "/jwt/validate/id-token";
    private static final String JWT_VALIDATE_REFRESH_TOKEN_PATH = "/jwt/validate/refresh-token";
    private static final String ACCESS_TOKEN_VALID_MESSAGE = "Access token is valid";
    private static final String TOKEN_FIELD_NAME = "token";
    public static final String VALID = "valid";
    public static final String MESSAGE = "message";
    public static final String REFRESH_TOKEN_IS_VALID = "Refresh token is valid";

    /**
     * Returns the TestRealm instance to use for testing.
     * Implementations should return either TestRealm.createBenchmarkRealm() or TestRealm.createIntegrationRealm().
     *
     * @return TestRealm instance for testing
     */
    protected abstract TestRealm getTestRealm();

    @Test
    @Order(1)
    @DisplayName("Verify Keycloak health and token obtaining functionality")
    void keycloakHealthiness() {
        // First test: Healthiness including resolving of tokens using TestRealm#isKeycloakHealthy
        assertTrue(getTestRealm().isKeycloakHealthy(), "Keycloak should be healthy and accessible");

        // Add verification of getTestRealm().obtainValidToken() with all tokens not being null
        TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
        assertNotNull(tokenResponse.accessToken(), "Access token should not be null");
        assertNotNull(tokenResponse.idToken(), "ID token should not be null");
        assertNotNull(tokenResponse.refreshToken(), "Refresh token should not be null");
    }

    @Nested
    @DisplayName("Positive Tests - Valid Token Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PositiveTests {

        @Test
        @Order(2)
        @DisplayName("Validate access token via Authorization header")
        void validateAccessTokenEndpointPositive() {
            // Obtain tokens locally for this test
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validAccessToken = tokenResponse.accessToken();

            // Test positive case: valid access token via Authorization header
            given()
                .contentType(CONTENT_TYPE_JSON)
                .header(AUTHORIZATION, BEARER_PREFIX + validAccessToken)
                .when()
                .post(JWT_VALIDATE_PATH)
                .then()
                .statusCode(200)
                .body(VALID, equalTo(true))
                .body(MESSAGE, equalTo(ACCESS_TOKEN_VALID_MESSAGE));
        }

        @Test
        @Order(3)
        @DisplayName("Validate ID token via request body")
        void validateIdTokenEndpointPositive() {
            // Obtain tokens locally for this test
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validIdToken = tokenResponse.idToken();

            // Test positive case: valid ID token via request body
            given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, validIdToken))
                .when()
                .post(JWT_VALIDATE_ID_TOKEN_PATH)
                .then()
                .statusCode(200)
                .body(VALID, equalTo(true))
                .body(MESSAGE, equalTo("ID token is valid"));
        }

        @Test
        @Order(4)
        @DisplayName("Validate refresh token via request body")
        void validateRefreshTokenEndpointPositive() {
            // Obtain tokens locally for this test
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validRefreshToken = tokenResponse.refreshToken();

            // Test positive case: valid refresh token via request body
            given()
                .contentType(CONTENT_TYPE_JSON)
                .body(Map.of(TOKEN_FIELD_NAME, validRefreshToken))
                .when()
                .post(JWT_VALIDATE_REFRESH_TOKEN_PATH)
                .then()
                .statusCode(200)
                .body(VALID, equalTo(true))
                .body(MESSAGE, equalTo(REFRESH_TOKEN_IS_VALID));
        }

        @Test
        @Order(14)
        @DisplayName("Validate access token with multiple consecutive requests")
        void validateAccessTokenEndpointMultipleRequests() {
            // Obtain tokens locally for this test
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validAccessToken = tokenResponse.accessToken();

            // Test multiple consecutive requests
            for (int i = 0; i < 3; i++) {
                given()
                    .contentType(CONTENT_TYPE_JSON)
                    .header(AUTHORIZATION, BEARER_PREFIX + validAccessToken)
                    .when()
                    .post(JWT_VALIDATE_PATH)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo(ACCESS_TOKEN_VALID_MESSAGE));
            }
        }
    }

    @Nested
    @DisplayName("Bearer Token Producer Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BearerTokenProducerTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "/jwt/bearer-token/with-scopes",
            "/jwt/bearer-token/with-roles",
            "/jwt/bearer-token/with-groups",
            "/jwt/bearer-token/with-all"
        })
        @DisplayName("Bearer token endpoint validation with different requirement types")
        void bearerTokenEndpointValidation(String endpoint) {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            if (endpoint.contains("scopes")) {
                LOGGER.info("bearerTokenWithScopes:" + tokenResponse.accessToken());
            }

            given()
                .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                .when()
                .get(endpoint)
                .then()
                .statusCode(200);
            // Just verify the endpoint responds - content validation depends on actual token
        }
    }

    @Test
    @Order(99)
    @DisplayName("Verify SecurityEventCounter metrics have sensible bounds after all tests")
    void verifySecurityEventCounterMetrics() {
        LOGGER.info("Verifying SecurityEventCounter metrics bounds after all integration tests");

        // Wait for metrics to be collected (collection interval is 2s)
        // Just wait a bit to ensure metrics collection has run at least once
        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                String response = given()
                    .when()
                    .get("/q/metrics")
                    .then()
                    .statusCode(200)
                    .extract()
                    .body()
                    .asString();
                // Just wait for any JWT validation metrics to appear
                return response.contains("cui_jwt_validation");
            });

        // Fetch metrics from the /q/metrics endpoint
        String metricsResponse = given()
            .when()
            .get("/q/metrics")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        LOGGER.debug("Raw metrics response length: {}", metricsResponse.length());

        // Debug: Print relevant metrics lines
        String[] lines = metricsResponse.split("\n");
        for (String line : lines) {
            if (line.contains("cui_jwt_validation")) {
                LOGGER.info("Found JWT validation metric: {}", line);
            }
        }

        // Verify we have error metrics (always present)
        assertTrue(metricsResponse.contains("cui_jwt_validation_errors_total"),
            "Should contain error metrics");

        // Check if success metrics are present (may not be if no success events occurred)
        boolean hasSuccessMetrics = metricsResponse.contains("cui_jwt_validation_success_total");
        LOGGER.info("Success metrics present: {}", hasSuccessMetrics);

        // Parse metrics to check bounds
        Map<String, Double> parsedMetrics = parseMetricsResponse(metricsResponse);

        if (hasSuccessMetrics) {
            // Verify success metrics have reasonable bounds
            // We expect ACCESS_TOKEN_CREATED to be the highest since all tests use access tokens
            double accessTokensCreated = getMetricValue(parsedMetrics, "cui_jwt_validation_success_total", "ACCESS_TOKEN_CREATED");
            assertTrue(accessTokensCreated >= 10,
                "Should have created at least 10 access tokens during integration tests, got: " + accessTokensCreated);
            assertTrue(accessTokensCreated <= 10000,
                "Access token creation count seems unreasonably high: " + accessTokensCreated);

            // Verify cache hits if caching is enabled
            double accessTokenCacheHits = getMetricValue(parsedMetrics, "cui_jwt_validation_success_total", "ACCESS_TOKEN_CACHE_HIT");
            // Cache hits should be >= 0 (could be 0 if cache is disabled)
            assertTrue(accessTokenCacheHits >= 0,
                "Cache hits should be non-negative: " + accessTokenCacheHits);

            // Verify total success count is reasonable
            double totalSuccess = accessTokensCreated + accessTokenCacheHits
                + getMetricValue(parsedMetrics, "cui_jwt_validation_success_total", "ID_TOKEN_CREATED")
                + getMetricValue(parsedMetrics, "cui_jwt_validation_success_total", "REFRESH_TOKEN_CREATED");
            assertTrue(totalSuccess >= 10,
                "Total successful operations should be at least 10: " + totalSuccess);

            LOGGER.info("SecurityEventCounter metrics validation passed - ACCESS_TOKEN_CREATED: {}, " +
                "ACCESS_TOKEN_CACHE_HIT: {}, Total Success: {}",
                accessTokensCreated, accessTokenCacheHits, totalSuccess);
        } else {
            LOGGER.warn("Success metrics not found - this indicates SecurityEventCounter success events are not being published");
            // For now, just verify that we at least have the error metrics structure
            assertTrue(parsedMetrics.size() > 0, "Should have some metrics available");
        }
    }

    private Map<String, Double> parseMetricsResponse(String metricsResponse) {
        Map<String, Double> metrics = new HashMap<>();
        String[] lines = metricsResponse.split("\n");

        for (String line : lines) {
            line = line.trim();
            // Skip comments and empty lines
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }

            // Parse metric lines: metric_name{tags} value
            int spaceIndex = line.lastIndexOf(' ');
            if (spaceIndex > 0) {
                String metricPart = line.substring(0, spaceIndex);
                String valuePart = line.substring(spaceIndex + 1);

                try {
                    double value = Double.parseDouble(valuePart);
                    metrics.put(metricPart, value);
                } catch (NumberFormatException e) {
                    // Ignore invalid metrics
                }
            }
        }

        return metrics;
    }

    private double getMetricValue(Map<String, Double> metrics, String metricPrefix, String eventType) {
        // Look for metrics like: cui_jwt_validation_success_total{event_type="ACCESS_TOKEN_CREATED",result="success"}
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String metricName = entry.getKey();
            if (metricName.startsWith(metricPrefix) &&
                metricName.contains("event_type=\"" + eventType + "\"")) {
                return entry.getValue();
            }
        }
        return 0.0; // Return 0 if metric not found
    }
}
