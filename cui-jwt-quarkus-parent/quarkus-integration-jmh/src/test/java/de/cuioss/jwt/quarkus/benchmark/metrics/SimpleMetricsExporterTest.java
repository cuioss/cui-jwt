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
                assertTrue(sampleCount > 0, "Sample count should be greater than 0");
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

        // Check for sample counts from real data (now much larger values)
        assertTrue(jsonContent.contains("\"sample_count\": "), "Should contain sample_count field");
        // The actual values in our test data are 114, 132, 384, etc.
        assertTrue(jsonContent.contains("\"sample_count\": 114") || 
                   jsonContent.contains("\"sample_count\": 132") || 
                   jsonContent.contains("\"sample_count\": 384"), 
                   "Should contain actual sample counts from test data");
    }

    @Test
    @DisplayName("Should format numbers with correct decimal rules")
    void shouldFormatNumbersCorrectlyWithDecimalRules() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        String jsonContent = Files.readString(aggregatedFile.toPath());

        // Check for formatted microsecond values from real data
        // Values should be properly formatted (no excessive decimal places)
        assertTrue(jsonContent.contains("\"p50_us\":"), "Should contain p50_us fields");
        assertTrue(jsonContent.contains("\"p95_us\":"), "Should contain p95_us fields");
        assertTrue(jsonContent.contains("\"p99_us\":"), "Should contain p99_us fields");
        
        // Check that we have proper formatting (looking for some actual values)
        // From the test data: token_parsing p50=18.875, p95=127.875, etc.
        assertTrue(jsonContent.contains("\"p50_us\": 19") || // token_parsing p50
                   jsonContent.contains("\"p50_us\": 32") || // complete_validation p50  
                   jsonContent.contains("\"p50_us\": 0.1"),  // token_format_check p50
                   "Should contain properly formatted p50 values");
        
        // Bearer token validation metrics should also be present
        assertTrue(jsonContent.contains("\"validation\""), "Should contain bearer token validation metrics");
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

        // Check for new @Timed metrics structure - validation only (extraction removed)
        assertTrue(httpMetrics.containsKey("validation"), 
                   "Should contain validation metric");

        // Check validation metric structure
        Map<String, Object> validation = (Map<String, Object>) httpMetrics.get("validation");
        assertTrue(validation.containsKey("sample_count"));
        assertTrue(validation.containsKey("p50_us"));
        assertTrue(validation.containsKey("p95_us"));
        assertTrue(validation.containsKey("p99_us"));
        
        // Verify that we have actual data (from the new test file with real metrics)
        Double sampleCount = (Double) validation.get("sample_count");
        assertTrue(sampleCount > 0, "Bearer token validation should have sample count > 0");
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