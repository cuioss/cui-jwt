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

import de.cuioss.tools.concurrent.StripedRingBuffer;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;
import lombok.Getter;
import lombok.NonNull;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides thread-safe monitoring of HTTP-level JWT processing metrics.
 * <p>
 * This class measures execution times for different stages of HTTP request processing
 * related to JWT validation with microsecond precision. It maintains a configurable
 * rolling window of recent measurements for each processing step.
 * <p>
 * <strong>Design Principles:</strong>
 * <ul>
 *   <li><strong>Thread-Safe:</strong> All operations are lock-free using atomic operations</li>
 *   <li><strong>Zero Runtime Impact:</strong> Optimized for minimal overhead during measurement</li>
 *   <li><strong>Microsecond Precision:</strong> All measurements recorded in microseconds</li>
 *   <li><strong>Rolling Window:</strong> Maintains configurable number of recent samples</li>
 *   <li><strong>HTTP Aware:</strong> Measures HTTP-specific processing steps</li>
 * </ul>
 * <p>
 * <strong>Measurement Types:</strong>
 * <ul>
 *   <li>{@link HttpMeasurementType#REQUEST_PROCESSING} - Total request processing time</li>
 *   <li>{@link HttpMeasurementType#HEADER_EXTRACTION} - Time to extract Authorization header</li>
 *   <li>{@link HttpMeasurementType#TOKEN_EXTRACTION} - Time to extract token from header</li>
 *   <li>{@link HttpMeasurementType#AUTHORIZATION_CHECK} - Time to check scopes/roles/groups</li>
 *   <li>{@link HttpMeasurementType#RESPONSE_FORMATTING} - Time to format error responses</li>
 * </ul>
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>
 * // Create monitor with default window size (100 samples)
 * HttpMetricsMonitor monitor = new HttpMetricsMonitor();
 *
 * // Record a measurement
 * long startNanos = System.nanoTime();
 * // ... extract bearer token from header ...
 * long durationNanos = System.nanoTime() - startNanos;
 * monitor.recordMeasurement(HttpMeasurementType.TOKEN_EXTRACTION, durationNanos);
 *
 * // Get average for analysis
 * Duration avgExtractionTime = monitor.getAverageDuration(HttpMeasurementType.TOKEN_EXTRACTION);
 * </pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class HttpMetricsMonitor {

    /**
     * Default window size for the ring buffer (number of samples to retain).
     */
    private static final int DEFAULT_WINDOW_SIZE = 10000;

    /**
     * Striped ring buffers for each measurement type.
     * Each buffer stores nanosecond precision measurements.
     */
    private final StripedRingBuffer[] measurementBuffers;

    /**
     * Tracks the count of requests by status.
     * Array indexed by HttpRequestStatus.ordinal()
     */
    private final AtomicLong[] requestStatusCounts;

    /**
     * Set of enabled measurement types. Only measurements for these types
     * will be recorded; others will be no-op operations.
     */
    @Getter
    private final Set<HttpMeasurementType> enabledTypes;

    /**
     * Window size for the ring buffer.
     */
    private final int windowSize;

    /**
     * Creates a new HTTP metrics monitor with all measurement types enabled.
     */
    public HttpMetricsMonitor() {
        this(HttpMetricsMonitorConfig.ALL_MEASUREMENT_TYPES, DEFAULT_WINDOW_SIZE);
    }

    /**
     * Creates a new HTTP metrics monitor with specified enabled measurement types and default window size.
     *
     * @param enabledTypes set of measurement types to monitor
     */
    public HttpMetricsMonitor(@NonNull Set<HttpMeasurementType> enabledTypes) {
        this(enabledTypes, DEFAULT_WINDOW_SIZE);
    }

    /**
     * Creates a new HTTP metrics monitor with specified enabled measurement types and window size.
     * <p>
     * Package-private constructor to be used from HttpMetricsMonitorConfig.
     *
     * @param enabledTypes set of measurement types to monitor (others will be no-op)
     * @param windowSize   size of the ring buffer window for each measurement type
     */
    @SuppressWarnings("unchecked")
    HttpMetricsMonitor(@NonNull Set<HttpMeasurementType> enabledTypes, int windowSize) {
        this.enabledTypes = enabledTypes;
        this.windowSize = windowSize;
        
        // Initialize ring buffers for enabled types
        this.measurementBuffers = new StripedRingBuffer[HttpMeasurementType.values().length];
        for (HttpMeasurementType type : HttpMeasurementType.values()) {
            if (enabledTypes.contains(type)) {
                measurementBuffers[type.ordinal()] = new StripedRingBuffer(windowSize);
            }
        }
        
        // Initialize request status counters
        this.requestStatusCounts = new AtomicLong[HttpRequestStatus.values().length];
        for (int i = 0; i < requestStatusCounts.length; i++) {
            requestStatusCounts[i] = new AtomicLong(0);
        }
    }

    /**
     * Checks if the specified measurement type is enabled for recording.
     *
     * @param measurementType the type to check
     * @return true if this measurement type will be recorded, false otherwise
     */
    public boolean isEnabled(@NonNull HttpMeasurementType measurementType) {
        return enabledTypes.contains(measurementType);
    }

    /**
     * Records a metrics measurement for the specified HTTP processing step.
     * <p>
     * This method updates a running average using an exponential moving average algorithm
     * for minimal overhead and good responsiveness to recent measurements.
     * <p>
     * If the measurement type is not enabled, this method does nothing.
     *
     * @param measurementType the type of measurement being recorded
     * @param durationNanos   the duration in nanoseconds
     */
    public void recordMeasurement(@NonNull HttpMeasurementType measurementType, long durationNanos) {
        if (!isEnabled(measurementType)) {
            return;
        }

        int index = measurementType.ordinal();

        // Record in ring buffer - convert nanos to microseconds for storage
        // StripedRingBuffer expects microseconds and returns Duration based on that
        StripedRingBuffer buffer = measurementBuffers[index];
        if (buffer != null) {
            long durationMicros = durationNanos / 1000;
            buffer.recordMeasurement(Math.max(0, durationMicros));
        }
    }


    /**
     * Records that a request completed with the specified status.
     * <p>
     * This method increments the counter for the given request status,
     * allowing tracking of the distribution of request outcomes.
     *
     * @param status the status of the completed request
     */
    public void recordRequestStatus(@NonNull HttpRequestStatus status) {
        requestStatusCounts[status.ordinal()].incrementAndGet();
    }

    /**
     * Gets the count of requests for the specified status.
     *
     * @param status the status to get the count for
     * @return the number of requests with this status
     */
    public long getRequestStatusCount(@NonNull HttpRequestStatus status) {
        return requestStatusCounts[status.ordinal()].get();
    }

    /**
     * Gets the counts of all request statuses as a map.
     *
     * @return a map of request status to count
     */
    public Map<HttpRequestStatus, Long> getRequestStatusCounts() {
        Map<HttpRequestStatus, Long> counts = new EnumMap<>(HttpRequestStatus.class);
        for (HttpRequestStatus status : HttpRequestStatus.values()) {
            counts.put(status, requestStatusCounts[status.ordinal()].get());
        }
        return counts;
    }

    /**
     * Resets all measurements for the specified type.
     *
     * @param measurementType the type of measurement to reset
     */
    public void reset(@NonNull HttpMeasurementType measurementType) {
        int index = measurementType.ordinal();
        
        // Clear ring buffer
        if (measurementBuffers[index] != null) {
            measurementBuffers[index].reset();
        }
    }

    /**
     * Resets all measurements and counters.
     */
    public void resetAll() {
        // Clear ring buffers
        for (int i = 0; i < measurementBuffers.length; i++) {
            if (measurementBuffers[i] != null) {
                measurementBuffers[i].reset();
            }
        }
        
        for (AtomicLong counter : requestStatusCounts) {
            counter.set(0);
        }
    }

    /**
     * Gets the percentile statistics for the specified measurement type.
     * <p>
     * Returns statistics calculated from the ring buffer if available,
     * providing accurate percentiles (p50, p95, p99) and sample count.
     *
     * @param measurementType the type of measurement to analyze
     * @return optional containing the statistics, or empty if no measurements exist
     */
    public Optional<StripedRingBufferStatistics> getHttpMetrics(@NonNull HttpMeasurementType measurementType) {
        if (!isEnabled(measurementType)) {
            return Optional.empty();
        }

        StripedRingBuffer buffer = measurementBuffers[measurementType.ordinal()];
        if (buffer == null) {
            return Optional.empty();
        }

        StripedRingBufferStatistics stats = buffer.getStatistics();
        if (stats.sampleCount() == 0) {
            return Optional.empty();
        }

        return Optional.of(stats);
    }

    /**
     * Clears all measurements and counters.
     * This is an alias for {@link #resetAll()} to match the naming convention
     * used in the rest of the system.
     */
    public void clear() {
        resetAll();
    }

    /**
     * Gets the window size for the ring buffers.
     *
     * @return the window size
     */
    public int getWindowSize() {
        return windowSize;
    }

}