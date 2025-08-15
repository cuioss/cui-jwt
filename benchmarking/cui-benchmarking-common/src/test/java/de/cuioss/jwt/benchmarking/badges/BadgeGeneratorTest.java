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
package de.cuioss.jwt.benchmarking.badges;

import com.google.gson.Gson;
import de.cuioss.jwt.benchmarking.BenchmarkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BadgeGenerator}.
 * 
 * @author CUI-OpenSource-Software
 */
class BadgeGeneratorTest {

    private BadgeGenerator badgeGenerator;
    private Gson gson;

    @BeforeEach
    void setUp() {
        badgeGenerator = new BadgeGenerator();
        gson = new Gson();
    }

    @Test
    void testPerformanceBadgeGeneration(@TempDir Path tempDir) throws IOException {
        // Create test results
        List<RunResult> results = Collections.emptyList(); // Would need proper mock in real implementation
        
        // Generate performance badge
        badgeGenerator.generatePerformanceBadge(results, BenchmarkType.MICRO, tempDir.toString());
        
        // Verify badge file was created
        Path badgeFile = tempDir.resolve("performance-badge.json");
        assertTrue(Files.exists(badgeFile));
        
        // Verify badge content structure
        String content = Files.readString(badgeFile);
        Badge badge = gson.fromJson(content, Badge.class);
        
        assertEquals(1, badge.schemaVersion());
        assertEquals("Performance Score", badge.label());
        assertNotNull(badge.message());
        assertFalse(badge.message().isEmpty());
        assertNotNull(badge.color());
        assertTrue(List.of("brightgreen", "green", "yellow", "orange", "red")
                      .contains(badge.color()));
    }

    @Test
    void testTrendBadgeGeneration(@TempDir Path tempDir) throws IOException {
        List<RunResult> results = Collections.emptyList();
        
        badgeGenerator.generateTrendBadge(results, BenchmarkType.INTEGRATION, tempDir.toString());
        
        Path badgeFile = tempDir.resolve("integration-trend-badge.json");
        assertTrue(Files.exists(badgeFile));
        
        String content = Files.readString(badgeFile);
        Badge badge = gson.fromJson(content, Badge.class);
        
        assertEquals(1, badge.schemaVersion());
        assertEquals("Performance Trend", badge.label());
        assertNotNull(badge.message());
    }

    @Test
    void testLastRunBadgeGeneration(@TempDir Path tempDir) throws IOException {
        badgeGenerator.generateLastRunBadge(tempDir.toString());
        
        Path badgeFile = tempDir.resolve("last-run-badge.json");
        assertTrue(Files.exists(badgeFile));
        
        String content = Files.readString(badgeFile);
        Badge badge = gson.fromJson(content, Badge.class);
        
        assertEquals(1, badge.schemaVersion());
        assertEquals("Last Run", badge.label());
        assertTrue(badge.message().contains("UTC"));
        assertEquals("blue", badge.color());
    }

    @Test
    void testBadgeFileCreationInNonExistentDirectory(@TempDir Path tempDir) throws IOException {
        Path nestedDir = tempDir.resolve("nested/badges");
        
        badgeGenerator.generateLastRunBadge(nestedDir.toString());
        
        assertTrue(Files.exists(nestedDir.resolve("last-run-badge.json")));
    }

    @Test
    void testBenchmarkTypeSpecificFilenames(@TempDir Path tempDir) throws IOException {
        List<RunResult> results = Collections.emptyList();
        
        // Test micro benchmark filenames
        badgeGenerator.generatePerformanceBadge(results, BenchmarkType.MICRO, tempDir.toString());
        badgeGenerator.generateTrendBadge(results, BenchmarkType.MICRO, tempDir.toString());
        
        assertTrue(Files.exists(tempDir.resolve("performance-badge.json")));
        assertTrue(Files.exists(tempDir.resolve("trend-badge.json")));
        
        // Test integration benchmark filenames  
        badgeGenerator.generatePerformanceBadge(results, BenchmarkType.INTEGRATION, tempDir.toString());
        badgeGenerator.generateTrendBadge(results, BenchmarkType.INTEGRATION, tempDir.toString());
        
        assertTrue(Files.exists(tempDir.resolve("integration-performance-badge.json")));
        assertTrue(Files.exists(tempDir.resolve("integration-trend-badge.json")));
    }
}