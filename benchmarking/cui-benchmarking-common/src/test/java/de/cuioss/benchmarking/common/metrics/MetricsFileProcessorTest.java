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
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for MetricsFileProcessor
 */
class MetricsFileProcessorTest {

    @TempDir
    Path tempDir;

    private Path downloadsDir;
    private MetricsFileProcessor processor;

    @BeforeEach void setUp() {
        downloadsDir = tempDir.resolve("downloads");
        processor = new MetricsFileProcessor(downloadsDir);
    }

    @Test void shouldProcessAllMetricsFilesSuccessfully() throws IOException {
        Files.createDirectories(downloadsDir);

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = downloadsDir.resolve("test-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Double> metrics = processor.processAllMetricsFiles();

        assertFalse(metrics.isEmpty(), "Should have processed metrics");
        assertTrue(metrics.size() > 10, "Should have multiple metrics");

        assertTrue(metrics.containsKey("system_cpu_usage"), "Should contain system CPU usage");
        assertTrue(metrics.containsKey("process_cpu_usage"), "Should contain process CPU usage");
        assertTrue(metrics.containsKey("system_cpu_count"), "Should contain CPU count");
        assertTrue(metrics.containsKey("system_load_average_1m"), "Should contain load average");

        assertEquals(0.4074119443697027, metrics.get("system_cpu_usage"));
        assertEquals(0.40740279833176374, metrics.get("process_cpu_usage"));
        assertEquals(4.0, metrics.get("system_cpu_count"));
        assertEquals(2.775390625, metrics.get("system_load_average_1m"));
    }

    @Test void shouldReturnEmptyMapWhenDirectoryDoesNotExist() throws IOException {
        Path nonExistentDir = tempDir.resolve("non-existent");
        MetricsFileProcessor nonExistentProcessor = new MetricsFileProcessor(nonExistentDir);

        Map<String, Double> metrics = nonExistentProcessor.processAllMetricsFiles();

        assertTrue(metrics.isEmpty(), "Should return empty map for non-existent directory");
    }

    @Test void shouldReturnEmptyMapWhenDirectoryIsEmpty() throws IOException {
        Files.createDirectories(downloadsDir);

        Map<String, Double> metrics = processor.processAllMetricsFiles();

        assertTrue(metrics.isEmpty(), "Should return empty map for empty directory");
    }

    @Test void shouldProcessSpecificFileByName() throws IOException {
        Files.createDirectories(downloadsDir);

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = downloadsDir.resolve("specific-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Double> metrics = processor.processMetricsFile("specific-metrics.txt");

        assertFalse(metrics.isEmpty(), "Should have processed specific file");
        assertTrue(metrics.containsKey("system_cpu_usage"), "Should contain system CPU usage");
    }

    @Test void shouldReturnEmptyMapForNonExistentFile() throws IOException {
        Files.createDirectories(downloadsDir);

        Map<String, Double> metrics = processor.processMetricsFile("non-existent.txt");

        assertTrue(metrics.isEmpty(), "Should return empty map for non-existent file");
    }

    @Test void shouldProcessSpecificFileByPath() throws IOException {
        Files.createDirectories(downloadsDir);

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = downloadsDir.resolve("path-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Double> metrics = processor.processMetricsFile(targetFile);

        assertFalse(metrics.isEmpty(), "Should have processed file by path");
        assertTrue(metrics.containsKey("system_cpu_usage"), "Should contain system CPU usage");
    }

    @Test void shouldExtractJwtValidationMetrics() throws IOException {
        Files.createDirectories(downloadsDir);

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = downloadsDir.resolve("jwt-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Double> allMetrics = processor.processAllMetricsFiles();
        Map<String, Double> jwtMetrics = processor.extractJwtValidationMetrics(allMetrics);

        assertFalse(jwtMetrics.isEmpty(), "Should have JWT validation metrics");

        assertTrue(jwtMetrics.containsKey("cui_jwt_validation_success_operations_total{event_type=\"ACCESS_TOKEN_CREATED\",result=\"success\"}"),
                "Should contain JWT validation success metrics");
        assertTrue(jwtMetrics.containsKey("cui_jwt_bearer_token_validation_seconds_count{class=\"de.cuioss.jwt.quarkus.producer.BearerTokenProducer\",exception=\"none\",method=\"getBearerTokenResult\"}"),
                "Should contain bearer token validation metrics");

        assertEquals(5864975.0, jwtMetrics.get("cui_jwt_validation_success_operations_total{event_type=\"ACCESS_TOKEN_CREATED\",result=\"success\"}"));
        assertEquals(5864975.0, jwtMetrics.get("cui_jwt_bearer_token_validation_seconds_count{class=\"de.cuioss.jwt.quarkus.producer.BearerTokenProducer\",exception=\"none\",method=\"getBearerTokenResult\"}"));
    }

    @Test void shouldExtractResourceMetrics() throws IOException {
        Files.createDirectories(downloadsDir);

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = downloadsDir.resolve("resource-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Double> allMetrics = processor.processAllMetricsFiles();
        Map<String, Double> resourceMetrics = processor.extractResourceMetrics(allMetrics);

        assertFalse(resourceMetrics.isEmpty(), "Should have resource metrics");

        assertTrue(resourceMetrics.containsKey("system_cpu_usage"), "Should contain system CPU metrics");
        assertTrue(resourceMetrics.containsKey("process_cpu_usage"), "Should contain process CPU metrics");
        assertTrue(resourceMetrics.containsKey("system_cpu_count"), "Should contain CPU count");
        assertTrue(resourceMetrics.containsKey("system_load_average_1m"), "Should contain load average");
        assertTrue(resourceMetrics.containsKey("jvm_memory_used_bytes{area=\"heap\",id=\"eden space\"}"), "Should contain memory metrics");

        assertEquals(0.4074119443697027, resourceMetrics.get("system_cpu_usage"));
        assertEquals(4.0, resourceMetrics.get("system_cpu_count"));
        assertEquals(3.0408704E7, resourceMetrics.get("jvm_memory_used_bytes{area=\"heap\",id=\"eden space\"}"));
    }

    @Test void shouldExtractTagFromMetricName() {
        String metricName = "cui_jwt_validation_errors_total{category=\"INVALID_STRUCTURE\",event_type=\"FAILED_TO_DECODE_HEADER\",result=\"failure\"}";

        String category = processor.extractTag(metricName, "category");
        String eventType = processor.extractTag(metricName, "event_type");
        String result = processor.extractTag(metricName, "result");
        String nonExistent = processor.extractTag(metricName, "non_existent");

        assertEquals("INVALID_STRUCTURE", category);
        assertEquals("FAILED_TO_DECODE_HEADER", eventType);
        assertEquals("failure", result);
        assertNull(nonExistent);
    }

    @Test void shouldCheckIfMetricsFilesExist() throws IOException {
        assertFalse(processor.hasMetricsFiles(), "Should return false when directory doesn't exist");

        Files.createDirectories(downloadsDir);
        assertFalse(processor.hasMetricsFiles(), "Should return false when directory is empty");

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = downloadsDir.resolve("test.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        assertTrue(processor.hasMetricsFiles(), "Should return true when .txt files exist");

        Files.delete(targetFile);
        Files.createFile(downloadsDir.resolve("test.log"));
        assertFalse(processor.hasMetricsFiles(), "Should return false when only non-.txt files exist");
    }

    @Test void shouldHandleMultipleMetricsFiles() throws IOException {
        Files.createDirectories(downloadsDir);

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path file1 = downloadsDir.resolve("metrics1.txt");
        Path file2 = downloadsDir.resolve("metrics2.txt");

        Files.copy(sourceFile, file1, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(sourceFile, file2, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Double> metrics = processor.processAllMetricsFiles();

        assertFalse(metrics.isEmpty(), "Should have processed multiple files");
        assertTrue(metrics.containsKey("system_cpu_usage"), "Should contain metrics from files");
    }

    @Test void shouldHandleEmptyMetricsFile() throws IOException {
        Files.createDirectories(downloadsDir);
        Path emptyFile = downloadsDir.resolve("empty.txt");
        Files.createFile(emptyFile);

        Map<String, Double> metrics = processor.processMetricsFile(emptyFile);

        assertTrue(metrics.isEmpty(), "Should return empty map for empty file");
    }

    @Test void shouldHandleMetricsFileWithOnlyComments() throws IOException {
        Files.createDirectories(downloadsDir);
        Path commentsFile = downloadsDir.resolve("comments.txt");
        Files.write(commentsFile, "# This is a comment\n# Another comment\n\n# More comments".getBytes());

        Map<String, Double> metrics = processor.processMetricsFile(commentsFile);

        assertTrue(metrics.isEmpty(), "Should return empty map for file with only comments");
    }

    @Test void shouldHandleInvalidMetricLines() throws IOException {
        Files.createDirectories(downloadsDir);
        Path invalidFile = downloadsDir.resolve("invalid.txt");
        Files.write(invalidFile, ("""
                # Valid comment
                valid_metric 123.45
                invalid_line_without_space
                invalid_metric not_a_number
                another_valid_metric 67.89
                """).getBytes());

        Map<String, Double> metrics = processor.processMetricsFile(invalidFile);

        assertEquals(2, metrics.size(), "Should process only valid metric lines");
        assertEquals(123.45, metrics.get("valid_metric"));
        assertEquals(67.89, metrics.get("another_valid_metric"));
    }

    @Test void shouldHandleMemoryMetricsWithLabels() throws IOException {
        Files.createDirectories(downloadsDir);

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path targetFile = downloadsDir.resolve("memory-metrics.txt");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Double> allMetrics = processor.processAllMetricsFiles();

        assertTrue(allMetrics.containsKey("jvm_memory_used_bytes{area=\"heap\",id=\"eden space\"}"),
                "Should contain heap memory metrics");
        assertTrue(allMetrics.containsKey("jvm_memory_used_bytes{area=\"nonheap\",id=\"runtime code cache (code and data)\"}"),
                "Should contain non-heap memory metrics");

        assertEquals(3.0408704E7, allMetrics.get("jvm_memory_used_bytes{area=\"heap\",id=\"eden space\"}"));
        assertEquals(0.0, allMetrics.get("jvm_memory_used_bytes{area=\"nonheap\",id=\"runtime code cache (code and data)\"}"));
    }

    @Test void shouldProcessOnlyTxtFiles() throws IOException {
        Files.createDirectories(downloadsDir);

        Path sourceFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
        Path txtFile = downloadsDir.resolve("metrics.txt");
        Path logFile = downloadsDir.resolve("metrics.log");
        Path csvFile = downloadsDir.resolve("metrics.csv");

        Files.copy(sourceFile, txtFile, StandardCopyOption.REPLACE_EXISTING);
        Files.write(logFile, "log content".getBytes());
        Files.write(csvFile, "csv,content".getBytes());

        Map<String, Double> metrics = processor.processAllMetricsFiles();

        assertFalse(metrics.isEmpty(), "Should have processed .txt file");
        assertTrue(metrics.containsKey("system_cpu_usage"), "Should contain metrics from .txt file only");
    }
}