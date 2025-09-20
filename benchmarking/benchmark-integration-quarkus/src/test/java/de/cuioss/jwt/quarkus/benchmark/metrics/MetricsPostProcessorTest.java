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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static de.cuioss.jwt.quarkus.benchmark.metrics.MetricsTestDataConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MetricsPostProcessor using parameterized test approach
 */
class MetricsPostProcessorTest extends AbstractMetricsProcessorTest {

    @Override protected void processMetrics(String inputFile, String outputDir, TestFixture fixture) throws IOException {
        MetricsPostProcessor processor = new MetricsPostProcessor(inputFile, outputDir);
        processor.parseAndExportHttpMetrics(Instant.now());
    }

    @Override protected void processMetricsWithTimestamp(String inputFile, String outputDir, TestFixture fixture, Instant timestamp) throws IOException {
        MetricsPostProcessor processor = new MetricsPostProcessor(inputFile, outputDir);
        processor.parseAndExportHttpMetrics(timestamp);
    }

    @Override protected Stream<Arguments> provideFileExistenceTestCases() {
        return Stream.of(
                Arguments.of("Standard health benchmark",
                        new TestFixture(STANDARD_HEALTH_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS)),
                Arguments.of("Standard JWT validation benchmark",
                        new TestFixture(STANDARD_JWT_VALIDATION_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS)),
                Arguments.of("Combined benchmarks",
                        new TestFixture(COMBINED_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS))
        );
    }

    @Override protected Stream<Arguments> provideJsonStructureTestCases() {
        return Stream.of(
                Arguments.of("Health endpoint structure",
                        new TestFixture(STANDARD_HEALTH_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS),
                        new String[]{"health"}),
                Arguments.of("JWT validation endpoint structure",
                        new TestFixture(STANDARD_JWT_VALIDATION_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS),
                        new String[]{"jwt_validation"}),
                Arguments.of("Combined endpoints structure",
                        new TestFixture(COMBINED_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS),
                        new String[]{"health", "jwt_validation"})
        );
    }

    @Override protected Stream<Arguments> provideNumericValidationTestCases() {
        return Stream.of(
                Arguments.of("Health percentile validation",
                        new TestFixture(STANDARD_HEALTH_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS),
                        new NumericValidation("health.percentiles.p50_us", 0, Double.MAX_VALUE)),
                Arguments.of("JWT validation sample count",
                        new TestFixture(STANDARD_JWT_VALIDATION_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS),
                        new NumericValidation("jwt_validation.sample_count", 1, Double.MAX_VALUE))
        );
    }

    @Override protected Stream<Arguments> provideTimestampTestCases() {
        return Stream.of(
                Arguments.of("Health endpoint timestamp",
                        new TestFixture(STANDARD_HEALTH_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS),
                        "health.timestamp"),
                Arguments.of("JWT validation endpoint timestamp",
                        new TestFixture(STANDARD_JWT_VALIDATION_BENCHMARK, "http-metrics.json", ProcessorType.HTTP_METRICS),
                        "jwt_validation.timestamp")
        );
    }

    @Override protected Stream<Arguments> provideExceptionTestCases() {
        return Stream.of(
                Arguments.of("File not found",
                        new ExceptionTestCase("/non/existent/file.json", IOException.class,
                                new TestFixture("", "http-metrics.json", ProcessorType.HTTP_METRICS)))
        );
    }

    // Additional specific tests that don't fit the parameterized pattern

