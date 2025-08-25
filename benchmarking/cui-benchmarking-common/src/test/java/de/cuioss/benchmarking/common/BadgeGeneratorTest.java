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
package de.cuioss.benchmarking.common;

import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.report.BadgeGenerator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BadgeGenerator} using JUnit Jupiter API.
 * <p>
 * Tests badge generation with proper shields.io JSON format compliance
 * and performance scoring logic.
 */
class BadgeGeneratorTest {

    private final Gson gson = new Gson();

    @Test void lastRunBadgeGeneration(@TempDir Path tempDir) throws Exception {
        BadgeGenerator generator = new BadgeGenerator();

        String outputDir = tempDir.toString();
        generator.generateLastRunBadge(outputDir);

        // Verify last run badge file was created
        Path badgeFile = Path.of(outputDir, "last-run-badge.json");
        assertTrue(Files.exists(badgeFile), "Last run badge file should be created");

        // Verify badge content
        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);

        assertEquals(1, badge.get("schemaVersion").getAsInt(), "Schema version should be 1");
        assertEquals("Last Run", badge.get("label").getAsString(), "Last run badge should have correct label");
        assertEquals("blue", badge.get("color").getAsString(), "Last run badge should be blue");

        // Verify timestamp format (should be date only)
        String message = badge.get("message").getAsString();
        assertNotNull(message, "Timestamp message should not be null");
        assertTrue(message.matches("\\d{4}-\\d{2}-\\d{2}"), "Message should be in YYYY-MM-DD format");
    }

    @Test void benchmarkTypeFileNames() {
        // Test that different benchmark types generate correct file names
        assertEquals("performance-badge.json", BenchmarkType.MICRO.getPerformanceBadgeFileName(),
                "Micro benchmark should use standard performance badge filename");
        assertEquals("integration-performance-badge.json", BenchmarkType.INTEGRATION.getPerformanceBadgeFileName(),
                "Integration benchmark should use integration-specific filename");

        assertEquals("trend-badge.json", BenchmarkType.MICRO.getTrendBadgeFileName(),
                "Micro benchmark should use standard trend badge filename");
        assertEquals("integration-trend-badge.json", BenchmarkType.INTEGRATION.getTrendBadgeFileName(),
                "Integration benchmark should use integration-specific trend filename");
    }
}