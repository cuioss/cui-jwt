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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ComprehensiveMetricsPostProcessorTest {

    @TempDir
    Path tempDir;

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder().create();
    }

    @Test
    @DisplayName("Should process both HTTP and Quarkus metrics")
    void shouldProcessBothHttpAndQuarkusMetrics() throws IOException {
        // Arrange
        String benchmarkResultsDir = createCompleteTestStructure();
        String benchmarkFile = benchmarkResultsDir + "/integration-benchmark-result.json";
        MetricsPostProcessor processor = new MetricsPostProcessor(benchmarkFile, benchmarkResultsDir);
        Instant testTimestamp = Instant.parse("2025-08-01T15:00:00.000Z");

        // Act
        processor.parseAndExportAllMetrics(testTimestamp);

        // Assert
        File httpMetricsFile = new File(benchmarkResultsDir, "http-metrics.json");
        File quarkusMetricsFile = new File(benchmarkResultsDir, "quarkus-metrics.json");

        assertTrue(httpMetricsFile.exists(), "Should create http-metrics.json");
        assertTrue(quarkusMetricsFile.exists(), "Should create quarkus-metrics.json");

        try (FileReader reader = new FileReader(httpMetricsFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> httpMetrics = (Map<String, Object>) gson.fromJson(reader, Map.class);

            assertTrue(httpMetrics.containsKey("health"));
            assertTrue(httpMetrics.containsKey("jwt_validation"));

            @SuppressWarnings("unchecked") Map<String, Object> healthData = (Map<String, Object>) httpMetrics.get("health");
            assertEquals(testTimestamp.toString(), healthData.get("timestamp"));
            assertTrue(healthData.containsKey("percentiles"));
        }

        try (FileReader reader = new FileReader(quarkusMetricsFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> quarkusMetrics = (Map<String, Object>) gson.fromJson(reader, Map.class);

            assertTrue(quarkusMetrics.containsKey("cpu"));
            assertTrue(quarkusMetrics.containsKey("memory"));
            assertTrue(quarkusMetrics.containsKey("metadata"));

            @SuppressWarnings("unchecked") Map<String, Object> cpuData = (Map<String, Object>) quarkusMetrics.get("cpu");
            assertTrue(cpuData.containsKey("system_cpu_usage_avg"));
            assertTrue(cpuData.containsKey("cpu_count"));

            @SuppressWarnings("unchecked") Map<String, Object> memoryData = (Map<String, Object>) quarkusMetrics.get("memory");
            assertTrue(memoryData.containsKey("heap"));
            assertTrue(memoryData.containsKey("nonheap"));
        }
    }

    @Test
    @DisplayName("Should handle partial failures gracefully")
    void shouldHandlePartialFailures() throws IOException {
        // Arrange
        String baseDirectory = createHttpOnlyTestStructure();
        String benchmarkFile = baseDirectory + "/integration-benchmark-result.json";
        MetricsPostProcessor processor = new MetricsPostProcessor(benchmarkFile, baseDirectory);

        // Act
        processor.parseAndExportAllMetrics(Instant.now());

        // Assert
        File httpMetricsFile = new File(baseDirectory, "http-metrics.json");
        File quarkusMetricsFile = new File(baseDirectory, "quarkus-metrics.json");

        assertTrue(httpMetricsFile.exists(), "Should create http-metrics.json even if Quarkus metrics fail");
        assertFalse(quarkusMetricsFile.exists(), "Should not create quarkus-metrics.json if metrics-download missing");

        try (FileReader reader = new FileReader(httpMetricsFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> httpMetrics = (Map<String, Object>) gson.fromJson(reader, Map.class);
            assertFalse(httpMetrics.isEmpty());
        }
    }

    @Test
    @DisplayName("Should use comprehensive convenience method for both metrics")
    void shouldUseComprehensiveConvenienceMethod() throws IOException {
        // Arrange
        String benchmarkResultsDir = createCompleteTestStructure();

        // Act
        MetricsPostProcessor.parseAndExport(benchmarkResultsDir);

        // Assert
        File httpMetricsFile = new File(benchmarkResultsDir, "http-metrics.json");
        File quarkusMetricsFile = new File(benchmarkResultsDir, "quarkus-metrics.json");

        assertTrue(httpMetricsFile.exists(), "Convenience method should create http-metrics.json");
        assertTrue(quarkusMetricsFile.exists(), "Convenience method should create quarkus-metrics.json");

        try (FileReader httpReader = new FileReader(httpMetricsFile);
             FileReader quarkusReader = new FileReader(quarkusMetricsFile)) {

            @SuppressWarnings("unchecked") Map<String, Object> httpMetrics = (Map<String, Object>) gson.fromJson(httpReader, Map.class);
            @SuppressWarnings("unchecked") Map<String, Object> quarkusMetrics = (Map<String, Object>) gson.fromJson(quarkusReader, Map.class);

            assertFalse(httpMetrics.isEmpty());
            assertFalse(quarkusMetrics.isEmpty());

            @SuppressWarnings("unchecked") Map<String, Object> healthMap = (Map<String, Object>) httpMetrics.get("health");
            String httpTimestamp = (String) healthMap.get("timestamp");
            @SuppressWarnings("unchecked") Map<String, Object> metadataMap = (Map<String, Object>) quarkusMetrics.get("metadata");
            String quarkusTimestamp = (String) metadataMap.get("timestamp");

            assertNotNull(httpTimestamp);
            assertNotNull(quarkusTimestamp);

            Instant httpTime = Instant.parse(httpTimestamp);
            Instant quarkusTime = Instant.parse(quarkusTimestamp);
            long timeDiff = Math.abs(httpTime.toEpochMilli() - quarkusTime.toEpochMilli());
            assertTrue(timeDiff < 5000);
        }
    }

    @Test
    @DisplayName("Should provide backward compatibility with HTTP-only method")
    void shouldProvideBackwardCompatibility() throws IOException {
        // Arrange
        String baseDirectory = createHttpOnlyTestStructure();

        // Act
        MetricsPostProcessor.parseAndExportHttpOnly(baseDirectory);

        // Assert
        File httpMetricsFile = new File(baseDirectory, "http-metrics.json");
        File quarkusMetricsFile = new File(baseDirectory, "quarkus-metrics.json");

        assertTrue(httpMetricsFile.exists(), "Should create http-metrics.json for backward compatibility");
        assertFalse(quarkusMetricsFile.exists(), "Should not create quarkus-metrics.json with HTTP-only method");
    }

    private String createCompleteTestStructure() throws IOException {
        File baseDir = new File(tempDir.toFile(), "complete-test");
        baseDir.mkdirs();

        // Create target directory structure as expected by MetricsPostProcessor
        File targetDir = new File(tempDir.toFile(), "complete-test-target");
        targetDir.mkdirs();

        // Create benchmark-results directory in target
        File benchmarkResultsDir = new File(targetDir, "benchmark-results");
        benchmarkResultsDir.mkdirs();

        createHttpBenchmarkFile(benchmarkResultsDir);
        createQuarkusMetricsData(targetDir);

        return benchmarkResultsDir.getAbsolutePath();
    }

    private String createHttpOnlyTestStructure() throws IOException {
        File baseDir = new File(tempDir.toFile(), "http-only-test");
        baseDir.mkdirs();

        createHttpBenchmarkFile(baseDir);

        return baseDir.getAbsolutePath();
    }

    private void createHttpBenchmarkFile(File baseDir) throws IOException {
        File benchmarkFile = new File(baseDir, "integration-benchmark-result.json");

        String benchmarkData = """
            [
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

    private void createQuarkusMetricsData(File targetDir) throws IOException {
        File metricsDir = new File(targetDir, "metrics-download");
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