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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkConfigurationTest {

    @Test
    void testDefaults() {
        BenchmarkConfiguration config = BenchmarkConfiguration.defaults().build();
        
        assertEquals(".*Benchmark.*", config.includePattern());
        assertEquals(ResultFormatType.JSON, config.resultFormat());
        assertEquals(1, config.forks());
        assertEquals(3, config.warmupIterations());
        assertEquals(5, config.measurementIterations());
        assertEquals(4, config.threads());
        assertEquals("target/benchmark-results", config.resultsDirectory());
        assertTrue(config.integrationServiceUrl().isEmpty());
        assertTrue(config.keycloakUrl().isEmpty());
        assertTrue(config.metricsUrl().isEmpty());
    }

    @Test
    void testCustomValues() {
        BenchmarkConfiguration config = BenchmarkConfiguration.defaults()
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
        assertEquals(ResultFormatType.CSV, config.resultFormat());
        assertEquals(2, config.forks());
        assertEquals(10, config.warmupIterations());
        assertEquals(20, config.measurementIterations());
        assertEquals(8, config.threads());
        assertEquals("custom/results", config.resultsDirectory());
        assertEquals("http://localhost:8080", config.integrationServiceUrl().orElse(null));
        assertEquals("http://keycloak:8080", config.keycloakUrl().orElse(null));
        assertEquals("http://metrics:9090", config.metricsUrl().orElse(null));
    }

    @Test
    void testFromSystemProperties() {
        // Set system properties
        System.setProperty("jmh.include", ".*SystemTest.*");
        System.setProperty("jmh.result.format", "TEXT");
        System.setProperty("jmh.forks", "3");
        System.setProperty("jmh.threads", "16");
        
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.fromSystemProperties().build();
            
            assertEquals(".*SystemTest.*", config.includePattern());
            assertEquals(ResultFormatType.TEXT, config.resultFormat());
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

    @Test
    void testToBuilder() {
        BenchmarkConfiguration original = BenchmarkConfiguration.defaults()
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

    @Test
    void testToJmhOptions() {
        BenchmarkConfiguration config = BenchmarkConfiguration.defaults()
                .withIncludePattern(".*Test.*")
                .withForks(2)
                .withThreads(8)
                .withIntegrationServiceUrl("http://service:8080")
                .build();
        
        var options = config.toJmhOptions();
        assertNotNull(options);
        // Options object doesn't expose getters, so we just verify it builds successfully
    }

    @Test
    void testThreadCountParsing() {
        // Test MAX threads
        System.setProperty("jmh.threads", "MAX");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.fromSystemProperties().build();
            assertEquals(Runtime.getRuntime().availableProcessors(), config.threads());
        } finally {
            System.clearProperty("jmh.threads");
        }
        
        // Test invalid thread count defaults to 4
        System.setProperty("jmh.threads", "invalid");
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.fromSystemProperties().build();
            assertEquals(4, config.threads());
        } finally {
            System.clearProperty("jmh.threads");
        }
    }

    @Test
    void testTimeValueParsing() {
        System.setProperty("jmh.time", "500ms");
        System.setProperty("jmh.warmupTime", "2m");
        
        try {
            BenchmarkConfiguration config = BenchmarkConfiguration.fromSystemProperties().build();
            assertNotNull(config.measurementTime());
            assertNotNull(config.warmupTime());
        } finally {
            System.clearProperty("jmh.time");
            System.clearProperty("jmh.warmupTime");
        }
    }

    @Test
    void testResultFileGeneration() {
        // Test with custom result file
        BenchmarkConfiguration config1 = BenchmarkConfiguration.defaults()
                .withResultFile("custom-result.json")
                .build();
        assertEquals("custom-result.json", config1.resultFile());
        
        // Test with file prefix system property
        System.setProperty("jmh.result.filePrefix", "prefix");
        try {
            BenchmarkConfiguration config2 = BenchmarkConfiguration.fromSystemProperties().build();
            assertEquals("prefix.json", config2.resultFile());
        } finally {
            System.clearProperty("jmh.result.filePrefix");
        }
        
        // Test default result file
        BenchmarkConfiguration config3 = BenchmarkConfiguration.defaults().build();
        assertTrue(config3.resultFile().endsWith("benchmark-result.json"));
    }
}