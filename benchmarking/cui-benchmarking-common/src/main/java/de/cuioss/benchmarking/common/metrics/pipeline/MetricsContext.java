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
package de.cuioss.benchmarking.common.metrics.pipeline;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object that holds metrics data as it passes through the processing pipeline.
 * This class uses a flexible map-based structure to support various metric types
 * and allow processors to add, modify, or remove data.
 *
 * @since 1.0
 */
@Getter @Setter public class MetricsContext {

    /**
     * The timestamp when these metrics were collected
     */
    private Instant timestamp;

    /**
     * The source of the metrics (e.g., "JMH", "Quarkus", "Integration")
     */
    private String source;

    /**
     * Raw data that needs to be processed (e.g., JSON string, file content)
     */
    private Object rawData;

    /**
     * Processed metrics data organized by metric type
     */
    private final Map<String, Object> metrics = new HashMap<>();

    /**
     * Metadata about the metrics or processing
     */
    private final Map<String, Object> metadata = new HashMap<>();

    /**
     * Configuration parameters for processors
     */
    private final Map<String, Object> configuration = new HashMap<>();

    /**
     * Processing status and errors
     */
    private ProcessingStatus status = ProcessingStatus.PENDING;

    /**
     * Error message if processing failed
     */
    private String errorMessage;

    /**
     * Constructor with required fields
     */
    public MetricsContext(String source) {
        this.source = source;
        this.timestamp = Instant.now();
    }

    /**
     * Constructor with source and raw data
     */
    public MetricsContext(String source, Object rawData) {
        this(source);
        this.rawData = rawData;
    }

    /**
     * Add a metric value
     */
    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }

    /**
     * Get a metric value with type casting
     */
    @SuppressWarnings("unchecked") public <T> T getMetric(String key, Class<T> type) {
        Object value = metrics.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Add metadata
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Get metadata value with type casting
     */
    @SuppressWarnings("unchecked") public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Add configuration parameter
     */
    public void addConfiguration(String key, Object value) {
        configuration.put(key, value);
    }

    /**
     * Get configuration value with type casting
     */
    @SuppressWarnings("unchecked") public <T> T getConfiguration(String key, Class<T> type) {
        Object value = configuration.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Mark processing as successful
     */
    public void markSuccess() {
        this.status = ProcessingStatus.SUCCESS;
        this.errorMessage = null;
    }

    /**
     * Mark processing as failed
     */
    public void markFailed(String errorMessage) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Check if processing was successful
     */
    public boolean isSuccessful() {
        return ProcessingStatus.SUCCESS == status;
    }

    /**
     * Processing status enum
     */
    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED
    }
}