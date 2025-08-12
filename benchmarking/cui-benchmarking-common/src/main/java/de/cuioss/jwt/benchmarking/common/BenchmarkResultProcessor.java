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
package de.cuioss.jwt.benchmarking.common;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Processes JMH benchmark results and generates all required artifacts.
 * <p>
 * This processor takes JMH results and generates:
 * <ul>
 *   <li>Performance and trend badges for shields.io</li>
 *   <li>HTML reports with embedded CSS and JavaScript</li>
 *   <li>Structured JSON metrics for API consumption</li>
 *   <li>GitHub Pages ready deployment structure</li>
 *   <li>Summary files for CI integration</li>
 * </ul>
 * </p>
 * 
 * @since 1.0
 */
public final class BenchmarkResultProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkResultProcessor.class);

    /**
     * Processes benchmark results and generates all artifacts.
     *
     * @param results   the JMH benchmark results
     * @param outputDir the output directory for all generated artifacts
     * @throws IOException if file operations fail
     */
    public void processResults(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Processing %d benchmark results...", results.size());

        // Create output directories
        createDirectories(outputDir);

        // Detect benchmark type
        var benchmarkType = detectBenchmarkType(results);
        LOGGER.info("Detected benchmark type: %s", benchmarkType);

        // Generate all badges
        var badgeGenerator = new BadgeGenerator();
        badgeGenerator.generatePerformanceBadge(results, benchmarkType, outputDir + "/badges");
        badgeGenerator.generateTrendBadge(results, benchmarkType, outputDir + "/badges");
        badgeGenerator.generateLastRunBadge(outputDir + "/badges");

        // Generate performance metrics
        var metricsGenerator = new MetricsGenerator();
        metricsGenerator.generateMetricsJson(results, outputDir + "/data");

        // Generate HTML reports with embedded data
        var reportGenerator = new ReportGenerator();
        reportGenerator.generateIndexPage(results, outputDir);
        reportGenerator.generateTrendsPage(results, outputDir);

        // Generate GitHub Pages structure
        var ghPagesGenerator = new GitHubPagesGenerator();
        ghPagesGenerator.prepareDeploymentStructure(outputDir, outputDir + "/gh-pages-ready");

        // Write summary for CI
        writeSummaryFile(results, benchmarkType, outputDir + "/benchmark-summary.json");

        LOGGER.info("All artifacts generated successfully in: %s", outputDir);
    }

    private void createDirectories(String outputDir) throws IOException {
        var paths = new String[]{
            outputDir + "/badges",
            outputDir + "/data",
            outputDir + "/reports",
            outputDir + "/gh-pages-ready"
        };

        for (var pathStr : paths) {
            Path path = Paths.get(pathStr);
            Files.createDirectories(path);
            LOGGER.debug("Created directory: %s", path);
        }
    }

    private BenchmarkType detectBenchmarkType(Collection<RunResult> results) {
        if (results.isEmpty()) {
            return BenchmarkType.MICRO;
        }

        // Smart detection based on package structure, not hardcoded class names
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

    private void writeSummaryFile(Collection<RunResult> results, BenchmarkType type, String filePath) throws IOException {
        var summary = new BenchmarkSummary(results, type);
        var json = summary.toJson();
        Files.writeString(Paths.get(filePath), json);
        LOGGER.info("Written benchmark summary to: %s", filePath);
    }
}