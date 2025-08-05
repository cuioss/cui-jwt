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
package de.cuioss.jwt.quarkus.benchmark;

import lombok.experimental.UtilityClass;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;

/**
 * Shared utility class for building JMH benchmark options for Quarkus integration benchmarks.
 * Consolidates common option parsing logic used by both standard and JFR benchmark runners.
 *
 * @since 1.0
 */
@UtilityClass
public final class BenchmarkOptionsHelper {

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
     * Gets the measurement time from system property or uses the provided default.
     *
     * @param defaultTime the default time value (e.g., "10s", "60s")
     * @return the parsed time value
     */
    public static TimeValue getMeasurementTime(String defaultTime) {
        String time = System.getProperty("jmh.time", defaultTime);
        return parseTimeValue(time);
    }

    /**
     * Gets the warmup time from system property or uses the provided default.
     *
     * @param defaultTime the default time value (e.g., "2s", "5s")
     * @return the parsed time value
     */
    public static TimeValue getWarmupTime(String defaultTime) {
        String time = System.getProperty("jmh.warmupTime", defaultTime);
        return parseTimeValue(time);
    }

    /**
     * Gets the thread count from system property or uses the provided default.
     * Supports "MAX" to use all available processors.
     *
     * @param defaultCount the default thread count
     * @return the thread count
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
     * Gets the number of forks from system property or uses the provided default.
     *
     * @param defaultForks the default number of forks
     * @return the number of forks
     */
    public static int getForks(int defaultForks) {
        return Integer.getInteger("jmh.forks", defaultForks);
    }

    /**
     * Gets the number of warmup iterations from system property or uses the provided default.
     *
     * @param defaultIterations the default number of warmup iterations
     * @return the number of warmup iterations
     */
    public static int getWarmupIterations(int defaultIterations) {
        return Integer.getInteger("jmh.warmupIterations", defaultIterations);
    }

    /**
     * Gets the number of measurement iterations from system property or uses the provided default.
     *
     * @param defaultIterations the default number of measurement iterations
     * @return the number of measurement iterations
     */
    public static int getMeasurementIterations(int defaultIterations) {
        return Integer.getInteger("jmh.iterations", defaultIterations);
    }

    /**
     * Gets the integration service URL from system property or uses the provided default.
     *
     * @param defaultUrl the default URL
     * @return the service URL
     */
    public static String getIntegrationServiceUrl(String defaultUrl) {
        return System.getProperty("integration.service.url", defaultUrl);
    }

    /**
     * Gets the Keycloak URL from system property or uses the provided default.
     *
     * @param defaultUrl the default URL
     * @return the Keycloak URL
     */
    public static String getKeycloakUrl(String defaultUrl) {
        return System.getProperty("keycloak.url", defaultUrl);
    }

    /**
     * Gets the Quarkus metrics URL from system property or uses the provided default.
     *
     * @param defaultUrl the default URL
     * @return the Quarkus metrics URL
     */
    public static String getQuarkusMetricsUrl(String defaultUrl) {
        return System.getProperty("quarkus.metrics.url", defaultUrl);
    }

    /**
     * Parses a time value string (e.g., "2s", "1000ms") into a TimeValue object.
     *
     * @param timeStr the time string to parse
     * @return the parsed time value
     */
    public static TimeValue parseTimeValue(String timeStr) {
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