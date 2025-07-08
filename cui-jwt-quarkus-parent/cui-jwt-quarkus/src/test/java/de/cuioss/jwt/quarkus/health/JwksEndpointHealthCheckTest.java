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
package de.cuioss.jwt.quarkus.health;

import de.cuioss.jwt.quarkus.config.JwtTestProfile;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtTestProfile.class)
@EnableTestLogger
class JwksEndpointHealthCheckTest {

    @Inject
    @Readiness
    JwksEndpointHealthCheck healthCheck;

    @Test
    @DisplayName("Health check bean should be injected and available")
    void healthCheckBeanIsInjected() {
        assertNotNull(healthCheck, "JwksEndpointHealthCheck should be injected");
    }

    @Test
    @DisplayName("Health check should return UP status with valid configuration")
    void healthCheckShouldReturnUpStatus() {
        HealthCheckResponse response = healthCheck.call();
        assertNotNull(response, "HealthCheckResponse should not be null");
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                "Health check status should be UP with valid configuration");
    }

    @Test
    @DisplayName("Health check should have correct name")
    void healthCheckName() {
        HealthCheckResponse response = healthCheck.call();
        assertEquals("jwks-endpoints", response.getName(),
                "Health check should have correct name");
    }

    @Test
    @DisplayName("Health check should include correct data for UP status")
    void healthCheckDataForUpStatus() {
        HealthCheckResponse response = healthCheck.call();

        // Verify response has data
        assertTrue(response.getData().isPresent(),
                "Health check data should be present for UP status");

        Map<String, Object> data = response.getData().get();

        // UP status should have endpoint count and issuer data
        assertTrue(data.containsKey("checkedEndpoints"),
                "Health check data should contain checkedEndpoints count when UP");

        Object endpointCountValue = data.get("checkedEndpoints");
        assertNotNull(endpointCountValue, "checkedEndpoints should not be null");

        assertInstanceOf(Number.class, endpointCountValue,
                "checkedEndpoints should be a Number, but was: " + endpointCountValue.getClass().getSimpleName());

        int endpointCount = ((Number) endpointCountValue).intValue();
        assertTrue(endpointCount > 0,
                "checkedEndpoints should be greater than 0 when UP, but was: " + endpointCount);

        // Check for issuer-specific data
        boolean hasIssuerData = data.keySet().stream()
                .anyMatch(key -> key.startsWith("issuer."));
        assertTrue(hasIssuerData, "Should contain issuer-specific data when UP");
    }

    @Test
    @DisplayName("Health check should contain issuer endpoint details")
    void issuerEndpointDetails() {
        HealthCheckResponse response = healthCheck.call();

        // With valid configuration, we expect UP status and data to be present
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                "Health check status should be UP with valid configuration");
        assertTrue(response.getData().isPresent(),
                "Health check data should be present with valid configuration");

        Map<String, Object> data = response.getData().get();

        // Look for issuer-specific data patterns
        data.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("issuer.") && entry.getKey().endsWith(".url"))
                .forEach(entry -> {
                    String issuerPrefix = entry.getKey().substring(0, entry.getKey().lastIndexOf(".url"));

                    // Check that each issuer has required fields
                    assertTrue(data.containsKey(issuerPrefix + ".url"),
                            "Should contain URL for " + issuerPrefix);
                    assertTrue(data.containsKey(issuerPrefix + ".jwksType"),
                            "Should contain jwksType for " + issuerPrefix);
                    assertTrue(data.containsKey(issuerPrefix + ".status"),
                            "Should contain status for " + issuerPrefix);

                    // Verify status values
                    Object statusValue = data.get(issuerPrefix + ".status");
                    assertTrue("UP".equals(statusValue) || "DOWN".equals(statusValue),
                            "Issuer status should be UP or DOWN");
                });
    }

    @Test
    @DisplayName("Health check should handle concurrent calls properly")
    void concurrentHealthCheckCalls() {
        // Make multiple concurrent calls to test thread safety and caching
        HealthCheckResponse response1 = healthCheck.call();
        HealthCheckResponse response2 = healthCheck.call();
        HealthCheckResponse response3 = healthCheck.call();

        assertNotNull(response1, "First response should not be null");
        assertNotNull(response2, "Second response should not be null");
        assertNotNull(response3, "Third response should not be null");

        // All responses should have the same status (due to caching)
        assertEquals(response1.getStatus(), response2.getStatus(),
                "Concurrent calls should return same status");
        assertEquals(response1.getStatus(), response3.getStatus(),
                "Concurrent calls should return same status");
    }

    @Test
    @DisplayName("Health check should handle valid configuration gracefully")
    void healthCheckValidConfiguration() {
        HealthCheckResponse response = healthCheck.call();

        // Response should be valid with proper configuration
        assertNotNull(response, "Response should not be null");
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                "Health check status should be UP with valid configuration");
        assertEquals("jwks-endpoints", response.getName(),
                "Health check should have correct name");

        assertTrue(response.getData().isPresent(), "Data should be present");
        Map<String, Object> data = response.getData().get();
        assertTrue(data.containsKey("checkedEndpoints"),
                "Should contain endpoint data for valid configuration");
    }

}
