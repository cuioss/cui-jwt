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
package de.cuioss.benchmarking.common.util;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;

/**
 * Provides structured logging messages for the cui-benchmarking-common module.
 * All messages follow the format: Benchmarking-[identifier]: [message]
 * <p>
 * Message identifier ranges:
 * <ul>
 *   <li>001-099: INFO messages</li>
 *   <li>100-199: WARN messages</li>
 *   <li>200-299: ERROR messages</li>
 *   <li>500-599: DEBUG messages</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class BenchmarkingLogMessages {

    private static final String PREFIX = "Benchmarking";

    /** Private constructor to prevent instantiation. */
    private BenchmarkingLogMessages() {
        // utility class
    }

    /**
     * INFO level messages for normal benchmark operations.
     */
    public static final class INFO {

        /** Private constructor to prevent instantiation. */
        private INFO() {
            // utility class
        }

        /** Message when starting benchmark runner. */
        public static final LogRecord BENCHMARK_RUNNER_STARTING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("Starting CUI benchmark runner...")
                .build();

        /** Message when benchmarks complete successfully. */
        public static final LogRecord BENCHMARKS_COMPLETED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(3)
                .template("Benchmarks completed successfully with %s results")
                .build();

        /** Message when all artifacts are generated. */
        public static final LogRecord ARTIFACTS_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(4)
                .template("All artifacts generated successfully")
                .build();

        /** Message when processing benchmark results. */
        public static final LogRecord PROCESSING_RESULTS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(5)
                .template("Processing %s benchmark results to generate artifacts")
                .build();

        /** Message showing detected benchmark type. */
        public static final LogRecord BENCHMARK_TYPE_DETECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(6)
                .template("Detected benchmark type: %s")
                .build();

        /** Message when generating performance badges. */
        public static final LogRecord GENERATING_BADGES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(7)
                .template("Generating performance badges for %s benchmarks")
                .build();

        /** Message when generating metrics. */
        public static final LogRecord GENERATING_METRICS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(8)
                .template("Generating performance metrics")
                .build();

        /** Message when generating HTML reports. */
        public static final LogRecord GENERATING_REPORTS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(9)
                .template("Generating HTML reports")
                .build();

        /** Message when generating GitHub Pages structure. */
        public static final LogRecord GENERATING_GITHUB_PAGES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(10)
                .template("Generating GitHub Pages deployment structure")
                .build();

        /** Message when writing benchmark summary. */
        public static final LogRecord WRITING_SUMMARY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(11)
                .template("Writing benchmark summary file")
                .build();

        /** Message when generating metrics JSON. */
        public static final LogRecord GENERATING_METRICS_JSON = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(12)
                .template("Generating metrics JSON for %s benchmark results")
                .build();

        /** Message when metrics file is generated. */
        public static final LogRecord METRICS_FILE_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(13)
                .template("Generated metrics file: %s")
                .build();

        /** Message when individual metric files are generated. */
        public static final LogRecord INDIVIDUAL_METRICS_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(14)
                .template("Generated %s individual metric files")
                .build();

        /** Message when performance badge is generated. */
        public static final LogRecord BADGE_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(15)
                .template("Generated %s badge: %s")
                .build();

        /** Message when GitHub Pages is being prepared. */
        public static final LogRecord PREPARING_GITHUB_PAGES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(16)
                .template("Preparing GitHub Pages deployment structure")
                .build();

        /** Message showing source directory. */
        public static final LogRecord SOURCE_DIRECTORY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(17)
                .template("Source: %s")
                .build();

        /** Message showing deploy directory. */
        public static final LogRecord DEPLOY_DIRECTORY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(18)
                .template("Deploy: %s")
                .build();

        /** Message when GitHub Pages is ready. */
        public static final LogRecord GITHUB_PAGES_READY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(19)
                .template("GitHub Pages deployment structure ready")
                .build();

        /** Message when generating index page. */
        public static final LogRecord GENERATING_INDEX_PAGE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(20)
                .template("Generating index page for %s benchmark results")
                .build();

        /** Message when index page is generated. */
        public static final LogRecord INDEX_PAGE_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(21)
                .template("Generated index page: %s")
                .build();

        /** Message when generating trends page. */
        public static final LogRecord GENERATING_TRENDS_PAGE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(22)
                .template("Generating trends page")
                .build();

        /** Message when trends page is generated. */
        public static final LogRecord TRENDS_PAGE_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(23)
                .template("Generated trends page: %s")
                .build();

        /** Message when writing benchmark summary. */
        public static final LogRecord WRITING_BENCHMARK_SUMMARY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(24)
                .template("Writing benchmark summary for %s %s results")
                .build();

        /** Message when summary file is generated. */
        public static final LogRecord SUMMARY_FILE_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(25)
                .template("Generated summary file: %s")
                .build();

        /** Message when JMH result file is copied to data directory. */
        public static final LogRecord JMH_RESULT_COPIED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(26)
                .template("Copied JMH result to data directory: %s")
                .build();

        /** Message when JWT validation micro benchmarks start with key cache initialized. */
        public static final LogRecord JWT_BENCHMARKS_STARTING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(27)
                .template("JWT validation micro benchmarks starting - Key cache initialized")
                .build();

        /** Message when RSA key pre-generation starts. */
        public static final LogRecord KEY_PREGENERATION_STARTING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(28)
                .template("BenchmarkKeyCache: Starting RSA key pre-generation...")
                .build();

        /** Message when RSA key pre-generation completes. */
        public static final LogRecord KEY_PREGENERATION_COMPLETED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(29)
                .template("BenchmarkKeyCache: Pre-generated keys for %s issuer configurations in %s ms")
                .build();

        /** Message when unified report generation starts. */
        public static final LogRecord UNIFIED_REPORT_GENERATION_START = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(30)
                .template("Starting unified report generation")
                .build();

        /** Message when unified report generation completes. */
        public static final LogRecord UNIFIED_REPORT_GENERATION_COMPLETE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(31)
                .template("Unified report generation completed")
                .build();

        /** Message when generating data file. */
        public static final LogRecord GENERATING_DATA_FILE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(32)
                .template("Generating benchmark data JSON file")
                .build();

        /** Message when data file is generated. */
        public static final LogRecord DATA_FILE_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(33)
                .template("Generated data file: %s")
                .build();

        /** Message when generating HTML files. */
        public static final LogRecord GENERATING_HTML_FILES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(34)
                .template("Generating HTML report files")
                .build();

        /** Message when HTML files are generated. */
        public static final LogRecord HTML_FILES_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(35)
                .template("Generated HTML files in: %s")
                .build();

        /** Message when generating badges (unified). */
        public static final LogRecord GENERATING_BADGES_UNIFIED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(36)
                .template("Generating performance badges")
                .build();

        /** Message when badges are generated. */
        public static final LogRecord BADGES_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(37)
                .template("Generated badges in: %s")
                .build();

        /** Message when generating API endpoints. */
        public static final LogRecord GENERATING_API_ENDPOINTS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(38)
                .template("Generating API endpoint files")
                .build();

        /** Message when API endpoints are generated. */
        public static final LogRecord API_ENDPOINTS_GENERATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(39)
                .template("Generated API endpoints in: %s")
                .build();

        /** Message when copying Prometheus metrics. */
        public static final LogRecord COPYING_PROMETHEUS_METRICS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(40)
                .template("Copying Prometheus metrics to deployment directory")
                .build();

        /** Message when Prometheus metrics are copied. */
        public static final LogRecord PROMETHEUS_METRICS_COPIED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(41)
                .template("Copied Prometheus metrics to: %s")
                .build();

        /** Message when copying support files. */
        public static final LogRecord COPYING_SUPPORT_FILES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(42)
                .template("Copying support files (CSS, JS)")
                .build();

        /** Message when support files are copied. */
        public static final LogRecord SUPPORT_FILES_COPIED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(43)
                .template("Copied support files to: %s")
                .build();

        /** Message when key cache is initialized. */
        public static final LogRecord KEY_CACHE_INITIALIZED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(44)
                .template("BenchmarkKeyCache: Initialized with %s configurations")
                .build();

        /** Message when Quarkus JWT integration benchmarks start. */
        public static final LogRecord QUARKUS_BENCHMARKS_STARTING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(45)
                .template("Quarkus JWT integration benchmarks starting - Service: %s, Keycloak: %s")
                .build();

        /** Message when processing results starts. */
        public static final LogRecord PROCESSING_RESULTS_STARTING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(46)
                .template("QuarkusIntegrationRunner.processResults() - Starting with %s results")
                .build();

        /** Message when results are available. */
        public static final LogRecord RESULTS_AVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(47)
                .template("Results available at: %s")
                .build();

        /** Message when starting to process WRK results. */
        public static final LogRecord WRK_PROCESSING_START = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(48)
                .template("Start processing WRK results")
                .build();

        /** Message when JFR instrumented benchmarks start. */
        public static final LogRecord JFR_BENCHMARKS_STARTING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(49)
                .template("JFR-instrumented benchmarks starting - Key cache initialized")
                .build();

        /** Message when JFR recording will be saved. */
        public static final LogRecord JFR_RECORDING_PATH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(50)
                .template("JFR recording will be saved to: %s")
                .build();

        /** Message when JFR benchmark completed. */
        public static final LogRecord JFR_BENCHMARK_COMPLETED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(51)
                .template("JFR benchmark completed. To analyze variance:")
                .build();

        /** Message showing how to run variance analysis. */
        public static final LogRecord JFR_VARIANCE_COMMAND = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(52)
                .template("java -cp \"target/classes:target/dependency/*\" de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer %s")
                .build();

    }

    /**
     * WARN level messages for potential issues.
     */
    public static final class WARN {

        /** Private constructor to prevent instantiation. */
        private WARN() {
            // utility class
        }

        /** Warning when failed to copy HTML file. */
        public static final LogRecord FAILED_COPY_HTML = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Failed to copy HTML file: %s")
                .build();

        /** Warning when failed to copy badge file. */
        public static final LogRecord FAILED_COPY_BADGE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("Failed to copy badge file: %s")
                .build();

        /** Warning when failed to copy data file. */
        public static final LogRecord FAILED_COPY_DATA = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(102)
                .template("Failed to copy data file: %s")
                .build();

        /** Warning for issues during index generation or processing. */
        public static final LogRecord ISSUE_DURING_INDEX_GENERATION = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(103)
                .template("Issue during %s")
                .build();

        /** Warning when key cache miss occurs during benchmark. */
        public static final LogRecord KEY_CACHE_MISS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(104)
                .template("BenchmarkKeyCache miss for count=%s. Generating keys during benchmark!")
                .build();

        /** Warning when invalid metrics data type. */
        public static final LogRecord INVALID_METRICS_TYPE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(105)
                .template("Invalid metrics data type: expected TokenValidatorMonitor, got %s")
                .build();
    }

    /**
     * ERROR level messages for serious failures.
     */
    public static final class ERROR {

        /** Private constructor to prevent instantiation. */
        private ERROR() {
            // utility class
        }

        /** Error when export of benchmark metrics fails. */
        public static final LogRecord EXPORT_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("Failed to export benchmark metrics")
                .build();

        /** Error when WRK result processor fails. */
        public static final LogRecord WRK_PROCESSOR_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(201)
                .template("Failed to execute WRK result processor")
                .build();

        /** Error when WRK usage is incorrect. */
        public static final LogRecord WRK_USAGE_ERROR = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(202)
                .template("Usage: WrkResultPostProcessor <input-dir> [output-dir]")
                .build();

        /** Error when no WRK output files found. */
        public static final LogRecord NO_WRK_FILES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(203)
                .template("No WRK output files found in: %s")
                .build();

        /** Error when WRK output directory does not exist. */
        public static final LogRecord WRK_DIR_NOT_EXIST = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(204)
                .template("WRK output directory does not exist: %s")
                .build();

        /** Error when no benchmark data extracted. */
        public static final LogRecord NO_BENCHMARK_DATA = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(205)
                .template("No benchmark data extracted from WRK output files")
                .build();

        /** Error when cannot collect Prometheus metrics. */
        public static final LogRecord NO_PROMETHEUS_METADATA = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(206)
                .template("Cannot collect Prometheus metrics: no benchmark metadata available")
                .build();

        /** Error when no metadata found for benchmark. */
        public static final LogRecord NO_METADATA_FOR_BENCHMARK = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(207)
                .template("No metadata found for benchmark: %s")
                .build();

        /** Error when failed to copy Prometheus metrics file. */
        public static final LogRecord FAILED_COPY_PROMETHEUS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(208)
                .template("Failed to copy Prometheus metrics file: %s")
                .build();

        /** Error when failed to copy Prometheus metrics to deployment directory. */
        public static final LogRecord FAILED_COPY_PROMETHEUS_DIR = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(209)
                .template("Failed to copy Prometheus metrics to deployment directory")
                .build();

        /** Error when missing timestamps in results. */
        public static final LogRecord MISSING_TIMESTAMPS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(210)
                .template("Missing start or end timestamp in result file: %s")
                .build();

        /** Error when failed to parse metadata. */
        public static final LogRecord FAILED_PARSE_METADATA = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(211)
                .template("Failed to parse metadata from %s: %s")
                .build();

        /** Error when incomplete metadata found. */
        public static final LogRecord INCOMPLETE_METADATA = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(212)
                .template("Incomplete metadata in file %s (name=%s, start=%s, end=%s)")
                .build();
    }

}