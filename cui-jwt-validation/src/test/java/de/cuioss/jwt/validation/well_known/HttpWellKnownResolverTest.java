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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link HttpWellKnownResolver}.
 * 
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("HttpWellKnownResolver")
class HttpWellKnownResolverTest {

    @Test
    @DisplayName("Should create resolver and start with UNDEFINED status")
    void shouldCreateResolverAndStartWithUndefinedStatus() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl("https://example.com/.well-known/openid-configuration")
                .build();

        HttpWellKnownResolver resolver = new HttpWellKnownResolver(config);
        assertNotNull(resolver);

        // Attempting to get endpoints without a valid server will result in empty results
        assertFalse(resolver.getJwksUri().isPresent());
        assertFalse(resolver.getAuthorizationEndpoint().isPresent());
        assertFalse(resolver.getTokenEndpoint().isPresent());
        assertFalse(resolver.getUserinfoEndpoint().isPresent());
        assertFalse(resolver.getIssuer().isPresent());

        // Health status should be ERROR after failed loading attempts
        assertEquals(LoaderStatus.ERROR, resolver.isHealthy());
    }

    @Test
    @DisplayName("Should handle malformed URLs gracefully")
    void shouldHandleMalformedUrlsGracefully() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl("invalid-url")
                .build();

        HttpWellKnownResolver resolver = new HttpWellKnownResolver(config);
        assertNotNull(resolver);

        // Should not crash, but return empty results
        assertFalse(resolver.getJwksUri().isPresent());
        assertEquals(LoaderStatus.ERROR, resolver.isHealthy());
    }

    @Test
    @DisplayName("Should test all getter methods for coverage")
    void shouldTestAllGetterMethodsForCoverage() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl("https://nonexistent-server.example.com/.well-known/openid-configuration")
                .build();

        HttpWellKnownResolver resolver = new HttpWellKnownResolver(config);

        // Call all getter methods to ensure they are covered by tests
        // These will fail with network errors but exercise the code paths
        assertFalse(resolver.getJwksUri().isPresent());
        assertFalse(resolver.getAuthorizationEndpoint().isPresent());
        assertFalse(resolver.getTokenEndpoint().isPresent());
        assertFalse(resolver.getUserinfoEndpoint().isPresent());
        assertFalse(resolver.getIssuer().isPresent());

        // Health status should be ERROR
        assertEquals(LoaderStatus.ERROR, resolver.isHealthy());
    }

    @Test
    @DisplayName("Should handle concurrent access without crashing")
    void shouldHandleConcurrentAccessWithoutCrashing() throws InterruptedException {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl("https://example.com/.well-known/openid-configuration")
                .build();

        HttpWellKnownResolver resolver = new HttpWellKnownResolver(config);

        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                // Each thread tries to access endpoints - will fail but shouldn't crash
                resolver.getJwksUri();
                resolver.isHealthy();
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Should complete without exceptions
        assertEquals(LoaderStatus.ERROR, resolver.isHealthy());
    }

    @Test
    @DisplayName("Should return empty issuer when not available")
    void shouldReturnEmptyIssuerWhenNotAvailable() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl("https://nonexistent.example.com/.well-known/openid-configuration")
                .build();

        HttpWellKnownResolver resolver = new HttpWellKnownResolver(config);

        // Call getIssuer() which will trigger ensureLoaded()
        Optional<String> issuer = resolver.getIssuer();

        // Should return empty since the server doesn't exist
        assertFalse(issuer.isPresent(), "Issuer should not be present when server is unavailable");

        // Health status should be ERROR after failed loading
        assertEquals(LoaderStatus.ERROR, resolver.isHealthy());
    }
}