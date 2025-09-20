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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enhanced unit tests for BadgeGenerator covering new functionality.
 */
class BadgeGeneratorEnhancedTest {

    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    @Test void performanceBadgeGradeColors() {
        BadgeGenerator generator = new BadgeGenerator();

        // Test A+ grade (brightgreen)
        BenchmarkMetrics metricsAPlus = new BenchmarkMetrics(
                "test", "test", 1000.0, 10.0, 96.0, "A+"
        );
        String badgeAPlus = generator.generatePerformanceBadge(metricsAPlus);
        JsonObject jsonAPlus = GSON.fromJson(badgeAPlus, JsonObject.class);
        assertEquals("brightgreen", jsonAPlus.get("color").getAsString());
        assertEquals("Grade A+", jsonAPlus.get("message").getAsString());

        // Test A grade (green)
        BenchmarkMetrics metricsA = new BenchmarkMetrics(
                "test", "test", 1000.0, 10.0, 86.0, "A"
        );
        String badgeA = generator.generatePerformanceBadge(metricsA);
        JsonObject jsonA = GSON.fromJson(badgeA, JsonObject.class);
        assertEquals("green", jsonA.get("color").getAsString());
        assertEquals("Grade A", jsonA.get("message").getAsString());

        // Test B grade (yellowgreen)
        BenchmarkMetrics metricsB = new BenchmarkMetrics(
                "test", "test", 800.0, 15.0, 76.0, "B"
        );
        String badgeB = generator.generatePerformanceBadge(metricsB);
        JsonObject jsonB = GSON.fromJson(badgeB, JsonObject.class);
        assertEquals("yellowgreen", jsonB.get("color").getAsString());
        assertEquals("Grade B", jsonB.get("message").getAsString());

        // Test C grade (yellow)
        BenchmarkMetrics metricsC = new BenchmarkMetrics(
                "test", "test", 600.0, 20.0, 66.0, "C"
        );
        String badgeC = generator.generatePerformanceBadge(metricsC);
        JsonObject jsonC = GSON.fromJson(badgeC, JsonObject.class);
        assertEquals("yellow", jsonC.get("color").getAsString());

        // Test D grade (orange)
        BenchmarkMetrics metricsD = new BenchmarkMetrics(
                "test", "test", 400.0, 30.0, 56.0, "D"
        );
        String badgeD = generator.generatePerformanceBadge(metricsD);
        JsonObject jsonD = GSON.fromJson(badgeD, JsonObject.class);
        assertEquals("orange", jsonD.get("color").getAsString());

        // Test F grade (red)
        BenchmarkMetrics metricsF = new BenchmarkMetrics(
                "test", "test", 200.0, 50.0, 45.0, "F"
        );
        String badgeF = generator.generatePerformanceBadge(metricsF);
        JsonObject jsonF = GSON.fromJson(badgeF, JsonObject.class);
        assertEquals("red", jsonF.get("color").getAsString());
    }

    @Test void trendBadgeWithMetrics() {
        BadgeGenerator generator = new BadgeGenerator();

        // Test upward trend
        TrendDataProcessor.TrendMetrics upTrend = new TrendDataProcessor.TrendMetrics(
                "up", 15.3, 85.0, 20.0, -10.0
        );
        String badgeUp = generator.generateTrendBadge(upTrend);
        JsonObject jsonUp = GSON.fromJson(badgeUp, JsonObject.class);
        assertEquals("green", jsonUp.get("color").getAsString());
        assertEquals("↑ 15.3%", jsonUp.get("message").getAsString());

        // Test downward trend
        TrendDataProcessor.TrendMetrics downTrend = new TrendDataProcessor.TrendMetrics(
                "down", -8.7, 75.0, -10.0, 15.0
        );
        String badgeDown = generator.generateTrendBadge(downTrend);
        JsonObject jsonDown = GSON.fromJson(badgeDown, JsonObject.class);
        assertEquals("red", jsonDown.get("color").getAsString());
        assertEquals("↓ 8.7%", jsonDown.get("message").getAsString());

        // Test stable trend
        TrendDataProcessor.TrendMetrics stableTrend = new TrendDataProcessor.TrendMetrics(
                "stable", 0.5, 80.0, 1.0, -0.5
        );
        String badgeStable = generator.generateTrendBadge(stableTrend);
        JsonObject jsonStable = GSON.fromJson(badgeStable, JsonObject.class);
        assertEquals("blue", jsonStable.get("color").getAsString());
        assertEquals("→ 0.5%", jsonStable.get("message").getAsString());
    }

    @Test void defaultTrendBadge() {
        BadgeGenerator generator = new BadgeGenerator();

        String badge = generator.generateDefaultTrendBadge();
        JsonObject json = GSON.fromJson(badge, JsonObject.class);

        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals("Trend", json.get("label").getAsString());
        assertEquals("No history", json.get("message").getAsString());
        assertEquals("lightgray", json.get("color").getAsString());
    }

    @Test void lastRunBadgeWithSpecificTime() {
        BadgeGenerator generator = new BadgeGenerator();

        // Create specific instant for testing
        Instant testTime = Instant.parse("2025-01-29T14:30:00Z");

        String badge = generator.generateLastRunBadge(testTime);
        JsonObject json = GSON.fromJson(badge, JsonObject.class);

        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals("Last Run", json.get("label").getAsString());
        assertEquals("2025-01-29", json.get("message").getAsString());
        assertEquals("blue", json.get("color").getAsString());
    }

