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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Computes benchmark metrics from JSON results.
 * This is the single source of truth for metric calculations.
 */
public class MetricsComputer {

    private final String throughputBenchmarkName;
    private final String latencyBenchmarkName;

    public MetricsComputer(String throughputBenchmarkName, String latencyBenchmarkName) {
        if (throughputBenchmarkName == null || throughputBenchmarkName.isBlank()) {
            throw new IllegalArgumentException("Throughput benchmark name must be specified");
        }
        if (latencyBenchmarkName == null || latencyBenchmarkName.isBlank()) {
            throw new IllegalArgumentException("Latency benchmark name must be specified");
        }
        this.throughputBenchmarkName = throughputBenchmarkName;
        this.latencyBenchmarkName = latencyBenchmarkName;
    }

    public BenchmarkMetrics computeMetrics(JsonArray benchmarks) {
        if (benchmarks == null || benchmarks.isEmpty()) {
            throw new IllegalArgumentException("No benchmark results provided");
        }

        double throughput = extractThroughput(benchmarks);
        double latency = extractLatency(benchmarks);
        double performanceScore = calculatePerformanceScore(throughput, latency);
        String performanceGrade = getPerformanceGrade(performanceScore);

        String throughputFormatted = formatThroughput(throughput);
        String latencyFormatted = formatLatency(latency);
        String performanceScoreFormatted = MetricConversionUtil.formatForDisplay(performanceScore);

        return new BenchmarkMetrics(
                throughputBenchmarkName,
                latencyBenchmarkName,
                throughput,
                latency,
                performanceScore,
                performanceGrade,
                throughputFormatted,
                latencyFormatted,
                performanceScoreFormatted
        );
    }

    private double extractThroughput(JsonArray benchmarks) {
        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String benchmarkName = benchmark.get("benchmark").getAsString();
            
            if (benchmarkName.contains(throughputBenchmarkName)) {
                String mode = benchmark.get("mode").getAsString();
                JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
                double score = primaryMetric.get("score").getAsDouble();
                String unit = primaryMetric.get("scoreUnit").getAsString();
                
                if ("thrpt".equals(mode) || unit.contains("ops")) {
                    return MetricConversionUtil.convertToOpsPerSecond(score, unit);
                }
            }
        }
        
        throw new IllegalStateException(
                "Required throughput benchmark '" + throughputBenchmarkName + "' not found in results");
    }

    private double extractLatency(JsonArray benchmarks) {
        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String benchmarkName = benchmark.get("benchmark").getAsString();
            
            if (benchmarkName.contains(latencyBenchmarkName)) {
                String mode = benchmark.get("mode").getAsString();
                JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
                double score = primaryMetric.get("score").getAsDouble();
                String unit = primaryMetric.get("scoreUnit").getAsString();
                
                if ("avgt".equals(mode) || "sample".equals(mode) || unit.contains("/op")) {
                    return MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                }
            }
        }
        
        throw new IllegalStateException(
                "Required latency benchmark '" + latencyBenchmarkName + "' not found in results");
    }

    private double calculatePerformanceScore(double throughput, double latency) {
        double throughputScore = (throughput / 100.0) * 0.5;
        double latencyScore = (100.0 / latency) * 0.5;
        return throughputScore + latencyScore;
    }

    private String getPerformanceGrade(double score) {
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 75) return "B";
        if (score >= 60) return "C";
        if (score >= 40) return "D";
        return "F";
    }

    private String formatThroughput(double value) {
        if (value >= 1_000_000) {
            double scaledValue = value / 1_000_000;
            return MetricConversionUtil.formatForDisplay(scaledValue) + "M ops/s";
        } else if (value >= 1000) {
            double scaledValue = value / 1000;
            return MetricConversionUtil.formatForDisplay(scaledValue) + "K ops/s";
        } else {
            return MetricConversionUtil.formatForDisplay(value) + " ops/s";
        }
    }

    private String formatLatency(double ms) {
        if (ms >= 1000) {
            double seconds = ms / 1000;
            return MetricConversionUtil.formatForDisplay(seconds) + "s";
        } else {
            return MetricConversionUtil.formatForDisplay(ms) + "ms";
        }
    }
}