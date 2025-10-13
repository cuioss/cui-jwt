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
package de.cuioss.sheriff.oauth.core.metrics;

import de.cuioss.http.client.retry.RetryContext;
import de.cuioss.sheriff.oauth.core.JWTValidationLogMessages;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
class JwtRetryMetricsTest {

    private TokenValidatorMonitor monitor;
    private JwtRetryMetrics retryMetrics;
    private RetryContext context;

    @BeforeEach
    void setUp() {
        // Create monitor with all retry measurement types enabled
        Set<MeasurementType> enabledTypes = Set.of(
                MeasurementType.RETRY_ATTEMPT,
                MeasurementType.RETRY_COMPLETE,
                MeasurementType.RETRY_DELAY
        );

        monitor = new TokenValidatorMonitor(100, enabledTypes);
        retryMetrics = new JwtRetryMetrics(monitor);
        context = RetryContext.initial("test-operation");
    }

    @Test
    @DisplayName("Should create JwtRetryMetrics with TokenValidatorMonitor")
    void shouldCreateJwtRetryMetricsWithTokenValidatorMonitor() {
        assertNotNull(retryMetrics, "JWT retry metrics instance should not be null");
    }

    @Test
    @DisplayName("Should record retry completion metrics")
    void shouldRecordRetryCompletionMetrics() {
        Duration totalDuration = Duration.ofMillis(1500);

        retryMetrics.recordRetryComplete(context, totalDuration, true, 3);

        // Verify that the measurement was recorded
        assertEquals(1, monitor.getSampleCount(MeasurementType.RETRY_COMPLETE), "Should record one RETRY_COMPLETE measurement");

        // Verify the info log for successful completion
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                JWTValidationLogMessages.INFO.RETRY_OPERATION_COMPLETED.resolveIdentifierString());

        // Verify the recorded value matches the duration in nanoseconds
        var metricsOpt = monitor.getValidationMetrics(MeasurementType.RETRY_COMPLETE);
        assertTrue(metricsOpt.isPresent(), "Validation metrics should be available after recording measurement");

        var metrics = metricsOpt.get();
        assertEquals(1, metrics.sampleCount(), "Metrics should show exactly one sample recorded");

        // The recorded value should be close to the expected nanoseconds (allowing for small variations)
        Duration recordedDuration = metrics.p50(); // median of single value
        long expectedNanos = totalDuration.toNanos();
        long recordedNanos = recordedDuration.toNanos();

