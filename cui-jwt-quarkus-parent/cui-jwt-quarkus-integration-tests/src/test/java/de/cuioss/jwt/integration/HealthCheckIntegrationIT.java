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

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * REST API tests for health check endpoints against external application.
 * <p>
 * These tests verify that health checks work correctly in the
 * external application environment with proper JWT extension integration.
 */
class HealthCheckIntegrationIT extends BaseIntegrationTest {

    @Test
    void shouldProvideOverallHealthStatus() {
        // This test verifies the overall health status, which includes:
        // - JWT validator health check
        // - JWKS endpoint health check
        // - Issuer information
        // - Other application health checks
        given()
            .when()
            .get("/q/health")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("status", equalTo("UP"));
    }

    @Test
    void shouldProvideReadinessCheck() {
        given()
            .when()
            .get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void shouldProvideLivenessCheck() {
        given()
            .when()
            .get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void shouldProvideStartupCheck() {
        given()
            .when()
            .get("/q/health/started")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

}