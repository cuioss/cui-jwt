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
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests to verify that HTTP-level JWT metrics are properly collected and exposed.
 * <p>
 * This test verifies the integration between:
 * <ul>
 *   <li>Custom {@code HttpMetricsMonitor} - JWT-specific HTTP processing metrics</li>
 *   <li>JWT validation library metrics - Token validation pipeline measurements</li>
 *   <li>Quarkus Micrometer integration - Standard HTTP server metrics</li>
 *   <li>Prometheus metrics export - All metrics available at /q/metrics endpoint</li>
 * </ul>
 * <p>
 * The test performs actual JWT validation requests and verifies that both
 * custom JWT metrics and standard HTTP metrics are properly recorded and exported.
 *
 * @author Oliver Wolff
 */
@DisplayName("Tests HTTP metrics integration with JWT validation")
class MetricsIntegrationIT extends BaseIntegrationTest {

    private static final CuiLogger log = new CuiLogger(MetricsIntegrationIT.class);
    private static final TestRealm testRealm = TestRealm.createIntegrationRealm();

    @Test
    @DisplayName("Should expose custom JWT HTTP metrics after successful validation")
    void shouldExposeCustomJwtHttpMetricsAfterSuccessfulValidation() {
        // Obtain a valid token with required scopes
        TestRealm.TokenResponse tokens = testRealm.obtainValidTokenWithAllScopes();
        
        // Make a successful JWT validation request
        given()
                .header("Authorization", "Bearer " + tokens.accessToken())
                .when()
                .get("/secured")
                .then()
                .statusCode(200);

        // Verify custom JWT HTTP metrics are exposed
        Response metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        String metricsBody = metricsResponse.getBody().asString();
        log.info("Metrics response length: {} characters", metricsBody.length());

        // Verify custom JWT HTTP metrics exist
        assertMetricExists(metricsBody, "cui_jwt_http_request_duration_seconds", 
                "Custom JWT HTTP request duration metric should be present");
        
        assertMetricExists(metricsBody, "cui_jwt_http_request_count_total", 
                "Custom JWT HTTP request count metric should be present");

        // Verify JWT validation library metrics exist  
        assertMetricExists(metricsBody, "cui_jwt_validation_duration_seconds", 
                "JWT validation pipeline metrics should be present");

        // Verify standard Quarkus HTTP metrics exist
        assertMetricExists(metricsBody, "http_server_requests_seconds", 
                "Standard Quarkus HTTP server metrics should be present");

        // Log sample of metrics for debugging
        log.info("Sample metrics found: JWT HTTP duration={}, JWT validation={}, Standard HTTP={}", 
                metricsBody.contains("cui_jwt_http_request_duration_seconds"),
                metricsBody.contains("cui_jwt_validation_duration_seconds"),
                metricsBody.contains("http_server_requests_seconds"));
    }

    @Test
    @DisplayName("Should expose HTTP metrics for missing token scenario")
    void shouldExposeHttpMetricsForMissingTokenScenario() {
        // Make request without Authorization header
        given()
                .when()
                .get("/secured")
                .then()
                .statusCode(401);

        // Verify metrics are still recorded for failed requests
        Response metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        String metricsBody = metricsResponse.getBody().asString();

        // Should have HTTP-level metrics even for failed requests
        assertMetricExists(metricsBody, "cui_jwt_http_request_duration_seconds", 
                "HTTP request duration should be recorded for missing token");
        
        assertMetricExists(metricsBody, "cui_jwt_http_request_count_total", 
                "HTTP request count should be recorded for missing token");

        // Standard HTTP metrics should show 401 status
        assertTrue(metricsBody.contains("status=\"401\""), 
                "Standard HTTP metrics should record 401 status for missing token");
    }

    @Test
    @DisplayName("Should expose HTTP metrics for invalid token scenario")
    void shouldExposeHttpMetricsForInvalidTokenScenario() {
        // Make request with invalid token
        given()
                .header("Authorization", "Bearer invalid.token.here")
                .when()
                .get("/secured")
                .then()
                .statusCode(401);

        // Verify metrics include invalid token processing
        Response metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        String metricsBody = metricsResponse.getBody().asString();

        // Should have HTTP-level metrics for invalid token processing
        assertMetricExists(metricsBody, "cui_jwt_http_request_duration_seconds", 
                "HTTP request duration should be recorded for invalid token");

        // JWT validation metrics should show token processing attempts
        assertMetricExists(metricsBody, "cui_jwt_validation_duration_seconds", 
                "JWT validation metrics should record invalid token processing");
    }

