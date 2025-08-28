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

import static de.cuioss.benchmarking.common.report.ReportConstants.*;

/**
 * Computes benchmark metrics from JSON results.
 * This is the single source of truth for metric calculations.
 */
public class MetricsComputer {

    private final String throughputBenchmarkName;
    private final String latencyBenchmarkName;

    public MetricsComputer(String throughputBenchmarkName, String latencyBenchmarkName) {
        if (throughputBenchmarkName == null || throughputBenchmarkName.isBlank()) {
            throw new IllegalArgumentException(ERRORS.THROUGHPUT_NAME_REQUIRED);
        }
        if (latencyBenchmarkName == null || latencyBenchmarkName.isBlank()) {
            throw new IllegalArgumentException(ERRORS.LATENCY_NAME_REQUIRED);
        }
        this.throughputBenchmarkName = throughputBenchmarkName;
        this.latencyBenchmarkName = latencyBenchmarkName;
    }

    public BenchmarkMetrics computeMetrics(JsonArray benchmarks) {
        if (benchmarks == null || benchmarks.isEmpty()) {
            throw new IllegalArgumentException(ERRORS.NO_RESULTS_PROVIDED);
        }

        double throughput = extractThroughput(benchmarks);
        double latency = extractLatency(benchmarks);
        double rawPerformanceScore = calculatePerformanceScore(throughput, latency);
        // Performance score is always stored as rounded value
        double performanceScore = Math.round(rawPerformanceScore);
        String performanceGrade = getPerformanceGrade(performanceScore);

        return new BenchmarkMetrics(
                throughputBenchmarkName,
                latencyBenchmarkName,
                throughput,
                latency,
                performanceScore,
                performanceGrade
        );
    }

    private double extractThroughput(JsonArray benchmarks) {
        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String benchmarkName = benchmark.get(JSON_FIELDS.BENCHMARK).getAsString();

            if (benchmarkName.contains(throughputBenchmarkName)) {
                String mode = benchmark.get(JSON_FIELDS.MODE).getAsString();
                JsonObject primaryMetric = benchmark.getAsJsonObject(JSON_FIELDS.PRIMARY_METRIC);
                double score = primaryMetric.get(JSON_FIELDS.SCORE).getAsDouble();
                String unit = primaryMetric.get(JSON_FIELDS.SCORE_UNIT).getAsString();

                if (MODES.THROUGHPUT.equals(mode) || unit.contains(UNITS.OPS)) {
                    return MetricConversionUtil.convertToOpsPerSecond(score, unit);
                }
            }
        }

        throw new IllegalStateException(
                ERRORS.THROUGHPUT_NOT_FOUND_FORMAT.formatted(throughputBenchmarkName));
    }

    private double extractLatency(JsonArray benchmarks) {
        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String benchmarkName = benchmark.get(JSON_FIELDS.BENCHMARK).getAsString();

            if (benchmarkName.contains(latencyBenchmarkName)) {
                String mode = benchmark.get(JSON_FIELDS.MODE).getAsString();
                JsonObject primaryMetric = benchmark.getAsJsonObject(JSON_FIELDS.PRIMARY_METRIC);
                double score = primaryMetric.get(JSON_FIELDS.SCORE).getAsDouble();
                String unit = primaryMetric.get(JSON_FIELDS.SCORE_UNIT).getAsString();

                if (MODES.AVERAGE_TIME.equals(mode) || MODES.SAMPLE.equals(mode) || unit.contains(UNITS.SUFFIX_OP)) {
                    return MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                }
            }
        }

        throw new IllegalStateException(
                ERRORS.LATENCY_NOT_FOUND_FORMAT.formatted(latencyBenchmarkName));
    }

    private double calculatePerformanceScore(double throughput, double latency) {
        double throughputScore = (throughput / 100.0) * 0.5;
        double latencyScore = (100.0 / latency) * 0.5;
        return throughputScore + latencyScore;
    }

    private String getPerformanceGrade(double score) {
        if (score >= 95) return GRADES.A_PLUS;
        if (score >= 90) return GRADES.A;
        if (score >= 75) return GRADES.B;
        if (score >= 60) return GRADES.C;
        if (score >= 40) return GRADES.D;
        return GRADES.F;
    }

}