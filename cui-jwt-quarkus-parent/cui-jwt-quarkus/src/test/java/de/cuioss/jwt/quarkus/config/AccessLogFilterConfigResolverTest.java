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
package de.cuioss.jwt.quarkus.config;

import de.cuioss.jwt.quarkus.logging.CustomAccessLogFilter;
import de.cuioss.jwt.quarkus.test.TestConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccessLogFilterConfigResolverTest {

    @Test
    @DisplayName("should resolve config with default values")
    void resolveConfigWithDefaults() {
        // Given - empty config (uses defaults)
        TestConfig config = new TestConfig(Map.of());
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        // When
        AccessLogFilterConfig result = resolver.resolveConfig();

        // Then
        assertNotNull(result);
        assertEquals(400, result.getMinStatusCode());
        assertEquals(599, result.getMaxStatusCode());
        assertTrue(result.getIncludeStatusCodes() == null || result.getIncludeStatusCodes().isEmpty());
        assertTrue(result.getIncludePaths() == null || result.getIncludePaths().isEmpty());
        assertTrue(result.getExcludePaths() == null || result.getExcludePaths().isEmpty());
        assertEquals("{remoteAddr} {method} {path} -> {status} ({duration}ms)", result.getPattern());
        assertEquals(CustomAccessLogFilter.class.getName(), result.getLoggerName());
    }

    @Test
    @DisplayName("should resolve config with custom values")
    void resolveConfigWithCustomValues() {
        // Given - custom config values
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESS_LOG.MIN_STATUS_CODE, "200",
                JwtPropertyKeys.ACCESS_LOG.MAX_STATUS_CODE, "599",
                JwtPropertyKeys.ACCESS_LOG.INCLUDE_STATUS_CODES, "201,202,204",
                JwtPropertyKeys.ACCESS_LOG.INCLUDE_PATHS, "/api/*,/health/*",
                JwtPropertyKeys.ACCESS_LOG.EXCLUDE_PATHS, "/metrics/*,/jwt/validate",
                JwtPropertyKeys.ACCESS_LOG.PATTERN, "{method} {path} -> {status}",
                JwtPropertyKeys.ACCESS_LOG.LOGGER_NAME, "my.custom.logger"
        ));
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        // When
        AccessLogFilterConfig result = resolver.resolveConfig();

        // Then
        assertNotNull(result);
        assertEquals(200, result.getMinStatusCode());
        assertEquals(599, result.getMaxStatusCode());
        assertEquals(List.of(201, 202, 204), result.getIncludeStatusCodes());
        assertEquals(List.of("/api/*", "/health/*"), result.getIncludePaths());
        assertEquals(List.of("/metrics/*", "/jwt/validate"), result.getExcludePaths());
        assertEquals("{method} {path} -> {status}", result.getPattern());
        assertEquals("my.custom.logger", result.getLoggerName());
    }

    @Test
    @DisplayName("should handle empty list values")
    void resolveConfigWithEmptyLists() {
        // Given - empty list values
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESS_LOG.INCLUDE_STATUS_CODES, "",
                JwtPropertyKeys.ACCESS_LOG.INCLUDE_PATHS, "  ",
                JwtPropertyKeys.ACCESS_LOG.EXCLUDE_PATHS, ""
        ));
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        // When
        AccessLogFilterConfig result = resolver.resolveConfig();

        // Then
        assertNotNull(result);
        assertTrue(result.getIncludeStatusCodes().isEmpty());
        assertTrue(result.getIncludePaths().isEmpty());
        assertTrue(result.getExcludePaths().isEmpty());
    }

    @Test
    @DisplayName("should handle single values in comma-separated lists")
    void resolveConfigWithSingleValues() {
        // Given - single values in lists
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESS_LOG.INCLUDE_STATUS_CODES, "404",
                JwtPropertyKeys.ACCESS_LOG.INCLUDE_PATHS, "/single-path",
                JwtPropertyKeys.ACCESS_LOG.EXCLUDE_PATHS, "/exclude-path"
        ));
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        // When
        AccessLogFilterConfig result = resolver.resolveConfig();

        // Then
        assertNotNull(result);
        assertEquals(List.of(404), result.getIncludeStatusCodes());
        assertEquals(List.of("/single-path"), result.getIncludePaths());
        assertEquals(List.of("/exclude-path"), result.getExcludePaths());
    }

    @Test
    @DisplayName("should resolve integration test configuration (HTTP codes > 205)")
    void resolveIntegrationTestConfig() {
        // Given - integration test configuration matching application.properties
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESS_LOG.MIN_STATUS_CODE, "206",
                JwtPropertyKeys.ACCESS_LOG.MAX_STATUS_CODE, "599",
                JwtPropertyKeys.ACCESS_LOG.PATTERN, "{remoteAddr} {method} {path} -> {status} ({duration}ms)"
        ));
        AccessLogFilterConfigResolver resolver = new AccessLogFilterConfigResolver(config);

        // When
        AccessLogFilterConfig result = resolver.resolveConfig();

        // Then
        assertNotNull(result);
        assertEquals(206, result.getMinStatusCode(), "Should log HTTP codes > 205 (starting from 206)");
        assertEquals(599, result.getMaxStatusCode());
        assertTrue(result.getExcludePaths().isEmpty(), "No exclusions by default - health and metrics endpoints will be logged for monitoring");
        assertEquals("{remoteAddr} {method} {path} -> {status} ({duration}ms)", result.getPattern());

        // Verify that this configuration will log the expected status codes
        // HTTP 200-205 should NOT be logged, 206+ should be logged
        assertFalse(shouldLogStatusCode(result, 200), "HTTP 200 should not be logged");
        assertFalse(shouldLogStatusCode(result, 201), "HTTP 201 should not be logged");
        assertFalse(shouldLogStatusCode(result, 202), "HTTP 202 should not be logged");
        assertFalse(shouldLogStatusCode(result, 204), "HTTP 204 should not be logged");
        assertFalse(shouldLogStatusCode(result, 205), "HTTP 205 should not be logged");
        assertTrue(shouldLogStatusCode(result, 206), "HTTP 206 should be logged");
        assertTrue(shouldLogStatusCode(result, 400), "HTTP 400 should be logged");
        assertTrue(shouldLogStatusCode(result, 404), "HTTP 404 should be logged");
        assertTrue(shouldLogStatusCode(result, 500), "HTTP 500 should be logged");
    }

    private boolean shouldLogStatusCode(AccessLogFilterConfig config, int statusCode) {
        return statusCode >= config.getMinStatusCode() && statusCode <= config.getMaxStatusCode();
    }
}