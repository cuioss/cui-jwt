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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BenchmarkOptionsHelper Tests")
class BenchmarkOptionsHelperTest {

    private String originalOutputDir;
    private String originalIncludePattern;

    @BeforeEach
    void setUp() {
        // Store original system properties
        originalOutputDir = System.getProperty("benchmark.output.dir");
        originalIncludePattern = System.getProperty("jmh.include");
    }

    @AfterEach
    void tearDown() {
        // Restore original system properties
        if (originalOutputDir != null) {
            System.setProperty("benchmark.output.dir", originalOutputDir);
        } else {
            System.clearProperty("benchmark.output.dir");
        }
        
        if (originalIncludePattern != null) {
            System.setProperty("jmh.include", originalIncludePattern);
        } else {
            System.clearProperty("jmh.include");
        }
        
        // Clear test properties
        System.clearProperty("jmh.forks");
        System.clearProperty("jmh.warmup.iterations");
        System.clearProperty("jmh.measurement.iterations");
        System.clearProperty("jmh.warmup.time");
        System.clearProperty("jmh.measurement.time");
        System.clearProperty("jmh.threads");
        System.clearProperty("benchmark.generate.badges");
        System.clearProperty("benchmark.generate.reports");
        System.clearProperty("benchmark.generate.github.pages");
    }

    @Test
    @DisplayName("Should return default output directory when system property not set")
    void shouldReturnDefaultOutputDirectory() {
        System.clearProperty("benchmark.output.dir");
        
        String result = BenchmarkOptionsHelper.getOutputDirectory();
        
        assertEquals("target/benchmark-results", result);
    }

    @Test
    @DisplayName("Should return custom output directory when system property is set")
    void shouldReturnCustomOutputDirectory() {
        System.setProperty("benchmark.output.dir", "/custom/path");
        
        String result = BenchmarkOptionsHelper.getOutputDirectory();
        
        assertEquals("/custom/path", result);
    }

    @Test
    @DisplayName("Should return default include pattern when system property not set")
    void shouldReturnDefaultIncludePattern() {
        System.clearProperty("jmh.include");
        
        String result = BenchmarkOptionsHelper.getIncludePattern();
        
        assertEquals(".*", result);
    }

    @Test
    @DisplayName("Should return custom include pattern when system property is set")
    void shouldReturnCustomIncludePattern() {
        System.setProperty("jmh.include", "com.example.*");
        
        String result = BenchmarkOptionsHelper.getIncludePattern();
        
        assertEquals("com.example.*", result);
    }

    @Test
    @DisplayName("Should return default forks count")
    void shouldReturnDefaultForks() {
        System.clearProperty("jmh.forks");
        
        int result = BenchmarkOptionsHelper.getForks();
        
        assertEquals(1, result);
    }

    @Test
    @DisplayName("Should return custom forks count")
    void shouldReturnCustomForks() {
        System.setProperty("jmh.forks", "3");
        
        int result = BenchmarkOptionsHelper.getForks();
        
        assertEquals(3, result);
    }

    @Test
    @DisplayName("Should return default warmup iterations")
    void shouldReturnDefaultWarmupIterations() {
        System.clearProperty("jmh.warmup.iterations");
        
        int result = BenchmarkOptionsHelper.getWarmupIterations();
        
        assertEquals(5, result);
    }

    @Test
    @DisplayName("Should return custom warmup iterations")
    void shouldReturnCustomWarmupIterations() {
        System.setProperty("jmh.warmup.iterations", "10");
        
        int result = BenchmarkOptionsHelper.getWarmupIterations();
        
        assertEquals(10, result);
    }

    @Test
    @DisplayName("Should return default measurement iterations")
    void shouldReturnDefaultMeasurementIterations() {
        System.clearProperty("jmh.measurement.iterations");
        
        int result = BenchmarkOptionsHelper.getMeasurementIterations();
        
        assertEquals(5, result);
    }

    @Test
    @DisplayName("Should return custom measurement iterations")
    void shouldReturnCustomMeasurementIterations() {
        System.setProperty("jmh.measurement.iterations", "15");
        
        int result = BenchmarkOptionsHelper.getMeasurementIterations();
        
        assertEquals(15, result);
    }

