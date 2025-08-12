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
package de.cuioss.jwt.benchmarking.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openjdk.jmh.results.RunResult;

import java.time.Instant;
import java.util.Collection;

/**
 * Summary of benchmark execution for CI integration.
 * <p>
 * Provides a compact summary of benchmark results suitable for
 * CI/CD pipeline integration and automated processing.
 * </p>
 * 
 * @since 1.0
 */
public final class BenchmarkSummary {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final String timestamp;
    private final BenchmarkType benchmarkType;
    private final int benchmarkCount;
    private final double averageScore;
    private final double totalScore;
    private final String performanceGrade;
    private final boolean success;

    /**
     * Creates a benchmark summary from results.
     *
     * @param results       the benchmark results
     * @param benchmarkType the type of benchmark
     */
    public BenchmarkSummary(Collection<RunResult> results, BenchmarkType benchmarkType) {
        this.timestamp = Instant.now().toString();
        this.benchmarkType = benchmarkType;
        this.benchmarkCount = results.size();
        this.averageScore = calculateAverageScore(results);
        this.totalScore = calculateTotalScore(results);
        this.performanceGrade = calculatePerformanceGrade(this.averageScore);
        this.success = !results.isEmpty() && this.averageScore > 0;
    }

    /**
     * Converts this summary to JSON format.
     *
     * @return JSON representation of the summary
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    private double calculateAverageScore(Collection<RunResult> results) {
        if (results.isEmpty()) {
            return 0.0;
        }
        
        return results.stream()
            .mapToDouble(r -> r.getPrimaryResult().getScore())
            .average()
            .orElse(0.0);
    }

    private double calculateTotalScore(Collection<RunResult> results) {
        return results.stream()
            .mapToDouble(r -> r.getPrimaryResult().getScore())
            .sum();
    }

    private String calculatePerformanceGrade(double score) {
        if (score >= 100.0) return "A+";
        if (score >= 50.0) return "A";
        if (score >= 25.0) return "B";
        if (score >= 10.0) return "C";
        return "D";
    }

    // Getters for JSON serialization
    public String getTimestamp() { return timestamp; }
    public BenchmarkType getBenchmarkType() { return benchmarkType; }
    public int getBenchmarkCount() { return benchmarkCount; }
    public double getAverageScore() { return averageScore; }
    public double getTotalScore() { return totalScore; }
    public String getPerformanceGrade() { return performanceGrade; }
    public boolean isSuccess() { return success; }
}