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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleMetricsExporterTest {

    @TempDir
    Path tempDir;

    private SimpleMetricsExporter exporter;
    private Gson gson;

    @BeforeEach
    void setUp() {
        MetricsFetcher testFetcher = new TestMetricsFetcher();
        exporter = new SimpleMetricsExporter(tempDir.toString(), testFetcher);
        gson = new GsonBuilder().create();
    }

    @Test
    @DisplayName("Should export real JWT validation metrics")
    void shouldExportRealJwtValidationMetrics() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));

            Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get("validateJwtThroughput");
            assertNotNull(benchmarkData);
            assertTrue(benchmarkData.containsKey("steps"));

            Map<String, Object> steps = (Map<String, Object>) benchmarkData.get("steps");

            assertTrue(steps.containsKey("token_parsing"));
            assertTrue(steps.containsKey("signature_validation"));
            assertTrue(steps.containsKey("complete_validation"));

            if (steps.containsKey("token_parsing")) {
                Map<String, Object> tokenParsing = (Map<String, Object>) steps.get("token_parsing");
                assertTrue(tokenParsing.containsKey("sample_count"));
                assertTrue(tokenParsing.containsKey("p50_us"));
                assertTrue(tokenParsing.containsKey("p95_us"));
                assertTrue(tokenParsing.containsKey("p99_us"));

                Double sampleCount = (Double) tokenParsing.get("sample_count");
                assertTrue(sampleCount > 0 && sampleCount < 100);
            }
        }
    }

    @Test
    @DisplayName("Should handle multiple benchmarks correctly")
    void shouldHandleMultipleBenchmarks() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());
        Thread.sleep(10);
        exporter.exportJwtValidationMetrics("validateJwtLatency", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);

            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));
            assertTrue(aggregatedData.containsKey("validateJwtLatency"));

            for (String benchmarkKey : aggregatedData.keySet()) {
                Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get(benchmarkKey);
                assertTrue(benchmarkData.containsKey("timestamp"));
                assertTrue(benchmarkData.containsKey("steps"));
            }
        }
    }

    @Test
    @DisplayName("Should format numbers correctly in JSON output")
    void shouldFormatNumbersCorrectly() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        String jsonContent = Files.readString(aggregatedFile.toPath());

        assertTrue(jsonContent.contains("\"sample_count\": 9") || jsonContent.contains("\"sample_count\": 11") || jsonContent.contains("\"sample_count\": 12"));
        assertFalse(jsonContent.contains("\"sample_count\": 1000"));
    }

    @Test
    @DisplayName("Should format numbers with correct decimal rules")
    void shouldFormatNumbersCorrectlyWithDecimalRules() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        String jsonContent = Files.readString(aggregatedFile.toPath());

        assertTrue(jsonContent.contains("\"p50_us\": 0.4"));
        assertTrue(jsonContent.contains("\"p50_us\": 2.2"));
        assertTrue(jsonContent.contains("\"p50_us\": 7.1"));

        assertTrue(jsonContent.contains("\"p50_us\": 13"));
        assertTrue(jsonContent.contains("\"p95_us\": 38"));
        assertTrue(jsonContent.contains("\"p50_us\": 184"));

        assertFalse(jsonContent.contains("0.419921875"));
        assertFalse(jsonContent.contains("16.75"));
        assertFalse(jsonContent.contains("192.0"));
    }

    @Test
    @DisplayName("Should only include JWT validation benchmarks")
    void shouldOnlyIncludeJwtValidationBenchmarks() throws Exception {
        // Arrange
        MetricsFetcher multiTypeFetcher = new TestMetricsFetcher();
        SimpleMetricsExporter filteringExporter = new SimpleMetricsExporter(tempDir.toString(), multiTypeFetcher);

        // Act
        filteringExporter.exportJwtValidationMetrics("JwtEchoBenchmark.echoGetThroughput", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtHealthBenchmark.healthCheckLatency", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtValidationBenchmark.validateJwtThroughput", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtValidationBenchmark.validateAccessTokenLatency", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtValidation", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtEcho", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtHealth", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        String jsonContent = Files.readString(aggregatedFile.toPath());

        assertTrue(jsonContent.contains("\"validateJwtThroughput\""));
        assertTrue(jsonContent.contains("\"validateAccessTokenLatency\""));

        assertFalse(jsonContent.contains("\"JwtEcho\""));
        assertFalse(jsonContent.contains("\"JwtHealth\""));
        assertFalse(jsonContent.contains("\"echoGetThroughput\""));
        assertFalse(jsonContent.contains("\"healthCheckLatency\""));

        Map<String, Object> aggregatedData = gson.fromJson(new FileReader(aggregatedFile), Map.class);
        assertEquals(3, aggregatedData.size());
    }

    @Test
    @DisplayName("Should include HTTP metrics in output")
    void shouldIncludeHttpMetricsInOutput() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("JwtValidationBenchmark.validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        Map<String, Object> aggregatedData = gson.fromJson(new FileReader(aggregatedFile), Map.class);

        Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get("validateJwtThroughput");
        assertNotNull(benchmarkData.get("bearer_token_producer_metrics"));

        Map<String, Object> httpMetrics = (Map<String, Object>) benchmarkData.get("bearer_token_producer_metrics");

        assertTrue(httpMetrics.containsKey("token_extraction"));
        assertTrue(httpMetrics.containsKey("header_extraction"));
        assertTrue(httpMetrics.containsKey("authorization_check"));
        assertTrue(httpMetrics.containsKey("request_processing"));
        assertTrue(httpMetrics.containsKey("response_formatting"));

        Map<String, Object> tokenExtraction = (Map<String, Object>) httpMetrics.get("token_extraction");
        assertTrue(tokenExtraction.containsKey("sample_count"));
        assertTrue(tokenExtraction.containsKey("p50_us"));
        assertTrue(tokenExtraction.containsKey("p95_us"));
        assertTrue(tokenExtraction.containsKey("p99_us"));

        Double p50Value = (Double) tokenExtraction.get("p50_us");
        if (p50Value < 10) {
            String jsonContent = Files.readString(aggregatedFile.toPath());
            assertTrue(jsonContent.contains("\"p50_us\": 8.2") || jsonContent.contains("\"p50_us\": 2.1"));
        } else {
            String jsonContent = Files.readString(aggregatedFile.toPath());
            assertTrue(jsonContent.contains("\"p50_us\": 13") || jsonContent.contains("\"p50_us\": 34"));
        }
    }

    @Test
    @DisplayName("Should handle empty metrics data gracefully")
    void shouldHandleEmptyMetricsData() throws Exception {
        // Arrange
        MetricsFetcher emptyFetcher = () -> new HashMap<>();
        SimpleMetricsExporter emptyExporter = new SimpleMetricsExporter(tempDir.toString(), emptyFetcher);

        // Act
        emptyExporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));

            Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get("validateJwtThroughput");
            Map<String, Object> steps = (Map<String, Object>) benchmarkData.get("steps");

            assertTrue(steps.isEmpty());
        }
    }

    private static class TestMetricsFetcher implements MetricsFetcher {

        @Override
        public Map<String, Double> fetchMetrics() {
            try {
                String testDataPath = "src/test/resources/sample-jwt-validation-metrics.txt";
                String content = Files.readString(Path.of(testDataPath));

                Map<String, Double> metrics = new HashMap<>();
                parseQuarkusMetrics(content, metrics);
                return metrics;

            } catch (Exception e) {
                System.err.println("Failed to load test metrics: " + e.getMessage());
                return new HashMap<>();
            }
        }

        private void parseQuarkusMetrics(String responseBody, Map<String, Double> results) {
            String[] lines = responseBody.split("\n");
            for (String line : lines) {
                line = line.trim();

                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                int spaceIndex = line.lastIndexOf(' ');
                if (spaceIndex > 0) {
                    String metricPart = line.substring(0, spaceIndex);
                    String valuePart = line.substring(spaceIndex + 1);

                    try {
                        double value = Double.parseDouble(valuePart);
                        results.put(metricPart, value);
                    } catch (NumberFormatException e) {
                        // Ignore invalid metrics
                    }
                }
            }
        }
    }
}