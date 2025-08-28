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

import lombok.experimental.UtilityClass;

/**
 * Constants for benchmark report generation.
 * Organized following the DSL-Style Nested Constants Pattern.
 */
@UtilityClass public final class ReportConstants {

    /**
     * JSON field names used in benchmark data structures.
     */
    @UtilityClass public static final class JSON_FIELDS {
        // Primary fields
        public static final String PRIMARY_METRIC = "primaryMetric";
        public static final String SECONDARY_METRICS = "secondaryMetrics";
        public static final String BENCHMARK = "benchmark";
        public static final String MODE = "mode";
        public static final String SCORE = "score";
        public static final String SCORE_UNIT = "scoreUnit";
        public static final String RAW_DATA = "rawData";
        public static final String SCORE_PERCENTILES = "scorePercentiles";
        public static final String SCORE_ERROR = "scoreError";
        public static final String SCORE_CONFIDENCE = "scoreConfidence";
        public static final String TIMESTAMP = "timestamp";
        public static final String BENCHMARKS = "benchmarks";
        public static final String SUMMARY = "summary";

        // Report data fields
        public static final String METADATA = "metadata";
        public static final String OVERVIEW = "overview";
        public static final String CHART_DATA = "chartData";
        public static final String TRENDS = "trends";
        public static final String DISPLAY_TIMESTAMP = "displayTimestamp";
        public static final String BENCHMARK_TYPE = "benchmarkType";
        public static final String REPORT_VERSION = "reportVersion";

        // Metrics fields
        public static final String THROUGHPUT = "throughput";
        public static final String LATENCY = "latency";
        public static final String THROUGHPUT_BENCHMARK_NAME = "throughputBenchmarkName";
        public static final String LATENCY_BENCHMARK_NAME = "latencyBenchmarkName";
        public static final String PERFORMANCE_SCORE = "performanceScore";
        public static final String PERFORMANCE_GRADE = "performanceGrade";
        public static final String PERFORMANCE_GRADE_CLASS = "performanceGradeClass";

        // Result fields
        public static final String NAME = "name";
        public static final String FULL_NAME = "fullName";
        public static final String ERROR = "error";
        public static final String ERROR_PERCENTAGE = "errorPercentage";
        public static final String CONFIDENCE_LOW = "confidenceLow";
        public static final String CONFIDENCE_HIGH = "confidenceHigh";
        public static final String PERCENTILES = "percentiles";

        // Chart fields
        public static final String LABELS = "labels";
        public static final String PERCENTILES_DATA = "percentilesData";
        public static final String PERCENTILE_LABELS = "percentileLabels";
        public static final String DATA = "data";
        public static final String DATASETS = "datasets";

        // Status fields
        public static final String STATUS = "status";
        public static final String GENERATED = "generated";
        public static final String LAST_RUN = "last_run";
        public static final String SERVICES = "services";
        public static final String LINKS = "links";
        public static final String AVAILABLE = "available";

        // Summary fields
        public static final String TOTAL_BENCHMARKS = "total_benchmarks";
        public static final String PERFORMANCE_GRADE_KEY = "performance_grade";
        public static final String AVERAGE_THROUGHPUT = "average_throughput";
    }

    /**
     * Badge-related constants for shields.io integration.
     */
    @UtilityClass public static final class BADGE {
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String LABEL = "label";
        public static final String MESSAGE = "message";
        public static final String COLOR = "color";

        /**
         * Badge colors following shields.io color scheme.
         */
        @UtilityClass public static final class COLORS {
            public static final String BRIGHT_GREEN = "brightgreen";
            public static final String GREEN = "green";
            public static final String YELLOW = "yellow";
            public static final String ORANGE = "orange";
            public static final String RED = "red";
            public static final String BLUE = "blue";
        }

        /**
         * Badge labels.
         */
        @UtilityClass public static final class LABELS {
            public static final String PERFORMANCE_TREND = "Performance Trend";
            public static final String LAST_RUN = "Last Run";
        }

        /**
         * Trend indicators.
         */
        @UtilityClass public static final class TRENDS {
            public static final String STABLE = "→ stable";
            public static final String IMPROVING_FORMAT = "↑ +%.1f%%";
            public static final String DEGRADING_FORMAT = "↓ %.1f%%";
        }
    }

