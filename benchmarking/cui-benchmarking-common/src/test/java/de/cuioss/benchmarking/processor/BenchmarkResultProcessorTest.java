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
package de.cuioss.benchmarking.processor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BenchmarkResultProcessor Tests")
class BenchmarkResultProcessorTest {

    private BenchmarkResultProcessor processor;
    private String originalBadgesSetting;
    private String originalReportsSetting;
    private String originalGitHubPagesSetting;

    @BeforeEach
    void setUp() {
        processor = new BenchmarkResultProcessor();
        
        // Store original settings
        originalBadgesSetting = System.getProperty("benchmark.generate.badges");
        originalReportsSetting = System.getProperty("benchmark.generate.reports");
        originalGitHubPagesSetting = System.getProperty("benchmark.generate.github.pages");
        
        // Enable all generation for tests
        System.setProperty("benchmark.generate.badges", "true");
        System.setProperty("benchmark.generate.reports", "true");
        System.setProperty("benchmark.generate.github.pages", "true");
    }

    @AfterEach
    void tearDown() {
        // Restore original settings
        if (originalBadgesSetting != null) {
            System.setProperty("benchmark.generate.badges", originalBadgesSetting);
        } else {
            System.clearProperty("benchmark.generate.badges");
        }
        
        if (originalReportsSetting != null) {
            System.setProperty("benchmark.generate.reports", originalReportsSetting);
        } else {
            System.clearProperty("benchmark.generate.reports");
        }
        
        if (originalGitHubPagesSetting != null) {
            System.setProperty("benchmark.generate.github.pages", originalGitHubPagesSetting);
        } else {
            System.clearProperty("benchmark.generate.github.pages");
        }
    }

    @Test
    @DisplayName("Should create required output directories")
    void shouldCreateRequiredOutputDirectories(@TempDir Path tempDir) throws IOException {
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        assertTrue(Files.exists(tempDir.resolve("badges")));
        assertTrue(Files.exists(tempDir.resolve("data")));
        assertTrue(Files.exists(tempDir.resolve("reports")));
        assertTrue(Files.exists(tempDir.resolve("gh-pages-ready")));
    }

    @Test
    @DisplayName("Should generate benchmark summary file")
    void shouldGenerateBenchmarkSummaryFile(@TempDir Path tempDir) throws IOException {
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        Path summaryFile = tempDir.resolve("benchmark-summary.json");
        assertTrue(Files.exists(summaryFile));
        
        String content = Files.readString(summaryFile);
        assertTrue(content.contains("timestamp"));
        assertTrue(content.contains("benchmarkType"));
        assertTrue(content.contains("benchmarkCount"));
        assertTrue(content.contains("artifactsGenerated"));
    }

    @Test
    @DisplayName("Should detect micro benchmark type from package name")
    void shouldDetectMicroBenchmarkTypeFromPackageName(@TempDir Path tempDir) throws IOException {
        // We can test this indirectly by checking the summary file content
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        Path summaryFile = tempDir.resolve("benchmark-summary.json");
        String content = Files.readString(summaryFile);
        
        // Default should be micro when no results provided
        assertTrue(content.contains("\"benchmarkType\": \"micro\""));
    }

    @Test
    @DisplayName("Should handle empty results gracefully")
    void shouldHandleEmptyResultsGracefully(@TempDir Path tempDir) {
        assertDoesNotThrow(() -> {
            processor.processResults(Collections.emptyList(), tempDir.toString());
        });
        
        // Verify basic structure was created
        assertTrue(Files.exists(tempDir.resolve("badges")));
        assertTrue(Files.exists(tempDir.resolve("data")));
    }

    @Test
    @DisplayName("Should respect badge generation setting when disabled")
    void shouldRespectBadgeGenerationSettingWhenDisabled(@TempDir Path tempDir) throws IOException {
        System.setProperty("benchmark.generate.badges", "false");
        
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        Path summaryFile = tempDir.resolve("benchmark-summary.json");
        String content = Files.readString(summaryFile);
        assertTrue(content.contains("\"badges\": false"));
    }

    @Test
    @DisplayName("Should respect report generation setting when disabled")
    void shouldRespectReportGenerationSettingWhenDisabled(@TempDir Path tempDir) throws IOException {
        System.setProperty("benchmark.generate.reports", "false");
        
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        Path summaryFile = tempDir.resolve("benchmark-summary.json");
        String content = Files.readString(summaryFile);
        assertTrue(content.contains("\"reports\": false"));
    }

    @Test
    @DisplayName("Should respect GitHub Pages generation setting when disabled")
    void shouldRespectGitHubPagesGenerationSettingWhenDisabled(@TempDir Path tempDir) throws IOException {
        System.setProperty("benchmark.generate.github.pages", "false");
        
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        Path summaryFile = tempDir.resolve("benchmark-summary.json");
        String content = Files.readString(summaryFile);
        assertTrue(content.contains("\"githubPages\": false"));
    }

    @Test
    @DisplayName("Should create output directory if it does not exist")
    void shouldCreateOutputDirectoryIfItDoesNotExist(@TempDir Path tempDir) throws IOException {
        Path nonExistentDir = tempDir.resolve("new-dir");
        assertFalse(Files.exists(nonExistentDir));
        
        processor.processResults(Collections.emptyList(), nonExistentDir.toString());
        
        assertTrue(Files.exists(nonExistentDir));
        assertTrue(Files.exists(nonExistentDir.resolve("badges")));
    }

    @Test
    @DisplayName("Should generate all artifacts when all options enabled")
    void shouldGenerateAllArtifactsWhenAllOptionsEnabled(@TempDir Path tempDir) throws IOException {
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        // Verify all directories exist
        assertTrue(Files.exists(tempDir.resolve("badges")));
        assertTrue(Files.exists(tempDir.resolve("data")));
        assertTrue(Files.exists(tempDir.resolve("reports")));
        assertTrue(Files.exists(tempDir.resolve("gh-pages-ready")));
        
        // Verify summary shows all enabled
        Path summaryFile = tempDir.resolve("benchmark-summary.json");
        String content = Files.readString(summaryFile);
        assertTrue(content.contains("\"badges\": true"));
        assertTrue(content.contains("\"reports\": true"));
        assertTrue(content.contains("\"githubPages\": true"));
    }

    @Test
    @DisplayName("Should record correct benchmark count in summary")
    void shouldRecordCorrectBenchmarkCountInSummary(@TempDir Path tempDir) throws IOException {
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        Path summaryFile = tempDir.resolve("benchmark-summary.json");
        String content = Files.readString(summaryFile);
        assertTrue(content.contains("\"benchmarkCount\": 0"));
    }

    @Test
    @DisplayName("Should include timestamp in summary file")
    void shouldIncludeTimestampInSummaryFile(@TempDir Path tempDir) throws IOException {
        processor.processResults(Collections.emptyList(), tempDir.toString());
        
        Path summaryFile = tempDir.resolve("benchmark-summary.json");
        String content = Files.readString(summaryFile);
        
        // Should contain ISO timestamp format
        assertTrue(content.matches(".*\"timestamp\":\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*\".*"));
    }
}