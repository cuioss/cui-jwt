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
package de.cuioss.jwt.validation.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;

/**
 * Main class for running optimized benchmarks.
 * <p>
 * This class collects and runs all benchmark classes in the package.
 * It configures JMH with optimized settings for fast execution (&lt;10 minutes)
 * and produces a combined JSON report.
 * <p>
 * Optimized benchmark classes:
 * <ul>
 *   <li><strong>PerformanceIndicatorBenchmark</strong>: Essential validation performance metrics</li>
 *   <li><strong>ErrorLoadBenchmark</strong>: Streamlined error handling scenarios</li>
 * </ul>
 */
public class BenchmarkRunner {

    /**
     * Main method to run all benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {

        // Configure JMH options
        Options options = new OptionsBuilder()
                // Include all benchmark classes in this package
                .include("de\\.cuioss\\.jwt\\.validation\\.benchmark\\..+Benchmark")
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
                // Use benchmark mode specified in individual benchmark annotations
                // (removed .mode(Mode.AverageTime) to allow individual benchmarks to specify their own mode)
                // Configure result output - create a combined report for all benchmarks
                .resultFormat(getResultFormat())
                .result(getResultFile())
                // JVM arguments are controlled by POM configuration
                .build();

        // Run the benchmarks
        new Runner(options).run();

        // Metrics are now exported by PerformanceIndicatorBenchmark @TearDown
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
     * Gets the result file from system property or defaults to jmh-result.json.
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
        return "jmh-result.json";
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
