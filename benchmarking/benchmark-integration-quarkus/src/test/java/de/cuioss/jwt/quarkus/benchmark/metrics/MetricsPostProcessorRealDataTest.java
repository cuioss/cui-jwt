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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MetricsPostProcessor using real benchmark data from integration-benchmark-result.json
 */
class MetricsPostProcessorRealDataTest {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsPostProcessorRealDataTest.class);

    @TempDir
    Path tempDir;

    private Gson gson;
    private String realBenchmarkFile;

    @BeforeEach void setUp() {
        gson = new GsonBuilder().create();
        realBenchmarkFile = "src/test/resources/integration-benchmark-result.json";
    }

    @Test void shouldParseRealBenchmarkData() throws IOException {
        // Given - parser with real benchmark data
        MetricsPostProcessor parser = new MetricsPostProcessor(realBenchmarkFile, tempDir.toString());

        // When - parse real benchmark results
        String jsonContent = Files.readString(Path.of(realBenchmarkFile));
        Map<String, MetricsPostProcessor.HttpEndpointMetrics> endpointMetrics = parser.parseBenchmarkResults(jsonContent);

        // Then - should parse expected endpoint types from real data
        assertFalse(endpointMetrics.isEmpty(), "Should parse metrics from real benchmark data");

        // Based on the real data, we expect Health and JWT Validation endpoints
        assertTrue(endpointMetrics.containsKey("health"), "Should contain health metrics from real data");
        assertTrue(endpointMetrics.containsKey("jwt_validation"), "Should contain JWT validation metrics from real data");

        // Verify health endpoint metrics
        MetricsPostProcessor.HttpEndpointMetrics healthMetrics = endpointMetrics.get("health");
        assertNotNull(healthMetrics);
        assertEquals("Health Check", healthMetrics.getDisplayName());
        assertTrue(healthMetrics.getSampleCount() > 0, "Health should have sample count > 0");
        assertTrue(healthMetrics.getP50() > 0, "Health p50 should be > 0");
        assertTrue(healthMetrics.getP95() > 0, "Health p95 should be > 0");
        assertTrue(healthMetrics.getP99() > 0, "Health p99 should be > 0");
        assertTrue(healthMetrics.getSourceBenchmark().contains("JwtHealthBenchmark"));

        // Verify JWT validation endpoint metrics
        MetricsPostProcessor.HttpEndpointMetrics jwtMetrics = endpointMetrics.get("jwt_validation");
        assertNotNull(jwtMetrics);
        assertEquals("JWT Validation", jwtMetrics.getDisplayName());
        assertTrue(jwtMetrics.getSampleCount() > 0, "JWT validation should have sample count > 0");
        assertTrue(jwtMetrics.getP50() > 0, "JWT validation p50 should be > 0");
        assertTrue(jwtMetrics.getP95() > 0, "JWT validation p95 should be > 0");
        assertTrue(jwtMetrics.getP99() > 0, "JWT validation p99 should be > 0");
        assertTrue(jwtMetrics.getSourceBenchmark().contains("JwtValidationBenchmark"));

        LOGGER.debug("Health metrics - samples: %s, p50: %sms, p95: %sms, p99: %sms",
                healthMetrics.getSampleCount(), healthMetrics.getP50(), healthMetrics.getP95(), healthMetrics.getP99());
        LOGGER.debug("JWT Validation metrics - samples: %s, p50: %sms, p95: %sms, p99: %sms",
                jwtMetrics.getSampleCount(), jwtMetrics.getP50(), jwtMetrics.getP95(), jwtMetrics.getP99());
    }

    @Test void shouldExportRealDataToJson() throws IOException {
        // Given - parser with real benchmark data
        MetricsPostProcessor parser = new MetricsPostProcessor(realBenchmarkFile, tempDir.toString());
        Instant testTimestamp = Instant.parse("2025-08-01T12:14:20.687806Z");

        // When - parse and export real benchmark results
        parser.parseAndExportHttpMetrics(testTimestamp);

        // Then - should create http-metrics.json with real data
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        assertTrue(outputFile.exists());

        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> metrics = (Map<String, Object>) gson.fromJson(reader, Map.class);

            // Should contain endpoints from real data
            assertTrue(metrics.containsKey("health"), "Should contain health from real data");
            assertTrue(metrics.containsKey("jwt_validation"), "Should contain JWT validation from real data");

            // Verify health data structure
            @SuppressWarnings("unchecked") Map<String, Object> healthData = (Map<String, Object>) metrics.get("health");
            assertEquals("Health Check", healthData.get("name"));
            assertEquals(testTimestamp.toString(), healthData.get("timestamp"));
            assertTrue(healthData.containsKey("sample_count"));
            assertTrue(healthData.containsKey("percentiles"));
            assertTrue(((String) healthData.get("source")).contains("JMH benchmark"));
        }
    }

    @Test void shouldApplyCorrectNumberFormattingToRealData() throws IOException {
        // Given - parser with real benchmark data
        MetricsPostProcessor parser = new MetricsPostProcessor(realBenchmarkFile, tempDir.toString());

        // When - parse and export real benchmark results
        parser.parseAndExportHttpMetrics(Instant.now());

        // Then - should apply number formatting rules to real data
        File outputFile = new File(tempDir.toFile(), "http-metrics.json");
        String jsonContent = Files.readString(outputFile.toPath());

        LOGGER.debug("Real data JSON content:\n%s", jsonContent);

        // Parse to verify structure
        @SuppressWarnings("unchecked") Map<String, Object> metrics = (Map<String, Object>) gson.fromJson(jsonContent, Map.class);
        assertFalse(metrics.isEmpty(), "Should have parsed real data");

        // Verify number formatting in JSON content
        // Values < 10 should have 1 decimal place, values >= 10 should be integers
        assertFalse(jsonContent.contains(".0\""), "Should not contain .0 for integers >= 10");

        // Verify that all percentiles are properly formatted numbers
        for (String endpointType : metrics.keySet()) {
            @SuppressWarnings("unchecked") Map<String, Object> endpointData = (Map<String, Object>) metrics.get(endpointType);
            @SuppressWarnings("unchecked") Map<String, Object> percentiles = (Map<String, Object>) endpointData.get("percentiles");

            for (String percentile : percentiles.keySet()) {
                Object value = percentiles.get(percentile);
                assertInstanceOf(Number.class, value, "Percentile " + percentile + " for " + endpointType + " should be a number");

                double numValue = ((Number) value).doubleValue();
                assertTrue(numValue > 0,
                        "Percentile " + percentile + " for " + endpointType + " should be > 0");
            }
        }
    }

    @Test void shouldHandleSpecificRealDataValues() throws IOException {
        // Given - parser with real benchmark data
        MetricsPostProcessor parser = new MetricsPostProcessor(realBenchmarkFile, tempDir.toString());

        // When - parse real benchmark results
        String jsonContent = Files.readString(Path.of(realBenchmarkFile));
        Map<String, MetricsPostProcessor.HttpEndpointMetrics> endpointMetrics = parser.parseBenchmarkResults(jsonContent);

        // Then - verify specific values are reasonable for HTTP roundtrip times
        if (endpointMetrics.containsKey("health")) {
            MetricsPostProcessor.HttpEndpointMetrics healthMetrics = endpointMetrics.get("health");

            // Health checks should be fast
            assertTrue(healthMetrics.getP50() > 1.0 && healthMetrics.getP50() < 50.0,
                    "Health p50 should be reasonable health check time (1-50ms): " + healthMetrics.getP50());
            assertTrue(healthMetrics.getP95() > healthMetrics.getP50(),
                    "Health p95 should be > p50");
            assertTrue(healthMetrics.getP99() > healthMetrics.getP95(),
                    "Health p99 should be > p95");
        }
    }

    @Test void shouldOnlyProcessSampleModeFromRealData() throws IOException {
        // Given - parser with real benchmark data (contains multiple modes)
        MetricsPostProcessor parser = new MetricsPostProcessor(realBenchmarkFile, tempDir.toString());

        // When - parse real benchmark results
        String jsonContent = Files.readString(Path.of(realBenchmarkFile));
        Map<String, MetricsPostProcessor.HttpEndpointMetrics> endpointMetrics = parser.parseBenchmarkResults(jsonContent);

        // Then - should only process sample mode benchmarks
        for (Map.Entry<String, MetricsPostProcessor.HttpEndpointMetrics> entry : endpointMetrics.entrySet()) {
            String endpointType = entry.getKey();
            MetricsPostProcessor.HttpEndpointMetrics metrics = entry.getValue();

            // Sample count should be > 0 for sample mode benchmarks
            assertTrue(metrics.getSampleCount() > 0,
                    "Sample count should be > 0 for " + endpointType + " from sample mode");

            // Percentiles should be reasonable values (not throughput ops/sec values)
            assertTrue(metrics.getP50() < 1000,
                    "P50 should be reasonable latency (ms), not throughput for " + endpointType);
        }
    }

    @Test void shouldCountSamplesCorrectlyFromRealData() throws IOException {
        // Given - parser with real benchmark data
        MetricsPostProcessor parser = new MetricsPostProcessor(realBenchmarkFile, tempDir.toString());

        // When - parse real benchmark results
        String jsonContent = Files.readString(Path.of(realBenchmarkFile));
        Map<String, MetricsPostProcessor.HttpEndpointMetrics> endpointMetrics = parser.parseBenchmarkResults(jsonContent);

        // Then - verify sample counts are extracted from rawDataHistogram
        for (Map.Entry<String, MetricsPostProcessor.HttpEndpointMetrics> entry : endpointMetrics.entrySet()) {
            String endpointType = entry.getKey();
            MetricsPostProcessor.HttpEndpointMetrics metrics = entry.getValue();

            LOGGER.debug("Endpoint: %s, Sample Count: %s, Source: %s",
                    endpointType, metrics.getSampleCount(), metrics.getSourceBenchmark());

            // Sample counts should be realistic for JMH benchmarks
            assertTrue(metrics.getSampleCount() >= 10,
                    "Sample count should be >= 10 for " + endpointType + " but was " + metrics.getSampleCount());
            assertTrue(metrics.getSampleCount() <= 10000,
                    "Sample count should be <= 10000 for " + endpointType + " but was " + metrics.getSampleCount());
        }
    }

    @Test void shouldUseCorrectEndpointDisplayNames() throws IOException {
        // Given - parser with real benchmark data
        MetricsPostProcessor parser = new MetricsPostProcessor(realBenchmarkFile, tempDir.toString());

        // When - parse real benchmark results
        String jsonContent = Files.readString(Path.of(realBenchmarkFile));
        Map<String, MetricsPostProcessor.HttpEndpointMetrics> endpointMetrics = parser.parseBenchmarkResults(jsonContent);

        // Then - verify display names are user-friendly
        if (endpointMetrics.containsKey("health")) {
            assertEquals("Health Check", endpointMetrics.get("health").getDisplayName());
        }
        if (endpointMetrics.containsKey("jwt_validation")) {
            assertEquals("JWT Validation", endpointMetrics.get("jwt_validation").getDisplayName());
        }
    }
}