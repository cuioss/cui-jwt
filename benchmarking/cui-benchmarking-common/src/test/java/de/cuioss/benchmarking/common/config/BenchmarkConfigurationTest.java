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

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.TimeValue;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkConfigurationTest {

    @Test void defaults() {
        BenchmarkConfiguration config = BenchmarkConfiguration.defaults()
                .withBenchmarkType(BenchmarkType.MICRO)
                .withThroughputBenchmarkName("testThroughput")
                .withLatencyBenchmarkName("testLatency")
                .build();

        assertEquals(".*Benchmark.*", config.includePattern());
        assertEquals(ResultFormatType.JSON, config.reportConfig().resultFormat());
        assertEquals(1, config.forks());
        assertEquals(3, config.warmupIterations());
        assertEquals(5, config.measurementIterations());
        assertEquals(4, config.threads());
        assertEquals("target/benchmark-results", config.resultsDirectory());
        assertTrue(config.integrationServiceUrl().isEmpty());
        assertTrue(config.keycloakUrl().isEmpty());
        assertTrue(config.metricsUrl().isEmpty());
    }

    @Test void customValues() {
        BenchmarkConfiguration config = BenchmarkConfiguration.defaults()
                .withBenchmarkType(BenchmarkType.INTEGRATION)
                .withThroughputBenchmarkName("customThroughput")
                .withLatencyBenchmarkName("customLatency")
                .withIncludePattern(".*MyBenchmark.*")
                .withResultFormat(ResultFormatType.CSV)
                .withForks(2)
                .withWarmupIterations(10)
                .withMeasurementIterations(20)
                .withThreads(8)
                .withResultsDirectory("custom/results")
                .withIntegrationServiceUrl("http://localhost:8080")
                .withKeycloakUrl("http://keycloak:8080")
                .withMetricsUrl("http://metrics:9090")
                .build();

        assertEquals(".*MyBenchmark.*", config.includePattern());
        assertEquals(ResultFormatType.CSV, config.reportConfig().resultFormat());
        assertEquals(2, config.forks());
        assertEquals(10, config.warmupIterations());
        assertEquals(20, config.measurementIterations());
        assertEquals(8, config.threads());
        assertEquals("custom/results", config.resultsDirectory());
        assertEquals("http://localhost:8080", config.integrationServiceUrl().orElse(null));
        assertEquals("http://keycloak:8080", config.keycloakUrl().orElse(null));
        assertEquals("http://metrics:9090", config.metricsUrl().orElse(null));
    }

    @Test void builderWithSystemProperties() {
        // Set system properties
        System.setProperty("jmh.include", ".*SystemTest.*");
        System.setProperty("jmh.result.format", "TEXT");
        System.setProperty("jmh.forks", "3");
        System.setProperty("jmh.threads", "16");

        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.MICRO)
                    .withThroughputBenchmarkName("sysThroughput")
                    .withLatencyBenchmarkName("sysLatency")
                    .build();

            assertEquals(".*SystemTest.*", config.includePattern());
            assertEquals(ResultFormatType.TEXT, config.reportConfig().resultFormat());
            assertEquals(3, config.forks());
            assertEquals(16, config.threads());
        } finally {
            // Clean up system properties
            System.clearProperty("jmh.include");
            System.clearProperty("jmh.result.format");
            System.clearProperty("jmh.forks");
            System.clearProperty("jmh.threads");
        }
    }

    @Test void toBuilder() {
        BenchmarkConfiguration original = BenchmarkConfiguration.defaults()
                .withBenchmarkType(BenchmarkType.MICRO)
                .withThroughputBenchmarkName("origThroughput")
                .withLatencyBenchmarkName("origLatency")
                .withIncludePattern(".*Original.*")
                .withForks(5)
                .build();

        BenchmarkConfiguration modified = original.toBuilder()
                .withIncludePattern(".*Modified.*")
                .withThreads(12)
                .build();

        // Original should be unchanged
        assertEquals(".*Original.*", original.includePattern());
        assertEquals(5, original.forks());
        assertEquals(4, original.threads()); // default

        // Modified should have new values
        assertEquals(".*Modified.*", modified.includePattern());
        assertEquals(5, modified.forks()); // inherited
        assertEquals(12, modified.threads()); // new
    }

    @Test void toJmhOptions() {
        BenchmarkConfiguration config = BenchmarkConfiguration.defaults()
                .withBenchmarkType(BenchmarkType.MICRO)
                .withThroughputBenchmarkName("jmhThroughput")
                .withLatencyBenchmarkName("jmhLatency")
                .withIncludePattern(".*JmhTest.*")
                .withResultFormat(ResultFormatType.JSON)
                .withForks(2)
                .build();

        var options = config.toJmhOptions();
        assertNotNull(options);
        // Options are properly built, detailed testing would require more JMH infrastructure
    }

    @Test void threadCountParsing() {
        System.setProperty("jmh.threads", "MAX");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.MICRO)
                    .withThroughputBenchmarkName("threadThroughput")
                    .withLatencyBenchmarkName("threadLatency")
                    .build();
            assertEquals(Runtime.getRuntime().availableProcessors(), config.threads());
        } finally {
            System.clearProperty("jmh.threads");
        }

        System.setProperty("jmh.threads", "HALF");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.MICRO)
                    .withThroughputBenchmarkName("threadThroughput")
                    .withLatencyBenchmarkName("threadLatency")
                    .build();
            assertEquals(Math.max(1, Runtime.getRuntime().availableProcessors() / 2), config.threads());
        } finally {
            System.clearProperty("jmh.threads");
        }
    }

    @Test void timeValueParsing() {
        System.setProperty("jmh.time", "5s");
        System.setProperty("jmh.warmupTime", "2m");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.MICRO)
                    .withThroughputBenchmarkName("timeThroughput")
                    .withLatencyBenchmarkName("timeLatency")
                    .build();
            assertEquals(TimeValue.seconds(5), config.measurementTime());
            assertEquals(TimeValue.minutes(2), config.warmupTime());
        } finally {
            System.clearProperty("jmh.time");
            System.clearProperty("jmh.warmupTime");
        }
    }

    @Test void resultFileGeneration() {
        BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                .withBenchmarkType(BenchmarkType.INTEGRATION)
                .withThroughputBenchmarkName("fileThroughput")
                .withLatencyBenchmarkName("fileLatency")
                .withResultsDirectory("test-results")
                .build();

        var options = config.toJmhOptions();
        assertNotNull(options);
    }

    @Test void invalidResultFormat() {
        System.setProperty("jmh.result.format", "INVALID");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.MICRO)
                    .withThroughputBenchmarkName("formatThroughput")
                    .withLatencyBenchmarkName("formatLatency")
                    .build();
            assertEquals(ResultFormatType.JSON, config.reportConfig().resultFormat()); // Should default to JSON
        } finally {
            System.clearProperty("jmh.result.format");
        }
    }

    @Test void timeValueParsingEdgeCases() {
        System.setProperty("jmh.time", "1h");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.MICRO)
                    .withThroughputBenchmarkName("edgeThroughput")
                    .withLatencyBenchmarkName("edgeLatency")
                    .build();
            assertEquals(TimeValue.hours(1), config.measurementTime());
        } finally {
            System.clearProperty("jmh.time");
        }

        // Test invalid time unit (should warn and use number as seconds)
        System.setProperty("jmh.warmupTime", "10x");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.MICRO)
                    .withThroughputBenchmarkName("edgeThroughput")
                    .withLatencyBenchmarkName("edgeLatency")
                    .build();
            assertEquals(TimeValue.seconds(10), config.warmupTime());
        } finally {
            System.clearProperty("jmh.warmupTime");
        }

        // Test empty time value (should default to 1 second)
        System.setProperty("jmh.time", "");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.MICRO)
                    .withThroughputBenchmarkName("edgeThroughput")
                    .withLatencyBenchmarkName("edgeLatency")
                    .build();
            assertEquals(TimeValue.seconds(1), config.measurementTime());
        } finally {
            System.clearProperty("jmh.time");
        }
    }

    @Test void allSystemProperties() {
        // Set all supported system properties
        System.setProperty("jmh.include", ".*AllTest.*");
        System.setProperty("jmh.result.format", "CSV");
        System.setProperty("jmh.forks", "4");
        System.setProperty("jmh.warmupIterations", "8");
        System.setProperty("jmh.iterations", "15");
        System.setProperty("jmh.time", "3s");
        System.setProperty("jmh.warmupTime", "1s");
        System.setProperty("jmh.threads", "24");
        System.setProperty("benchmark.results.dir", "all-results");
        System.setProperty("integration.service.url", "http://service:8080");
        System.setProperty("keycloak.url", "http://keycloak:8180");
        System.setProperty("quarkus.metrics.url", "http://metrics:9090");

        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.builder()
                    .withBenchmarkType(BenchmarkType.INTEGRATION)
                    .withThroughputBenchmarkName("allThroughput")
                    .withLatencyBenchmarkName("allLatency")
                    .build();

            assertEquals(".*AllTest.*", config.includePattern());
            assertEquals(ResultFormatType.CSV, config.reportConfig().resultFormat());
            assertEquals(4, config.forks());
            assertEquals(8, config.warmupIterations());
            assertEquals(15, config.measurementIterations());
            assertEquals(TimeValue.seconds(3), config.measurementTime());
            assertEquals(TimeValue.seconds(1), config.warmupTime());
            assertEquals(24, config.threads());
            assertEquals("all-results", config.resultsDirectory());
            assertEquals("http://service:8080", config.integrationServiceUrl().orElse(null));
            assertEquals("http://keycloak:8180", config.keycloakUrl().orElse(null));
            assertEquals("http://metrics:9090", config.metricsUrl().orElse(null));
        } finally {
            // Clean up all system properties
            System.clearProperty("jmh.include");
            System.clearProperty("jmh.result.format");
            System.clearProperty("jmh.forks");
            System.clearProperty("jmh.warmupIterations");
            System.clearProperty("jmh.iterations");
            System.clearProperty("jmh.time");
            System.clearProperty("jmh.warmupTime");
            System.clearProperty("jmh.threads");
            System.clearProperty("benchmark.results.dir");
            System.clearProperty("integration.service.url");
            System.clearProperty("keycloak.url");
            System.clearProperty("quarkus.metrics.url");
        }
    }

    @Test void recordEquality() {
        BenchmarkConfiguration config1 = BenchmarkConfiguration.defaults()
                .withBenchmarkType(BenchmarkType.MICRO)
                .withThroughputBenchmarkName("eqThroughput")
                .withLatencyBenchmarkName("eqLatency")
                .withIncludePattern(".*Test.*")
                .withForks(2)
                .build();

        BenchmarkConfiguration config2 = BenchmarkConfiguration.defaults()
                .withBenchmarkType(BenchmarkType.MICRO)
                .withThroughputBenchmarkName("eqThroughput")
                .withLatencyBenchmarkName("eqLatency")
                .withIncludePattern(".*Test.*")
                .withForks(2)
                .build();

        BenchmarkConfiguration config3 = BenchmarkConfiguration.defaults()
                .withBenchmarkType(BenchmarkType.MICRO)
                .withThroughputBenchmarkName("eqThroughput")
                .withLatencyBenchmarkName("eqLatency")
                .withIncludePattern(".*Different.*")
                .withForks(2)
                .build();

        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test void requiredFieldValidation() {
        // Missing benchmark type
        assertThrows(IllegalArgumentException.class, () ->
                BenchmarkConfiguration.defaults()
                        .withThroughputBenchmarkName("throughput")
                        .withLatencyBenchmarkName("latency")
                        .build()
        );

        // Missing throughput benchmark name
        assertThrows(IllegalArgumentException.class, () ->
                BenchmarkConfiguration.defaults()
                        .withBenchmarkType(BenchmarkType.MICRO)
                        .withLatencyBenchmarkName("latency")
                        .build()
        );

        // Missing latency benchmark name
        assertThrows(IllegalArgumentException.class, () ->
                BenchmarkConfiguration.defaults()
                        .withBenchmarkType(BenchmarkType.MICRO)
                        .withThroughputBenchmarkName("throughput")
                        .build()
        );
    }
}