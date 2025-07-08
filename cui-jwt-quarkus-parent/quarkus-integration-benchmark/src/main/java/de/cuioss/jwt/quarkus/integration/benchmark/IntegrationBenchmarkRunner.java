/**
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
package de.cuioss.jwt.quarkus.integration.benchmark;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;

/**
 * Main entry point for running JMH integration benchmarks.
 * This class provides the entry point for executing all integration benchmarks
 * in a containerized environment with Quarkus native execution.
 */
public class IntegrationBenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(IntegrationBenchmarkRunner.class);

    /**
     * Main method to run all integration benchmarks.
     *
     * @param args Command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {
        LOGGER.info("🚀 Starting JWT Quarkus Integration Benchmarks");
        LOGGER.info("📊 Running in containerized environment with native Quarkus");

        // Configure JMH options
        Options options = new OptionsBuilder()
                // Include all integration benchmark classes in this package
                .include("de\\.cuioss\\.jwt\\.quarkus\\.integration\\.benchmark\\..+Benchmark")
                // Set number of forks
                .forks(Integer.getInteger("jmh.forks", 1))
                // Set warmup iterations
                .warmupIterations(Integer.getInteger("jmh.warmupIterations", 3))
                // Set measurement iterations
                .measurementIterations(Integer.getInteger("jmh.iterations", 5))
                // Set measurement time
                .measurementTime(getMeasurementTime())
                // Set warmup time
                .warmupTime(getWarmupTime())
                // Set number of threads
                .threads(getThreadCount())
                // Configure result output
                .resultFormat(getResultFormat())
                .result(getResultFile())
                .build();

        // Run the benchmarks
        new Runner(options).run();

        LOGGER.info("✅ Integration benchmarks completed");
    }

    /**
     * Gets the result format from system property or defaults to JSON.
     */
    private static ResultFormatType getResultFormat() {
        String format = System.getProperty("jmh.result.format", "JSON");
        try {
            return ResultFormatType.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResultFormatType.JSON;
        }
    }

    /**
     * Gets the result file from system property or defaults to integration-benchmark-result.json.
     */
    private static String getResultFile() {
        String filePrefix = System.getProperty("jmh.result.filePrefix");
        if (filePrefix != null && !filePrefix.isEmpty()) {
            String resultFile = filePrefix + ".json";
            // Ensure parent directory exists
            File file = new File(resultFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            return resultFile;
        }
        return "integration-benchmark-result.json";
    }

    /**
     * Gets the measurement time from system property or defaults to 2 seconds.
     */
    private static TimeValue getMeasurementTime() {
        String time = System.getProperty("jmh.time", "2s");
        return parseTimeValue(time);
    }

    /**
     * Gets the warmup time from system property or defaults to 2 seconds.
     */
    private static TimeValue getWarmupTime() {
        String time = System.getProperty("jmh.warmupTime", "2s");
        return parseTimeValue(time);
    }

    /**
     * Gets the thread count from system property. Supports 'MAX' for maximum available cores.
     */
    private static int getThreadCount() {
        String threads = System.getProperty("jmh.threads", "2");
        if ("MAX".equals(threads)) {
            return Runtime.getRuntime().availableProcessors();
        }
        try {
            return Integer.parseInt(threads);
        } catch (NumberFormatException e) {
            return 2; // Default fallback
        }
    }

    /**
     * Parses a time value string (e.g., "2s", "1000ms") into a TimeValue object.
     */
    private static TimeValue parseTimeValue(String timeStr) {
        if (timeStr.endsWith("s")) {
            return TimeValue.seconds(Integer.parseInt(timeStr.substring(0, timeStr.length() - 1)));
        } else if (timeStr.endsWith("ms")) {
            return TimeValue.milliseconds(Integer.parseInt(timeStr.substring(0, timeStr.length() - 2)));
        } else {
            // Default to seconds if no unit specified
            return TimeValue.seconds(Integer.parseInt(timeStr));
        }
    }
}