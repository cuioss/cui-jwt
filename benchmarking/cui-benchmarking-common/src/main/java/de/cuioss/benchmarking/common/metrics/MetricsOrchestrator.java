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
import java.util.*;

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
        List<String> metricNames = List.of(
                "process_cpu_usage",
                "system_cpu_usage",
                "jvm_memory_used_bytes",
                "jvm_memory_committed_bytes",
                "jvm_memory_max_bytes",
                "jvm_gc_collection_seconds_count",
                "jvm_gc_collection_seconds_sum",
                "jvm_threads_current",
                "jvm_threads_daemon",
                "http_server_requests_seconds_count",
                "http_server_requests_seconds_sum",
                "cui_jwt_validation_success_operations_total"
        );

        try {
            // Query Prometheus for metrics within the benchmark time window
            Duration step = Duration.ofSeconds(2); // 2-second resolution matching scrape interval
            Map<String, PrometheusClient.TimeSeries> timeSeriesData =
                    prometheusClient.queryRange(metricNames, startTime, endTime, step);

            // Process time-series data to calculate statistics
            Map<String, Object> processedMetrics = processTimeSeriesData(timeSeriesData);

            // Add metadata
            Map<String, Object> metricsOutput = new LinkedHashMap<>();
            metricsOutput.put("benchmark_name", benchmarkName);
            metricsOutput.put("start_time", startTime.toString());
            metricsOutput.put("end_time", endTime.toString());
            metricsOutput.put("duration_seconds", Duration.between(startTime, endTime).getSeconds());
            metricsOutput.put("metrics", processedMetrics);

            // Export to JSON file
            Files.createDirectories(outputDir);
            String fileName = "%s-metrics.json".formatted(benchmarkName);
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

    /**
     * Processes time-series data to calculate statistical summaries.
     *
     * @param timeSeriesData Raw time-series data from Prometheus
     * @return Processed metrics with appropriate statistics (avg/min/max for CPU, full stats for others)
     */
    private Map<String, Object> processTimeSeriesData(Map<String, PrometheusClient.TimeSeries> timeSeriesData) {
        Map<String, Object> processed = new LinkedHashMap<>();

        for (Map.Entry<String, PrometheusClient.TimeSeries> entry : timeSeriesData.entrySet()) {
            String metricName = entry.getKey();
            PrometheusClient.TimeSeries timeSeries = entry.getValue();

            List<PrometheusClient.DataPoint> dataPoints = timeSeries.getValues();
            if (dataPoints.isEmpty()) {
                LOGGER.warn("No data points found for metric: {}", metricName);
                continue;
            }

            // Extract values for statistical calculation
            List<Double> values = new ArrayList<>();
            for (PrometheusClient.DataPoint dp : dataPoints) {
                values.add(dp.getValue());
            }

            // Calculate appropriate statistics based on metric type
            Map<String, Double> stats;
            if (isCpuMetric(metricName)) {
                // For CPU metrics: use avg, min, max only (no percentiles)
                stats = calculateCpuStatistics(values);
            } else {
                // For other metrics: use full statistics including percentiles
                stats = calculateStatistics(values);
            }

            // Build metric structure
            Map<String, Object> metricData = new LinkedHashMap<>();
            metricData.put("labels", timeSeries.getLabels());
            metricData.put("data_points", dataPoints.size());
            metricData.put("statistics", stats);

            processed.put(metricName, metricData);
        }

        return processed;
    }

    /**
     * Checks if a metric is CPU-related.
     *
     * @param metricName Name of the metric
     * @return true if CPU metric, false otherwise
     */
    private boolean isCpuMetric(String metricName) {
        return metricName.contains("cpu_usage") || metricName.contains("cpu_percent");
    }

    /**
     * Calculates CPU-specific statistics (avg, min, max).
     *
     * @param values List of CPU usage values
     * @return Map containing avg, min, max statistics
     */
    private Map<String, Double> calculateCpuStatistics(List<Double> values) {
        if (values.isEmpty()) {
            return Map.of("avg", 0.0, "min", 0.0, "max", 0.0);
        }

        double avg = values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double min = values.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);

        double max = values.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        return Map.of(
                "avg", avg,
                "min", min,
                "max", max
        );
    }

    /**
     * Calculates full statistical summaries for non-CPU metrics.
     *
     * @param values List of numeric values
     * @return Map containing avg, p50, p95, max statistics
     */
    private Map<String, Double> calculateStatistics(List<Double> values) {
        if (values.isEmpty()) {
            return Map.of("avg", 0.0, "p50", 0.0, "p95", 0.0, "max", 0.0);
        }

        // Sort values for percentile calculation
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);

        // Calculate average
        double avg = values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Calculate percentiles
        int size = sorted.size();
        double p50 = sorted.get(size / 2);
        double p95 = sorted.get((int) Math.ceil(size * 0.95) - 1);
        double max = sorted.get(size - 1);

        return Map.of(
                "avg", avg,
                "p50", p50,
                "p95", p95,
                "max", max
        );
    }
}