    /**
     * Unit strings for benchmark measurements.
     */
    @UtilityClass public static final class UNITS {
        // Operations per time unit
        public static final String OPS_PER_SEC = "ops/s";
        public static final String OPS_PER_SEC_ALT = "ops/sec";
        public static final String OPS_PER_MS = "ops/ms";
        public static final String OPS_PER_US = "ops/us";
        public static final String OPS_PER_NS = "ops/ns";
        public static final String OPS = "ops";

        // Time per operation
        public static final String SEC_PER_OP = "s/op";
        public static final String MS_PER_OP = "ms/op";
        public static final String US_PER_OP = "us/op";
        public static final String NS_PER_OP = "ns/op";

        // Display suffixes
        public static final String K_OPS_S = "K ops/s";
        public static final String M_OPS_S = "M ops/s";
        public static final String SPACE_OPS_S = " ops/s";
        public static final String SUFFIX_S = "s";
        public static final String SUFFIX_MS = "ms";
        public static final String SUFFIX_OP = "/op";
    }

    /**
     * Conversion factors between different units.
     */
    @UtilityClass public static final class CONVERSIONS {
        public static final double MILLIS_TO_SECONDS = 1000.0;
        public static final double MICROS_TO_SECONDS = 1_000_000.0;
        public static final double NANOS_TO_SECONDS = 1_000_000_000.0;
        public static final double MICROS_TO_MILLIS = 1000.0;
        public static final double NANOS_TO_MILLIS = 1_000_000.0;
    }

    /**
     * Performance grade constants.
     */
    @UtilityClass public static final class GRADES {
        public static final String A_PLUS = "A+";
        public static final String A = "A";
        public static final String B = "B";
        public static final String C = "C";
        public static final String D = "D";
        public static final String F = "F";

        /**
         * CSS class names for grades.
         */
        @UtilityClass public static final class CSS_CLASSES {
            public static final String GRADE_A_PLUS = "grade-a-plus";
            public static final String GRADE_A = "grade-a";
            public static final String GRADE_B = "grade-b";
            public static final String GRADE_C = "grade-c";
            public static final String GRADE_D = "grade-d";
            public static final String GRADE_F = "grade-f";
            public static final String GRADE_UNKNOWN = "grade-unknown";
        }
    }

    /**
     * Percentile-related constants.
     */
    @UtilityClass public static final class PERCENTILES {
        // Percentile keys
        public static final String P_0 = "0.0";
        public static final String P_50 = "50.0";
        public static final String P_90 = "90.0";
        public static final String P_95 = "95.0";
        public static final String P_99 = "99.0";
        public static final String P_99_9 = "99.9";
        public static final String P_99_99 = "99.99";
        public static final String P_100 = "100.0";

        // Percentile labels
        public static final String LABEL_P0 = "P0";
        public static final String LABEL_P50 = "P50";
        public static final String LABEL_P90 = "P90";
        public static final String LABEL_P95 = "P95";
        public static final String LABEL_P99 = "P99";
        public static final String LABEL_P99_9 = "P99.9";
        public static final String LABEL_P99_99 = "P99.99";
        public static final String LABEL_P100 = "P100";

        public static final String SUFFIX_TH = "th";
    }

    /**
     * File and directory names.
     */
    @UtilityClass public static final class FILES {
        // HTML files
        public static final String INDEX_HTML = "index.html";
        public static final String TRENDS_HTML = "trends.html";
        public static final String DETAILED_HTML = "detailed.html";
        public static final String ERROR_404_HTML = "404.html";

        // Support files
        public static final String REPORT_STYLES_CSS = "report-styles.css";
        public static final String DATA_LOADER_JS = "data-loader.js";
        public static final String ROBOTS_TXT = "robots.txt";
        public static final String SITEMAP_XML = "sitemap.xml";

