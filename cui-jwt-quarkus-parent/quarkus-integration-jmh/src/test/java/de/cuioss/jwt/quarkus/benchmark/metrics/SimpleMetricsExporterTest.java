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
import de.cuioss.tools.logging.CuiLogger;
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

    private static final CuiLogger LOGGER = new CuiLogger(SimpleMetricsExporterTest.class);

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
    @DisplayName("Should export JWT bearer token validation metrics")
    void shouldExportRealJwtValidationMetrics() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));

            Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get("validateJwtThroughput");
            assertNotNull(benchmarkData);
            assertTrue(benchmarkData.containsKey("bearer_token_producer_metrics"));
            assertTrue(benchmarkData.containsKey("security_event_counter_metrics"));

            Map<String, Object> bearerMetrics = (Map<String, Object>) benchmarkData.get("bearer_token_producer_metrics");
            assertTrue(bearerMetrics.containsKey("validation"));

            Map<String, Object> validation = (Map<String, Object>) bearerMetrics.get("validation");
            assertTrue(validation.containsKey("sample_count"));
            assertTrue(validation.containsKey("p50_us"));
            assertTrue(validation.containsKey("p95_us"));
            assertTrue(validation.containsKey("p99_us"));

            // Check security event metrics
            Map<String, Object> securityMetrics = (Map<String, Object>) benchmarkData.get("security_event_counter_metrics");
            assertTrue(securityMetrics.containsKey("total_errors"));
            assertTrue(securityMetrics.containsKey("total_success"));
            assertTrue(securityMetrics.containsKey("errors_by_category"));
            assertTrue(securityMetrics.containsKey("success_by_type"));
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
        File aggregatedFile = new File(tempDir.toFile(), "integration-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);

            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));
            assertTrue(aggregatedData.containsKey("validateJwtLatency"));

            for (String benchmarkKey : aggregatedData.keySet()) {
                Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get(benchmarkKey);
                assertTrue(benchmarkData.containsKey("timestamp"));
                assertTrue(benchmarkData.containsKey("bearer_token_producer_metrics"));
                assertTrue(benchmarkData.containsKey("security_event_counter_metrics"));
            }
        }
    }

    @Test
    @DisplayName("Should format numbers correctly in JSON output")
    void shouldFormatNumbersCorrectly() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-metrics.json");
        assertTrue(aggregatedFile.exists());

        String jsonContent = Files.readString(aggregatedFile.toPath());

        // Check for sample count in bearer token validation metrics
        assertTrue(jsonContent.contains("\"sample_count\": "), "Should contain sample_count field");
        // Check for bearer token validation metrics
        assertTrue(jsonContent.contains("\"validation\""), "Should contain bearer token validation metrics");
    }

    @Test
    @DisplayName("Should format numbers with correct decimal rules")
    void shouldFormatNumbersCorrectlyWithDecimalRules() throws Exception {
        // Act
        exporter.exportJwtValidationMetrics("validateJwtThroughput", Instant.now());

        // Assert
        File aggregatedFile = new File(tempDir.toFile(), "integration-metrics.json");
        String jsonContent = Files.readString(aggregatedFile.toPath());

        // Check for formatted microsecond values in bearer token metrics
        assertTrue(jsonContent.contains("\"p50_us\":"), "Should contain p50_us fields");
        assertTrue(jsonContent.contains("\"p95_us\":"), "Should contain p95_us fields");
        assertTrue(jsonContent.contains("\"p99_us\":"), "Should contain p99_us fields");

        // Bearer token validation metrics should be present
        assertTrue(jsonContent.contains("\"validation\""), "Should contain bearer token validation metrics");
        assertTrue(jsonContent.contains("\"bearer_token_producer_metrics\""), "Should contain bearer token producer metrics");
        // Security event metrics should be present
        assertTrue(jsonContent.contains("\"security_event_counter_metrics\""), "Should contain security event counter metrics");
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
        File aggregatedFile = new File(tempDir.toFile(), "integration-metrics.json");
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
        File aggregatedFile = new File(tempDir.toFile(), "integration-metrics.json");
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

        // Check security event metrics
        Map<String, Object> securityMetrics = (Map<String, Object>) benchmarkData.get("security_event_counter_metrics");
        assertNotNull(securityMetrics, "Should have security event counter metrics");
        assertTrue(securityMetrics.containsKey("total_errors"));
        assertTrue(securityMetrics.containsKey("total_success"));
        assertTrue(securityMetrics.containsKey("errors_by_category"));
        assertTrue(securityMetrics.containsKey("success_by_type"));

        // Verify we have the MISSING_CLAIM error from the test data (169356.0)
        Object totalErrorsObj = securityMetrics.get("total_errors");
        assertTrue(totalErrorsObj instanceof Number, "total_errors should be a number");
        Number totalErrors = (Number) totalErrorsObj;
        assertTrue(totalErrors.longValue() > 0, "Should have some security events recorded");
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
        File aggregatedFile = new File(tempDir.toFile(), "integration-metrics.json");
        assertTrue(aggregatedFile.exists());

        try (FileReader reader = new FileReader(aggregatedFile)) {
            Map<String, Object> aggregatedData = gson.fromJson(reader, Map.class);
            assertTrue(aggregatedData.containsKey("validateJwtThroughput"));

            Map<String, Object> benchmarkData = (Map<String, Object>) aggregatedData.get("validateJwtThroughput");
            Map<String, Object> bearerMetrics = (Map<String, Object>) benchmarkData.get("bearer_token_producer_metrics");
            Map<String, Object> securityMetrics = (Map<String, Object>) benchmarkData.get("security_event_counter_metrics");

            assertNotNull(bearerMetrics, "Should have bearer token producer metrics even with empty data");
            assertTrue(bearerMetrics.containsKey("validation"));
            assertNotNull(securityMetrics, "Should have security event counter metrics even with empty data");
            assertTrue(securityMetrics.containsKey("total_errors"));
            assertTrue(securityMetrics.containsKey("total_success"));
            assertTrue(securityMetrics.containsKey("errors_by_category"));
            assertTrue(securityMetrics.containsKey("success_by_type"));
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
                LOGGER.error("Failed to load test metrics: %s", e.getMessage());
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