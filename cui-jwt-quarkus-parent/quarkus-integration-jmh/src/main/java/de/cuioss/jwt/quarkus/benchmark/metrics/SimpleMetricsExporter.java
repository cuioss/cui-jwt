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
import de.cuioss.tools.logging.CuiLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Simplified metrics exporter that processes JWT validation metrics.
 * Only creates integration-jwt-validation-metrics.json with all JWT validation benchmark results.
 * Uses dependency injection pattern for metrics fetching to improve testability.
 *
 * @author Generated
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
        LOGGER.info("SimpleMetricsExporter initialized with output directory: {} (exists: {})", 
                dir.getAbsolutePath(), dir.exists());
    }

    /**
     * Export JWT validation metrics for a specific benchmark method.
     * Updates the aggregated integration-jwt-validation-metrics.json file.
     */
    public void exportJwtValidationMetrics(String benchmarkMethodName, Instant timestamp) {
        LOGGER.info("Exporting JWT validation metrics for: {}", benchmarkMethodName);
        
        // Set the benchmark context for proper directory naming
        BenchmarkContextManager.setBenchmarkContext(benchmarkMethodName);
        
        // Always save raw metrics for ALL benchmarks
        try {
            Map<String, Double> allMetrics = metricsFetcher.fetchMetrics();
            LOGGER.debug("Fetched {} metrics from Quarkus", allMetrics.size());
            
            // Process JWT validation specific metrics if applicable
            if (isJwtValidationBenchmark(benchmarkMethodName)) {
                Map<String, Object> stepMetrics = extractStepMetrics(allMetrics);
                Map<String, Object> httpMetrics = extractHttpMetrics(allMetrics);
                
                // Create benchmark data
                Map<String, Object> benchmarkData = new LinkedHashMap<>();
                benchmarkData.put("timestamp", timestamp.toString());
                benchmarkData.put("steps", stepMetrics);
                benchmarkData.put("bearer_token_producer_metrics", httpMetrics);
                
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
        String filename = outputDirectory + "/integration-jwt-validation-metrics.json";
        
        // Read existing data
        Map<String, Object> allMetrics = new LinkedHashMap<>();
        File file = new File(filename);
        if (file.exists()) {
            String content = Files.readString(file.toPath());
            if (!content.trim().isEmpty()) {
                allMetrics = GSON.fromJson(content, Map.class);
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
            LOGGER.info("Updated integration-jwt-validation-metrics.json with {} benchmarks at: {}", 
                    allMetrics.size(), outputFile.getAbsolutePath());
        }
    }

    /**
     * Extract JWT validation step metrics from fetched metrics data
     */
    private Map<String, Object> extractStepMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> stepMetrics = new LinkedHashMap<>();
        Map<String, StepPercentileData> stepData = new HashMap<>();
        
        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            // Parse cui_jwt_validation_duration_percentiles_microseconds metrics
            if (metricName.startsWith("cui_jwt_validation_duration_percentiles_microseconds")) {
                String stepName = extractStepName(metricName);
                if (stepName != null && !metricName.contains("_max")) {
                    StepPercentileData data = stepData.computeIfAbsent(stepName, k -> new StepPercentileData());
                    
                    if (metricName.contains("_count")) {
                        data.count = value.longValue();
                    } else if (metricName.contains("quantile=\"0.5\"")) {
                        data.p50 = value;
                    } else if (metricName.contains("quantile=\"0.95\"")) {
                        data.p95 = value;
                    } else if (metricName.contains("quantile=\"0.99\"")) {
                        data.p99 = value;
                    }
                }
            }
        }

        // Convert collected data to expected format
        for (Map.Entry<String, StepPercentileData> entry : stepData.entrySet()) {
            String stepName = entry.getKey();
            StepPercentileData data = entry.getValue();
            
            if (data.count > 0) {
                Map<String, Object> metric = new LinkedHashMap<>();
                metric.put("sample_count", formatNumber(data.count));
                metric.put("p50_us", formatNumber(data.p50));
                metric.put("p95_us", formatNumber(data.p95));
                metric.put("p99_us", formatNumber(data.p99));
                stepMetrics.put(stepName, metric);
            }
        }

        LOGGER.info("Extracted {} step metrics", stepMetrics.size());
        return stepMetrics;
    }


    /**
     * Extract step name from metric name like "cui_jwt_validation_duration_seconds_count{step="token_parsing"}"
     */
    private String extractStepName(String metricName) {
        Pattern pattern = Pattern.compile("step=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(metricName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }


    /**
     * Helper class to collect step percentile data
     */
    private static class StepPercentileData {
        long count = 0;
        double p50 = 0.0;
        double p95 = 0.0;
        double p99 = 0.0;
    }

    /**
     * Check if a benchmark name represents a JWT validation benchmark
     */
    private boolean isJwtValidationBenchmark(String benchmarkMethodName) {
        return benchmarkMethodName.contains("JwtValidationBenchmark") || 
               benchmarkMethodName.equals("JwtValidation") ||  // getBenchmarkName() returns this
               benchmarkMethodName.startsWith("validateJwt") ||
               benchmarkMethodName.contains("validateAccessToken") ||
               benchmarkMethodName.contains("validateIdToken");
    }

    /**
     * Extract HTTP metrics from fetched metrics data
     */
    private Map<String, Object> extractHttpMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> httpMetrics = new LinkedHashMap<>();
        Map<String, StepPercentileData> httpData = new HashMap<>();
        
        // HTTP metrics pattern: cui_jwt_http_request_duration_percentiles_microseconds{type="token_extraction",quantile="0.5"}
        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("cui_jwt_http_request_duration_percentiles_microseconds")) {
                String measurementType = extractHttpMeasurementType(metricName);
                if (measurementType != null && !metricName.contains("_max")) {
                    StepPercentileData data = httpData.computeIfAbsent(measurementType, k -> new StepPercentileData());
                    
                    if (metricName.contains("_count")) {
                        data.count = value.longValue();
                    } else if (metricName.contains("quantile=\"0.5\"")) {
                        data.p50 = value;
                    } else if (metricName.contains("quantile=\"0.95\"")) {
                        data.p95 = value;
                    } else if (metricName.contains("quantile=\"0.99\"")) {
                        data.p99 = value;
                    }
                }
            }
        }

        // Convert collected data to expected format
        for (Map.Entry<String, StepPercentileData> entry : httpData.entrySet()) {
            String measurementType = entry.getKey();
            StepPercentileData data = entry.getValue();
            
            // Include all metrics even if count is 0
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("sample_count", formatNumber(data.count));
            metric.put("p50_us", formatNumber(data.p50));
            metric.put("p95_us", formatNumber(data.p95));
            metric.put("p99_us", formatNumber(data.p99));
            httpMetrics.put(measurementType.toLowerCase(), metric);
        }

        LOGGER.info("Extracted {} HTTP metrics", httpMetrics.size());
        return httpMetrics;
    }

    /**
     * Extract HTTP measurement type from metric name
     */
    private String extractHttpMeasurementType(String metricName) {
        // Try type attribute first (used in the test data)
        Pattern typePattern = Pattern.compile("type=\"([^\"]+)\"");
        Matcher typeMatcher = typePattern.matcher(metricName);
        if (typeMatcher.find()) {
            return typeMatcher.group(1);
        }
        
        // Fall back to measurement attribute
        Pattern measurementPattern = Pattern.compile("measurement=\"([^\"]+)\"");
        Matcher measurementMatcher = measurementPattern.matcher(metricName);
        if (measurementMatcher.find()) {
            return measurementMatcher.group(1);
        }
        
        return null;
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