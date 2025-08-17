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
package de.cuioss.benchmarking.common;

import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

/**
 * Helper class for configuring JMH benchmark options via system properties.
 * <p>
 * This class provides centralized configuration management for benchmark parameters,
 * allowing easy customization via Maven profiles or command line properties.
 */
public final class BenchmarkOptionsHelper {

    private BenchmarkOptionsHelper() {
        // Utility class
    }

    /**
     * Gets the benchmark include pattern from system property.
     * 
     * @return the include pattern for benchmark classes
     */
    public static String getInclude() {
        return System.getProperty("jmh.include", ".*Benchmark.*");
    }

    /**
     * Gets the number of forks from system property.
     * 
     * @return the number of forks to use
     */
    public static int getForks() {
        return Integer.parseInt(System.getProperty("jmh.forks", "1"));
    }

    /**
     * Gets the number of warmup iterations from system property.
     * 
     * @return the number of warmup iterations
     */
    public static int getWarmupIterations() {
        return Integer.parseInt(System.getProperty("jmh.warmupIterations", "3"));
    }

    /**
     * Gets the number of measurement iterations from system property.
     * 
     * @return the number of measurement iterations
     */
    public static int getMeasurementIterations() {
        return Integer.parseInt(System.getProperty("jmh.measurementIterations", "5"));
    }

    /**
     * Gets the measurement time from system property.
     * 
     * @return the measurement time
     */
    public static TimeValue getMeasurementTime() {
        String timeStr = System.getProperty("jmh.measurementTime", "2s");
        return parseTimeValue(timeStr);
    }

    /**
     * Gets the warmup time from system property.
     * 
     * @return the warmup time
     */
    public static TimeValue getWarmupTime() {
        String timeStr = System.getProperty("jmh.warmupTime", "1s");
        return parseTimeValue(timeStr);
    }

    /**
     * Gets the thread count from system property.
     * 
     * @return the number of threads to use
     */
    public static int getThreadCount() {
        return Integer.parseInt(System.getProperty("jmh.threads", "4"));
    }

    /**
     * Parses a time value string into a JMH TimeValue.
     * 
     * @param timeStr the time string (e.g., "2s", "1000ms")
     * @return the parsed TimeValue
     */
    private static TimeValue parseTimeValue(String timeStr) {
        if (timeStr.endsWith("ms")) {
            long value = Long.parseLong(timeStr.substring(0, timeStr.length() - 2));
            return TimeValue.milliseconds(value);
        } else if (timeStr.endsWith("s")) {
            long value = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
            return TimeValue.seconds(value);
        } else if (timeStr.endsWith("m")) {
            long value = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
            return TimeValue.minutes(value);
        } else {
            // Default to seconds
            long value = Long.parseLong(timeStr);
            return TimeValue.seconds(value);
        }
    }
}