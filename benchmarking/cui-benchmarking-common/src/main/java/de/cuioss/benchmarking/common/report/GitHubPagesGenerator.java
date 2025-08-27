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
import com.google.gson.GsonBuilder;
import de.cuioss.tools.logging.CuiLogger;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.*;

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

    // File and directory name constants
    private static final String INDEX_HTML = "index.html";
    private static final String REPORTS_DIR = "reports";
    private static final String BENCHMARK_SUMMARY_JSON = "benchmark-summary.json";
    private static final String DATA_METRICS_JSON = "data/metrics.json";
    private static final String BADGES_DIR = "badges";
    private static final String BENCHMARKS_KEY = "benchmarks";

    // JSON field constants
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String TOTAL_BENCHMARKS_KEY = "total_benchmarks";
    private static final String PERFORMANCE_GRADE_KEY = "performance_grade";
    private static final String AVERAGE_THROUGHPUT_KEY = "average_throughput";
    private static final String OPERATIONAL_STATUS = "operational";
    private static final String NO_DATA_STATUS = "no_data";

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
        copyIfExists(sourceDir.resolve(INDEX_HTML), deployDir.resolve(INDEX_HTML));
        copyIfExists(sourceDir.resolve("trends.html"), deployDir.resolve("trends.html"));
        copyIfExists(sourceDir.resolve("detailed.html"), deployDir.resolve("detailed.html"));

        // Copy any additional HTML files
        if (Files.exists(sourceDir.resolve(REPORTS_DIR))) {
            try (var stream = Files.walk(sourceDir.resolve(REPORTS_DIR))) {
                stream.filter(path -> path.toString().endsWith(".html"))
                        .forEach(htmlFile -> {
                            try {
                                Path targetFile = deployDir.resolve(sourceDir.resolve(REPORTS_DIR).relativize(htmlFile));
                                Files.createDirectories(targetFile.getParent());
                                Files.copy(htmlFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                LOGGER.warn(WARN.FAILED_COPY_HTML.format(htmlFile), e);
                            }
                        });
            }
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
        Path summaryFile = sourceDir.resolve(BENCHMARK_SUMMARY_JSON);

        Map<String, Object> latestData = new LinkedHashMap<>();
        latestData.put(TIMESTAMP_KEY, Instant.now().toString());
        latestData.put("status", "success");

        Map<String, Object> summary = new LinkedHashMap<>();
        if (Files.exists(summaryFile)) {
            String summaryContent = Files.readString(summaryFile);
            var gson = new Gson();
            @SuppressWarnings("unchecked") Map<String, Object> summaryData = gson.fromJson(summaryContent, Map.class);
            summary.put(TOTAL_BENCHMARKS_KEY, summaryData.getOrDefault(TOTAL_BENCHMARKS_KEY, 0));
            summary.put(PERFORMANCE_GRADE_KEY, summaryData.getOrDefault(PERFORMANCE_GRADE_KEY, "N/A"));
            summary.put(AVERAGE_THROUGHPUT_KEY, summaryData.getOrDefault(AVERAGE_THROUGHPUT_KEY, 0.0));
        } else {
            summary.put(TOTAL_BENCHMARKS_KEY, 0);
            summary.put(PERFORMANCE_GRADE_KEY, "N/A");
        }
        latestData.put("summary", summary);

        Map<String, String> links = new LinkedHashMap<>();
        links.put("full_metrics", "/api/metrics.json");
        links.put(BENCHMARKS_KEY, "/api/benchmarks.json");
        links.put(BADGES_DIR, "/badges/");
        latestData.put("links", links);

        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(latestFile, gson.toJson(latestData));
        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(latestFile));
    }

    private void createBenchmarksEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path sourceMetrics = sourceDir.resolve(DATA_METRICS_JSON);
        Path benchmarksFile = apiDir.resolve("benchmarks.json");

        if (Files.exists(sourceMetrics)) {
            Files.copy(sourceMetrics, benchmarksFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Map<String, Object> benchmarksData = new LinkedHashMap<>();
            benchmarksData.put(BENCHMARKS_KEY, new LinkedHashMap<>());
            benchmarksData.put("generated", Instant.now().toString());

            var gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(benchmarksFile, gson.toJson(benchmarksData));
        }

        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(benchmarksFile));
    }

    private void createMetricsEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path sourceMetrics = sourceDir.resolve(DATA_METRICS_JSON);
        Path metricsFile = apiDir.resolve("metrics.json");

        if (Files.exists(sourceMetrics)) {
            Files.copy(sourceMetrics, metricsFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Map<String, Object> emptyMetrics = new LinkedHashMap<>();
            emptyMetrics.put(TIMESTAMP_KEY, Instant.now().toString());
            emptyMetrics.put(BENCHMARKS_KEY, new LinkedHashMap<>());
            emptyMetrics.put("summary", Map.of(
                    TOTAL_BENCHMARKS_KEY, 0,
                    "total_score", 0.0,
                    AVERAGE_THROUGHPUT_KEY, 0.0,
                    PERFORMANCE_GRADE_KEY, "N/A"
            ));

            var gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(metricsFile, gson.toJson(emptyMetrics));
        }

        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(metricsFile));
    }

    private void createStatusEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path statusFile = apiDir.resolve("status.json");
        Path summaryFile = sourceDir.resolve(BENCHMARK_SUMMARY_JSON);

        Map<String, Object> statusData = new LinkedHashMap<>();
        statusData.put("status", "healthy");
        statusData.put(TIMESTAMP_KEY, Instant.now().toString());

        if (Files.exists(summaryFile)) {
            String summaryContent = Files.readString(summaryFile);
            var gson = new Gson();
            @SuppressWarnings("unchecked") Map<String, Object> summaryData = gson.fromJson(summaryContent, Map.class);
            statusData.put("last_run", summaryData.getOrDefault(TIMESTAMP_KEY, Instant.now().toString()));
        } else {
            statusData.put("last_run", Instant.now().toString());
        }

        Map<String, String> services = new LinkedHashMap<>();
        services.put(BENCHMARKS_KEY, Files.exists(sourceDir.resolve(DATA_METRICS_JSON)) ? OPERATIONAL_STATUS : NO_DATA_STATUS);
        services.put("metrics", Files.exists(sourceDir.resolve(DATA_METRICS_JSON)) ? OPERATIONAL_STATUS : NO_DATA_STATUS);
        services.put(REPORTS_DIR, Files.exists(sourceDir.resolve(INDEX_HTML)) ? OPERATIONAL_STATUS : NO_DATA_STATUS);
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

        Path sourceBadges = sourceDir.resolve(BADGES_DIR);
        Path deployBadges = deployDir.resolve(BADGES_DIR);

        if (Files.exists(sourceBadges)) {
            Files.createDirectories(deployBadges);

            try (var stream = Files.walk(sourceBadges)) {
                stream.filter(path -> path.toString().endsWith(".json"))
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

            try (var stream = Files.walk(sourceData)) {
                stream.filter(Files::isRegularFile)
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
        String html404 = loadTemplate("404.html");
        Files.writeString(deployDir.resolve("404.html"), html404);
    }

    /**
     * Generates robots.txt for search engines.
     */
    private void generateRobotsTxt(Path deployDir) throws IOException {
        String robotsTxt = loadTemplate("robots.txt");
        Files.writeString(deployDir.resolve("robots.txt"), robotsTxt);
    }

    /**
     * Generates a basic sitemap.xml.
     */
    private void generateSitemap(Path deployDir) throws IOException {
        String sitemap = loadTemplate("sitemap.xml");
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
     * Loads a template from the classpath resources.
     *
     * @param templateName the name of the template file
     * @return the template content as a string
     * @throws IOException if the template cannot be loaded
     */
    private String loadTemplate(String templateName) throws IOException {
        String resourcePath = "/templates/" + templateName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}