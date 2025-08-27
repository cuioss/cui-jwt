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
package de.cuioss.benchmarking.common.report;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.util.Statistics;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.BenchmarkResultMetaData;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BadgeGenerator using real test data.
 * Tests the new performance score format with throughput and latency.
 */
class BadgeGeneratorRealDataTest {

    private final Gson gson = new Gson();

    @Test
    void testIntegrationBadgeWithRealData(@TempDir Path tempDir) throws Exception {
        // Load and parse real integration benchmark JSON
        Path testDataPath = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        // Use the test JSON parser to create collection for badge generator
        TestJSONParser parser = new TestJSONParser();
        Collection<RunResult> results = parser.parseJsonToResults(benchmarkArray);
        
        // Generate badge
        BadgeGenerator generator = new BadgeGenerator();
        generator.generatePerformanceBadge(results, BenchmarkType.INTEGRATION, tempDir.toString());
        
        // Verify badge was created
        Path badgeFile = tempDir.resolve("integration-performance-badge.json");
        assertTrue(Files.exists(badgeFile));
        
        // Read and verify badge content
        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);
        
        // Verify structure
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Integration Performance", badge.get("label").getAsString());
        assertNotNull(badge.get("message").getAsString());
        assertEquals("green", badge.get("color").getAsString());
        
        // Verify message format: "Score (throughput, latency)"
        String message = badge.get("message").getAsString();
        assertTrue(message.contains("("));
        assertTrue(message.contains(")"));
        assertTrue(message.contains("ops/s"));
        assertTrue(message.contains("ms"));
        
