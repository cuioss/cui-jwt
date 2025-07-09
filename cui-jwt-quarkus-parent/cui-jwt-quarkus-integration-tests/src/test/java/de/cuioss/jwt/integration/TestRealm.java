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
package de.cuioss.jwt.integration;

import io.restassured.response.Response;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Helper class for managing test realms in Keycloak integration tests.
 * Provides common functionality for token management and endpoint health checks.
 */
public class TestRealm {

    private static final String KEYCLOAK_BASE_URL = "https://localhost:1443";
    private static final String KEYCLOAK_MANAGEMENT_URL = "https://localhost:1090";

    private static final String TOKEN_ENDPOINT_TEMPLATE = "/realms/%s/protocol/openid-connect/token";
    private static final String CERTS_ENDPOINT_TEMPLATE = "/realms/%s/protocol/openid-connect/certs";
    private static final String WELL_KNOWN_ENDPOINT_TEMPLATE = "/realms/%s/.well-known/openid-configuration";

    // Integration realm constants
    private static final String INTEGRATION_REALM_ID = "integration";
    private static final String INTEGRATION_CLIENT_ID = "integration-client";
    private static final String INTEGRATION_CLIENT_SECRET = "integration-secret";
    private static final String INTEGRATION_USERNAME = "integration-user";
    private static final String INTEGRATION_PASSWORD = "integration-password";

    // Benchmark realm constants
    private static final String BENCHMARK_REALM_ID = "benchmark";
    private static final String BENCHMARK_CLIENT_ID = "benchmark-client";
    private static final String BENCHMARK_CLIENT_SECRET = "benchmark-secret";
    private static final String BENCHMARK_USERNAME = "benchmark-user";
    private static final String BENCHMARK_PASSWORD = "benchmark-password";

    private final String realmIdentifier;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;

    /**
     * Creates a new TestRealm instance.
     *
     * @param realmIdentifier the realm identifier (e.g., "integration", "benchmark")
     * @param clientId the client ID for authentication
     * @param clientSecret the client secret for authentication
     * @param username the username for authentication
     * @param password the password for authentication
     */
    public TestRealm(String realmIdentifier, String clientId, String clientSecret, String username, String password) {
        this.realmIdentifier = realmIdentifier;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
    }

    /**
     * Factory method to create a TestRealm instance for integration tests.
     *
     * @return TestRealm configured for integration realm
     */
    public static TestRealm createIntegrationRealm() {
        return new TestRealm(
                INTEGRATION_REALM_ID,
                INTEGRATION_CLIENT_ID,
                INTEGRATION_CLIENT_SECRET,
                INTEGRATION_USERNAME,
                INTEGRATION_PASSWORD
        );
    }

    /**
     * Factory method to create a TestRealm instance for benchmark tests.
     *
     * @return TestRealm configured for benchmark realm
     */
    public static TestRealm createBenchmarkRealm() {
        return new TestRealm(
                BENCHMARK_REALM_ID,
                BENCHMARK_CLIENT_ID,
                BENCHMARK_CLIENT_SECRET,
                BENCHMARK_USERNAME,
                BENCHMARK_PASSWORD
        );
    }

    /**
     * Obtains a valid token from the realm.
     * Similar to JwtValidationIntegrationIT#obtainValidTokenFromIntegrationRealm
     *
     * @return TokenResponse containing access, ID, and refresh tokens
     */
    public TokenResponse obtainValidToken() {
        Response tokenResponse = given()
                .baseUri(KEYCLOAK_BASE_URL)
                .contentType("application/x-www-form-urlencoded")
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .formParam("username", username)
                .formParam("password", password)
                .formParam("grant_type", "password")
                .formParam("scope", "openid profile email")
                .when()
                .post(TOKEN_ENDPOINT_TEMPLATE.formatted(realmIdentifier));

        assertEquals(200, tokenResponse.statusCode(),
                "Should be able to obtain tokens from " + realmIdentifier + " realm. Response: " + tokenResponse.body().asString());

        Map<String, Object> tokenData = tokenResponse.jsonPath().getMap("");

        String accessToken = (String) tokenData.get("access_token");
        String idToken = (String) tokenData.get("id_token");
        String refreshToken = (String) tokenData.get("refresh_token");

        // Validate tokens
        validateToken(accessToken, "Access token");
        validateToken(idToken, "ID token");
        validateToken(refreshToken, "Refresh token");

        return new TokenResponse(accessToken, idToken, refreshToken);
    }

    /**
     * Checks if the well-known endpoint is healthy/available.
     *
     * @return true if the endpoint is healthy, false otherwise
     */
    public boolean isWellKnownEndpointHealthy() {
        try {
            Response response = given()
                    .baseUri(KEYCLOAK_BASE_URL)
                    .when()
                    .get(WELL_KNOWN_ENDPOINT_TEMPLATE.formatted(realmIdentifier));

            return response.statusCode() == 200 &&
                    response.body().asString().contains("\"issuer\"") &&
                    response.body().asString().contains("\"jwks_uri\"");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the JWKS endpoint is accessible.
     *
     * @return true if the JWKS endpoint is accessible, false otherwise
     */
    public boolean isJwksEndpointHealthy() {
        try {
            Response response = given()
                    .baseUri(KEYCLOAK_BASE_URL)
                    .when()
                    .get(CERTS_ENDPOINT_TEMPLATE.formatted(realmIdentifier));

            return response.statusCode() == 200 && response.body().asString().contains("\"keys\"");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if Keycloak management endpoint is healthy.
     *
     * @return true if Keycloak is healthy, false otherwise
     */
    public boolean isKeycloakHealthy() {
        try {
            Response response = given()
                    .baseUri(KEYCLOAK_MANAGEMENT_URL)
                    .when()
                    .get("/health/ready");

            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateToken(String token, String tokenType) {
        assertNotNull(token, tokenType + " should not be null");
        assertFalse(token.isEmpty(), tokenType + " should not be empty");
    }

    /**
     * Response object containing the different token types.
     */
    public record TokenResponse(String accessToken, String idToken, String refreshToken) {
    }
}