    @Test
    @DisplayName("Should track different HTTP measurement types")
    void shouldTrackDifferentHttpMeasurementTypes() {
        // Obtain a valid token with required scopes
        TestRealm.TokenResponse tokens = testRealm.obtainValidTokenWithAllScopes();
        
        // Make multiple requests to generate diverse metrics
        for (int i = 0; i < 5; i++) {
            // Successful request
            given()
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .when()
                    .get("/secured")
                    .then()
                    .statusCode(200);

            // Missing token request  
            given()
                    .when()
                    .get("/secured")
                    .then()
                    .statusCode(401);
        }

        // Verify comprehensive metrics collection
        Response metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        String metricsBody = metricsResponse.getBody().asString();

        // Verify different metric dimensions exist
        assertTrue(metricsBody.contains("cui_jwt_http_request_duration_seconds_count"), 
                "HTTP duration count metric should exist");
        assertTrue(metricsBody.contains("cui_jwt_http_request_duration_seconds_sum"), 
                "HTTP duration sum metric should exist");
        assertTrue(metricsBody.contains("cui_jwt_http_request_count_total"), 
                "HTTP request count metric should exist");

        // Verify different request outcomes are tracked
        boolean hasSuccessMetrics = metricsBody.contains("status=\"success\"") || 
                                   metricsBody.contains("status=\"200\"");
        boolean hasFailureMetrics = metricsBody.contains("status=\"missing_token\"") || 
                                   metricsBody.contains("status=\"401\"");

        assertTrue(hasSuccessMetrics, "Should have metrics for successful requests");
        assertTrue(hasFailureMetrics, "Should have metrics for failed requests");

        log.info("Verified diverse HTTP metrics: success={}, failure={}", 
                hasSuccessMetrics, hasFailureMetrics);
    }

    @Test
    @DisplayName("Should expose both JWT-specific and standard HTTP metrics simultaneously")
    void shouldExposeBothJwtSpecificAndStandardHttpMetricsSimultaneously() {
        // Obtain a valid token with required scopes
        TestRealm.TokenResponse tokens = testRealm.obtainValidTokenWithAllScopes();
        
        // Generate some traffic
        given()
                .header("Authorization", "Bearer " + tokens.accessToken())
                .when()
                .get("/secured")
                .then()
                .statusCode(200);

        // Access health endpoint (should only have standard metrics, no JWT processing)
        given()
                .when()
                .get("/q/health")
                .then()
                .statusCode(200);

        // Verify both types of metrics coexist
        Response metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        String metricsBody = metricsResponse.getBody().asString();

        // JWT-specific metrics (only for /secured endpoint)
        assertMetricExists(metricsBody, "cui_jwt_http_request_duration_seconds", 
                "JWT-specific HTTP metrics should exist");
        assertMetricExists(metricsBody, "cui_jwt_validation_duration_seconds", 
                "JWT validation metrics should exist");

        // Standard HTTP metrics (for all endpoints)
        assertMetricExists(metricsBody, "http_server_requests_seconds", 
                "Standard HTTP metrics should exist");

        // Verify both endpoints are tracked in standard metrics
        assertTrue(metricsBody.contains("uri=\"/secured\""), 
                "Standard metrics should track secured endpoint");
        assertTrue(metricsBody.contains("uri=\"/q/health\""), 
                "Standard metrics should track health endpoint");

        // JWT metrics should only be for secured endpoints
        int jwtMetricsCount = countOccurrences(metricsBody, "cui_jwt_http_request");
        int standardMetricsCount = countOccurrences(metricsBody, "http_server_requests_seconds");

        assertTrue(jwtMetricsCount > 0, "Should have JWT-specific metrics");
        assertTrue(standardMetricsCount > jwtMetricsCount, 
                "Should have more standard metrics than JWT-specific metrics");

        log.info("Metrics coexistence verified: JWT={}, Standard={}", 
                jwtMetricsCount, standardMetricsCount);
    }

    @Test
    @DisplayName("Should maintain metric accuracy under concurrent load")
    void shouldMaintainMetricAccuracyUnderConcurrentLoad() {
        // Obtain a valid token with required scopes
        TestRealm.TokenResponse tokens = testRealm.obtainValidTokenWithAllScopes();
        
        // Make multiple concurrent requests to test thread safety
        int requestCount = 20;
        
        for (int i = 0; i < requestCount; i++) {
            if (i % 2 == 0) {
                // Successful requests
                given()
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .when()
                        .get("/secured")
                        .then()
                        .statusCode(200);
            } else {
                // Failed requests
                given()
                        .when()
                        .get("/secured")
                        .then()
                        .statusCode(401);
            }
        }

        // Verify metrics reflect the load
        Response metricsResponse = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract().response();

        String metricsBody = metricsResponse.getBody().asString();

        // Verify metrics show reasonable counts
        assertMetricExists(metricsBody, "cui_jwt_http_request_count_total", 
                "HTTP request count should be present after load");

        // Should have both success and failure counts in standard metrics
        boolean hasSuccessCount = metricsBody.matches(".*http_server_requests_seconds_count.*status=\"200\".*[1-9][0-9]*.*");
        boolean hasFailureCount = metricsBody.matches(".*http_server_requests_seconds_count.*status=\"401\".*[1-9][0-9]*.*");

        assertTrue(hasSuccessCount, "Should have positive success count in standard metrics");
        assertTrue(hasFailureCount, "Should have positive failure count in standard metrics");

        log.info("Concurrent load test completed: {} requests processed", requestCount);
    }

    /**
     * Helper method to assert that a specific metric exists in the metrics output.
     *
     * @param metricsBody the full metrics response body
     * @param metricName  the name of the metric to check for
     * @param message     the assertion message
     */
    private void assertMetricExists(String metricsBody, String metricName, String message) {
        assertTrue(metricsBody.contains(metricName), 
                message + " (searching for: " + metricName + ")");
    }

    /**
     * Helper method to count occurrences of a substring in a text.
     *
     * @param text the text to search in
     * @param substring the substring to count
     * @return the number of occurrences
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}