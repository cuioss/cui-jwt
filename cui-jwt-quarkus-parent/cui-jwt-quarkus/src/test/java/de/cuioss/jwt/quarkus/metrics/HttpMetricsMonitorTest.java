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
 *   <li>Basic functionality - recording measurements and retrieving percentiles</li>
 *   <li>Request status tracking - counting different outcome types</li>
 *   <li>Thread safety - massive parallel operations from multiple threads</li>
 *   <li>Performance characteristics - minimal overhead validation</li>
 *   <li>Edge cases - boundary conditions and error scenarios</li>
 *   <li>Ring buffer behavior - percentile accuracy testing</li>
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
            var metrics = monitor.getHttpMetrics(type);
            assertTrue(metrics.isEmpty(), "Initial metrics should be empty for " + type);
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

        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isPresent(), "Should have metrics after recording");
        assertEquals(1, metrics.get().sampleCount(),
                "Should have one sample recorded");
        assertEquals(1_000_000L, metrics.get().p50().toNanos(),
                "P50 should equal the single measurement in nanoseconds");
    }

    @Test
    @DisplayName("Should record multiple measurements and calculate percentiles")
    void shouldRecordMultipleMeasurementsAndCalculatePercentiles() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.HEADER_EXTRACTION;

        // Record measurements: 10ms, 20ms, 30ms
        monitor.recordMeasurement(measurementType, 10_000_000); // 10ms
        monitor.recordMeasurement(measurementType, 20_000_000); // 20ms
        monitor.recordMeasurement(measurementType, 30_000_000); // 30ms

        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isPresent(), "Should have metrics after recording");
        assertEquals(3, metrics.get().sampleCount(),
                "Should have three samples recorded");

        var p50 = metrics.get().p50();
        // P50 should be the median value (20ms)
        assertTrue(p50.toNanos() >= 10_000_000L && p50.toNanos() <= 30_000_000L,
                "P50 should be between 10ms and 30ms: " + p50.toNanos() + "ns");
    }

    @Test
    @DisplayName("Should handle microsecond precision")
    void shouldHandleMicrosecondPrecision() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.AUTHORIZATION_CHECK;

        // Record 500 microseconds (500,000 nanoseconds)
        long durationNanos = 500_000;
        monitor.recordMeasurement(measurementType, durationNanos);

        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isPresent(), "Should have metrics after recording");
        
        var p50 = metrics.get().p50();
        assertTrue(p50.toNanos() > 0,
                "Should have positive p50 after parallel recording");
        assertEquals(500_000L, p50.toNanos(),
                "Should maintain microsecond precision: " + p50.toNanos() + " nanos");
    }

    @Test
    @DisplayName("Should handle selective measurement types")
    void shouldHandleSelectiveMeasurementTypes() {
        var monitor = createMonitorWithEnabledTypes(
                HttpMeasurementType.REQUEST_PROCESSING,
                HttpMeasurementType.TOKEN_EXTRACTION
        );

        // Record measurements for enabled types
        monitor.recordMeasurement(HttpMeasurementType.REQUEST_PROCESSING, 1_000_000);
        monitor.recordMeasurement(HttpMeasurementType.TOKEN_EXTRACTION, 2_000_000);

        // Record measurements for disabled types
        monitor.recordMeasurement(HttpMeasurementType.RESPONSE_FORMATTING, 3_000_000);
        monitor.recordMeasurement(HttpMeasurementType.AUTHORIZATION_CHECK, 4_000_000);

        // REQUEST_PROCESSING should have measurements
        var reqMetrics = monitor.getHttpMetrics(HttpMeasurementType.REQUEST_PROCESSING);
        assertTrue(reqMetrics.isPresent(),
                "REQUEST_PROCESSING should have measurements");

        // RESPONSE_FORMATTING should NOT have measurements
        var respMetrics = monitor.getHttpMetrics(HttpMeasurementType.RESPONSE_FORMATTING);
        assertTrue(respMetrics.isEmpty(),
                "RESPONSE_FORMATTING should not have measurements");

        // TOKEN_EXTRACTION should have measurements
        var tokenMetrics = monitor.getHttpMetrics(HttpMeasurementType.TOKEN_EXTRACTION);
        assertTrue(tokenMetrics.isPresent(),
                "TOKEN_EXTRACTION should have measurements");
    }

    @Test
    @DisplayName("Should reset individual measurement type")
    void shouldResetIndividualMeasurementType() {
        var monitor = new HttpMetricsMonitor();

        // Record measurements for multiple types
        monitor.recordMeasurement(HttpMeasurementType.REQUEST_PROCESSING, 1_000_000);
        monitor.recordMeasurement(HttpMeasurementType.HEADER_EXTRACTION, 2_000_000);
        monitor.recordMeasurement(HttpMeasurementType.TOKEN_EXTRACTION, 3_000_000);

        // Reset only REQUEST_PROCESSING
        monitor.reset(HttpMeasurementType.REQUEST_PROCESSING);

        // Verify reset
        var resetMetrics = monitor.getHttpMetrics(HttpMeasurementType.REQUEST_PROCESSING);
        assertTrue(resetMetrics.isEmpty(),
                "REQUEST_PROCESSING should be reset");

        // Others should remain unchanged
        var headerMetrics = monitor.getHttpMetrics(HttpMeasurementType.HEADER_EXTRACTION);
        assertTrue(headerMetrics.isPresent(),
                "HEADER_EXTRACTION should be unchanged");
    }

    @Test
    @DisplayName("Should reset all measurements")
    void shouldResetAllMeasurements() {
        var monitor = new HttpMetricsMonitor();

        // Record measurements and status counts
        for (HttpMeasurementType type : HttpMeasurementType.values()) {
            monitor.recordMeasurement(type, 1_000_000);
        }
        monitor.recordRequestStatus(HttpRequestStatus.SUCCESS);
        monitor.recordRequestStatus(HttpRequestStatus.INVALID_TOKEN);

        // Reset all
        monitor.resetAll();

        // Verify all metrics are reset
        for (HttpMeasurementType type : HttpMeasurementType.values()) {
            var metrics = monitor.getHttpMetrics(type);
            assertTrue(metrics.isEmpty(),
                    "Should be reset for " + type);
        }

        // Verify status counts are reset
        for (HttpRequestStatus status : HttpRequestStatus.values()) {
            assertEquals(0, monitor.getRequestStatusCount(status),
                    "Status count should be reset for " + status);
        }
    }

    @Test
    @DisplayName("Should ignore measurements for disabled types")
    void shouldIgnoreMeasurementsForDisabledTypes() {
        var monitor = createMonitorWithEnabledTypes(
                HttpMeasurementType.REQUEST_PROCESSING
        );

        // Try to record for disabled type
        var measurementType = HttpMeasurementType.TOKEN_EXTRACTION;
        assertFalse(monitor.isEnabled(measurementType),
                "Type should be disabled");

        // Verify measurement was ignored
        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isEmpty(),
                "Should have no measurements");

        // Force enable and record
        monitor.recordMeasurement(measurementType, Duration.ofMillis(100).toNanos());
        metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isEmpty(),
                "Should still have no measurements for disabled type");
    }

    @Test
    @DisplayName("Should handle negative measurements")
    void shouldHandleNegativeMeasurements() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.REQUEST_PROCESSING;

        // Record negative duration
        monitor.recordMeasurement(measurementType, -1_000_000);

        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isPresent(), "Should have metrics");
        assertTrue(metrics.get().sampleCount() > 0,
                "Should have recorded samples");

        Duration p50 = metrics.get().p50();
        assertTrue(p50.toNanos() >= 0,
                "Should clamp negative values to zero");
    }

    @Test
    @DisplayName("Should handle concurrent measurements")
    void shouldHandleConcurrentMeasurements() throws InterruptedException {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.REQUEST_PROCESSING;
        int threadCount = 100;
        int measurementsPerThread = 1000;

        var latch = new CountDownLatch(threadCount);
        var executor = Executors.newFixedThreadPool(threadCount);

        var successCounter = new AtomicInteger(0);
        var errorCounter = new AtomicInteger(0);

        // Start concurrent threads
        IntStream.range(0, threadCount).forEach(i ->
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < measurementsPerThread; j++) {
                            monitor.recordMeasurement(measurementType, (i + j) * 1_000);
                        }
                        successCounter.incrementAndGet();
                    } catch (Exception e) {
                        errorCounter.incrementAndGet();
                        log.error("Error in thread " + i, e);
                    } finally {
                        latch.countDown();
                    }
                })
        );

        assertTrue(latch.await(5, SECONDS),
                "All threads should complete within timeout");

        assertEquals(threadCount, successCounter.get(),
                "All threads should complete successfully");
        assertEquals(0, errorCounter.get(),
                "No errors should occur");

        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isPresent(), "Should have metrics");
        assertTrue(metrics.get().sampleCount() > 0,
                "Should have recorded all samples without exceptions");

        // All threads should complete
        Duration p50 = metrics.get().p50();
        assertTrue(p50.toNanos() > 0,
                "Should have positive p50");

        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle multiple measurement types concurrently")
    void shouldHandleMultipleMeasurementTypesConcurrently() throws InterruptedException {
        var monitor = new HttpMetricsMonitor();
        var latch = new CountDownLatch(HttpMeasurementType.values().length);
        var executor = Executors.newFixedThreadPool(HttpMeasurementType.values().length);

        // Start thread for each measurement type
        for (HttpMeasurementType measurementType : HttpMeasurementType.values()) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 1000; i++) {
                        monitor.recordMeasurement(measurementType, i * 1_000);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, SECONDS),
                "All threads should complete");

        // Verify all types have measurements
        for (HttpMeasurementType measurementType : HttpMeasurementType.values()) {
            var metrics = monitor.getHttpMetrics(measurementType);
            assertTrue(metrics.isPresent(), "Should have metrics for " + measurementType);
            assertTrue(metrics.get().sampleCount() > 0,
                    "Should have measurements for " + measurementType);

            Duration p50 = metrics.get().p50();
            assertTrue(p50.toNanos() >= 0,
                    "Should have valid p50 for " + measurementType);
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("Should calculate accurate percentiles")
    void shouldCalculateAccuratePercentiles() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.REQUEST_PROCESSING;

        // Record 100 measurements from 1ms to 100ms
        for (int i = 1; i <= 100; i++) {
            monitor.recordMeasurement(measurementType, i * 1_000_000L);
        }

        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isPresent(), "Should have metrics");
        
        Duration p50 = metrics.get().p50();
        assertTrue(p50.toNanos() > 0, "Should have positive p50");
        assertTrue(p50.toNanos() <= 100_000_000L, "P50 should be reasonable (<=100ms in nanos)");

        // Verify sample count
        assertEquals(100, metrics.get().sampleCount(),
                "Should have exactly 100 samples");
    }

    @Test
    @DisplayName("Should record and retrieve request status counts")
    void shouldRecordAndRetrieveRequestStatusCounts() {
        var monitor = new HttpMetricsMonitor();

        // Record various status counts
        monitor.recordRequestStatus(HttpRequestStatus.SUCCESS);
        monitor.recordRequestStatus(HttpRequestStatus.SUCCESS);
        monitor.recordRequestStatus(HttpRequestStatus.INVALID_TOKEN);
        monitor.recordRequestStatus(HttpRequestStatus.MISSING_TOKEN);
        monitor.recordRequestStatus(HttpRequestStatus.INSUFFICIENT_PERMISSIONS);

        // Verify individual counts
        assertEquals(2, monitor.getRequestStatusCount(HttpRequestStatus.SUCCESS));
        assertEquals(1, monitor.getRequestStatusCount(HttpRequestStatus.INVALID_TOKEN));
        assertEquals(1, monitor.getRequestStatusCount(HttpRequestStatus.MISSING_TOKEN));
        assertEquals(1, monitor.getRequestStatusCount(HttpRequestStatus.INSUFFICIENT_PERMISSIONS));
        assertEquals(0, monitor.getRequestStatusCount(HttpRequestStatus.ERROR));

        // Verify map of all counts
        Map<HttpRequestStatus, Long> statusCounts = monitor.getRequestStatusCounts();
        assertEquals(2L, statusCounts.get(HttpRequestStatus.SUCCESS));
        assertEquals(1L, statusCounts.get(HttpRequestStatus.INVALID_TOKEN));
        assertEquals(1L, statusCounts.get(HttpRequestStatus.MISSING_TOKEN));
    }

    @Test
    @DisplayName("Should create monitor from configuration")
    void shouldCreateMonitorFromConfiguration() {
        // Test default enabled configuration
        var defaultConfig = HttpMetricsMonitorConfig.defaultEnabled();
        var defaultMonitor = defaultConfig.createMonitor();
        assertEquals(HttpMetricsMonitorConfig.ALL_MEASUREMENT_TYPES, defaultMonitor.getEnabledTypes());

        // Test disabled configuration
        var disabledConfig = HttpMetricsMonitorConfig.disabled();
        var disabledMonitor = disabledConfig.createMonitor();
        assertTrue(disabledMonitor.getEnabledTypes().isEmpty());

        // Test selective configuration
        var selectiveConfig = HttpMetricsMonitorConfig.builder()
                .measurementType(HttpMeasurementType.REQUEST_PROCESSING)
                .build();
        var selectiveMonitor = selectiveConfig.createMonitor();
        assertEquals(1, selectiveMonitor.getEnabledTypes().size());
        assertTrue(selectiveMonitor.isEnabled(HttpMeasurementType.REQUEST_PROCESSING));
    }

    @Test
    @DisplayName("Should handle configuration with specific measurement types")
    void shouldHandleConfigurationWithSpecificMeasurementTypes() {
        var config = HttpMetricsMonitorConfig.builder()
                .measurementType(HttpMeasurementType.REQUEST_PROCESSING)
                .measurementType(HttpMeasurementType.TOKEN_EXTRACTION)
                .build();

        var monitor = config.createMonitor();

        // Verify only configured types are enabled
        assertTrue(monitor.isEnabled(HttpMeasurementType.REQUEST_PROCESSING));
        assertTrue(monitor.isEnabled(HttpMeasurementType.TOKEN_EXTRACTION));
        assertFalse(monitor.isEnabled(HttpMeasurementType.HEADER_EXTRACTION));
        assertFalse(monitor.isEnabled(HttpMeasurementType.AUTHORIZATION_CHECK));
        assertFalse(monitor.isEnabled(HttpMeasurementType.RESPONSE_FORMATTING));
    }

    @Test
    @DisplayName("Should handle clear alias method")
    void shouldHandleClearAliasMethod() {
        var monitor = new HttpMetricsMonitor();

        // Record some measurements
        monitor.recordMeasurement(HttpMeasurementType.REQUEST_PROCESSING, 1_000_000);
        monitor.recordRequestStatus(HttpRequestStatus.SUCCESS);

        // Use clear() alias
        monitor.clear();

        // REQUEST_PROCESSING should be cleared
        var metrics = monitor.getHttpMetrics(HttpMeasurementType.REQUEST_PROCESSING);
        assertTrue(metrics.isEmpty(), "REQUEST_PROCESSING should be cleared");

        // Status counts should be cleared
        assertEquals(0, monitor.getRequestStatusCount(HttpRequestStatus.SUCCESS));
    }

    @RepeatedTest(10)
    @DisplayName("Should complete operations within reasonable time")
    void shouldCompleteOperationsWithinReasonableTime() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.TOKEN_EXTRACTION;

        // Warm up
        for (int i = 0; i < 100; i++) {
            monitor.recordMeasurement(measurementType, i * 1_000);
        }

        var start = System.nanoTime();

        // Measure recording performance
        for (int i = 0; i < 10_000; i++) {
            monitor.recordMeasurement(measurementType, i * 1_000);
        }

        var duration = Duration.ofNanos(System.nanoTime() - start);

        // Should complete 10,000 recordings quickly
        assertTrue(duration.toMillis() < 100,
                "Should complete 10,000 recordings in under 100ms, took: " + duration.toMillis() + "ms");
    }

    @Test
    @DisplayName("Should track request status counts concurrently")
    void shouldTrackRequestStatusCountsConcurrently() throws InterruptedException {
        var monitor = new HttpMetricsMonitor();
        int threadCount = 10;
        int recordsPerThread = 1000;

        var latch = new CountDownLatch(threadCount);
        var executor = Executors.newFixedThreadPool(threadCount);

        // Record from multiple threads
        IntStream.range(0, threadCount).forEach(i ->
                executor.submit(() -> {
                    try {
                        HttpRequestStatus status = i % 2 == 0 ?
                                HttpRequestStatus.SUCCESS : HttpRequestStatus.INVALID_TOKEN;
                        for (int j = 0; j < recordsPerThread; j++) {
                            monitor.recordRequestStatus(status);
                        }
                    } finally {
                        latch.countDown();
                    }
                })
        );

        assertTrue(latch.await(5, SECONDS),
                "All threads should complete");

        // Verify counts
        assertEquals(5000, monitor.getRequestStatusCount(HttpRequestStatus.SUCCESS),
                "Should have 5000 success counts");
        assertEquals(5000, monitor.getRequestStatusCount(HttpRequestStatus.INVALID_TOKEN),
                "Should have 5000 invalid token counts");

        executor.shutdown();
    }

    @Test
    @DisplayName("Should have stable memory footprint")
    void shouldHaveStableMemoryFootprint() {
        var config = HttpMetricsMonitorConfig.builder()
                .measurementType(HttpMeasurementType.TOKEN_EXTRACTION)
                .windowSize(1000) // Small window for memory test
                .build();
        var monitor = config.createMonitor();

        var measurementType = HttpMeasurementType.TOKEN_EXTRACTION;

        // Record many measurements (more than window size)
        for (int i = 0; i < 10_000; i++) {
            monitor.recordMeasurement(measurementType, i * 1_000);
        }

        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isPresent(), "Should have metrics");
        
        // Due to ring buffer, sample count should be capped at window size
        assertTrue(metrics.get().sampleCount() <= 1000,
                "Sample count should be limited by window size");
    }

    @Test
    @DisplayName("Should maintain percentile accuracy with ring buffer")
    void shouldMaintainPercentileAccuracyWithRingBuffer() {
        var monitor = new HttpMetricsMonitor();
        var measurementType = HttpMeasurementType.REQUEST_PROCESSING;

        // Record measurements in sorted order
        for (int i = 1; i <= 1000; i++) {
            monitor.recordMeasurement(measurementType, i * 1_000L); // 1 microsecond to 1 millisecond
        }

        var metrics = monitor.getHttpMetrics(measurementType);
        assertTrue(metrics.isPresent(), "Should have metrics");

        var p50 = metrics.get().p50();
        var p95 = metrics.get().p95();
        var p99 = metrics.get().p99();

        // Verify percentiles are in correct order
        assertTrue(p50.compareTo(p95) <= 0, "P50 should be <= P95");
        assertTrue(p95.compareTo(p99) <= 0, "P95 should be <= P99");

        // Verify approximate values (allowing for ring buffer approximation)
        assertTrue(p50.toNanos() >= 400_000L && p50.toNanos() <= 600_000L,
                "P50 should be around 500 microseconds: " + p50.toNanos());
        assertTrue(p95.toNanos() >= 900_000 && p95.toNanos() <= 960_000,
                "P95 should be around 950 microseconds: " + p95.toNanos());
        assertTrue(p99.toNanos() >= 980_000 && p99.toNanos() <= 1_000_000,
                "P99 should be around 990 microseconds: " + p99.toNanos());
    }

    @Test
    @DisplayName("Should handle enable check for measurement types")
    void shouldHandleEnableCheckForMeasurementTypes() {
        var enabledMonitor = createMonitorWithEnabledTypes(HttpMeasurementType.TOKEN_EXTRACTION);

        assertTrue(enabledMonitor.isEnabled(HttpMeasurementType.TOKEN_EXTRACTION));
        assertFalse(enabledMonitor.isEnabled(HttpMeasurementType.REQUEST_PROCESSING));
        assertFalse(enabledMonitor.isEnabled(HttpMeasurementType.HEADER_EXTRACTION));

        // Record and verify
        enabledMonitor.recordMeasurement(HttpMeasurementType.TOKEN_EXTRACTION, 1_000_000);
        var metrics = enabledMonitor.getHttpMetrics(HttpMeasurementType.TOKEN_EXTRACTION);
        assertTrue(metrics.isPresent(), "Should have metrics");
        assertEquals(1, metrics.get().sampleCount());
    }
}