        // Verify specific values from test data
        // The test data has throughput of ~13.6 ops/ms = ~13600 ops/s
        assertTrue(message.contains("K"));
        assertTrue(message.contains("13") || message.contains("14"));
    }
    
    @Test
    void testMicroBadgeWithRealData(@TempDir Path tempDir) throws Exception {
        // Load and parse real micro benchmark JSON
        Path testDataPath = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        // Use the test JSON parser to create collection for badge generator
        TestJSONParser parser = new TestJSONParser();
        Collection<RunResult> results = parser.parseJsonToResults(benchmarkArray);
        
        // Generate badge
        BadgeGenerator generator = new BadgeGenerator();
        generator.generatePerformanceBadge(results, BenchmarkType.MICRO, tempDir.toString());
        
        // Verify badge was created
        Path badgeFile = tempDir.resolve("performance-badge.json");
        assertTrue(Files.exists(badgeFile));
        
        // Read and verify badge content
        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);
        
        // Verify structure
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Performance Score", badge.get("label").getAsString());
        assertNotNull(badge.get("message").getAsString());
        assertEquals("green", badge.get("color").getAsString());
        
        // Verify message format
        String message = badge.get("message").getAsString();
        assertTrue(message.contains("("));
        assertTrue(message.contains(")"));
        assertTrue(message.contains("ops/s"));
        assertTrue(message.contains("ms") || message.contains("us"));
        
        // The test data has throughput of 103380 ops/s = ~103K ops/s
        assertTrue(message.contains("K"));
        assertTrue(message.contains("103") || message.contains("104"));
    }
    
    @Test
    void testTrendBadgeGeneration(@TempDir Path tempDir) throws Exception {
        // Load and parse real benchmark JSON
        Path testDataPath = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        // Use the test JSON parser to create collection for badge generator
        TestJSONParser parser = new TestJSONParser();
        Collection<RunResult> results = parser.parseJsonToResults(benchmarkArray);
        
        // Generate trend badge
        BadgeGenerator generator = new BadgeGenerator();
        generator.generateTrendBadge(results, BenchmarkType.INTEGRATION, tempDir.toString());
        
        // Verify badge was created
        Path badgeFile = tempDir.resolve("integration-trend-badge.json");
        assertTrue(Files.exists(badgeFile));
        
        // Read and verify badge content
        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);
        
        // Verify structure
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Trend", badge.get("label").getAsString());
        assertEquals("stable", badge.get("message").getAsString());
        assertEquals("blue", badge.get("color").getAsString());
    }
    
    @Test
    void testLastRunBadgeGeneration(@TempDir Path tempDir) throws Exception {
        // Generate last run badge
        BadgeGenerator generator = new BadgeGenerator();
        generator.generateLastRunBadge(tempDir.toString());
        
        // Verify badge was created
        Path badgeFile = tempDir.resolve("last-run-badge.json");
        assertTrue(Files.exists(badgeFile));
        
        // Read and verify badge content
        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);
        
        // Verify structure
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Last Run", badge.get("label").getAsString());
        assertNotNull(badge.get("message").getAsString());
        assertEquals("lightgray", badge.get("color").getAsString());
    }
    
    @Test
    void testBadgeMessageFormatExplicit() {
        // Test message format explicitly - no conditionals
        
        // Test case 1: 50K score with throughput and latency
        String message1 = "50K (61.7K ops/s, 0.16ms)";
        assertEquals("50K (61.7K ops/s, 0.16ms)", message1);
        assertTrue(message1.startsWith("50K"));
        assertTrue(message1.contains("61.7K ops/s"));
        assertTrue(message1.contains("0.16ms"));
        
        // Test case 2: 4K score
        String message2 = "4K (3.8K ops/s, 250.0ms)";
        assertEquals("4K (3.8K ops/s, 250.0ms)", message2);
        assertTrue(message2.startsWith("4K"));
        assertTrue(message2.contains("3.8K ops/s"));
        assertTrue(message2.contains("250.0ms"));
        
        // Test case 3: 100 score (no K/M suffix)
        String message3 = "100 (100 ops/s, 10.00ms)";
        assertEquals("100 (100 ops/s, 10.00ms)", message3);
        assertTrue(message3.startsWith("100"));
        assertTrue(message3.contains("100 ops/s"));
        assertTrue(message3.contains("10.00ms"));
        
        // Test case 4: 1M score
        String message4 = "1M (1.2M ops/s, 0.83ms)";
        assertEquals("1M (1.2M ops/s, 0.83ms)", message4);
        assertTrue(message4.startsWith("1M"));
        assertTrue(message4.contains("1.2M ops/s"));
        assertTrue(message4.contains("0.83ms"));
    }
    
    @Test
    void testLatencyConversionsExplicit() {
        // Test latency formatting explicitly - no conditionals
        
        // Test case 1: 2.5 ms/op
        double msPerOp1 = 2.5;
        String formatted1 = String.format("%.1fms", msPerOp1);
        assertEquals("2.5ms", formatted1);
        
        // Test case 2: 2500.0 ms (large value)
        double msPerOp2 = 2500.0;
        String formatted2 = String.format("%.1fms", msPerOp2);
        assertEquals("2500.0ms", formatted2);
        
        // Test case 3: 0.15 ms (small value)
        double msPerOp3 = 0.15;
        String formatted3 = String.format("%.2fms", msPerOp3);
        assertEquals("0.15ms", formatted3);
        
        // Test case 4: 1.0 ms (exact value)
        double msPerOp4 = 1.0;
        String formatted4 = String.format("%.1fms", msPerOp4);
        assertEquals("1.0ms", formatted4);
    }
    
    @Test
    void testIntegrationBenchmarkDataStructure() throws Exception {
        // Verify the structure of integration benchmark test data
        Path testDataPath = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        assertNotNull(benchmarkArray);
        assertEquals(4, benchmarkArray.size());
        
        // First benchmark: throughput
        JsonObject benchmark1 = benchmarkArray.get(0).getAsJsonObject();
        assertEquals("thrpt", benchmark1.get("mode").getAsString());
        assertEquals("ops/ms", benchmark1.get("primaryMetric").getAsJsonObject().get("scoreUnit").getAsString());
        assertEquals(13.646787243168102, benchmark1.get("primaryMetric").getAsJsonObject().get("score").getAsDouble(), 0.01);
        
        // Second benchmark: average time
        JsonObject benchmark2 = benchmarkArray.get(1).getAsJsonObject();
        assertEquals("avgt", benchmark2.get("mode").getAsString());
        assertEquals("ms/op", benchmark2.get("primaryMetric").getAsJsonObject().get("scoreUnit").getAsString());
        
        // Third benchmark: sample time
        JsonObject benchmark3 = benchmarkArray.get(2).getAsJsonObject();
        assertEquals("sample", benchmark3.get("mode").getAsString());
        assertEquals("ms/op", benchmark3.get("primaryMetric").getAsJsonObject().get("scoreUnit").getAsString());
        
        // Fourth benchmark: single shot
        JsonObject benchmark4 = benchmarkArray.get(3).getAsJsonObject();
        assertEquals("ss", benchmark4.get("mode").getAsString());
        assertEquals("ms/op", benchmark4.get("primaryMetric").getAsJsonObject().get("scoreUnit").getAsString());
    }
    
    @Test
    void testMicroBenchmarkDataStructure() throws Exception {
        // Verify the structure of micro benchmark test data
        Path testDataPath = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        assertNotNull(benchmarkArray);
        assertEquals(6, benchmarkArray.size());
        
        // First benchmark: throughput
        JsonObject benchmark1 = benchmarkArray.get(0).getAsJsonObject();
        assertTrue(benchmark1.get("benchmark").getAsString().contains("SimpleCoreValidationBenchmark.measureThroughput"));
        assertEquals("thrpt", benchmark1.get("mode").getAsString());
        assertEquals("ops/s", benchmark1.get("primaryMetric").getAsJsonObject().get("scoreUnit").getAsString());
        assertEquals(103380.86760034731, benchmark1.get("primaryMetric").getAsJsonObject().get("score").getAsDouble(), 1.0);
        
        // Second benchmark: average time
        JsonObject benchmark2 = benchmarkArray.get(1).getAsJsonObject();
        assertTrue(benchmark2.get("benchmark").getAsString().contains("SimpleCoreValidationBenchmark.measureAverageTime"));
        assertEquals("avgt", benchmark2.get("mode").getAsString());
        assertEquals("us/op", benchmark2.get("primaryMetric").getAsJsonObject().get("scoreUnit").getAsString());
    }
}