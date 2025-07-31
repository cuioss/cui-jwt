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

import de.cuioss.jwt.validation.metrics.NoOpMetricsTicker;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link HttpMetricsMonitor}.
 * <p>
 * Tests cover:
 * <ul>
 *   <li>Basic functionality - recording measurements and calculating averages</li>
 *   <li>Request status tracking - counting different outcome types</li>
 *   <li>Thread safety - massive parallel operations from multiple threads</li>
 *   <li>Performance characteristics - minimal overhead validation</li>
 *   <li>Edge cases - boundary conditions and error scenarios</li>
 *   <li>Exponential moving average behavior - responsiveness testing</li>
 * </ul>
 * <p>
 * This test class ensures that HTTP metrics monitoring operations are accurate,
 * thread-safe, and have minimal impact on runtime performance.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests HttpMetricsMonitor functionality")
class HttpMetricsMonitorTest {

    private HttpMetricsMonitor createMonitorWithEnabledTypes(HttpMeasurementType... types) {
        var config = HttpMetricsMonitorConfig.builder();
        for (HttpMeasurementType type : types) {
            config.measurementType(type);
        }
        return config.build().createMonitor();
    }

    private static final CuiLogger log = new CuiLogger(HttpMetricsMonitorTest.class);

    @Test
    @DisplayName("Should create monitor with initial state")
    void shouldCreateMonitorWithInitialState() {
        var monitor = new HttpMetricsMonitor();
        assertNotNull(monitor, "Monitor should be created");

        // Verify all measurement types are enabled by default
        assertEquals(HttpMetricsMonitorConfig.ALL_MEASUREMENT_TYPES, monitor.getEnabledTypes(),
                "All measurement types should be enabled by default");

        // Verify initial state - no measurements recorded
        for (HttpMeasurementType type : HttpMeasurementType.values()) {
            assertEquals(Duration.ZERO, monitor.getAverageDuration(type),
                    "Initial average should be zero for " + type);
            assertEquals(0, monitor.getSampleCount(type),
                    "Initial sample count should be zero for " + type);
        }

        // Verify initial request status counts
        for (HttpRequestStatus status : HttpRequestStatus.values()) {
            assertEquals(0, monitor.getRequestStatusCount(status),
                    "Initial request status count should be zero for " + status);
        }
    }

    @Test
    @DisplayName("Should record single measurement")
    void shouldRecordSingleMeasurement() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.TOKEN_EXTRACTION;

        // Record 1 millisecond (1,000,000 nanoseconds)
        long durationNanos = 1_000_000;
        monitor.recordMeasurement(measurementType, durationNanos);

        assertEquals(1, monitor.getSampleCount(measurementType),
                "Should have one sample recorded");

