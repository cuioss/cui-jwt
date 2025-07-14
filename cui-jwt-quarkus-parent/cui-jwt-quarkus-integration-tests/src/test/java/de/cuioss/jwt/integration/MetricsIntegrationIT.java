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
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REST API tests for metrics endpoints against external application.
 * <p>
 * These tests verify that Prometheus metrics are properly exposed
 * and include JWT validation metrics against an external running application.
 */
class MetricsIntegrationIT extends BaseIntegrationTest {

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
}