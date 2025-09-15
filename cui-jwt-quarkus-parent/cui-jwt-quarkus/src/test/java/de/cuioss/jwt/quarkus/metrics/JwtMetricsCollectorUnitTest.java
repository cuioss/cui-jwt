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
package de.cuioss.jwt.quarkus.metrics;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;
import static de.cuioss.test.juli.LogAsserts.assertSingleLogMessagePresent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link JwtMetricsCollector} focusing on initialization logging.
 * This test class verifies logging behavior without CDI timing dependencies.
 */
@EnableTestLogger
class JwtMetricsCollectorUnitTest {

    @Test
    @DisplayName("Should log during initialization")
    void shouldLogDuringInitialization() {
        // Given - real dependencies (no mocking needed)
        MeterRegistry registry = new SimpleMeterRegistry();
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier("test-issuer")
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
        TokenValidator tokenValidator = TokenValidator.builder()
                .issuerConfig(issuerConfig)
                .build();

        // When - initialize collector
        JwtMetricsCollector collector = new JwtMetricsCollector(registry, tokenValidator);

        assertDoesNotThrow(collector::initialize, "Initialization should not throw");

        // Then - verify logging occurred
        assertSingleLogMessagePresent(TestLogLevel.INFO, INFO.INITIALIZING_JWT_METRICS_COLLECTOR.resolveIdentifierString());
        assertSingleLogMessagePresent(TestLogLevel.INFO, INFO.JWT_METRICS_COLLECTOR_INITIALIZED.format(EventType.values().length));
    }

    @Test
    @DisplayName("Should log during metrics clearing")
    void shouldLogDuringClearing() {
        // Given - initialized collector
        MeterRegistry registry = new SimpleMeterRegistry();
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier("test-issuer")
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
        TokenValidator tokenValidator = TokenValidator.builder()
                .issuerConfig(issuerConfig)
                .build();

        JwtMetricsCollector collector = new JwtMetricsCollector(registry, tokenValidator);
        collector.initialize();

        // When - clear metrics
        assertDoesNotThrow(collector::clear, "Clear should not throw");

        // Then - verify logging occurred
        assertSingleLogMessagePresent(TestLogLevel.INFO, INFO.CLEARING_JWT_METRICS.resolveIdentifierString());
        assertSingleLogMessagePresent(TestLogLevel.INFO, INFO.JWT_METRICS_CLEARED.resolveIdentifierString());
    }

    @Test
    @DisplayName("Should handle initialization with null dependencies gracefully")
    void shouldHandleNullDependencies() {
        // Given - null dependencies (defensive programming test)
        assertThrows(NullPointerException.class, () -> new JwtMetricsCollector(null, null),
                "Constructor should reject null dependencies");
    }
}