        // Data files
        public static final String BENCHMARK_DATA_JSON = "benchmark-data.json";
        public static final String BENCHMARK_SUMMARY_JSON = "data/benchmark-summary.json";
        public static final String LAST_RUN_BADGE_JSON = "last-run-badge.json";
        public static final String INTEGRATION_PERFORMANCE_BADGE_JSON = "integration-performance-badge.json";
        public static final String PERFORMANCE_BADGE_JSON = "performance-badge.json";
        public static final String INTEGRATION_TREND_BADGE_JSON = "integration-trend-badge.json";

        // Directories
        public static final String REPORTS_DIR = "reports";
        public static final String BADGES_DIR = "badges";
        public static final String DATA_DIR = "data";
        public static final String API_DIR = "api";

        // Extensions
        public static final String EXT_HTML = ".html";
        public static final String EXT_JSON = ".json";
    }

    /**
     * API endpoint paths.
     */
    @UtilityClass public static final class API {
        public static final String LATEST_JSON = "latest.json";
        public static final String BENCHMARKS_JSON = "benchmarks.json";
        public static final String STATUS_JSON = "status.json";

        // API paths
        public static final String API_BENCHMARKS_PATH = "/api/benchmarks.json";
        public static final String BADGES_PATH = "/badges/";

        // Status values
        public static final String STATUS_OPERATIONAL = "operational";
        public static final String STATUS_NO_DATA = "no_data";
        public static final String STATUS_HEALTHY = "healthy";
        public static final String STATUS_SUCCESS = "success";
    }

    /**
     * Benchmark modes.
     */
    @UtilityClass public static final class MODES {
        public static final String THROUGHPUT = "thrpt";
        public static final String AVERAGE_TIME = "avgt";
        public static final String SAMPLE = "sample";
    }

    /**
     * Default values for missing data.
     */
    @UtilityClass public static final class DEFAULTS {
        public static final String N_A = "N/A";
        public static final String NOT_AVAILABLE = "n/a";
    }

    /**
     * Template paths and resources.
     */
    @UtilityClass public static final class TEMPLATES {
        public static final String PATH_PREFIX = "/templates/";
        public static final String NOT_FOUND_FORMAT = "Template not found: %s";
    }

    /**
     * Error messages.
     */
    @UtilityClass public static final class ERRORS {
        public static final String THROUGHPUT_NAME_REQUIRED = "Throughput benchmark name must be specified";
        public static final String LATENCY_NAME_REQUIRED = "Latency benchmark name must be specified";
        public static final String NO_RESULTS_PROVIDED = "No benchmark results provided";
        public static final String THROUGHPUT_NOT_FOUND_FORMAT = "Required throughput benchmark '%s' not found in results";
        public static final String LATENCY_NOT_FOUND_FORMAT = "Required latency benchmark '%s' not found in results";
        public static final String THROUGHPUT_POSITIVE = "Throughput must be positive, got: ";
        public static final String LATENCY_POSITIVE = "Latency must be positive, got: ";
        public static final String SCORE_NON_NEGATIVE = "Performance score must be non-negative, got: ";
    }

    /**
     * Messages for UI display.
     */
    @UtilityClass public static final class MESSAGES {
        public static final String HISTORICAL_DATA_NOT_AVAILABLE = "Historical data not yet available";
    }

    /**
     * Date/time format patterns.
     */
    @UtilityClass public static final class DATE_FORMATS {
        public static final String DISPLAY_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss 'UTC'";
    }

    /**
     * Version information.
     */
    @UtilityClass public static final class VERSIONS {
        public static final String REPORT_VERSION = "2.0.0";
    }

    /**
     * Statistical field names (for backward compatibility).
     */
    @UtilityClass public static final class STATS {
        public static final String MEAN = "mean";
        public static final String STDDEV = "stddev";
        public static final String MIN = "min";
        public static final String MAX = "max";
        public static final String N = "n";
        public static final String P50 = "p50";
        public static final String P95 = "p95";
        public static final String P99 = "p99";
    }

