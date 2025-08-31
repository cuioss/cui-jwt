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
import de.cuioss.benchmarking.common.constants.BenchmarkConstants;
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

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Data.BENCHMARK_DATA_JSON;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Data.BENCHMARK_SUMMARY_JSON;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Directories.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Extensions.HTML;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Extensions.JSON;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Html.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Support.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Api.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Defaults.N_A;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.JsonFields.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Templates.NOT_FOUND_FORMAT;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Templates.PATH_PREFIX;
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
        copyIfExists(sourceDir.resolve(INDEX), deployDir.resolve(INDEX));
        copyIfExists(sourceDir.resolve(BenchmarkConstants.Files.Html.TRENDS), deployDir.resolve(BenchmarkConstants.Files.Html.TRENDS));
        copyIfExists(sourceDir.resolve(DETAILED), deployDir.resolve(DETAILED));

        // Copy support files (CSS, JS)
        copyIfExists(sourceDir.resolve(REPORT_STYLES_CSS), deployDir.resolve(REPORT_STYLES_CSS));
        copyIfExists(sourceDir.resolve(DATA_LOADER_JS), deployDir.resolve(DATA_LOADER_JS));

        // Copy any additional HTML files
        if (Files.exists(sourceDir.resolve(REPORTS_DIR))) {
            try (var stream = Files.walk(sourceDir.resolve(REPORTS_DIR))) {
                stream.filter(path -> path.toString().endsWith(HTML))
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

        Path apiDir = deployDir.resolve(API_DIR);
        Files.createDirectories(apiDir);

        // Create API structure
        createLatestEndpoint(sourceDir, apiDir);
        createBenchmarksEndpoint(sourceDir, apiDir);
        createStatusEndpoint(sourceDir, apiDir);
    }

    private void createLatestEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path latestFile = apiDir.resolve(LATEST_JSON);
        Path summaryFile = sourceDir.resolve(BENCHMARK_SUMMARY_JSON);

        Map<String, Object> latestData = new LinkedHashMap<>();
        latestData.put(TIMESTAMP, Instant.now().toString());
        latestData.put(STATUS, STATUS_SUCCESS);

        Map<String, Object> summary = new LinkedHashMap<>();
        if (Files.exists(summaryFile)) {
            String summaryContent = Files.readString(summaryFile);
            var gson = new Gson();
            @SuppressWarnings("unchecked") Map<String, Object> summaryData = gson.fromJson(summaryContent, Map.class);
            summary.put(TOTAL_BENCHMARKS, summaryData.getOrDefault(TOTAL_BENCHMARKS, 0));
            summary.put(PERFORMANCE_GRADE_KEY, summaryData.getOrDefault(PERFORMANCE_GRADE_KEY, N_A));
            summary.put(AVERAGE_THROUGHPUT, summaryData.getOrDefault(AVERAGE_THROUGHPUT, 0.0));
        } else {
            summary.put(TOTAL_BENCHMARKS, 0);
            summary.put(PERFORMANCE_GRADE_KEY, N_A);
        }
        latestData.put(SUMMARY, summary);

        Map<String, String> links = new LinkedHashMap<>();
        links.put(BENCHMARKS, API_BENCHMARKS_PATH);
        links.put(BADGES_DIR, BADGES_PATH);
        latestData.put(LINKS, links);

        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(latestFile, gson.toJson(latestData));
        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(latestFile));
    }

    private void createBenchmarksEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path benchmarksFile = apiDir.resolve(BENCHMARKS_JSON);
        Path benchmarkDataFile = sourceDir.resolve(DATA_DIR + "/" + BENCHMARK_DATA_JSON);

        if (Files.exists(benchmarkDataFile)) {
            // Extract benchmark data from benchmark-data.json
            String content = Files.readString(benchmarkDataFile);
            var gson = new Gson();
            @SuppressWarnings("unchecked") Map<String, Object> data = gson.fromJson(content, Map.class);
            Map<String, Object> benchmarksData = new LinkedHashMap<>();
            benchmarksData.put(BENCHMARKS, data.getOrDefault(BENCHMARKS, new LinkedHashMap<>()));
            benchmarksData.put(GENERATED, Instant.now().toString());

            var gsonWriter = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(benchmarksFile, gsonWriter.toJson(benchmarksData));
        } else {
            Map<String, Object> benchmarksData = new LinkedHashMap<>();
            benchmarksData.put(BENCHMARKS, new LinkedHashMap<>());
            benchmarksData.put(GENERATED, Instant.now().toString());

            var gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(benchmarksFile, gson.toJson(benchmarksData));
        }

        LOGGER.debug(DEBUG.API_ENDPOINT_CREATED.format(benchmarksFile));
    }


    private void createStatusEndpoint(Path sourceDir, Path apiDir) throws IOException {
        Path statusFile = apiDir.resolve(STATUS_JSON);
        Path summaryFile = sourceDir.resolve(BENCHMARK_SUMMARY_JSON);

        Map<String, Object> statusData = new LinkedHashMap<>();
        statusData.put(STATUS, STATUS_HEALTHY);
        statusData.put(TIMESTAMP, Instant.now().toString());

        if (Files.exists(summaryFile)) {
            String summaryContent = Files.readString(summaryFile);
            var gson = new Gson();
            @SuppressWarnings("unchecked") Map<String, Object> summaryData = gson.fromJson(summaryContent, Map.class);
            statusData.put(LAST_RUN, summaryData.getOrDefault(TIMESTAMP, Instant.now().toString()));
        } else {
            statusData.put(LAST_RUN, Instant.now().toString());
        }

        Map<String, String> services = new LinkedHashMap<>();
        services.put(BENCHMARKS, Files.exists(sourceDir.resolve(DATA_DIR + "/" + BENCHMARK_DATA_JSON)) ? STATUS_OPERATIONAL : STATUS_NO_DATA);
        services.put(REPORTS_DIR, Files.exists(sourceDir.resolve(INDEX)) ? STATUS_OPERATIONAL : STATUS_NO_DATA);
        statusData.put(SERVICES, services);

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
                stream.filter(path -> path.toString().endsWith(JSON))
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

        Path sourceData = sourceDir.resolve(DATA_DIR);
        Path deployData = deployDir.resolve(DATA_DIR);

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
        String html404 = loadTemplate(ERROR_404);
        Files.writeString(deployDir.resolve(ERROR_404), html404);
    }

    /**
     * Generates robots.txt for search engines.
     */
    private void generateRobotsTxt(Path deployDir) throws IOException {
        String robotsTxt = loadTemplate(ROBOTS_TXT);
        Files.writeString(deployDir.resolve(ROBOTS_TXT), robotsTxt);
    }

    /**
     * Generates a basic sitemap.xml.
     */
    private void generateSitemap(Path deployDir) throws IOException {
        String sitemap = loadTemplate(SITEMAP_XML);
        Files.writeString(deployDir.resolve(SITEMAP_XML), sitemap);
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
        String resourcePath = PATH_PREFIX + templateName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException(NOT_FOUND_FORMAT.formatted(resourcePath));
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}