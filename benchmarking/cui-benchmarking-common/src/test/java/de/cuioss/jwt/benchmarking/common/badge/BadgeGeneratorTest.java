/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.benchmarking.common.badge;

import de.cuioss.jwt.benchmarking.common.model.BenchmarkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BadgeGenerator using JUnit Jupiter API.
 * Verifies badge generation functionality and output format compliance.
 * 
 * @author CUI Benchmarking Infrastructure
 */
class BadgeGeneratorTest {
    
    @Test
    void testPerformanceBadgeGeneration(@TempDir Path tempDir) throws Exception {
        Collection<RunResult> results = createMockResults();
        BadgeGenerator generator = new BadgeGenerator();
        
        String outputDir = tempDir.toString();
        generator.generatePerformanceBadge(results, BenchmarkType.MICRO, outputDir);
        
        // Verify badge file was created
        Path badgeFile = Paths.get(outputDir, BenchmarkType.MICRO.getBadgeFilename());
        assertTrue(Files.exists(badgeFile));
        assertTrue(Files.size(badgeFile) > 0);
        
        // Verify file contains JSON content
        String content = Files.readString(badgeFile);
        assertTrue(content.contains("schemaVersion"));
        assertTrue(content.contains("label"));
        assertTrue(content.contains("message"));
        assertTrue(content.contains("color"));
    }
    
    @Test
    void testTrendBadgeGeneration(@TempDir Path tempDir) throws Exception {
        Collection<RunResult> results = createMockResults();
        BadgeGenerator generator = new BadgeGenerator();
        
        String outputDir = tempDir.toString();
        generator.generateTrendBadge(results, BenchmarkType.INTEGRATION, outputDir);
        
        // Verify trend badge file was created
        Path trendFile = Paths.get(outputDir, BenchmarkType.INTEGRATION.getTrendBadgeFilename());
        assertTrue(Files.exists(trendFile));
        
        // Verify JSON structure
        String content = Files.readString(trendFile);
        assertTrue(content.contains("Performance Trend"));
    }
    
    @Test
    void testLastRunBadgeGeneration(@TempDir Path tempDir) throws Exception {
        BadgeGenerator generator = new BadgeGenerator();
        
        String outputDir = tempDir.toString();
        generator.generateLastRunBadge(outputDir);
        
        // Verify last run badge was created
        Path lastRunFile = Paths.get(outputDir, "last-run-badge.json");
        assertTrue(Files.exists(lastRunFile));
        
        // Verify contains timestamp
        String content = Files.readString(lastRunFile);
        assertTrue(content.contains("Last Benchmark"));
        assertTrue(content.contains("blue"));
    }
    
    @Test
    void testAllBadgeGeneration(@TempDir Path tempDir) throws Exception {
        Collection<RunResult> results = createMockResults();
        BadgeGenerator generator = new BadgeGenerator();
        
        String outputDir = tempDir.toString();
        generator.generateAllBadges(results, BenchmarkType.MICRO, outputDir);
        
        // Verify all badge files were created
        assertTrue(Files.exists(Paths.get(outputDir, BenchmarkType.MICRO.getBadgeFilename())));
        assertTrue(Files.exists(Paths.get(outputDir, BenchmarkType.MICRO.getTrendBadgeFilename())));
        assertTrue(Files.exists(Paths.get(outputDir, "last-run-badge.json")));
    }
    
    @Test
    void testBadgeBuilder() {
        // Test Badge builder functionality
        Badge badge = Badge.builder()
            .schemaVersion(1)
            .label("Test Label")
            .message("Test Message")
            .color("green")
            .build();
        
        assertEquals(1, badge.getSchemaVersion());
        assertEquals("Test Label", badge.getLabel());
        assertEquals("Test Message", badge.getMessage());
        assertEquals("green", badge.getColor());
    }
    
    @Test
    void testBadgeBuilderValidation() {
        // Test validation of required fields
        assertThrows(IllegalArgumentException.class, () -> 
            Badge.builder().schemaVersion(1).build());
        
        assertThrows(IllegalArgumentException.class, () -> 
            Badge.builder().schemaVersion(1).label("").message("test").color("green").build());
        
        assertThrows(IllegalArgumentException.class, () -> 
            Badge.builder().schemaVersion(1).label("test").message("").color("green").build());
        
        assertThrows(IllegalArgumentException.class, () -> 
            Badge.builder().schemaVersion(1).label("test").message("test").color("").build());
    }
    
    @Test
    void testBenchmarkTypeProperties() {
        // Verify benchmark type configuration
        assertEquals("Micro", BenchmarkType.MICRO.getDisplayName());
        assertEquals("Integration", BenchmarkType.INTEGRATION.getDisplayName());
        
        assertEquals("performance-badge.json", BenchmarkType.MICRO.getBadgeFilename());
        assertEquals("integration-performance-badge.json", BenchmarkType.INTEGRATION.getBadgeFilename());
        
        assertEquals("Performance Score", BenchmarkType.MICRO.getBadgeLabel());
        assertEquals("Integration Performance", BenchmarkType.INTEGRATION.getBadgeLabel());
        
        assertEquals("jwt-validation", BenchmarkType.MICRO.getMetricsPrefix());
        assertEquals("integration", BenchmarkType.INTEGRATION.getMetricsPrefix());
    }
    
    /**
     * Create mock benchmark results for testing.
     */
    private Collection<RunResult> createMockResults() {
        // For testing structure creation, empty list is sufficient
        return new ArrayList<>();
    }
}