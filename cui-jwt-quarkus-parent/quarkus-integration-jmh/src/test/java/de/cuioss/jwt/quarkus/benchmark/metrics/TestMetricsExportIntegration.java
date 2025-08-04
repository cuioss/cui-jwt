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
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Test class to verify the JWT validation metrics export functionality
 * by simulating the export with test data.
 */
public class TestMetricsExportIntegration {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        // Create temporary directory for output
        Path tempDir = Files.createTempDirectory("metrics-test");
        System.out.println("Using temp directory: " + tempDir);

        // Create test metrics fetcher with sample data
        MetricsFetcher testFetcher = new TestMetricsFetcher();

        // Create exporter
        SimpleMetricsExporter exporter = new SimpleMetricsExporter(tempDir.toString(), testFetcher);

        // Simulate exporting metrics for JWT validation benchmarks
        String[] benchmarks = {
                "JwtValidationBenchmark.validateJwtThroughput",
                "JwtValidationBenchmark.validateAccessTokenThroughput",
                "JwtValidationBenchmark.validateIdTokenThroughput",
                "JwtValidationBenchmark.validateJwtLatency",
                "JwtEchoBenchmark.echoGetThroughput",  // Should be filtered out
                "JwtHealthBenchmark.healthCheckLatency" // Should be filtered out
        };

        for (String benchmark : benchmarks) {
            exporter.exportJwtValidationMetrics(benchmark, Instant.now());
            Thread.sleep(100); // Small delay to ensure different timestamps
        }

        // Read and display the result
        File resultFile = new File(tempDir.toFile(), "integration-metrics.json");
        if (resultFile.exists()) {
            System.out.println("\nGenerated integration-metrics.json:");
            System.out.println("=====================================");
            String content = Files.readString(resultFile.toPath());
            System.out.println(content);

            // Verify structure
            JsonObject json = GSON.fromJson(new FileReader(resultFile), JsonObject.class);
            System.out.println("\nVerification:");
            System.out.println("- Number of benchmarks: " + json.size());
            System.out.println("- Contains JwtEchoBenchmark: " + json.has("echoGetThroughput"));
            System.out.println("- Contains JwtHealthBenchmark: " + json.has("healthCheckLatency"));

            // Check one benchmark in detail
            if (json.has("validateJwtThroughput")) {
                JsonObject benchmark = json.getAsJsonObject("validateJwtThroughput");
                System.out.println("\nvalidateJwtThroughput structure:");
                System.out.println("- Has timestamp: " + benchmark.has("timestamp"));
                System.out.println("- Has steps: " + benchmark.has("steps"));
                System.out.println("- Has bearer_token_producer_metrics: " + benchmark.has("bearer_token_producer_metrics"));

                if (benchmark.has("steps")) {
                    JsonObject steps = benchmark.getAsJsonObject("steps");
                    System.out.println("- Number of steps: " + steps.size());
                }

                if (benchmark.has("bearer_token_producer_metrics")) {
                    JsonObject httpMetrics = benchmark.getAsJsonObject("bearer_token_producer_metrics");
                    System.out.println("- Number of bearer token producer metrics: " + httpMetrics.size());
                }
            }
        } else {
            System.err.println("ERROR: integration-metrics.json was not created!");
        }

        // Clean up
        Files.deleteIfExists(resultFile.toPath());
        Files.delete(tempDir);
    }

    /**
     * Test metrics fetcher that loads data from test resource files
     */
    private static class TestMetricsFetcher implements MetricsFetcher {

        @Override
        public Map<String, Double> fetchMetrics() {
            try {
                String testDataPath = "cui-jwt-quarkus-parent/quarkus-integration-jmh/src/test/resources/sample-jwt-validation-metrics.txt";
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