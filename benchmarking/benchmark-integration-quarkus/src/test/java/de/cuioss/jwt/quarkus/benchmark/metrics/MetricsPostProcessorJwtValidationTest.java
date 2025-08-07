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

import com.google.gson.*;
import de.cuioss.tools.logging.CuiLogger;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MetricsPostProcessor with JWT validation sample mode benchmarks
 */
class MetricsPostProcessorJwtValidationTest {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsPostProcessorJwtValidationTest.class);

    @TempDir
    Path tempDir;

    private Gson gson;
    private String jwtValidationBenchmarkFile;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder().create();
        jwtValidationBenchmarkFile = "src/test/resources/sample-jwt-validation-benchmark.json";
    }

    @Test
    void shouldParseJwtValidationSampleModeData() throws IOException {
        // Given - parser with JWT validation sample mode data
        MetricsPostProcessor parser = new MetricsPostProcessor(jwtValidationBenchmarkFile, tempDir.toString());

        // When - parse JWT validation benchmark results
        String jsonContent = Files.readString(Path.of(jwtValidationBenchmarkFile));
        Map<String, MetricsPostProcessor.HttpEndpointMetrics> endpointMetrics = parser.parseBenchmarkResults(jsonContent);

        // Then - should parse JWT validation metrics
        assertTrue(endpointMetrics.containsKey("jwt_validation"), "Should contain JWT validation metrics");
        assertEquals(1, endpointMetrics.size(), "Should only contain JWT validation endpoint");

        // Verify JWT validation endpoint metrics
        MetricsPostProcessor.HttpEndpointMetrics jwtMetrics = endpointMetrics.get("jwt_validation");
        assertNotNull(jwtMetrics);
        assertEquals("JWT Validation", jwtMetrics.getDisplayName());
        assertTrue(jwtMetrics.getSampleCount() > 0, "JWT validation should have sample count > 0");
        assertTrue(jwtMetrics.getP50() > 0, "JWT validation p50 should be > 0");
        assertTrue(jwtMetrics.getP95() > 0, "JWT validation p95 should be > 0");
        assertTrue(jwtMetrics.getP99() > 0, "JWT validation p99 should be > 0");
        assertTrue(jwtMetrics.getSourceBenchmark().contains("JwtValidationBenchmark"));

        LOGGER.debug("JWT Validation metrics - samples: %s, p50: %sms, p95: %sms, p99: %sms",
                jwtMetrics.getSampleCount(), jwtMetrics.getP50(), jwtMetrics.getP95(), jwtMetrics.getP99());
    }

    @Test
    void shouldExportCompleteHttpMetricsWithJwtValidation() throws IOException {
        // Given - combined benchmark data with all three endpoint types
        String combinedBenchmarkData = createCombinedBenchmarkData();
        MetricsPostProcessor parser = new MetricsPostProcessor("dummy", tempDir.toString());

        // When - parse combined benchmark results
        Map<String, MetricsPostProcessor.HttpEndpointMetrics> endpointMetrics = parser.parseBenchmarkResults(combinedBenchmarkData);

        // Then - should contain all endpoint types
        assertEquals(2, endpointMetrics.size(), "Should contain all endpoint types");
        assertTrue(endpointMetrics.containsKey("jwt_validation"), "Should contain JWT validation");
        assertTrue(endpointMetrics.containsKey("health"), "Should contain health");

        // Generate output file
        File outputFile = new File(tempDir.toFile(), "complete-http-metrics.json");
        MetricsPostProcessor exportParser = new MetricsPostProcessor("dummy", tempDir.toString()) {
            @Override
            public Map<String, HttpEndpointMetrics> parseBenchmarkResults(FileReader reader) {
                return endpointMetrics; // Use our pre-parsed data
            }
        };

        Map<String, Object> output = new LinkedHashMap<>();
        Instant timestamp = Instant.now();

        for (Map.Entry<String, MetricsPostProcessor.HttpEndpointMetrics> entry : endpointMetrics.entrySet()) {
            String endpointType = entry.getKey();
            MetricsPostProcessor.HttpEndpointMetrics metrics = entry.getValue();

            Map<String, Object> endpointData = new LinkedHashMap<>();
            endpointData.put("name", metrics.getDisplayName());
            endpointData.put("timestamp", timestamp.toString());
            endpointData.put("sample_count", metrics.getSampleCount());

            Map<String, Object> percentiles = new LinkedHashMap<>();
            percentiles.put("p50_ms", formatNumber(metrics.getP50()));
            percentiles.put("p95_ms", formatNumber(metrics.getP95()));
            percentiles.put("p99_ms", formatNumber(metrics.getP99()));
            endpointData.put("percentiles", percentiles);

            endpointData.put("source", "JMH benchmark - " + metrics.getSourceBenchmark() + " sample mode");

            output.put(endpointType, endpointData);
        }

        // Write to file
        try (FileWriter writer = new FileWriter(outputFile)) {
            gson.toJson(output, writer);
        }

        // Verify complete output
        try (FileReader reader = new FileReader(outputFile)) {
            @SuppressWarnings("unchecked") Map<String, Object> completeMetrics = (Map<String, Object>) gson.fromJson(reader, Map.class);

            // Should have all endpoint types
            assertTrue(completeMetrics.containsKey("jwt_validation"));
            assertTrue(completeMetrics.containsKey("health"));

            // Verify JWT validation data
            @SuppressWarnings("unchecked") Map<String, Object> jwtData = (Map<String, Object>) completeMetrics.get("jwt_validation");
            assertEquals("JWT Validation", jwtData.get("name"));
            assertTrue(jwtData.containsKey("percentiles"));

            @SuppressWarnings("unchecked") Map<String, Object> jwtPercentiles = (Map<String, Object>) jwtData.get("percentiles");
            assertTrue(jwtPercentiles.containsKey("p50_ms"));
            assertTrue(jwtPercentiles.containsKey("p95_ms"));
            assertTrue(jwtPercentiles.containsKey("p99_ms"));

            LOGGER.debug("Complete HTTP metrics generated with JWT validation data:");
            LOGGER.debug(Files.readString(outputFile.toPath()));
        }
    }

    @Test
    void shouldHandleJwtValidationPercentileValues() throws IOException {
        // Given - parser with JWT validation data
        MetricsPostProcessor parser = new MetricsPostProcessor(jwtValidationBenchmarkFile, tempDir.toString());

        // When - parse JWT validation results
        String jsonContent = Files.readString(Path.of(jwtValidationBenchmarkFile));
        Map<String, MetricsPostProcessor.HttpEndpointMetrics> endpointMetrics = parser.parseBenchmarkResults(jsonContent);

        // Then - verify JWT validation percentiles are reasonable for HTTP roundtrip times
        MetricsPostProcessor.HttpEndpointMetrics jwtMetrics = endpointMetrics.get("jwt_validation");
        assertNotNull(jwtMetrics);

        // JWT validation should be slower than health checks but faster than complex operations
        assertTrue(jwtMetrics.getP50() > 5.0 && jwtMetrics.getP50() < 100.0,
                "JWT validation p50 should be reasonable HTTP roundtrip time (5-100ms): " + jwtMetrics.getP50());
        assertTrue(jwtMetrics.getP95() > jwtMetrics.getP50(),
                "JWT validation p95 should be > p50");
        assertTrue(jwtMetrics.getP99() > jwtMetrics.getP95(),
                "JWT validation p99 should be > p95");

        // Should handle multiple sample mode benchmarks (validateJwtSample + validateAccessTokenSample)
        assertTrue(jwtMetrics.getSampleCount() >= 100,
                "Should accumulate samples from multiple JWT validation benchmarks");
    }

    private Object formatNumber(double value) {
        if (value < 10) {
            return Math.round(value * 10.0) / 10.0;
        } else {
            return (long) Math.round(value);
        }
    }

    private String createCombinedBenchmarkData() throws IOException {
        // Combine JWT validation and health sample mode data
        String jwtData = Files.readString(Path.of(jwtValidationBenchmarkFile));
        String realData = Files.readString(Path.of("src/test/resources/integration-benchmark-result.json"));

        // Extract just the sample mode benchmarks from real data
        JsonArray realBenchmarks = gson.fromJson(realData, JsonArray.class);
        JsonArray jwtBenchmarks = gson.fromJson(jwtData, JsonArray.class);

        JsonArray combined = new JsonArray();

        // Add JWT validation benchmarks
        for (JsonElement element : jwtBenchmarks) {
            combined.add(element);
        }

        // Add sample mode benchmarks from real data (health only)
        for (JsonElement element : realBenchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            if ("sample".equals(benchmark.get("mode").getAsString()) &&
                    !benchmark.get("benchmark").getAsString().contains("JwtEchoBenchmark")) {
                combined.add(element);
            }
        }

        return gson.toJson(combined);
    }
}