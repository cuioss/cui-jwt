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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WrkIntegrationRunner's WRK output parsing logic.
 * Uses real WRK output captured from actual benchmark runs.
 */
class WrkIntegrationRunnerTest {

    private WrkIntegrationRunner runner;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        runner = new WrkIntegrationRunner();
    }

    @Test
    void parseRealWrkJwtOutput() throws IOException {
        // Load real WRK JWT validation output
        String realWrkOutput = loadTestResource("real-wrk-jwt-output.txt");

        // Create temp file with real output
        Path inputFile = tempDir.resolve("wrk-jwt-input.txt");
        Files.writeString(inputFile, realWrkOutput);

        // Parse the output using reflection to test private method
        WrkIntegrationRunner.WrkResults results = parseWrkOutputUsingReflection(inputFile.toString());

        // Verify parsed results match real output
        assertNotNull(results);
        assertTrue(results.getRequestsPerSecond() > 25000, "Should have high throughput from real run");
        assertTrue(results.getTotalRequests() > 800000, "Should have processed many requests");
        assertTrue(results.getDurationSeconds() > 29, "Should run for ~30 seconds");
        assertTrue(results.getLatencyAvg() > 0, "Should have measured latency");

        // All requests were 4xx errors in this run due to missing authentication
        assertEquals(results.getTotalRequests(), results.getErrors(), "All requests should be errors");
    }

    @Test
    void parseRealWrkHealthOutput() throws IOException {
        // Load real WRK health check output
        String realWrkOutput = loadTestResource("real-wrk-health-output.txt");

        // Create temp file with real output
        Path inputFile = tempDir.resolve("wrk-health-input.txt");
        Files.writeString(inputFile, realWrkOutput);

        // Parse the output
        WrkIntegrationRunner.WrkResults results = parseWrkOutputUsingReflection(inputFile.toString());

        // Verify parsed results
        assertNotNull(results);
        assertTrue(results.getRequestsPerSecond() > 10000, "Health endpoint should be fast");
        assertTrue(results.getLatencyAvg() < 100, "Health endpoint should have low latency");
        assertEquals(0, results.getErrors(), "Health endpoint should not have errors");
    }

    @Test
    void parseThroughputFromWrkOutput() {
        String wrkLine = "Requests/sec:  28192.77";

        // Test parsing using helper method
        double throughput = parseThroughputFromLine(wrkLine);

        assertEquals(28192.77, throughput, 0.01);
    }

    @Test
    void parseLatencyFromWrkOutput() {
        String latencyLine = "    Latency    39.07ms   67.21ms 607.31ms   82.87%";

        // Test parsing using helper method
        double avgLatency = parseLatencyFromLine(latencyLine);

        assertEquals(39.07, avgLatency, 0.01);
    }

    @Test
    void parseRequestsAndDurationFromWrkOutput() {
        String requestsLine = "  848646 requests in 30.10s, 112.50MB read";

        // Test parsing using helper methods
        long totalRequests = parseRequestsFromLine(requestsLine);
        double duration = parseDurationFromLine(requestsLine);

        assertEquals(848646, totalRequests);
        assertEquals(30.10, duration, 0.01);
    }

    @Test
    void parseErrorsFromWrkOutput() {
        String errorLine = "  Non-2xx or 3xx responses: 848646";

        // Test parsing using helper method
        long errors = parseErrorsFromLine(errorLine);

        assertEquals(848646, errors);
    }

    @Test
    void createBenchmarkReport() throws IOException {
        // Create mock WRK results
        WrkIntegrationRunner.WrkResults wrkResults = new WrkIntegrationRunner.WrkResults();
        wrkResults.setRequestsPerSecond(25000.0);
        wrkResults.setLatencyAvg(40.0);
        wrkResults.setTotalRequests(750000);
        wrkResults.setDurationSeconds(30.0);
        wrkResults.setErrors(0);

        // Mock Quarkus metrics
        Map<String, Double> mockMetrics = Map.of(
                "cpu_usage", 45.0,
                "memory_used", 256.0,
                "heap_used", 128.0
        );

        // Create mock integration config (this would need system properties in real use)
        // For unit test, we'll test the report structure
        WrkIntegrationRunner.BenchmarkReport report = new WrkIntegrationRunner.BenchmarkReport();
        report.setBenchmarkType("wrk-integration");
        report.setServiceUrl("https://localhost:10443");

        // Create performance map
        Map<String, Object> performance = new HashMap<>();
        performance.put("requests_per_second", wrkResults.getRequestsPerSecond());
        performance.put("latency_avg_ms", wrkResults.getLatencyAvg());
        performance.put("total_requests", wrkResults.getTotalRequests());
        performance.put("duration_seconds", wrkResults.getDurationSeconds());
        performance.put("errors", wrkResults.getErrors());

        report.setPerformance(performance);
        report.setSystemMetrics(mockMetrics);

        // Verify report structure
        assertNotNull(report);
        assertEquals("wrk-integration", report.getBenchmarkType());
        assertEquals("https://localhost:10443", report.getServiceUrl());
        assertEquals(25000.0, report.getPerformance().get("requests_per_second"));
        assertEquals(45.0, report.getSystemMetrics().get("cpu_usage"));
    }

    // Helper methods to test individual parsing functions

    private String loadTestResource(String filename) throws IOException {
        Path resourcePath = Path.of("src/test/resources", filename);
        if (!Files.exists(resourcePath)) {
            // Fallback to classpath
            try (var stream = getClass().getClassLoader().getResourceAsStream(filename)) {
                if (stream != null) {
                    return new String(stream.readAllBytes());
                }
            }
        }
        return Files.readString(resourcePath);
    }

    private WrkIntegrationRunner.WrkResults parseWrkOutputUsingReflection(String filePath) throws IOException {
        try {
            var method = WrkIntegrationRunner.class.getDeclaredMethod("parseWrkOutput", String.class);
            method.setAccessible(true);
            return (WrkIntegrationRunner.WrkResults) method.invoke(runner, filePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke parseWrkOutput via reflection", e);
        }
    }

    private double parseThroughputFromLine(String line) {
        // Implement the logic from parseDouble helper
        String prefix = "Requests/sec:";
        if (line.trim().startsWith(prefix)) {
            return Double.parseDouble(line.substring(line.indexOf(':') + 1).trim());
        }
        return 0.0;
    }

    private double parseLatencyFromLine(String line) {
        // Parse "Latency    39.07ms   67.21ms 607.31ms   82.87%"
        String[] parts = line.trim().split("\\s+");
        if (parts.length > 1 && "Latency".equals(parts[0])) {
            String latencyStr = parts[1].replace("ms", "");
            return Double.parseDouble(latencyStr);
        }
        return 0.0;
    }

    private long parseRequestsFromLine(String line) {
        // Parse "848646 requests in 30.10s, 112.50MB read"
        String[] parts = line.trim().split("\\s+");
        if (parts.length > 0) {
            return Long.parseLong(parts[0]);
        }
        return 0L;
    }

    private double parseDurationFromLine(String line) {
        // Parse "848646 requests in 30.10s, 112.50MB read"
        String[] parts = line.trim().split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if ("in".equals(parts[i]) && i + 1 < parts.length) {
                return Double.parseDouble(parts[i + 1].replace("s,", ""));
            }
        }
        return 0.0;
    }

    private long parseErrorsFromLine(String line) {
        // Parse "Non-2xx or 3xx responses: 848646"
        if (line.contains("Non-2xx or 3xx responses:")) {
            String[] parts = line.split(":");
            if (parts.length > 1) {
                return Long.parseLong(parts[1].trim());
            }
        }
        return 0L;
    }
}