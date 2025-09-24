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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the new simplified metrics classes with real files.
 * Tests the complete workflow: file processing -> JSON export.
 */
class MetricsIntegrationTest {

    @TempDir
    Path tempDir;

    private Path downloadsDirectory;
    private Path targetDirectory;
    private MetricsFileProcessor processor;
    private MetricsJsonExporter exporter;

    @BeforeEach void setUp() {
        downloadsDirectory = tempDir.resolve("downloads");
        targetDirectory = tempDir.resolve("target");
        processor = new MetricsFileProcessor(downloadsDirectory);
        exporter = new MetricsJsonExporter(targetDirectory);
    }

    @Test void completeWorkflowWithRealMetricsFile() throws IOException {
        // Create a realistic Prometheus metrics file
        createSampleMetricsFile();

        // Test file processor
        Map<String, Double> allMetrics = processor.processAllMetricsFiles();
        assertFalse(allMetrics.isEmpty(), "Should have processed metrics from file");

        // Test JWT validation metrics extraction
        Map<String, Double> jwtMetrics = processor.extractJwtValidationMetrics(allMetrics);
        assertTrue(jwtMetrics.containsKey("cui_jwt_validation_errors_total{category=\"INVALID_STRUCTURE\",event_type=\"FAILED_TO_DECODE_HEADER\",result=\"failure\"}"));

        // Test resource metrics extraction
        Map<String, Double> resourceMetrics = processor.extractResourceMetrics(allMetrics);
        assertTrue(resourceMetrics.containsKey("system_cpu_usage"));
        assertTrue(resourceMetrics.containsKey("jvm_memory_used_bytes{area=\"heap\",id=\"G1 Eden Space\"}"));

        // Test JSON export
        exporter.exportJwtValidationMetrics("JwtValidationBenchmark", Instant.now(), allMetrics);

        // Verify JSON file was created
        Path jsonFile = targetDirectory.resolve("integration-metrics.json");
        assertTrue(Files.exists(jsonFile), "JSON file should be created");

        // Verify JSON content
        String content = Files.readString(jsonFile);
        assertTrue(content.contains("timestamp"), "JSON should contain timestamp");
        assertTrue(content.contains("bearer_token_producer_metrics"), "JSON should contain bearer token metrics");
        assertTrue(content.contains("security_event_counter_metrics"), "JSON should contain security event metrics");
    }

    @Test void metricsFileProcessorWithNonExistentDirectory() throws IOException {
        Path nonExistentDir = tempDir.resolve("nonexistent");
        MetricsFileProcessor badProcessor = new MetricsFileProcessor(nonExistentDir);

        Map<String, Double> metrics = badProcessor.processAllMetricsFiles();
        assertTrue(metrics.isEmpty(), "Should return empty map for non-existent directory");

        assertFalse(badProcessor.hasMetricsFiles(), "Should return false for non-existent directory");
    }

    @Test void metricsFileProcessorWithEmptyFile() throws IOException {
        Files.createDirectories(downloadsDirectory);
        Path emptyFile = downloadsDirectory.resolve("empty-metrics.txt");
        Files.createFile(emptyFile);

        Map<String, Double> metrics = processor.processAllMetricsFiles();
        assertTrue(metrics.isEmpty(), "Should return empty map for empty file");
    }

    @Test void tagExtraction() {
        String metricName = "cui_jwt_validation_errors_total{category=\"INVALID_STRUCTURE\",event_type=\"FAILED_TO_DECODE_HEADER\",result=\"failure\"}";

        assertEquals("INVALID_STRUCTURE", processor.extractTag(metricName, "category"));
        assertEquals("FAILED_TO_DECODE_HEADER", processor.extractTag(metricName, "event_type"));
        assertEquals("failure", processor.extractTag(metricName, "result"));
        assertNull(processor.extractTag(metricName, "nonexistent"));
    }

    @Test void jsonExporterDirectoryCreation() throws IOException {
        Path newTargetDir = tempDir.resolve("new-target");
        assertFalse(Files.exists(newTargetDir));

        new MetricsJsonExporter(newTargetDir);
        assertTrue(Files.exists(newTargetDir), "Target directory should be created");
    }

    @Test void resourceMetricsExport() throws IOException {
        createSampleResourceMetricsFile();

        Map<String, Double> allMetrics = processor.processAllMetricsFiles();
        exporter.exportResourceMetrics(Instant.now(), allMetrics);

        Path jsonFile = targetDirectory.resolve("resource-metrics.json");
        assertTrue(Files.exists(jsonFile), "Resource metrics JSON file should be created");

        String content = Files.readString(jsonFile);
        assertTrue(content.contains("cpu_metrics"), "JSON should contain CPU metrics");
        assertTrue(content.contains("memory_metrics"), "JSON should contain memory metrics");
    }

    private void createSampleMetricsFile() throws IOException {
        Files.createDirectories(downloadsDirectory);
        Path metricsFile = downloadsDirectory.resolve("sample-metrics.txt");

        String sampleMetrics = """
                # HELP cui_jwt_validation_errors_total Total number of JWT validation errors
                # TYPE cui_jwt_validation_errors_total counter
                cui_jwt_validation_errors_total{category="INVALID_STRUCTURE",event_type="FAILED_TO_DECODE_HEADER",result="failure"} 5.0
                cui_jwt_validation_errors_total{category="INVALID_SIGNATURE",event_type="SIGNATURE_VERIFICATION_FAILED",result="failure"} 3.0

                # HELP cui_jwt_validation_success_total Total number of successful JWT validations
                # TYPE cui_jwt_validation_success_total counter
                cui_jwt_validation_success_total{event_type="ACCESS_TOKEN_CREATED",result="success"} 100.0

                # HELP bearer_token_validation_seconds Time spent validating bearer tokens
                # TYPE bearer_token_validation_seconds summary
                bearer_token_validation_seconds_count{result="getBearerTokenResult"} 105.0
                bearer_token_validation_seconds_sum{result="getBearerTokenResult"} 0.021
                bearer_token_validation_seconds_max{result="getBearerTokenResult"} 0.005

                # HELP system_cpu_usage System CPU usage
                # TYPE system_cpu_usage gauge
                system_cpu_usage 0.75

                # HELP jvm_memory_used_bytes Used bytes of a given JVM memory area
                # TYPE jvm_memory_used_bytes gauge
                jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 1048576.0
                jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 524288.0
                """;

        Files.writeString(metricsFile, sampleMetrics);
    }

    private void createSampleResourceMetricsFile() throws IOException {
        Files.createDirectories(downloadsDirectory);
        Path metricsFile = downloadsDirectory.resolve("resource-metrics.txt");

        String resourceMetrics = """
                # System metrics
                system_cpu_usage 0.85
                process_cpu_usage 0.45
                system_cpu_count 8.0
                system_load_average_1m 2.5

                # JVM memory metrics
                jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 2097152.0
                jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 4194304.0
                jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 1048576.0
                jvm_memory_committed_bytes{area="heap",id="G1 Eden Space"} 8388608.0
                jvm_memory_max_bytes{area="heap",id="G1 Eden Space"} 16777216.0
                """;

        Files.writeString(metricsFile, resourceMetrics);
    }
}