        // Allow for small timing variations (within 1ms)
        assertTrue(Math.abs(recordedNanos - expectedNanos) < Duration.ofMillis(1).toNanos(),
                "Recorded duration should match expected duration within 1ms tolerance: expected ~%d nanos, got %d nanos".formatted(expectedNanos, recordedNanos));
    }

    @Test
    @DisplayName("Should record retry attempt metrics")
    void shouldRecordRetryAttemptMetrics() {
        Duration attemptDuration = Duration.ofMillis(250);

        retryMetrics.recordRetryAttempt(context, 1, attemptDuration, false);

        // Verify that the measurement was recorded
        assertEquals(1, monitor.getSampleCount(MeasurementType.RETRY_ATTEMPT), "Should record one RETRY_ATTEMPT measurement");
    }

    @Test
    @DisplayName("Should record retry delay metrics")
    void shouldRecordRetryDelayMetrics() {
        Duration plannedDelay = Duration.ofMillis(1000);
        Duration actualDelay = Duration.ofMillis(1050);

        retryMetrics.recordRetryDelay(context, 2, plannedDelay, actualDelay);

        // Verify that the measurement was recorded
        assertEquals(1, monitor.getSampleCount(MeasurementType.RETRY_DELAY), "Should record one RETRY_DELAY measurement");

        // Verify the recorded value matches the actual delay
        var metricsOpt = monitor.getValidationMetrics(MeasurementType.RETRY_DELAY);
        assertTrue(metricsOpt.isPresent(), "Validation metrics should be available after recording delay measurement");

        var metrics = metricsOpt.get();
        assertEquals(1, metrics.sampleCount(), "Delay metrics should show exactly one sample recorded");
    }

    @Test
    @DisplayName("Should handle retry start without recording metrics")
    void shouldHandleRetryStartWithoutRecordingMetrics() {
        // recordRetryStart doesn't record metrics, just logs
        assertDoesNotThrow(() -> retryMetrics.recordRetryStart(context), "Recording retry start should not throw exceptions");

        // Verify no measurements were recorded for any retry types
        assertEquals(0, monitor.getSampleCount(MeasurementType.RETRY_COMPLETE), "Retry start should not record RETRY_COMPLETE measurements");
        assertEquals(0, monitor.getSampleCount(MeasurementType.RETRY_ATTEMPT), "Retry start should not record RETRY_ATTEMPT measurements");
        assertEquals(0, monitor.getSampleCount(MeasurementType.RETRY_DELAY), "Retry start should not record RETRY_DELAY measurements");
    }

    @Test
    @DisplayName("Should record multiple measurements correctly")
    void shouldRecordMultipleMeasurementsCorrectly() {
        // Record multiple retry attempts
        retryMetrics.recordRetryAttempt(context, 1, Duration.ofMillis(100), false);
        retryMetrics.recordRetryAttempt(context, 2, Duration.ofMillis(150), false);
        retryMetrics.recordRetryAttempt(context, 3, Duration.ofMillis(200), true);

        // Record delays
        retryMetrics.recordRetryDelay(context, 2, Duration.ofMillis(500), Duration.ofMillis(505));
        retryMetrics.recordRetryDelay(context, 3, Duration.ofMillis(1000), Duration.ofMillis(1020));

        // Record completion
        retryMetrics.recordRetryComplete(context, Duration.ofMillis(2000), true, 3);

        // Verify all measurements were recorded
        assertEquals(3, monitor.getSampleCount(MeasurementType.RETRY_ATTEMPT), "Should record 3 retry attempts");
        assertEquals(2, monitor.getSampleCount(MeasurementType.RETRY_DELAY), "Should record 2 retry delays");
        assertEquals(1, monitor.getSampleCount(MeasurementType.RETRY_COMPLETE), "Should record 1 retry completion");
    }

    @Test
    @DisplayName("Should respect monitor enabled types")
    void shouldRespectMonitorEnabledTypes() {
        // Create monitor with only RETRY_COMPLETE enabled
        Set<MeasurementType> limitedTypes = Set.of(MeasurementType.RETRY_COMPLETE);
        TokenValidatorMonitor limitedMonitor = new TokenValidatorMonitor(100, limitedTypes);
        JwtRetryMetrics limitedMetrics = new JwtRetryMetrics(limitedMonitor);

        // Record all types of measurements
        limitedMetrics.recordRetryAttempt(context, 1, Duration.ofMillis(100), false);
        limitedMetrics.recordRetryDelay(context, 2, Duration.ofMillis(500), Duration.ofMillis(505));
        limitedMetrics.recordRetryComplete(context, Duration.ofMillis(1000), true, 2);

        // Only RETRY_COMPLETE should have been recorded
        assertEquals(0, limitedMonitor.getSampleCount(MeasurementType.RETRY_ATTEMPT), "Disabled RETRY_ATTEMPT should not be recorded");
        assertEquals(0, limitedMonitor.getSampleCount(MeasurementType.RETRY_DELAY), "Disabled RETRY_DELAY should not be recorded");
        assertEquals(1, limitedMonitor.getSampleCount(MeasurementType.RETRY_COMPLETE), "Enabled RETRY_COMPLETE should be recorded");
    }

    @Test
    @DisplayName("Should log failure on unsuccessful retry completion")
    void shouldLogFailureOnUnsuccessfulRetryCompletion() {
        Duration totalDuration = Duration.ofMillis(2500);

        retryMetrics.recordRetryComplete(context, totalDuration, false, 3);

        // Verify that the measurement was recorded
        assertEquals(1, monitor.getSampleCount(MeasurementType.RETRY_COMPLETE),
                "Should record one RETRY_COMPLETE measurement even for failure");

        // Verify the warning log for failed completion
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.RETRY_OPERATION_FAILED.resolveIdentifierString());
    }
}