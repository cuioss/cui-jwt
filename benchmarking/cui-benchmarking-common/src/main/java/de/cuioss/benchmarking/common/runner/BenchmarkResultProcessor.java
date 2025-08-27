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
package de.cuioss.benchmarking.common.runner;

import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.report.*;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Processes JMH benchmark results to generate all required artifacts during execution.
 * <p>
 * This processor handles the complete pipeline from raw JMH results to deployment-ready
 * artifacts including badges, reports, metrics, and GitHub Pages structure.
 * <p>
 * Generated artifacts:
 * <ul>
 *   <li>Performance badges (shields.io compatible JSON)</li>
 *   <li>Trend analysis badges</li>
 *   <li>Self-contained HTML reports</li>
 *   <li>Structured metrics in JSON format</li>
 *   <li>GitHub Pages deployment structure</li>
 * </ul>
 */
public class BenchmarkResultProcessor {

    private static final CuiLogger LOGGER =
            new CuiLogger(BenchmarkResultProcessor.class);

    // Directory name constants
    private static final String BADGES_DIR = "/badges";
    private static final String DATA_DIR = "/data";
    private static final String REPORTS_DIR = "/reports";
    private static final String GH_PAGES_DIR = "/gh-pages-ready";
    private static final String SUMMARY_FILE = "/benchmark-summary.json";

    private final BenchmarkType benchmarkType;

    /**
     * Creates a processor for the specified benchmark type.
     *
     * @param benchmarkType the type of benchmarks being processed
     */
    public BenchmarkResultProcessor(BenchmarkType benchmarkType) {
        this.benchmarkType = benchmarkType;
    }

    /**
     * Processes benchmark results to generate all artifacts.
     *
     * @param results the JMH benchmark results
     * @param outputDir the output directory for generated artifacts
     * @throws IOException if file operations fail
     */
    public void processResults(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info(INFO.PROCESSING_RESULTS.format(results.size()));
        LOGGER.info(INFO.BENCHMARK_TYPE_DETECTED.format(benchmarkType));

        // Create output directories
        createOutputDirectories(outputDir);

        // Generate all badges
        generateBadges(results, benchmarkType, outputDir);

        // Generate performance metrics
        generateMetrics(results, outputDir);

        // Generate HTML reports
        generateReports(results, outputDir);

        // Generate GitHub Pages structure
        generateGitHubPagesStructure(outputDir);

        // Write summary file for CI
        writeSummaryFile(results, benchmarkType, outputDir);

        LOGGER.info(INFO.ARTIFACTS_GENERATED::format);
    }


    /**
     * Creates all necessary output directories.
     */
    private void createOutputDirectories(String outputDir) throws IOException {
        String[] directories = {
                outputDir + BADGES_DIR,
                outputDir + DATA_DIR,
                outputDir + REPORTS_DIR,
                outputDir + GH_PAGES_DIR
        };

        for (String dir : directories) {
            Files.createDirectories(Path.of(dir));
        }
    }

    /**
     * Generates all types of badges.
     */
    private void generateBadges(Collection<RunResult> results, BenchmarkType type, String outputDir)
            throws IOException {
        BadgeGenerator badgeGen = new BadgeGenerator();

        LOGGER.info(INFO.GENERATING_BADGES.format(results.size()));
        badgeGen.generatePerformanceBadge(results, type, outputDir + BADGES_DIR);
        badgeGen.generateTrendBadge(results, type, outputDir + BADGES_DIR);
        badgeGen.generateLastRunBadge(outputDir + BADGES_DIR);
    }

    /**
     * Generates performance metrics in JSON format.
     */
    private void generateMetrics(Collection<RunResult> results, String outputDir) throws IOException {
        MetricsGenerator metricsGen = new MetricsGenerator();

        LOGGER.info(INFO.GENERATING_METRICS::format);
        metricsGen.generateMetricsJson(results, outputDir + DATA_DIR);
    }

    /**
     * Generates HTML reports with embedded CSS.
     */
    private void generateReports(Collection<RunResult> results, String outputDir) throws IOException {
        ReportGenerator reportGen = new ReportGenerator();

        LOGGER.info(INFO.GENERATING_REPORTS::format);
        reportGen.generateIndexPage(results, outputDir);
        reportGen.generateTrendsPage(results, outputDir);
        reportGen.generateDetailedPage(results, benchmarkType.getDisplayName(), outputDir);
    }

    /**
     * Generates GitHub Pages ready deployment structure.
     */
    private void generateGitHubPagesStructure(String outputDir) throws IOException {
        GitHubPagesGenerator ghGen = new GitHubPagesGenerator();

        LOGGER.info(INFO.GENERATING_GITHUB_PAGES::format);
        ghGen.prepareDeploymentStructure(outputDir, outputDir + GH_PAGES_DIR);
    }

    /**
     * Writes a summary file for CI consumption.
     */
    private void writeSummaryFile(Collection<RunResult> results, BenchmarkType type, String outputDir)
            throws IOException {
        SummaryGenerator summaryGen = new SummaryGenerator();

        LOGGER.info(INFO.WRITING_SUMMARY::format);
        summaryGen.writeSummary(results, type, Instant.now(), outputDir + SUMMARY_FILE);
    }
}