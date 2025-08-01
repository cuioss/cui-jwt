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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimpleMetricsExporter
 */
class SimpleMetricsExporterTest {

    @TempDir
    Path tempDir;

    private SimpleMetricsExporter exporter;
    private Gson gson;

    @BeforeEach
    void setUp() {
        // Use test metrics fetcher that loads from test resource file
        MetricsFetcher testFetcher = new TestMetricsFetcher();
        exporter = new SimpleMetricsExporter(tempDir.toString(), testFetcher);
        gson = new GsonBuilder().create();
    }

    @Test
    void shouldExportRealJwtValidationMetrics() throws Exception {
        // Given - exporter is already configured with test data in setUp
        
        // When - export metrics using real test data
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Then - verify real metrics were extracted
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));
            
            Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get("validateJwtThroughput");
            assertNotNull(benchmarkData);
            assertTrue(benchmarkData.containsKey("steps"));
            
            Map<String, Object> steps = (Map<String, Object>) benchmarkData.get("steps");
            
            // Should have JWT validation steps from real data
            assertTrue(steps.containsKey("token_parsing"), "Should contain token_parsing step from real metrics");
            assertTrue(steps.containsKey("signature_validation"), "Should contain signature_validation step from real metrics");
            assertTrue(steps.containsKey("complete_validation"), "Should contain complete_validation step from real metrics");
            
            // Verify structure of a real step
            if (steps.containsKey("token_parsing")) {
                Map<String, Object> tokenParsing = (Map<String, Object>) steps.get("token_parsing");
                assertTrue(tokenParsing.containsKey("sample_count"));
                assertTrue(tokenParsing.containsKey("p50_us"));
                assertTrue(tokenParsing.containsKey("p95_us"));
                assertTrue(tokenParsing.containsKey("p99_us"));
                
                // Sample count should be > 0 from real data (not the synthetic 1000)
                Double sampleCount = (Double) tokenParsing.get("sample_count");
                assertTrue(sampleCount > 0 && sampleCount < 100, "Sample count should be small real value from test data, not synthetic 1000");
            }
        }
    }

    @Test
    void shouldHandleMultipleBenchmarks() throws Exception {
        // Given - exporter is already configured with test data in setUp
        
        // When - export metrics for multiple benchmarks
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());
        Thread.sleep(10); // Ensure different timestamp
        exporter.exportJwtValidationMetrics("validateJwtLatency", Instant.now());

        // Then - check aggregated file contains both benchmarks
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            
            // Should have entries for both benchmarks
            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));
            assertTrue(aggregatedData.containsKey("validateJwtLatency"));
            
            // Each should have the expected structure
            for (String benchmarkKey : aggregatedData.keySet()) {
                Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get(benchmarkKey);
                assertTrue(benchmarkData.containsKey("timestamp"));
                assertTrue(benchmarkData.containsKey("steps"));
            }
        }
    }

    @Test
    void shouldFormatNumbersCorrectly() throws Exception {
        // Given - exporter is already configured with test data in setUp
        
        // When - export metrics
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Then - check number formatting in JSON
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        // Read the JSON content as a string to verify number formatting
        String jsonContent = java.nio.file.Files.readString(aggregatedFile.toPath());

        // Verify formatting based on real data from our test file
        // Values < 10 should have one decimal place, values >= 10 should be integers
        assertTrue(jsonContent.contains("\"sample_count\": 9") || jsonContent.contains("\"sample_count\": 11") || jsonContent.contains("\"sample_count\": 12"), 
                   "Should contain real sample counts from test data");
        
        // Check that we don't have the synthetic 1000 sample count
        assertFalse(jsonContent.contains("\"sample_count\": 1000"), 
                    "Should not contain synthetic sample count of 1000");
    }

    @Test
    void shouldFormatNumbersCorrectlyWithDecimalRules() throws Exception {
        // Given - exporter with test data containing various number ranges
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Then - check number formatting follows rules: 1 decimal for <10, no decimal for >=10
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        String jsonContent = Files.readString(aggregatedFile.toPath());

        // Values < 10 should have exactly 1 decimal place
        System.out.println("JSON Content for debug:\n" + jsonContent);
        assertTrue(jsonContent.contains("\"p50_us\": 0.4"), "Values < 10 should have 1 decimal (not 0.419921875)");
        assertTrue(jsonContent.contains("\"p50_us\": 2.2"), "Values < 10 should have 1 decimal (not 2.21875)");
        assertTrue(jsonContent.contains("\"p50_us\": 7.1"), "Values < 10 should have 1 decimal (not 7.125)");
        
        // Values >= 10 should have no decimal places
        assertTrue(jsonContent.contains("\"p50_us\": 13"), "Values >= 10 should have no decimal (not 13.375)");
        assertTrue(jsonContent.contains("\"p95_us\": 38"), "Values >= 10 should have no decimal (not 37.875)");
        assertTrue(jsonContent.contains("\"p50_us\": 184"), "Values >= 10 should have no decimal (not 184.0)");
        
        // Should not contain these incorrectly formatted values
        assertFalse(jsonContent.contains("0.419921875"), "Should not contain unformatted decimal");
        assertFalse(jsonContent.contains("16.75"), "Should not contain decimal for value >= 10");
        assertFalse(jsonContent.contains("192.0"), "Should not contain .0 for whole numbers >= 10");
    }

    @Test
    void shouldOnlyIncludeJwtValidationBenchmarks() throws Exception {
        // Given - Create test exporter that returns metrics for various benchmark types
        MetricsFetcher multiTypeFetcher = new TestMetricsFetcher();
        SimpleMetricsExporter filteringExporter = new SimpleMetricsExporter(tempDir.toString(), multiTypeFetcher);
        
        // When - export metrics for different benchmark types
        filteringExporter.exportJwtValidationMetrics("JwtEchoBenchmark.echoGetThroughput", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtHealthBenchmark.healthCheckLatency", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtValidationBenchmark.validateJwtThroughput", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtValidationBenchmark.validateAccessTokenLatency", Instant.now());
        // Also test the actual benchmark name returned by getBenchmarkName()
        filteringExporter.exportJwtValidationMetrics("JwtValidation", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtEcho", Instant.now());
        filteringExporter.exportJwtValidationMetrics("JwtHealth", Instant.now());
        
        // Then - only JWT validation benchmarks should be in the output
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        String jsonContent = Files.readString(aggregatedFile.toPath());
        
        // Should only contain JWT validation benchmarks
        assertTrue(jsonContent.contains("\"validateJwtThroughput\""), "Should include JWT validation benchmark");
        assertTrue(jsonContent.contains("\"validateAccessTokenLatency\""), "Should include access token validation");
        
        // Should NOT contain echo or health benchmarks
        assertFalse(jsonContent.contains("\"JwtEcho\""), "Should not include JwtEcho benchmarks");
        assertFalse(jsonContent.contains("\"JwtHealth\""), "Should not include JwtHealth benchmarks");
        assertFalse(jsonContent.contains("\"echoGetThroughput\""), "Should not include echo methods");
        assertFalse(jsonContent.contains("\"healthCheckLatency\""), "Should not include health check methods");
        
        // Verify file only has 3 benchmarks (the validation ones)
        Map<String, Object> aggregatedData = gson.fromJson(new FileReader(aggregatedFile), Map.class);
        assertEquals(3, aggregatedData.size(), "Should only have 3 JWT validation benchmarks (2 method names + 1 class name)");
    }

    @Test
    void shouldIncludeHttpMetricsInOutput() throws Exception {
        // Given - exporter with test data that includes HTTP metrics
        exporter.exportJwtValidationMetrics("JwtValidationBenchmark.validateJwtThroughput", Instant.now());

        // Then - check HTTP metrics are included
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        Map<String, Object> aggregatedData = gson.fromJson(new FileReader(aggregatedFile), Map.class);
        
        Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get("validateJwtThroughput");
        assertNotNull(benchmarkData.get("bearer_token_producer_metrics"), "Should include bearer_token_producer_metrics section");
        
        Map<String, Object> httpMetrics = (Map<String, Object>) benchmarkData.get("bearer_token_producer_metrics");
        
        // Should have all HTTP measurement types
        assertTrue(httpMetrics.containsKey("token_extraction"), "Should include token_extraction");
        assertTrue(httpMetrics.containsKey("header_extraction"), "Should include header_extraction");
        assertTrue(httpMetrics.containsKey("authorization_check"), "Should include authorization_check");
        assertTrue(httpMetrics.containsKey("request_processing"), "Should include request_processing");
        assertTrue(httpMetrics.containsKey("response_formatting"), "Should include response_formatting");
        
        // Verify structure of HTTP metrics
        Map<String, Object> tokenExtraction = (Map<String, Object>) httpMetrics.get("token_extraction");
        assertTrue(tokenExtraction.containsKey("sample_count"));
        assertTrue(tokenExtraction.containsKey("p50_us"));
        assertTrue(tokenExtraction.containsKey("p95_us"));
        assertTrue(tokenExtraction.containsKey("p99_us"));
        
        // Verify formatting rules apply to HTTP metrics too
        Double p50Value = (Double) tokenExtraction.get("p50_us");
        if (p50Value < 10) {
            String jsonContent = Files.readString(aggregatedFile.toPath());
            // Check that value has 1 decimal place
            assertTrue(jsonContent.contains("\"p50_us\": 8.2") || jsonContent.contains("\"p50_us\": 2.1"), "HTTP metrics < 10 should have 1 decimal");
        } else {
            String jsonContent = Files.readString(aggregatedFile.toPath());
            // Check that value has no decimal
            assertTrue(jsonContent.contains("\"p50_us\": 13") || jsonContent.contains("\"p50_us\": 34"), "HTTP metrics >= 10 should have no decimal");
        }
    }

    @Test
    void shouldHandleEmptyMetricsData() throws Exception {
        // Given - exporter with empty metrics fetcher
        MetricsFetcher emptyFetcher = () -> new HashMap<>();
        SimpleMetricsExporter emptyExporter = new SimpleMetricsExporter(tempDir.toString(), emptyFetcher);
        
        // When - export metrics with no data
        emptyExporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Then - should create file with empty steps
        File aggregatedFile = new File(tempDir.toFile(), "integration-jwt-validation-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));
            
            Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get("validateJwtThroughput");
            Map<String, Object> steps = (Map<String, Object>) benchmarkData.get("steps");
            
            // Should have empty steps when no metrics data
            assertTrue(steps.isEmpty(), "Steps should be empty when no metrics data available");
        }
    }
    
    /**
     * Test metrics fetcher that loads data from test resource files
     */
    private static class TestMetricsFetcher implements MetricsFetcher {
        
        @Override
        public Map<String, Double> fetchMetrics() {
            try {
                String testDataPath = "src/test/resources/sample-jwt-validation-metrics.txt";
                String content = java.nio.file.Files.readString(java.nio.file.Paths.get(testDataPath));
                
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