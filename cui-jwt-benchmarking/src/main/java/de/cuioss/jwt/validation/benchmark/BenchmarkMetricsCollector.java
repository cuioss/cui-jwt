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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Collects and exports JWT validation metrics in the same format as integration tests.
 * This allows direct comparison between microbenchmark and integration test performance.
 * 
 * Also provides utility methods to reduce code duplication across benchmark classes.
 */
public class BenchmarkMetricsCollector {

    private static String getOutputDir() {
        return System.getProperty("benchmark.results.dir", "target/benchmark-results");
    }

    /**
     * Get output directory, using cached value from BenchmarkMetricsAggregator if available.
     * This ensures the shutdown hook uses the correct directory path.
     */
    static String getOutputDirForExport() {
        String cachedDir = BenchmarkMetricsAggregator.getCachedOutputDir();
        if (cachedDir != null) {
            return cachedDir;
        }
        return getOutputDir();
    }
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Collects metrics from a TokenValidatorMonitor and aggregates them.
     * This is a utility method to avoid code duplication across benchmark classes.
     * 
     * @param monitor The TokenValidatorMonitor to collect metrics from
     * @param currentBenchmarkName The name of the current benchmark being executed
     */
    public static void collectIterationMetrics(TokenValidatorMonitor monitor, String currentBenchmarkName) {
        if (currentBenchmarkName != null && monitor != null) {
            // Collect metrics for each measurement type
            for (MeasurementType type : monitor.getEnabledTypes()) {
                Optional<StripedRingBufferStatistics> metricsOpt = monitor.getValidationMetrics(type);

                if (metricsOpt.isPresent()) {
                    StripedRingBufferStatistics metrics = metricsOpt.get();
                    if (metrics.sampleCount() > 0) {
                        // Use the pre-calculated percentiles from StripedRingBufferStatistics
                        long p50Nanos = metrics.p50().toNanos();
                        long p95Nanos = metrics.p95().toNanos();
                        long p99Nanos = metrics.p99().toNanos();

                        // Aggregate the actual percentile values
                        BenchmarkMetricsAggregator.aggregatePreCalculatedMetrics(
                                currentBenchmarkName,
                                type,
                                metrics.sampleCount(),
                                p50Nanos,
                                p95Nanos,
                                p99Nanos
                        );
                    }
                }
            }
        }
    }

    /**
     * Determines the current benchmark name from the thread name or stack trace.
     * This is a utility method to avoid code duplication across benchmark classes.
     * 
     * @param validBenchmarkNames Array of valid benchmark method names to check for
     * @return The current benchmark name or null if not found
     */
    public static String getCurrentBenchmarkName(String[] validBenchmarkNames) {
        // JMH typically includes the benchmark method name in the thread name
        String threadName = Thread.currentThread().getName();

        for (String name : validBenchmarkNames) {
            if (threadName.contains(name)) {
                return name;
            }
        }

        // Fallback: check stack trace
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String methodName = element.getMethodName();
            for (String name : validBenchmarkNames) {
                if (name.equals(methodName)) {
                    return methodName;
                }
            }
        }

