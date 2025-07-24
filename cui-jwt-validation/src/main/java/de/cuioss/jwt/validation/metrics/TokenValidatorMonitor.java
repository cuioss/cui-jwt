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

import de.cuioss.jwt.validation.TokenType;
import lombok.NonNull;

import java.time.Duration;

/**
 * Provides high-metrics, thread-safe monitoring of JWT validation pipeline metrics.
 * <p>
 * This class measures execution times for different stages of JWT validation with microsecond
 * precision. It maintains a configurable rolling window of recent measurements for each
 * pipeline step and provides average calculation capabilities.
 * <p>
 * <strong>Design Principles:</strong>
 * <ul>
 *   <li><strong>Thread-Safe:</strong> All operations are lock-free using atomic operations</li>
 *   <li><strong>Zero Runtime Impact:</strong> Optimized for minimal overhead during measurement</li>
 *   <li><strong>Microsecond Precision:</strong> All measurements recorded in microseconds</li>
 *   <li><strong>Rolling Window:</strong> Maintains configurable number of recent samples</li>
 *   <li><strong>Pipeline Aware:</strong> Measures each validation step separately</li>
 * </ul>
 * <p>
 * <strong>Implementation:</strong>
 * Uses a striped ring buffer design with multiple independent buffers to minimize contention
 * between threads. Each measurement type maintains its own set of striped buffers for optimal
 * metrics in high-concurrency environments.
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>
 * // Create monitor with default window size (100 samples)
 * TokenValidatorMonitor monitor = new TokenValidatorMonitor();
 *
 * // Record a measurement
 * long startNanos = System.nanoTime();
 * // ... perform signature validation ...
 * long durationNanos = System.nanoTime() - startNanos;
 * monitor.recordMeasurement(MeasurementType.SIGNATURE_VALIDATION, durationNanos);
 *
 * // Get average for analysis
 * Duration avgSignatureTime = monitor.getAverageDuration(MeasurementType.SIGNATURE_VALIDATION);
 * </pre>
 * <p>
 * This implementation is structured to simplify later integration with micrometer-based
 * metrics systems but does not create any dependency on external monitoring frameworks.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class TokenValidatorMonitor {

    /**
     * Default number of samples to maintain in the rolling window.
     */
    public static final int DEFAULT_WINDOW_SIZE = 100;

    /**
     * Striped ring buffers for each measurement type.
     */
    private final StripedRingBuffer[] measurementBuffers;

    /**
     * Tracks the count of tokens processed by type.
     * Array indexed by TokenType.ordinal()
     */
    private final java.util.concurrent.atomic.AtomicLong[] tokenTypeCounts;

    /**
     * Creates a new metrics monitor with default window size.
     */
    public TokenValidatorMonitor() {
        this(DEFAULT_WINDOW_SIZE);
    }

    /**
     * Creates a new metrics monitor with specified window size.
     *
     * @param windowSize number of samples to maintain per measurement type (must be positive)
     * @throws IllegalArgumentException if windowSize is not positive
     */
    public TokenValidatorMonitor(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be positive, got: " + windowSize);
        }

        // Initialize one striped ring buffer per measurement type
        this.measurementBuffers = new StripedRingBuffer[MeasurementType.values().length];
        for (int i = 0; i < measurementBuffers.length; i++) {
            measurementBuffers[i] = new StripedRingBuffer(windowSize);
        }

        // Initialize token type counters
        this.tokenTypeCounts = new java.util.concurrent.atomic.AtomicLong[TokenType.values().length];
        for (int i = 0; i < tokenTypeCounts.length; i++) {
            tokenTypeCounts[i] = new java.util.concurrent.atomic.AtomicLong(0);
        }
    }

    /**
     * Records a metrics measurement for the specified pipeline step.
     * <p>
     * This method is designed for maximum metrics with minimal overhead.
     * The measurement is recorded in microseconds and stored in a lock-free
     * ring buffer for later analysis.
     *
     * @param measurementType the type of measurement being recorded
     * @param durationNanos   the duration in nanoseconds (will be converted to microseconds)
     */
    public void recordMeasurement(@NonNull MeasurementType measurementType, long durationNanos) {
        // Convert nanoseconds to microseconds for consistent storage
        long durationMicros = Math.max(0, durationNanos / 1000);
        measurementBuffers[measurementType.ordinal()].recordMeasurement(durationMicros);
    }

    /**
     * Calculates the average duration for the specified measurement type.
     * <p>
     * The average is calculated from all current samples in the rolling window.
     * If no measurements have been recorded, returns Duration.ZERO.
     *
     * @param measurementType the type of measurement to analyze
     * @return the average duration, or Duration.ZERO if no measurements exist
     */
    public Duration getAverageDuration(@NonNull MeasurementType measurementType) {
        long averageMicros = measurementBuffers[measurementType.ordinal()].getAverage();
        // Convert microseconds back to Duration
        return Duration.ofNanos(averageMicros * 1000);
    }

    /**
     * Gets the current sample count for the specified measurement type.
     *
     * @param measurementType the type of measurement to check
     * @return the number of samples currently recorded (up to window size)
     */
    public int getSampleCount(@NonNull MeasurementType measurementType) {
        return measurementBuffers[measurementType.ordinal()].getSampleCount();
    }

    /**
     * Resets all measurements for the specified type.
     *
     * @param measurementType the type of measurement to reset
     */
    public void reset(@NonNull MeasurementType measurementType) {
        measurementBuffers[measurementType.ordinal()].reset();
    }

    /**
     * Resets all measurements for all types.
     */
    public void resetAll() {
        for (StripedRingBuffer buffer : measurementBuffers) {
            buffer.reset();
        }
        for (java.util.concurrent.atomic.AtomicLong counter : tokenTypeCounts) {
            counter.set(0);
        }
    }

    /**
     * Records that a token of the specified type was processed.
     * <p>
     * This method increments the counter for the given token type,
     * allowing tracking of the distribution of token types being validated.
     *
     * @param tokenType the type of token that was processed
     */
    public void recordTokenType(@NonNull TokenType tokenType) {
        tokenTypeCounts[tokenType.ordinal()].incrementAndGet();
    }

    /**
     * Gets the count of tokens processed for the specified type.
     *
     * @param tokenType the type of token to get the count for
     * @return the number of tokens of this type that have been processed
     */
    public long getTokenTypeCount(@NonNull TokenType tokenType) {
        return tokenTypeCounts[tokenType.ordinal()].get();
    }

    /**
     * Gets the counts of all token types as a map.
     *
     * @return a map of token type to count
     */
    public java.util.Map<TokenType, Long> getTokenTypeCounts() {
        java.util.Map<TokenType, Long> counts = new java.util.EnumMap<>(TokenType.class);
        for (TokenType type : TokenType.values()) {
            counts.put(type, tokenTypeCounts[type.ordinal()].get());
        }
        return counts;
    }

}