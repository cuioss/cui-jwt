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
package de.cuioss.benchmarking.common.metrics.pipeline.processors;

import de.cuioss.benchmarking.common.metrics.pipeline.MetricsContext;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessingException;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessor;
import de.cuioss.tools.logging.CuiLogger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Processor that enriches metrics with additional metadata, computed values,
 * and contextual information.
 *
 * @since 1.0
 */
public class EnrichmentProcessor implements MetricsProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(EnrichmentProcessor.class);

    /**
     * Map of enrichment functions to apply
     */
    private final Map<String, EnrichmentFunction> enrichments;

    /**
     * Whether to add default enrichments (timestamp, source info, etc.)
     */
    private final boolean addDefaults;

    /**
     * Default constructor with default enrichments enabled
     */
    public EnrichmentProcessor() {
        this(new HashMap<>(), true);
    }

    /**
     * Constructor with custom enrichments only
     */
    public EnrichmentProcessor(Map<String, EnrichmentFunction> enrichments) {
        this(enrichments, false);
    }

    /**
     * Full constructor
     */
    public EnrichmentProcessor(Map<String, EnrichmentFunction> enrichments, boolean addDefaults) {
        this.enrichments = enrichments != null ? enrichments : new HashMap<>();
        this.addDefaults = addDefaults;
    }

    @Override public MetricsContext process(MetricsContext context) throws MetricsProcessingException {
        LOGGER.debug("Enriching metrics from source: {}", context.getSource());

        // Add default enrichments if enabled
        if (addDefaults) {
            addDefaultEnrichments(context);
        }

        // Apply custom enrichments
        for (Map.Entry<String, EnrichmentFunction> entry : enrichments.entrySet()) {
            String key = entry.getKey();
            EnrichmentFunction function = entry.getValue();

            try {
                Object value = function.enrich(context);
                if (value != null) {
                    context.addMetric(key, value);
                    LOGGER.debug("Added enrichment '{}' with value: {}", key, value);
                }
            } catch (Exception e) {
                throw new MetricsProcessingException(getName(),
                        "Failed to compute enrichment for key: " + key, e);
            }
        }

        // Add derived metrics
        addDerivedMetrics(context);

        LOGGER.debug("Enrichment completed, added {} enrichments", enrichments.size());
        return context;
    }

    /**
     * Add default enrichments
     */
    private void addDefaultEnrichments(MetricsContext context) {
        // Add timestamp information
        Instant timestamp = context.getTimestamp();
        if (timestamp != null) {
            context.addMetric("timestamp_iso", timestamp.toString());
            context.addMetric("timestamp_millis", timestamp.toEpochMilli());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
            context.addMetric("timestamp_formatted", formatter.format(timestamp));
        }

        // Add source information
        String source = context.getSource();
        if (source != null) {
            context.addMetric("source_system", source);
            context.addMetric("source_type", determineSourceType(source));
        }

        // Add processing metadata
        context.addMetadata("processed_at", Instant.now().toString());
        context.addMetadata("processor", getName());
        context.addMetadata("enrichment_version", "1.0");
    }

    /**
     * Determine source type from source name
     */
    private String determineSourceType(String source) {
        if (source.toLowerCase().contains("jmh")) {
            return "benchmark";
        } else if (source.toLowerCase().contains("quarkus")) {
            return "application";
        } else if (source.toLowerCase().contains("integration")) {
            return "integration_test";
        }
        return "unknown";
    }

    /**
     * Add derived metrics based on existing metrics
     */
    private void addDerivedMetrics(MetricsContext context) {
        // Calculate throughput from response time if available
        Object p50 = context.getMetrics().get("p50");
        if (p50 instanceof Number number) {
            double responseTimeMs = number.doubleValue();
            if (responseTimeMs > 0) {
                double throughput = 1000.0 / responseTimeMs; // requests per second
                context.addMetric("estimated_throughput", throughput);
            }
        }

        // Calculate efficiency ratio if CPU and throughput are available
        Object cpuUsage = context.getMetrics().get("cpu_usage");
        Object throughput = context.getMetrics().get("throughput");
        if (cpuUsage instanceof Number number && throughput instanceof Number number1) {
            double cpu = number.doubleValue();
            double tps = number1.doubleValue();
            if (cpu > 0) {
                double efficiency = tps / cpu; // transactions per CPU percent
                context.addMetric("cpu_efficiency", efficiency);
            }
        }

        // Add percentile ratios for response time analysis
        Object p95 = context.getMetrics().get("p95");
        Object p99 = context.getMetrics().get("p99");
        if (p50 instanceof Number number && p95 instanceof Number number1) {
            double ratio = number1.doubleValue() / number.doubleValue();
            context.addMetric("p95_p50_ratio", ratio);
        }
        if (p95 instanceof Number number && p99 instanceof Number number1) {
            double ratio = number1.doubleValue() / number.doubleValue();
            context.addMetric("p99_p95_ratio", ratio);
        }
    }

    /**
     * Add an enrichment function
     */
    public EnrichmentProcessor addEnrichment(String key, EnrichmentFunction function) {
        enrichments.put(key, function);
        return this;
    }

    @Override public String getName() {
        return "EnrichmentProcessor";
    }

    /**
     * Interface for enrichment functions
     */
    @FunctionalInterface public interface EnrichmentFunction {
        /**
         * Compute enrichment value from context
         *
         * @param context The metrics context
         * @return The enriched value
         */
        Object enrich(MetricsContext context);
    }

    /**
     * Common enrichment functions
     */
    public static class Functions {

        /**
         * Add a constant value
         */
        public static EnrichmentFunction constant(Object value) {
            return context -> value;
        }

        /**
         * Transform an existing metric
         */
        public static EnrichmentFunction transform(String sourceKey, Function<Object, Object> transformer) {
            return context -> {
                Object value = context.getMetrics().get(sourceKey);
                return value != null ? transformer.apply(value) : null;
            };
        }

        /**
         * Convert units (e.g., milliseconds to microseconds)
         */
        public static EnrichmentFunction convertUnit(String sourceKey, double multiplier) {
            return context -> {
                Object value = context.getMetrics().get(sourceKey);
                if (value instanceof Number number) {
                    return number.doubleValue() * multiplier;
                }
                return null;
            };
        }

        /**
         * Add environment variable value
         */
        public static EnrichmentFunction environment(String envVar) {
            return context -> System.getenv(envVar);
        }

        /**
         * Add system property value
         */
        public static EnrichmentFunction systemProperty(String property) {
            return context -> System.getProperty(property);
        }

        /**
         * Compute value based on condition
         */
        public static EnrichmentFunction conditional(String conditionKey, Object conditionValue,
                Object trueValue, Object falseValue) {
            return context -> {
                Object value = context.getMetrics().get(conditionKey);
                return conditionValue.equals(value) ? trueValue : falseValue;
            };
        }

        /**
         * Format a numeric value with specified decimal places
         */
        public static EnrichmentFunction format(String sourceKey, int decimalPlaces) {
            return context -> {
                Object value = context.getMetrics().get(sourceKey);
                if (value instanceof Number number) {
                    double num = number.doubleValue();
                    return String.format("%." + decimalPlaces + "f", num);
                }
                return null;
            };
        }
    }
}