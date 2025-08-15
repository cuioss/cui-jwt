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
package de.cuioss.jwt.benchmarking;

import de.cuioss.jwt.benchmarking.badges.BadgeGenerator;
import de.cuioss.jwt.benchmarking.metrics.MetricsGenerator;
import de.cuioss.jwt.benchmarking.reports.ReportGenerator;
import de.cuioss.jwt.benchmarking.github.GitHubPagesGenerator;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Processes JMH benchmark results and generates complete artifact pipeline.
 * <p>
 * This processor handles all post-benchmark artifact generation including:
 * <ul>
 *   <li>Performance and trend badges</li>
 *   <li>HTML reports with embedded styling</li>
 *   <li>Structured metrics in JSON format</li>
 *   <li>GitHub Pages deployment structure</li>
 *   <li>Summary files for CI integration</li>
 * </ul>
 * <p>
 * The processor automatically detects benchmark types (micro vs integration) 
 * based on package structure and generates appropriate artifacts for each type.
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 */
public class BenchmarkResultProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkResultProcessor.class);

    private final BadgeGenerator badgeGenerator;
    private final MetricsGenerator metricsGenerator;
    private final ReportGenerator reportGenerator;
    private final GitHubPagesGenerator gitHubPagesGenerator;

    /**
     * Creates a new processor with default generators.
     */
    public BenchmarkResultProcessor() {
        this.badgeGenerator = new BadgeGenerator();
        this.metricsGenerator = new MetricsGenerator();
        this.reportGenerator = new ReportGenerator();
        this.gitHubPagesGenerator = new GitHubPagesGenerator();
    }

    /**
     * Processes benchmark results and generates all artifacts.
     * 
     * @param results JMH benchmark results
     * @param outputDir base output directory for artifacts
     * @throws IOException if artifact generation fails
     */
    public void processResults(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Processing %d benchmark results", results.size());

        // Create output directory structure
        createOutputDirectories(outputDir);

        // Detect benchmark type based on result content
        BenchmarkType type = detectBenchmarkType(results);
        LOGGER.info("Detected benchmark type: %s", type);

        // Generate all badges
        generateBadges(results, type, outputDir);

        // Generate performance metrics
        generateMetrics(results, outputDir);

        // Generate HTML reports with embedded data
        generateReports(results, outputDir);

        // Generate GitHub Pages structure
        generateGitHubPages(outputDir);

        // Write summary for CI
        writeSummaryFile(results, type, outputDir);

        LOGGER.info("Result processing completed successfully");
    }

    private void createOutputDirectories(String outputDir) throws IOException {
        String[] directories = {
            outputDir + "/badges",
            outputDir + "/data",
            outputDir + "/reports",
            outputDir + "/gh-pages-ready"
        };

        for (String dir : directories) {
            Path path = Paths.get(dir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                LOGGER.debug("Created directory: %s", dir);
            }
        }
    }

    private void generateBadges(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        LOGGER.info("Generating badges for benchmark type: %s", type);

        String badgeDir = outputDir + "/badges";

        // Generate performance badge
        badgeGenerator.generatePerformanceBadge(results, type, badgeDir);

        // Generate trend badge
        badgeGenerator.generateTrendBadge(results, type, badgeDir);

        // Generate last run badge
        badgeGenerator.generateLastRunBadge(badgeDir);

        LOGGER.info("Badge generation completed");
    }

    private void generateMetrics(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating performance metrics");

        String dataDir = outputDir + "/data";
        metricsGenerator.generateMetricsJson(results, dataDir);

        LOGGER.info("Metrics generation completed");
    }

    private void generateReports(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating HTML reports");

        // Generate index page
        reportGenerator.generateIndexPage(results, outputDir);

        // Generate trends page
        reportGenerator.generateTrendsPage(results, outputDir);

        LOGGER.info("Report generation completed");
    }

    private void generateGitHubPages(String outputDir) throws IOException {
        LOGGER.info("Generating GitHub Pages structure");

        String ghPagesDir = outputDir + "/gh-pages-ready";
        gitHubPagesGenerator.prepareDeploymentStructure(outputDir, ghPagesDir);

        LOGGER.info("GitHub Pages structure generation completed");
    }

    private BenchmarkType detectBenchmarkType(Collection<RunResult> results) {
        return results.stream()
                .map(r -> r.getParams().getBenchmark())
                .findFirst()
                .map(benchmark -> {
                    if (benchmark.contains(".integration.") || 
                        benchmark.contains(".quarkus.")) {
                        return BenchmarkType.INTEGRATION;
                    }
                    return BenchmarkType.MICRO;
                })
                .orElse(BenchmarkType.MICRO);
    }

    private void writeSummaryFile(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        LOGGER.info("Writing benchmark summary file");

        BenchmarkSummary summary = new BenchmarkSummary(results, type);
        String summaryPath = outputDir + "/benchmark-summary.json";
        
        metricsGenerator.writeSummaryFile(summary, summaryPath);

        LOGGER.info("Summary file written to: %s", summaryPath);
    }
}