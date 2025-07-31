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
import de.cuioss.tools.logging.CuiLogger;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exports metrics from Prometheus endpoint and saves them as structured JSON files.
 * Collects both JVM and application-specific metrics for performance analysis.
 * 
 * @author Generated
 * @since 1.0
 */
public class MetricsExporter {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsExporter.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final String quarkusUrl;
    private final String outputDirectory;

    /**
     * Creates a new MetricsExporter.
     * 
     * @param quarkusUrl the Quarkus application URL (e.g., https://localhost:8443)
     * @param outputDirectory the directory to save metrics files
     */
    public MetricsExporter(String quarkusUrl, String outputDirectory) {
        this.quarkusUrl = quarkusUrl;
        this.outputDirectory = outputDirectory;
        
        // Ensure output directory exists
        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Collects and exports metrics for the given benchmark.
     * 
     * @param benchmarkName the name of the benchmark
     * @param metadata additional metadata about the benchmark run
     * @return the collected metrics
     */
    public BenchmarkMetrics exportMetrics(String benchmarkName, BenchmarkMetrics.BenchmarkMetadata metadata) {
        LOGGER.info("Collecting metrics for benchmark: {}", benchmarkName);

        try {
            // Collect JVM metrics
            JvmMetrics jvmMetrics = collectJvmMetrics();
            
            // Collect application metrics
            ApplicationMetrics applicationMetrics = collectApplicationMetrics();
            
            // Build complete metrics object
            BenchmarkMetrics metrics = BenchmarkMetrics.builder()
                    .timestamp(Instant.now())
                    .benchmarkName(benchmarkName)
                    .jvmMetrics(jvmMetrics)
                    .applicationMetrics(applicationMetrics)
                    .metadata(metadata)
                    .build();

            // Save to JSON file with timestamp
            saveMetricsToFile(metrics, benchmarkName);
            
            // Also save as jwt-validation-metrics.json for standardized access
            saveJwtValidationMetrics(metrics);
            
            LOGGER.info("Metrics exported successfully for benchmark: {}", benchmarkName);
            return metrics;
            
        } catch (Exception e) {
            LOGGER.error("Failed to export metrics for benchmark: {}", benchmarkName, e);
            throw new RuntimeException("Failed to export metrics", e);
        }
    }

    private JvmMetrics collectJvmMetrics() {
        LOGGER.debug("Collecting JVM metrics from Quarkus");
        
        try {
            Map<String, Double> metrics = queryQuarkusMetrics();

            return JvmMetrics.builder()
                    .heapUsedBytes(getHeapMetricSum(metrics, "jvm_memory_used_bytes", "heap"))
                    .heapCommittedBytes(getHeapMetricSum(metrics, "jvm_memory_committed_bytes", "heap"))
                    .heapMaxBytes(getHeapMetricSum(metrics, "jvm_memory_max_bytes", "heap"))
                    .nonHeapUsedBytes(getHeapMetricSum(metrics, "jvm_memory_used_bytes", "nonheap"))
                    .nonHeapCommittedBytes(getHeapMetricSum(metrics, "jvm_memory_committed_bytes", "nonheap"))
                    .nonHeapMaxBytes(getHeapMetricSum(metrics, "jvm_memory_max_bytes", "nonheap"))
                    .gcCollectionSecondsTotal(getDoubleValue(metrics, "jvm_gc_collection_seconds_total"))
                    .threadsCurrent(getIntValue(metrics, "jvm_threads_current"))
                    .threadsPeak(getIntValue(metrics, "jvm_threads_peak_threads"))
                    .cpuUsage(getDoubleValue(metrics, "process_cpu_usage"))
                    .systemLoadAverage1m(getDoubleValue(metrics, "system_load_average_1m"))
                    .build();
                    
        } catch (Exception e) {
            LOGGER.warn("Failed to collect JVM metrics, returning empty metrics", e);
            return JvmMetrics.builder().build();
        }
    }

    private ApplicationMetrics collectApplicationMetrics() {
        LOGGER.debug("Collecting application metrics from Quarkus");
        
        try {
            Map<String, Double> metrics = queryQuarkusMetrics();

            // Sum all HTTP requests (Quarkus exports per URI/method/status)
            long totalRequests = getMetricSum(metrics, "http_server_requests_seconds_count");
            long successRequests = getMetricSumByStatusPattern(metrics, "http_server_requests_seconds_count", "2");
            long errorRequests = totalRequests - successRequests;

            return ApplicationMetrics.builder()
                    .httpRequestsTotal(totalRequests)
                    .httpRequestsSuccessTotal(successRequests)
                    .httpRequestsErrorTotal(errorRequests)
                    .httpRequestDurationSecondsMean(getDoubleValue(metrics, "http_server_requests_seconds_sum") / Math.max(1, totalRequests))
                    .httpRequestDurationSeconds50p(getDoubleValue(metrics, "http_server_requests_seconds{quantile=\"0.5\"}"))
                    .httpRequestDurationSeconds95p(getDoubleValue(metrics, "http_server_requests_seconds{quantile=\"0.95\"}"))
                    .httpRequestDurationSeconds99p(getDoubleValue(metrics, "http_server_requests_seconds{quantile=\"0.99\"}"))
                    .jwtValidationsTotal(getMetricSum(metrics, "cui_jwt_validation_total"))
                    .jwtValidationsSuccessTotal(getMetricSumByResult(metrics, "cui_jwt_validation_total", "success"))
                    .jwtValidationsErrorTotal(getMetricSumByResult(metrics, "cui_jwt_validation_total", "error"))
                    .jwtCacheHitsTotal(getLongValue(metrics, "cui_jwt_cache_hits_total"))
                    .jwtCacheMissesTotal(getLongValue(metrics, "cui_jwt_cache_misses_total"))
                    .jwtValidationDurationSecondsMean(getDoubleValue(metrics, "cui_jwt_validation_duration_seconds_sum") / 
                            Math.max(1, getMetricSum(metrics, "cui_jwt_validation_total")))
                    .build();
                    
        } catch (Exception e) {
            LOGGER.warn("Failed to collect application metrics, returning empty metrics", e);
            return ApplicationMetrics.builder().build();
        }
    }

    private Map<String, Double> queryQuarkusMetrics() {
        Map<String, Double> results = new HashMap<>();
        
        try {
            String metricsUrl = quarkusUrl + "/q/metrics";
            Response response = RestAssured.get(metricsUrl);
            
            if (response.getStatusCode() == 200) {
                String responseBody = response.getBody().asString();
                parseQuarkusMetrics(responseBody, results);
            } else {
                LOGGER.warn("Failed to query Quarkus metrics: HTTP {}", response.getStatusCode());
            }
        } catch (Exception e) {
            LOGGER.warn("Error querying Quarkus metrics", e);
        }
        
        return results;
    }

    private void parseQuarkusMetrics(String responseBody, Map<String, Double> results) {
        // Parse Quarkus metrics in Prometheus text format
        // Format: # HELP metric_name description
        //         # TYPE metric_name counter/gauge/histogram
        //         metric_name{labels} value timestamp
        
        String[] lines = responseBody.split("\n");
        for (String line : lines) {
            line = line.trim();
            
            // Skip comments and empty lines
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            
            // Parse metric line: metric_name{labels} value [timestamp]
            int spaceIndex = line.lastIndexOf(' ');
            if (spaceIndex > 0) {
                String metricPart = line.substring(0, spaceIndex);
                String valuePart = line.substring(spaceIndex + 1);
                
                // Extract metric name (before { or space)
                String metricName;
                int braceIndex = metricPart.indexOf('{');
                if (braceIndex > 0) {
                    metricName = metricPart.substring(0, braceIndex);
                } else {
                    metricName = metricPart;
                }
                
                try {
                    double value = Double.parseDouble(valuePart);
                    results.put(metricPart, value); // Store full metric with labels as key
                    results.put(metricName, value); // Also store base metric name
                } catch (NumberFormatException e) {
                    LOGGER.debug("Could not parse metric value: {} = {}", metricPart, valuePart);
                }
            }
        }
    }

    private void saveMetricsToFile(BenchmarkMetrics metrics, String benchmarkName) throws IOException {
        String filename = String.format("%s/metrics-%s-%d.json", 
                outputDirectory, benchmarkName, System.currentTimeMillis());
        
        try (FileWriter writer = new FileWriter(filename)) {
            GSON.toJson(metrics, writer);
            LOGGER.info("Metrics saved to: {}", filename);
        }
    }
    
    private void saveJwtValidationMetrics(BenchmarkMetrics metrics) {
        String filename = outputDirectory + "/jwt-validation-metrics.json";
        
        try (FileWriter writer = new FileWriter(filename)) {
            GSON.toJson(metrics, writer);
            LOGGER.info("JWT validation metrics saved to: {}", filename);
        } catch (IOException e) {
            LOGGER.error("Failed to save jwt-validation-metrics.json", e);
        }
    }

    private long getLongValue(Map<String, Double> metrics, String key) {
        return metrics.getOrDefault(key, 0.0).longValue();
    }

    private int getIntValue(Map<String, Double> metrics, String key) {
        return metrics.getOrDefault(key, 0.0).intValue();
    }

    private double getDoubleValue(Map<String, Double> metrics, String key) {
        return metrics.getOrDefault(key, 0.0);
    }
    
    /**
     * Sums all metrics for a given area (heap/nonheap) from Quarkus metrics.
     * Quarkus exports multiple memory regions per area, so we need to sum them.
     */
    private long getHeapMetricSum(Map<String, Double> metrics, String metricName, String area) {
        return metrics.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(metricName + "{area=\"" + area + "\""))
                .mapToLong(entry -> entry.getValue().longValue())
                .sum();
    }
    
    /**
     * Sums all variants of a metric (e.g., HTTP requests across all URIs)
     */
    private long getMetricSum(Map<String, Double> metrics, String metricName) {
        return metrics.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(metricName))
                .mapToLong(entry -> entry.getValue().longValue())
                .sum();
    }
    
    /**
     * Sums metrics by HTTP status pattern (e.g., all 2xx responses)
     */
    private long getMetricSumByStatusPattern(Map<String, Double> metrics, String metricName, String statusPrefix) {
        return metrics.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(metricName) && entry.getKey().contains("status=\"" + statusPrefix))
                .mapToLong(entry -> entry.getValue().longValue())
                .sum();
    }
    
    /**
     * Sums metrics by result type (success/error)
     */
    private long getMetricSumByResult(Map<String, Double> metrics, String metricName, String result) {
        return metrics.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(metricName) && entry.getKey().contains("result=\"" + result + "\""))
                .mapToLong(entry -> entry.getValue().longValue())
                .sum();
    }
}