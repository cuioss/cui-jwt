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
 * Tests for QuarkusMetricsPostProcessor
 */
class QuarkusMetricsPostProcessorTest {

    @TempDir
    Path tempDir;

    private Gson gson;
    private String metricsDownloadDir;

    @BeforeEach void setUp() throws IOException {
        gson = new GsonBuilder().create();
        metricsDownloadDir = createTestMetricsDirectory();
    }

    @Test void shouldParseQuarkusMetricsFiles() throws IOException {
        // Given - processor with test metrics directory
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(metricsDownloadDir, tempDir.toString());

        // When - parse and export Quarkus metrics
        Instant testTimestamp = Instant.parse("2025-08-01T14:00:00.000Z");
        processor.parseAndExportQuarkusMetrics(testTimestamp);

        // Then - should create quarkus-metrics.json
        File outputFile = new File(tempDir.toFile(), "quarkus-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            // Should have CPU, memory, and metadata sections
            assertTrue(metrics.containsKey("cpu"), "Should contain CPU metrics");
            assertTrue(metrics.containsKey("memory"), "Should contain memory metrics");
            assertTrue(metrics.containsKey("metadata"), "Should contain metadata");

            // Verify CPU metrics structure
            Map<String, Object> cpuMetrics = (Map<String, Object>) metrics.get("cpu");
            assertTrue(cpuMetrics.containsKey("system_cpu_usage_avg"));
            assertTrue(cpuMetrics.containsKey("system_cpu_usage_max"));
            assertTrue(cpuMetrics.containsKey("process_cpu_usage_avg"));
            assertTrue(cpuMetrics.containsKey("process_cpu_usage_max"));
            assertTrue(cpuMetrics.containsKey("cpu_count"));
            assertTrue(cpuMetrics.containsKey("load_average_1m_avg"));
            assertTrue(cpuMetrics.containsKey("load_average_1m_max"));

            // Verify memory metrics structure
            Map<String, Object> memoryMetrics = (Map<String, Object>) metrics.get("memory");
            assertTrue(memoryMetrics.containsKey("heap"));
            assertTrue(memoryMetrics.containsKey("nonheap"));

            Map<String, Object> heapMetrics = (Map<String, Object>) memoryMetrics.get("heap");
            assertTrue(heapMetrics.containsKey("used_bytes"));
            assertTrue(heapMetrics.containsKey("committed_bytes"));

            // Verify metadata
            Map<String, Object> metadata = (Map<String, Object>) metrics.get("metadata");
            assertEquals(testTimestamp.toString(), metadata.get("timestamp"));
            assertEquals("Quarkus metrics - Prometheus format", metadata.get("source"));
        }
    }

    @Test void shouldHandlePercentageFormatting() throws IOException {
        // Given - processor with test metrics
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(metricsDownloadDir, tempDir.toString());

        // When - parse metrics
        processor.parseAndExportQuarkusMetrics(Instant.now());

        // Then - verify percentage formatting
        File outputFile = new File(tempDir.toFile(), "quarkus-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            Map<String, Object> cpuMetrics = (Map<String, Object>) metrics.get("cpu");

            // CPU usage values should be formatted as percentages
            Object systemCpuAvg = cpuMetrics.get("system_cpu_usage_avg");
            assertTrue(systemCpuAvg instanceof Number, "CPU usage should be a number");

            double cpuValue = ((Number) systemCpuAvg).doubleValue();
            assertTrue(cpuValue >= 0 && cpuValue <= 100, "CPU usage should be in percentage range (0-100)");
        }
    }

