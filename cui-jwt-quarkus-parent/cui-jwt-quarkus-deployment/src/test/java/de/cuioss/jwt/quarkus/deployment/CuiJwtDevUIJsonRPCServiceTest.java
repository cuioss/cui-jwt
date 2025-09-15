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
package de.cuioss.jwt.quarkus.deployment;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CuiJwtDevUIJsonRPCService}.
 */
class CuiJwtDevUIJsonRPCServiceTest {

    private final CuiJwtDevUIJsonRPCService service = new CuiJwtDevUIJsonRPCService();

    @Test
    void getValidationStatus_shouldReturnBuildTimeStatus() {
        // When
        Map<String, Object> result = service.getValidationStatus();

        // Then
        assertNotNull(result);
        assertFalse((Boolean) result.get("enabled"));
        assertFalse((Boolean) result.get("validatorPresent"));
        assertEquals("BUILD_TIME", result.get("status"));
        assertEquals("JWT validation status will be available at runtime", result.get("statusMessage"));
    }

    @Test
    void getJwksStatus_shouldReturnBuildTimeStatus() {
        // When
        Map<String, Object> result = service.getJwksStatus();

        // Then
        assertNotNull(result);
        assertEquals("BUILD_TIME", result.get("status"));
        assertEquals("JWKS endpoint status will be available at runtime", result.get("message"));
    }

    @Test
    void getConfiguration_shouldReturnBuildTimeConfiguration() {
        // When
        Map<String, Object> result = service.getConfiguration();

        // Then
        assertNotNull(result);
        assertFalse((Boolean) result.get("enabled"));
        assertFalse((Boolean) result.get("healthEnabled"));
        assertTrue((Boolean) result.get("buildTime"));
        assertEquals("Configuration details will be available at runtime", result.get("message"));
    }

    @ParameterizedTest(name = "Should return error for {0} token")
    @NullAndEmptySource
    void validateToken_shouldReturnErrorForNullOrEmptyToken(String token) {
        // When
        Map<String, Object> result = service.validateToken(token);

        // Then
        assertNotNull(result);
        assertFalse((Boolean) result.get("valid"));
        assertEquals("Token is empty or null", result.get("error"));
    }

    @Test
    void validateToken_shouldReturnBuildTimeErrorForValidToken() {
        // When
        Map<String, Object> result = service.validateToken("sample.jwt.token");

        // Then
        assertNotNull(result);
        assertFalse((Boolean) result.get("valid"));
        assertEquals("Token validation not available at build time", result.get("error"));
    }

    @Test
    void getHealthInfo_shouldReturnBuildTimeHealthInfo() {
        // When
        Map<String, Object> result = service.getHealthInfo();

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("configurationValid"));
        assertFalse((Boolean) result.get("tokenValidatorAvailable"));
        assertFalse((Boolean) result.get("securityCounterAvailable"));
        assertEquals("BUILD_TIME", result.get("overallStatus"));
        assertEquals("Health information will be available at runtime", result.get("message"));
    }
}
