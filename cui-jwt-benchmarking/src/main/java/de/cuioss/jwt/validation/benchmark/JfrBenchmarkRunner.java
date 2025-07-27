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
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;

/**
 * JFR-enabled benchmark runner for JWT validation performance analysis with variance metrics.
 * <p>
 * This runner specifically executes the JFR-instrumented benchmarks that capture:
 * <ul>
 *   <li>Individual operation timing with metadata</li>
 *   <li>Periodic statistics including variance</li>
 *   <li>Concurrent operation tracking</li>
 * </ul>
 * <p>
 * To run: {@code mvn verify -Pbenchmark-jfr}
 * <p>
 * JFR data is automatically saved to: {@code target/benchmark-results/}
 *
 * @see JfrInstrumentedBenchmark
 * @see de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer
 */
public class JfrBenchmarkRunner {

    /**
     * Main method to run JFR-instrumented benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {

        // Configure JMH options
        ChainedOptionsBuilder builder = new OptionsBuilder()
                // Include only JFR instrumented benchmarks
                .include("de\\.cuioss\\.jwt\\.validation\\.benchmark\\.JfrInstrumentedBenchmark")
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
                // Add JVM args for better JFR profiling
                .jvmArgs("-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+DebugNonSafepoints",
                        "-XX:StartFlightRecording=filename=target/benchmark-results/jfr-benchmark.jfr,settings=profile");

        Options options = builder.build();

        // Run the benchmarks
        System.out.println("Running JFR-instrumented benchmarks...");
        System.out.println("JFR recording will be saved to: target/benchmark-results/jfr-benchmark.jfr");

        new Runner(options).run();

        System.out.println("\nBenchmark completed. To analyze variance:");
        System.out.println("java -cp \"target/classes:target/dependency/*\" \\");
        System.out.println("  de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer \\");
        System.out.println("  target/benchmark-results/jfr-benchmark.jfr");
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
     * Gets the result file from system property or defaults to jfr-benchmark-result.json.
     */
    private static String getResultFile() {
        String filePrefix = System.getProperty("jmh.result.filePrefix");
        if (filePrefix != null && !filePrefix.isEmpty()) {
            String resultFile = filePrefix + "-jfr.json";
            // Ensure parent directory exists
            File file = new File(resultFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            return resultFile;
        }
        return "target/benchmark-results/jfr-benchmark-result.json";
    }

    /**
     * Gets the measurement time from system property or defaults to 5 seconds for better variance data.
     */
    private static TimeValue getMeasurementTime() {
        String time = System.getProperty("jmh.time", "5s");
        return parseTimeValue(time);
    }

    /**
     * Gets the warmup time from system property or defaults to 3 seconds.
     */
    private static TimeValue getWarmupTime() {
        String time = System.getProperty("jmh.warmupTime", "3s");
        return parseTimeValue(time);
    }

    /**
     * Gets the thread count from system property. Defaults to 8 for variance analysis.
     */
    private static int getThreadCount() {
        String threads = System.getProperty("jmh.threads", "8");
        if ("MAX".equals(threads)) {
            return Runtime.getRuntime().availableProcessors();
        }
        try {
            return Integer.parseInt(threads);
        } catch (NumberFormatException e) {
            return 8; // Default for better concurrency variance analysis
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