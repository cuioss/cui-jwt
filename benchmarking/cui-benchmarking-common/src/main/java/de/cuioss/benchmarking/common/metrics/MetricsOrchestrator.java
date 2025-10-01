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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator for metrics processing.
 * Coordinates the download, transformation, and export of metrics.
 * Delegates all transformation logic to MetricsTransformer.
 */
public class MetricsOrchestrator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsOrchestrator.class);

    private final String metricsURL;
    private final Path downloadsDirectory;
    private final Path targetDirectory;
    private final PrometheusClient prometheusClient;

    /**
     * Creates a new metrics orchestrator.
     *
     * @param metricsURL URL to download metrics from
     * @param downloadsDirectory Directory to store downloaded metrics files
     * @param targetDirectory Directory to write processed metrics JSON files
     */
    public MetricsOrchestrator(String metricsURL, Path downloadsDirectory, Path targetDirectory) {
        this.metricsURL = metricsURL;
        this.downloadsDirectory = downloadsDirectory;
        this.targetDirectory = targetDirectory;
        // Initialize PrometheusClient with default settings
        // Assumes Prometheus is running at localhost:9090 (configurable in production)
        this.prometheusClient = new PrometheusClient("http://localhost:9090");
    }

    /**
     * Creates a new metrics orchestrator with custom Prometheus client.
     *
     * @param metricsURL URL to download metrics from
     * @param downloadsDirectory Directory to store downloaded metrics files
     * @param targetDirectory Directory to write processed metrics JSON files
     * @param prometheusClient PrometheusClient for real-time metrics collection
     */
    public MetricsOrchestrator(String metricsURL, Path downloadsDirectory, Path targetDirectory, PrometheusClient prometheusClient) {
        this.metricsURL = metricsURL;
        this.downloadsDirectory = downloadsDirectory;
        this.targetDirectory = targetDirectory;
        this.prometheusClient = prometheusClient;
    }


    /**
     * Collects real-time metrics from Prometheus for a benchmark execution.
     * Fetches time-series data, calculates statistics (avg, p50, p95, max),
     * and exports to JSON format.
     *
     * @param benchmarkName Name of the benchmark for file naming
     * @param startTime Start time of the benchmark execution
     * @param endTime End time of the benchmark execution
     * @param outputDir Directory to store the metrics JSON file
     * @throws IOException if I/O operations fail
     */
    public void collectBenchmarkMetrics(String benchmarkName, Instant startTime, Instant endTime, Path outputDir)
            throws IOException {
        LOGGER.info("Collecting real-time metrics for benchmark '{}' from {} to {}",
                benchmarkName, startTime, endTime);

        // Define metrics to collect during benchmark execution
        // Using actual metric names from Prometheus
        List<String> metricNames = List.of(
                // CPU metrics
                "process_cpu_usage",
                "system_cpu_usage",
                "system_cpu_count",

                // Memory metrics
                "jvm_memory_used_bytes",
                "jvm_memory_committed_bytes",
                "jvm_memory_max_bytes",

                // Thread metrics
                "jvm_threads_live_threads",
                "jvm_threads_daemon_threads",
                "jvm_threads_peak_threads",

                // GC metrics
                "jvm_gc_overhead",

                // JWT specific metrics
                "cui_jwt_validation_success_operations_total",
                "cui_jwt_validation_errors_total",
                "cui_jwt_bearer_token_validation_seconds_count",
                "cui_jwt_bearer_token_validation_seconds_sum"
        );

        try {
            // Query Prometheus for metrics within the benchmark time window
            Duration step = Duration.ofSeconds(2); // 2-second resolution matching scrape interval
            Map<String, PrometheusClient.TimeSeries> timeSeriesData =
                    prometheusClient.queryRange(metricNames, startTime, endTime, step);

            // Transform time-series data using the new BenchmarkMetricsTransformer
            BenchmarkMetricsTransformer transformer = new BenchmarkMetricsTransformer();
            Map<String, Object> metricsOutput = transformer.transformToServerMetrics(
                    benchmarkName, startTime, endTime, timeSeriesData);

            // Export to JSON file in the format specified in benchmark-metrics.adoc
            Files.createDirectories(outputDir);
            String fileName = "%s-server-metrics.json".formatted(benchmarkName);
            MetricsJsonExporter exporter = new MetricsJsonExporter(outputDir);
            exporter.exportToFile(fileName, metricsOutput);

            Path outputFile = outputDir.resolve(fileName);
            LOGGER.info("Exported real-time metrics for '{}' to: {}", benchmarkName, outputFile);

        } catch (PrometheusClient.PrometheusException e) {
            LOGGER.error("Failed to collect Prometheus metrics for benchmark '{}': {}",
                    benchmarkName, e.getMessage());
            throw new IOException("Failed to collect Prometheus metrics", e);
        }
    }
}
