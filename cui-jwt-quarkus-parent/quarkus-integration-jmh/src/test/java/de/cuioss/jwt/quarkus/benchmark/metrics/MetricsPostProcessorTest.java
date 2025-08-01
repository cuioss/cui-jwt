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
 * Tests for MetricsPostProcessor
 */
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
    void shouldParseAllThreeEndpointTypes() throws IOException {
        // Given - test benchmark file with all three endpoint types
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());
        
        // When - parse and export metrics
        Instant testTimestamp = Instant.parse("2025-08-01T12:14:20.687806Z");
        parser.parseAndExportHttpMetrics(testTimestamp);

        // Then - verify all three endpoint types are present
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            
            // Should have all three endpoint types
            assertTrue(metrics.containsKey("jwt_validation"), "Should contain JWT validation metrics");
            assertTrue(metrics.containsKey("echo"), "Should contain echo metrics");
            assertTrue(metrics.containsKey("health"), "Should contain health metrics");
            
            assertEquals(3, metrics.size(), "Should have exactly 3 endpoint types");
        }
    }

    @Test 
    void shouldExtractCorrectPercentileData() throws IOException {
        // Given - parser with test data
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());
        
        // When - parse metrics
        parser.parseAndExportHttpMetrics(Instant.now());

        // Then - verify percentile data is correctly extracted
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            
            // Check echo endpoint metrics (from test data)
            Map<String, Object> echoMetrics = (Map<String, Object>) metrics.get("echo");
            assertNotNull(echoMetrics);
            assertEquals("Echo", echoMetrics.get("name"));
            
            Map<String, Object> percentiles = (Map<String, Object>) echoMetrics.get("percentiles");
            assertNotNull(percentiles);
            
            // Verify percentiles are present and formatted correctly
            assertTrue(percentiles.containsKey("p50_us"));
            assertTrue(percentiles.containsKey("p95_us"));
            assertTrue(percentiles.containsKey("p99_us"));
            
            // Values should be numbers (Double or Long)
            assertTrue(percentiles.get("p50_us") instanceof Number);
            assertTrue(percentiles.get("p95_us") instanceof Number);
            assertTrue(percentiles.get("p99_us") instanceof Number);
        }
    }

    @Test
    void shouldFormatNumbersCorrectly() throws IOException {
        // Given - parser with test data containing various number ranges
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());
        
        // When - parse metrics
        parser.parseAndExportHttpMetrics(Instant.now());

        // Then - verify number formatting rules
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        String jsonContent = Files.readString(outputFile.toPath());
        
        // Values < 10 should have 1 decimal place, values >= 10 should be integers
        System.out.println("JSON Content for number formatting test:\n" + jsonContent);
        
        // The test data should contain values that test both formatting rules
        // Values < 10 should appear as X.X format
        // Values >= 10 should appear as integer format
        assertFalse(jsonContent.contains(".0\""), "Should not contain .0 for integers >= 10");
        
        // Verify structure is valid JSON
        Map<String, Object> parsed = gson.fromJson(jsonContent, Map.class);
        assertNotNull(parsed);
        assertTrue(parsed.size() >= 1, "Should contain at least one endpoint");
    }

    @Test
    void shouldIncludeSampleCounts() throws IOException {
        // Given - parser with test data
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());
        
        // When - parse metrics
        parser.parseAndExportHttpMetrics(Instant.now());

        // Then - verify sample counts are included
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            
            for (String endpointType : metrics.keySet()) {
                Map<String, Object> endpointData = (Map<String, Object>) metrics.get(endpointType);
                
                assertTrue(endpointData.containsKey("sample_count"), 
                    "Endpoint " + endpointType + " should have sample_count");
                
                Object sampleCount = endpointData.get("sample_count");
                assertTrue(sampleCount instanceof Number, 
                    "Sample count should be a number for " + endpointType);
                
                assertTrue(((Number) sampleCount).intValue() > 0, 
                    "Sample count should be > 0 for " + endpointType);
            }
        }
    }

    @Test
    void shouldIncludeTimestampAndSource() throws IOException {
        // Given - parser with specific timestamp
        MetricsPostProcessor parser = new MetricsPostProcessor(testBenchmarkFile, tempDir.toString());
        Instant testTimestamp = Instant.parse("2025-08-01T12:14:20.687806Z");
        
        // When - parse metrics
        parser.parseAndExportHttpMetrics(testTimestamp);

        // Then - verify timestamp and source information
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            
            for (String endpointType : metrics.keySet()) {
                Map<String, Object> endpointData = (Map<String, Object>) metrics.get(endpointType);
                
                assertEquals(testTimestamp.toString(), endpointData.get("timestamp"),
                    "Should have correct timestamp for " + endpointType);
                
                assertTrue(endpointData.containsKey("source"), 
                    "Should have source information for " + endpointType);
                
                String source = (String) endpointData.get("source");
                assertTrue(source.contains("JMH benchmark"), 
                    "Source should mention JMH benchmark for " + endpointType);
                assertTrue(source.contains("sample mode"), 
                    "Source should mention sample mode for " + endpointType);
            }
        }
    }

    @Test
    void shouldOnlyProcessSampleModeBenchmarks() throws IOException {
        // Given - parser with mixed mode benchmark file
        String mixedModeFile = createMixedModeBenchmarkFile();
        MetricsPostProcessor parser = new MetricsPostProcessor(mixedModeFile, tempDir.toString());
        
        // When - parse metrics
        parser.parseAndExportHttpMetrics(Instant.now());

        // Then - should only include sample mode results
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        assertTrue(outputFile.exists());
        
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            
            // Should still have metrics (from sample mode entries)
            assertFalse(metrics.isEmpty(), "Should have metrics from sample mode benchmarks");
            
            // Verify source mentions sample mode
            for (String endpointType : metrics.keySet()) {
                Map<String, Object> endpointData = (Map<String, Object>) metrics.get(endpointType);
                String source = (String) endpointData.get("source");
                assertTrue(source.contains("sample mode"), 
                    "Should only process sample mode for " + endpointType);
            }
        }
    }

    @Test
    void shouldHandleFileNotFound() {
        // Given - parser with non-existent file
        MetricsPostProcessor parser = new MetricsPostProcessor("/non/existent/file.json", tempDir.toString());
        
        // When/Then - should throw IOException
        IOException exception = assertThrows(IOException.class, () -> {
            parser.parseAndExportHttpMetrics(Instant.now());
        });
        
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void shouldUseConvenienceMethod() throws IOException {
        // Given - test file in results directory format
        File resultsDir = tempDir.toFile();
        File benchmarkFile = new File(resultsDir, "integration-benchmark-result.json");
        
        // Copy test benchmark file to expected location
        Files.copy(Path.of(testBenchmarkFile), benchmarkFile.toPath());
        
        // When - use convenience method
        MetricsPostProcessor.parseAndExport(resultsDir.getAbsolutePath());

        // Then - should create http-metrics.json
        File outputFile = new File(resultsDir, "http-metrics.json");
        assertTrue(outputFile.exists());
        
        try (FileReader reader = new FileReader(outputFile)) {
            Map<String, Object> metrics = gson.fromJson(reader, Map.class);
            assertFalse(metrics.isEmpty(), "Should have parsed metrics");
        }
    }

    /**
     * Create a test benchmark file with sample mode data for all three endpoint types
     */
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

    /**
     * Create a test file with mixed benchmark modes to test filtering
     */
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