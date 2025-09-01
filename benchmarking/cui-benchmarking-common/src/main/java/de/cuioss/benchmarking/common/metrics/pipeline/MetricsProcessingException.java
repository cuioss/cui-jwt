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

import java.io.Serial;

/**
 * Exception thrown when metrics processing fails in the pipeline.
 *
 * @since 1.0
 */
public class MetricsProcessingException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The processor that threw the exception
     */
    private final String processorName;

    /**
     * Constructor with message
     */
    public MetricsProcessingException(String message) {
        super(message);
        this.processorName = null;
    }

    /**
     * Constructor with message and cause
     */
    public MetricsProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.processorName = null;
    }

    /**
     * Constructor with processor name and message
     */
    public MetricsProcessingException(String processorName, String message) {
        super("[%s] %s".formatted(processorName, message));
        this.processorName = processorName;
    }

    /**
     * Constructor with processor name, message and cause
     */
    public MetricsProcessingException(String processorName, String message, Throwable cause) {
        super("[%s] %s".formatted(processorName, message), cause);
        this.processorName = processorName;
    }

    /**
     * Get the processor name that threw the exception
     */
    public String getProcessorName() {
        return processorName;
    }
}