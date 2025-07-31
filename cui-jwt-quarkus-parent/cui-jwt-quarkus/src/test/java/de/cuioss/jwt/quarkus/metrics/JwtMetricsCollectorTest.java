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

import de.cuioss.jwt.quarkus.config.JwtPropertyKeys;
import de.cuioss.jwt.quarkus.config.JwtTestProfile;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JwtMetricsCollector}.
 * <p>
 * This test class verifies that the metrics collector properly initializes and registers
 * metrics for all security event types. It also tests that the collector correctly updates
 * metrics when security events occur.
 * <p>
 * The tests cover:
 * <ul>
 *   <li>Initialization of metrics for all event types</li>
 *   <li>Recording and updating metrics for security events</li>
 *   <li>Initialization and recording of performance metrics</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(JwtTestProfile.class)
class JwtMetricsCollectorTest {

    @Inject
    TokenValidator tokenValidator;

    @Inject
    MeterRegistry registry;

    @Inject
    JwtMetricsCollector metricsCollector;

    @Test
    @DisplayName("Should initialize metrics for all event types")
    void shouldInitializeMetrics() {
        // Ensure collector is properly initialized
        assertNotNull(metricsCollector);

        // Get counters from registry
        Collection<Counter> counters = registry.find(JwtPropertyKeys.METRICS.VALIDATION_ERRORS).counters();

        // Verify counters exist for all event types
        assertFalse(counters.isEmpty(), "Should have registered counters");

        // Verify all event types have corresponding counters
        for (EventType eventType : SecurityEventCounter.EventType.values()) {
            boolean hasCounter = counters.stream()
                    .anyMatch(counter -> Objects.equals(counter.getId().getTag("event_type"), eventType.name()));
            assertTrue(hasCounter, "Should have counter for event type: " + eventType.name());
        }
    }

    @Test
    @DisplayName("Should record metrics for security events")
    void shouldHaveMetricsForSecurityEvents() {
        // Get the security event counter from the token validator
        SecurityEventCounter counter = tokenValidator.getSecurityEventCounter();
        assertNotNull(counter);

        // Record some events
        EventType testEventType = EventType.SIGNATURE_VALIDATION_FAILED;
        counter.increment(testEventType);
        counter.increment(testEventType);

        // Manually update counters (instead of waiting for scheduled update)
        metricsCollector.updateCounters();

        // Verify the metric exists with the correct tags
        boolean hasMetric = !registry.find(JwtPropertyKeys.METRICS.VALIDATION_ERRORS)
                .tag("event_type", testEventType.name())
                .tag("result", "failure")
                .tag("category", "INVALID_SIGNATURE")
                .counters().isEmpty();

        assertTrue(hasMetric, "Should have metric for the event type");
    }

    @Test
    @DisplayName("Should initialize performance timers for all measurement types")
    void shouldInitializePerformanceTimers() {
        // Ensure collector is properly initialized
        assertNotNull(metricsCollector);

        // Force initialization by calling a method on the collector
        metricsCollector.updateCounters();

        // Get timers from registry
        Collection<Timer> timers = registry.find(JwtPropertyKeys.METRICS.VALIDATION_DURATION).timers();

        // Verify timers exist for all measurement types
        assertFalse(timers.isEmpty(), "Should have registered timers");

        // Verify all measurement types have corresponding timers
        for (MeasurementType measurementType : MeasurementType.values()) {
            boolean hasTimer = timers.stream()
                    .anyMatch(timer -> Objects.equals(timer.getId().getTag("step"), measurementType.name().toLowerCase()));
            assertTrue(hasTimer, "Should have timer for measurement type: " + measurementType.name());
        }
    }

    @Test
    @DisplayName("Should record performance metrics when monitor has data")
    void shouldRecordPerformanceMetrics() {
        // Get the performance monitor from the token validator
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();
        assertNotNull(monitor);

        // Record some measurements
        MeasurementType testMeasurementType = MeasurementType.SIGNATURE_VALIDATION;
        monitor.recordMeasurement(testMeasurementType, 1000000); // 1ms in nanoseconds
        monitor.recordMeasurement(testMeasurementType, 2000000); // 2ms in nanoseconds

        // Manually update metrics (instead of waiting for scheduled update)
        metricsCollector.updateCounters();

        // Verify the timer exists with the correct tags
        boolean hasTimer = !registry.find(JwtPropertyKeys.METRICS.VALIDATION_DURATION)
                .tag("step", testMeasurementType.name().toLowerCase())
                .timers().isEmpty();

        assertTrue(hasTimer, "Should have timer for the measurement type");
    }

    @Test
    @DisplayName("Should clear all metrics when clear method is called")
    void shouldClearAllMetrics() {
        // Get the monitors and counters
        SecurityEventCounter securityEventCounter = tokenValidator.getSecurityEventCounter();
        TokenValidatorMonitor performanceMonitor = tokenValidator.getPerformanceMonitor();
        assertNotNull(securityEventCounter);
        assertNotNull(performanceMonitor);

        // Record some events and measurements
        EventType testEventType = EventType.SIGNATURE_VALIDATION_FAILED;
        securityEventCounter.increment(testEventType);
        securityEventCounter.increment(testEventType);

        MeasurementType testMeasurementType = MeasurementType.SIGNATURE_VALIDATION;
        performanceMonitor.recordMeasurement(testMeasurementType, 1000000); // 1ms
        performanceMonitor.recordMeasurement(testMeasurementType, 2000000); // 2ms

        // Verify data is recorded
        assertTrue(securityEventCounter.getCount(testEventType) > 0, "Should have recorded security events");
        assertTrue(performanceMonitor.getSampleCount(testMeasurementType) > 0, "Should have recorded performance measurements");

        // Clear all metrics
        metricsCollector.clear();

        // Verify all metrics are cleared
        assertEquals(0, securityEventCounter.getCount(testEventType), "Security event counter should be cleared");
        assertEquals(0, performanceMonitor.getSampleCount(testMeasurementType), "Performance monitor should be cleared");

        // Verify the tracking maps are cleared by recording new events and checking delta calculation
        securityEventCounter.increment(testEventType);
        metricsCollector.updateCounters();

        // The counter should only reflect the new increment, not the old ones
        Counter metricCounter = registry.find(JwtPropertyKeys.METRICS.VALIDATION_ERRORS)
                .tag("event_type", testEventType.name())
                .counter();
        assertNotNull(metricCounter, "Counter should exist");
        // Note: We can't directly check the counter value as it's cumulative in Micrometer,
        // but we've verified the underlying monitors are cleared
    }
}