    @Test
    @DisplayName("Should parse seconds time specification")
    void shouldParseSecondsTimeSpec() {
        System.setProperty("jmh.warmup.time", "5s");
        
        TimeValue result = BenchmarkOptionsHelper.getWarmupTime();
        
        assertEquals(5, result.getTime());
        assertEquals(TimeUnit.SECONDS, result.getTimeUnit());
    }

    @Test
    @DisplayName("Should parse milliseconds time specification")
    void shouldParseMillisecondsTimeSpec() {
        System.setProperty("jmh.measurement.time", "500ms");
        
        TimeValue result = BenchmarkOptionsHelper.getMeasurementTime();
        
        assertEquals(500, result.getTime());
        assertEquals(TimeUnit.MILLISECONDS, result.getTimeUnit());
    }

    @Test
    @DisplayName("Should parse microseconds time specification")
    void shouldParseMicrosecondsTimeSpec() {
        System.setProperty("jmh.warmup.time", "1000us");
        
        TimeValue result = BenchmarkOptionsHelper.getWarmupTime();
        
        assertEquals(1000, result.getTime());
        assertEquals(TimeUnit.MICROSECONDS, result.getTimeUnit());
    }

    @Test
    @DisplayName("Should parse nanoseconds time specification")
    void shouldParseNanosecondsTimeSpec() {
        System.setProperty("jmh.measurement.time", "1000000ns");
        
        TimeValue result = BenchmarkOptionsHelper.getMeasurementTime();
        
        assertEquals(1000000, result.getTime());
        assertEquals(TimeUnit.NANOSECONDS, result.getTimeUnit());
    }

    @Test
    @DisplayName("Should parse time specification without suffix as seconds")
    void shouldParseTimeSpecWithoutSuffixAsSeconds() {
        System.setProperty("jmh.warmup.time", "3");
        
        TimeValue result = BenchmarkOptionsHelper.getWarmupTime();
        
        assertEquals(3, result.getTime());
        assertEquals(TimeUnit.SECONDS, result.getTimeUnit());
    }

    @Test
    @DisplayName("Should return default thread count")
    void shouldReturnDefaultThreadCount() {
        System.clearProperty("jmh.threads");
        
        int result = BenchmarkOptionsHelper.getThreadCount();
        
        assertEquals(8, result);
    }

    @Test
    @DisplayName("Should return custom thread count")
    void shouldReturnCustomThreadCount() {
        System.setProperty("jmh.threads", "16");
        
        int result = BenchmarkOptionsHelper.getThreadCount();
        
        assertEquals(16, result);
    }

    @Test
    @DisplayName("Should default to generate badges enabled")
    void shouldDefaultToGenerateBadgesEnabled() {
        System.clearProperty("benchmark.generate.badges");
        
        boolean result = BenchmarkOptionsHelper.shouldGenerateBadges();
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Should respect custom badge generation setting")
    void shouldRespectCustomBadgeGenerationSetting() {
        System.setProperty("benchmark.generate.badges", "false");
        
        boolean result = BenchmarkOptionsHelper.shouldGenerateBadges();
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should default to generate reports enabled")
    void shouldDefaultToGenerateReportsEnabled() {
        System.clearProperty("benchmark.generate.reports");
        
        boolean result = BenchmarkOptionsHelper.shouldGenerateReports();
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Should respect custom report generation setting")
    void shouldRespectCustomReportGenerationSetting() {
        System.setProperty("benchmark.generate.reports", "false");
        
        boolean result = BenchmarkOptionsHelper.shouldGenerateReports();
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should default to generate GitHub Pages enabled")
    void shouldDefaultToGenerateGitHubPagesEnabled() {
        System.clearProperty("benchmark.generate.github.pages");
        
        boolean result = BenchmarkOptionsHelper.shouldGenerateGitHubPages();
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Should respect custom GitHub Pages generation setting")
    void shouldRespectCustomGitHubPagesGenerationSetting() {
        System.setProperty("benchmark.generate.github.pages", "false");
        
        boolean result = BenchmarkOptionsHelper.shouldGenerateGitHubPages();
        
        assertFalse(result);
    }
}