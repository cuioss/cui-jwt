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
import de.cuioss.benchmarking.common.constants.BenchmarkConstants;

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Metrics.Modes.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Metrics.Units.OPS;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Metrics.Units.SUFFIX_OP;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Errors.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Grades.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.JsonFields.*;

/**
 * Computes benchmark-specific metrics from JSON results.
 * <p>
 * This class is responsible for extracting and calculating domain-specific benchmark metrics:
 * <ul>
 *   <li>Extracting throughput and latency values from JMH JSON output</li>
 *   <li>Converting units to standard measurements (ops/s, ms/op)</li>
 *   <li>Calculating composite performance scores</li>
 *   <li>Assigning performance grades based on scores</li>
 * </ul>
 * <p>
 * Use this class when you need to process raw JMH benchmark results into meaningful metrics.
 * For pure statistical computations, use {@link StatisticsCalculator}.
 * For time-series analysis and trend detection, use {@link TrendDataProcessor}.
 * 
 * @see StatisticsCalculator for pure statistical computations
 * @see TrendDataProcessor for trend analysis and historical data processing
 */
public class MetricsComputer {

    private final String throughputBenchmarkName;
    private final String latencyBenchmarkName;

    public MetricsComputer(String throughputBenchmarkName, String latencyBenchmarkName) {
        if (throughputBenchmarkName == null || throughputBenchmarkName.isBlank()) {
            throw new IllegalArgumentException(THROUGHPUT_NAME_REQUIRED);
        }
        if (latencyBenchmarkName == null || latencyBenchmarkName.isBlank()) {
            throw new IllegalArgumentException(LATENCY_NAME_REQUIRED);
        }
        this.throughputBenchmarkName = throughputBenchmarkName;
        this.latencyBenchmarkName = latencyBenchmarkName;
    }

    public BenchmarkMetrics computeMetrics(JsonArray benchmarks) {
        if (benchmarks == null || benchmarks.isEmpty()) {
            throw new IllegalArgumentException(NO_RESULTS_PROVIDED);
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
            String benchmarkName = benchmark.get(BENCHMARK).getAsString();

            if (benchmarkName.contains(throughputBenchmarkName)) {
                String mode = benchmark.get(MODE).getAsString();
                JsonObject primaryMetric = benchmark.getAsJsonObject(PRIMARY_METRIC);
                double score = primaryMetric.get(SCORE).getAsDouble();
                String unit = primaryMetric.get(SCORE_UNIT).getAsString();

                if (BenchmarkConstants.Metrics.Modes.THROUGHPUT.equals(mode) || unit.contains(OPS)) {
                    return MetricConversionUtil.convertToOpsPerSecond(score, unit);
                }
            }
        }

        throw new IllegalStateException(
                THROUGHPUT_NOT_FOUND_FORMAT.formatted(throughputBenchmarkName));
    }

    private double extractLatency(JsonArray benchmarks) {
        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String benchmarkName = benchmark.get(BENCHMARK).getAsString();

            if (benchmarkName.contains(latencyBenchmarkName)) {
                String mode = benchmark.get(MODE).getAsString();
                JsonObject primaryMetric = benchmark.getAsJsonObject(PRIMARY_METRIC);
                double score = primaryMetric.get(SCORE).getAsDouble();
                String unit = primaryMetric.get(SCORE_UNIT).getAsString();

                if (AVERAGE_TIME.equals(mode) || SAMPLE.equals(mode) || unit.contains(SUFFIX_OP)) {
                    return MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                }
            }
        }

        throw new IllegalStateException(
                LATENCY_NOT_FOUND_FORMAT.formatted(latencyBenchmarkName));
    }

    private double calculatePerformanceScore(double throughput, double latency) {
        double throughputScore = (throughput / 100.0) * 0.5;
        double latencyScore = (100.0 / latency) * 0.5;
        return throughputScore + latencyScore;
    }

    private String getPerformanceGrade(double score) {
        if (score >= 95) return A_PLUS;
        if (score >= 90) return A;
        if (score >= 75) return B;
        if (score >= 60) return C;
        if (score >= 40) return D;
        return F;
    }

}