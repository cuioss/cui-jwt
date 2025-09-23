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
package de.cuioss.jwt.wrk.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.benchmarking.common.config.IntegrationConfiguration;
import de.cuioss.benchmarking.common.metrics.QuarkusMetricsFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Main class for processing WRK benchmark results and generating comprehensive reports.
 * <p>
 * This class processes WRK output files and combines them with Quarkus metrics
 * to generate unified benchmark reports in JSON format compatible with the
 * existing benchmark reporting infrastructure.
 * <p>
 * Input: Raw WRK output text files and detailed JSON results from Lua scripts
 * Output: Processed JSON reports with latency, throughput, and system metrics
 */
public class WrkIntegrationRunner {

    private static final Logger LOGGER = Logger.getLogger(WrkIntegrationRunner.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Main entry point for WRK result processing.
     *
     * @param args Command line arguments:
     *             args[0] - Input WRK raw results file path
     *             args[1] - Output JSON results file path
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            LOGGER.severe("Usage: WrkIntegrationRunner <wrk-results-file> <output-json-file>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        try {
            new WrkIntegrationRunner().processWrkResults(inputFile, outputFile);
        } catch (Exception e) {
            LOGGER.severe("Failed to process WRK results: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Process WRK results and generate comprehensive benchmark report.
     */
    public void processWrkResults(String inputFile, String outputFile) throws IOException {
        LOGGER.info("Starting WRK results processing: " + inputFile + " -> " + outputFile);

        // Load integration configuration
        IntegrationConfiguration integrationConfig = IntegrationConfiguration.fromProperties();

        // Parse WRK raw output
        WrkResults wrkResults = parseWrkOutput(inputFile);
        LOGGER.info("Parsed WRK results: " + wrkResults.getRequestsPerSecond() + " req/s");

        // Fetch Quarkus metrics
        Map<String, Double> quarkusMetrics = fetchQuarkusMetrics(integrationConfig);

        // Combine results into comprehensive report
        BenchmarkReport report = createBenchmarkReport(wrkResults, quarkusMetrics, integrationConfig);

        // Write JSON report
        writeJsonReport(report, outputFile);

        LOGGER.info("WRK results processing completed successfully");
    }

    /**
     * Parse WRK raw text output into structured data.
     */
    public WrkResults parseWrkOutput(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("WRK results file not found: " + filePath);
        }

        String content = Files.readString(path);
        LOGGER.fine("Parsing WRK output: " + content.length() + " characters");

        // Parse WRK output format
        // Example: "Requests/sec:   20532.45"
        WrkResults results = new WrkResults();

        for (String line : content.split("\n")) {
            line = line.trim();

            if (line.startsWith("Requests/sec:")) {
                results.setRequestsPerSecond(parseDouble(line, "Requests/sec:"));
            } else if (line.startsWith("Latency")) {
                double latency = parseLatency(line);
                // Only set latency if we got a valid value and haven't set it before
                if (latency > 0.0 && results.getLatencyAvg() == 0.0) {
                    results.setLatencyAvg(latency);
                }
            } else if (line.contains("requests in")) {
                parseRequestsAndDuration(line, results);
            } else if (line.startsWith("Socket errors:")) {
                results.setErrors(parseSocketErrors(line));
            } else if (line.contains("Non-2xx or 3xx responses:")) {
                results.setErrors(parseNon2xxErrors(line));
            }
        }

        return results;
    }

    /**
     * Fetch Quarkus application metrics.
     */
    private Map<String, Double> fetchQuarkusMetrics(IntegrationConfiguration config) {
        try {
            QuarkusMetricsFetcher fetcher = new QuarkusMetricsFetcher(config.metricsUrl());
            return fetcher.fetchMetrics();
        } catch (Exception e) {
            LOGGER.warning("Failed to fetch Quarkus metrics: " + e.getMessage());
            // Return empty map since fetchMetrics returns Map<String, Double>
            return new HashMap<>();
        }
    }

    /**
     * Create comprehensive benchmark report combining WRK and system metrics.
     */
    private BenchmarkReport createBenchmarkReport(WrkResults wrkResults,
            Map<String, Double> quarkusMetrics,
            IntegrationConfiguration config) {
        BenchmarkReport report = new BenchmarkReport();

        // Basic metadata
        report.setTimestamp(Instant.now().toString());
        report.setBenchmarkType("wrk-integration");
        report.setServiceUrl(config.integrationServiceUrl());

        // WRK performance results
        Map<String, Object> performance = new HashMap<>();
        performance.put("requests_per_second", wrkResults.getRequestsPerSecond());
        performance.put("latency_avg_ms", wrkResults.getLatencyAvg());
        performance.put("total_requests", wrkResults.getTotalRequests());
        performance.put("duration_seconds", wrkResults.getDurationSeconds());
        performance.put("errors", wrkResults.getErrors());

        report.setPerformance(performance);

        // System metrics from Quarkus
        report.setSystemMetrics(quarkusMetrics);

        return report;
    }

    /**
     * Write JSON report to file.
     */
    private void writeJsonReport(BenchmarkReport report, String outputFile) throws IOException {
        String json = GSON.toJson(report);
        Files.writeString(Path.of(outputFile), json);
        LOGGER.info("Benchmark report written to: " + outputFile);
    }

    // Helper parsing methods
    private double parseDouble(String line, String prefix) {
        return Double.parseDouble(line.substring(prefix.length()).trim());
    }

    private double parseLatency(String line) {
        // Parse "Latency     2.34ms    1.23ms   45.67ms   89.12%"
        // Skip lines like "Latency Distribution"
        String[] parts = line.trim().split("\\s+");
        if (parts.length > 1 && "Latency".equals(parts[0]) && !"Distribution".equals(parts[1])) {
            String latencyStr = parts[1].replace("ms", "");
            try {
                return Double.parseDouble(latencyStr);
            } catch (NumberFormatException e) {
                LOGGER.fine("Could not parse latency from: " + line);
                return 0.0;
            }
        }
        return 0.0;
    }

    private void parseRequestsAndDuration(String line, WrkResults results) {
        // Parse "1234567 requests in 30.00s, 123.45MB read"
        String[] parts = line.split("\\s+");
        if (parts.length >= 4) {
            results.setTotalRequests(Long.parseLong(parts[0]));
            results.setDurationSeconds(Double.parseDouble(parts[3].replace("s,", "")));
        }
    }

    private long parseSocketErrors(String line) {
        // Parse "Socket errors: connect 0, read 0, write 0, timeout 0"
        long total = 0;
        String[] parts = line.split("[,:]");
        for (String part : parts) {
            part = part.trim();
            if (part.matches("\\d+")) {
                total += Long.parseLong(part);
            }
        }
        return total;
    }

    private long parseNon2xxErrors(String line) {
        // Parse "Non-2xx or 3xx responses: 848646"
        String[] parts = line.split(":");
        if (parts.length > 1) {
            return Long.parseLong(parts[1].trim());
        }
        return 0;
    }

    /**
     * Data class for WRK benchmark results.
     */
    public static class WrkResults {
        private double requestsPerSecond;
        private double latencyAvg;
        private long totalRequests;
        private double durationSeconds;
        private long errors;

        // Getters and setters
        public double getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public void setRequestsPerSecond(double requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }

        public double getLatencyAvg() {
            return latencyAvg;
        }

        public void setLatencyAvg(double latencyAvg) {
            this.latencyAvg = latencyAvg;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public void setTotalRequests(long totalRequests) {
            this.totalRequests = totalRequests;
        }

        public double getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(double durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public long getErrors() {
            return errors;
        }

        public void setErrors(long errors) {
            this.errors = errors;
        }
    }

    /**
     * Data class for comprehensive benchmark report.
     */
    public static class BenchmarkReport {
        private String timestamp;
        private String benchmarkType;
        private String serviceUrl;
        private Map<String, Object> performance;
        private Map<String, Double> systemMetrics;

        // Getters and setters
        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getBenchmarkType() {
            return benchmarkType;
        }

        public void setBenchmarkType(String benchmarkType) {
            this.benchmarkType = benchmarkType;
        }

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        public Map<String, Object> getPerformance() {
            return performance;
        }

        public void setPerformance(Map<String, Object> performance) {
            this.performance = performance;
        }

        public Map<String, Double> getSystemMetrics() {
            return systemMetrics;
        }

        public void setSystemMetrics(Map<String, Double> systemMetrics) {
            this.systemMetrics = systemMetrics;
        }
    }
}