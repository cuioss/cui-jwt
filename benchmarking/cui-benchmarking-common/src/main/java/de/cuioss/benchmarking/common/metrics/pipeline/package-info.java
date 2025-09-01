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

/**
 * Metrics processing pipeline implementation using the chain of responsibility pattern.
 * 
 * <h2>Overview</h2>
 * This package provides a flexible pipeline framework for processing benchmark metrics
 * from various sources (JMH benchmarks, Quarkus application metrics, etc.) through
 * a series of configurable processing stages.
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessor} - Interface for processing stages</li>
 *   <li>{@link de.cuioss.benchmarking.common.metrics.pipeline.MetricsContext} - Context object that carries data through the pipeline</li>
 *   <li>{@link de.cuioss.benchmarking.common.metrics.pipeline.MetricsPipeline} - Pipeline manager that orchestrates processors</li>
 *   <li>{@link de.cuioss.benchmarking.common.metrics.pipeline.PipelineFactory} - Factory for common pipeline configurations</li>
 * </ul>
 * 
 * <h2>Processing Stages</h2>
 * The processors package contains implementations for common processing stages:
 * <ul>
 *   <li>Validation - Ensures data completeness and correctness</li>
 *   <li>Aggregation - Computes statistical summaries and derived metrics</li>
 *   <li>Enrichment - Adds contextual information and metadata</li>
 *   <li>Formatting - Converts metrics to various output formats (JSON, CSV, etc.)</li>
 *   <li>Export - Writes processed metrics to files or external systems</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Simple HTTP Metrics Processing</h3>
 * <pre>{@code
 * // Create pipeline for HTTP benchmark metrics
 * MetricsPipeline pipeline = PipelineFactory.createHttpMetricsPipeline("/output/dir");
 * 
 * // Create context with JMH benchmark results
 * MetricsContext context = new MetricsContext("JMH");
 * context.setRawData(benchmarkJsonString);
 * 
 * // Execute pipeline
 * MetricsContext result = pipeline.execute(context);
 * }</pre>
 * 
 * <h3>Custom Pipeline Configuration</h3>
 * <pre>{@code
 * // Build custom pipeline
 * MetricsPipeline pipeline = MetricsPipeline.builder("CustomPipeline")
 *     .addProcessor(new ValidationProcessor())
 *     .addProcessor(new EnrichmentProcessor())
 *     .addProcessor(new FormatProcessor(Format.PRETTY_JSON))
 *     .addProcessor(new ExportProcessor()
 *         .addTarget(Targets.file("/output/custom-metrics.json", false)))
 *     .build();
 * }</pre>
 * 
 * <h3>Multiple Export Targets</h3>
 * <pre>{@code
 * // Pipeline that exports to multiple formats and locations
 * ExportProcessor exporter = new ExportProcessor()
 *     .addTarget(Targets.file("/output/metrics.json", false))
 *     .addTarget(Targets.timestampedFile("/archive", "metrics", "json"))
 *     .addTarget(Targets.multiFile("/output/by-category", "json"));
 * 
 * MetricsPipeline pipeline = MetricsPipeline.builder("MultiExportPipeline")
 *     .addProcessor(new HttpMetricsProcessor())
 *     .addProcessor(new FormatProcessor())
 *     .addProcessor(exporter)
 *     .build();
 * }</pre>
 * 
 * <h2>Extension Points</h2>
 * The pipeline framework is designed for extensibility:
 * <ul>
 *   <li>Implement {@link de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessor} for custom processing stages</li>
 *   <li>Add custom aggregation operations via {@link de.cuioss.benchmarking.common.metrics.pipeline.processors.AggregationProcessor.AggregationOperation}</li>
 *   <li>Create custom enrichment functions via {@link de.cuioss.benchmarking.common.metrics.pipeline.processors.EnrichmentProcessor.EnrichmentFunction}</li>
 *   <li>Implement custom export targets via {@link de.cuioss.benchmarking.common.metrics.pipeline.processors.ExportProcessor.ExportTarget}</li>
 *   <li>Add custom formatters via {@link de.cuioss.benchmarking.common.metrics.pipeline.processors.FormatProcessor.FormatterFunction}</li>
 * </ul>
 *
 * @since 1.0
 */
package de.cuioss.benchmarking.common.metrics.pipeline;