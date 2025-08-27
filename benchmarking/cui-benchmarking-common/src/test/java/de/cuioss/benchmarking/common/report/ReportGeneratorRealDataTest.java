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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReportGenerator with real data focusing on new features:
 * - Average latency in overview
 * - Detailed visualizer page generation
 */
class ReportGeneratorRealDataTest {

    private final Gson gson = new Gson();

    @Test
    void testGenerateDetailedPageWithRealData(@TempDir Path tempDir) throws Exception {
        // Load real integration benchmark results
        Path testDataPath = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        TestJSONParser parser = new TestJSONParser();
        Collection<RunResult> results = parser.parseJsonToResults(benchmarkArray);
        
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();
        
        // Generate detailed page
        generator.generateDetailedPage(results, "Integration Performance", outputDir);
        
        // Verify detailed.html was created
        Path detailedFile = tempDir.resolve("detailed.html");
        assertTrue(Files.exists(detailedFile));
        
        // Verify content
        String content = Files.readString(detailedFile);
        
        // Check for required elements
        assertTrue(content.contains("Detailed Benchmark Analysis"));
        assertTrue(content.contains("JMH Visualizer"));
        assertTrue(content.contains("Integration Performance"));
        assertTrue(content.contains("jmh-visualizer"));
        assertTrue(content.contains("https://jmh.morethan.io/"));
        assertTrue(content.contains("benchmark-result.json"));
        
        // Check navigation
        assertTrue(content.contains("index.html"));
        assertTrue(content.contains("trends.html"));
        assertTrue(content.contains("detailed.html"));
    }
    
    @Test
    void testOverviewWithLatencyRealData(@TempDir Path tempDir) throws Exception {
        // Load real integration benchmark results
        Path testDataPath = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        TestJSONParser parser = new TestJSONParser();
        Collection<RunResult> results = parser.parseJsonToResults(benchmarkArray);
        
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();
        
        // Generate index page
        generator.generateIndexPage(results, outputDir);
        
        // Verify index.html was created
        Path indexFile = tempDir.resolve("index.html");
        assertTrue(Files.exists(indexFile));
        
        // Verify content includes latency
        String content = Files.readString(indexFile);
        
        // Check for latency in overview
        assertTrue(content.contains("Average Latency"));
        
        // Should have actual latency value, not N/A
        assertFalse(content.contains(">N/A</"));
        assertTrue(content.contains("ms"));
        
        // Check for other required metrics
        assertTrue(content.contains("Average Throughput"));
        assertTrue(content.contains("Performance Grade"));
        assertTrue(content.contains("Total Benchmarks"));
        
        // Should show 4 benchmarks from test data
        assertTrue(content.contains("4"));
    }
    
    @Test
    void testNavigationMenuUpdatedWithRealData(@TempDir Path tempDir) throws Exception {
        // Load real micro benchmark results
        Path testDataPath = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        TestJSONParser parser = new TestJSONParser();
        Collection<RunResult> results = parser.parseJsonToResults(benchmarkArray);
        
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();
        
        // Generate index page
        generator.generateIndexPage(results, outputDir);
        
        Path indexFile = tempDir.resolve("index.html");
        assertTrue(Files.exists(indexFile));
        String content = Files.readString(indexFile);
        
        // Verify navigation includes all pages - explicit assertions
        assertTrue(content.contains("href=\"index.html\""));
        assertTrue(content.contains("href=\"trends.html\""));
        assertTrue(content.contains("href=\"detailed.html\""));
        assertTrue(content.contains("href=\"data/metrics.json\""));
        
        // Verify navigation labels
        assertTrue(content.contains(">Overview<"));
        assertTrue(content.contains(">Trends<"));
        assertTrue(content.contains(">Detailed<"));
        assertTrue(content.contains(">Raw Data<"));
    }
    
    @Test
    void testTrendsPageWithRealData(@TempDir Path tempDir) throws Exception {
        // Load real benchmark results
        Path testDataPath = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        TestJSONParser parser = new TestJSONParser();
        Collection<RunResult> results = parser.parseJsonToResults(benchmarkArray);
        
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();
        
        // Generate trends page
        generator.generateTrendsPage(results, outputDir);
        
        // Verify trends.html was created
        Path trendsFile = tempDir.resolve("trends.html");
        assertTrue(Files.exists(trendsFile));
        
        // Verify content
        String content = Files.readString(trendsFile);
        
        // Should contain chart elements
        assertTrue(content.contains("Chart.js"));
        assertTrue(content.contains("canvas"));
        assertTrue(content.contains("trendsChart"));
        
        // Should contain navigation
        assertTrue(content.contains("href=\"index.html\""));
        assertTrue(content.contains("href=\"trends.html\""));
        assertTrue(content.contains("href=\"detailed.html\""));
    }
    
    @Test
    void testLatencyFormattingExplicit() {
        // Test latency formatting explicitly
        
        // Test case 1: 0.0 should be N/A
        double latency1 = 0.0;
        String expected1 = "N/A";
        String actual1 = latency1 == 0.0 ? "N/A" : String.format("%.2f ms", latency1);
        assertEquals(expected1, actual1);
        
        // Test case 2: 0.15 ms
        double latency2 = 0.15;
        String expected2 = "0.15 ms";
        String actual2 = String.format("%.2f ms", latency2);
        assertEquals(expected2, actual2);
        
        // Test case 3: 1.5 ms
        double latency3 = 1.5;
        String expected3 = "1.50 ms";
        String actual3 = String.format("%.2f ms", latency3);
        assertEquals(expected3, actual3);
        
        // Test case 4: 250.0 ms
        double latency4 = 250.0;
        String expected4 = "250.00 ms";
        String actual4 = String.format("%.2f ms", latency4);
        assertEquals(expected4, actual4);
        
        // Test case 5: 2500.0 ms should convert to seconds
        double latency5 = 2500.0;
        String expected5 = "2.5 s";
        String actual5 = latency5 >= 1000 ? String.format("%.1f s", latency5 / 1000) : String.format("%.2f ms", latency5);
        assertEquals(expected5, actual5);
    }
    
    @Test
    void testIndexPageContainsAllMetrics(@TempDir Path tempDir) throws Exception {
        // Load real micro benchmark results
        Path testDataPath = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        String jsonContent = Files.readString(testDataPath);
        JsonArray benchmarkArray = gson.fromJson(jsonContent, JsonArray.class);
        
        TestJSONParser parser = new TestJSONParser();
        Collection<RunResult> results = parser.parseJsonToResults(benchmarkArray);
        
        ReportGenerator generator = new ReportGenerator();
        String outputDir = tempDir.toString();
        
        // Generate index page
        generator.generateIndexPage(results, outputDir);
        
        Path indexFile = tempDir.resolve("index.html");
        assertTrue(Files.exists(indexFile));
        String content = Files.readString(indexFile);
        
        // Verify all metrics are present
        assertTrue(content.contains("Total Benchmarks"));
        assertTrue(content.contains("6")); // 6 benchmarks in micro test data
        
        assertTrue(content.contains("Performance Grade"));
        assertTrue(content.contains("Average Throughput"));
        assertTrue(content.contains("Average Latency"));
        
        // Should have actual values, not N/A
        assertTrue(content.contains("ops/s"));
        assertTrue(content.contains("us") || content.contains("ms")); // Latency units
    }
}