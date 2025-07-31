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

import lombok.Getter;
import lombok.NonNull;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
     * Tracks running averages for each measurement type.
     * Uses AtomicReference for thread-safe updates.
     */
    private final AtomicReference<Duration>[] averageDurations;

    /**
     * Tracks sample counts for each measurement type.
     */
    private final AtomicLong[] sampleCounts;

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
     * Creates a new HTTP metrics monitor with all measurement types enabled.
     */
    public HttpMetricsMonitor() {
        this(HttpMetricsMonitorConfig.ALL_MEASUREMENT_TYPES);
    }

    /**
     * Creates a new HTTP metrics monitor with specified enabled measurement types.
     * <p>
     * Package-private constructor to be used from HttpMetricsMonitorConfig.
     *
     * @param enabledTypes set of measurement types to monitor (others will be no-op)
     */
    @SuppressWarnings("unchecked")
    HttpMetricsMonitor(@NonNull Set<HttpMeasurementType> enabledTypes) {
        this.enabledTypes = enabledTypes;
        // Initialize average durations
        this.averageDurations = new AtomicReference[HttpMeasurementType.values().length];
        for (int i = 0; i < averageDurations.length; i++) {
            averageDurations[i] = new AtomicReference<>(Duration.ZERO);
        }

        // Initialize sample counts
        this.sampleCounts = new AtomicLong[HttpMeasurementType.values().length];
        for (int i = 0; i < sampleCounts.length; i++) {
            sampleCounts[i] = new AtomicLong(0);
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
        Duration newDuration = Duration.ofNanos(Math.max(0, durationNanos));

        // Increment sample count
        long count = sampleCounts[index].incrementAndGet();

        // Update average using exponential moving average
        averageDurations[index].updateAndGet(current -> {
            if (count == 1) {
                return newDuration;
            } else {
                // Use exponential moving average with alpha = 0.1 for responsiveness
                double alpha = 0.1;
                long currentNanos = current.toNanos();
                long newNanos = newDuration.toNanos();
                long avgNanos = (long) (alpha * newNanos + (1 - alpha) * currentNanos);
                return Duration.ofNanos(avgNanos);
            }
        });
    }

    /**
     * Gets the average duration for the specified measurement type.
     * <p>
     * Returns the current exponential moving average of all measurements.
     * If no measurements have been recorded, returns Duration.ZERO.
     *
     * @param measurementType the type of measurement to analyze
     * @return the average duration, or Duration.ZERO if no measurements exist
     */
    public Duration getAverageDuration(@NonNull HttpMeasurementType measurementType) {
        return averageDurations[measurementType.ordinal()].get();
    }

    /**
     * Gets the current sample count for the specified measurement type.
     *
     * @param measurementType the type of measurement to check
     * @return the number of samples recorded
     */
    public long getSampleCount(@NonNull HttpMeasurementType measurementType) {
        return sampleCounts[measurementType.ordinal()].get();
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
        averageDurations[index].set(Duration.ZERO);
        sampleCounts[index].set(0);
    }

    /**
     * Resets all measurements and counters.
     */
    public void resetAll() {
        for (int i = 0; i < averageDurations.length; i++) {
            averageDurations[i].set(Duration.ZERO);
            sampleCounts[i].set(0);
        }
        for (AtomicLong counter : requestStatusCounts) {
            counter.set(0);
        }
    }

    /**
     * Clears all measurements and counters.
     * This is an alias for {@link #resetAll()} to match the naming convention
     * used in the rest of the system.
     */
    public void clear() {
        resetAll();
    }


}