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

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static de.cuioss.jwt.quarkus.benchmark.metrics.MetricsTestDataConstants.STANDARD_PROMETHEUS_METRICS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QuarkusMetricsPostProcessor using parameterized test approach
 */
class QuarkusMetricsPostProcessorTest extends AbstractMetricsProcessorTest {

    private String metricsDownloadDir;

    @Override protected void onSetUp() throws IOException {
        metricsDownloadDir = createTestMetricsDirectory();
    }

    @Override protected void processMetrics(String inputFile, String outputDir, TestFixture fixture) throws IOException {
        // For Quarkus metrics, we need to set up the metrics directory structure
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(metricsDownloadDir, outputDir);
        processor.parseAndExportQuarkusMetrics(Instant.now());
    }

    @Override protected void processMetricsWithTimestamp(String inputFile, String outputDir, TestFixture fixture, Instant timestamp) throws IOException {
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(metricsDownloadDir, outputDir);
        processor.parseAndExportQuarkusMetrics(timestamp);
    }

    @Override protected Stream<Arguments> provideFileExistenceTestCases() {
        return Stream.of(
                Arguments.of("Standard Prometheus metrics",
                        new TestFixture(STANDARD_PROMETHEUS_METRICS, "quarkus-metrics.json", ProcessorType.QUARKUS_METRICS))
        );
    }

    @Override protected Stream<Arguments> provideJsonStructureTestCases() {
        return Stream.of(
                Arguments.of("Quarkus metrics structure",
                        new TestFixture(STANDARD_PROMETHEUS_METRICS, "quarkus-metrics.json", ProcessorType.QUARKUS_METRICS),
                        new String[]{"cpu", "memory", "metadata"})
        );
    }

    @Override protected Stream<Arguments> provideNumericValidationTestCases() {
        return Stream.of(
                Arguments.of("CPU usage percentage validation",
                        new TestFixture(STANDARD_PROMETHEUS_METRICS, "quarkus-metrics.json", ProcessorType.QUARKUS_METRICS),
                        new NumericValidation("cpu.system_cpu_usage_avg", 0, 100)),
                Arguments.of("CPU count validation",
                        new TestFixture(STANDARD_PROMETHEUS_METRICS, "quarkus-metrics.json", ProcessorType.QUARKUS_METRICS),
                        new NumericValidation("cpu.cpu_count", 1, 1024))
        );
    }

    @Override protected Stream<Arguments> provideTimestampTestCases() {
        return Stream.of(
                Arguments.of("Quarkus metadata timestamp",
                        new TestFixture(STANDARD_PROMETHEUS_METRICS, "quarkus-metrics.json", ProcessorType.QUARKUS_METRICS),
                        "metadata.timestamp")
        );
    }

    // QuarkusMetricsPostProcessor doesn't throw exceptions in our test scenarios,
    // so we don't override provideExceptionTestCases (returns empty stream)

    // Additional specific tests
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
            Type mapType = new TypeToken<Map<String, Object>>(){
            }.getType();
            Map<String, Object> metrics = gson.fromJson(reader, mapType);

            // Should have CPU, memory, and metadata sections
            assertTrue(metrics.containsKey("cpu"), "Should contain CPU metrics");
            assertTrue(metrics.containsKey("memory"), "Should contain memory metrics");
            assertTrue(metrics.containsKey("metadata"), "Should contain metadata");

            // Verify CPU metrics structure - safe cast with instanceof check
            Object cpuObj = metrics.get("cpu");
            assertInstanceOf(Map.class, cpuObj, "CPU metrics should be a Map");
            @SuppressWarnings("unchecked") Map<String, Object> cpuMetrics = (Map<String, Object>) cpuObj;
            assertTrue(cpuMetrics.containsKey("system_cpu_usage_avg"));
            assertTrue(cpuMetrics.containsKey("system_cpu_usage_max"));
            assertTrue(cpuMetrics.containsKey("process_cpu_usage_avg"));
            assertTrue(cpuMetrics.containsKey("process_cpu_usage_max"));
            assertTrue(cpuMetrics.containsKey("cpu_count"));
            assertTrue(cpuMetrics.containsKey("load_average_1m_avg"));
            assertTrue(cpuMetrics.containsKey("load_average_1m_max"));