    @Test void shouldHandleMemoryCalculations() throws IOException {
        // Given - processor with test metrics
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(metricsDownloadDir, tempDir.toString());

        // When - parse metrics
        processor.parseAndExportQuarkusMetrics(Instant.now());

        // Then - verify memory calculations
        File outputFile = new File(tempDir.toFile(), "quarkus-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            Map<String, Object> memoryMetrics = (Map<String, Object>) metrics.get("memory");
            Map<String, Object> heapMetrics = (Map<String, Object>) memoryMetrics.get("heap");

            // Heap metrics should be present and positive
            Object usedBytes = heapMetrics.get("used_bytes");
            assertTrue(usedBytes instanceof Number, "Used bytes should be a number");
            assertTrue(((Number) usedBytes).longValue() > 0, "Used bytes should be positive");

            Object committedBytes = heapMetrics.get("committed_bytes");
            assertTrue(committedBytes instanceof Number, "Committed bytes should be a number");
            assertTrue(((Number) committedBytes).longValue() > 0, "Committed bytes should be positive");
        }
    }

    @Test void shouldUseConvenienceMethod() throws IOException {
        // Given - test metrics in base directory structure
        String baseDir = tempDir.toString();
        File metricsDir = new File(baseDir, "metrics-download");
        metricsDir.mkdirs();

        // Copy test metrics file to expected location
        File sourceFile = new File(metricsDownloadDir, "jwt-validation-metrics.txt");
        File targetFile = new File(metricsDir, "jwt-validation-metrics.txt");
        Files.copy(sourceFile.toPath(), targetFile.toPath());

        // When - use convenience method
        QuarkusMetricsPostProcessor.parseAndExport(baseDir);

        // Then - should create quarkus-metrics.json in base directory
        File outputFile = new File(baseDir, "quarkus-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            assertFalse(metrics.isEmpty(), "Should have parsed metrics");
        }
    }

    /**
     * Create a test metrics directory with sample Prometheus metrics
     */
    private String createTestMetricsDirectory() throws IOException {
        File metricsDir = new File(tempDir.toFile(), "test-metrics");
        metricsDir.mkdirs();

        File testMetricsFile = new File(metricsDir, "jwt-validation-metrics.txt");

        String testMetricsContent = """
            # TYPE system_cpu_usage gauge
            # HELP system_cpu_usage The "recent cpu usage" of the system the application is running in
            system_cpu_usage 0.13922521857923498
            # TYPE process_cpu_usage gauge  
            # HELP process_cpu_usage The "recent cpu usage" for the Java Virtual Machine process
            process_cpu_usage 0.1377049180327869
            # TYPE system_cpu_count gauge
            # HELP system_cpu_count The number of processors available to the Java virtual machine
            system_cpu_count 2.0
            # TYPE system_load_average_1m gauge
            # HELP system_load_average_1m The sum of the number of runnable entities queued to available processors
            system_load_average_1m 3.77783203125
            # TYPE jvm_memory_used_bytes gauge
            # HELP jvm_memory_used_bytes The amount of used memory
            jvm_memory_used_bytes{area="heap",id="old generation space"} 7864320.0
            jvm_memory_used_bytes{area="heap",id="eden space"} 5242880.0
            jvm_memory_used_bytes{area="nonheap",id="runtime code cache (code and data)"} 0.0
            jvm_memory_used_bytes{area="heap",id="survivor space"} 0.0
            # TYPE jvm_memory_committed_bytes gauge
            # HELP jvm_memory_committed_bytes The amount of memory in bytes that is committed for the Java virtual machine to use
            jvm_memory_committed_bytes{area="heap",id="old generation space"} 7864320.0
            jvm_memory_committed_bytes{area="heap",id="eden space"} 4718592.0
            jvm_memory_committed_bytes{area="nonheap",id="runtime code cache (code and data)"} 0.0
            jvm_memory_committed_bytes{area="heap",id="survivor space"} 0.0
            # TYPE jvm_memory_max_bytes gauge
            # HELP jvm_memory_max_bytes The maximum amount of memory in bytes that can be used for memory management
            jvm_memory_max_bytes{area="heap",id="old generation space"} -1.0
            jvm_memory_max_bytes{area="heap",id="eden space"} -1.0
            jvm_memory_max_bytes{area="nonheap",id="runtime code cache (code and data)"} -1.0
            jvm_memory_max_bytes{area="heap",id="survivor space"} -1.0
            """;

        try (FileWriter writer = new FileWriter(testMetricsFile)) {
            writer.write(testMetricsContent);
        }

        return metricsDir.getAbsolutePath();
    }
}