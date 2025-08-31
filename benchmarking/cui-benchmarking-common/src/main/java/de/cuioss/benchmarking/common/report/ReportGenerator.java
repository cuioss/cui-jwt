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

import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Data.BENCHMARK_DATA_JSON;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Html.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Files.Support.*;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Templates.NOT_FOUND_FORMAT;
import static de.cuioss.benchmarking.common.constants.BenchmarkConstants.Report.Templates.PATH_PREFIX;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates HTML reports by creating a data JSON file and copying templates.
 * <p>
 * This generator now works in conjunction with ReportDataGenerator to:
 * <ol>
 *   <li>Generate a standardized benchmark-data.json file with all report data</li>
 *   <li>Copy HTML templates that load and render the JSON data</li>
 * </ol>
 * <p>
 * Templates use JavaScript to dynamically load and display the JSON data,
 * making the system more maintainable and flexible.
 */
public class ReportGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(ReportGenerator.class);
    private static final String DATA_FILE_NAME = BENCHMARK_DATA_JSON;
    private final ReportDataGenerator dataGenerator;
    private final BenchmarkMetrics metrics;
    private final BadgeGenerator badgeGenerator;

    /**
     * Creates a ReportGenerator with pre-computed metrics.
     * 
     * @param metrics the pre-computed benchmark metrics
     */
    public ReportGenerator(BenchmarkMetrics metrics) {
        this.metrics = metrics;
        this.dataGenerator = new ReportDataGenerator();
        this.badgeGenerator = new BadgeGenerator();
    }

    /**
     * Generates the main index page and benchmark data.
     *
     * @param jsonFile the path to the benchmark JSON results file
     * @param benchmarkType the type of benchmark being processed  
     * @param outputDir the output directory for HTML files
     * @throws IOException if reading JSON or writing files fails
     */
    public void generateIndexPage(Path jsonFile, BenchmarkType benchmarkType, String outputDir) throws IOException {
        // First generate the data file
        dataGenerator.generateDataFile(jsonFile, metrics, benchmarkType, outputDir);

        // Then copy the index template
        LOGGER.info(INFO.GENERATING_INDEX_PAGE.format(0));
        copyTemplate(INDEX, outputDir);

        Path indexFile = Path.of(outputDir).resolve(INDEX);
        LOGGER.info(INFO.INDEX_PAGE_GENERATED.format(indexFile));
    }

    /**
     * Generates the trends page.
     *
     * @param outputDir the output directory for HTML files
     * @throws IOException if writing HTML files fails
     */
    public void generateTrendsPage(String outputDir) throws IOException {
        LOGGER.info(INFO.GENERATING_TRENDS_PAGE::format);

        // Copy the trends template
        copyTemplate(TRENDS, outputDir);

        Path trendsFile = Path.of(outputDir).resolve(TRENDS);
        LOGGER.info(INFO.TRENDS_PAGE_GENERATED.format(trendsFile));
    }

    /**
     * Generates the detailed visualizer page.
     *
     * @param outputDir the output directory for HTML files
     * @throws IOException if writing HTML files fails
     */
    public void generateDetailedPage(String outputDir) throws IOException {
        LOGGER.info(INFO.GENERATING_REPORTS::format);

        // Copy the detailed template
        copyTemplate(DETAILED, outputDir);

        Path detailedFile = Path.of(outputDir).resolve(DETAILED);
        LOGGER.info(INFO.INDEX_PAGE_GENERATED.format(detailedFile));
    }

    /**
     * Copies all necessary support files (CSS, JS, etc.) to the output directory.
     *
     * @param outputDir the output directory
     * @throws IOException if copying files fails
     */
    public void copySupportFiles(String outputDir) throws IOException {
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        // Copy CSS file
        copyTemplate(REPORT_STYLES_CSS, outputDir);

        // Copy the data loader JavaScript
        copyTemplate(DATA_LOADER_JS, outputDir);

        // Copy robots.txt and sitemap if needed
        copyTemplate(ROBOTS_TXT, outputDir);
        copyTemplate(SITEMAP_XML, outputDir);
    }

    /**
     * Copies a template file from resources to the output directory.
     *
     * @param templateName the name of the template file
     * @param outputDir the output directory
     * @throws IOException if copying fails
     */
    private void copyTemplate(String templateName, String outputDir) throws IOException {
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        String resourcePath = PATH_PREFIX + templateName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException(NOT_FOUND_FORMAT.formatted(resourcePath));
            }
            Path targetFile = outputPath.resolve(templateName);
            Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}