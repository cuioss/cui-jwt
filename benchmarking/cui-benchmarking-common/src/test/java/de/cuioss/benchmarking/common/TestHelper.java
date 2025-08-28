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
package de.cuioss.benchmarking.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import de.cuioss.benchmarking.common.report.BenchmarkMetrics;
import de.cuioss.benchmarking.common.report.MetricsComputer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static de.cuioss.benchmarking.common.TestConstants.DEFAULT_LATENCY_BENCHMARK;
import static de.cuioss.benchmarking.common.TestConstants.DEFAULT_THROUGHPUT_BENCHMARK;

/**
 * Helper methods for tests.
 */
public final class TestHelper {
    
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();
    
    private TestHelper() {
        // utility class
    }
    
    /**
     * Creates test metrics from a JSON file.
     */
    public static BenchmarkMetrics createTestMetrics(Path jsonFile) throws IOException {
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);
        
        MetricsComputer computer = new MetricsComputer(
                DEFAULT_THROUGHPUT_BENCHMARK, 
                DEFAULT_LATENCY_BENCHMARK);
        return computer.computeMetrics(benchmarks);
    }
    
    /**
     * Creates test metrics with specific values.
     */
    public static BenchmarkMetrics createTestMetrics(double throughput, double latency) {
        double performanceScore = (throughput / 100.0) * 0.5 + (100.0 / latency) * 0.5;
        String grade = getGrade(performanceScore);
        
        return new BenchmarkMetrics(
                DEFAULT_THROUGHPUT_BENCHMARK,
                DEFAULT_LATENCY_BENCHMARK,
                throughput,
                latency,
                performanceScore,
                grade,
                formatThroughput(throughput),
                formatLatency(latency),
                String.valueOf(Math.round(performanceScore))
        );
    }
    
    private static String getGrade(double score) {
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 75) return "B";
        if (score >= 60) return "C";
        if (score >= 40) return "D";
        return "F";
    }
    
    private static String formatThroughput(double value) {
        if (value >= 1000) {
            return Math.round(value / 1000) + "K ops/s";
        }
        return Math.round(value) + " ops/s";
    }
    
    private static String formatLatency(double ms) {
        if (ms >= 1000) {
            return Math.round(ms / 1000) + "s";
        }
        return Math.round(ms) + "ms";
    }
}