            // Verify memory metrics structure - safe cast with instanceof check
            Object memoryObj = metrics.get("memory");
            assertInstanceOf(Map.class, memoryObj, "Memory metrics should be a Map");
            @SuppressWarnings("unchecked") Map<String, Object> memoryMetrics = (Map<String, Object>) memoryObj;
            assertTrue(memoryMetrics.containsKey("heap"));
            assertTrue(memoryMetrics.containsKey("nonheap"));

            Object heapObj = memoryMetrics.get("heap");
            assertInstanceOf(Map.class, heapObj, "Heap metrics should be a Map");
            @SuppressWarnings("unchecked") Map<String, Object> heapMetrics = (Map<String, Object>) heapObj;
            assertTrue(heapMetrics.containsKey("used_bytes"));
            assertTrue(heapMetrics.containsKey("committed_bytes"));

            // Verify metadata - safe cast with instanceof check
            Object metadataObj = metrics.get("metadata");
            assertInstanceOf(Map.class, metadataObj, "Metadata should be a Map");
            @SuppressWarnings("unchecked") Map<String, Object> metadata = (Map<String, Object>) metadataObj;
            assertEquals(testTimestamp.toString(), metadata.get("timestamp"));
            assertEquals("Quarkus metrics - Prometheus format", metadata.get("source"));
        }
    }

    // Percentage formatting is now covered by provideNumericValidationTestCases

    @Test void shouldHandleMemoryCalculations() throws IOException {
        // Specific test for memory calculations
        QuarkusMetricsPostProcessor processor = new QuarkusMetricsPostProcessor(metricsDownloadDir, tempDir.toString());
        processor.parseAndExportQuarkusMetrics(Instant.now());

        File outputFile = new File(tempDir.toFile(), "quarkus-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Type mapType = new TypeToken<Map<String, Object>>(){
            }.getType();
            Map<String, Object> metrics = gson.fromJson(reader, mapType);

            Object memoryObj = metrics.get("memory");
            assertInstanceOf(Map.class, memoryObj, "Memory metrics should be a Map");
            @SuppressWarnings("unchecked") Map<String, Object> memoryMetrics = (Map<String, Object>) memoryObj;

            Object heapObj = memoryMetrics.get("heap");
            assertInstanceOf(Map.class, heapObj, "Heap metrics should be a Map");
            @SuppressWarnings("unchecked") Map<String, Object> heapMetrics = (Map<String, Object>) heapObj;

            Object usedBytes = heapMetrics.get("used_bytes");
            assertInstanceOf(Number.class, usedBytes);
            assertTrue(((Number) usedBytes).longValue() > 0);
        }
    }

    @Test void shouldUseConvenienceMethod() throws IOException {
        // Given - test metrics in base directory structure
        String baseDir = tempDir.toString();
        File metricsDir = new File(baseDir, "metrics-download");
        metricsDir.mkdirs();

        // Copy test metrics file to expected location
        File sourceFile = new File(metricsDownloadDir, "quarkus-metrics.txt");
        File targetFile = new File(metricsDir, "quarkus-metrics.txt");
        Files.copy(sourceFile.toPath(), targetFile.toPath());

        // When - use convenience method
        QuarkusMetricsPostProcessor.parseAndExport(baseDir);

        // Then - should create quarkus-metrics.json in base directory
        File outputFile = new File(baseDir, "quarkus-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            Type mapType = new TypeToken<Map<String, Object>>(){
            }.getType();
            Map<String, Object> metrics = gson.fromJson(reader, mapType);
            assertFalse(metrics.isEmpty(), "Should have parsed metrics");
        }
    }

    /**
     * Create a test metrics directory with sample Prometheus metrics
     */
    private String createTestMetricsDirectory() throws IOException {
        File metricsDir = new File(tempDir.toFile(), "test-metrics");
        metricsDir.mkdirs();

        File testMetricsFile = new File(metricsDir, "quarkus-metrics.txt");
        try (FileWriter writer = new FileWriter(testMetricsFile)) {
            writer.write(STANDARD_PROMETHEUS_METRICS);
        }

        return metricsDir.getAbsolutePath();
    }
}