    // Legacy field name constants for backward compatibility (deprecated)
    @Deprecated
    static final String FIELD_PRIMARY_METRIC = JSON_FIELDS.PRIMARY_METRIC;
    @Deprecated
    static final String FIELD_SECONDARY_METRICS = JSON_FIELDS.SECONDARY_METRICS;
    @Deprecated
    static final String FIELD_BENCHMARK = JSON_FIELDS.BENCHMARK;
    @Deprecated
    static final String FIELD_MODE = JSON_FIELDS.MODE;
    @Deprecated
    static final String FIELD_SCORE = JSON_FIELDS.SCORE;
    @Deprecated
    static final String FIELD_SCORE_UNIT = JSON_FIELDS.SCORE_UNIT;
    @Deprecated
    static final String FIELD_RAW_DATA = JSON_FIELDS.RAW_DATA;
    @Deprecated
    static final String FIELD_SCORE_PERCENTILES = JSON_FIELDS.SCORE_PERCENTILES;
    @Deprecated
    static final String FIELD_TIMESTAMP = JSON_FIELDS.TIMESTAMP;
    @Deprecated
    static final String FIELD_BENCHMARKS = JSON_FIELDS.BENCHMARKS;
    @Deprecated
    static final String FIELD_SUMMARY = JSON_FIELDS.SUMMARY;

    // Legacy unit constants for backward compatibility (deprecated)
    @Deprecated
    static final String UNIT_OPS_PER_SEC = UNITS.OPS_PER_SEC;
    @Deprecated
    static final String UNIT_OPS_PER_SEC_ALT = UNITS.OPS_PER_SEC_ALT;
    @Deprecated
    static final String UNIT_OPS_PER_MS = UNITS.OPS_PER_MS;
    @Deprecated
    static final String UNIT_OPS_PER_US = UNITS.OPS_PER_US;
    @Deprecated
    static final String UNIT_OPS_PER_NS = UNITS.OPS_PER_NS;
    @Deprecated
    static final String UNIT_SEC_PER_OP = UNITS.SEC_PER_OP;
    @Deprecated
    static final String UNIT_MS_PER_OP = UNITS.MS_PER_OP;
    @Deprecated
    static final String UNIT_US_PER_OP = UNITS.US_PER_OP;
    @Deprecated
    static final String UNIT_NS_PER_OP = UNITS.NS_PER_OP;
    @Deprecated
    static final String UNIT_OPS = UNITS.OPS;

    // Legacy percentile constants for backward compatibility (deprecated)
    @Deprecated
    static final String PERCENTILE_50 = PERCENTILES.P_50;
    @Deprecated
    static final String PERCENTILE_95 = PERCENTILES.P_95;
    @Deprecated
    static final String PERCENTILE_99 = PERCENTILES.P_99;

    // Legacy stats constants for backward compatibility (deprecated)
    @Deprecated
    static final String STATS_MEAN = STATS.MEAN;
    @Deprecated
    static final String STATS_STDDEV = STATS.STDDEV;
    @Deprecated
    static final String STATS_MIN = STATS.MIN;
    @Deprecated
    static final String STATS_MAX = STATS.MAX;
    @Deprecated
    static final String STATS_N = STATS.N;
    @Deprecated
    static final String STATS_P50 = STATS.P50;
    @Deprecated
    static final String STATS_P95 = STATS.P95;
    @Deprecated
    static final String STATS_P99 = STATS.P99;

    // Legacy grade constants for backward compatibility (deprecated)
    @Deprecated
    static final String GRADE_A_PLUS = GRADES.A_PLUS;
    @Deprecated
    static final String GRADE_A = GRADES.A;
    @Deprecated
    static final String GRADE_B = GRADES.B;
    @Deprecated
    static final String GRADE_C = GRADES.C;
    @Deprecated
    static final String GRADE_D = GRADES.D;
    @Deprecated
    static final String GRADE_F = GRADES.F;

    // Legacy conversion factors for backward compatibility (deprecated)
    @Deprecated
    static final double MILLIS_TO_SECONDS = CONVERSIONS.MILLIS_TO_SECONDS;
    @Deprecated
    static final double MICROS_TO_SECONDS = CONVERSIONS.MICROS_TO_SECONDS;
    @Deprecated
    static final double NANOS_TO_SECONDS = CONVERSIONS.NANOS_TO_SECONDS;
    @Deprecated
    static final double MICROS_TO_MILLIS = CONVERSIONS.MICROS_TO_MILLIS;
    @Deprecated
    static final double NANOS_TO_MILLIS = CONVERSIONS.NANOS_TO_MILLIS;
}