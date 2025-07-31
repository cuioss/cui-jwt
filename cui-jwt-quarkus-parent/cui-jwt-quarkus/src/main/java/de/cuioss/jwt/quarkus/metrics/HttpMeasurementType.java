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

import de.cuioss.jwt.validation.metrics.MetricsTicker;
import de.cuioss.jwt.validation.metrics.NoOpMetricsTicker;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Enum defining all measurement types for HTTP JWT processing pipeline steps.
 * <p>
 * Each measurement type represents a specific phase of the HTTP request processing
 * that can be independently monitored and analyzed for metrics optimization.
 * <p>
 * The measurements follow the HTTP request processing order:
 * <ol>
 *   <li>{@link #REQUEST_PROCESSING} - Total request processing time</li>
 *   <li>{@link #HEADER_EXTRACTION} - Authorization header extraction</li>
 *   <li>{@link #TOKEN_EXTRACTION} - Bearer token extraction from header</li>
 *   <li>{@link #AUTHORIZATION_CHECK} - Scopes/roles/groups validation</li>
 *   <li>{@link #RESPONSE_FORMATTING} - Error response formatting</li>
 * </ol>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor
@Getter
public enum HttpMeasurementType {
    /**
     * Total request processing time from start to finish.
     * <p>
     * Includes all pipeline steps, error handling, and represents the total
     * time taken for an HTTP request from the perspective of the caller.
     * This is the most important metric for end-to-end metrics analysis.
     */
    REQUEST_PROCESSING("Total HTTP request processing"),

    /**
     * Time to extract Authorization header from request.
     * <p>
     * Measures time to locate and extract the Authorization header from
     * the HTTP request. Performance issues here may indicate problems
     * with header processing or request parsing.
     */
    HEADER_EXTRACTION("Authorization header extraction"),

    /**
     * Time to extract bearer token from Authorization header.
     * <p>
     * Measures time to parse the Authorization header value and extract
     * the bearer token. This includes validation of the header format
     * and token extraction logic.
     */
    TOKEN_EXTRACTION("Bearer token extraction"),

    /**
     * Time to check scopes, roles, and groups after validation.
     * <p>
     * Measures time to verify that the validated token contains the
     * required scopes, roles, or groups for the requested resource.
     * This is typically fast but can indicate authorization logic complexity.
     */
    AUTHORIZATION_CHECK("Authorization requirements check"),

    /**
     * Time to format error responses.
     * <p>
     * Measures time to create error response bodies for failed requests.
     * This includes JSON formatting and error message construction.
     */
    RESPONSE_FORMATTING("Error response formatting");

    /**
     * Human-readable description of this measurement type for logging and monitoring.
     */
    private final String description;

    /**
     * Creates a {@link MetricsTicker} for this measurement type.
     * <p>
     * The recording decision is derived from the monitor's enabled types configuration.
     * If this measurement type is not enabled in the monitor, returns a no-op ticker with zero overhead.
     * Otherwise, returns an active ticker that will record measurements to the provided monitor.
     *
     * @param monitor the monitor to record measurements to (or check for enabled status)
     * @return a MetricsTicker instance appropriate for the monitor's configuration
     */
    @NonNull
    public MetricsTicker createTicker(@NonNull HttpMetricsMonitor monitor) {
        if (!monitor.isEnabled(this)) {
            return NoOpMetricsTicker.INSTANCE;
        }
        return new ActiveMetricsTicker(monitor, this);
    }

    /**
     * Creates a started {@link MetricsTicker} for this measurement type.
     * <p>
     * This is a convenience method that creates a ticker and immediately calls
     * {@link MetricsTicker#startRecording()} on it. This simplifies the common
     * pattern of creating a ticker and immediately starting it.
     * <p>
     * The recording decision is derived from the monitor's enabled types configuration.
     * If this measurement type is not enabled in the monitor, returns a no-op ticker with zero overhead.
     * Otherwise, returns an active ticker that is already started.
     *
     * @param monitor the monitor to record measurements to (or check for enabled status)
     * @return a started MetricsTicker instance appropriate for the monitor's configuration
     */
    @NonNull
    public MetricsTicker createStartedTicker(@NonNull HttpMetricsMonitor monitor) {
        MetricsTicker ticker = createTicker(monitor);
        ticker.startRecording();
        return ticker;
    }
}