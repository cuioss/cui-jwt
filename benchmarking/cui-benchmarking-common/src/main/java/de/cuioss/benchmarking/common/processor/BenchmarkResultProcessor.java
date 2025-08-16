package de.cuioss.benchmarking.common.processor;

import de.cuioss.benchmarking.common.badge.BadgeGenerator;
import de.cuioss.benchmarking.common.detector.BenchmarkType;
import de.cuioss.benchmarking.common.detector.BenchmarkTypeDetector;
import de.cuioss.benchmarking.common.ghpages.GitHubPagesGenerator;
import de.cuioss.benchmarking.common.metrics.MetricsGenerator;
import de.cuioss.benchmarking.common.report.ReportGenerator;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Processes a collection of JMH {@link RunResult}s to generate various artifacts
 * like badges, reports, and data files.
 */
public class BenchmarkResultProcessor {

    /**
     * Processes the given benchmark results and generates all artifacts.
     *
     * @param results   the collection of benchmark run results
     * @param outputDir the root directory where artifacts will be generated
     */
    public void processResults(Collection<RunResult> results, String outputDir) {
        BenchmarkType type = BenchmarkTypeDetector.detect(results);

        // Generate all badges
        BadgeGenerator badgeGen = new BadgeGenerator();
        badgeGen.generatePerformanceBadge(results, type, outputDir + "/badges");
        badgeGen.generateTrendBadge(results, type, outputDir + "/badges");
        badgeGen.generateLastRunBadge(outputDir + "/badges");

        // Generate performance metrics
        MetricsGenerator metricsGen = new MetricsGenerator();
        metricsGen.generateMetricsJson(results, outputDir + "/data");

        // Generate HTML reports with embedded data
        ReportGenerator reportGen = new ReportGenerator();
        reportGen.generateIndexPage(results, outputDir);
        reportGen.generateTrendsPage(results, outputDir);

        // Generate GitHub Pages structure
        GitHubPagesGenerator ghGen = new GitHubPagesGenerator();
        ghGen.prepareDeploymentStructure(outputDir, outputDir + "/gh-pages-ready");

        // Write summary for CI
        writeSummaryFile(results, type, outputDir + "/benchmark-summary.json");
    }

    private void writeSummaryFile(Collection<RunResult> results, BenchmarkType type, String filePath) {
        // Simple placeholder implementation
        try (Writer writer = Files.newBufferedWriter(Paths.get(filePath))) {
            writer.write("{\n");
            writer.write("  \"benchmark_type\": \"" + type + "\",\n");
            writer.write("  \"results_count\": " + results.size() + "\n");
            writer.write("}\n");
        } catch (IOException e) {
            // In a real implementation, we would use a logger
            e.printStackTrace();
        }
    }
}
