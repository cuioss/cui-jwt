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
    }

    /**
     * DEBUG level messages for detailed diagnostic information.
     */
    public static final class DEBUG {

        /** Private constructor to prevent instantiation. */
        private DEBUG() {
            // utility class
        }

        /** Debug message when copying HTML files. */
        public static final LogRecord COPYING_HTML_FILES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(500)
                .template("Copying HTML files")
                .build();

        /** Debug message when creating API endpoints. */
        public static final LogRecord CREATING_API_ENDPOINTS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(501)
                .template("Creating API endpoints")
                .build();

        /** Debug message when API endpoint is created. */
        public static final LogRecord API_ENDPOINT_CREATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(502)
                .template("Created API endpoint: %s")
                .build();

        /** Debug message when copying badge files. */
        public static final LogRecord COPYING_BADGE_FILES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(503)
                .template("Copying badge files")
                .build();

        /** Debug message when copying data files. */
        public static final LogRecord COPYING_DATA_FILES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(504)
                .template("Copying data files")
                .build();

        /** Debug message when generating additional pages. */
        public static final LogRecord GENERATING_ADDITIONAL_PAGES = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(505)
                .template("Generating additional pages")
                .build();
    }
}