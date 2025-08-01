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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for comprehensive MetricsPostProcessor functionality
 * Testing integration of both HTTP metrics and Quarkus metrics processing
 */
class ComprehensiveMetricsPostProcessorTest {

    @TempDir
    Path tempDir;

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder().create();
    }

    @Test
    void shouldProcessBothHttpAndQuarkusMetrics() throws IOException {
        // Given - complete test directory structure with both benchmark and metrics data
        String baseDirectory = createCompleteTestStructure();
        
        String benchmarkFile = baseDirectory + "/integration-benchmark-result.json";
        MetricsPostProcessor processor = new MetricsPostProcessor(benchmarkFile, baseDirectory);
        
        // When - process all metrics
        Instant testTimestamp = Instant.parse("2025-08-01T15:00:00.000Z");
        processor.parseAndExportAllMetrics(testTimestamp);

        // Then - should create both output files
        File httpMetricsFile = new File(baseDirectory, "http-metrics.json");
        File quarkusMetricsFile = new File(baseDirectory, "quarkus-metrics.json");
        
        assertTrue(httpMetricsFile.exists(), "Should create http-metrics.json");
        assertTrue(quarkusMetricsFile.exists(), "Should create quarkus-metrics.json");

        // Verify HTTP metrics content
        try (FileReader reader = new FileReader(httpMetricsFile)) {
            Map<String, Object> httpMetrics = gson.fromJson(reader, Map.class);
            
            assertTrue(httpMetrics.containsKey("echo"), "HTTP metrics should contain echo endpoint");
            assertTrue(httpMetrics.containsKey("health"), "HTTP metrics should contain health endpoint");
            assertTrue(httpMetrics.containsKey("jwt_validation"), "HTTP metrics should contain JWT validation endpoint");
            
            Map<String, Object> echoData = (Map<String, Object>) httpMetrics.get("echo");
            assertEquals(testTimestamp.toString(), echoData.get("timestamp"));
            assertTrue(echoData.containsKey("percentiles"));
        }

        // Verify Quarkus metrics content
        try (FileReader reader = new FileReader(quarkusMetricsFile)) {
            Map<String, Object> quarkusMetrics = gson.fromJson(reader, Map.class);
            
            assertTrue(quarkusMetrics.containsKey("cpu"), "Quarkus metrics should contain CPU data");
            assertTrue(quarkusMetrics.containsKey("memory"), "Quarkus metrics should contain memory data");
            assertTrue(quarkusMetrics.containsKey("metadata"), "Quarkus metrics should contain metadata");
            
            Map<String, Object> cpuData = (Map<String, Object>) quarkusMetrics.get("cpu");
            assertTrue(cpuData.containsKey("system_cpu_usage_avg"));
            assertTrue(cpuData.containsKey("cpu_count"));
            
            Map<String, Object> memoryData = (Map<String, Object>) quarkusMetrics.get("memory");
            assertTrue(memoryData.containsKey("heap"));
            assertTrue(memoryData.containsKey("nonheap"));
        }
    }

    @Test
    void shouldHandlePartialFailures() throws IOException {
        // Given - directory structure with only HTTP benchmark data (no Quarkus metrics)
        String baseDirectory = createHttpOnlyTestStructure();
        
        String benchmarkFile = baseDirectory + "/integration-benchmark-result.json";
        MetricsPostProcessor processor = new MetricsPostProcessor(benchmarkFile, baseDirectory);
        
        // When - process all metrics (should handle missing Quarkus metrics gracefully)
        processor.parseAndExportAllMetrics(Instant.now());

        // Then - should create HTTP metrics but not fail on missing Quarkus metrics
        File httpMetricsFile = new File(baseDirectory, "http-metrics.json");
        File quarkusMetricsFile = new File(baseDirectory, "quarkus-metrics.json");
        
        assertTrue(httpMetricsFile.exists(), "Should create http-metrics.json even if Quarkus metrics fail");
        assertFalse(quarkusMetricsFile.exists(), "Should not create quarkus-metrics.json if metrics-download missing");

        // Verify HTTP metrics are still valid
        try (FileReader reader = new FileReader(httpMetricsFile)) {
            Map<String, Object> httpMetrics = gson.fromJson(reader, Map.class);
            assertFalse(httpMetrics.isEmpty(), "HTTP metrics should be processed successfully");
        }
    }

    @Test
    void shouldUseComprehensiveConvenienceMethod() throws IOException {
        // Given - complete test directory structure
        String baseDirectory = createCompleteTestStructure();
        
        // When - use static convenience method
        MetricsPostProcessor.parseAndExport(baseDirectory);

        // Then - should create both metrics files
        File httpMetricsFile = new File(baseDirectory, "http-metrics.json");
        File quarkusMetricsFile = new File(baseDirectory, "quarkus-metrics.json");
        
        assertTrue(httpMetricsFile.exists(), "Convenience method should create http-metrics.json");
        assertTrue(quarkusMetricsFile.exists(), "Convenience method should create quarkus-metrics.json");

        // Verify both files contain valid data
        try (FileReader httpReader = new FileReader(httpMetricsFile);
             FileReader quarkusReader = new FileReader(quarkusMetricsFile)) {
            
            Map<String, Object> httpMetrics = gson.fromJson(httpReader, Map.class);
            Map<String, Object> quarkusMetrics = gson.fromJson(quarkusReader, Map.class);
            
            assertFalse(httpMetrics.isEmpty(), "HTTP metrics should be populated");
            assertFalse(quarkusMetrics.isEmpty(), "Quarkus metrics should be populated");
            
            // Both should have the same timestamp (approximately)
            String httpTimestamp = (String) ((Map<String, Object>) httpMetrics.get("echo")).get("timestamp");
            String quarkusTimestamp = (String) ((Map<String, Object>) quarkusMetrics.get("metadata")).get("timestamp");
            
            assertNotNull(httpTimestamp, "HTTP metrics should have timestamp");
            assertNotNull(quarkusTimestamp, "Quarkus metrics should have timestamp");
            
            // Timestamps should be within a few seconds of each other
            Instant httpTime = Instant.parse(httpTimestamp);
            Instant quarkusTime = Instant.parse(quarkusTimestamp);
            long timeDiff = Math.abs(httpTime.toEpochMilli() - quarkusTime.toEpochMilli());
            assertTrue(timeDiff < 5000, "Timestamps should be within 5 seconds: " + timeDiff + "ms");
        }
    }

    @Test
    void shouldProvideBackwardCompatibility() throws IOException {
        // Given - directory with HTTP benchmark data
        String baseDirectory = createHttpOnlyTestStructure();
        
        // When - use deprecated HTTP-only method
        MetricsPostProcessor.parseAndExportHttpOnly(baseDirectory);

        // Then - should only create HTTP metrics (backward compatibility)
        File httpMetricsFile = new File(baseDirectory, "http-metrics.json");
        File quarkusMetricsFile = new File(baseDirectory, "quarkus-metrics.json");
        
        assertTrue(httpMetricsFile.exists(), "Should create http-metrics.json for backward compatibility");
        assertFalse(quarkusMetricsFile.exists(), "Should not create quarkus-metrics.json with HTTP-only method");
    }

    /**
     * Create a complete test structure with both HTTP benchmark and Quarkus metrics data
     */
    private String createCompleteTestStructure() throws IOException {
        File baseDir = new File(tempDir.toFile(), "complete-test");
        baseDir.mkdirs();
        
        // Create HTTP benchmark data
        createHttpBenchmarkFile(baseDir);
        
        // Create Quarkus metrics data
        createQuarkusMetricsData(baseDir);
        
        return baseDir.getAbsolutePath();
    }

    /**
     * Create test structure with only HTTP benchmark data
     */
    private String createHttpOnlyTestStructure() throws IOException {
        File baseDir = new File(tempDir.toFile(), "http-only-test");
        baseDir.mkdirs();
        
        // Create only HTTP benchmark data
        createHttpBenchmarkFile(baseDir);
        
        return baseDir.getAbsolutePath();
    }

    private void createHttpBenchmarkFile(File baseDir) throws IOException {
        File benchmarkFile = new File(baseDir, "integration-benchmark-result.json");
        
        String benchmarkData = """
            [
                {
                    "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtEchoBenchmark.echoComprehensive",
                    "mode": "sample",
                    "primaryMetric": {
                        "scorePercentiles": {
                            "50.0": 8.5,
                            "95.0": 15.2,
                            "99.0": 28.1
                        },
                        "rawDataHistogram": [
                            [
                                [
                                    [8.5, 50]
                                ]
                            ]
                        ]
                    }
                },
                {
                    "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                    "mode": "sample",
                    "primaryMetric": {
                        "scorePercentiles": {
                            "50.0": 7.2,
                            "95.0": 12.8,
                            "99.0": 25.4
                        },
                        "rawDataHistogram": [
                            [
                                [
                                    [7.2, 60]
                                ]
                            ]
                        ]
                    }
                },
                {
                    "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtValidationBenchmark.validateJwtSample",
                    "mode": "sample",
                    "primaryMetric": {
                        "scorePercentiles": {
                            "50.0": 4.1,
                            "95.0": 6.8,
                            "99.0": 12.3
                        },
                        "rawDataHistogram": [
                            [
                                [
                                    [4.1, 100]
                                ]
                            ]
                        ]
                    }
                }
            ]
            """;
        
        try (FileWriter writer = new FileWriter(benchmarkFile)) {
            writer.write(benchmarkData);
        }
    }

    private void createQuarkusMetricsData(File baseDir) throws IOException {
        File metricsDir = new File(baseDir, "metrics-download");
        metricsDir.mkdirs();
        
        File metricsFile = new File(metricsDir, "jwt-validation-metrics.txt");
        
        String metricsData = """
            system_cpu_usage 0.12
            process_cpu_usage 0.10
            system_cpu_count 4.0
            system_load_average_1m 2.5
            jvm_memory_used_bytes{area="heap",id="eden space"} 8388608.0
            jvm_memory_used_bytes{area="heap",id="old generation space"} 12582912.0
            jvm_memory_committed_bytes{area="heap",id="eden space"} 8388608.0
            jvm_memory_committed_bytes{area="heap",id="old generation space"} 12582912.0
            """;
        
        try (FileWriter writer = new FileWriter(metricsFile)) {
            writer.write(metricsData);
        }
    }
}