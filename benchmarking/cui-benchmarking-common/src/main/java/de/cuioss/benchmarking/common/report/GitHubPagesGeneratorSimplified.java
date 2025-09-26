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

import de.cuioss.benchmarking.common.output.OutputDirectoryStructure;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Html.ERROR_404;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Support.ROBOTS_TXT;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Support.SITEMAP_XML;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Templates.NOT_FOUND_FORMAT;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Templates.PATH_PREFIX;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.DEBUG;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Simplified GitHubPagesGenerator that only generates deployment-specific files.
 * Since all report files are now written directly to gh-pages-ready by other generators,
 * this class only needs to generate additional deployment assets (404.html, robots.txt, sitemap.xml).
 * <p>
 * NO COPYING is performed - everything else is already in gh-pages-ready.
 */
public class GitHubPagesGeneratorSimplified {

    private static final CuiLogger LOGGER = new CuiLogger(GitHubPagesGeneratorSimplified.class);

    /**
     * Generates deployment-specific assets for GitHub Pages.
     * This assumes all report files are already written to gh-pages-ready directory.
     *
     * @param structure the output directory structure
     * @throws IOException if file operations fail
     */
    public void generateDeploymentAssets(OutputDirectoryStructure structure) throws IOException {
        LOGGER.info(INFO.PREPARING_GITHUB_PAGES::format);

        // Ensure deployment directory exists
        structure.ensureDirectories();

        Path deployDir = structure.getDeploymentDir();
        LOGGER.info(INFO.DEPLOY_DIRECTORY.format(deployDir));

        // Generate deployment-specific pages
        generate404Page(deployDir);
        generateRobotsTxt(deployDir);
        generateSitemap(deployDir);

        LOGGER.info(INFO.GITHUB_PAGES_READY::format);
    }

    /**
     * Generates a 404 error page.
     */
    private void generate404Page(Path deployDir) throws IOException {
        LOGGER.debug(DEBUG.GENERATING_ADDITIONAL_PAGES::format);
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