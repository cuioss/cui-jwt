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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricsPostProcessorTest {

    @TempDir
    Path tempDir;

    private Gson gson;
    private String testBenchmarkFile;

    @BeforeEach
    void setUp() throws IOException {
        gson = new GsonBuilder().create();
        testBenchmarkFile = createTestBenchmarkFile();
    }

    @Test
    @DisplayName("Should parse all three endpoint types")
    void shouldParseAllThreeEndpointTypes() throws IOException {
        // Arrange
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());
        Instant testTimestamp = Instant.parse("2025-08-01T12:14:20.687806Z");

        // Act
        parser.parseAndExportHttpMetrics(testTimestamp);

        // Assert
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            assertTrue(metrics.containsKey("jwt_validation"));
            assertTrue(metrics.containsKey("echo"));
            assertTrue(metrics.containsKey("health"));
            assertEquals(3, metrics.size());
        }
    }

    @Test
    @DisplayName("Should extract correct percentile data")
    void shouldExtractCorrectPercentileData() throws IOException {
        // Arrange
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());

        // Act
        parser.parseAndExportHttpMetrics(Instant.now());

        // Assert
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            Map<String, Object> echoMetrics = (Map<String, Object>) metrics.get("echo");
            assertNotNull(echoMetrics);
            assertEquals("Echo", echoMetrics.get("name"));

            Map<String, Object> percentiles = (Map<String, Object>) echoMetrics.get("percentiles");
            assertNotNull(percentiles);

            assertTrue(percentiles.containsKey("p50_us"));
            assertTrue(percentiles.containsKey("p95_us"));
            assertTrue(percentiles.containsKey("p99_us"));

            assertTrue(percentiles.get("p50_us") instanceof Number);
            assertTrue(percentiles.get("p95_us") instanceof Number);
            assertTrue(percentiles.get("p99_us") instanceof Number);
        }
    }

    @Test
    @DisplayName("Should format numbers correctly according to rules")
    void shouldFormatNumbersCorrectly() throws IOException {
        // Arrange
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());

        // Act
        parser.parseAndExportHttpMetrics(Instant.now());

        // Assert
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        String jsonContent = Files.readString(outputFile.toPath());

        assertFalse(jsonContent.contains(".0\""));

        Map<String, Object> parsed = gson.fromJson(jsonContent, Map.class);
        assertNotNull(parsed);
        assertTrue(parsed.size() >= 1);
    }

    @Test
    @DisplayName("Should include sample counts in metrics")
    void shouldIncludeSampleCounts() throws IOException {
        // Arrange
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());

        // Act
        parser.parseAndExportHttpMetrics(Instant.now());

        // Assert
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            for (String endpointType : metrics.keySet()) {
                Map<String, Object> endpointData = (Map<String, Object>) metrics.get(endpointType);

                assertTrue(endpointData.containsKey("sample_count"));

                Object sampleCount = endpointData.get("sample_count");
                assertTrue(sampleCount instanceof Number);
                assertTrue(((Number) sampleCount).intValue() > 0);
            }
        }
    }

    @Test
    @DisplayName("Should include timestamp and source information")
    void shouldIncludeTimestampAndSource() throws IOException {
        // Arrange
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());
        Instant testTimestamp = Instant.parse("2025-08-01T12:14:20.687806Z");

        // Act
        parser.parseAndExportHttpMetrics(testTimestamp);

        // Assert
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            for (String endpointType : metrics.keySet()) {
                Map<String, Object> endpointData = (Map<String, Object>) metrics.get(endpointType);

                assertEquals(testTimestamp.toString(), endpointData.get("timestamp"));
                assertTrue(endpointData.containsKey("source"));

                String source = (String) endpointData.get("source");
                assertTrue(source.contains("JMH benchmark"));
                assertTrue(source.contains("sample mode"));
            }
        }
    }

    @Test
    @DisplayName("Should only process sample mode benchmarks")
    void shouldOnlyProcessSampleModeBenchmarks() throws IOException {
        // Arrange
        String mixedModeFile = createMixedModeBenchmarkFile();
        MetricsPostProcessor parser = new MetricsPostProcessor(mixedModeFile, tempDir.toString());

        // Act
        parser.parseAndExportHttpMetrics(Instant.now());

        // Assert
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);

            assertFalse(metrics.isEmpty());

            for (String endpointType : metrics.keySet()) {
                Map<String, Object> endpointData = (Map<String, Object>) metrics.get(endpointType);
                String source = (String) endpointData.get("source");
                assertTrue(source.contains("sample mode"));
            }
        }
    }

    @Test
    @DisplayName("Should handle file not found exception")
    void shouldHandleFileNotFound() {
        // Arrange
        MetricsPostProcessor parser = new MetricsPostProcessor("/non/existent/file.json", tempDir.toString());

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> parser.parseAndExportHttpMetrics(Instant.now()));

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should use static convenience method")
    void shouldUseConvenienceMethod() throws IOException {
        // Arrange
        File resultsDir = tempDir.toFile();
        File benchmarkFile = new File(resultsDir, "integration-benchmark-result.json");
        Files.copy(Path.of(testBenchmarkFile), benchmarkFile.toPath());

        // Act
        MetricsPostProcessor.parseAndExport(resultsDir.getAbsolutePath());

        // Assert
        File outputFile = new File(resultsDir, "http-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            assertFalse(metrics.isEmpty());
        }
    }

    private String createTestBenchmarkFile() throws IOException {
        File testFile = new File(tempDir.toFile(), "test-benchmark-result.json");

        String testData = """
        [
            {
                "jmhVersion": "1.37",
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtEchoBenchmark.echoComprehensive",
                "mode": "sample",
                "threads": 1,
                "forks": 1,
                "primaryMetric": {
                    "score": 9.150612945454549,
                    "scorePercentiles": {
                        "0.0": 5.226496,
                        "50.0": 8.318976,
                        "90.0": 13.198950400000001,
                        "95.0": 14.766079999999999,
                        "99.0": 26.88860160000001,
                        "99.9": 28.213248,
                        "100.0": 28.213248
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [5.226496, 1],
                                [8.318976, 50],
                                [28.213248, 1]
                            ]
                        ]
                    ]
                }
            },
            {
                "jmhVersion": "1.37",
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                "mode": "sample",
                "threads": 1,
                "forks": 1,
                "primaryMetric": {
                    "score": 8.297886677685952,
                    "scorePercentiles": {
                        "0.0": 4.58752,
                        "50.0": 7.6513279999999995,
                        "90.0": 11.327897599999998,
                        "95.0": 13.4529024,
                        "99.0": 29.980098560000016,
                        "99.9": 33.325056,
                        "100.0": 33.325056
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [4.58752, 1],
                                [7.6513279999999995, 60],
                                [33.325056, 1]
                            ]
                        ]
                    ]
                }
            },
            {
                "jmhVersion": "1.37",
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtValidationBenchmark.validateAccessTokenThroughput",
                "mode": "sample",
                "threads": 1,
                "forks": 1,
                "primaryMetric": {
                    "score": 15.5,
                    "scorePercentiles": {
                        "0.0": 12.1,
                        "50.0": 15.2,
                        "90.0": 18.7,
                        "95.0": 21.3,
                        "99.0": 35.8,
                        "99.9": 42.1,
                        "100.0": 42.1
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [12.1, 1],
                                [15.2, 40],
                                [42.1, 1]
                            ]
                        ]
                    ]
                }
            }
        ]
        """;

        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write(testData);
        }

        return testFile.getAbsolutePath();
    }

    private String createMixedModeBenchmarkFile() throws IOException {
        File testFile = new File(tempDir.toFile(), "mixed-mode-benchmark-result.json");

        String testData = """
        [
            {
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtEchoBenchmark.echoComprehensive",
                "mode": "thrpt",
                "primaryMetric": {
                    "score": 1000.0,
                    "scoreUnit": "ops/s"
                }
            },
            {
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtEchoBenchmark.echoComprehensive",
                "mode": "sample",
                "primaryMetric": {
                    "score": 8.5,
                    "scorePercentiles": {
                        "50.0": 8.3,
                        "95.0": 14.7,
                        "99.0": 26.9
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [8.3, 30]
                            ]
                        ]
                    ]
                }
            }
        ]
        """;

        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write(testData);
        }

        return testFile.getAbsolutePath();
    }
}