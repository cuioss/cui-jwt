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
package de.cuioss.benchmarking.common.metrics.pipeline.processors;

import de.cuioss.benchmarking.common.metrics.pipeline.MetricsContext;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessingException;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessor;
import de.cuioss.tools.logging.CuiLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processor that extracts Quarkus metrics from Prometheus format files.
 * This processor handles parsing of CPU, memory, and system metrics
 * from Quarkus application metrics files.
 *
 * @since 1.0
 */
public class QuarkusMetricsProcessor implements MetricsProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(QuarkusMetricsProcessor.class);

    // Patterns for extracting metrics from Prometheus format
    private static final Pattern CPU_USAGE_PATTERN = Pattern.compile("^system_cpu_usage\\s+([0-9.]+)$");
    private static final Pattern PROCESS_CPU_PATTERN = Pattern.compile("^process_cpu_usage\\s+([0-9.]+)$");
    private static final Pattern CPU_COUNT_PATTERN = Pattern.compile("^system_cpu_count\\s+([0-9.]+)$");
    private static final Pattern LOAD_AVG_PATTERN = Pattern.compile("^system_load_average_1m\\s+([0-9.]+)$");
    private static final Pattern JVM_MEMORY_USED_PATTERN =
            Pattern.compile("^jvm_memory_used_bytes\\{area=\"([^\"]+)\",id=\"([^\"]+)\"\\}\\s+([0-9.]+)$");
    private static final Pattern JVM_MEMORY_COMMITTED_PATTERN =
            Pattern.compile("^jvm_memory_committed_bytes\\{area=\"([^\"]+)\",id=\"([^\"]+)\"\\}\\s+([0-9.]+)$");
    private static final Pattern JVM_MEMORY_MAX_PATTERN =
            Pattern.compile("^jvm_memory_max_bytes\\{area=\"([^\"]+)\",id=\"([^\"]+)\"\\}\\s+([0-9.]+)$");

    private static final double BYTES_TO_MB = 1024.0 * 1024.0;

    /**
     * Directory containing metrics files
     */
    private final String metricsDirectory;

    /**
     * Default constructor (requires metrics directory to be set in context)
     */
    public QuarkusMetricsProcessor() {
        this(null);
    }

    /**
     * Constructor with metrics directory
     */
    public QuarkusMetricsProcessor(String metricsDirectory) {
        this.metricsDirectory = metricsDirectory;
    }

    @Override public MetricsContext process(MetricsContext context) throws MetricsProcessingException {
        LOGGER.debug("Processing Quarkus metrics from source: {}", context.getSource());

        // Determine metrics directory
        String dir = metricsDirectory;
        if (dir == null) {
            dir = context.getConfiguration("metricsDirectory", String.class);
        }
        if (dir == null) {
            throw new MetricsProcessingException(getName(), "Metrics directory not specified");
        }

        File metricsDir = new File(dir);
        if (!metricsDir.exists() || !metricsDir.isDirectory()) {
            throw new MetricsProcessingException(getName(), "Metrics directory not found: " + dir);
        }

        // Find metrics files
        File[] metricsFiles = metricsDir.listFiles((d, name) ->
                name.endsWith("-metrics.txt") && (name.contains("jwt-health") ||
                        name.contains("jwt-validation") || name.contains("finalcumulativemetrics")));

        if (metricsFiles == null || metricsFiles.length == 0) {
            LOGGER.warn("No Quarkus metrics files found in: {}", dir);
            return context;
        }

        // Process each metrics file
        for (File metricsFile : metricsFiles) {
            try {
                processMetricsFile(metricsFile, context);
            } catch (IOException e) {
                throw new MetricsProcessingException(getName(),
                        "Failed to process metrics file: " + metricsFile.getName(), e);
            }
        }

        LOGGER.debug("Processed {} Quarkus metrics files", metricsFiles.length);
        return context;
    }

    private void processMetricsFile(File file, MetricsContext context) throws IOException {
        LOGGER.debug("Processing metrics file: {}", file.getName());

        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n");

        QuarkusMetrics metrics = new QuarkusMetrics();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Parse CPU metrics
            Matcher matcher = CPU_USAGE_PATTERN.matcher(line);
            if (matcher.matches()) {
                metrics.systemCpuUsage = Double.parseDouble(matcher.group(1));
                continue;
            }

            matcher = PROCESS_CPU_PATTERN.matcher(line);
            if (matcher.matches()) {
                metrics.processCpuUsage = Double.parseDouble(matcher.group(1));
                continue;
            }

            matcher = CPU_COUNT_PATTERN.matcher(line);
            if (matcher.matches()) {
                metrics.cpuCount = (int) Double.parseDouble(matcher.group(1));
                continue;
            }

            matcher = LOAD_AVG_PATTERN.matcher(line);
            if (matcher.matches()) {
                metrics.loadAverage = Double.parseDouble(matcher.group(1));
                continue;
            }

            // Parse memory metrics
            matcher = JVM_MEMORY_USED_PATTERN.matcher(line);
            if (matcher.matches()) {
                String area = matcher.group(1);
                double bytes = Double.parseDouble(matcher.group(3));
                if ("heap".equals(area)) {
                    metrics.heapUsedBytes += bytes;
                } else if ("nonheap".equals(area)) {
                    metrics.nonHeapUsedBytes += bytes;
                }
                continue;
            }

            matcher = JVM_MEMORY_COMMITTED_PATTERN.matcher(line);
            if (matcher.matches()) {
                String area = matcher.group(1);
                double bytes = Double.parseDouble(matcher.group(3));
                if ("heap".equals(area)) {
                    metrics.heapCommittedBytes += bytes;
                } else if ("nonheap".equals(area)) {
                    metrics.nonHeapCommittedBytes += bytes;
                }
                continue;
            }

            matcher = JVM_MEMORY_MAX_PATTERN.matcher(line);
            if (matcher.matches()) {
                String area = matcher.group(1);
                double bytes = Double.parseDouble(matcher.group(3));
                if ("heap".equals(area) && bytes > 0) {
                    metrics.heapMaxBytes = Math.max(metrics.heapMaxBytes, bytes);
                }
            }
        }

        // Add metrics to context
        String prefix = extractMetricPrefix(file.getName());
        addMetricsToContext(context, metrics, prefix);
    }

    private String extractMetricPrefix(String filename) {
        if (filename.contains("jwt-validation")) {
            return "quarkus_jwt_validation_";
        } else if (filename.contains("jwt-health")) {
            return "quarkus_health_";
        } else if (filename.contains("finalcumulativemetrics")) {
            return "quarkus_cumulative_";
        }
        return "quarkus_";
    }

    private void addMetricsToContext(MetricsContext context, QuarkusMetrics metrics, String prefix) {
        // CPU metrics
        if (metrics.systemCpuUsage >= 0) {
            context.addMetric(prefix + "system_cpu_usage", formatPercent(metrics.systemCpuUsage));
        }
        if (metrics.processCpuUsage >= 0) {
            context.addMetric(prefix + "process_cpu_usage", formatPercent(metrics.processCpuUsage));
        }
        if (metrics.cpuCount > 0) {
            context.addMetric(prefix + "cpu_count", metrics.cpuCount);
        }
        if (metrics.loadAverage >= 0) {
            context.addMetric(prefix + "load_average_1m", formatNumber(metrics.loadAverage));
        }

        // Memory metrics in MB
        if (metrics.heapUsedBytes > 0) {
            context.addMetric(prefix + "heap_used_mb", formatNumber(metrics.heapUsedBytes / BYTES_TO_MB));
        }
        if (metrics.heapCommittedBytes > 0) {
            context.addMetric(prefix + "heap_committed_mb", formatNumber(metrics.heapCommittedBytes / BYTES_TO_MB));
        }
        if (metrics.heapMaxBytes > 0) {
            context.addMetric(prefix + "heap_max_mb", formatNumber(metrics.heapMaxBytes / BYTES_TO_MB));
        }
        if (metrics.nonHeapUsedBytes > 0) {
            context.addMetric(prefix + "nonheap_used_mb", formatNumber(metrics.nonHeapUsedBytes / BYTES_TO_MB));
        }
        if (metrics.nonHeapCommittedBytes > 0) {
            context.addMetric(prefix + "nonheap_committed_mb", formatNumber(metrics.nonHeapCommittedBytes / BYTES_TO_MB));
        }

        // Calculate total memory
        double totalUsedMB = (metrics.heapUsedBytes + metrics.nonHeapUsedBytes) / BYTES_TO_MB;
        double totalCommittedMB = (metrics.heapCommittedBytes + metrics.nonHeapCommittedBytes) / BYTES_TO_MB;

        if (totalUsedMB > 0) {
            context.addMetric(prefix + "total_memory_used_mb", formatNumber(totalUsedMB));
        }
        if (totalCommittedMB > 0) {
            context.addMetric(prefix + "total_memory_committed_mb", formatNumber(totalCommittedMB));
        }

        // Calculate heap utilization percentage
        if (metrics.heapMaxBytes > 0 && metrics.heapUsedBytes > 0) {
            double heapUtilization = (metrics.heapUsedBytes / metrics.heapMaxBytes) * 100;
            context.addMetric(prefix + "heap_utilization_percent", formatPercent(heapUtilization));
        }
    }

    private double formatNumber(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double formatPercent(double value) {
        return Math.round(value * 10000.0) / 100.0;
    }

    @Override public String getName() {
        return "QuarkusMetricsProcessor";
    }

    /**
     * Internal class to hold parsed Quarkus metrics
     */
    private static class QuarkusMetrics {
        double systemCpuUsage = -1;
        double processCpuUsage = -1;
        int cpuCount = 0;
        double loadAverage = -1;
        double heapUsedBytes = 0;
        double heapCommittedBytes = 0;
        double heapMaxBytes = 0;
        double nonHeapUsedBytes = 0;
        double nonHeapCommittedBytes = 0;
    }
}