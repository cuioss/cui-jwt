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
 * Constants for benchmark report generation.
 */
final class ReportConstants {
    
    private ReportConstants() {
        // Constants class
    }
    
    // JSON field names
    static final String FIELD_PRIMARY_METRIC = "primaryMetric";
    static final String FIELD_SECONDARY_METRICS = "secondaryMetrics";
    static final String FIELD_BENCHMARK = "benchmark";
    static final String FIELD_MODE = "mode";
    static final String FIELD_SCORE = "score";
    static final String FIELD_SCORE_UNIT = "scoreUnit";
    static final String FIELD_RAW_DATA = "rawData";
    static final String FIELD_SCORE_PERCENTILES = "scorePercentiles";
    static final String FIELD_TIMESTAMP = "timestamp";
    static final String FIELD_BENCHMARKS = "benchmarks";
    static final String FIELD_SUMMARY = "summary";
    
    // Unit strings
    static final String UNIT_OPS_PER_SEC = "ops/s";
    static final String UNIT_OPS_PER_SEC_ALT = "ops/sec";
    static final String UNIT_OPS_PER_MS = "ops/ms";
    static final String UNIT_OPS_PER_US = "ops/us";
    static final String UNIT_OPS_PER_NS = "ops/ns";
    static final String UNIT_SEC_PER_OP = "s/op";
    static final String UNIT_MS_PER_OP = "ms/op";
    static final String UNIT_US_PER_OP = "us/op";
    static final String UNIT_NS_PER_OP = "ns/op";
    static final String UNIT_OPS = "ops";
    
    // Percentile keys
    static final String PERCENTILE_50 = "50.0";
    static final String PERCENTILE_95 = "95.0";
    static final String PERCENTILE_99 = "99.0";
    
    // Stats field names
    static final String STATS_MEAN = "mean";
    static final String STATS_STDDEV = "stddev";
    static final String STATS_MIN = "min";
    static final String STATS_MAX = "max";
    static final String STATS_N = "n";
    static final String STATS_P50 = "p50";
    static final String STATS_P95 = "p95";
    static final String STATS_P99 = "p99";
    
    // Performance grades
    static final String GRADE_A_PLUS = "A+";
    static final String GRADE_A = "A";
    static final String GRADE_B = "B";
    static final String GRADE_C = "C";
    static final String GRADE_D = "D";
    static final String GRADE_F = "F";
    
    // Conversion factors
    static final double MILLIS_TO_SECONDS = 1000.0;
    static final double MICROS_TO_SECONDS = 1_000_000.0;
    static final double NANOS_TO_SECONDS = 1_000_000_000.0;
    static final double MICROS_TO_MILLIS = 1000.0;
    static final double NANOS_TO_MILLIS = 1_000_000.0;
}