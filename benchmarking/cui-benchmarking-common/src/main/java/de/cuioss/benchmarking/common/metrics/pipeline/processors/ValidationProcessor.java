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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Processor that validates metrics data for completeness and correctness.
 * Ensures that required fields are present and values are within expected ranges.
 *
 * @since 1.0
 */
public class ValidationProcessor implements MetricsProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(ValidationProcessor.class);

    /**
     * List of required metric keys that must be present
     */
    private final List<String> requiredKeys;

    /**
     * Map of metric keys to their validation rules
     */
    private final Map<String, ValidationRule> validationRules;

    /**
     * Whether to fail on validation errors or just log warnings
     */
    private final boolean strictMode;

    /**
     * Constructor with default settings (non-strict mode)
     */
    public ValidationProcessor() {
        this(new ArrayList<>(), Map.of(), false);
    }

    /**
     * Constructor with required keys
     */
    public ValidationProcessor(List<String> requiredKeys) {
        this(requiredKeys, Map.of(), false);
    }

    /**
     * Full constructor
     */
    public ValidationProcessor(List<String> requiredKeys, Map<String, ValidationRule> validationRules,
            boolean strictMode) {
        this.requiredKeys = requiredKeys != null ? requiredKeys : new ArrayList<>();
        this.validationRules = validationRules != null ? validationRules : Map.of();
        this.strictMode = strictMode;
    }

    @Override public MetricsContext process(MetricsContext context) throws MetricsProcessingException {
        LOGGER.debug("Validating metrics context from source: {}", context.getSource());

        List<String> validationErrors = new ArrayList<>();

        // Check required keys
        for (String key : requiredKeys) {
            if (!context.getMetrics().containsKey(key)) {
                validationErrors.add("Missing required metric: " + key);
            }
        }

        // Apply validation rules
        for (Map.Entry<String, ValidationRule> entry : validationRules.entrySet()) {
            String key = entry.getKey();
            ValidationRule rule = entry.getValue();

            Object value = context.getMetrics().get(key);
            if (value != null) {
                String error = rule.validate(key, value);
                if (error != null) {
                    validationErrors.add(error);
                }
            }
        }

        // Handle validation results
        if (!validationErrors.isEmpty()) {
            String errorMessage = "Validation failed: " + String.join(", ", validationErrors);

            if (strictMode) {
                throw new MetricsProcessingException(getName(), errorMessage);
            } else {
                LOGGER.warn(errorMessage);
                context.addMetadata("validation_warnings", validationErrors);
            }
        }

        LOGGER.debug("Validation completed with {} errors/warnings", validationErrors.size());
        return context;
    }

    @Override public String getName() {
        return "ValidationProcessor";
    }

    /**
     * Interface for validation rules
     */
    @FunctionalInterface public interface ValidationRule {
        /**
         * Validate a value and return error message if invalid
         *
         * @param key The metric key
         * @param value The value to validate
         * @return Error message if invalid, null if valid
         */
        String validate(String key, Object value);
    }

    /**
     * Common validation rules
     */
    public static class Rules {

        /**
         * Create a rule that checks if a numeric value is within a range
         */
        public static ValidationRule range(double min, double max) {
            return (key, value) -> {
                if (value instanceof Number number) {
                    double num = number.doubleValue();
                    if (num < min || num > max) {
                        return "%s value %.2f is outside range [%.2f, %.2f]".formatted(
                                key, num, min, max);
                    }
                }
                return null;
            };
        }

        /**
         * Create a rule that checks if a numeric value is positive
         */
        public static ValidationRule positive() {
            return (key, value) -> {
                if (value instanceof Number number) {
                    double num = number.doubleValue();
                    if (num <= 0) {
                        return "%s value %.2f must be positive".formatted(key, num);
                    }
                }
                return null;
            };
        }

        /**
         * Create a rule that checks if a value is of a specific type
         */
        public static ValidationRule type(Class<?> expectedType) {
            return (key, value) -> {
                if (!expectedType.isInstance(value)) {
                    return "%s value must be of type %s, but was %s".formatted(
                            key, expectedType.getSimpleName(), value.getClass().getSimpleName());
                }
                return null;
            };
        }

        /**
         * Create a rule that checks if a string matches a pattern
         */
        public static ValidationRule pattern(String regex) {
            return (key, value) -> {
                if (value instanceof String str) {
                    if (!str.matches(regex)) {
                        return "%s value '%s' does not match pattern '%s'".formatted(
                                key, str, regex);
                    }
                }
                return null;
            };
        }
    }
}