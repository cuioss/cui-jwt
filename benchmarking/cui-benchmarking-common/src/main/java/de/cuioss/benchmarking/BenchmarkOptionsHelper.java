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
package de.cuioss.benchmarking;

import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

/**
 * Helper class for configuring JMH benchmark options from system properties.
 * <p>
 * This class provides a centralized way to configure benchmark execution parameters
 * through system properties, allowing for flexible configuration without code changes.
 *
 * @since 1.0.0
 */
public final class BenchmarkOptionsHelper {

    private BenchmarkOptionsHelper() {
        // Utility class
    }

    /**
     * Gets the output directory for benchmark results.
     *
     * @return the output directory path
     */
    public static String getOutputDirectory() {
        return System.getProperty("benchmark.output.dir", "target/benchmark-results");
    }

    /**
     * Gets the include pattern for selecting benchmarks.
     *
     * @return the include pattern for JMH
     */
    public static String getIncludePattern() {
        return System.getProperty("jmh.include", ".*");
    }

    /**
     * Gets the number of forks for benchmark execution.
     *
     * @return number of forks, defaults to 1
     */
    public static int getForks() {
        return Integer.parseInt(System.getProperty("jmh.forks", "1"));
    }

    /**
     * Gets the number of warmup iterations.
     *
     * @return number of warmup iterations, defaults to 5
     */
    public static int getWarmupIterations() {
        return Integer.parseInt(System.getProperty("jmh.warmup.iterations", "5"));
    }

    /**
     * Gets the number of measurement iterations.
     *
     * @return number of measurement iterations, defaults to 5
     */
    public static int getMeasurementIterations() {
        return Integer.parseInt(System.getProperty("jmh.measurement.iterations", "5"));
    }

    /**
     * Gets the warmup time per iteration.
     *
     * @return warmup time value, defaults to 2 seconds
     */
    public static TimeValue getWarmupTime() {
        String timeSpec = System.getProperty("jmh.warmup.time", "2s");
        return parseTimeValue(timeSpec);
    }

    /**
     * Gets the measurement time per iteration.
     *
     * @return measurement time value, defaults to 2 seconds
     */
    public static TimeValue getMeasurementTime() {
        String timeSpec = System.getProperty("jmh.measurement.time", "2s");
        return parseTimeValue(timeSpec);
    }

    /**
     * Gets the number of threads for benchmark execution.
     *
     * @return number of threads, defaults to 8
     */
    public static int getThreadCount() {
        return Integer.parseInt(System.getProperty("jmh.threads", "8"));
    }

    /**
     * Checks if badge generation is enabled.
     *
     * @return true if badges should be generated
     */
    public static boolean shouldGenerateBadges() {
        return Boolean.parseBoolean(System.getProperty("benchmark.generate.badges", "true"));
    }

    /**
     * Checks if report generation is enabled.
     *
     * @return true if reports should be generated
     */
    public static boolean shouldGenerateReports() {
        return Boolean.parseBoolean(System.getProperty("benchmark.generate.reports", "true"));
    }

    /**
     * Checks if GitHub Pages structure generation is enabled.
     *
     * @return true if GitHub Pages structure should be generated
     */
    public static boolean shouldGenerateGitHubPages() {
        return Boolean.parseBoolean(System.getProperty("benchmark.generate.github.pages", "true"));
    }

    /**
     * Parses a time specification string into a JMH TimeValue.
     * Supports formats like "2s", "500ms", "1000us", etc.
     *
     * @param timeSpec the time specification string
     * @return the parsed TimeValue
     * @throws IllegalArgumentException if the time specification is invalid
     */
    private static TimeValue parseTimeValue(String timeSpec) {
        if (timeSpec.endsWith("s")) {
            long seconds = Long.parseLong(timeSpec.substring(0, timeSpec.length() - 1));
            return TimeValue.seconds(seconds);
        } else if (timeSpec.endsWith("ms")) {
            long milliseconds = Long.parseLong(timeSpec.substring(0, timeSpec.length() - 2));
            return TimeValue.milliseconds(milliseconds);
        } else if (timeSpec.endsWith("us")) {
            long microseconds = Long.parseLong(timeSpec.substring(0, timeSpec.length() - 2));
            return TimeValue.microseconds(microseconds);
        } else if (timeSpec.endsWith("ns")) {
            long nanoseconds = Long.parseLong(timeSpec.substring(0, timeSpec.length() - 2));
            return TimeValue.nanoseconds(nanoseconds);
        } else {
            // Try to parse as seconds without suffix
            try {
                long seconds = Long.parseLong(timeSpec);
                return TimeValue.seconds(seconds);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid time specification: " + timeSpec +
                        ". Use format like '2s', '500ms', '1000us', or '1000000ns'", e);
            }
        }
    }
}