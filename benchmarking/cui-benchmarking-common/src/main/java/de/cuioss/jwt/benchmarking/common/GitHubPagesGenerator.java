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

import de.cuioss.tools.logging.CuiLogger;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Prepares deployment-ready GitHub Pages structure.
 * <p>
 * Creates a complete directory structure optimized for GitHub Pages
 * deployment with proper organization and navigation.
 * </p>
 * 
 * @since 1.0
 */
public final class GitHubPagesGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(GitHubPagesGenerator.class);

    /**
     * Prepares a deployment-ready directory structure for GitHub Pages.
     *
     * @param sourceDir  the source directory containing generated artifacts
     * @param deployDir  the target directory for GitHub Pages deployment
     * @throws IOException if file operations fail
     */
    public void prepareDeploymentStructure(String sourceDir, String deployDir) throws IOException {
        LOGGER.info("Preparing GitHub Pages deployment structure...");
        
        var deployPath = Paths.get(deployDir);
        
        // Clean and create deployment directory
        if (Files.exists(deployPath)) {
            FileUtils.deleteDirectory(deployPath.toFile());
        }
        Files.createDirectories(deployPath);

        // Copy all reports and static files
        copyHtmlReports(sourceDir, deployDir);
        copyBadges(sourceDir, deployDir);
        copyMetrics(sourceDir, deployDir);
        
        // Create API endpoints structure
        createApiStructure(sourceDir, deployDir);
        
        // Generate navigation and index structure
        generateNavigationStructure(deployDir);
        
        LOGGER.info("GitHub Pages structure ready in: %s", deployDir);
    }

    private void copyHtmlReports(String sourceDir, String deployDir) throws IOException {
        var sourceReports = Paths.get(sourceDir);
        var targetReports = Paths.get(deployDir);
        
        // Copy HTML files
        if (Files.exists(sourceReports.resolve("index.html"))) {
            Files.copy(sourceReports.resolve("index.html"), 
                      targetReports.resolve("index.html"), 
                      StandardCopyOption.REPLACE_EXISTING);
        }
        
        if (Files.exists(sourceReports.resolve("trends.html"))) {
            Files.copy(sourceReports.resolve("trends.html"), 
                      targetReports.resolve("trends.html"), 
                      StandardCopyOption.REPLACE_EXISTING);
        }
        
        LOGGER.debug("Copied HTML reports to deployment structure");
    }

    private void copyBadges(String sourceDir, String deployDir) throws IOException {
        var sourceBadges = Paths.get(sourceDir, "badges");
        var targetBadges = Paths.get(deployDir, "badges");
        
        if (Files.exists(sourceBadges)) {
            FileUtils.copyDirectory(sourceBadges.toFile(), targetBadges.toFile());
            LOGGER.debug("Copied badges to deployment structure");
        }
    }

    private void copyMetrics(String sourceDir, String deployDir) throws IOException {
        var sourceData = Paths.get(sourceDir, "data");
        var targetData = Paths.get(deployDir, "data");
        
        if (Files.exists(sourceData)) {
            FileUtils.copyDirectory(sourceData.toFile(), targetData.toFile());
            LOGGER.debug("Copied metrics data to deployment structure");
        }
    }

    private void createApiStructure(String sourceDir, String deployDir) throws IOException {
        var apiDir = Paths.get(deployDir, "api", "v1");
        Files.createDirectories(apiDir);
        
        // Create API endpoint for latest metrics
        var metricsFile = Paths.get(sourceDir, "data", "metrics.json");
        if (Files.exists(metricsFile)) {
            Files.copy(metricsFile, 
                      apiDir.resolve("latest.json"), 
                      StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Create API endpoint for badges
        var badgesDir = Paths.get(sourceDir, "badges");
        if (Files.exists(badgesDir)) {
            var apiBadgesDir = apiDir.resolve("badges");
            FileUtils.copyDirectory(badgesDir.toFile(), apiBadgesDir.toFile());
        }
        
        LOGGER.debug("Created API structure for GitHub Pages");
    }

    private void generateNavigationStructure(String deployDir) throws IOException {
        // Create a simple navigation index if main index doesn't exist
        var indexPath = Paths.get(deployDir, "index.html");
        if (!Files.exists(indexPath)) {
            var simpleIndex = createSimpleIndexPage();
            Files.writeString(indexPath, simpleIndex);
        }
        
        // Create a README for the deployment
        var readmePath = Paths.get(deployDir, "README.md");
        var readmeContent = createDeploymentReadme();
        Files.writeString(readmePath, readmeContent);
        
        LOGGER.debug("Generated navigation structure");
    }

    private String createSimpleIndexPage() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>JWT Benchmark Results</title>
                <style>
                    body { font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }
                    h1 { color: #333; }
                    .nav { margin: 20px 0; }
                    .nav a { display: inline-block; margin: 10px; padding: 10px 20px; background: #007cba; color: white; text-decoration: none; border-radius: 4px; }
                    .nav a:hover { background: #005a8a; }
                </style>
            </head>
            <body>
                <h1>JWT Validation Benchmark Results</h1>
                <div class="nav">
                    <a href="./data/metrics.json">Latest Metrics (JSON)</a>
                    <a href="./badges/">Badges</a>
                    <a href="./api/v1/latest.json">API Endpoint</a>
                </div>
                <p>This page provides access to benchmark results and performance metrics for the JWT validation library.</p>
                <h2>Available Resources</h2>
                <ul>
                    <li><strong>Metrics API:</strong> <code>/api/v1/latest.json</code> - Latest benchmark metrics</li>
                    <li><strong>Badges:</strong> <code>/badges/</code> - Performance and trend badges</li>
                    <li><strong>Raw Data:</strong> <code>/data/</code> - Complete metrics and results</li>
                </ul>
            </body>
            </html>
            """;
    }

    private String createDeploymentReadme() {
        return """
            # JWT Validation Benchmark Results
            
            This directory contains automatically generated benchmark results and performance metrics.
            
            ## Structure
            
            - `index.html` - Main results page
            - `trends.html` - Performance trends analysis
            - `badges/` - Performance badges for README integration
            - `data/` - Raw metrics and JSON data
            - `api/v1/` - API endpoints for programmatic access
            
            ## API Endpoints
            
            - `/api/v1/latest.json` - Latest benchmark metrics
            - `/api/v1/badges/*.json` - Individual badge data
            
            ## Badge Integration
            
            Use these badges in your README:
            
            ```markdown
            ![Performance](https://your-site.github.io/badges/performance-badge.json)
            ![Trend](https://your-site.github.io/badges/trend-badge.json)
            ```
            
            ## Automated Generation
            
            This content is automatically generated by the cui-benchmarking-common infrastructure
            during JMH benchmark execution.
            """;
    }
}