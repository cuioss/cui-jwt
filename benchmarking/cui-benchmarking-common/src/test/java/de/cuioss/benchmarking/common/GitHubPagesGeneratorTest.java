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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.report.GitHubPagesGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GitHubPagesGenerator}.
 */
class GitHubPagesGeneratorTest {

    private final Gson gson = new Gson();

    @Test void prepareDeploymentStructureCreatesDirectories(@TempDir Path tempDir) throws Exception {
        GitHubPagesGenerator generator = new GitHubPagesGenerator();

        Path sourceDir = tempDir.resolve("source");
        Path deployDir = tempDir.resolve("deploy");

        // Create source structure with test files
        Files.createDirectories(sourceDir.resolve("badges"));
        Files.createDirectories(sourceDir.resolve("data"));
        Files.writeString(sourceDir.resolve("index.html"), "<html>Test</html>");
        Files.writeString(sourceDir.resolve("badges/test-badge.json"), "{}");
        Files.writeString(sourceDir.resolve("data/metrics.json"), "{}");

        generator.prepareDeploymentStructure(sourceDir.toString(), deployDir.toString());

        // Verify deployment structure was created
        assertTrue(Files.exists(deployDir), "Deploy directory should be created");
        assertTrue(Files.exists(deployDir.resolve("api")), "API directory should be created");
        assertTrue(Files.exists(deployDir.resolve("badges")), "Badges directory should be created");
        assertTrue(Files.exists(deployDir.resolve("data")), "Data directory should be created");
    }

    @Test void prepareDeploymentStructureCopiesHtmlFiles(@TempDir Path tempDir) throws Exception {
        GitHubPagesGenerator generator = new GitHubPagesGenerator();

        Path sourceDir = tempDir.resolve("source");
        Path deployDir = tempDir.resolve("deploy");

        // Create test HTML files
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("index.html"), "<html>Index</html>");
        Files.writeString(sourceDir.resolve("trends.html"), "<html>Trends</html>");

        generator.prepareDeploymentStructure(sourceDir.toString(), deployDir.toString());

        // Verify HTML files were copied
        assertTrue(Files.exists(deployDir.resolve("index.html")), "index.html should be copied");
        assertTrue(Files.exists(deployDir.resolve("trends.html")), "trends.html should be copied");

        String indexContent = Files.readString(deployDir.resolve("index.html"));
        assertEquals("<html>Index</html>", indexContent, "HTML content should be preserved");
    }

    @Test void prepareDeploymentStructureCreatesApiEndpoints(@TempDir Path tempDir) throws Exception {
        GitHubPagesGenerator generator = new GitHubPagesGenerator();

        Path sourceDir = tempDir.resolve("source");
        Path deployDir = tempDir.resolve("deploy");

        // Create minimal source structure
        Files.createDirectories(sourceDir.resolve("data"));
        Files.writeString(sourceDir.resolve("data/metrics.json"), "{\"test\": \"data\"}");

        generator.prepareDeploymentStructure(sourceDir.toString(), deployDir.toString());

        // Verify API endpoints were created
        assertTrue(Files.exists(deployDir.resolve("api/latest.json")),
                "Latest API endpoint should be created");
        assertTrue(Files.exists(deployDir.resolve("api/benchmarks.json")),
                "Benchmarks API endpoint should be created");
        assertTrue(Files.exists(deployDir.resolve("api/metrics.json")),
                "Metrics API endpoint should be created");
        assertTrue(Files.exists(deployDir.resolve("api/status.json")),
                "Status API endpoint should be created");

        // Verify status endpoint content
        String statusContent = Files.readString(deployDir.resolve("api/status.json"));
        JsonObject status = gson.fromJson(statusContent, JsonObject.class);
        assertEquals("healthy", status.get("status").getAsString(), "Status should be healthy");
        assertNotNull(status.get("timestamp"), "Timestamp should be present");
    }

    @Test void prepareDeploymentStructureHandlesMissingSourceFiles(@TempDir Path tempDir) throws Exception {
        GitHubPagesGenerator generator = new GitHubPagesGenerator();

        Path sourceDir = tempDir.resolve("source");
        Path deployDir = tempDir.resolve("deploy");

        // Create empty source directory
        Files.createDirectories(sourceDir);

        // Should not throw exception with missing files
        assertDoesNotThrow(() ->
                        generator.prepareDeploymentStructure(sourceDir.toString(), deployDir.toString()),
                "Should handle missing source files gracefully");

        // Basic structure should still be created
        assertTrue(Files.exists(deployDir), "Deploy directory should be created");
        assertTrue(Files.exists(deployDir.resolve("api")), "API directory should be created");
    }

    @Test void prepareDeploymentStructureCopiesBadgeFiles(@TempDir Path tempDir) throws Exception {
        GitHubPagesGenerator generator = new GitHubPagesGenerator();

        Path sourceDir = tempDir.resolve("source");
        Path deployDir = tempDir.resolve("deploy");

        // Create badge files
        Files.createDirectories(sourceDir.resolve("badges"));
        Files.writeString(sourceDir.resolve("badges/performance-badge.json"),
                "{\"label\": \"Performance\", \"message\": \"100 ops/s\"}");
        Files.writeString(sourceDir.resolve("badges/trend-badge.json"),
                "{\"label\": \"Trend\", \"message\": \"↑ 10%\"}");

        generator.prepareDeploymentStructure(sourceDir.toString(), deployDir.toString());

        // Verify badge files were copied
        assertTrue(Files.exists(deployDir.resolve("badges/performance-badge.json")),
                "Performance badge should be copied");
        assertTrue(Files.exists(deployDir.resolve("badges/trend-badge.json")),
                "Trend badge should be copied");
    }
}