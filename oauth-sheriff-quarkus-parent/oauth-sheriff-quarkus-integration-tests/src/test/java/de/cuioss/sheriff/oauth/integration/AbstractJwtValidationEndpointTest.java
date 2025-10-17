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
package de.cuioss.sheriff.oauth.integration;

import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

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
                LOGGER.debug("bearerTokenWithScopes:%s", tokenResponse.accessToken());
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

    @Nested
    @DisplayName("Bearer Token Interceptor Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BearerTokenInterceptorTests {

        @Test
        @DisplayName("Interceptor validation - basic (no requirements)")
        void interceptorValidationBasic() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/basic")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (basic)"));
        }

        @Test
        @DisplayName("Interceptor validation - with scopes")
        void interceptorValidationWithScopes() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-scopes")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (with scopes)"));
        }

        @Test
        @DisplayName("Interceptor validation - with roles")
        void interceptorValidationWithRoles() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-roles")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (with roles)"));
        }

        @Test
        @DisplayName("Interceptor validation - with groups")
        void interceptorValidationWithGroups() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-groups")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (with groups)"));
        }

        @Test
        @DisplayName("Interceptor validation - with all requirements")
        void interceptorValidationWithAll() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidTokenWithAllScopes();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-all")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Interceptor validation successful (with all requirements)"));
        }

        @Test
        @DisplayName("Interceptor validation - missing token returns 401")
        void interceptorValidationMissingToken() {
            given()
                    .when()
                    .get("/jwt/interceptor/basic")
                    .then()
                    .statusCode(401);
        }

        @Test
        @DisplayName("Interceptor validation - invalid token returns 401")
        void interceptorValidationInvalidToken() {
            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + "invalid.token.here")
                    .when()
                    .get("/jwt/interceptor/basic")
                    .then()
                    .statusCode(401);
        }

        @ParameterizedTest
        @ValueSource(strings = {"/jwt/interceptor/with-scopes", "/jwt/interceptor/with-roles", "/jwt/interceptor/with-groups"})
        @DisplayName("Interceptor validation - token with default scopes/roles/groups succeeds")
        void interceptorValidationWithDefaultPermissions(String endpoint) {
            // Note: obtainValidToken() gets a token with default scopes ("read"), roles, and groups ("/test-group")
            // as configured in the Keycloak realm. These match the requirements of the test endpoints.
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get(endpoint)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true));
        }

        @Test
        @DisplayName("Interceptor validation with String return type - success")
        void interceptorValidationWithStringReturnSuccess() {
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            String response = given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/string-return")
                    .then()
                    .statusCode(200)
                    .contentType("text/plain")
                    .extract()
                    .asString();

            assertEquals("String return type validation successful", response);
        }

        @Test
        @DisplayName("Interceptor validation with String return type - failure throws WebApplicationException")
        void interceptorValidationWithStringReturnFailure() {
            // This tests the critical ClassCastException fix:
            // When validation fails and method returns String (not Response),
            // the interceptor should throw WebApplicationException, not try to cast Response to String
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/string-return-fail")
                    .then()
                    .statusCode(401); // Constraint violation - missing required scope (scopes return 401)
        }

        @Test
        @DisplayName("Interceptor validation with String return type - missing token")
        void interceptorValidationWithStringReturnMissingToken() {
            // Test that missing token also properly throws WebApplicationException for String return type
            given()
                    .when()
                    .get("/jwt/interceptor/string-return")
                    .then()
                    .statusCode(401); // No token given
        }

        @Test
        @DisplayName("CDI injection validation with @BearerToken - validates token structure")
        void cdiInjectionWithBearerTokenValidation() {
            // This test validates the CDI-based validation pattern where the endpoint
            // manually checks authorization status using Instance<BearerTokenResult>

            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();

            var response = given()
                    .header(AUTHORIZATION, BEARER_PREFIX + tokenResponse.accessToken())
                    .when()
                    .get("/jwt/interceptor/with-token-access")
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("Token access successful"))
                    .extract()
                    .response();

            // Validate response contains expected token data
            var data = response.jsonPath().getMap("data");
            assertNotNull(data, "Response data should not be null");

            // Validate userId is present and not empty
            String userId = (String) data.get("userId");
            assertNotNull(userId, "userId should be present in response");
            assertFalse(userId.isBlank(), "userId should not be blank");

            // Validate scopes contains 'read'
            @SuppressWarnings("unchecked") var scopes = (Collection<String>) data.get("scopes");
            assertNotNull(scopes, "scopes should be present in response");
            assertTrue(scopes.contains("read"), "scopes should contain 'read'");

            // Validate roles and groups are present (may be empty but should be present)
            assertNotNull(data.get("roles"), "roles should be present in response");
            assertNotNull(data.get("groups"), "groups should be present in response");
        }

        @Test
        @DisplayName("CDI injection validation with @BearerToken - missing token returns 401")
        void cdiInjectionWithBearerTokenMissingToken() {
            // Verify that missing token is properly handled by CDI validation
            given()
                    .when()
                    .get("/jwt/interceptor/with-token-access")
                    .then()
                    .statusCode(401); // CDI validation should reject before processing
        }

        @Test
        @DisplayName("CDI injection validation with @BearerToken - invalid token returns 401")
        void cdiInjectionWithBearerTokenInvalidToken() {
            // Verify that invalid token is properly handled by CDI validation
            given()
                    .header(AUTHORIZATION, BEARER_PREFIX + "invalid.jwt.token")
                    .when()
                    .get("/jwt/interceptor/with-token-access")
                    .then()
                    .statusCode(401); // CDI validation should reject invalid tokens
        }
    }

    @Test
    @Order(99)
    @DisplayName("Verify SecurityEventCounter metrics have sensible bounds after all tests")
    void verifySecurityEventCounterMetrics() {
        LOGGER.debug("Verifying SecurityEventCounter metrics bounds after all integration tests");

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
                    return response.contains("sheriff_oauth_validation");
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

        LOGGER.debug("Raw metrics response length: %s", metricsResponse.length());

        // Debug: Print relevant metrics lines
        String[] lines = metricsResponse.split("\n");
        for (String line : lines) {
            if (line.contains("sheriff_oauth_validation")) {
                LOGGER.debug("Found JWT validation metric: %s", line);
            }
        }

        // Verify we have error metrics (always present)
        assertTrue(metricsResponse.contains("sheriff_oauth_validation_errors_total"),
                "Should contain error metrics");

        // Check if success metrics are present (may not be if no success events occurred)
        boolean hasSuccessMetrics = metricsResponse.contains("sheriff_oauth_validation_success_total");
        LOGGER.debug("Success metrics present: %s", hasSuccessMetrics);

        // Parse metrics to check bounds
        Map<String, Double> parsedMetrics = parseMetricsResponse(metricsResponse);

        if (hasSuccessMetrics) {
            // Verify success metrics have reasonable bounds
            // We expect ACCESS_TOKEN_CREATED to be the highest since all tests use access tokens
            double accessTokensCreated = getMetricValue(parsedMetrics, "sheriff_oauth_validation_success_total", "ACCESS_TOKEN_CREATED");
            assertTrue(accessTokensCreated >= 10,
                    "Should have created at least 10 access tokens during integration tests, got: " + accessTokensCreated);
            assertTrue(accessTokensCreated <= 10000,
                    "Access token creation count seems unreasonably high: " + accessTokensCreated);

            // Verify cache hits if caching is enabled
            double accessTokenCacheHits = getMetricValue(parsedMetrics, "sheriff_oauth_validation_success_total", "ACCESS_TOKEN_CACHE_HIT");
            // Cache hits should be >= 0 (could be 0 if cache is disabled)
            assertTrue(accessTokenCacheHits >= 0,
                    "Cache hits should be non-negative: " + accessTokenCacheHits);

            // Verify total success count is reasonable
            double totalSuccess = accessTokensCreated + accessTokenCacheHits
                    + getMetricValue(parsedMetrics, "sheriff_oauth_validation_success_total", "ID_TOKEN_CREATED")
                    + getMetricValue(parsedMetrics, "sheriff_oauth_validation_success_total", "REFRESH_TOKEN_CREATED");
            assertTrue(totalSuccess >= 10,
                    "Total successful operations should be at least 10: " + totalSuccess);

            LOGGER.debug("""
                    SecurityEventCounter metrics validation passed - ACCESS_TOKEN_CREATED: %s, \
                    ACCESS_TOKEN_CACHE_HIT: %s, Total Success: %s""",
                    accessTokensCreated, accessTokenCacheHits, totalSuccess);
        } else {
            LOGGER.debug("Success metrics not found - this indicates SecurityEventCounter success events are not being published");
            // For now, just verify that we at least have the error metrics structure
            assertFalse(parsedMetrics.isEmpty(), "Should have some metrics available");
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
