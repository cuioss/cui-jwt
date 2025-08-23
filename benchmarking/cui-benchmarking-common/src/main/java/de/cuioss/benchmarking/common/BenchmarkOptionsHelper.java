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

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;

/**
 * Centralized helper class for configuring JMH benchmark options via system properties.
 * <p>
 * This class provides configuration management for benchmark parameters across all modules,
 * allowing easy customization via Maven profiles or command line properties.
 * <p>
 * Supported system properties:
 * <ul>
 *   <li>jmh.include - Benchmark include pattern</li>
 *   <li>jmh.result.format - Result format (JSON, CSV, etc.)</li>
 *   <li>jmh.result.filePrefix - Result file prefix</li>
 *   <li>jmh.forks - Number of forks</li>
 *   <li>jmh.warmupIterations - Number of warmup iterations</li>
 *   <li>jmh.iterations - Number of measurement iterations</li>
 *   <li>jmh.time - Measurement time per iteration</li>
 *   <li>jmh.warmupTime - Warmup time per iteration</li>
 *   <li>jmh.threads - Number of threads (supports "MAX")</li>
 * </ul>
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
     * Gets the result format from system property or defaults to JSON.
     * 
     * @return the result format type
     */
    public static ResultFormatType getResultFormat() {
        String format = System.getProperty("jmh.result.format", "JSON");
        try {
            return ResultFormatType.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResultFormatType.JSON;
        }
    }

    /**
     * Gets the result file path from system property or returns the default.
     * Ensures parent directory exists.
     * 
     * @param defaultFileName the default file name to use if property not set
     * @return the result file path
     */
    public static String getResultFile(String defaultFileName) {
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
        return defaultFileName;
    }

    /**
     * Gets the number of forks from system property with default fallback.
     * 
     * @param defaultForks the default number of forks
     * @return the number of forks to use
     */
    public static int getForks(int defaultForks) {
        return Integer.getInteger("jmh.forks", defaultForks);
    }

    /**
     * Gets the number of forks from system property.
     * 
     * @return the number of forks to use (default: 1)
     */
    public static int getForks() {
        return getForks(1);
    }

    /**
     * Gets the number of warmup iterations from system property with default fallback.
     * 
     * @param defaultIterations the default number of warmup iterations
     * @return the number of warmup iterations
     */
    public static int getWarmupIterations(int defaultIterations) {
        return Integer.getInteger("jmh.warmupIterations", defaultIterations);
    }

    /**
     * Gets the number of warmup iterations from system property.
     * 
     * @return the number of warmup iterations (default: 3)
     */
    public static int getWarmupIterations() {
        return getWarmupIterations(3);
    }

    /**
     * Gets the number of measurement iterations from system property with default fallback.
     * 
     * @param defaultIterations the default number of measurement iterations
     * @return the number of measurement iterations
     */
    public static int getMeasurementIterations(int defaultIterations) {
        return Integer.getInteger("jmh.iterations", defaultIterations);
    }

    /**
     * Gets the number of measurement iterations from system property.
     * 
     * @return the number of measurement iterations (default: 5)
     */
    public static int getMeasurementIterations() {
        return getMeasurementIterations(5);
    }

    /**
     * Gets the measurement time from system property with default fallback.
     * 
     * @param defaultTime the default time value (e.g., "2s", "5s")
     * @return the measurement time
     */
    public static TimeValue getMeasurementTime(String defaultTime) {
        String time = System.getProperty("jmh.time", defaultTime);
        return parseTimeValue(time);
    }

    /**
     * Gets the measurement time from system property.
     * 
     * @return the measurement time (default: 2s)
     */
    public static TimeValue getMeasurementTime() {
        return getMeasurementTime("2s");
    }

    /**
     * Gets the warmup time from system property with default fallback.
     * 
     * @param defaultTime the default time value (e.g., "1s", "2s")
     * @return the warmup time
     */
    public static TimeValue getWarmupTime(String defaultTime) {
        String time = System.getProperty("jmh.warmupTime", defaultTime);
        return parseTimeValue(time);
    }

    /**
     * Gets the warmup time from system property.
     * 
     * @return the warmup time (default: 1s)
     */
    public static TimeValue getWarmupTime() {
        return getWarmupTime("1s");
    }

    /**
     * Gets the thread count from system property with default fallback.
     * Supports "MAX" to use all available processors.
     * 
     * @param defaultCount the default thread count
     * @return the number of threads to use
     */
    public static int getThreadCount(int defaultCount) {
        String threads = System.getProperty("jmh.threads", String.valueOf(defaultCount));
        if ("MAX".equals(threads)) {
            return Runtime.getRuntime().availableProcessors();
        }
        try {
            return Integer.parseInt(threads);
        } catch (NumberFormatException e) {
            return defaultCount;
        }
    }

    /**
     * Gets the thread count from system property.
     * 
     * @return the number of threads to use (default: 4)
     */
    public static int getThreadCount() {
        return getThreadCount(4);
    }

    /**
     * Parses a time value string into a JMH TimeValue.
     * Supports seconds (s), milliseconds (ms), and minutes (m).
     * 
     * @param timeStr the time string (e.g., "2s", "1000ms", "5m")
     * @return the parsed TimeValue
     * @throws IllegalArgumentException if the time string format is invalid
     */
    public static TimeValue parseTimeValue(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            throw new IllegalArgumentException("Time value cannot be null or empty");
        }
        
        try {
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
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid time value format: " + timeStr, e);
        }
    }

    /**
     * Gets a custom string property from system properties.
     * Useful for module-specific configuration like service URLs.
     * 
     * @param propertyName the property name
     * @param defaultValue the default value
     * @return the property value or default
     */
    public static String getProperty(String propertyName, String defaultValue) {
        return System.getProperty(propertyName, defaultValue);
    }

    /**
     * Gets the benchmark results directory from system property.
     * 
     * @param defaultDir the default directory
     * @return the benchmark results directory path
     */
    public static String getBenchmarkResultsDir(String defaultDir) {
        return System.getProperty("benchmark.results.dir", defaultDir);
    }

    /**
     * Gets the integration service URL from system property.
     * 
     * @param defaultUrl the default URL
     * @return the service URL
     */
    public static String getIntegrationServiceUrl(String defaultUrl) {
        return System.getProperty("integration.service.url", defaultUrl);
    }

    /**
     * Gets the Keycloak URL from system property.
     * 
     * @param defaultUrl the default URL
     * @return the Keycloak URL
     */
    public static String getKeycloakUrl(String defaultUrl) {
        return System.getProperty("keycloak.url", defaultUrl);
    }

    /**
     * Gets the Quarkus metrics URL from system property.
     * 
     * @param defaultUrl the default URL
     * @return the Quarkus metrics URL
     */
    public static String getQuarkusMetricsUrl(String defaultUrl) {
        return System.getProperty("quarkus.metrics.url", defaultUrl);
    }
}