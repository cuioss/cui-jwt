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

/**
 * Collects and exports JWT validation metrics in the same format as integration tests.
 * This allows direct comparison between microbenchmark and integration test performance.
 */
public class BenchmarkMetricsCollector {

    private static final String OUTPUT_DIR = "target/benchmark-results";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Exports aggregated metrics collected from benchmarks
     */
    public static void exportAggregatedMetrics(Map<String, Map<String, Object>> aggregatedMetrics) throws IOException {
        // Create output directory
        Path outputDir = Path.of(OUTPUT_DIR);
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
                Double p50Ms = (Double) stepMetrics.get("p50_ms");
                Double p95Ms = (Double) stepMetrics.get("p95_ms");
                Double p99Ms = (Double) stepMetrics.get("p99_ms");

                if (sampleCount != null && sampleCount > 0) {
                    // Weight metrics by sample count
                    aggregated.merge("sample_count", sampleCount.doubleValue(), Double::sum);
                    aggregated.merge("p50_sum", p50Ms * sampleCount, Double::sum);
                    aggregated.merge("p95_sum", p95Ms * sampleCount, Double::sum);
                    aggregated.merge("p99_sum", p99Ms * sampleCount, Double::sum);
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
                stepMetrics.put("p50_ms", Math.round((metrics.get("p50_sum") / totalSamples) * 1000) / 1000.0);
                stepMetrics.put("p95_ms", Math.round((metrics.get("p95_sum") / totalSamples) * 1000) / 1000.0);
                stepMetrics.put("p99_ms", Math.round((metrics.get("p99_sum") / totalSamples) * 1000) / 1000.0);
                steps.put(stepName, stepMetrics);
            }
        }

        metricsJson.put("steps", steps);

        // Add benchmark-specific metrics
        metricsJson.put("benchmark_metrics", aggregatedMetrics);

        // Add http_metrics section for compatibility
        Map<String, Map<String, Object>> httpMetrics = new LinkedHashMap<>();
        Map<String, Object> jwtValidation = new LinkedHashMap<>();

        // Calculate total sample count from all steps
        long totalSampleCount = 0;
        for (Map<String, Object> stepMetrics : steps.values()) {
            totalSampleCount += (Long) stepMetrics.get("sample_count");
        }

        jwtValidation.put("request_count", totalSampleCount);
        jwtValidation.put("total_duration_ms", 0.0);
        jwtValidation.put("average_duration_ms", 0.0);
        httpMetrics.put("jwt_validation", jwtValidation);
        metricsJson.put("http_metrics", httpMetrics);

        // Add http_status_counts section
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        statusCounts.put("200", 0);
        metricsJson.put("http_status_counts", statusCounts);

        // Write JSON file
        Path outputFile = Path.of(OUTPUT_DIR, "jwt-validation-metrics.json");
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
}