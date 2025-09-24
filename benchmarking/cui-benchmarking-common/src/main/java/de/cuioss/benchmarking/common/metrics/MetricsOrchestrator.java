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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal orchestrator for metrics processing.
 * Coordinates the download, processing, and export of metrics using the three concrete classes.
 */
public class MetricsOrchestrator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsOrchestrator.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final String metricsURL;
    private final Path downloadsDirectory;
    private final Path targetDirectory;

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
    }

    /**
     * Downloads, processes and exports Quarkus metrics.
     * This is the main entry point for Quarkus integration metrics.
     *
     * @param prefix Prefix for downloaded file naming
     * @throws IOException if I/O operations fail
     */
    public void processQuarkusMetrics(String prefix) throws IOException {
        LOGGER.info("Starting processQuarkusMetrics for prefix: {}", prefix);

        // 1. Download metrics
        MetricsDownloader downloader = new MetricsDownloader(metricsURL, downloadsDirectory);
        Path downloadedFile = downloader.downloadMetrics(prefix);
        LOGGER.info("Downloaded metrics to: {}", downloadedFile);

        // 2. Process metrics
        MetricsFileProcessor processor = new MetricsFileProcessor(downloadsDirectory);
        Map<String, Double> allMetrics = processor.processAllMetricsFiles();
        LOGGER.info("Processed {} total metrics", allMetrics.size());

        // 3. Create gh-pages-ready/data directory and export directly there
        Path ghPagesDataDir = targetDirectory.resolve("gh-pages-ready").resolve("data");
        Files.createDirectories(ghPagesDataDir);
        LOGGER.info("Created gh-pages data directory: {}", ghPagesDataDir);

        // 4. Create structured Quarkus runtime metrics from raw data
        Map<String, Object> structuredMetrics = createQuarkusRuntimeMetricsStructure(allMetrics, Instant.now());

        // 5. Export to JSON directly in the target location
        MetricsJsonExporter exporter = new MetricsJsonExporter(ghPagesDataDir);
        exporter.updateAggregatedMetrics("quarkus-metrics.json", prefix, structuredMetrics);

        Path ghPagesMetricsFile = ghPagesDataDir.resolve("quarkus-metrics.json");
        LOGGER.info("Exported Quarkus metrics directly to: {}", ghPagesMetricsFile);
    }

    private Map<String, Object> createExportData(String benchmarkName, Instant timestamp, Map<String, Double> metrics) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("benchmark", benchmarkName);
        data.put("timestamp", ISO_FORMATTER.format(timestamp.atOffset(ZoneOffset.UTC)));
        data.put("metrics", metrics);
        return data;
    }

    /**
     * Creates structured Quarkus runtime metrics from raw Prometheus metrics.
     * Transforms flat metrics into the new structured format with 4 main nodes.
     *
     * @param allMetrics Raw metrics from Prometheus format
     * @param timestamp Timestamp when metrics were collected
     * @return Structured metrics map ready for JSON export
     */
    private Map<String, Object> createQuarkusRuntimeMetricsStructure(Map<String, Double> allMetrics, Instant timestamp) {
        Map<String, Object> structuredMetrics = new LinkedHashMap<>();

        // Add timestamp
        structuredMetrics.put("timestamp", ISO_FORMATTER.format(timestamp.atOffset(ZoneOffset.UTC)));

        // First node: system metrics
        structuredMetrics.put("system", createSystemMetrics(allMetrics));

        // Second node: http_server_requests metrics
        structuredMetrics.put("http_server_requests", createHttpServerRequestsMetrics(allMetrics));

        // Third node: cui_jwt_validation_success_operations_total
        structuredMetrics.put("cui_jwt_validation_success_operations_total", createJwtValidationSuccessMetrics(allMetrics));

        // Fourth node: cui_jwt_validation_errors
        structuredMetrics.put("cui_jwt_validation_errors", createJwtValidationErrorMetrics(allMetrics));

        return structuredMetrics;
    }

    private Map<String, Object> createSystemMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> systemMetrics = new LinkedHashMap<>();

        // Calculate total heap and non-heap memory used
        long totalHeapUsed = 0;
        long totalNonHeapUsed = 0;
        long totalHeapCommitted = 0;
        long totalHeapMax = 0;
        int liveThreads = 0;
        int daemonThreads = 0;

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("process_cpu_usage")) {
                // Convert to percentage for better readability - this is the Quarkus JVM process CPU
                double cpuPercent = value * 100;
                if (cpuPercent > 0.01) {  // Only show if meaningful
                    systemMetrics.put("quarkus_cpu_usage_percent", formatNumber(cpuPercent));
                }
            } else if (metricName.startsWith("system_cpu_usage")) {
                // Convert to percentage for better readability - this is total system CPU
                double cpuPercent = value * 100;
                if (cpuPercent > 0.01) {  // Only show if meaningful
                    systemMetrics.put("system_cpu_usage_percent", formatNumber(cpuPercent));
                }
            } else if (metricName.startsWith("system_cpu_count")) {
                systemMetrics.put("cpu_cores_available", value.intValue());
            } else if (metricName.startsWith("jvm_threads_peak_threads")) {
                systemMetrics.put("threads_peak", value.intValue());
            } else if (metricName.startsWith("jvm_threads_live_threads")) {
                // Skip - not needed per user request
            } else if (metricName.startsWith("jvm_threads_daemon_threads")) {
                // Skip - not needed per user request
            } else if (metricName.startsWith("jvm_gc_overhead")) {
                // Convert to percentage only if meaningful
                double gcPercent = value * 100;
                if (gcPercent > 0.001) {
                    systemMetrics.put("gc_overhead_percent", formatNumber(gcPercent));
                }
            } else if (metricName.startsWith("jvm_memory_used_bytes")) {
                // Sum up actual memory usage
                if (metricName.contains("area=\"heap\"")) {
                    totalHeapUsed += value.longValue();
                } else if (metricName.contains("area=\"nonheap\"")) {
                    totalNonHeapUsed += value.longValue();
                }
            } else if (metricName.startsWith("jvm_memory_committed_bytes") && metricName.contains("area=\"heap\"")) {
                totalHeapCommitted += value.longValue();
            } else if (metricName.startsWith("jvm_memory_max_bytes") && metricName.contains("area=\"heap\"")) {
                // Only add positive max values
                if (value > 0) {
                    totalHeapMax += value.longValue();
                }
            } else if (metricName.startsWith("process_uptime_seconds")) {
                // Skip uptime - not needed per user request
            }
        }

        // Skip thread counts - not needed per user request

        // Only add memory metrics that are actually meaningful (non-zero)
        if (totalHeapUsed > 0) {
            systemMetrics.put("memory_heap_used_mb", totalHeapUsed / (1024 * 1024));
        }
        if (totalNonHeapUsed > 0) {
            systemMetrics.put("memory_nonheap_used_mb", totalNonHeapUsed / (1024 * 1024));
        }
        if (totalHeapCommitted > 0 && totalHeapCommitted != totalHeapUsed) {
            // Only add committed if it's different from used by more than 10%
            long diff = Math.abs(totalHeapCommitted - totalHeapUsed);
            if (diff > totalHeapUsed * 0.1) {
                systemMetrics.put("memory_heap_committed_mb", totalHeapCommitted / (1024 * 1024));
            }
        }
        if (totalHeapMax > 0) {
            systemMetrics.put("memory_heap_max_mb", totalHeapMax / (1024 * 1024));
        }

        // Calculate total memory used (heap + nonheap) only if both are meaningful
        if (totalHeapUsed > 0 && totalNonHeapUsed > 0) {
            long totalMemoryUsed = totalHeapUsed + totalNonHeapUsed;
            systemMetrics.put("memory_total_used_mb", totalMemoryUsed / (1024 * 1024));
        }

        return systemMetrics;
    }

    private Map<String, Object> createHttpServerRequestsMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> httpMetrics = new LinkedHashMap<>();

        double sumSeconds = 0;
        long count = 0;
        double maxSeconds = 0;

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("http_server_requests_seconds")) {
                if (metricName.contains("_count")) {
                    count = value.longValue();
                    httpMetrics.put("total_requests", count);
                } else if (metricName.contains("_sum")) {
                    sumSeconds = value;
                    // Don't output total_duration_seconds as it's not meaningful to users
                } else if (metricName.contains("_max")) {
                    maxSeconds = value;
                    // Convert max to milliseconds for better readability
                    if (maxSeconds > 0 && maxSeconds < 1) {
                        httpMetrics.put("max_duration_ms", formatNumber(maxSeconds * 1000));
                    } else if (maxSeconds > 0) {
                        httpMetrics.put("max_duration_seconds", formatNumber(maxSeconds));
                    }
                }
            } else if (metricName.startsWith("http_server_active_requests")) {
                // Skip active_requests - not needed per user request
            }
        }

        // Calculate meaningful average and percentiles
        if (count > 0 && sumSeconds > 0) {
            double avgSeconds = sumSeconds / count;

            // Convert to appropriate unit based on magnitude
            if (avgSeconds < 0.001) {
                // Use microseconds for very small values
                httpMetrics.put("average_duration_us", formatNumber(avgSeconds * 1_000_000));
                httpMetrics.put("requests_per_second", formatNumber(count / sumSeconds));
            } else if (avgSeconds < 1) {
                // Use milliseconds for sub-second values
                httpMetrics.put("average_duration_ms", formatNumber(avgSeconds * 1000));
                httpMetrics.put("requests_per_second", formatNumber(count / sumSeconds));
            } else {
                // Use seconds for larger values
                httpMetrics.put("average_duration_seconds", formatNumber(avgSeconds));
            }
        }

        return httpMetrics;
    }

    private Map<String, Object> createJwtValidationSuccessMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> successMetrics = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("cui_jwt_validation_success_operations_total")) {
                String eventType = extractTag(metricName, "event_type");
                if (eventType != null && value != null && value > 0) {
                    successMetrics.put(eventType, value.longValue());
                }
            }
        }

        return successMetrics;
    }

    private Map<String, Object> createJwtValidationErrorMetrics(Map<String, Double> allMetrics) {
        Map<String, Object> errorMetrics = new LinkedHashMap<>();
        Map<String, Map<String, Long>> errorsByCategory = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            Double value = entry.getValue();

            if (metricName.startsWith("cui_jwt_validation_errors_total")) {
                String category = extractTag(metricName, "category");
                String eventType = extractTag(metricName, "event_type");

                if (category != null && eventType != null && value != null) {
                    Map<String, Long> categoryErrors = errorsByCategory.computeIfAbsent(category, k -> new LinkedHashMap<>());
                    categoryErrors.put(eventType, value.longValue());
                }
            }
        }

        // Transform into the required structure: ordered by category, then by event_type
        for (Map.Entry<String, Map<String, Long>> categoryEntry : errorsByCategory.entrySet()) {
            String category = categoryEntry.getKey();
            Map<String, Long> events = categoryEntry.getValue();

            for (Map.Entry<String, Long> eventEntry : events.entrySet()) {
                String eventType = eventEntry.getKey();
                Long count = eventEntry.getValue();

                Map<String, Object> errorEntry = new LinkedHashMap<>();
                errorEntry.put("category", category);
                errorEntry.put("event_type", eventType);
                errorEntry.put("count", count);

                // Use category_eventType as key for ordering
                String key = category + "_" + eventType;
                errorMetrics.put(key, errorEntry);
            }
        }

        return errorMetrics;
    }

    private String extractTag(String metricName, String tagName) {
        String pattern = tagName + "=\"([^\"]+)\"";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(metricName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Object formatNumber(double value) {
        if (value < 10) {
            DecimalFormat df = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
            return Double.parseDouble(df.format(value));
        } else {
            return Long.valueOf(Math.round(value));
        }
    }
}