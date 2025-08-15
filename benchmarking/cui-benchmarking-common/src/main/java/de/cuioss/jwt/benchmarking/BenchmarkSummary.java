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
package de.cuioss.jwt.benchmarking;

import org.openjdk.jmh.results.RunResult;

import java.time.Instant;
import java.util.Collection;

/**
 * Summary of benchmark execution results for CI integration.
 * <p>
 * This class provides a structured summary of benchmark results that can be
 * consumed by CI systems for reporting and decision making.
 * 
 * @param type the type of benchmarks that were executed
 * @param timestamp when the benchmarks were executed
 * @param benchmarkCount number of benchmarks executed
 * @param totalScore overall performance score
 * @param successful whether all benchmarks completed successfully
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 */
public record BenchmarkSummary(
    BenchmarkType type,
    Instant timestamp,
    int benchmarkCount,
    double totalScore,
    boolean successful
) {

    /**
     * Creates a benchmark summary from JMH results.
     * 
     * @param results JMH benchmark results
     * @param type detected benchmark type
     */
    public BenchmarkSummary(Collection<RunResult> results, BenchmarkType type) {
        this(
            type,
            Instant.now(),
            results.size(),
            calculateTotalScore(results),
            areAllSuccessful(results)
        );
    }

    private static double calculateTotalScore(Collection<RunResult> results) {
        return results.stream()
            .mapToDouble(result -> {
                if (result.getPrimaryResult() != null && 
                    result.getPrimaryResult().getStatistics() != null) {
                    return result.getPrimaryResult().getScore();
                }
                return 0.0;
            })
            .average()
            .orElse(0.0);
    }

    private static boolean areAllSuccessful(Collection<RunResult> results) {
        return results.stream()
            .allMatch(result -> 
                result.getPrimaryResult() != null &&
                result.getPrimaryResult().getStatistics() != null &&
                result.getPrimaryResult().getStatistics().getN() > 0
            );
    }

    /**
     * Gets a human-readable description of the benchmark results.
     * 
     * @return description string
     */
    public String getDescription() {
        String status = successful ? "✅ PASSED" : "❌ FAILED";
        return String.format("%s %s benchmarks: %d tests, avg score: %.2f", 
                           status, type.getDisplayName(), benchmarkCount, totalScore);
    }
}