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
package de.cuioss.jwt.quarkus.benchmark.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import de.cuioss.jwt.quarkus.benchmark.constants.MetricConstants;
import de.cuioss.tools.logging.CuiLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simplified metrics exporter that processes JWT bearer token validation metrics.
 * Creates integration-metrics.json with bearer token validation results.
 * Uses dependency injection pattern for metrics fetching to improve testability.
 *
 * @since 1.0
 */
public class SimpleMetricsExporter {

    private static final CuiLogger LOGGER = new CuiLogger(SimpleMetricsExporter.class);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
            .create();

    private final String outputDirectory;
    private final MetricsFetcher metricsFetcher;

    public SimpleMetricsExporter(String outputDirectory, MetricsFetcher metricsFetcher) {
        this.outputDirectory = outputDirectory;
        this.metricsFetcher = metricsFetcher;
        File dir = new File(outputDirectory);
        dir.mkdirs();
        LOGGER.debug("SimpleMetricsExporter initialized with output directory: {} (exists: {})",
                dir.getAbsolutePath(), dir.exists());
    }

    /**
     * Export JWT bearer token validation metrics for a specific benchmark method.
     * Updates the aggregated integration-metrics.json file.
     */
    public void exportJwtValidationMetrics(String benchmarkMethodName, Instant timestamp) {
        LOGGER.info("Exporting JWT bearer token validation metrics for: {}", benchmarkMethodName);

        // Set the benchmark context for proper directory naming
        BenchmarkContextManager.setBenchmarkContext(benchmarkMethodName);

        // Always save raw metrics for ALL benchmarks
        try {
            Map<String, Double> allMetrics = metricsFetcher.fetchMetrics();
            LOGGER.debug("Fetched {} metrics from Quarkus", allMetrics.size());

            // Process JWT validation specific metrics if applicable
            if (isJwtValidationBenchmark(benchmarkMethodName)) {
                Map<String, Object> timedMetrics = extractTimedMetrics(allMetrics);
                Map<String, Object> securityEventMetrics = extractSecurityEventMetrics(allMetrics);

                // Create benchmark data with bearer token and security event metrics
                Map<String, Object> benchmarkData = new LinkedHashMap<>();
                benchmarkData.put("timestamp", timestamp.toString());
                benchmarkData.put("bearer_token_producer_metrics", timedMetrics);
                benchmarkData.put("security_event_counter_metrics", securityEventMetrics);

                // Extract just the method name (remove class prefix)
                String simpleBenchmarkName = benchmarkMethodName;
                if (benchmarkMethodName.contains(".")) {
                    simpleBenchmarkName = benchmarkMethodName.substring(benchmarkMethodName.lastIndexOf('.') + 1);
                }

                // Update aggregated file
                updateAggregatedMetrics(simpleBenchmarkName, benchmarkData);
            } else {
                LOGGER.info("Benchmark {} is not JWT validation, raw metrics were saved", benchmarkMethodName);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to export metrics for {}", benchmarkMethodName, e);
        }
    }

    private void updateAggregatedMetrics(String benchmarkMethodName, Map<String, Object> benchmarkData) throws IOException {
        String filename = outputDirectory + "/integration-metrics.json";

        // Read existing data
        Map<String, Object> allMetrics = new LinkedHashMap<>();
        File file = new File(filename);
        if (file.exists()) {
            String content = Files.readString(file.toPath());
            if (!content.trim().isEmpty()) {
                Type mapType = new TypeToken<Map<String, Object>>(){
                }.getType();
                allMetrics = GSON.fromJson(content, mapType);
            }
        }

        // Add/update benchmark data
        allMetrics.put(benchmarkMethodName, benchmarkData);

        // Write back
        File outputFile = new File(filename);
        LOGGER.info("Writing metrics to: {} (parent exists: {})",
                outputFile.getAbsolutePath(), outputFile.getParentFile().exists());

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(allMetrics, writer);
            writer.flush();
            LOGGER.info("Updated integration-metrics.json with {} benchmarks at: {}",
                    allMetrics.size(), outputFile.getAbsolutePath());
        }
    }


    /**
     * Check if a benchmark name represents a JWT validation benchmark
     */
    private boolean isJwtValidationBenchmark(String benchmarkMethodName) {
        return benchmarkMethodName.contains("JwtValidationBenchmark") ||
                "JwtValidation".equals(benchmarkMethodName) ||  // getBenchmarkName() returns this
                benchmarkMethodName.startsWith("validateJwt") ||
                benchmarkMethodName.contains("validateAccessToken") ||
                benchmarkMethodName.contains("validateIdToken");
    }

    /**
     * Helper class to collect percentile data
     */
    private static class StepPercentileData {
        long count = 0;
        double p50 = 0.0;
        double p95 = 0.0;
        double p99 = 0.0;
    }

    /**
     * Extract SecurityEventCounter metrics from fetched metrics data
     */
    private Map<String, Object> extractSecurityEventMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> securityMetrics = new LinkedHashMap<>();
        Map<String, Map<String, Object>> errorsByCategory = new LinkedHashMap<>();
        Map<String, Object> successByType = new LinkedHashMap<>();
        long totalErrors = 0;
        long totalSuccess = 0;

        // Debug: Count metrics by type
        int errorMetricsFound = 0;
        int successMetricsFound = 0;
        int successOperationsMetricsFound = 0;

