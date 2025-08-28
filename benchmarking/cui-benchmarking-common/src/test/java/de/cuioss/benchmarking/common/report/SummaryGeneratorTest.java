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
import java.util.Map;

import static de.cuioss.benchmarking.common.TestHelper.createTestMetrics;
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

    @Test void writeSummaryWithMetrics() throws IOException {
        // Use test metrics
        BenchmarkMetrics metrics = createTestMetrics(10000, 1.5);

        // Generate summary
        Path summaryFile = tempDir.resolve("summary.json");
        generator.writeSummary(metrics, BenchmarkType.MICRO, summaryFile);

        // Verify summary file was created
        assertTrue(Files.exists(summaryFile));

        // Read and verify content
        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        assertNotNull(summary.get("benchmark_type"));
        assertNotNull(summary.get("timestamp"));
        assertNotNull(summary.get("throughput"));
        assertNotNull(summary.get("latency"));
        assertNotNull(summary.get("performance_score"));
        assertNotNull(summary.get("performance_grade"));
        assertNotNull(summary.get("status"));
        assertNotNull(summary.get("deployment_ready"));
        assertNotNull(summary.get("regression_detected"));
    }

    @Test void writeSummaryWithRealData() throws IOException {
        // Use real test data
        Path jsonFile = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        BenchmarkMetrics metrics = createTestMetrics(jsonFile);

        // Generate summary
        Path summaryFile = tempDir.resolve("summary.json");
        generator.writeSummary(metrics, BenchmarkType.MICRO, summaryFile);

        // Verify summary file was created
        assertTrue(Files.exists(summaryFile));

        // Read and verify content structure
        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        assertEquals(BenchmarkType.MICRO.toString(), summary.get("benchmark_type").toString());
        assertNotNull(summary.get("throughputBenchmarkName"));
        assertNotNull(summary.get("latencyBenchmarkName"));
        assertNotNull(summary.get("throughputFormatted"));
        assertNotNull(summary.get("latencyFormatted"));
    }

    @Test void performanceStatusDetermination() throws IOException {
        // Test different performance levels
        // Score = (throughput/100 * 0.5) + (100/latency * 0.5)
        testPerformanceStatus(createTestMetrics(20000, 0.5), "EXCELLENT");  // Score = 100 + 100 = 200
        testPerformanceStatus(createTestMetrics(8000, 1.0), "EXCELLENT");   // Score = 40 + 50 = 90 (>= 90)
        testPerformanceStatus(createTestMetrics(8500, 1.2), "GOOD");        // Score = 42.5 + 41.67 = 84.17 (75-89)
        testPerformanceStatus(createTestMetrics(6500, 1.5), "FAIR");        // Score = 32.5 + 33.33 = 65.83 (60-74)
        testPerformanceStatus(createTestMetrics(1000, 10.0), "POOR");       // Score = 5 + 5 = 10
    }

    private void testPerformanceStatus(BenchmarkMetrics metrics, String expectedStatus) throws IOException {
        Path summaryFile = tempDir.resolve("summary-" + expectedStatus + ".json");
        generator.writeSummary(metrics, BenchmarkType.MICRO, summaryFile);

        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        assertEquals(expectedStatus, summary.get("status"));
    }

    @Test void deploymentReadinessCheck() throws IOException {
        // Test deployment readiness based on performance
        
        // Good performance should be deployment ready
        BenchmarkMetrics goodMetrics = createTestMetrics(10000, 1.0);
        Path goodSummary = tempDir.resolve("good-summary.json");
        generator.writeSummary(goodMetrics, BenchmarkType.MICRO, goodSummary);

        String content = Files.readString(goodSummary);
        Map<String, Object> summary = gson.fromJson(content, Map.class);
        assertTrue((Boolean)summary.get("deployment_ready"));

        // Poor performance should not be deployment ready
        BenchmarkMetrics poorMetrics = createTestMetrics(100, 50.0);
        Path poorSummary = tempDir.resolve("poor-summary.json");
        generator.writeSummary(poorMetrics, BenchmarkType.MICRO, poorSummary);

        content = Files.readString(poorSummary);
        summary = gson.fromJson(content, Map.class);
        assertFalse((Boolean)summary.get("deployment_ready"));
    }
}