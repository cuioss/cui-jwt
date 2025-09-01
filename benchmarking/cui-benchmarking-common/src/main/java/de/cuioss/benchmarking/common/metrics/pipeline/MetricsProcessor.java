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

/**
 * Interface for metrics processors in the chain of responsibility pattern.
 * Each processor handles a specific aspect of metrics processing and can
 * pass the context to the next processor in the chain.
 *
 * @since 1.0
 */
public interface MetricsProcessor {

    /**
     * Process the metrics context. Implementations should modify the context
     * in-place and return it for chaining.
     *
     * @param context The metrics context containing data to process
     * @return The processed context for chaining
     * @throws MetricsProcessingException if processing fails
     */
    MetricsContext process(MetricsContext context) throws MetricsProcessingException;

    /**
     * Get the name of this processor for logging and debugging purposes.
     *
     * @return The processor name
     */
    String getName();

    /**
     * Check if this processor should be executed for the given context.
     * Default implementation returns true.
     *
     * @param context The metrics context to check
     * @return true if this processor should process the context, false otherwise
     */
    default boolean shouldProcess(MetricsContext context) {
        return true;
    }
}