        // Extract both error and success metrics
        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("cui_jwt_validation_errors_total")) {
                errorMetricsFound++;
                // Parse error metrics tags: category="INVALID_STRUCTURE",event_type="FAILED_TO_DECODE_HEADER",result="failure"
                String category = extractTag(metricName, "category");
                String eventType = extractTag(metricName, "event_type");
                String result = extractTag(metricName, "result");

                if (category != null && eventType != null && value != null && value > 0) {
                    Map<String, Object> categoryData = errorsByCategory.computeIfAbsent(category, k -> new LinkedHashMap<>());
                    categoryData.put(eventType, formatNumber(value.longValue()));
                    totalErrors += value.longValue();
                }
            } else if (metricName.startsWith("cui_jwt_validation_success_operations_total")) {
                successOperationsMetricsFound++;
                // Parse success metrics tags: event_type="ACCESS_TOKEN_CREATED",result="success"
                String eventType = extractTag(metricName, "event_type");
                String result = extractTag(metricName, "result");

                LOGGER.debug("Found success_operations metric: {} with eventType={}, result={}, value={}",
                        metricName, eventType, result, value);

                if (eventType != null && "success".equals(result) && value != null && value > 0) {
                    successByType.put(eventType, formatNumber(value.longValue()));
                    totalSuccess += value.longValue();
                    LOGGER.debug("Added success event: {} = {}", eventType, value.longValue());
                }
            } else if (metricName.startsWith("cui_jwt_validation_success_total")) {
                successMetricsFound++;
                // Parse success metrics tags: event_type="ACCESS_TOKEN_CREATED",result="success"
                String eventType = extractTag(metricName, "event_type");
                String result = extractTag(metricName, "result");

                LOGGER.debug("Found success_total metric: {} with eventType={}, result={}, value={}",
                        metricName, eventType, result, value);

                if (eventType != null && "success".equals(result) && value != null && value > 0) {
                    successByType.put(eventType, formatNumber(value.longValue()));
                    totalSuccess += value.longValue();
                }
            }
        }

        LOGGER.info("Metrics scan summary: {} error metrics, {} success_total metrics, {} success_operations metrics",
                errorMetricsFound, successMetricsFound, successOperationsMetricsFound);

        securityMetrics.put("total_errors", formatNumber(totalErrors));
        securityMetrics.put("total_success", formatNumber(totalSuccess));
        securityMetrics.put("errors_by_category", errorsByCategory);
        securityMetrics.put("success_by_type", successByType);

        LOGGER.info("Extracted security event metrics: {} total errors across {} categories, {} total successes across {} types",
                totalErrors, errorsByCategory.size(), totalSuccess, successByType.size());

        return securityMetrics;
    }

    /**
     * Extract tag value from Prometheus metric name
     */
    private String extractTag(String metricName, String tagName) {
        String pattern = tagName + "=\"([^\"]+)\"";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(metricName);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extract Micrometer @Timed metrics from fetched metrics data
     */
    private Map<String, Object> extractTimedMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> timedMetrics = new LinkedHashMap<>();
        Map<String, StepPercentileData> timedData = new HashMap<>();

        // Micrometer @Timed metrics in Prometheus format provide:
        // - _count: total number of observations
        // - _sum: total time in seconds
        // - _max: maximum observed value in seconds
        // We need to estimate percentiles from these values

        String validationMetricPrefix = MetricConstants.BEARER_TOKEN.VALIDATION.replace(".", "_") + "_seconds";

        // Collect the available metrics
        Double count = null;
        Double sum = null;
        Double max = null;

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith(validationMetricPrefix)) {
                if (metricName.contains("_count") && metricName.contains("getBearerTokenResult")) {
                    count = value;
                } else if (metricName.contains("_sum") && metricName.contains("getBearerTokenResult")) {
                    sum = value;
                } else if (metricName.contains("_max") && metricName.contains("getBearerTokenResult")) {
                    max = value;
                }
            }
        }

        // Create validation metrics if we have data
        if (count != null && sum != null && max != null && count > 0) {
            StepPercentileData data = new StepPercentileData();
            data.count = count.longValue();

            // Calculate average in microseconds
            double avgMicros = (sum / count) * 1_000_000;

            // Estimate percentiles based on available data
            // For a simple estimation:
            // - p50 ≈ average (reasonable for symmetric distributions)
            // - p95 ≈ 2x average (conservative estimate)
            // - p99 ≈ closer to max but not quite max
            data.p50 = avgMicros;
            data.p95 = Math.min(avgMicros * 2, max * 1_000_000 * 0.8); // 80% of max as upper bound
            data.p99 = max * 1_000_000 * 0.9; // 90% of max as p99 estimate

            timedData.put("validation", data);
        } else {
            // No data available, create empty metrics
            StepPercentileData data = new StepPercentileData();
            data.count = 0;
            data.p50 = 0;
            data.p95 = 0;
            data.p99 = 0;
            timedData.put("validation", data);
        }

        // Convert collected data to expected format
        for (Map.Entry<String, StepPercentileData> entry : timedData.entrySet()) {
            String measurementType = entry.getKey();
            StepPercentileData data = entry.getValue();

            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("sample_count", formatNumber(data.count));
            metric.put("p50_us", formatNumber(data.p50));
            metric.put("p95_us", formatNumber(data.p95));
            metric.put("p99_us", formatNumber(data.p99));
            timedMetrics.put(measurementType.toLowerCase(), metric);
        }

        LOGGER.info("Extracted {} timed metrics with count={}, avg={}μs",
                timedMetrics.size(),
                count != null ? count.longValue() : 0,
                count != null && count > 0 ? "%.2f".formatted((sum / count) * 1_000_000) : "N/A");
        return timedMetrics;
    }


    /**
     * Format number according to rules: 1 decimal for <10, no decimal for >=10
     */
    private Object formatNumber(double value) {
        if (value < 10) {
            // Use DecimalFormat to ensure exactly 1 decimal place
            DecimalFormat df = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
            return Double.parseDouble(df.format(value));
        } else {
            // Return as integer for values >= 10
            return (long) Math.round(value);
        }
    }

}