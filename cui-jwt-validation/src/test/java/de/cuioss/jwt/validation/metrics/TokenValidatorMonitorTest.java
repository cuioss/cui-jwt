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
package de.cuioss.jwt.validation.metrics;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link TokenValidatorMonitor}.
 * <p>
 * Tests cover:
 * <ul>
 *   <li>Basic functionality - recording measurements and calculating averages</li>
 *   <li>Thread safety - massive parallel operations from multiple threads</li>
 *   <li>Performance characteristics - minimal overhead validation</li>
 *   <li>Edge cases - boundary conditions and error scenarios</li>
 *   <li>Window behavior - rolling window mechanics</li>
 * </ul>
 * <p>
 * This test class ensures that metrics monitoring operations are accurate,
 * thread-safe, and have minimal impact on runtime metrics.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests TokenValidatorMonitor functionality")
class TokenValidatorMonitorTest {

    @Test
    @DisplayName("Should create monitor with default window size")
    void shouldCreateMonitorWithDefaultWindowSize() {
        var monitor = new TokenValidatorMonitor();
        assertNotNull(monitor, "Monitor should be created");

        // Verify initial state - no measurements recorded
        for (MeasurementType type : MeasurementType.values()) {
            assertEquals(Duration.ZERO, monitor.getAverageDuration(type),
                    "Initial average should be zero for " + type);
            assertEquals(0, monitor.getSampleCount(type),
                    "Initial sample count should be zero for " + type);
        }
    }

    @Test
    @DisplayName("Should create monitor with custom window size")
    void shouldCreateMonitorWithCustomWindowSize() {
        var monitor = new TokenValidatorMonitor(50);
        assertNotNull(monitor, "Monitor should be created with custom window size");
    }

