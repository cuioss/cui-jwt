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
import org.openjdk.jmh.results.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SummaryGeneratorTest {

    @TempDir
    Path tempDir;

    private SummaryGenerator generator;
    private Gson gson;

    @BeforeEach
    void setUp() {
        generator = new SummaryGenerator();
        gson = new Gson();
    }

    @Test
    void testWriteSummaryWithEmptyResults() throws IOException {
        Collection<RunResult> results = new ArrayList<>();

        // Generate summary with empty results
        Path summaryFile = tempDir.resolve("summary.json");
        generator.writeSummary(results, BenchmarkType.MICRO, Instant.now(), summaryFile.toString());

        // Verify summary file was created
        assertTrue(Files.exists(summaryFile));

        // Read and verify content
        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        assertEquals(0.0, summary.get("total_benchmarks"));
        assertEquals("FAILED", summary.get("execution_status"));
        assertEquals("micro", summary.get("benchmark_type"));
        assertNotNull(summary.get("timestamp"));
    }

    @Test
    void testWriteSummaryWithIntegrationBenchmark() throws IOException {
        Collection<RunResult> results = new ArrayList<>();

        Path summaryFile = tempDir.resolve("summary.json");
        generator.writeSummary(results, BenchmarkType.INTEGRATION, Instant.now(), summaryFile.toString());

        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        assertEquals("integration", summary.get("benchmark_type"));
    }

    @Test
    void testWriteSummaryThrowsIOException() {
        Collection<RunResult> results = new ArrayList<>();

        // Use invalid path to trigger IOException
        String invalidPath = "/invalid/path/that/does/not/exist/summary.json";
        
        // Should throw IOException
        assertThrows(IOException.class, () -> 
            generator.writeSummary(results, BenchmarkType.INTEGRATION, Instant.now(), invalidPath));
    }

    @Test
    void testWriteSummaryCreatesRequiredFields() throws IOException {
        Collection<RunResult> results = new ArrayList<>();
        
        Path summaryFile = tempDir.resolve("summary.json");
        generator.writeSummary(results, BenchmarkType.MICRO, Instant.now(), summaryFile.toString());

        String content = Files.readString(summaryFile);
        Map<String, Object> summary = gson.fromJson(content, Map.class);

        // Verify all required fields are present
        assertNotNull(summary.get("timestamp"));
        assertNotNull(summary.get("benchmark_type"));
        assertNotNull(summary.get("execution_status"));
        assertNotNull(summary.get("metrics"));
        assertNotNull(summary.get("total_benchmarks"));
        assertNotNull(summary.get("performance_grade"));
        assertNotNull(summary.get("average_throughput"));
        assertNotNull(summary.get("quality_gates"));
        assertNotNull(summary.get("recommendations"));
        assertNotNull(summary.get("artifacts"));
    }
}