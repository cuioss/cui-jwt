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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes metrics files from a downloads directory.
 * Parses Prometheus format metrics and extracts relevant data.
 * This class is designed to be testable with clear separation of concerns.
 *
 * @since 1.0
 */
public class MetricsFileProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsFileProcessor.class);

    private final Path downloadsDirectory;

    /**
     * Creates a new metrics file processor.
     *
     * @param downloadsDirectory Directory containing metrics files to process
     */
    public MetricsFileProcessor(Path downloadsDirectory) {
        this.downloadsDirectory = downloadsDirectory;
    }

    /**
     * Processes all metrics files in the downloads directory and returns parsed metrics.
     *
     * @return Map of metric names to values
     * @throws IOException if file reading fails
     */
    public Map<String, Double> processAllMetricsFiles() throws IOException {
        Map<String, Double> allMetrics = new HashMap<>();

        if (!Files.exists(downloadsDirectory) || !Files.isDirectory(downloadsDirectory)) {
            LOGGER.warn("Downloads directory does not exist or is not a directory: {}", downloadsDirectory);
            return allMetrics;
        }

        try (var stream = Files.list(downloadsDirectory)) {
            List<Path> metricsFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .toList();

            LOGGER.debug("Found {} metrics files to process", metricsFiles.size());

            for (Path metricsFile : metricsFiles) {
                Map<String, Double> fileMetrics = processMetricsFile(metricsFile);
                allMetrics.putAll(fileMetrics);
                LOGGER.debug("Processed {} metrics from file: {}", fileMetrics.size(), metricsFile.getFileName());
            }
        }

        LOGGER.debug("Total metrics processed: {}", allMetrics.size());
        return allMetrics;
    }

    /**
     * Processes a specific metrics file.
     *
     * @param fileName Name of the file in the downloads directory
     * @return Map of metric names to values
     * @throws IOException if file reading fails
     */
    public Map<String, Double> processMetricsFile(String fileName) throws IOException {
        Path filePath = downloadsDirectory.resolve(fileName);
        return processMetricsFile(filePath);
    }

    /**
     * Processes a specific metrics file by path.
     *
     * @param filePath Path to the metrics file
     * @return Map of metric names to values
     * @throws IOException if file reading fails
     */
    public Map<String, Double> processMetricsFile(Path filePath) throws IOException {
        Map<String, Double> metrics = new HashMap<>();

        if (!Files.exists(filePath)) {
            LOGGER.warn("Metrics file does not exist: {}", filePath);
            return metrics;
        }

        List<String> lines = Files.readAllLines(filePath);
        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }

            parsePrometheusMetricLine(line, metrics);
        }

        return metrics;
    }

    /**
     * Parses a single Prometheus format metric line.
     * Format: metric_name{labels} value [timestamp]
     *
     * @param line The metric line to parse
     * @param metrics Map to store the parsed metric
     */
    private void parsePrometheusMetricLine(String line, Map<String, Double> metrics) {
        int spaceIndex = line.lastIndexOf(' ');
        if (spaceIndex <= 0) {
            return;
        }

        String metricPart = line.substring(0, spaceIndex);
        String valuePart = line.substring(spaceIndex + 1);

        try {
            double value = Double.parseDouble(valuePart);
            metrics.put(metricPart, value);
        } catch (NumberFormatException e) {
            LOGGER.debug("Could not parse metric value: {} = {}", metricPart, valuePart);
        }
    }

    /**
     * Extracts JWT validation specific metrics from a metrics map.
     *
     * @param allMetrics All parsed metrics
     * @return Map containing only JWT validation related metrics
     */
    public Map<String, Double> extractJwtValidationMetrics(Map<String, Double> allMetrics) {
        Map<String, Double> jwtMetrics = new HashMap<>();

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            if (isJwtValidationMetric(metricName)) {
                jwtMetrics.put(metricName, entry.getValue());
            }
        }

        LOGGER.debug("Extracted {} JWT validation metrics from {} total metrics",
                jwtMetrics.size(), allMetrics.size());
        return jwtMetrics;
    }

    /**
     * Checks if a metric name is related to JWT validation.
     *
     * @param metricName The metric name to check
     * @return true if the metric is JWT validation related
     */
    private boolean isJwtValidationMetric(String metricName) {
        return metricName.contains("cui_jwt_validation") ||
                metricName.contains("cui_jwt_bearer_token") ||
                metricName.contains("jwt_bearer_token") ||
                metricName.contains("bearer_token_validation");
    }

    /**
     * Extracts resource metrics (CPU, memory) from a metrics map.
     *
     * @param allMetrics All parsed metrics
     * @return Map containing only resource metrics
     */
    public Map<String, Double> extractResourceMetrics(Map<String, Double> allMetrics) {
        Map<String, Double> resourceMetrics = new HashMap<>();

        for (Map.Entry<String, Double> entry : allMetrics.entrySet()) {
            String metricName = entry.getKey();
            if (isResourceMetric(metricName)) {
                resourceMetrics.put(metricName, entry.getValue());
            }
        }

        LOGGER.debug("Extracted {} resource metrics from {} total metrics",
                resourceMetrics.size(), allMetrics.size());
        return resourceMetrics;
    }

    /**
     * Checks if a metric name is related to system resources.
     *
     * @param metricName The metric name to check
     * @return true if the metric is resource related
     */
    private boolean isResourceMetric(String metricName) {
        return metricName.startsWith("system_cpu_") ||
                metricName.startsWith("process_cpu_") ||
                metricName.startsWith("jvm_memory_") ||
                metricName.startsWith("system_load_average");
    }

    /**
     * Extracts a tag value from a Prometheus metric name.
     * Example: metric_name{category="INVALID_STRUCTURE",event_type="FAILED_TO_DECODE_HEADER"}
     *
     * @param metricName The metric name with tags
     * @param tagName The tag name to extract
     * @return The tag value or null if not found
     */
    public String extractTag(String metricName, String tagName) {
        String pattern = tagName + "=\"([^\"]+)\"";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(metricName);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Checks if the downloads directory exists and contains metrics files.
     *
     * @return true if the directory exists and contains .txt files
     */
    public boolean hasMetricsFiles() {
        if (!Files.exists(downloadsDirectory) || !Files.isDirectory(downloadsDirectory)) {
            return false;
        }

        try (var stream = Files.list(downloadsDirectory)) {
            return stream.anyMatch(path -> Files.isRegularFile(path) && path.toString().endsWith(".txt"));
        } catch (IOException e) {
            LOGGER.warn("Error checking for metrics files in directory: {}", downloadsDirectory, e);
            return false;
        }
    }
}