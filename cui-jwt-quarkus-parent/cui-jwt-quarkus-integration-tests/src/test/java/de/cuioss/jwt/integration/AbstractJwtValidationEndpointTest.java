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

import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
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
}