    @Test @DisplayName("Should parse all endpoint types") void shouldParseAllEndpointTypes() throws IOException {
        // Arrange
        String testFile = createTestDataFile(COMBINED_BENCHMARK);
        MetricsPostProcessor parser = new MetricsPostProcessor(testFile, tempDir.toString());
        Instant testTimestamp = Instant.parse("2025-08-01T12:14:20.687806Z");

        // Act
        parser.parseAndExportHttpMetrics(testTimestamp);

        // Assert
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = (Map<String, Object>) gson.fromJson(reader, Map.class);

            assertTrue(metrics.containsKey("jwt_validation"));
            assertTrue(metrics.containsKey("health"));
            assertEquals(2, metrics.size());
        }
    }

    @Test @DisplayName("Should extract correct percentile data") void shouldExtractCorrectPercentileData() throws IOException {
        // This test is now covered by parameterized tests provideJsonStructureTestCases and provideNumericValidationTestCases
        // Keeping a simplified version for backward compatibility
        String testFile = createTestDataFile(STANDARD_HEALTH_BENCHMARK);
        MetricsPostProcessor parser = new MetricsPostProcessor(testFile, tempDir.toString());

        parser.parseAndExportHttpMetrics(Instant.now());

        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            @SuppressWarnings("unchecked") Map<String, Object> healthMetrics = (Map<String, Object>) metrics.get("health");
            @SuppressWarnings("unchecked") Map<String, Object> percentiles = (Map<String, Object>) healthMetrics.get("percentiles");

            assertNotNull(percentiles);
            assertTrue(percentiles.containsKey("p50_us"));
            assertTrue(percentiles.containsKey("p95_us"));
            assertTrue(percentiles.containsKey("p99_us"));
        }
    }

    @Test @DisplayName("Should format numbers correctly according to rules") void shouldFormatNumbersCorrectly() throws IOException {
        // Specific formatting test
        String testFile = createTestDataFile(STANDARD_HEALTH_BENCHMARK);
        MetricsPostProcessor parser = new MetricsPostProcessor(testFile, tempDir.toString());

        parser.parseAndExportHttpMetrics(Instant.now());

        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        String jsonContent = Files.readString(outputFile.toPath());

        assertFalse(jsonContent.contains(".0\""), "Should not have unnecessary .0 in numbers");

        @SuppressWarnings("unchecked") Map<String, Object> parsed = gson.fromJson(jsonContent, Map.class);
        assertNotNull(parsed);
        assertFalse(parsed.isEmpty());
    }

    // Sample count validation is now covered by provideNumericValidationTestCases

    @Test @DisplayName("Should correctly sum samples from multiple iterations") void shouldSumSamplesFromMultipleIterations() throws IOException {
        // Specific test for multi-iteration sample summing
        String testFile = createTestDataFile(MULTI_ITERATION_BENCHMARK);
        MetricsPostProcessor parser = new MetricsPostProcessor(testFile, tempDir.toString());

        parser.parseAndExportHttpMetrics(Instant.now());

        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            @SuppressWarnings("unchecked") Map<String, Object> healthMetrics = (Map<String, Object>) metrics.get("health");
            assertNotNull(healthMetrics);

            // Should sum up: 150 + 250 = 400 samples from two iterations
            assertEquals(400.0, healthMetrics.get("sample_count"));
        }
    }

    // Timestamp validation is now covered by provideTimestampTestCases

    @Test @DisplayName("Should only process sample mode benchmarks") void shouldOnlyProcessSampleModeBenchmarks() throws IOException {
        // Specific test for mode filtering
        String testFile = createTestDataFile(MIXED_MODE_BENCHMARK);
        MetricsPostProcessor parser = new MetricsPostProcessor(testFile, tempDir.toString());

        parser.parseAndExportHttpMetrics(Instant.now());

        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            assertFalse(metrics.isEmpty());

            for (String endpointType : metrics.keySet()) {
                @SuppressWarnings("unchecked") Map<String, Object> endpointData = (Map<String, Object>) metrics.get(endpointType);
                String source = (String) endpointData.get("source");
                assertTrue(source.contains("sample mode"));
            }
        }
    }

    // Exception handling is now covered by provideExceptionTestCases

    @Test @DisplayName("Should use static convenience method") void shouldUseConvenienceMethod() throws IOException {
        // Arrange
        File resultsDir = tempDir.toFile();
        File benchmarkFile = new File(resultsDir, "integration-benchmark-result.json");
        String testFile = createTestDataFile(COMBINED_BENCHMARK);
        Files.copy(Path.of(testFile), benchmarkFile.toPath());

        // Act
        MetricsPostProcessor.parseAndExport(resultsDir.getAbsolutePath());

        // Assert
        File outputFile = new File(resultsDir, "http-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = (Map<String, Object>) gson.fromJson(reader, Map.class);
            assertFalse(metrics.isEmpty());
        }
    }

    // Test data creation methods are no longer needed as we use MetricsTestDataConstants
}