        Duration average = monitor.getAverageDuration(measurementType);
        assertEquals(Duration.ofMillis(1), average,
                "Average should equal the single measurement");
    }

    @Test
    @DisplayName("Should record multiple measurements and use exponential moving average")
    void shouldRecordMultipleMeasurementsWithExponentialMovingAverage() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.HEADER_EXTRACTION;

        // Record measurements: 10ms, 20ms, 30ms
        monitor.recordMeasurement(measurementType, 10_000_000); // 10ms
        monitor.recordMeasurement(measurementType, 20_000_000); // 20ms
        monitor.recordMeasurement(measurementType, 30_000_000); // 30ms

        assertEquals(3, monitor.getSampleCount(measurementType),
                "Should have three samples recorded");

        Duration average = monitor.getAverageDuration(measurementType);

        // With exponential moving average (alpha=0.1), the result should be between first and last measurement
        assertTrue(average.toMillis() >= 10 && average.toMillis() <= 30,
                "Average should be between 10ms and 30ms: " + average.toMillis() + "ms");

        // The exponential moving average should not be exactly the simple average (20ms)
        // Due to the weighting algorithm, it will be influenced by the sequence of measurements
        assertNotEquals(20, average.toMillis(),
                "Exponential moving average should differ from simple arithmetic mean");
    }

    @Test
    @DisplayName("Should handle microsecond precision")
    void shouldHandleMicrosecondPrecision() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.AUTHORIZATION_CHECK;

        // Record 500 microseconds (500,000 nanoseconds)
        long durationNanos = 500_000;
        monitor.recordMeasurement(measurementType, durationNanos);

        Duration average = monitor.getAverageDuration(measurementType);
        assertEquals(Duration.ofNanos(500_000), average,
                "Should maintain microsecond precision");
    }

    @Test
    @DisplayName("Should isolate measurements between types")
    void shouldIsolateMeasurementsBetweenTypes() {
        var monitor = new HttpMetricsMonitor();

        // Record different values for different types
        monitor.recordMeasurement(HttpMeasurementType.REQUEST_PROCESSING, 5_000_000); // 5ms
        monitor.recordMeasurement(HttpMeasurementType.RESPONSE_FORMATTING, 1_000_000);    // 1ms

        assertEquals(Duration.ofMillis(5),
                monitor.getAverageDuration(HttpMeasurementType.REQUEST_PROCESSING),
                "Request processing average should be 5ms");

        assertEquals(Duration.ofMillis(1),
                monitor.getAverageDuration(HttpMeasurementType.RESPONSE_FORMATTING),
                "Response formatting average should be 1ms");

        assertEquals(Duration.ZERO,
                monitor.getAverageDuration(HttpMeasurementType.TOKEN_EXTRACTION),
                "Unused measurement type should remain at zero");
    }

    @Test
    @DisplayName("Should record and track request statuses")
    void shouldRecordAndTrackRequestStatuses() {
        var monitor = new HttpMetricsMonitor();

        // Record different request outcomes
        monitor.recordRequestStatus(HttpRequestStatus.SUCCESS);
        monitor.recordRequestStatus(HttpRequestStatus.SUCCESS);
        monitor.recordRequestStatus(HttpRequestStatus.MISSING_TOKEN);
        monitor.recordRequestStatus(HttpRequestStatus.INVALID_TOKEN);

        assertEquals(2, monitor.getRequestStatusCount(HttpRequestStatus.SUCCESS),
                "Should have two successful requests");
        assertEquals(1, monitor.getRequestStatusCount(HttpRequestStatus.MISSING_TOKEN),
                "Should have one missing token request");
        assertEquals(1, monitor.getRequestStatusCount(HttpRequestStatus.INVALID_TOKEN),
                "Should have one invalid token request");
        assertEquals(0, monitor.getRequestStatusCount(HttpRequestStatus.INSUFFICIENT_PERMISSIONS),
                "Should have zero insufficient permissions requests");

        // Test getting all counts
        Map<HttpRequestStatus, Long> allCounts = monitor.getRequestStatusCounts();
        assertEquals(2L, allCounts.get(HttpRequestStatus.SUCCESS));
        assertEquals(1L, allCounts.get(HttpRequestStatus.MISSING_TOKEN));
        assertEquals(1L, allCounts.get(HttpRequestStatus.INVALID_TOKEN));
        assertEquals(0L, allCounts.get(HttpRequestStatus.INSUFFICIENT_PERMISSIONS));
    }

    @Test
    @DisplayName("Should reset specific measurement type")
    void shouldResetSpecificMeasurementType() {
        var monitor = new HttpMetricsMonitor();

        // Record measurements for multiple types
        monitor.recordMeasurement(HttpMeasurementType.REQUEST_PROCESSING, 5_000_000);
        monitor.recordMeasurement(HttpMeasurementType.HEADER_EXTRACTION, 1_000_000);

        // Reset only request processing
        monitor.reset(HttpMeasurementType.REQUEST_PROCESSING);

        assertEquals(Duration.ZERO,
                monitor.getAverageDuration(HttpMeasurementType.REQUEST_PROCESSING),
                "Reset measurement type should be zero");
        assertEquals(0, monitor.getSampleCount(HttpMeasurementType.REQUEST_PROCESSING),
                "Reset sample count should be zero");

        assertEquals(Duration.ofMillis(1),
                monitor.getAverageDuration(HttpMeasurementType.HEADER_EXTRACTION),
                "Other measurement types should remain unchanged");
    }

    @Test
    @DisplayName("Should reset all measurements and counters")
    void shouldResetAllMeasurementsAndCounters() {
        var monitor = new HttpMetricsMonitor();

        // Record measurements for all types
        for (HttpMeasurementType type : HttpMeasurementType.values()) {
            monitor.recordMeasurement(type, 1_000_000);
        }

        // Record request statuses
        for (HttpRequestStatus status : HttpRequestStatus.values()) {
            monitor.recordRequestStatus(status);
        }

        // Reset all
        monitor.resetAll();

        // Verify all measurements are reset
        for (HttpMeasurementType type : HttpMeasurementType.values()) {
            assertEquals(Duration.ZERO, monitor.getAverageDuration(type),
                    "All measurement types should be reset: " + type);
            assertEquals(0, monitor.getSampleCount(type),
                    "All sample counts should be reset: " + type);
        }

        // Verify all request status counts are reset
        for (HttpRequestStatus status : HttpRequestStatus.values()) {
            assertEquals(0, monitor.getRequestStatusCount(status),
                    "All request status counts should be reset: " + status);
        }
    }

    @Test
    @DisplayName("Should handle zero and negative durations")
    void shouldHandleZeroAndNegativeDurations() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.TOKEN_EXTRACTION;

        // Record zero duration
        monitor.recordMeasurement(measurementType, 0);
        assertEquals(Duration.ZERO, monitor.getAverageDuration(measurementType),
                "Zero duration should be handled correctly");

        // Record negative duration (should be clamped to zero)
        monitor.recordMeasurement(measurementType, -1000);
        assertEquals(Duration.ZERO, monitor.getAverageDuration(measurementType),
                "Negative duration should be clamped to zero");
    }

    @Test
    @DisplayName("Should be thread-safe under moderate load")
    void shouldBeThreadSafeUnderModerateLoad() {
        var threadCount = 10;
        var measurementsPerThread = 1000;
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.REQUEST_PROCESSING;
        var startLatch = new CountDownLatch(1);
        var completedThreads = new AtomicInteger(0);
        var executor = Executors.newFixedThreadPool(threadCount);

        // Submit all threads
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < measurementsPerThread; j++) {
                        // Use different durations per thread for verification
                        long duration = (threadId + 1) * 1_000_000L; // 1ms, 2ms, 3ms, etc.
                        monitor.recordMeasurement(measurementType, duration);

                        // Also record request statuses
                        HttpRequestStatus status = HttpRequestStatus.values()[j % HttpRequestStatus.values().length];
                        monitor.recordRequestStatus(status);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completedThreads.incrementAndGet();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        await("All threads to complete their measurements")
                .atMost(15, SECONDS)
                .until(() -> completedThreads.get() == threadCount);

        executor.shutdown();

        // Verify measurements were recorded
        assertTrue(monitor.getSampleCount(measurementType) > 0,
                "Should have recorded measurements");

        Duration average = monitor.getAverageDuration(measurementType);
        assertTrue(average.toNanos() > 0,
                "Average should be positive: " + average.toNanos() + "ns");

        // Verify request status counts
        long totalStatusCounts = monitor.getRequestStatusCounts().values().stream()
                .mapToLong(Long::longValue)
                .sum();
        assertEquals(threadCount * measurementsPerThread, totalStatusCounts,
                "Total status counts should equal total operations");
    }

    @Test
    @DisplayName("Should handle massive parallel load")
    void shouldHandleMassiveParallelLoad() {
        var threadCount = 50;
        var measurementsPerThread = 5000;
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.AUTHORIZATION_CHECK;
        var startLatch = new CountDownLatch(1);
        var completedThreads = new AtomicInteger(0);
        var totalMeasurements = new AtomicLong(0);
        var executor = Executors.newFixedThreadPool(threadCount);

        // Submit all threads
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < measurementsPerThread; j++) {
                        // Use consistent duration for average calculation verification
                        monitor.recordMeasurement(measurementType, 2_000_000); // 2ms
                        totalMeasurements.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completedThreads.incrementAndGet();
                }
            });
        }

        // Measure metrics impact
        long startTime = System.nanoTime();
        startLatch.countDown();

        // Wait for all threads to complete
        await("All threads to complete massive parallel measurements")
                .atMost(30, SECONDS)
                .until(() -> completedThreads.get() == threadCount);

        long endTime = System.nanoTime();
        long totalDurationMs = (endTime - startTime) / 1_000_000;

        executor.shutdown();

        // Verify measurements were recorded
        assertTrue(monitor.getSampleCount(measurementType) > 0,
                "Should have recorded measurements under massive load");

        // Verify average is reasonable (should be close to 2ms due to exponential moving average)
        Duration average = monitor.getAverageDuration(measurementType);
        assertTrue(average.toMillis() >= 1 && average.toMillis() <= 3,
                "Average should be close to 2ms under massive load: " + average.toMillis() + "ms");

        // Performance assertion - should complete reasonably quickly
        double measurementsPerMs = totalMeasurements.get() / (double) totalDurationMs;

        assertTrue(measurementsPerMs > 500, // Should handle at least 500 measurements per millisecond
                "Performance too slow: %.1f measurements/ms (expected > 500)".formatted(measurementsPerMs));

        log.info("Massive parallel test: {} threads, {} measurements/thread, {:.1f} measurements/ms",
                threadCount, measurementsPerThread, measurementsPerMs);
    }

    @Test
    @DisplayName("Should handle concurrent access to different measurement types and statuses")
    void shouldHandleConcurrentAccessToDifferentMeasurementTypesAndStatuses() {
        var monitor = new HttpMetricsMonitor();
        var measurementTypes = HttpMeasurementType.values();
        var requestStatuses = HttpRequestStatus.values();
        var threadsPerType = 5;
        var measurementsPerThread = 100;
        var startLatch = new CountDownLatch(1);
        var completedThreads = new AtomicInteger(0);
        var executor = Executors.newFixedThreadPool(measurementTypes.length * threadsPerType);

        // Submit threads for each measurement type
        for (int typeIndex = 0; typeIndex < measurementTypes.length; typeIndex++) {
            final var measurementType = measurementTypes[typeIndex];
            final long baseDuration = (typeIndex + 1) * 1_000_000L; // 1ms, 2ms, 3ms, etc.

            for (int threadIndex = 0; threadIndex < threadsPerType; threadIndex++) {
                final int statusIndex = threadIndex % requestStatuses.length;
                final var requestStatus = requestStatuses[statusIndex];

                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < measurementsPerThread; j++) {
                            monitor.recordMeasurement(measurementType, baseDuration);
                            monitor.recordRequestStatus(requestStatus);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completedThreads.incrementAndGet();
                    }
                });
            }
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        int expectedThreads = measurementTypes.length * threadsPerType;
        await("All measurement type threads to complete")
                .atMost(20, SECONDS)
                .until(() -> completedThreads.get() == expectedThreads);

        executor.shutdown();

        // Verify each measurement type has recorded samples
        for (int i = 0; i < measurementTypes.length; i++) {
            var measurementType = measurementTypes[i];

            assertTrue(monitor.getSampleCount(measurementType) > 0,
                    "Should have samples for " + measurementType);

            Duration average = monitor.getAverageDuration(measurementType);
            assertTrue(average.toMillis() > 0,
                    "Average for " + measurementType + " should be positive");
        }

        // Verify request status counts
        long totalRequestCounts = monitor.getRequestStatusCounts().values().stream()
                .mapToLong(Long::longValue)
                .sum();
        assertEquals(expectedThreads * measurementsPerThread, totalRequestCounts,
                "Total request status counts should match expected operations");
    }

    @RepeatedTest(5)
    @DisplayName("Should be consistent across multiple runs")
    void shouldBeConsistentAcrossMultipleRuns() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.HEADER_EXTRACTION;

        // Record consistent measurements
        IntStream.range(0, 100).forEach(i ->
                monitor.recordMeasurement(measurementType, 1_500_000)); // 1.5ms each

        Duration average = monitor.getAverageDuration(measurementType);
        // With exponential moving average, should converge to the input value
        assertTrue(Math.abs(average.toNanos() - 1_500_000) < 100_000,
                "Average should be close to 1.5ms: " + average.toNanos() + "ns");

        assertEquals(100, monitor.getSampleCount(measurementType),
                "Sample count should be consistent");
    }

    @Test
    @DisplayName("Should have minimal performance overhead")
    void shouldHaveMinimalPerformanceOverhead() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.TOKEN_EXTRACTION;
        var iterations = 100_000;

        // Measure time for recording measurements
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            monitor.recordMeasurement(measurementType, 1_000_000);
        }
        long endTime = System.nanoTime();

        long totalOverheadNanos = endTime - startTime;
        double overheadPerMeasurementNanos = totalOverheadNanos / (double) iterations;

        // Overhead should be minimal (less than 2 microseconds per measurement)
        assertTrue(overheadPerMeasurementNanos < 2000,
                "Performance overhead too high: %.1f ns/measurement (expected < 2000)".formatted(
                        overheadPerMeasurementNanos));

        log.info("Performance overhead: {:.1f} ns per measurement", overheadPerMeasurementNanos);
    }

    @Test
    @DisplayName("Should verify enum descriptions")
    void shouldVerifyEnumDescriptions() {
        // Verify HttpMeasurementType descriptions
        assertEquals("Total HTTP request processing",
                HttpMeasurementType.REQUEST_PROCESSING.getDescription());
        assertEquals("Authorization header extraction",
                HttpMeasurementType.HEADER_EXTRACTION.getDescription());
        assertEquals("Bearer token extraction",
                HttpMeasurementType.TOKEN_EXTRACTION.getDescription());
        assertEquals("Authorization requirements check",
                HttpMeasurementType.AUTHORIZATION_CHECK.getDescription());
        assertEquals("Error response formatting",
                HttpMeasurementType.RESPONSE_FORMATTING.getDescription());

        // Verify HttpRequestStatus enum values exist
        assertNotNull(HttpRequestStatus.SUCCESS);
        assertNotNull(HttpRequestStatus.MISSING_TOKEN);
        assertNotNull(HttpRequestStatus.INVALID_TOKEN);
        assertNotNull(HttpRequestStatus.INSUFFICIENT_PERMISSIONS);
        assertNotNull(HttpRequestStatus.ERROR);
    }

    @Test
    @DisplayName("Should create monitor with selective measurement types enabled")
    void shouldCreateMonitorWithSelectiveMeasurementTypes() {
        var monitor = createMonitorWithEnabledTypes(
                HttpMeasurementType.REQUEST_PROCESSING,
                HttpMeasurementType.TOKEN_EXTRACTION
        );

        // Verify only selected types are enabled
        assertTrue(monitor.isEnabled(HttpMeasurementType.REQUEST_PROCESSING));
        assertTrue(monitor.isEnabled(HttpMeasurementType.TOKEN_EXTRACTION));
        assertFalse(monitor.isEnabled(HttpMeasurementType.HEADER_EXTRACTION));
        assertFalse(monitor.isEnabled(HttpMeasurementType.AUTHORIZATION_CHECK));
        assertFalse(monitor.isEnabled(HttpMeasurementType.RESPONSE_FORMATTING));
    }

    @Test
    @DisplayName("Should ignore measurements for disabled types")
    void shouldIgnoreMeasurementsForDisabledTypes() {
        var monitor = createMonitorWithEnabledTypes(HttpMeasurementType.REQUEST_PROCESSING);

        // Record measurement for enabled type
        monitor.recordMeasurement(HttpMeasurementType.REQUEST_PROCESSING, 1_000_000);
        assertEquals(1, monitor.getSampleCount(HttpMeasurementType.REQUEST_PROCESSING));
        assertEquals(Duration.ofMillis(1), monitor.getAverageDuration(HttpMeasurementType.REQUEST_PROCESSING));

        // Record measurement for disabled type (should be ignored)
        monitor.recordMeasurement(HttpMeasurementType.TOKEN_EXTRACTION, 5_000_000);
        assertEquals(0, monitor.getSampleCount(HttpMeasurementType.TOKEN_EXTRACTION));
        assertEquals(Duration.ZERO, monitor.getAverageDuration(HttpMeasurementType.TOKEN_EXTRACTION));
    }

    @Test
    @DisplayName("Should test clear method alias")
    void shouldTestClearMethodAlias() {
        var monitor = new HttpMetricsMonitor();

        // Record some data
        monitor.recordMeasurement(HttpMeasurementType.REQUEST_PROCESSING, 1_000_000);
        monitor.recordRequestStatus(HttpRequestStatus.SUCCESS);

        // Clear using the alias method
        monitor.clear();

        // Verify everything is cleared
        assertEquals(Duration.ZERO, monitor.getAverageDuration(HttpMeasurementType.REQUEST_PROCESSING));
        assertEquals(0, monitor.getSampleCount(HttpMeasurementType.REQUEST_PROCESSING));
        assertEquals(0, monitor.getRequestStatusCount(HttpRequestStatus.SUCCESS));
    }

    @Test
    @DisplayName("Should test HttpMetricsMonitorConfig builders")
    void shouldTestHttpMetricsMonitorConfigBuilders() {
        // Test default enabled config
        var defaultConfig = HttpMetricsMonitorConfig.defaultEnabled();
        assertEquals(HttpMetricsMonitorConfig.ALL_MEASUREMENT_TYPES, defaultConfig.getMeasurementTypes());

        // Test disabled config
        var disabledConfig = HttpMetricsMonitorConfig.disabled();
        assertTrue(disabledConfig.getMeasurementTypes().isEmpty());

        // Test custom config
        var customConfig = HttpMetricsMonitorConfig.builder()
                .measurementType(HttpMeasurementType.REQUEST_PROCESSING)
                .measurementType(HttpMeasurementType.TOKEN_EXTRACTION)
                .build();
        assertEquals(2, customConfig.getMeasurementTypes().size());
        assertTrue(customConfig.getMeasurementTypes().contains(HttpMeasurementType.REQUEST_PROCESSING));
        assertTrue(customConfig.getMeasurementTypes().contains(HttpMeasurementType.TOKEN_EXTRACTION));
    }

    @Test
    @DisplayName("Should test MetricsTicker creation")
    void shouldTestMetricsTickerCreation() {
        var enabledMonitor = new HttpMetricsMonitor();
        var disabledMonitor = createMonitorWithEnabledTypes(); // Empty = all disabled

        // Test ticker creation for enabled type
        var enabledTicker = HttpMeasurementType.REQUEST_PROCESSING.createTicker(enabledMonitor);
        assertNotNull(enabledTicker);
        assertInstanceOf(ActiveMetricsTicker.class, enabledTicker);

        // Test ticker creation for disabled type
        var disabledTicker = HttpMeasurementType.REQUEST_PROCESSING.createTicker(disabledMonitor);
        assertNotNull(disabledTicker);
        assertSame(NoOpMetricsTicker.INSTANCE, disabledTicker);

        // Test started ticker creation
        var startedTicker = HttpMeasurementType.TOKEN_EXTRACTION.createStartedTicker(enabledMonitor);
        assertNotNull(startedTicker);
        // Can't easily verify it's started, but we can verify it records
        startedTicker.stopAndRecord();
        assertEquals(1, enabledMonitor.getSampleCount(HttpMeasurementType.TOKEN_EXTRACTION));
    }
}