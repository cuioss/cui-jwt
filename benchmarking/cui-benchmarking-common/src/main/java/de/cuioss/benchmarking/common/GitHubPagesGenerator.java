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
import com.google.gson.GsonBuilder;
import de.cuioss.tools.logging.CuiLogger;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static de.cuioss.benchmarking.common.BenchmarkingLogMessages.DEBUG;
import static de.cuioss.benchmarking.common.BenchmarkingLogMessages.INFO;
import static de.cuioss.benchmarking.common.BenchmarkingLogMessages.WARN;

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

    private static final CuiLogger LOGGER =
            new CuiLogger(GitHubPagesGenerator.class);

    /**
     * Prepares the complete GitHub Pages deployment structure.
     *
     * @param sourceDir the source directory containing generated artifacts
     * @param deployDir the target directory for deployment-ready structure
     * @throws IOException if file operations fail
     */
    public void prepareDeploymentStructure(String sourceDir, String deployDir) throws IOException {
        LOGGER.info(INFO.PREPARING_GITHUB_PAGES::format);
        LOGGER.info(INFO.SOURCE_DIRECTORY.format(sourceDir));
        LOGGER.info(INFO.DEPLOY_DIRECTORY.format(deployDir));

        Path deployPath = Path.of(deployDir);
        Path sourcePath = Path.of(sourceDir);

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

        LOGGER.info(INFO.GITHUB_PAGES_READY::format);
    }

    /**
     * Copies HTML report files to the deployment root.
     */
    private void copyHtmlFiles(Path sourceDir, Path deployDir) throws IOException {
        LOGGER.debug(DEBUG.COPYING_HTML_FILES::format);

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
                            LOGGER.warn(WARN.FAILED_COPY_HTML.format(htmlFile), e);
                        }
                    });
        }
    }

    /**
     * Creates API endpoints for programmatic access to benchmark data.
     */
    private void createApiEndpoints(Path sourceDir, Path deployDir) throws IOException {
        LOGGER.debug(DEBUG.CREATING_API_ENDPOINTS::format);

        Path apiDir = deployDir.resolve("api");
        Files.createDirectories(apiDir);

        // Create API structure
        createLatestEndpoint(sourceDir, apiDir);
        createBenchmarksEndpoint(sourceDir, apiDir);
        createMetricsEndpoint(sourceDir, apiDir);
        createStatusEndpoint(sourceDir, apiDir);
    }

    private void createLatestEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path latestFile = apiDir.resolve("latest.json");
        Path summaryFile = sourceDir.resolve("benchmark-summary.json");

        Map<String, Object> latestData = new LinkedHashMap<>();
        latestData.put("timestamp", Instant.now().toString());
        latestData.put("status", "success");

        Map<String, Object> summary = new LinkedHashMap<>();
        if (Files.exists(summaryFile)) {
            String summaryContent = Files.readString(summaryFile);
            var gson = new Gson();
            var summaryData = gson.fromJson(summaryContent, Map.class);
            summary.put("total_benchmarks", summaryData.getOrDefault("total_benchmarks", 0));
            summary.put("performance_grade", summaryData.getOrDefault("performance_grade", "N/A"));
            summary.put("average_throughput", summaryData.getOrDefault("average_throughput", 0.0));
        } else {
            summary.put("total_benchmarks", 0);
            summary.put("performance_grade", "N/A");
        }
        latestData.put("summary", summary);

        Map<String, String> links = new LinkedHashMap<>();
        links.put("full_metrics", "/api/metrics.json");
        links.put("benchmarks", "/api/benchmarks.json");
        links.put("badges", "/badges/");
        latestData.put("links", links);

        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(latestFile, gson.toJson(latestData));
        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(latestFile));
    }

    private void createBenchmarksEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path sourceMetrics = sourceDir.resolve("data/metrics.json");
        Path benchmarksFile = apiDir.resolve("benchmarks.json");

        if (Files.exists(sourceMetrics)) {
            Files.copy(sourceMetrics, benchmarksFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Map<String, Object> benchmarksData = new LinkedHashMap<>();
            benchmarksData.put("benchmarks", new LinkedHashMap<>());
            benchmarksData.put("generated", Instant.now().toString());

            var gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(benchmarksFile, gson.toJson(benchmarksData));
        }

        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(benchmarksFile));
    }

    private void createMetricsEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path sourceMetrics = sourceDir.resolve("data/metrics.json");
        Path metricsFile = apiDir.resolve("metrics.json");

        if (Files.exists(sourceMetrics)) {
            Files.copy(sourceMetrics, metricsFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Map<String, Object> emptyMetrics = new LinkedHashMap<>();
            emptyMetrics.put("timestamp", Instant.now().toString());
            emptyMetrics.put("benchmarks", new LinkedHashMap<>());
            emptyMetrics.put("summary", Map.of(
                    "total_benchmarks", 0,
                    "total_score", 0.0,
                    "average_throughput", 0.0,
                    "performance_grade", "N/A"
            ));

            var gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(metricsFile, gson.toJson(emptyMetrics));
        }

        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(metricsFile));
    }

    private void createStatusEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path statusFile = apiDir.resolve("status.json");
        Path summaryFile = sourceDir.resolve("benchmark-summary.json");

        Map<String, Object> statusData = new LinkedHashMap<>();
        statusData.put("status", "healthy");
        statusData.put("timestamp", Instant.now().toString());

        if (Files.exists(summaryFile)) {
            String summaryContent = Files.readString(summaryFile);
            var gson = new Gson();
            var summaryData = gson.fromJson(summaryContent, Map.class);
            statusData.put("last_run", summaryData.getOrDefault("timestamp", Instant.now().toString()));
        } else {
            statusData.put("last_run", Instant.now().toString());
        }

        Map<String, String> services = new LinkedHashMap<>();
        services.put("benchmarks", Files.exists(sourceDir.resolve("data/metrics.json")) ? "operational" : "no_data");
        services.put("metrics", Files.exists(sourceDir.resolve("data/metrics.json")) ? "operational" : "no_data");
        services.put("reports", Files.exists(sourceDir.resolve("index.html")) ? "operational" : "no_data");
        statusData.put("services", services);

        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(statusFile, gson.toJson(statusData));
        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(statusFile));
    }

    /**
     * Copies badge files to the deployment structure.
     */
    private void copyBadgeFiles(Path sourceDir, Path deployDir) throws IOException {
        LOGGER.debug(DEBUG.COPYING_BADGE_FILES::format);

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
                            LOGGER.warn(WARN.FAILED_COPY_BADGE.format(badgeFile), e);
                        }
                    });
        }
    }

    /**
     * Copies data files to the deployment structure.
     */
    private void copyDataFiles(Path sourceDir, Path deployDir) throws IOException {
        LOGGER.debug(DEBUG.COPYING_DATA_FILES::format);

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
                            LOGGER.warn(WARN.FAILED_COPY_DATA.format(dataFile), e);
                        }
                    });
        }
    }

    /**
     * Generates additional pages for the GitHub Pages site.
     */
    private void generateAdditionalPages(Path deployDir) throws IOException {
        LOGGER.debug(DEBUG.GENERATING_ADDITIONAL_PAGES::format);

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

    private int countBenchmarks(Path sourceDir) {
        Path metricsFile = sourceDir.resolve("data/metrics.json");
        Path summaryFile = sourceDir.resolve("benchmark-summary.json");

        if (Files.exists(metricsFile)) {
            try {
                String content = Files.readString(metricsFile);
                var gson = new Gson();
                var metrics = gson.fromJson(content, Map.class);
                var benchmarks = (Map<String, Object>) metrics.get("benchmarks");
                return benchmarks != null ? benchmarks.size() : 0;
            } catch (IOException e) {
                LOGGER.debug("Failed to read metrics file for benchmark count", e);
            }
        }

        if (Files.exists(summaryFile)) {
            try {
                String content = Files.readString(summaryFile);
                var gson = new Gson();
                var summary = gson.fromJson(content, Map.class);
                Object count = summary.get("total_benchmarks");
                if (count instanceof Number number) {
                    return number.intValue();
                }
            } catch (IOException e) {
                LOGGER.debug("Failed to read summary file for benchmark count", e);
            }
        }

        return 0;
    }
}