    @Test
    @DisplayName("Should reject invalid window sizes")
    void shouldRejectInvalidWindowSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenValidatorMonitor(0),
                "Should reject zero window size");

        assertThrows(IllegalArgumentException.class,
                () -> new TokenValidatorMonitor(-1),
                "Should reject negative window size");
    }

    @Test
    @DisplayName("Should record single measurement")
    void shouldRecordSingleMeasurement() {
        var monitor = new TokenValidatorMonitor();
        var measurementType = MeasurementType.SIGNATURE_VALIDATION;

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
    @DisplayName("Should record multiple measurements and calculate average")
    void shouldRecordMultipleMeasurementsAndCalculateAverage() {
        var monitor = new TokenValidatorMonitor();
        var measurementType = MeasurementType.COMPLETE_VALIDATION;

        // Record measurements: 1ms, 2ms, 3ms (average = 2ms)
        monitor.recordMeasurement(measurementType, 1_000_000); // 1ms
        monitor.recordMeasurement(measurementType, 2_000_000); // 2ms
        monitor.recordMeasurement(measurementType, 3_000_000); // 3ms

        assertEquals(3, monitor.getSampleCount(measurementType),
                "Should have three samples recorded");

        Duration average = monitor.getAverageDuration(measurementType);
        assertEquals(Duration.ofMillis(2), average,
                "Average should be 2ms");
    }

    @Test
    @DisplayName("Should handle microsecond precision")
    void shouldHandleMicrosecondPrecision() {
        var monitor = new TokenValidatorMonitor();
        var measurementType = MeasurementType.TOKEN_PARSING;

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
        var monitor = new TokenValidatorMonitor();

        // Record different values for different types
        monitor.recordMeasurement(MeasurementType.SIGNATURE_VALIDATION, 5_000_000); // 5ms
        monitor.recordMeasurement(MeasurementType.CLAIMS_VALIDATION, 1_000_000);    // 1ms

        assertEquals(Duration.ofMillis(5),
                monitor.getAverageDuration(MeasurementType.SIGNATURE_VALIDATION),
                "Signature validation average should be 5ms");

        assertEquals(Duration.ofMillis(1),
                monitor.getAverageDuration(MeasurementType.CLAIMS_VALIDATION),
                "Claims validation average should be 1ms");

        assertEquals(Duration.ZERO,
                monitor.getAverageDuration(MeasurementType.TOKEN_PARSING),
                "Unused measurement type should remain at zero");
    }

    @Test
    @DisplayName("Should respect rolling window size")
    void shouldRespectRollingWindowSize() {
        var windowSize = 3;
        var monitor = new TokenValidatorMonitor(windowSize);
        var measurementType = MeasurementType.HEADER_VALIDATION;

        // Record more measurements than window size
        monitor.recordMeasurement(measurementType, 1_000_000); // 1ms - should be overwritten
        monitor.recordMeasurement(measurementType, 2_000_000); // 2ms
        monitor.recordMeasurement(measurementType, 3_000_000); // 3ms
        monitor.recordMeasurement(measurementType, 4_000_000); // 4ms - overwrites 1ms

        // Sample count should not exceed window size
        assertTrue(monitor.getSampleCount(measurementType) <= windowSize,
                "Sample count should not exceed window size");

        // Average should reflect recent values
        Duration average = monitor.getAverageDuration(measurementType);
        assertTrue(average.toMillis() >= 2 && average.toMillis() <= 4,
                "Average should reflect recent measurements: " + average.toMillis() + "ms");
    }

    @Test
    @DisplayName("Should reset specific measurement type")
    void shouldResetSpecificMeasurementType() {
        var monitor = new TokenValidatorMonitor();

        // Record measurements for multiple types
        monitor.recordMeasurement(MeasurementType.SIGNATURE_VALIDATION, 5_000_000);
        monitor.recordMeasurement(MeasurementType.CLAIMS_VALIDATION, 1_000_000);

        // Reset only signature validation
        monitor.reset(MeasurementType.SIGNATURE_VALIDATION);

        assertEquals(Duration.ZERO,
                monitor.getAverageDuration(MeasurementType.SIGNATURE_VALIDATION),
                "Reset measurement type should be zero");

        assertEquals(Duration.ofMillis(1),
                monitor.getAverageDuration(MeasurementType.CLAIMS_VALIDATION),
                "Other measurement types should remain unchanged");
    }

    @Test
    @DisplayName("Should reset all measurements")
    void shouldResetAllMeasurements() {
        var monitor = new TokenValidatorMonitor();

        // Record measurements for all types
        for (MeasurementType type : MeasurementType.values()) {
            monitor.recordMeasurement(type, 1_000_000);
        }

        // Reset all
        monitor.resetAll();

        // Verify all are reset
        for (MeasurementType type : MeasurementType.values()) {
            assertEquals(Duration.ZERO, monitor.getAverageDuration(type),
                    "All measurement types should be reset: " + type);
            assertEquals(0, monitor.getSampleCount(type),
                    "All sample counts should be reset: " + type);
        }
    }

    @Test
    @DisplayName("Should handle zero and negative durations")
    void shouldHandleZeroAndNegativeDurations() {
        var monitor = new TokenValidatorMonitor();
        var measurementType = MeasurementType.JWKS_OPERATIONS;

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
        var monitor = new TokenValidatorMonitor();
        var measurementType = MeasurementType.COMPLETE_VALIDATION;
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
                        long duration = (threadId + 1) * 1_000_000; // 1ms, 2ms, 3ms, etc.
                        monitor.recordMeasurement(measurementType, duration);
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

        // Verify measurements were recorded (exact count may be less due to window size)
        assertTrue(monitor.getSampleCount(measurementType) > 0,
                "Should have recorded measurements");

        Duration average = monitor.getAverageDuration(measurementType);
        assertTrue(average.toNanos() > 0,
                "Average should be positive: " + average.toNanos() + "ns");
    }

    @Test
    @DisplayName("Should handle massive parallel load")
    void shouldHandleMassiveParallelLoad() {
        var threadCount = 50;
        var measurementsPerThread = 5000;
        var monitor = new TokenValidatorMonitor(200); // Larger window for this test
        var measurementType = MeasurementType.SIGNATURE_VALIDATION;
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

        // Verify average is reasonable (should be close to 2ms)
        Duration average = monitor.getAverageDuration(measurementType);
        assertTrue(average.toMillis() >= 1 && average.toMillis() <= 3,
                "Average should be close to 2ms under massive load: " + average.toMillis() + "ms");

        // Performance assertion - should complete reasonably quickly
        double measurementsPerMs = totalMeasurements.get() / (double) totalDurationMs;

        assertTrue(measurementsPerMs > 1000, // Should handle at least 1000 measurements per millisecond
                "Performance too slow: %.1f measurements/ms (expected > 1000)".formatted(measurementsPerMs));

        System.out.printf("Massive parallel test: %d threads, %d measurements/thread, %.1f measurements/ms%n",
                threadCount, measurementsPerThread, measurementsPerMs);
    }

    @Test
    @DisplayName("Should handle concurrent access to different measurement types")
    void shouldHandleConcurrentAccessToDifferentMeasurementTypes() {
        var monitor = new TokenValidatorMonitor();
        var measurementTypes = MeasurementType.values();
        var threadsPerType = 5;
        var measurementsPerThread = 100;
        var startLatch = new CountDownLatch(1);
        var completedThreads = new AtomicInteger(0);
        var executor = Executors.newFixedThreadPool(measurementTypes.length * threadsPerType);

        // Submit threads for each measurement type
        for (int typeIndex = 0; typeIndex < measurementTypes.length; typeIndex++) {
            final var measurementType = measurementTypes[typeIndex];
            final long baseDuration = (typeIndex + 1) * 1_000_000; // 1ms, 2ms, 3ms, etc.

            for (int threadIndex = 0; threadIndex < threadsPerType; threadIndex++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < measurementsPerThread; j++) {
                            monitor.recordMeasurement(measurementType, baseDuration);
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

        // Verify each measurement type has the expected average
        for (int i = 0; i < measurementTypes.length; i++) {
            var measurementType = measurementTypes[i];
            var expectedDurationMs = i + 1;

            assertTrue(monitor.getSampleCount(measurementType) > 0,
                    "Should have samples for " + measurementType);

            Duration average = monitor.getAverageDuration(measurementType);
            assertEquals(expectedDurationMs, average.toMillis(),
                    "Average for " + measurementType + " should be " + expectedDurationMs + "ms");
        }
    }

    @RepeatedTest(5)
    @DisplayName("Should be consistent across multiple runs")
    void shouldBeConsistentAcrossMultipleRuns() {
        var monitor = new TokenValidatorMonitor();
        var measurementType = MeasurementType.CLAIMS_VALIDATION;

        // Record consistent measurements
        IntStream.range(0, 100).forEach(i ->
                monitor.recordMeasurement(measurementType, 1_500_000)); // 1.5ms each

        Duration average = monitor.getAverageDuration(measurementType);
        assertEquals(Duration.ofNanos(1_500_000), average,
                "Average should be consistent across repeated runs");
    }

    @Test
    @DisplayName("Should have minimal metrics overhead")
    void shouldHaveMinimalPerformanceOverhead() {
        var monitor = new TokenValidatorMonitor();
        var measurementType = MeasurementType.SIGNATURE_VALIDATION;
        var iterations = 100_000;

        // Measure time for recording measurements
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            monitor.recordMeasurement(measurementType, 1_000_000);
        }
        long endTime = System.nanoTime();

        long totalOverheadNanos = endTime - startTime;
        double overheadPerMeasurementNanos = totalOverheadNanos / (double) iterations;

        // Overhead should be minimal (less than 1 microsecond per measurement)
        assertTrue(overheadPerMeasurementNanos < 1000,
                "Performance overhead too high: %.1f ns/measurement (expected < 1000)".formatted(
                        overheadPerMeasurementNanos));

        System.out.printf("Performance overhead: %.1f ns per measurement%n", overheadPerMeasurementNanos);
    }
}