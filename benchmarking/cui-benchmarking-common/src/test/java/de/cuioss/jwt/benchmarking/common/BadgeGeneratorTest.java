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
package de.cuioss.jwt.benchmarking.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.RunResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BadgeGenerator}.
 * 
 * @since 1.0
 */
class BadgeGeneratorTest {

    private final Gson gson = new Gson();

    @Test
    void testPerformanceBadgeGeneration(@TempDir Path tempDir) throws Exception {
        // Arrange
        var generator = new BadgeGenerator();
        var outputDir = tempDir.toString();
        var results = Collections.<RunResult>emptyList();

        // Act
        generator.generatePerformanceBadge(results, BenchmarkType.MICRO, outputDir);

        // Assert
        var badgeFile = Paths.get(outputDir, BenchmarkType.MICRO.getPerformanceBadgeFilename());
        assertTrue(Files.exists(badgeFile));
        
        var badgeContent = Files.readString(badgeFile);
        var badge = gson.fromJson(badgeContent, JsonObject.class);
        
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals(BenchmarkType.MICRO.getLabelText(), badge.get("label").getAsString());
        assertNotNull(badge.get("message"));
        assertNotNull(badge.get("color"));
        
        // Verify color is valid
        var validColors = List.of("brightgreen", "green", "yellow", "orange", "red");
        assertTrue(validColors.contains(badge.get("color").getAsString()));
    }

    @Test
    void testTrendBadgeGeneration(@TempDir Path tempDir) throws Exception {
        // Arrange
        var generator = new BadgeGenerator();
        var outputDir = tempDir.toString();
        var results = Collections.<RunResult>emptyList();

        // Act
        generator.generateTrendBadge(results, BenchmarkType.INTEGRATION, outputDir);

        // Assert
        var badgeFile = Paths.get(outputDir, BenchmarkType.INTEGRATION.getTrendBadgeFilename());
        assertTrue(Files.exists(badgeFile));
        
        var badgeContent = Files.readString(badgeFile);
        var badge = gson.fromJson(badgeContent, JsonObject.class);
        
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Performance Trend", badge.get("label").getAsString());
        assertNotNull(badge.get("message"));
        assertNotNull(badge.get("color"));
    }

    @Test
    void testLastRunBadgeGeneration(@TempDir Path tempDir) throws Exception {
        // Arrange
        var generator = new BadgeGenerator();
        var outputDir = tempDir.toString();

        // Act
        generator.generateLastRunBadge(outputDir);

        // Assert
        var badgeFile = Paths.get(outputDir, "last-run-badge.json");
        assertTrue(Files.exists(badgeFile));
        
        var badgeContent = Files.readString(badgeFile);
        var badge = gson.fromJson(badgeContent, JsonObject.class);
        
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Last Run", badge.get("label").getAsString());
        assertEquals("blue", badge.get("color").getAsString());
        
        // Verify timestamp format (should be a valid date string)
        var message = badge.get("message").getAsString();
        assertNotNull(message);
        assertFalse(message.isEmpty());
        assertTrue(message.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"));
    }

    @Test
    void testBadgeDirectoryCreation(@TempDir Path tempDir) throws Exception {
        // Arrange
        var generator = new BadgeGenerator();
        var nestedOutput = tempDir.resolve("nested/badges").toString();
        var results = Collections.<RunResult>emptyList();

        // Act
        generator.generatePerformanceBadge(results, BenchmarkType.MICRO, nestedOutput);

        // Assert
        assertTrue(Files.exists(Paths.get(nestedOutput)));
        assertTrue(Files.exists(Paths.get(nestedOutput, BenchmarkType.MICRO.getPerformanceBadgeFilename())));
    }

    @Test
    void testBenchmarkTypeSpecificFilenames() throws Exception {
        // Test that different benchmark types generate different filenames
        assertEquals("performance-badge.json", BenchmarkType.MICRO.getPerformanceBadgeFilename());
        assertEquals("integration-performance-badge.json", BenchmarkType.INTEGRATION.getPerformanceBadgeFilename());
        
        assertEquals("trend-badge.json", BenchmarkType.MICRO.getTrendBadgeFilename());
        assertEquals("integration-trend-badge.json", BenchmarkType.INTEGRATION.getTrendBadgeFilename());
        
        assertEquals("Performance Score", BenchmarkType.MICRO.getLabelText());
        assertEquals("Integration Performance", BenchmarkType.INTEGRATION.getLabelText());
    }

    @Test
    void testBadgeJsonStructure(@TempDir Path tempDir) throws Exception {
        // Arrange
        var generator = new BadgeGenerator();
        var outputDir = tempDir.toString();
        var results = Collections.<RunResult>emptyList();

        // Act
        generator.generatePerformanceBadge(results, BenchmarkType.MICRO, outputDir);

        // Assert - Verify JSON structure is valid for shields.io
        var badgeFile = Paths.get(outputDir, BenchmarkType.MICRO.getPerformanceBadgeFilename());
        var badgeContent = Files.readString(badgeFile);
        
        // Should be valid JSON
        assertDoesNotThrow(() -> gson.fromJson(badgeContent, JsonObject.class));
        
        var badge = gson.fromJson(badgeContent, JsonObject.class);
        
        // Required fields for shields.io
        assertTrue(badge.has("schemaVersion"));
        assertTrue(badge.has("label"));
        assertTrue(badge.has("message"));
        assertTrue(badge.has("color"));
        
        // Verify types
        assertTrue(badge.get("schemaVersion").isJsonPrimitive());
        assertTrue(badge.get("label").isJsonPrimitive());
        assertTrue(badge.get("message").isJsonPrimitive());
        assertTrue(badge.get("color").isJsonPrimitive());
    }
}