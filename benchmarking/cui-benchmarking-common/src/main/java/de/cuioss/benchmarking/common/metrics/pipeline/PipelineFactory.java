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

import de.cuioss.benchmarking.common.metrics.pipeline.processors.*;

import java.util.List;
import java.util.Map;

/**
 * Factory class that provides common pipeline configurations for different
 * types of metrics processing scenarios.
 *
 * @since 1.0
 */
public class PipelineFactory {

    /**
     * Create a pipeline for processing HTTP benchmark metrics from JMH results.
     * Pipeline stages: HttpMetricsProcessor -> EnrichmentProcessor -> FormatProcessor -> ExportProcessor
     *
     * @param outputDirectory Directory where metrics files should be written
     * @return Configured pipeline for HTTP metrics processing
     */
    public static MetricsPipeline createHttpMetricsPipeline(String outputDirectory) {
        return MetricsPipeline.builder("HTTPMetricsPipeline")
                .addProcessor(new HttpMetricsProcessor())
                .addProcessor(new EnrichmentProcessor())
                .addProcessor(new FormatProcessor(FormatProcessor.Format.PRETTY_JSON))
                .addProcessor(new ExportProcessor()
                        .addTarget(ExportProcessor.Targets.file(outputDirectory + "/http-metrics.json", false)))
                .build();
    }

    /**
     * Create a pipeline for processing Quarkus application metrics.
     * Pipeline stages: QuarkusMetricsProcessor -> EnrichmentProcessor -> FormatProcessor -> ExportProcessor
     *
     * @param metricsDirectory Directory containing Prometheus metrics files
     * @param outputDirectory Directory where processed metrics should be written
     * @return Configured pipeline for Quarkus metrics processing
     */
    public static MetricsPipeline createQuarkusMetricsPipeline(String metricsDirectory, String outputDirectory) {
        return MetricsPipeline.builder("QuarkusMetricsPipeline")
                .addProcessor(new QuarkusMetricsProcessor(metricsDirectory))
                .addProcessor(new EnrichmentProcessor())
                .addProcessor(new FormatProcessor(FormatProcessor.Format.PRETTY_JSON))
                .addProcessor(new ExportProcessor()
                        .addTarget(ExportProcessor.Targets.file(outputDirectory + "/quarkus-metrics.json", false)))
                .build();
    }

    /**
     * Create a comprehensive pipeline that processes both HTTP and Quarkus metrics.
     * This pipeline can handle multiple input sources and produces combined output.
     *
     * @param outputDirectory Directory where combined metrics should be written
     * @return Configured pipeline for comprehensive metrics processing
     */
    public static MetricsPipeline createComprehensiveMetricsPipeline(String outputDirectory) {
        return MetricsPipeline.builder("ComprehensiveMetricsPipeline")
                .stopOnError(false)  // Continue processing even if one source fails
                .addProcessor(new ValidationProcessor())
                .addProcessor(new AggregationProcessor())
                .addProcessor(new EnrichmentProcessor())
                .addProcessor(new FormatProcessor(FormatProcessor.Format.PRETTY_JSON))
                .addProcessor(new ExportProcessor()
                        .addTarget(ExportProcessor.Targets.timestampedFile(outputDirectory, "metrics", "json"))
                        .addTarget(ExportProcessor.Targets.file(outputDirectory + "/latest-metrics.json", false)))
                .build();
    }

    /**
     * Create a pipeline optimized for development/debugging with extensive logging and validation.
     *
     * @param outputDirectory Directory where debug metrics should be written
     * @return Configured pipeline for development/debugging
     */
    public static MetricsPipeline createDebugPipeline(String outputDirectory) {
        // Create validation processor with strict mode
        ValidationProcessor validator = new ValidationProcessor(
                List.of("timestamp", "source"), // Required keys
                Map.of(), // No custom validation rules for now
                false // Non-strict mode for debugging
        );

        return MetricsPipeline.builder("DebugPipeline")
                .stopOnError(false)  // Don't stop on errors for debugging
                .addProcessor(validator)
                .addProcessor(new EnrichmentProcessor())
                .addProcessor(new AggregationProcessor())
                .addProcessor(new FormatProcessor(FormatProcessor.Format.PRETTY_JSON))
                .addProcessor(new ExportProcessor()
                        .addTarget(ExportProcessor.Targets.multiFile(outputDirectory + "/debug", "json")))
                .build();
    }

    /**
     * Create a minimal pipeline for simple metrics export without processing.
     *
     * @param outputDirectory Directory where raw metrics should be written
     * @return Configured pipeline for minimal processing
     */
    public static MetricsPipeline createMinimalPipeline(String outputDirectory) {
        return MetricsPipeline.builder("MinimalPipeline")
                .addProcessor(new FormatProcessor(FormatProcessor.Format.PRETTY_JSON))
                .addProcessor(new ExportProcessor()
                        .addTarget(ExportProcessor.Targets.file(outputDirectory + "/raw-metrics.json", false)))
                .build();
    }

    /**
     * Create a custom pipeline with user-specified processors.
     * This is useful when you need complete control over the processing stages.
     *
     * @param name Pipeline name
     * @param processors Array of processors to add to the pipeline
     * @return Configured custom pipeline
     */
    public static MetricsPipeline createCustomPipeline(String name, MetricsProcessor... processors) {
        MetricsPipeline.Builder builder = MetricsPipeline.builder(name);
        for (MetricsProcessor processor : processors) {
            builder.addProcessor(processor);
        }
        return builder.build();
    }

    /**
     * Create a pipeline configuration based on source type.
     * This factory method automatically selects appropriate processors based on the source.
     *
     * @param source The metrics source type (e.g., "JMH", "Quarkus", "Integration")
     * @param outputDirectory Directory for output files
     * @return Configured pipeline appropriate for the source type
     */
    public static MetricsPipeline createForSource(String source, String outputDirectory) {
        if (source == null || source.isEmpty()) {
            return createMinimalPipeline(outputDirectory);
        }

        String lowerSource = source.toLowerCase();
        if (lowerSource.contains("jmh") || lowerSource.contains("benchmark")) {
            return createHttpMetricsPipeline(outputDirectory);
        } else if (lowerSource.contains("quarkus")) {
            // Note: This assumes metrics directory is in standard location
            String metricsDir = outputDirectory + "/../metrics-download";
            return createQuarkusMetricsPipeline(metricsDir, outputDirectory);
        } else {
            return createComprehensiveMetricsPipeline(outputDirectory);
        }
    }
}