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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-processor for Quarkus metrics that extracts CPU and RAM usage data
 * from Prometheus format metrics files and generates quarkus-metrics.json.
 * 
 * This processor analyzes metrics-download directory containing timestamped
 * Quarkus metrics files and extracts system resource usage information.
 *
 * @since 1.0
 */
public class QuarkusMetricsPostProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(QuarkusMetricsPostProcessor.class);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
            .create();

    // Patterns for extracting metrics from Prometheus format
    private static final Pattern CPU_USAGE_PATTERN = Pattern.compile("^system_cpu_usage\\s+([0-9.]+)$");
    private static final Pattern PROCESS_CPU_PATTERN = Pattern.compile("^process_cpu_usage\\s+([0-9.]+)$");
    private static final Pattern CPU_COUNT_PATTERN = Pattern.compile("^system_cpu_count\\s+([0-9.]+)$");
    private static final Pattern LOAD_AVG_PATTERN = Pattern.compile("^system_load_average_1m\\s+([0-9.]+)$");
    private static final Pattern JVM_MEMORY_USED_PATTERN = Pattern.compile("^jvm_memory_used_bytes\\{area=\"([^\"]+)\",id=\"([^\"]+)\"\\}\\s+([0-9.]+)$");
    private static final Pattern JVM_MEMORY_COMMITTED_PATTERN = Pattern.compile("^jvm_memory_committed_bytes\\{area=\"([^\"]+)\",id=\"([^\"]+)\"\\}\\s+([0-9.]+)$");
    private static final Pattern JVM_MEMORY_MAX_PATTERN = Pattern.compile("^jvm_memory_max_bytes\\{area=\"([^\"]+)\",id=\"([^\"]+)\"\\}\\s+([0-9.]+)$");

    private final String metricsDownloadDirectory;
    private final String outputDirectory;

    public QuarkusMetricsPostProcessor(String metricsDownloadDirectory, String outputDirectory) {
        this.metricsDownloadDirectory = metricsDownloadDirectory;
        this.outputDirectory = outputDirectory;
        File dir = new File(outputDirectory);
        dir.mkdirs();
        LOGGER.info("QuarkusMetricsPostProcessor initialized with metrics directory: {} and output directory: {}",
                metricsDownloadDirectory, dir.getAbsolutePath());
    }

    /**
     * Parse Quarkus metrics files and generate quarkus-metrics.json with CPU and RAM usage data
     * 
     * @param timestamp Timestamp for the metrics output
     * @throws IOException if file operations fail
     */
    public void parseAndExportQuarkusMetrics(Instant timestamp) throws IOException {
        LOGGER.info("Parsing Quarkus metrics from directory: {}", metricsDownloadDirectory);

        File metricsDir = new File(metricsDownloadDirectory);
        if (!metricsDir.exists() || !metricsDir.isDirectory()) {
            throw new IOException("Metrics download directory not found: " + metricsDownloadDirectory);
        }

        // Find all metrics files (jwt-health-metrics.txt, jwt-validation-metrics.txt, finalcumulativemetrics-*.txt)
        File[] metricsFiles = metricsDir.listFiles((dir, name) ->
                name.endsWith("-metrics.txt") && (name.contains("jwt-health") ||
                        name.contains("jwt-validation") || name.contains("finalcumulativemetrics")));

        if (metricsFiles == null || metricsFiles.length == 0) {
            throw new IOException("No Quarkus metrics files found in: " + metricsDownloadDirectory);
        }

        LOGGER.info("Found {} metrics files to process", metricsFiles.length);

        // Process all metrics files and aggregate data
        QuarkusResourceMetrics aggregatedMetrics = new QuarkusResourceMetrics();

        for (File metricsFile : metricsFiles) {
            LOGGER.debug("Processing metrics file: {}", metricsFile.getName());
            processMetricsFile(metricsFile, aggregatedMetrics);
            aggregatedMetrics.incrementFilesProcessed();
        }

        // Generate output file
        generateQuarkusMetricsFile(aggregatedMetrics, timestamp);

        LOGGER.info("Successfully exported Quarkus metrics from {} files", metricsFiles.length);
    }

    private void processMetricsFile(File metricsFile, QuarkusResourceMetrics aggregatedMetrics) throws IOException {
        List<String> lines = Files.readAllLines(metricsFile.toPath());

        for (String line : lines) {
            line = line.trim();

            // Skip comments and empty lines
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }

            // Extract CPU metrics
            Matcher cpuMatcher = CPU_USAGE_PATTERN.matcher(line);
            if (cpuMatcher.matches()) {
                double cpuUsage = Double.parseDouble(cpuMatcher.group(1));
                aggregatedMetrics.addCpuUsage(cpuUsage);
                continue;
            }

            Matcher processCpuMatcher = PROCESS_CPU_PATTERN.matcher(line);
            if (processCpuMatcher.matches()) {
                double processCpuUsage = Double.parseDouble(processCpuMatcher.group(1));
                aggregatedMetrics.addProcessCpuUsage(processCpuUsage);
                continue;
            }

            Matcher cpuCountMatcher = CPU_COUNT_PATTERN.matcher(line);
            if (cpuCountMatcher.matches()) {
                int cpuCount = (int) Double.parseDouble(cpuCountMatcher.group(1));
                aggregatedMetrics.setCpuCount(cpuCount);
                continue;
            }

            Matcher loadAvgMatcher = LOAD_AVG_PATTERN.matcher(line);
            if (loadAvgMatcher.matches()) {
                double loadAvg = Double.parseDouble(loadAvgMatcher.group(1));
                aggregatedMetrics.addLoadAverage(loadAvg);
                continue;
            }

            // Extract memory metrics
            Matcher memUsedMatcher = JVM_MEMORY_USED_PATTERN.matcher(line);
            if (memUsedMatcher.matches()) {
                String area = memUsedMatcher.group(1);
                String id = memUsedMatcher.group(2);
                long memoryUsed = (long) Double.parseDouble(memUsedMatcher.group(3));
                aggregatedMetrics.addMemoryUsed(area, id, memoryUsed);
                continue;
            }

            Matcher memCommittedMatcher = JVM_MEMORY_COMMITTED_PATTERN.matcher(line);
            if (memCommittedMatcher.matches()) {
                String area = memCommittedMatcher.group(1);
                String id = memCommittedMatcher.group(2);
                long memoryCommitted = (long) Double.parseDouble(memCommittedMatcher.group(3));
                aggregatedMetrics.addMemoryCommitted(area, id, memoryCommitted);
                continue;
            }

            Matcher memMaxMatcher = JVM_MEMORY_MAX_PATTERN.matcher(line);
            if (memMaxMatcher.matches()) {
                String area = memMaxMatcher.group(1);
                String id = memMaxMatcher.group(2);
                long memoryMax = (long) Double.parseDouble(memMaxMatcher.group(3));
                // Only add if not -1 (unlimited)
                if (memoryMax > 0) {
                    aggregatedMetrics.addMemoryMax(area, id, memoryMax);
                }
            }
        }
    }

    private void generateQuarkusMetricsFile(QuarkusResourceMetrics metrics, Instant timestamp) throws IOException {
        Map<String, Object> output = new LinkedHashMap<>();

        // CPU metrics
        Map<String, Object> cpuMetrics = new LinkedHashMap<>();
        cpuMetrics.put("system_cpu_usage_avg", formatPercentage(metrics.getAverageCpuUsage()));
        cpuMetrics.put("system_cpu_usage_max", formatPercentage(metrics.getMaxCpuUsage()));
        cpuMetrics.put("process_cpu_usage_avg", formatPercentage(metrics.getAverageProcessCpuUsage()));
        cpuMetrics.put("process_cpu_usage_max", formatPercentage(metrics.getMaxProcessCpuUsage()));
        cpuMetrics.put("cpu_count", metrics.getCpuCount());
        cpuMetrics.put("load_average_1m_avg", formatNumber(metrics.getAverageLoadAverage()));
        cpuMetrics.put("load_average_1m_max", formatNumber(metrics.getMaxLoadAverage()));
        output.put("cpu", cpuMetrics);

        // Memory metrics
        Map<String, Object> memoryMetrics = new LinkedHashMap<>();

        // Heap memory
        Map<String, Object> heapMetrics = new LinkedHashMap<>();
        heapMetrics.put("used_bytes", metrics.getTotalHeapUsed());
        heapMetrics.put("committed_bytes", metrics.getTotalHeapCommitted());
        long maxHeap = metrics.getTotalHeapMax();
        if (maxHeap > 0) {
            heapMetrics.put("max_bytes", maxHeap);
            heapMetrics.put("usage_percentage", formatPercentage((double) metrics.getTotalHeapUsed() / maxHeap));
        }
        memoryMetrics.put("heap", heapMetrics);

        // Non-heap memory
        Map<String, Object> nonHeapMetrics = new LinkedHashMap<>();
        nonHeapMetrics.put("used_bytes", metrics.getTotalNonHeapUsed());
        nonHeapMetrics.put("committed_bytes", metrics.getTotalNonHeapCommitted());
        long maxNonHeap = metrics.getTotalNonHeapMax();
        if (maxNonHeap > 0) {
            nonHeapMetrics.put("max_bytes", maxNonHeap);
            nonHeapMetrics.put("usage_percentage", formatPercentage((double) metrics.getTotalNonHeapUsed() / maxNonHeap));
        }
        memoryMetrics.put("nonheap", nonHeapMetrics);

        output.put("memory", memoryMetrics);

        // Metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("timestamp", timestamp.toString());
        metadata.put("files_processed", metrics.getFilesProcessed());
        metadata.put("source", "Quarkus metrics - Prometheus format");
        output.put("metadata", metadata);

        String outputFilePath = outputDirectory + "/quarkus-metrics.json";
        File outputFile = new File(outputFilePath);

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(output, writer);
            writer.flush();
            LOGGER.info("Generated quarkus-metrics.json at: {}", outputFile.getAbsolutePath());
        }
    }

    /**
     * Format percentage values (0-1) as percentages (0-100%)
     */
    private Object formatPercentage(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        double percentage = value * 100.0;
        if (percentage < 10) {
            return Math.round(percentage * 10.0) / 10.0;
        } else {
            return (long) Math.round(percentage);
        }
    }

    /**
     * Format number according to rules: 1 decimal for <10, no decimal for >=10
     */
    private Object formatNumber(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 10) {
            return Math.round(value * 10.0) / 10.0;
        } else {
            return (long) Math.round(value);
        }
    }

    /**
     * Class to accumulate resource metrics across multiple files
     */
    public static class QuarkusResourceMetrics {
        // CPU metrics
        private double totalCpuUsage = 0.0;
        private double maxCpuUsage = 0.0;
        private int cpuUsageCount = 0;

        private double totalProcessCpuUsage = 0.0;
        private double maxProcessCpuUsage = 0.0;
        private int processCpuUsageCount = 0;

        private int cpuCount = 0;

        private double totalLoadAverage = 0.0;
        private double maxLoadAverage = 0.0;
        private int loadAverageCount = 0;

        // Memory metrics - organized by area and id
        private final Map<String, Long> memoryUsed = new LinkedHashMap<>();
        private final Map<String, Long> memoryCommitted = new LinkedHashMap<>();
        private final Map<String, Long> memoryMax = new LinkedHashMap<>();

        private int filesProcessed = 0;

        // CPU methods
        public void addCpuUsage(double cpuUsage) {
            totalCpuUsage += cpuUsage;
            maxCpuUsage = Math.max(maxCpuUsage, cpuUsage);
            cpuUsageCount++;
        }

        public void addProcessCpuUsage(double processCpuUsage) {
            totalProcessCpuUsage += processCpuUsage;
            maxProcessCpuUsage = Math.max(maxProcessCpuUsage, processCpuUsage);
            processCpuUsageCount++;
        }

        public void setCpuCount(int cpuCount) {
            this.cpuCount = cpuCount;
        }

        public void addLoadAverage(double loadAverage) {
            totalLoadAverage += loadAverage;
            maxLoadAverage = Math.max(maxLoadAverage, loadAverage);
            loadAverageCount++;
        }

        // Memory methods
        public void addMemoryUsed(String area, String id, long memoryUsed) {
            String key = area + ":" + id;
            this.memoryUsed.put(key, memoryUsed);
        }

        public void addMemoryCommitted(String area, String id, long memoryCommitted) {
            String key = area + ":" + id;
            this.memoryCommitted.put(key, memoryCommitted);
        }

        public void addMemoryMax(String area, String id, long memoryMax) {
            String key = area + ":" + id;
            this.memoryMax.put(key, memoryMax);
        }

        public void incrementFilesProcessed() {
            filesProcessed++;
        }

        // Getter methods
        public double getAverageCpuUsage() {
            return cpuUsageCount > 0 ? totalCpuUsage / cpuUsageCount : 0.0;
        }

        public double getMaxCpuUsage() {
            return maxCpuUsage;
        }

        public double getAverageProcessCpuUsage() {
            return processCpuUsageCount > 0 ? totalProcessCpuUsage / processCpuUsageCount : 0.0;
        }

        public double getMaxProcessCpuUsage() {
            return maxProcessCpuUsage;
        }

        public int getCpuCount() {
            return cpuCount;
        }

        public double getAverageLoadAverage() {
            return loadAverageCount > 0 ? totalLoadAverage / loadAverageCount : 0.0;
        }

        public double getMaxLoadAverage() {
            return maxLoadAverage;
        }

        public long getTotalHeapUsed() {
            return memoryUsed.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("heap:"))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
        }

        public long getTotalHeapCommitted() {
            return memoryCommitted.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("heap:"))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
        }

        public long getTotalHeapMax() {
            return memoryMax.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("heap:"))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
        }

        public long getTotalNonHeapUsed() {
            return memoryUsed.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("nonheap:"))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
        }

        public long getTotalNonHeapCommitted() {
            return memoryCommitted.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("nonheap:"))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
        }

        public long getTotalNonHeapMax() {
            return memoryMax.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("nonheap:"))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
        }

        public int getFilesProcessed() {
            return filesProcessed;
        }
    }

    /**
     * Convenience method to parse and export using default directory locations
     * Automatically detects and processes benchmark context directories
     */
    public static void parseAndExport(String baseDirectory) throws IOException {
        String metricsDownloadBaseDir = baseDirectory + "/metrics-download";
        File baseDir = new File(metricsDownloadBaseDir);

        if (!baseDir.exists()) {
            throw new IOException("Metrics download directory not found: " + metricsDownloadBaseDir);
        }

        // Look for context directories (legacy format with numbers)
        File[] contextDirs = baseDir.listFiles(file ->
                file.isDirectory() && file.getName().matches("\\d+-.*"));

        if (contextDirs != null && contextDirs.length > 0) {
            // Process the most recent directory
            File latestDir = Arrays.stream(contextDirs)
                    .max((a, b) -> {
                        int numA = Integer.parseInt(a.getName().split("-")[0]);
                        int numB = Integer.parseInt(b.getName().split("-")[0]);
                        return Integer.compare(numA, numB);
                    })
                    .orElse(contextDirs[0]);

            LOGGER.info("Processing metrics directory: {}", latestDir.getName());
            QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(
                    latestDir.getAbsolutePath(), baseDirectory);
            processor.parseAndExportQuarkusMetrics(Instant.now());
        } else {
            // Fallback to old flat directory structure
            LOGGER.info("No legacy directories found, using flat metrics-download structure");
            QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(
                    metricsDownloadBaseDir, baseDirectory);
            processor.parseAndExportQuarkusMetrics(Instant.now());
        }
    }
}