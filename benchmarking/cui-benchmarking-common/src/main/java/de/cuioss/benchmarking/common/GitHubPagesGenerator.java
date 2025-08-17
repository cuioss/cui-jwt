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

import de.cuioss.tools.logging.CuiLogger;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Generates GitHub Pages ready deployment structure from benchmark artifacts.
 * <p>
 * This generator creates a complete directory structure that can be deployed
 * directly to GitHub Pages or any static hosting service.
 * <p>
 * Generated structure:
 * <ul>
 *   <li>index.html - Main performance overview page</li>
 *   <li>trends.html - Historical trends and analysis</li>
 *   <li>api/ - JSON API endpoints for programmatic access</li>
 *   <li>badges/ - Shield.io compatible badge JSON files</li>
 *   <li>data/ - Raw metrics and performance data</li>
 * </ul>
 */
public class GitHubPagesGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(GitHubPagesGenerator.class);

    /**
     * Prepares the complete GitHub Pages deployment structure.
     *
     * @param sourceDir the source directory containing generated artifacts
     * @param deployDir the target directory for deployment-ready structure
     * @throws IOException if file operations fail
     */
    public void prepareDeploymentStructure(String sourceDir, String deployDir) throws IOException {
        LOGGER.info("Preparing GitHub Pages deployment structure");
        LOGGER.info("Source: {}", sourceDir);
        LOGGER.info("Deploy: {}", deployDir);
        
        Path deployPath = Paths.get(deployDir);
        Path sourcePath = Paths.get(sourceDir);
        
        // Clean and create deployment directory
        if (Files.exists(deployPath)) {
            FileUtils.deleteDirectory(deployPath.toFile());
        }
        Files.createDirectories(deployPath);
        
        // Copy HTML reports to root
        copyHtmlFiles(sourcePath, deployPath);
        
        // Create API endpoints
        createApiEndpoints(sourcePath, deployPath);
        
        // Copy badge files
        copyBadgeFiles(sourcePath, deployPath);
        
        // Copy data files
        copyDataFiles(sourcePath, deployPath);
        
        // Generate additional pages
        generateAdditionalPages(deployPath);
        
        LOGGER.info("GitHub Pages deployment structure ready");
    }

    /**
     * Copies HTML report files to the deployment root.
     */
    private void copyHtmlFiles(Path sourceDir, Path deployDir) throws IOException {
        LOGGER.debug("Copying HTML files");
        
        // Copy main reports
        copyIfExists(sourceDir.resolve("index.html"), deployDir.resolve("index.html"));
        copyIfExists(sourceDir.resolve("trends.html"), deployDir.resolve("trends.html"));
        
        // Copy any additional HTML files
        if (Files.exists(sourceDir.resolve("reports"))) {
            Files.walk(sourceDir.resolve("reports"))
                .filter(path -> path.toString().endsWith(".html"))
                .forEach(htmlFile -> {
                    try {
                        Path targetFile = deployDir.resolve(sourceDir.resolve("reports").relativize(htmlFile));
                        Files.createDirectories(targetFile.getParent());
                        Files.copy(htmlFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to copy HTML file: {}", htmlFile, e);
                    }
                });
        }
    }

    /**
     * Creates API endpoints for programmatic access to benchmark data.
     */
    private void createApiEndpoints(Path sourceDir, Path deployDir) throws IOException {
        LOGGER.debug("Creating API endpoints");
        
        Path apiDir = deployDir.resolve("api");
        Files.createDirectories(apiDir);
        
        // Create API structure
        createLatestEndpoint(sourceDir, apiDir);
        createBenchmarksEndpoint(sourceDir, apiDir);
        createMetricsEndpoint(sourceDir, apiDir);
        createStatusEndpoint(sourceDir, apiDir);
    }

    /**
     * Creates the /api/latest endpoint with current performance data.
     */
    private void createLatestEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path latestFile = apiDir.resolve("latest.json");
        
        // Create a simplified latest results JSON
        String latestJson = """
            {
              "timestamp": "%s",
              "status": "success",
              "summary": {
                "total_benchmarks": %d,
                "performance_grade": "%s"
              },
              "links": {
                "full_metrics": "/api/metrics.json",
                "benchmarks": "/api/benchmarks.json",
                "badges": "/badges/"
              }
            }
            """.formatted(
                java.time.Instant.now().toString(),
                countBenchmarks(sourceDir),
                "A" // Placeholder grade
            );
        
        Files.writeString(latestFile, latestJson);
        LOGGER.debug("Created API endpoint: {}", latestFile);
    }

    /**
     * Creates the /api/benchmarks endpoint with benchmark list.
     */
    private void createBenchmarksEndpoint(Path sourceDir, Path apiDir) throws IOException {
        // Copy existing benchmarks data or create minimal structure
        Path sourceMetrics = sourceDir.resolve("data/metrics.json");
        Path benchmarksFile = apiDir.resolve("benchmarks.json");
        
        if (Files.exists(sourceMetrics)) {
            Files.copy(sourceMetrics, benchmarksFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            // Create minimal benchmarks structure
            String benchmarksJson = """
                {
                  "benchmarks": {},
                  "generated": "%s"
                }
                """.formatted(java.time.Instant.now().toString());
            Files.writeString(benchmarksFile, benchmarksJson);
        }
        
        LOGGER.debug("Created API endpoint: {}", benchmarksFile);
    }

    /**
     * Creates the /api/metrics endpoint with detailed metrics.
     */
    private void createMetricsEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path sourceMetrics = sourceDir.resolve("data/metrics.json");
        Path metricsFile = apiDir.resolve("metrics.json");
        
        copyIfExists(sourceMetrics, metricsFile);
        LOGGER.debug("Created API endpoint: {}", metricsFile);
    }

    /**
     * Creates the /api/status endpoint with system status.
     */
    private void createStatusEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path statusFile = apiDir.resolve("status.json");
        
        String statusJson = """
            {
              "status": "healthy",
              "last_run": "%s",
              "services": {
                "benchmarks": "operational",
                "metrics": "operational",
                "reports": "operational"
              }
            }
            """.formatted(java.time.Instant.now().toString());
        
        Files.writeString(statusFile, statusJson);
        LOGGER.debug("Created API endpoint: {}", statusFile);
    }

    /**
     * Copies badge files to the deployment structure.
     */
    private void copyBadgeFiles(Path sourceDir, Path deployDir) throws IOException {
        LOGGER.debug("Copying badge files");
        
        Path sourceBadges = sourceDir.resolve("badges");
        Path deployBadges = deployDir.resolve("badges");
        
        if (Files.exists(sourceBadges)) {
            Files.createDirectories(deployBadges);
            
            Files.walk(sourceBadges)
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(badgeFile -> {
                    try {
                        Path targetFile = deployBadges.resolve(sourceBadges.relativize(badgeFile));
                        Files.copy(badgeFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to copy badge file: {}", badgeFile, e);
                    }
                });
        }
    }

    /**
     * Copies data files to the deployment structure.
     */
    private void copyDataFiles(Path sourceDir, Path deployDir) throws IOException {
        LOGGER.debug("Copying data files");
        
        Path sourceData = sourceDir.resolve("data");
        Path deployData = deployDir.resolve("data");
        
        if (Files.exists(sourceData)) {
            Files.createDirectories(deployData);
            
            Files.walk(sourceData)
                .filter(Files::isRegularFile)
                .forEach(dataFile -> {
                    try {
                        Path targetFile = deployData.resolve(sourceData.relativize(dataFile));
                        Files.createDirectories(targetFile.getParent());
                        Files.copy(dataFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to copy data file: {}", dataFile, e);
                    }
                });
        }
    }

    /**
     * Generates additional pages for the GitHub Pages site.
     */
    private void generateAdditionalPages(Path deployDir) throws IOException {
        LOGGER.debug("Generating additional pages");
        
        // Generate 404 page
        generate404Page(deployDir);
        
        // Generate robots.txt
        generateRobotsTxt(deployDir);
        
        // Generate sitemap.xml (basic)
        generateSitemap(deployDir);
    }

    /**
     * Generates a 404 error page.
     */
    private void generate404Page(Path deployDir) throws IOException {
        String html404 = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Page Not Found - CUI Benchmarking</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; margin-top: 100px; }
                    h1 { color: #e74c3c; }
                    a { color: #3498db; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
                <h1>404 - Page Not Found</h1>
                <p>The requested page could not be found.</p>
                <p><a href="/">Return to Benchmark Overview</a></p>
            </body>
            </html>
            """;
        
        Files.writeString(deployDir.resolve("404.html"), html404);
    }

    /**
     * Generates robots.txt for search engines.
     */
    private void generateRobotsTxt(Path deployDir) throws IOException {
        String robotsTxt = """
            User-agent: *
            Allow: /
            
            Sitemap: /sitemap.xml
            """;
        
        Files.writeString(deployDir.resolve("robots.txt"), robotsTxt);
    }

    /**
     * Generates a basic sitemap.xml.
     */
    private void generateSitemap(Path deployDir) throws IOException {
        String sitemap = """
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                <url>
                    <loc>/</loc>
                    <changefreq>daily</changefreq>
                    <priority>1.0</priority>
                </url>
                <url>
                    <loc>/trends.html</loc>
                    <changefreq>daily</changefreq>
                    <priority>0.8</priority>
                </url>
                <url>
                    <loc>/api/latest.json</loc>
                    <changefreq>daily</changefreq>
                    <priority>0.6</priority>
                </url>
            </urlset>
            """;
        
        Files.writeString(deployDir.resolve("sitemap.xml"), sitemap);
    }

    /**
     * Copies a file if it exists, creating parent directories as needed.
     */
    private void copyIfExists(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Counts the number of benchmarks (placeholder implementation).
     */
    private int countBenchmarks(Path sourceDir) {
        // Placeholder - in a real implementation, this would count benchmarks
        // from the metrics or summary files
        return 1;
    }
}