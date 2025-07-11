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

import java.util.Map;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integration tests specifically for TokenRequest functionality in complex scenarios.
 * API validation tests (null/empty/blank strings) have been moved to JwtValidationEndpointApiValidationIT.
 * This class focuses on TokenRequest behavior in integration scenarios.
 */
@DisplayName("JWT Validation Endpoint - TokenRequest Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwtValidationEndpointTokenRequestIT extends BaseIntegrationTest {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String TOKEN_FIELD_NAME = "token";
    private static final String VALID = "valid";
    private static final String MESSAGE = "message";

    @Test
    @Order(1)
    @DisplayName("TokenRequest record should be properly deserialized from JSON")
    void testTokenRequestDeserialization() {
        // Test that TokenRequest record works correctly with JSON deserialization
        given()
            .contentType(CONTENT_TYPE_JSON)
            .body(Map.of(TOKEN_FIELD_NAME, "test.token.value"))
            .when()
            .post("/jwt/validate-explicit")
            .then()
            .statusCode(401) // Should get 401 for invalid token, not 400 for missing token
            .body(VALID, equalTo(false))
            .body(MESSAGE, containsString("Token validation failed"));
    }

    @Test
    @Order(2)
    @DisplayName("TokenRequest.isEmpty() should work correctly with token trimming")
    void testTokenRequestIsEmptyWithTokenTrimming() {
        // Test that tokens with surrounding whitespace are handled correctly
        given()
            .contentType(CONTENT_TYPE_JSON)
            .body(Map.of(TOKEN_FIELD_NAME, "  valid.token.value  "))
            .when()
            .post("/jwt/validate-explicit")
            .then()
            .statusCode(401) // Should get 401 for invalid token, not 400 for empty token
            .body(VALID, equalTo(false))
            .body(MESSAGE, containsString("Token validation failed"));
    }
}