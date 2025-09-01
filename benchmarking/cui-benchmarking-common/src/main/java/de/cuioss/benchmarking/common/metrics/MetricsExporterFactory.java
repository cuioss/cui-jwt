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
package de.cuioss.benchmarking.common.metrics;

import de.cuioss.tools.logging.CuiLogger;

/**
 * Factory for creating appropriate MetricsExporter instances based on benchmark type.
 * 
 * @since 1.0
 */
public class MetricsExporterFactory {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsExporterFactory.class);

    /**
     * Types of metrics exporters available.
     */
    public enum ExporterType {
        /** Exporter for library benchmarks using TokenValidatorMonitor */
        LIBRARY,
        /** Exporter for integration benchmarks using MetricsFetcher */
        INTEGRATION
    }

    /**
     * Create a metrics exporter for the specified type.
     * 
     * @param type The type of exporter to create
     * @param outputDirectory The output directory for metrics files
     * @param metricsFetcher Optional MetricsFetcher for integration exporters (can be null for library exporters)
     * @return The appropriate MetricsExporter instance
     * @throws IllegalArgumentException if required parameters are missing
     */
    public static MetricsExporter createExporter(ExporterType type, String outputDirectory, MetricsFetcher metricsFetcher) {
        if (outputDirectory == null || outputDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Output directory must be specified");
        }

        switch (type) {
            case LIBRARY:
                // Use reflection to avoid compile-time dependency on benchmark-library
                try {
                    Class<?> clazz = Class.forName("de.cuioss.jwt.validation.benchmark.SimplifiedMetricsExporter");
                    Object instance = clazz.getMethod("getInstance").invoke(null);
                    if (instance instanceof MetricsExporter exporter) {
                        return exporter;
                    }
                    throw new RuntimeException("SimplifiedMetricsExporter does not implement MetricsExporter interface");
                } catch (Exception e) {
                    LOGGER.error("Failed to create library metrics exporter", e);
                    throw new RuntimeException("Failed to create library metrics exporter: " + e.getMessage(), e);
                }

            case INTEGRATION:
                if (metricsFetcher == null) {
                    throw new IllegalArgumentException("MetricsFetcher is required for integration exporters");
                }
                // Use reflection to avoid compile-time dependency on benchmark-integration-quarkus
                try {
                    Class<?> clazz = Class.forName("de.cuioss.jwt.quarkus.benchmark.metrics.SimpleMetricsExporter");
                    return (MetricsExporter) clazz.getConstructor(String.class, MetricsFetcher.class)
                            .newInstance(outputDirectory, metricsFetcher);
                } catch (Exception e) {
                    LOGGER.error("Failed to create integration metrics exporter", e);
                    throw new RuntimeException("Failed to create integration metrics exporter: " + e.getMessage(), e);
                }

            default:
                throw new IllegalArgumentException("Unknown exporter type: " + type);
        }
    }

    /**
     * Create a library benchmark metrics exporter.
     * 
     * @param outputDirectory The output directory for metrics files
     * @return The library metrics exporter
     */
    public static MetricsExporter createLibraryExporter(String outputDirectory) {
        return createExporter(ExporterType.LIBRARY, outputDirectory, null);
    }

    /**
     * Create an integration benchmark metrics exporter.
     * 
     * @param outputDirectory The output directory for metrics files
     * @param metricsFetcher The MetricsFetcher for retrieving metrics
     * @return The integration metrics exporter
     */
    public static MetricsExporter createIntegrationExporter(String outputDirectory, MetricsFetcher metricsFetcher) {
        return createExporter(ExporterType.INTEGRATION, outputDirectory, metricsFetcher);
    }
}