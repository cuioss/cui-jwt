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

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.EnumSet;
import java.util.Set;

/**
 * Configuration for HttpMetricsMonitor to control which metrics are collected
 * and how they are stored.
 * <p>
 * This configuration allows selective monitoring of HTTP JWT processing pipeline steps,
 * enabling fine-grained control over metrics collection overhead and storage
 * requirements. When no measurement types are configured, no metrics will be
 * recorded, effectively disabling monitoring.
 * <p>
 * <strong>Performance Considerations:</strong>
 * <ul>
 *   <li>Only configured measurement types incur recording overhead</li>
 *   <li>Unconfigured types result in no-op operations</li>
 *   <li>Empty measurement type set disables all monitoring</li>
 * </ul>
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>
 * // Monitor only request processing and authorization checks
 * HttpMetricsMonitorConfig config = HttpMetricsMonitorConfig.builder()
 *     .measurementType(HttpMeasurementType.REQUEST_PROCESSING)
 *     .measurementType(HttpMeasurementType.AUTHORIZATION_CHECK)
 *     .build();
 *
 * // Create configured monitor
 * HttpMetricsMonitor monitor = config.createMonitor();
 *
 * // Or disable all monitoring
 * HttpMetricsMonitorConfig disabled = HttpMetricsMonitorConfig.builder()
 *     .build(); // No measurement types = disabled
 * </pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@Builder
@Getter
public class HttpMetricsMonitorConfig {

    /**
     * All available measurement types for convenient configuration.
     * Use this constant when you want to monitor all pipeline steps.
     */
    public static final Set<HttpMeasurementType> ALL_MEASUREMENT_TYPES =
            EnumSet.allOf(HttpMeasurementType.class);

    /**
     * The set of measurement types to monitor and record.
     * <p>
     * Only the measurement types included in this set will be recorded.
     * All other measurement types will result in no-op operations.
     * If this set is empty, no monitoring will be performed at all.
     * <p>
     * Use {@link #ALL_MEASUREMENT_TYPES} to monitor all available types.
     */
    @Singular
    private final Set<HttpMeasurementType> measurementTypes;

    /**
     * Window size for the ring buffer (number of samples to retain).
     * <p>
     * Defaults to 10000 if not specified.
     * Higher values provide more accurate percentiles but use more memory.
     */
    @Builder.Default
    private final int windowSize = 10000;

    /**
     * Creates a preconfigured HttpMetricsMonitor based on this configuration.
     * <p>
     * The returned monitor will only record measurements for the configured
     * measurement types. All other types will be ignored (no-op).
     *
     * @return a new HttpMetricsMonitor configured according to this config
     */
    public HttpMetricsMonitor createMonitor() {
        Set<HttpMeasurementType> types = measurementTypes != null ? measurementTypes : EnumSet.noneOf(HttpMeasurementType.class);
        return new HttpMetricsMonitor(types, windowSize);
    }


    /**
     * Creates a configuration with default settings that monitors all measurement types.
     *
     * @return configuration with all measurement types enabled
     */
    public static HttpMetricsMonitorConfig defaultEnabled() {
        return HttpMetricsMonitorConfig.builder()
                .measurementTypes(ALL_MEASUREMENT_TYPES)
                .build();
    }

    /**
     * Creates a configuration that disables all monitoring.
     *
     * @return configuration with no measurement types enabled
     */
    public static HttpMetricsMonitorConfig disabled() {
        return HttpMetricsMonitorConfig.builder()
                .build(); // Empty measurement types = disabled
    }
}