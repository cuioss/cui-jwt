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
package de.cuioss.benchmarking.common.config;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Modern configuration API for JMH benchmarks.
 * Provides a fluent builder pattern and immutable configuration objects.
 * 
 * <p>Example usage:
 * <pre>{@code
 * var config = BenchmarkConfiguration.fromSystemProperties()
 *     .withForks(2)
 *     .withThreads(10)
 *     .build();
 * 
 * Options jmhOptions = config.toJmhOptions();
 * }</pre>
 * 
 * @since 1.0
 */
public record BenchmarkConfiguration(
String includePattern,
ResultFormatType resultFormat,
String resultFile,
int forks,
int warmupIterations,
int measurementIterations,
TimeValue measurementTime,
TimeValue warmupTime,
int threads,
String resultsDirectory,
Optional<String> integrationServiceUrl,
Optional<String> keycloakUrl,
Optional<String> metricsUrl
) {

    /**
     * System property keys for benchmark configuration.
     */
    public static final class Properties {
        public static final String INCLUDE = "jmh.include";
        public static final String RESULT_FORMAT = "jmh.result.format";
        public static final String RESULT_FILE_PREFIX = "jmh.result.filePrefix";
        public static final String FORKS = "jmh.forks";
        public static final String WARMUP_ITERATIONS = "jmh.warmupIterations";
        public static final String MEASUREMENT_ITERATIONS = "jmh.iterations";
        public static final String MEASUREMENT_TIME = "jmh.time";
        public static final String WARMUP_TIME = "jmh.warmupTime";
        public static final String THREADS = "jmh.threads";
        public static final String RESULTS_DIR = "benchmark.results.dir";
        public static final String INTEGRATION_SERVICE_URL = "integration.service.url";
        public static final String KEYCLOAK_URL = "keycloak.url";
        public static final String METRICS_URL = "quarkus.metrics.url";

        private Properties() {
        }
    }

    /**
     * Default values for benchmark configuration.
     */
    public static final class Defaults {
        public static final String INCLUDE_PATTERN = ".*Benchmark.*";
        public static final ResultFormatType RESULT_FORMAT = ResultFormatType.JSON;
        public static final int FORKS = 1;
        public static final int WARMUP_ITERATIONS = 3;
        public static final int MEASUREMENT_ITERATIONS = 5;
        public static final String MEASUREMENT_TIME = "2s";
        public static final String WARMUP_TIME = "1s";
        public static final int THREADS = 4;
        public static final String RESULTS_DIR = "target/benchmark-results";

        private Defaults() {
        }
    }

    /**
     * Creates a configuration from system properties with defaults.
     * 
     * @return a new builder initialized from system properties
     */
    public static Builder fromSystemProperties() {
        return new Builder()
                .withIncludePattern(System.getProperty(Properties.INCLUDE, Defaults.INCLUDE_PATTERN))
                .withResultFormat(parseResultFormat(System.getProperty(Properties.RESULT_FORMAT, "JSON")))
                .withForks(getIntProperty(Properties.FORKS, Defaults.FORKS))
                .withWarmupIterations(getIntProperty(Properties.WARMUP_ITERATIONS, Defaults.WARMUP_ITERATIONS))
                .withMeasurementIterations(getIntProperty(Properties.MEASUREMENT_ITERATIONS, Defaults.MEASUREMENT_ITERATIONS))
                .withMeasurementTime(parseTimeValue(System.getProperty(Properties.MEASUREMENT_TIME, Defaults.MEASUREMENT_TIME)))
                .withWarmupTime(parseTimeValue(System.getProperty(Properties.WARMUP_TIME, Defaults.WARMUP_TIME)))
                .withThreads(parseThreadCount(System.getProperty(Properties.THREADS, String.valueOf(Defaults.THREADS))))
                .withResultsDirectory(System.getProperty(Properties.RESULTS_DIR, Defaults.RESULTS_DIR))
                .withIntegrationServiceUrl(System.getProperty(Properties.INTEGRATION_SERVICE_URL))
                .withKeycloakUrl(System.getProperty(Properties.KEYCLOAK_URL))
                .withMetricsUrl(System.getProperty(Properties.METRICS_URL));
    }

    /**
     * Creates a default configuration.
     * 
     * @return a new builder with default values
     */
    public static Builder defaults() {
        return new Builder();
    }

    /**
     * Creates a builder from this configuration.
     * 
     * @return a new builder initialized with this configuration's values
     */
    public Builder toBuilder() {
        return new Builder()
                .withIncludePattern(includePattern)
                .withResultFormat(resultFormat)
                .withResultFile(resultFile)
                .withForks(forks)
                .withWarmupIterations(warmupIterations)
                .withMeasurementIterations(measurementIterations)
                .withMeasurementTime(measurementTime)
                .withWarmupTime(warmupTime)
                .withThreads(threads)
                .withResultsDirectory(resultsDirectory)
                .withIntegrationServiceUrl(integrationServiceUrl.orElse(null))
                .withKeycloakUrl(keycloakUrl.orElse(null))
                .withMetricsUrl(metricsUrl.orElse(null));
    }

    /**
     * Converts this configuration to JMH Options.
     * 
     * @return JMH Options configured from this configuration
     */
    public Options toJmhOptions() {
        var builder = new OptionsBuilder()
                .include(includePattern)
                .resultFormat(resultFormat)
                .result(getOrCreateResultFile())
                .forks(forks)
                .warmupIterations(warmupIterations)
                .measurementIterations(measurementIterations)
                .measurementTime(measurementTime)
                .warmupTime(warmupTime)
                .threads(threads);

        // Add JVM arguments
        builder.jvmArgs(
                "-Dbenchmark.results.dir=" + resultsDirectory,
                "-Djava.util.logging.config.file=src/main/resources/benchmark-logging.properties"
        );

        // Add integration-specific properties if present
        integrationServiceUrl.ifPresent(url ->
                builder.jvmArgsAppend("-Dintegration.service.url=" + url));
        keycloakUrl.ifPresent(url ->
                builder.jvmArgsAppend("-Dkeycloak.url=" + url));
        metricsUrl.ifPresent(url ->
                builder.jvmArgsAppend("-Dquarkus.metrics.url=" + url));

        return builder.build();
    }

    /**
     * Gets the result file path, creating parent directories if needed.
     */
    private String getOrCreateResultFile() {
        if (resultFile != null && !resultFile.isEmpty()) {
            File file = new File(resultFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            return resultFile;
        }
        return Path.of(resultsDirectory, "benchmark-result.json").toString();
    }

    private static ResultFormatType parseResultFormat(String format) {
        try {
            return ResultFormatType.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Defaults.RESULT_FORMAT;
        }
    }

    private static TimeValue parseTimeValue(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return TimeValue.seconds(2);
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
                return TimeValue.seconds(Long.parseLong(timeStr));
            }
        } catch (NumberFormatException e) {
            return TimeValue.seconds(2);
        }
    }

    private static int parseThreadCount(String threads) {
        if ("MAX".equalsIgnoreCase(threads)) {
            return Runtime.getRuntime().availableProcessors();
        }
        try {
            return Integer.parseInt(threads);
        } catch (NumberFormatException e) {
            return Defaults.THREADS;
        }
    }

    private static int getIntProperty(String key, int defaultValue) {
        return Integer.getInteger(key, defaultValue);
    }

    /**
     * Builder for BenchmarkConfiguration.
     */
    public static class Builder {
        private String includePattern = Defaults.INCLUDE_PATTERN;
        private ResultFormatType resultFormat = Defaults.RESULT_FORMAT;
        private String resultFile;
        private int forks = Defaults.FORKS;
        private int warmupIterations = Defaults.WARMUP_ITERATIONS;
        private int measurementIterations = Defaults.MEASUREMENT_ITERATIONS;
        private TimeValue measurementTime = parseTimeValue(Defaults.MEASUREMENT_TIME);
        private TimeValue warmupTime = parseTimeValue(Defaults.WARMUP_TIME);
        private int threads = Defaults.THREADS;
        private String resultsDirectory = Defaults.RESULTS_DIR;
        private String integrationServiceUrl;
        private String keycloakUrl;
        private String metricsUrl;

        public Builder withIncludePattern(String pattern) {
            this.includePattern = pattern;
            return this;
        }

        public Builder withResultFormat(ResultFormatType format) {
            this.resultFormat = format;
            return this;
        }

        public Builder withResultFile(String file) {
            this.resultFile = file;
            return this;
        }

        public Builder withForks(int forks) {
            this.forks = forks;
            return this;
        }

        public Builder withWarmupIterations(int iterations) {
            this.warmupIterations = iterations;
            return this;
        }

        public Builder withMeasurementIterations(int iterations) {
            this.measurementIterations = iterations;
            return this;
        }

        public Builder withMeasurementTime(TimeValue time) {
            this.measurementTime = time;
            return this;
        }

        public Builder withWarmupTime(TimeValue time) {
            this.warmupTime = time;
            return this;
        }

        public Builder withThreads(int threads) {
            this.threads = threads;
            return this;
        }

        public Builder withResultsDirectory(String directory) {
            this.resultsDirectory = directory;
            return this;
        }

        public Builder withIntegrationServiceUrl(String url) {
            this.integrationServiceUrl = url;
            return this;
        }

        public Builder withKeycloakUrl(String url) {
            this.keycloakUrl = url;
            return this;
        }

        public Builder withMetricsUrl(String url) {
            this.metricsUrl = url;
            return this;
        }

        public BenchmarkConfiguration build() {
            String finalResultFile = resultFile;
            if (finalResultFile == null || finalResultFile.isEmpty()) {
                String filePrefix = System.getProperty(Properties.RESULT_FILE_PREFIX);
                if (filePrefix != null && !filePrefix.isEmpty()) {
                    finalResultFile = filePrefix + ".json";
                } else {
                    finalResultFile = Path.of(resultsDirectory, "benchmark-result.json").toString();
                }
            }

            return new BenchmarkConfiguration(
                    includePattern,
                    resultFormat,
                    finalResultFile,
                    forks,
                    warmupIterations,
                    measurementIterations,
                    measurementTime,
                    warmupTime,
                    threads,
                    resultsDirectory,
                    Optional.ofNullable(integrationServiceUrl),
                    Optional.ofNullable(keycloakUrl),
                    Optional.ofNullable(metricsUrl)
            );
        }
    }
}