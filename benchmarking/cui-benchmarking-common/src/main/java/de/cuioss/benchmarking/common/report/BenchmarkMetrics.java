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

/**
 * Immutable record containing all computed benchmark metrics.
 * This is the central data structure shared across all report generators.
 */
public record BenchmarkMetrics(
String throughputBenchmarkName,
String latencyBenchmarkName,
double throughput,
double latency,
double performanceScore,
String performanceGrade,
String throughputFormatted,
String latencyFormatted,
String performanceScoreFormatted
) {
    public BenchmarkMetrics {
        if (throughputBenchmarkName == null || throughputBenchmarkName.isBlank()) {
            throw new IllegalArgumentException("Throughput benchmark name must be specified");
        }
        if (latencyBenchmarkName == null || latencyBenchmarkName.isBlank()) {
            throw new IllegalArgumentException("Latency benchmark name must be specified");
        }
        if (throughput <= 0) {
            throw new IllegalArgumentException("Throughput must be positive, got: " + throughput);
        }
        if (latency <= 0) {
            throw new IllegalArgumentException("Latency must be positive, got: " + latency);
        }
        if (performanceScore < 0) {
            throw new IllegalArgumentException("Performance score must be non-negative, got: " + performanceScore);
        }
    }
}