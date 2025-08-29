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

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Optional;

/**
 * Modern configuration API for JMH benchmarks.
 * Provides a fluent builder pattern and immutable configuration objects.
 * Combines JMH runtime configuration with report generation configuration.
 * 
 * <p>Example usage:
 * <pre>{@code
 * var config = BenchmarkConfiguration.builder()
 *     .withReportConfig(ReportConfiguration.builder()
 *         .withBenchmarkType(BenchmarkType.MICRO)
 *         .withThroughputBenchmarkName("myThroughputTest")
 *         .withLatencyBenchmarkName("myLatencyTest")
 *         .build())
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
ReportConfiguration reportConfig,
String includePattern,
int forks,
int warmupIterations,
int measurementIterations,
TimeValue measurementTime,
TimeValue warmupTime,
int threads,
Optional<String> integrationServiceUrl,
Optional<String> keycloakUrl,
Optional<String> metricsUrl
) {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkConfiguration.class);

    /**
     * System property keys for benchmark configuration.
     */
    public static final class Properties {
        public static final String INCLUDE = "jmh.include";
        public static final String FORKS = "jmh.forks";
        public static final String WARMUP_ITERATIONS = "jmh.warmupIterations";
        public static final String MEASUREMENT_ITERATIONS = "jmh.iterations";
        public static final String MEASUREMENT_TIME = "jmh.time";
        public static final String WARMUP_TIME = "jmh.warmupTime";
        public static final String THREADS = "jmh.threads";
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
        public static final int FORKS = 1;
        public static final int WARMUP_ITERATIONS = 3;
        public static final int MEASUREMENT_ITERATIONS = 5;
        public static final String MEASUREMENT_TIME = "2s";
        public static final String WARMUP_TIME = "1s";
        public static final int THREADS = 4;

        private Defaults() {
        }
    }

    /**
     * Creates a configuration builder with system properties for JMH settings.
     * Note: Report configuration must be set explicitly via the builder.
     * 
     * @return a new builder initialized from system properties
     */
    public static Builder builder() {
        return new Builder()
                .withIncludePattern(System.getProperty(Properties.INCLUDE, Defaults.INCLUDE_PATTERN))
                .withForks(getIntProperty(Properties.FORKS, Defaults.FORKS))
                .withWarmupIterations(getIntProperty(Properties.WARMUP_ITERATIONS, Defaults.WARMUP_ITERATIONS))
                .withMeasurementIterations(getIntProperty(Properties.MEASUREMENT_ITERATIONS, Defaults.MEASUREMENT_ITERATIONS))
                .withMeasurementTime(parseTimeValue(System.getProperty(Properties.MEASUREMENT_TIME, Defaults.MEASUREMENT_TIME)))
                .withWarmupTime(parseTimeValue(System.getProperty(Properties.WARMUP_TIME, Defaults.WARMUP_TIME)))
                .withThreads(parseThreadCount(System.getProperty(Properties.THREADS, String.valueOf(Defaults.THREADS))))
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
                .withReportConfig(reportConfig)
                .withIncludePattern(includePattern)
                .withForks(forks)
                .withWarmupIterations(warmupIterations)
                .withMeasurementIterations(measurementIterations)
                .withMeasurementTime(measurementTime)
                .withWarmupTime(warmupTime)
                .withThreads(threads)
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
                .resultFormat(reportConfig.resultFormat())
                .result(reportConfig.getOrCreateResultFile())
                .forks(forks)
                .warmupIterations(warmupIterations)
                .measurementIterations(measurementIterations)
                .measurementTime(measurementTime)
                .warmupTime(warmupTime)
                .threads(threads);

        // Add JVM arguments
        builder.jvmArgs(
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
     * Convenience methods for accessing nested report configuration.
     */
    public BenchmarkType benchmarkType() {
        return reportConfig.benchmarkType();
    }

    public String throughputBenchmarkName() {
        return reportConfig.throughputBenchmarkName();
    }

    public String latencyBenchmarkName() {
        return reportConfig.latencyBenchmarkName();
    }

    public String resultsDirectory() {
        return reportConfig.resultsDirectory();
    }

    public String resultFile() {
        return reportConfig.resultFile();
    }


    private static TimeValue parseTimeValue(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return TimeValue.seconds(1);
        }

        // Check if the last character is a digit (no unit specified)
        char lastChar = timeStr.charAt(timeStr.length() - 1);
        if (Character.isDigit(lastChar)) {
            // No unit specified, assume seconds
            return TimeValue.seconds(Long.parseLong(timeStr));
        }

        // Parse value and unit
        long value = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
        
        return switch (lastChar) {
            case 's' -> TimeValue.seconds(value);
            case 'm' -> TimeValue.minutes(value);
            case 'h' -> TimeValue.hours(value);
            default -> {
                LOGGER.warn("Unknown time unit '{}' in '{}', defaulting to seconds", lastChar, timeStr);
                yield TimeValue.seconds(value);
            }
        };
    }

    private static int parseThreadCount(String threads) {
        if ("MAX".equalsIgnoreCase(threads)) {
            return Runtime.getRuntime().availableProcessors();
        }
        if ("HALF".equalsIgnoreCase(threads)) {
            return Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
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
        private ReportConfiguration reportConfig;
        private ReportConfiguration.Builder reportConfigBuilder;
        private String includePattern = Defaults.INCLUDE_PATTERN;
        private int forks = Defaults.FORKS;
        private int warmupIterations = Defaults.WARMUP_ITERATIONS;
        private int measurementIterations = Defaults.MEASUREMENT_ITERATIONS;
        private TimeValue measurementTime = parseTimeValue(Defaults.MEASUREMENT_TIME);
        private TimeValue warmupTime = parseTimeValue(Defaults.WARMUP_TIME);
        private int threads = Defaults.THREADS;
        private String integrationServiceUrl;
        private String keycloakUrl;
        private String metricsUrl;

        /**
         * Sets the complete report configuration.
         */
        public Builder withReportConfig(ReportConfiguration config) {
            this.reportConfig = config;
            this.reportConfigBuilder = null; // Clear builder if direct config is set
            return this;
        }

        /**
         * Convenience method to set benchmark type for report config.
         * Creates a report config builder if needed.
         */
        public Builder withBenchmarkType(BenchmarkType type) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withBenchmarkType(type);
            return this;
        }

        /**
         * Convenience method to set throughput benchmark name.
         * Creates a report config builder if needed.
         */
        public Builder withThroughputBenchmarkName(String name) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withThroughputBenchmarkName(name);
            return this;
        }

        /**
         * Convenience method to set latency benchmark name.
         * Creates a report config builder if needed.
         */
        public Builder withLatencyBenchmarkName(String name) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withLatencyBenchmarkName(name);
            return this;
        }

        /**
         * Convenience method to set results directory.
         * Creates a report config builder if needed.
         */
        public Builder withResultsDirectory(String dir) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withResultsDirectory(dir);
            return this;
        }

        /**
         * Convenience method to set result file.
         * Creates a report config builder if needed.
         */
        public Builder withResultFile(String file) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withResultFile(file);
            return this;
        }

        /**
         * Convenience method to set result format.
         * Creates a report config builder if needed.
         */
        public Builder withResultFormat(ResultFormatType format) {
            ensureReportConfigBuilder();
            this.reportConfigBuilder.withResultFormat(format);
            return this;
        }

        private void ensureReportConfigBuilder() {
            if (reportConfigBuilder == null && reportConfig == null) {
                reportConfigBuilder = ReportConfiguration.builder();
            } else if (reportConfigBuilder == null && reportConfig != null) {
                reportConfigBuilder = reportConfig.toBuilder();
                reportConfig = null; // Will be rebuilt from builder
            }
        }

        public Builder withIncludePattern(String pattern) {
            this.includePattern = pattern;
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

        /**
         * Builds the configuration.
         * 
         * @return the built configuration
         * @throws IllegalArgumentException if required fields are not set
         */
        public BenchmarkConfiguration build() {
            // Build report config if using builder
            ReportConfiguration finalReportConfig = reportConfig;
            if (finalReportConfig == null) {
                if (reportConfigBuilder == null) {
                    throw new IllegalArgumentException("Report configuration must be set");
                }
                finalReportConfig = reportConfigBuilder.build();
            }

            return new BenchmarkConfiguration(
                    finalReportConfig,
                    includePattern,
                    forks,
                    warmupIterations,
                    measurementIterations,
                    measurementTime,
                    warmupTime,
                    threads,
                    Optional.ofNullable(integrationServiceUrl),
                    Optional.ofNullable(keycloakUrl),
                    Optional.ofNullable(metricsUrl)
            );
        }
    }
}