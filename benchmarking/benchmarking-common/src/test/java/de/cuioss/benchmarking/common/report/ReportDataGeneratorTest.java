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
import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.model.BenchmarkData;
import de.cuioss.benchmarking.common.util.JsonSerializationHelper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReportDataGenerator, focusing on chart data generation with real benchmark data.
 */
class ReportDataGeneratorTest {

    private static final Gson GSON = new Gson();

    @Test
    void chartDataGenerationWithRealIntegrationBenchmarks() throws Exception {
        // TEST: Reproduce the issue where latency and percentiles are missing from chart data
        // Uses REAL benchmark data from integration tests that has thrpt mode WITH latency/percentiles

        // Load real benchmark-data.json from test resources
        Path realDataPath = Path.of("src/test/resources/integration-benchmark-results/real-benchmark-data.json");
        assertTrue(Files.exists(realDataPath), "Real benchmark data file must exist: " + realDataPath);

        String jsonContent = Files.readString(realDataPath);
        JsonObject realData = GSON.fromJson(jsonContent, JsonObject.class);

        // Deserialize to BenchmarkData model
        BenchmarkData benchmarkData = JsonSerializationHelper.fromJson(jsonContent, BenchmarkData.class);

        assertNotNull(benchmarkData, "Benchmark data should not be null");
        assertNotNull(benchmarkData.getBenchmarks(), "Benchmarks list should not be null");
        assertFalse(benchmarkData.getBenchmarks().isEmpty(), "Should have at least one benchmark");

        // Verify the jwtValidation benchmark has the expected structure
        BenchmarkData.Benchmark jwtBenchmark = benchmarkData.getBenchmarks().stream()
                .filter(b -> "jwtValidation".equals(b.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(jwtBenchmark, "jwtValidation benchmark must exist");
        assertEquals("thrpt", jwtBenchmark.getMode(), "Should be throughput mode");
        assertNotNull(jwtBenchmark.getLatency(), "Latency data should exist for WRK benchmark");
        assertNotNull(jwtBenchmark.getPercentiles(), "Percentiles should exist for WRK benchmark");
        assertFalse(jwtBenchmark.getPercentiles().isEmpty(), "Percentiles should not be empty");

        // VERIFICATION: Check that the real data has latency despite being thrpt mode
        // This is the core issue: thrpt mode benchmarks from WRK have BOTH throughput AND latency
        assertTrue(jwtBenchmark.getLatency().contains("ms"),
                "Latency should be in milliseconds format, got: " + jwtBenchmark.getLatency());

        // NOW PROCESS THE DATA WITH THE REPORTDATAGENERATOR TO GENERATE CHART DATA
        ReportDataGenerator generator = new ReportDataGenerator();
        java.lang.reflect.Method createChartDataMethod = ReportDataGenerator.class.getDeclaredMethod(
                "createChartData", List.class);
        createChartDataMethod.setAccessible(true);

        @SuppressWarnings("unchecked") Map<String, Object> chartData = (Map<String, Object>) createChartDataMethod.invoke(
                generator, benchmarkData.getBenchmarks());

        assertNotNull(chartData, "Chart data should be generated");

        // AFTER FIX: The latency array should have actual values
        @SuppressWarnings("unchecked") List<Double> latencyList = (List<Double>) chartData.get("latency");
        assertNotNull(latencyList, "Latency list should exist");

        // Find the jwtValidation index
        @SuppressWarnings("unchecked") List<String> labelsList = (List<String>) chartData.get("labels");
        int jwtIndex = labelsList.indexOf("jwtValidation");
        assertTrue(jwtIndex >= 0, "jwtValidation should be in labels");

        // AFTER FIX: latency should have actual value from the benchmark's P50 percentile
        Double latencyValue = latencyList.get(jwtIndex);
        assertNotNull(latencyValue, "FIXED: Latency should NOT be null when benchmark has percentile data");
        assertTrue(latencyValue > 0, "Latency value should be positive, got: " + latencyValue);

        // AFTER FIX: jwtValidation should be in percentiles data
        @SuppressWarnings("unchecked") Map<String, Object> percentilesData = (Map<String, Object>) chartData.get("percentilesData");
        assertNotNull(percentilesData, "Percentiles data should exist");

        @SuppressWarnings("unchecked") List<String> benchmarkNames = (List<String>) percentilesData.get("benchmarks");

        // FIXED: jwtValidation should be in the percentiles benchmarks list
        assertTrue(benchmarkNames.contains("jwtValidation"),
                "FIXED: jwtValidation should be in percentiles data when it has percentile info");

        // Verify percentile values are actually present
        @SuppressWarnings("unchecked") Map<String, List<Double>> datasets = (Map<String, List<Double>>) percentilesData.get("datasets");
        assertNotNull(datasets, "Datasets should exist");

        List<Double> p50Values = datasets.get("50.0th");
        assertNotNull(p50Values, "P50 values should exist");
        assertTrue(p50Values.size() > 0, "Should have at least one percentile value");
    }
}
