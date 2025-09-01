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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Processor that aggregates metrics data, computing statistical summaries
 * such as averages, percentiles, min/max values, and other aggregate metrics.
 *
 * @since 1.0
 */
public class AggregationProcessor implements MetricsProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(AggregationProcessor.class);

    /**
     * Map of aggregation operations to perform
     */
    private final Map<String, AggregationOperation> operations;

    /**
     * Default constructor with no predefined operations
     */
    public AggregationProcessor() {
        this(new HashMap<>());
    }

    /**
     * Constructor with predefined operations
     */
    public AggregationProcessor(Map<String, AggregationOperation> operations) {
        this.operations = operations != null ? operations : new HashMap<>();
    }

    @Override public MetricsContext process(MetricsContext context) throws MetricsProcessingException {
        LOGGER.debug("Aggregating metrics from source: {}", context.getSource());

        for (Map.Entry<String, AggregationOperation> entry : operations.entrySet()) {
            String resultKey = entry.getKey();
            AggregationOperation operation = entry.getValue();

            try {
                Object result = operation.aggregate(context);
                if (result != null) {
                    context.addMetric(resultKey, result);
                    LOGGER.debug("Added aggregated metric '{}' with value: {}", resultKey, result);
                }
            } catch (Exception e) {
                throw new MetricsProcessingException(getName(),
                        "Failed to compute aggregation for key: " + resultKey, e);
            }
        }

        // Auto-compute common aggregations if certain patterns are detected
        autoComputeAggregations(context);

        LOGGER.debug("Aggregation completed, computed {} aggregate metrics", operations.size());
        return context;
    }

    /**
     * Auto-compute common aggregations based on metric patterns
     */
    private void autoComputeAggregations(MetricsContext context) {
        // Look for percentile data and compute summary statistics
        Map<String, List<Double>> percentileGroups = new HashMap<>();

        for (Map.Entry<String, Object> entry : context.getMetrics().entrySet()) {
            String key = entry.getKey();

            // Group percentile metrics (e.g., p50, p95, p99)
            if (key.matches("p\\d+")) {
                String group = "percentiles";
                percentileGroups.computeIfAbsent(group, k -> new ArrayList<>());

                if (entry.getValue() instanceof Number) {
                    percentileGroups.get(group).add(((Number) entry.getValue()).doubleValue());
                }
            }
        }

        // Add summary statistics for grouped metrics
        for (Map.Entry<String, List<Double>> group : percentileGroups.entrySet()) {
            if (!group.getValue().isEmpty()) {
                DoubleSummaryStatistics stats = group.getValue().stream()
                        .mapToDouble(Double::doubleValue)
                        .summaryStatistics();

                context.addMetric(group.getKey() + "_avg", stats.getAverage());
                context.addMetric(group.getKey() + "_min", stats.getMin());
                context.addMetric(group.getKey() + "_max", stats.getMax());
            }
        }
    }

    /**
     * Add an aggregation operation
     */
    public AggregationProcessor addOperation(String resultKey, AggregationOperation operation) {
        operations.put(resultKey, operation);
        return this;
    }

    @Override public String getName() {
        return "AggregationProcessor";
    }

    /**
     * Interface for aggregation operations
     */
    @FunctionalInterface public interface AggregationOperation {
        /**
         * Perform aggregation on the context
         *
         * @param context The metrics context
         * @return The aggregated result
         */
        Object aggregate(MetricsContext context);
    }

    /**
     * Common aggregation operations
     */
    public static class Operations {

        /**
         * Sum numeric values from multiple metrics
         */
        public static AggregationOperation sum(String... metricKeys) {
            return context -> {
                double sum = 0;
                for (String key : metricKeys) {
                    Object value = context.getMetrics().get(key);
                    if (value instanceof Number number) {
                        sum += number.doubleValue();
                    }
                }
                return sum;
            };
        }

        /**
         * Average numeric values from multiple metrics
         */
        public static AggregationOperation average(String... metricKeys) {
            return context -> {
                double sum = 0;
                int count = 0;
                for (String key : metricKeys) {
                    Object value = context.getMetrics().get(key);
                    if (value instanceof Number number) {
                        sum += number.doubleValue();
                        count++;
                    }
                }
                return count > 0 ? sum / count : 0;
            };
        }

        /**
         * Find minimum value from multiple metrics
         */
        public static AggregationOperation min(String... metricKeys) {
            return context -> {
                double min = Double.MAX_VALUE;
                boolean found = false;
                for (String key : metricKeys) {
                    Object value = context.getMetrics().get(key);
                    if (value instanceof Number number) {
                        double num = number.doubleValue();
                        if (num < min) {
                            min = num;
                            found = true;
                        }
                    }
                }
                return found ? min : null;
            };
        }

        /**
         * Find maximum value from multiple metrics
         */
        public static AggregationOperation max(String... metricKeys) {
            return context -> {
                double max = Double.MIN_VALUE;
                boolean found = false;
                for (String key : metricKeys) {
                    Object value = context.getMetrics().get(key);
                    if (value instanceof Number number) {
                        double num = number.doubleValue();
                        if (num > max) {
                            max = num;
                            found = true;
                        }
                    }
                }
                return found ? max : null;
            };
        }

        /**
         * Count non-null metrics
         */
        public static AggregationOperation count(String... metricKeys) {
            return context -> {
                long count = 0;
                for (String key : metricKeys) {
                    if (context.getMetrics().containsKey(key) &&
                            context.getMetrics().get(key) != null) {
                        count++;
                    }
                }
                return count;
            };
        }

        /**
         * Calculate percentile from a list of values
         */
        public static AggregationOperation percentile(String listKey, double percentile) {
            return context -> {
                Object value = context.getMetrics().get(listKey);
                if (value instanceof List) {
                    @SuppressWarnings("unchecked") List<Number> numbers = (List<Number>) value;
                    if (!numbers.isEmpty()) {
                        List<Double> sorted = numbers.stream()
                                .map(Number::doubleValue)
                                .sorted()
                                .collect(Collectors.toList());

                        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
                        index = Math.max(0, Math.min(index, sorted.size() - 1));
                        return sorted.get(index);
                    }
                }
                return null;
            };
        }

        /**
         * Concatenate string values
         */
        public static AggregationOperation concat(String separator, String... metricKeys) {
            return context -> {
                List<String> values = new ArrayList<>();
                for (String key : metricKeys) {
                    Object value = context.getMetrics().get(key);
                    if (value != null) {
                        values.add(value.toString());
                    }
                }
                return String.join(separator, values);
            };
        }
    }
}