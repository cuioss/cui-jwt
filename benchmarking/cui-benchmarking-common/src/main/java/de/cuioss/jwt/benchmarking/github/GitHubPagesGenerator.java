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
package de.cuioss.jwt.benchmarking.github;

import de.cuioss.tools.logging.CuiLogger;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Generates deployment-ready directory structure for GitHub Pages.
 * <p>
 * This generator creates a complete directory structure that can be directly
 * deployed to GitHub Pages, including:
 * <ul>
 *   <li>Static HTML files and assets</li>
 *   <li>Badge JSON files for dynamic badges</li>
 *   <li>API endpoints for metrics consumption</li>
 *   <li>Proper directory structure for GitHub Pages</li>
 * </ul>
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 */
public class GitHubPagesGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(GitHubPagesGenerator.class);

    /**
     * Prepares a deployment-ready directory structure for GitHub Pages.
     * 
     * @param sourceDir source directory containing generated artifacts
     * @param targetDir target directory for GitHub Pages deployment
     * @throws IOException if deployment structure creation fails
     */
    public void prepareDeploymentStructure(String sourceDir, String targetDir) throws IOException {
        LOGGER.info("Preparing GitHub Pages deployment structure");
        LOGGER.info("Source: %s", sourceDir);
        LOGGER.info("Target: %s", targetDir);

        // Create target directory
        Path targetPath = Paths.get(targetDir);
        if (Files.exists(targetPath)) {
            FileUtils.deleteDirectory(targetPath.toFile());
        }
        Files.createDirectories(targetPath);

        // Copy HTML reports to root
        copyHtmlReports(sourceDir, targetDir);

        // Copy badges to api/badges/
        copyBadges(sourceDir, targetDir);

        // Copy metrics to api/data/
        copyMetrics(sourceDir, targetDir);

        // Create API index
        createApiIndex(targetDir);

        // Create GitHub Pages configuration
        createGitHubPagesConfig(targetDir);

        LOGGER.info("GitHub Pages deployment structure created successfully");
    }

    private void copyHtmlReports(String sourceDir, String targetDir) throws IOException {
        LOGGER.debug("Copying HTML reports");

        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);

        // Copy index.html if it exists
        Path indexSource = sourcePath.resolve("index.html");
        if (Files.exists(indexSource)) {
            Files.copy(indexSource, targetPath.resolve("index.html"), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("Copied index.html");
        }

        // Copy trends.html if it exists
        Path trendsSource = sourcePath.resolve("trends.html");
        if (Files.exists(trendsSource)) {
            Files.copy(trendsSource, targetPath.resolve("trends.html"), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("Copied trends.html");
        }
    }

    private void copyBadges(String sourceDir, String targetDir) throws IOException {
        LOGGER.debug("Copying badges to API structure");

        Path badgesSource = Paths.get(sourceDir, "badges");
        Path badgesTarget = Paths.get(targetDir, "api", "badges");

        if (Files.exists(badgesSource)) {
            Files.createDirectories(badgesTarget);
            FileUtils.copyDirectory(badgesSource.toFile(), badgesTarget.toFile());
            LOGGER.debug("Copied badges directory");
        }
    }

    private void copyMetrics(String sourceDir, String targetDir) throws IOException {
        LOGGER.debug("Copying metrics to API structure");

        Path dataSource = Paths.get(sourceDir, "data");
        Path dataTarget = Paths.get(targetDir, "api", "data");

        if (Files.exists(dataSource)) {
            Files.createDirectories(dataTarget);
            FileUtils.copyDirectory(dataSource.toFile(), dataTarget.toFile());
            LOGGER.debug("Copied data directory");
        }
    }

    private void createApiIndex(String targetDir) throws IOException {
        LOGGER.debug("Creating API index");

        String apiIndexContent = """
            {
              "name": "JWT Validation Benchmark API",
              "version": "1.0.0",
              "description": "API endpoints for JWT validation benchmark data",
              "endpoints": {
                "badges": {
                  "performance": "/api/badges/performance-badge.json",
                  "integration_performance": "/api/badges/integration-performance-badge.json",
                  "trend": "/api/badges/trend-badge.json",
                  "integration_trend": "/api/badges/integration-trend-badge.json",
                  "last_run": "/api/badges/last-run-badge.json"
                },
                "data": {
                  "metrics": "/api/data/metrics.json",
                  "summary": "/api/data/benchmark-summary.json"
                }
              },
              "generated": "%s"
            }
            """.formatted(java.time.Instant.now().toString());

        Path apiIndexPath = Paths.get(targetDir, "api", "index.json");
        Files.createDirectories(apiIndexPath.getParent());
        Files.writeString(apiIndexPath, apiIndexContent);

        LOGGER.debug("Created API index");
    }

    private void createGitHubPagesConfig(String targetDir) throws IOException {
        LOGGER.debug("Creating GitHub Pages configuration");

        // Create _config.yml for Jekyll (GitHub Pages)
        String configContent = """
            title: "JWT Validation Benchmarks"
            description: "Performance benchmarks for JWT validation library"
            theme: minima
            plugins:
              - jekyll-optional-front-matter
              - jekyll-readme-index
            """;

        Path configPath = Paths.get(targetDir, "_config.yml");
        Files.writeString(configPath, configContent);

        // Create .nojekyll to bypass Jekyll processing for certain files
        Path noJekyllPath = Paths.get(targetDir, ".nojekyll");
        Files.createFile(noJekyllPath);

        // Create README.md
        String readmeContent = """
            # JWT Validation Benchmarks
            
            This site contains performance benchmark results for the CUI JWT validation library.
            
            ## Available Pages
            
            - [Current Results](index.html) - Latest benchmark results
            - [Performance Trends](trends.html) - Historical performance data
            
            ## API Endpoints
            
            - [API Index](api/index.json) - Available API endpoints
            - [Performance Badge](api/badges/performance-badge.json) - Shields.io performance badge
            - [Trend Badge](api/badges/trend-badge.json) - Shields.io trend badge
            - [Metrics Data](api/data/metrics.json) - Structured performance metrics
            
            Generated automatically by the CUI JWT benchmarking infrastructure.
            """;

        Path readmePath = Paths.get(targetDir, "README.md");
        Files.writeString(readmePath, readmeContent);

        LOGGER.debug("Created GitHub Pages configuration files");
    }
}