        return null;
    }

    /**
     * Exports aggregated metrics collected from benchmarks
     */
    public static void exportAggregatedMetrics(Map<String, Map<String, Object>> aggregatedMetrics) throws IOException {
        // Create output directory - use cached value if available (for shutdown hook context)
        String outputDirPath = getOutputDirForExport();
        Path outputDir = Path.of(outputDirPath);
        Files.createDirectories(outputDir);

        Map<String, Object> metricsJson = new LinkedHashMap<>();
        metricsJson.put("timestamp", ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));

        // Convert benchmark metrics to integration test format
        Map<String, Map<String, Object>> steps = new LinkedHashMap<>();

        // Aggregate metrics across all benchmarks
        Map<String, Map<String, Double>> aggregatedStepMetrics = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : aggregatedMetrics.entrySet()) {
            String benchmarkName = entry.getKey();
            Map<String, Object> benchmarkData = entry.getValue();

            for (Map.Entry<String, Object> stepEntry : benchmarkData.entrySet()) {
                String stepName = stepEntry.getKey();
                Map<String, Object> stepMetrics = (Map<String, Object>) stepEntry.getValue();

                // Initialize aggregated metrics for this step if needed
                aggregatedStepMetrics.putIfAbsent(stepName, new LinkedHashMap<>());
                Map<String, Double> aggregated = aggregatedStepMetrics.get(stepName);

                // Aggregate each metric
                Long sampleCount = (Long) stepMetrics.get("sample_count");
                Double p50Us = (Double) stepMetrics.get("p50_us");
                Double p95Us = (Double) stepMetrics.get("p95_us");
                Double p99Us = (Double) stepMetrics.get("p99_us");

                if (sampleCount != null && sampleCount > 0) {
                    // Weight metrics by sample count
                    aggregated.merge("sample_count", sampleCount.doubleValue(), Double::sum);
                    aggregated.merge("p50_sum", p50Us * sampleCount, Double::sum);
                    aggregated.merge("p95_sum", p95Us * sampleCount, Double::sum);
                    aggregated.merge("p99_sum", p99Us * sampleCount, Double::sum);
                }
            }
        }

        // Calculate final weighted averages
        for (Map.Entry<String, Map<String, Double>> entry : aggregatedStepMetrics.entrySet()) {
            String stepName = entry.getKey();
            Map<String, Double> metrics = entry.getValue();

            Double totalSamples = metrics.get("sample_count");
            if (totalSamples != null && totalSamples > 0) {
                Map<String, Object> stepMetrics = new LinkedHashMap<>();
                stepMetrics.put("sample_count", totalSamples.longValue());

                // Calculate and round microsecond values
                double p50 = metrics.get("p50_sum") / totalSamples;
                double p95 = metrics.get("p95_sum") / totalSamples;
                double p99 = metrics.get("p99_sum") / totalSamples;

                stepMetrics.put("p50_us", roundMicroseconds(p50));
                stepMetrics.put("p95_us", roundMicroseconds(p95));
                stepMetrics.put("p99_us", roundMicroseconds(p99));
                steps.put(stepName, stepMetrics);
            }
        }

        metricsJson.put("steps", steps);

        // Add benchmark-specific metrics
        metricsJson.put("benchmark_metrics", aggregatedMetrics);

        // Write JSON file - use dynamic output directory
        Path outputFile = Path.of(outputDirPath, "jwt-validation-metrics.json");
        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            writer.write(formatJson(metricsJson));
        }
    }

    /**
     * Simple JSON formatter for readable output
     */
    private static String formatJson(Map<String, Object> json) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        formatJsonObject(json, sb, "  ");
        sb.append("}\n");
        return sb.toString();
    }

    private static void formatJsonObject(Map<String, Object> obj, StringBuilder sb, String indent) {
        Iterator<Map.Entry<String, Object>> iter = obj.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Object> entry = iter.next();
            sb.append(indent).append("\"").append(entry.getKey()).append("\": ");

            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                if (map.isEmpty()) {
                    sb.append("{}");
                } else {
                    sb.append("{\n");
                    formatJsonObject((Map<String, Object>) value, sb, indent + "  ");
                    sb.append(indent).append("}");
                }
            } else if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }

            if (iter.hasNext()) {
                sb.append(",");
            }
            sb.append("\n");
        }
    }

    /**
     * Round microseconds appropriately:
     * - Values >= 1: round to integer
     * - Values < 1: round to 1 decimal place
     * 
     * @param microseconds value in microseconds
     * @return appropriately rounded value
     */
    private static double roundMicroseconds(double microseconds) {
        if (microseconds >= 1.0) {
            // Round to integer for values >= 1
            return Math.round(microseconds);
        } else {
            // Round to 1 decimal place for values < 1
            return Math.round(microseconds * 10) / 10.0;
        }
    }
}