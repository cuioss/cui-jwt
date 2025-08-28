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
import de.cuioss.benchmarking.common.config.BenchmarkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SummaryGeneratorTest {

    @TempDir
    Path tempDir;

    private SummaryGenerator generator;
    private Gson gson;

    @BeforeEach void setUp() {
        generator = new SummaryGenerator();
        gson = new Gson();
    }

    @Test void writeSummaryWithEmptyResults() throws IOException {
        // Create an empty JSON file
        Path jsonFile = tempDir.resolve("benchmark-result.json");
        Files.writeString(jsonFile, "[]");

        // Generate summary with empty results
        Path summaryFile = tempDir.resolve("summary.json");
        generator.writeSummary(jsonFile, BenchmarkType.MICRO, Instant.now(), summaryFile.toString());

        // Verify summary file was created
        assertTrue(Files.exists(summaryFile));

        // Read and verify content
        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        assertEquals("FAILED", summary.get("execution_status"));
        assertEquals("micro", summary.get("benchmark_type"));
        assertNotNull(summary.get("timestamp"));
    }

    @Test void writeSummaryWithIntegrationBenchmark() throws IOException {
        // Use real integration test data
        Path sourceJson = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        Path jsonFile = tempDir.resolve("integration-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        Path summaryFile = tempDir.resolve("summary.json");
        generator.writeSummary(jsonFile, BenchmarkType.INTEGRATION, Instant.now(), summaryFile.toString());

        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        assertEquals("integration", summary.get("benchmark_type"));
    }

    @Test void writeSummaryThrowsIOException() {
        // Create a JSON file
        Path jsonFile = tempDir.resolve("benchmark-result.json");

        // Use invalid path to trigger IOException
        String invalidPath = "/invalid/path/that/does/not/exist/summary.json";

        // Should throw IOException
        assertThrows(IOException.class, () ->
                generator.writeSummary(jsonFile, BenchmarkType.INTEGRATION, Instant.now(), invalidPath));
    }

    @Test void writeSummaryCreatesRequiredFields() throws IOException {
        // Use real micro benchmark test data
        Path sourceJson = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        Path jsonFile = tempDir.resolve("micro-benchmark-result.json");
        Files.copy(sourceJson, jsonFile);

        Path summaryFile = tempDir.resolve("summary.json");
        generator.writeSummary(jsonFile, BenchmarkType.MICRO, Instant.now(), summaryFile.toString());

        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        // Verify all required fields are present
        assertNotNull(summary.get("timestamp"));
        assertNotNull(summary.get("benchmark_type"));
        assertNotNull(summary.get("execution_status"));
        assertNotNull(summary.get("metrics"));
        assertNotNull(summary.get("performance_grade"));
        assertNotNull(summary.get("average_throughput"));
        assertNotNull(summary.get("quality_gates"));
        assertNotNull(summary.get("recommendations"));
        assertNotNull(summary.get("artifacts"));
    }

    @Test void calculatePerformanceScoreWithTypicalIntegrationValues() {
        // Test with typical integration benchmark values
        double avgThroughput = 5847.0;  // ops/s
        double avgLatency = 1.17;        // ms

        double score = generator.calculatePerformanceScore(avgThroughput, avgLatency);

        // Score should be: (5847/100) * 0.5 + (100/1.17) * 0.5
        // = 58.47 * 0.5 + 85.47 * 0.5 = 29.24 + 42.74 = 71.98
        assertEquals(71.98, score, 0.1, "Score should be around 72 for integration benchmarks");

        String grade = generator.getPerformanceGrade(score);
        assertEquals("C", grade, "Grade should be C for score between 60-74");
    }

    @Test void calculatePerformanceScoreWithHighPerformanceMicro() {
        // Test with high performance micro benchmark values  
        double avgThroughput = 83909.0;  // ops/s
        double avgLatency = 0.35;        // ms

        double score = generator.calculatePerformanceScore(avgThroughput, avgLatency);

        // Uncapped scoring - can exceed 100
        // Throughput: 83909/100 = 839.09
        // Latency: 100/0.35 = 285.71
        // Score = 839.09 * 0.5 + 285.71 * 0.5 = 419.55 + 142.86 = 562.41
        assertEquals(562.4, score, 0.1, "Score should be around 562 for high performance micro benchmarks");

        String grade = generator.getPerformanceGrade(score);
        assertEquals("A", grade, "Grade should be A for exceptional performance");
    }

    @Test void calculatePerformanceScoreWithPoorPerformance() {
        // Test with poor performance values
        double avgThroughput = 1000.0;  // ops/s (low)
        double avgLatency = 5.0;        // ms (high)

        double score = generator.calculatePerformanceScore(avgThroughput, avgLatency);

        // Throughput: 1000/100 = 10
        // Latency: 100/5 = 20
        // Score = 10 * 0.5 + 20 * 0.5 = 5 + 10 = 15
        assertEquals(15.0, score, 0.1, "Score should be around 15 for poor performance");

        String grade = generator.getPerformanceGrade(score);
        assertEquals("F", grade, "Grade should be F for score < 40");
    }

    @Test void calculatePerformanceScoreWithMediumPerformance() {
        // Test with medium performance values
        double avgThroughput = 4000.0;  // ops/s
        double avgLatency = 1.5;        // ms

        double score = generator.calculatePerformanceScore(avgThroughput, avgLatency);

        // Throughput: 4000/100 = 40
        // Latency: 100/1.5 = 66.67
        // Score = 40 * 0.5 + 66.67 * 0.5 = 20 + 33.33 = 53.33
        assertEquals(53.3, score, 0.1, "Score should be around 53.3 for medium performance");

        String grade = generator.getPerformanceGrade(score);
        assertEquals("D", grade, "Grade should be D for score between 40-59");
    }

    @Test void getPerformanceGradeForAllRanges() {
        assertEquals("A", generator.getPerformanceGrade(95), "95 should be grade A");
        assertEquals("A", generator.getPerformanceGrade(90), "90 should be grade A");
        assertEquals("B", generator.getPerformanceGrade(85), "85 should be grade B");
        assertEquals("B", generator.getPerformanceGrade(75), "75 should be grade B");
        assertEquals("C", generator.getPerformanceGrade(70), "70 should be grade C");
        assertEquals("C", generator.getPerformanceGrade(60), "60 should be grade C");
        assertEquals("D", generator.getPerformanceGrade(50), "50 should be grade D");
        assertEquals("D", generator.getPerformanceGrade(40), "40 should be grade D");
        assertEquals("F", generator.getPerformanceGrade(30), "30 should be grade F");
        assertEquals("F", generator.getPerformanceGrade(0), "0 should be grade F");
    }

    @Test void calculatePerformanceScoreWithZeroLatency() {
        // Edge case: near-zero latency should result in very high latency score
        double avgThroughput = 5000.0;
        double avgLatency = 0.001;  // Near zero

        double score = generator.calculatePerformanceScore(avgThroughput, avgLatency);

        // Throughput: 5000/100 = 50
        // Latency: 100/0.001 = 100,000 (uncapped)
        // Score = 50 * 0.5 + 100000 * 0.5 = 25 + 50000 = 50025
        assertTrue(score > 50000, "Score should be very high with near-zero latency");
    }

    @Test void calculatePerformanceScoreWithVeryHighLatency() {
        // Edge case: very high latency should result in low score
        double avgThroughput = 5000.0;
        double avgLatency = 100.0;  // Very high latency

        double score = generator.calculatePerformanceScore(avgThroughput, avgLatency);

        // Throughput: 5000/100 = 50
        // Latency: 100/100 = 1
        // Score = 50 * 0.5 + 1 * 0.5 = 25 + 0.5 = 25.5
        assertEquals(25.5, score, 0.1, "Score should be low with high latency");

        String grade = generator.getPerformanceGrade(score);
        assertEquals("F", grade, "Grade should be F for score below 40");
    }

    @Test void calculatePerformanceScoreAtBaseline() {
        // Test at baseline values: 10,000 ops/s and 1ms should give score of 100
        double avgThroughput = 10000.0;  // ops/s (baseline)
        double avgLatency = 1.0;         // ms (baseline)

        double score = generator.calculatePerformanceScore(avgThroughput, avgLatency);

        // Throughput: 10000/100 = 100
        // Latency: 100/1 = 100
        // Score = 100 * 0.5 + 100 * 0.5 = 50 + 50 = 100
        assertEquals(100.0, score, 0.01, "Score should be exactly 100 at baseline");

        String grade = generator.getPerformanceGrade(score);
        assertEquals("A", grade, "Grade should be A for score of 100");
    }
}