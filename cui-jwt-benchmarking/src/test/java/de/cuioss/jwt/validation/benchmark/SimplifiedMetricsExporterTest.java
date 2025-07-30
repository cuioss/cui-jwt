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
package de.cuioss.jwt.validation.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimplifiedMetricsExporterTest {

    @TempDir
    Path tempDir;

    private final Gson gson = new GsonBuilder().create();

    @Test
    void shouldExportMetricsToJsonFile() throws IOException, InterruptedException {
        // Given
        TokenValidatorMonitor monitor = TokenValidatorMonitorConfig.builder()
                .measurementType(MeasurementType.TOKEN_PARSING)
                .measurementType(MeasurementType.HEADER_VALIDATION)
                .build()
                .createMonitor();

        // Simulate some measurements
        long parseStart = System.nanoTime();
        Thread.sleep(10);
        long parseDuration = System.nanoTime() - parseStart;
        monitor.recordMeasurement(MeasurementType.TOKEN_PARSING, parseDuration);

        long headerStart = System.nanoTime();
        Thread.sleep(5);
        long headerDuration = System.nanoTime() - headerStart;
        monitor.recordMeasurement(MeasurementType.HEADER_VALIDATION, headerDuration);

        // Set up test directory
        System.setProperty("benchmark.results.dir", tempDir.toString());

        // When
        SimplifiedMetricsExporter.exportMetrics(monitor);

        // Then
        Path[] jsonFiles = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".json"))
                .toArray(Path[]::new);

        assertEquals(1, jsonFiles.length);
        assertTrue(jsonFiles[0].getFileName().toString().equals("jwt-validation-metrics.json"));
        
        String jsonContent = Files.readString(jsonFiles[0]);
        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
        Map<String, Object> allMetrics = gson.fromJson(jsonContent, typeToken.getType());

        // Verify we have at least one benchmark entry (unknown_benchmark since no specific benchmark name is set)
        assertFalse(allMetrics.isEmpty());
        
        // Get the first benchmark entry (should be "unknown_benchmark")
        String benchmarkName = allMetrics.keySet().iterator().next();
        Map<String, Object> benchmarkMetrics = (Map<String, Object>) allMetrics.get(benchmarkName);

        // Verify JSON structure for the benchmark
        assertNotNull(benchmarkMetrics.get("timestamp"));
        assertNotNull(benchmarkMetrics.get("steps"));

        Map<String, Map<String, Object>> steps = (Map<String, Map<String, Object>>) benchmarkMetrics.get("steps");
        assertTrue(steps.containsKey("token_parsing"));
        assertTrue(steps.containsKey("header_validation"));

        // Verify metrics exist
        Map<String, Object> parseMetrics = steps.get("token_parsing");
        assertNotNull(parseMetrics.get("sample_count"));
        assertNotNull(parseMetrics.get("p50_us"));
        assertNotNull(parseMetrics.get("p95_us"));
        assertNotNull(parseMetrics.get("p99_us"));
    }

    @Test
    void shouldHandleNullMonitor() throws IOException {
        // Should not throw exception
        SimplifiedMetricsExporter.exportMetrics(null);
    }
}