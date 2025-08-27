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
package de.cuioss.benchmarking.common.report;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BadgeGenerator using real JSON test data.
 */
class BadgeGeneratorTest {

    private final Gson gson = new Gson();

    @Test void integrationBadgeGeneration(@TempDir Path tempDir) throws Exception {
        // Use real integration benchmark JSON
        Path jsonFile = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        assertTrue(Files.exists(jsonFile));

        // Generate badge
        BadgeGenerator generator = new BadgeGenerator();
        generator.generatePerformanceBadge(jsonFile, BenchmarkType.INTEGRATION, tempDir.toString());

        // Verify badge file was created
        Path badgeFile = tempDir.resolve("integration-performance-badge.json");
        assertTrue(Files.exists(badgeFile));

        // Read and verify badge content
        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);

        // Verify structure
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Integration Performance", badge.get("label").getAsString());
        assertNotNull(badge.get("message").getAsString());
        assertNotNull(badge.get("color").getAsString());

        // Verify message format: "Score (throughput, latency)"
        String message = badge.get("message").getAsString();
        assertNotNull(message, "Badge message must exist");
        assertFalse(message.isEmpty(), "Badge message must not be empty");

        // Parse the message to verify it contains valid performance metrics
        // Expected format: "Grade (XXK ops/s, Y.YY ms)"
        // Verify it matches expected pattern for performance data
        assertTrue(message.matches(".*\\d+.*ops/s.*\\d+.*ms.*"),
                "Message should contain performance metrics in format: throughput ops/s, latency ms");
    }

    @Test void microBadgeGeneration(@TempDir Path tempDir) throws Exception {
        // Use real micro benchmark JSON
        Path jsonFile = Path.of("src/test/resources/library-benchmark-results/micro-benchmark-result.json");
        assertTrue(Files.exists(jsonFile));

        // Generate badge
        BadgeGenerator generator = new BadgeGenerator();
        generator.generatePerformanceBadge(jsonFile, BenchmarkType.MICRO, tempDir.toString());

        // Verify badge file was created
        Path badgeFile = tempDir.resolve("performance-badge.json");
        assertTrue(Files.exists(badgeFile));

        // Read and verify badge content
        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);

        // Verify structure
        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Performance Score", badge.get("label").getAsString());
        assertNotNull(badge.get("message").getAsString());
        assertNotNull(badge.get("color").getAsString());

        // Verify message contains expected format
        String message = badge.get("message").getAsString();
        assertNotNull(message, "Badge message must exist");
        assertFalse(message.isEmpty(), "Badge message must not be empty");

        // Verify it matches expected pattern for performance data
        assertTrue(message.matches(".*\\d+.*ops/s.*"),
                "Message should contain performance metrics with throughput in ops/s");
    }

    @Test void trendBadgeGeneration(@TempDir Path tempDir) throws Exception {
        Path jsonFile = Path.of("src/test/resources/integration-benchmark-results/integration-benchmark-result.json");
        assertTrue(Files.exists(jsonFile));

        BadgeGenerator generator = new BadgeGenerator();
        generator.generateTrendBadge(jsonFile, BenchmarkType.INTEGRATION, tempDir.toString());

        Path badgeFile = tempDir.resolve("integration-trend-badge.json");
        assertTrue(Files.exists(badgeFile));

        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);

        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Performance Trend", badge.get("label").getAsString());
        assertEquals("→ stable", badge.get("message").getAsString());
        assertEquals("blue", badge.get("color").getAsString());
    }

    @Test void lastRunBadgeGeneration(@TempDir Path tempDir) throws Exception {
        BadgeGenerator generator = new BadgeGenerator();
        generator.generateLastRunBadge(tempDir.toString());

        Path badgeFile = tempDir.resolve("last-run-badge.json");
        assertTrue(Files.exists(badgeFile));

        String badgeContent = Files.readString(badgeFile);
        JsonObject badge = gson.fromJson(badgeContent, JsonObject.class);

        assertEquals(1, badge.get("schemaVersion").getAsInt());
        assertEquals("Last Run", badge.get("label").getAsString());
        assertNotNull(badge.get("message").getAsString());
        assertEquals("blue", badge.get("color").getAsString());
    }
}