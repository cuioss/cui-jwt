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
package de.cuioss.benchmarking.processor;

import de.cuioss.benchmarking.BenchmarkOptionsHelper;
import de.cuioss.benchmarking.badge.BadgeGenerator;
import de.cuioss.benchmarking.model.BenchmarkType;
import de.cuioss.benchmarking.pages.GitHubPagesGenerator;
import de.cuioss.benchmarking.report.MetricsGenerator;
import de.cuioss.benchmarking.report.ReportGenerator;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;

/**
 * Processes JMH benchmark results to generate comprehensive artifacts including badges,
 * reports, metrics, and GitHub Pages deployment structure.
 * <p>
 * This processor handles both micro-benchmarks and integration benchmarks,
 * automatically detecting the type based on benchmark class packages.
 *
 * @since 1.0.0
 */
public class BenchmarkResultProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkResultProcessor.class);

    /**
     * Processes benchmark results and generates all configured artifacts.
     *
     * @param results the JMH benchmark results
     * @param outputDir the output directory for artifacts
     * @throws IOException if artifact generation fails
     */
    public void processResults(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Processing %d benchmark results", results.size());

        // Create output directory structure
        createOutputDirectories(outputDir);

        // Detect benchmark type
        BenchmarkType type = detectBenchmarkType(results);
        LOGGER.info("Detected benchmark type: %s", type);

        // Generate badges if enabled
        if (BenchmarkOptionsHelper.shouldGenerateBadges()) {
            LOGGER.info("Generating performance badges...");
            BadgeGenerator badgeGenerator = new BadgeGenerator();
            badgeGenerator.generatePerformanceBadge(results, type, outputDir + "/badges");
            badgeGenerator.generateTrendBadge(results, type, outputDir + "/badges");
            badgeGenerator.generateLastRunBadge(outputDir + "/badges");
        }

        // Generate performance metrics
        LOGGER.info("Generating performance metrics...");
        MetricsGenerator metricsGenerator = new MetricsGenerator();
        metricsGenerator.generateMetricsJson(results, outputDir + "/data");

        // Generate HTML reports if enabled
        if (BenchmarkOptionsHelper.shouldGenerateReports()) {
            LOGGER.info("Generating HTML reports...");
            ReportGenerator reportGenerator = new ReportGenerator();
            reportGenerator.generateIndexPage(results, outputDir);
            reportGenerator.generateTrendsPage(results, outputDir);
        }

        // Generate GitHub Pages structure if enabled
        if (BenchmarkOptionsHelper.shouldGenerateGitHubPages()) {
            LOGGER.info("Generating GitHub Pages deployment structure...");
            GitHubPagesGenerator ghPagesGenerator = new GitHubPagesGenerator();
            ghPagesGenerator.prepareDeploymentStructure(outputDir, outputDir + "/gh-pages-ready");
        }

        // Write benchmark summary
        writeSummaryFile(results, type, outputDir);

        LOGGER.info("Result processing completed successfully");
    }

    /**
     * Detects the benchmark type based on benchmark class packages.
     * <p>
     * Uses smart detection based on package structure rather than hardcoded class names:
     * <ul>
     *     <li>Integration benchmarks contain ".integration." or ".quarkus." in package</li>
     *     <li>All others are considered micro benchmarks</li>
     * </ul>
     *
     * @param results the benchmark results
     * @return the detected benchmark type
     */
    private BenchmarkType detectBenchmarkType(Collection<RunResult> results) {
        return results.stream()
                .map(result -> result.getParams().getBenchmark())
                .findFirst()
                .map(benchmark -> {
                    if (benchmark.contains(".integration.") || benchmark.contains(".quarkus.")) {
                        return BenchmarkType.INTEGRATION;
                    }
                    return BenchmarkType.MICRO;
                })
                .orElse(BenchmarkType.MICRO);
    }

    /**
     * Creates the required output directory structure.
     *
     * @param outputDir the base output directory
     * @throws IOException if directory creation fails
     */
    private void createOutputDirectories(String outputDir) throws IOException {
        Path baseDir = Paths.get(outputDir);
        Files.createDirectories(baseDir);
        Files.createDirectories(baseDir.resolve("badges"));
        Files.createDirectories(baseDir.resolve("data"));
        Files.createDirectories(baseDir.resolve("reports"));
        Files.createDirectories(baseDir.resolve("gh-pages-ready"));
    }

    /**
     * Writes a summary file with benchmark execution information.
     *
     * @param results the benchmark results
     * @param type the benchmark type
     * @param outputDir the output directory
     * @throws IOException if file writing fails
     */
    private void writeSummaryFile(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        Path summaryFile = Paths.get(outputDir, "benchmark-summary.json");

        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("timestamp", Instant.now().toString());
        summary.put("benchmarkType", type.toString().toLowerCase());
        summary.put("benchmarkCount", results.size());

        java.util.Map<String, Object> artifacts = new java.util.LinkedHashMap<>();
        artifacts.put("badges", BenchmarkOptionsHelper.shouldGenerateBadges());
        artifacts.put("reports", BenchmarkOptionsHelper.shouldGenerateReports());
        artifacts.put("githubPages", BenchmarkOptionsHelper.shouldGenerateGitHubPages());
        summary.put("artifactsGenerated", artifacts);

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        java.nio.file.Files.writeString(summaryFile, gson.toJson(summary), java.nio.charset.StandardCharsets.UTF_8);
        LOGGER.info("Written benchmark summary to: %s", summaryFile);
    }
}