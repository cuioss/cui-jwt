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

import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline manager that orchestrates the execution of metrics processors
 * in a chain of responsibility pattern.
 *
 * @since 1.0
 */
public class MetricsPipeline {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsPipeline.class);

    /**
     * List of processors in the pipeline
     */
    @Getter
    private final List<MetricsProcessor> processors = new ArrayList<>();

    /**
     * Pipeline name for identification
     */
    @Getter
    private final String name;

    /**
     * Whether to stop on first error or continue processing
     */
    private final boolean stopOnError;

    /**
     * Constructor with default settings
     */
    public MetricsPipeline(String name) {
        this(name, true);
    }

    /**
     * Constructor with custom settings
     */
    public MetricsPipeline(String name, boolean stopOnError) {
        this.name = name;
        this.stopOnError = stopOnError;
    }

    /**
     * Add a processor to the pipeline
     *
     * @param processor The processor to add
     * @return This pipeline for fluent API
     */
    public MetricsPipeline addProcessor(MetricsProcessor processor) {
        if (processor != null) {
            processors.add(processor);
            LOGGER.debug("Added processor '{}' to pipeline '{}'", processor.getName(), name);
        }
        return this;
    }

    /**
     * Add multiple processors to the pipeline
     *
     * @param processorList The processors to add
     * @return This pipeline for fluent API
     */
    public MetricsPipeline addProcessors(List<MetricsProcessor> processorList) {
        if (processorList != null) {
            processorList.forEach(this::addProcessor);
        }
        return this;
    }

    /**
     * Execute the pipeline with the given context
     *
     * @param context The metrics context to process
     * @return The processed context
     * @throws MetricsProcessingException if processing fails and stopOnError is true
     */
    public MetricsContext execute(MetricsContext context) throws MetricsProcessingException {
        LOGGER.debug("Executing pipeline '{}' with {} processors", name, processors.size());

        if (context == null) {
            throw new MetricsProcessingException("Pipeline '" + name + "': Context cannot be null");
        }

        context.setStatus(MetricsContext.ProcessingStatus.PROCESSING);

        for (MetricsProcessor processor : processors) {
            try {
                if (processor.shouldProcess(context)) {
                    LOGGER.debug("Executing processor '{}'", processor.getName());
                    context = processor.process(context);

                    if (context == null) {
                        throw new MetricsProcessingException(processor.getName(),
                                "Processor returned null context");
                    }
                } else {
                    LOGGER.debug("Skipping processor '{}' (shouldProcess returned false)",
                            processor.getName());
                }
            } catch (MetricsProcessingException e) {
                handleProcessingError(context, processor, e);
            } catch (Exception e) {
                MetricsProcessingException wrapped = new MetricsProcessingException(
                        processor.getName(), "Unexpected error during processing", e);
                handleProcessingError(context, processor, wrapped);
            }
        }

        if (context.getStatus() == MetricsContext.ProcessingStatus.PROCESSING) {
            context.markSuccess();
        }

        LOGGER.debug("Pipeline '{}' execution completed with status: {}", name, context.getStatus());
        return context;
    }

    /**
     * Handle processing errors based on pipeline configuration
     */
    private void handleProcessingError(MetricsContext context, MetricsProcessor processor,
            MetricsProcessingException e) throws MetricsProcessingException {

        LOGGER.error("Processor '{}' failed in pipeline '{}'", processor.getName(), name, e);
        context.markFailed(e.getMessage());

        if (stopOnError) {
            throw e;
        }

        LOGGER.warn("Continuing pipeline execution despite error (stopOnError=false)");
    }

    /**
     * Clear all processors from the pipeline
     */
    public void clear() {
        processors.clear();
        LOGGER.debug("Cleared all processors from pipeline '{}'", name);
    }

    /**
     * Get the number of processors in the pipeline
     */
    public int size() {
        return processors.size();
    }

    /**
     * Check if the pipeline is empty
     */
    public boolean isEmpty() {
        return processors.isEmpty();
    }

    /**
     * Create a builder for constructing pipelines
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Builder class for constructing MetricsPipeline instances
     */
    public static class Builder {
        private final String name;
        private boolean stopOnError = true;
        private final List<MetricsProcessor> processors = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder stopOnError(boolean stopOnError) {
            this.stopOnError = stopOnError;
            return this;
        }

        public Builder addProcessor(MetricsProcessor processor) {
            if (processor != null) {
                processors.add(processor);
            }
            return this;
        }

        public Builder addProcessors(List<MetricsProcessor> processorList) {
            if (processorList != null) {
                processors.addAll(processorList);
            }
            return this;
        }

        public MetricsPipeline build() {
            MetricsPipeline pipeline = new MetricsPipeline(name, stopOnError);
            pipeline.addProcessors(processors);
            return pipeline;
        }
    }
}