    @Test void writeBadgeFiles(@TempDir Path tempDir) throws IOException {
        BadgeGenerator generator = new BadgeGenerator();

        BenchmarkMetrics metrics = new BenchmarkMetrics(
                "test", "test", 1000.0, 10.0, 85.0, "A"
        );

        TrendDataProcessor.TrendMetrics trendMetrics = new TrendDataProcessor.TrendMetrics(
                "up", 5.0, 82.0, 10.0, -5.0
        );

        generator.writeBadgeFiles(metrics, trendMetrics, tempDir.toString());

        // Verify all badge files were created
        Path badgesDir = tempDir.resolve("badges");
        assertTrue(Files.exists(badgesDir));
        assertTrue(Files.isDirectory(badgesDir));

        Path perfBadge = badgesDir.resolve("performance-badge.json");
        Path trendBadge = badgesDir.resolve("trend-badge.json");
        Path lastRunBadge = badgesDir.resolve("last-run-badge.json");

        assertTrue(Files.exists(perfBadge));
        assertTrue(Files.exists(trendBadge));
        assertTrue(Files.exists(lastRunBadge));

        // Verify performance badge content
        String perfContent = Files.readString(perfBadge);
        JsonObject perfJson = GSON.fromJson(perfContent, JsonObject.class);
        assertEquals("Grade A", perfJson.get("message").getAsString());

        // Verify trend badge content
        String trendContent = Files.readString(trendBadge);
        JsonObject trendJson = GSON.fromJson(trendContent, JsonObject.class);
        assertEquals("↑ 5.0%", trendJson.get("message").getAsString());

        // Verify last run badge content
        String lastRunContent = Files.readString(lastRunBadge);
        JsonObject lastRunJson = GSON.fromJson(lastRunContent, JsonObject.class);
        assertEquals(DATE_FORMAT.format(Instant.now()), lastRunJson.get("message").getAsString());
    }

    @Test void writeBadgeFilesWithoutTrendMetrics(@TempDir Path tempDir) throws IOException {
        BadgeGenerator generator = new BadgeGenerator();

        BenchmarkMetrics metrics = new BenchmarkMetrics(
                "test", "test", 1000.0, 10.0, 85.0, "A"
        );

        // Call with null trend metrics
        generator.writeBadgeFiles(metrics, null, tempDir.toString());

        Path badgesDir = tempDir.resolve("badges");
        Path trendBadge = badgesDir.resolve("trend-badge.json");

        assertTrue(Files.exists(trendBadge));

        // Should have default trend badge
        String trendContent = Files.readString(trendBadge);
        JsonObject trendJson = GSON.fromJson(trendContent, JsonObject.class);
        assertEquals("No history", trendJson.get("message").getAsString());
        assertEquals("lightgray", trendJson.get("color").getAsString());
    }

    @Test void badgeJsonStructure() {
        BadgeGenerator generator = new BadgeGenerator();

        BenchmarkMetrics metrics = new BenchmarkMetrics(
                "test", "test", 1000.0, 10.0, 85.0, "A"
        );

        String badge = generator.generatePerformanceBadge(metrics);
        JsonObject json = GSON.fromJson(badge, JsonObject.class);

        // Verify shields.io schema compliance
        assertTrue(json.has("schemaVersion"));
        assertEquals(1, json.get("schemaVersion").getAsInt());

        assertTrue(json.has("label"));
        assertFalse(json.get("label").getAsString().isEmpty());

        assertTrue(json.has("message"));
        assertFalse(json.get("message").getAsString().isEmpty());

        assertTrue(json.has("color"));
        assertFalse(json.get("color").getAsString().isEmpty());

        // Should not have any extra fields
        assertEquals(4, json.size());
    }

    @Test void percentageFormatting() {
        BadgeGenerator generator = new BadgeGenerator();

        // Test various percentage values for proper formatting
        TrendDataProcessor.TrendMetrics trend1 = new TrendDataProcessor.TrendMetrics(
                "up", 5.678, 80.0, 10.0, -5.0
        );
        String badge1 = generator.generateTrendBadge(trend1);
        JsonObject json1 = GSON.fromJson(badge1, JsonObject.class);
        assertEquals("↑ 5.7%", json1.get("message").getAsString());

        TrendDataProcessor.TrendMetrics trend2 = new TrendDataProcessor.TrendMetrics(
                "down", -0.123, 80.0, -1.0, 0.5
        );
        String badge2 = generator.generateTrendBadge(trend2);
        JsonObject json2 = GSON.fromJson(badge2, JsonObject.class);
        assertEquals("↓ 0.1%", json2.get("message").getAsString());

        TrendDataProcessor.TrendMetrics trend3 = new TrendDataProcessor.TrendMetrics(
                "up", 100.999, 80.0, 100.0, -50.0
        );
        String badge3 = generator.generateTrendBadge(trend3);
        JsonObject json3 = GSON.fromJson(badge3, JsonObject.class);
        assertEquals("↑ 101.0%", json3.get("message").getAsString());
    }
}