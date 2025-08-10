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
package de.cuioss.benchmarking.pages;

import de.cuioss.tools.logging.CuiLogger;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Prepares the GitHub Pages deployment structure by organizing all generated artifacts
 * into a deployment-ready directory structure.
 * <p>
 * This generator creates a complete GitHub Pages site structure that can be deployed
 * with minimal shell script operations.
 *
 * @since 1.0.0
 */
public class GitHubPagesGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(GitHubPagesGenerator.class);

    /**
     * Prepares the complete deployment structure for GitHub Pages.
     * <p>
     * Copies all generated artifacts (HTML reports, badges, data files) into
     * a deployment-ready directory structure.
     *
     * @param sourceDir the source directory containing generated artifacts
     * @param deploymentDir the target deployment directory
     * @throws IOException if file operations fail
     */
    public void prepareDeploymentStructure(String sourceDir, String deploymentDir) throws IOException {
        LOGGER.info("Preparing GitHub Pages deployment structure");
        LOGGER.info("Source: %s", sourceDir);
        LOGGER.info("Deployment: %s", deploymentDir);

        Path source = Paths.get(sourceDir);
        Path deployment = Paths.get(deploymentDir);

        // Create deployment directory
        Files.createDirectories(deployment);

        // Copy HTML reports (index.html, trends.html, etc.)
        copyHtmlReports(source, deployment);

        // Copy badges directory
        copyBadges(source, deployment);

        // Copy data directory
        copyData(source, deployment);

        // Create additional GitHub Pages files
        createGitHubPagesConfig(deployment);
        createReadmeFile(deployment);

        LOGGER.info("GitHub Pages deployment structure prepared successfully");
    }

    /**
     * Copies HTML report files to the deployment directory.
     */
    private void copyHtmlReports(Path source, Path deployment) throws IOException {
        LOGGER.info("Copying HTML reports...");

        // Copy main HTML files
        copyFileIfExists(source.resolve("index.html"), deployment.resolve("index.html"));
        copyFileIfExists(source.resolve("trends.html"), deployment.resolve("trends.html"));

        // Copy any other HTML files in the reports directory
        Path reportsDir = source.resolve("reports");
        if (Files.exists(reportsDir)) {
            Path deploymentReports = deployment.resolve("reports");
            Files.createDirectories(deploymentReports);
            FileUtils.copyDirectory(reportsDir.toFile(), deploymentReports.toFile());
        }
    }

    /**
     * Copies badge files to the deployment directory.
     */
    private void copyBadges(Path source, Path deployment) throws IOException {
        LOGGER.info("Copying badges...");

        Path badgesSource = source.resolve("badges");
        if (Files.exists(badgesSource)) {
            Path badgesDeployment = deployment.resolve("badges");
            Files.createDirectories(badgesDeployment);
            FileUtils.copyDirectory(badgesSource.toFile(), badgesDeployment.toFile());
        }
    }

    /**
     * Copies data files to the deployment directory.
     */
    private void copyData(Path source, Path deployment) throws IOException {
        LOGGER.info("Copying data files...");

        Path dataSource = source.resolve("data");
        if (Files.exists(dataSource)) {
            Path dataDeployment = deployment.resolve("data");
            Files.createDirectories(dataDeployment);
            FileUtils.copyDirectory(dataSource.toFile(), dataDeployment.toFile());
        }

        // Also copy raw result files if they exist
        copyFileIfExists(source.resolve("raw-result.json"), deployment.resolve("data/raw-result.json"));
        copyFileIfExists(source.resolve("benchmark-summary.json"), deployment.resolve("data/benchmark-summary.json"));
    }

    /**
     * Creates GitHub Pages configuration files.
     */
    private void createGitHubPagesConfig(Path deployment) throws IOException {
        LOGGER.info("Creating GitHub Pages configuration...");

        // Create _config.yml for Jekyll configuration
        String jekyllConfig = """
            title: "JWT Benchmark Results"
            description: "Performance benchmark results for JWT validation"
            theme: minima
            
            # Enable plugins
            plugins:
              - jekyll-feed
              - jekyll-sitemap
            
            # Custom settings
            show_downloads: false
            github:
              is_project_page: true
            """;

        Files.write(deployment.resolve("_config.yml"), jekyllConfig.getBytes());

        // Create .nojekyll file to bypass Jekyll processing if needed
        Files.write(deployment.resolve(".nojekyll"), "".getBytes());
    }

    /**
     * Creates a README file for the GitHub Pages site.
     */
    private void createReadmeFile(Path deployment) throws IOException {
        LOGGER.info("Creating README file...");

        String readme = """
            # JWT Benchmark Results
            
            This site contains performance benchmark results for JWT token validation.
            
            ## Available Reports
            
            - [Current Results](index.html) - Latest benchmark execution results
            - [Performance Trends](trends.html) - Historical performance analysis
            - [Raw Data](data/metrics.json) - Machine-readable performance data
            
            ## Badge Endpoints
            
            Performance badges are available at:
            
            - [Performance Badge](badges/performance-badge.json)
            - [Trend Badge](badges/trend-badge.json)
            - [Last Run Badge](badges/last-run-badge.json)
            
            These JSON files can be consumed by Shields.io for dynamic badge generation.
            
            ## Data Files
            
            - `data/metrics.json` - Comprehensive performance metrics
            - `data/raw-result.json` - Raw JMH benchmark results
            - `data/benchmark-summary.json` - Benchmark execution summary
            
            Generated by CUI Benchmarking Infrastructure.
            """;

        Files.write(deployment.resolve("README.md"), readme.getBytes());
    }

    /**
     * Copies a file if it exists, creating parent directories as needed.
     */
    private void copyFileIfExists(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("Copied: %s -> %s", source, target);
        }
    }
}