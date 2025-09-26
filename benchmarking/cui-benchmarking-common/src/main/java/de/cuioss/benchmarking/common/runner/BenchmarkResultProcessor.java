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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.converter.JmhBenchmarkConverter;
import de.cuioss.benchmarking.common.model.BenchmarkData;
import de.cuioss.benchmarking.common.output.OutputDirectoryStructure;
import de.cuioss.benchmarking.common.report.GitHubPagesGenerator;
import de.cuioss.benchmarking.common.report.ReportGenerator;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();


    private final BenchmarkType benchmarkType;
    private final String throughputBenchmarkName;
    private final String latencyBenchmarkName;

    /**
     * Creates a processor for the specified benchmark type.
     *
     * @param benchmarkType the type of benchmarks being processed
     * @param throughputBenchmarkName the benchmark name to extract throughput from
     * @param latencyBenchmarkName the benchmark name to extract latency from
     */
    public BenchmarkResultProcessor(BenchmarkType benchmarkType,
            String throughputBenchmarkName,
            String latencyBenchmarkName) {
        this.benchmarkType = benchmarkType;
        this.throughputBenchmarkName = throughputBenchmarkName;
        this.latencyBenchmarkName = latencyBenchmarkName;
    }

    /**
     * Processes benchmark results to generate all artifacts.
     *
     * @param results the JMH benchmark results (still needed for some generators)
     * @param outputDir the output directory for generated artifacts
     * @throws IOException if file operations fail
     */
    public void processResults(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info(INFO.PROCESSING_RESULTS.format(results.size()));
        LOGGER.info(INFO.BENCHMARK_TYPE_DETECTED.format(benchmarkType));

        // Create OutputDirectoryStructure for organized file generation
        Path benchmarkResultsPath = Path.of(outputDir);
        OutputDirectoryStructure structure = new OutputDirectoryStructure(benchmarkResultsPath);
        structure.ensureDirectories();

        // Determine JSON result file path based on benchmark type
        String jsonFileName = benchmarkType == BenchmarkType.MICRO ?
                "micro-result.json" : "integration-result.json";
        Path jsonFile = benchmarkResultsPath.resolve(jsonFileName);

        // Target location in gh-pages-ready/data directory with unified name
        Path targetJsonFile = structure.getDataDir().resolve("original-jmh-result.json");

        // FAIL FAST: JSON file must exist (created by runner in production)
        // For testing, tests must provide proper JSON files
        if (!Files.exists(jsonFile)) {
            throw new IllegalStateException("Benchmark JSON file not found: " + jsonFile +
                    ". The benchmark runner should have created this file.");
        }

        // Convert JMH JSON to BenchmarkData using converter
        JmhBenchmarkConverter converter = new JmhBenchmarkConverter(benchmarkType);
        BenchmarkData benchmarkData;
        try {
            benchmarkData = converter.convert(jsonFile);
        } catch (IOException e) {
            throw new IOException("Failed to convert JMH results to BenchmarkData", e);
        }

        // Copy JMH result to gh-pages-ready/data directory with unified name
        Files.copy(jsonFile, targetJsonFile, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info(INFO.JMH_RESULT_COPIED.format(targetJsonFile));

        // Generate reports directly to gh-pages-ready structure
        generateReportsToDeploymentDir(benchmarkData, structure);

        // Generate deployment-specific assets (404.html, robots.txt, sitemap.xml)
        generateGitHubPagesAssets(structure);

        LOGGER.info(INFO.ARTIFACTS_GENERATED::format);
    }


    /**
     * Generates HTML reports directly to the deployment directory.
     */
    private void generateReportsToDeploymentDir(BenchmarkData benchmarkData, OutputDirectoryStructure structure) throws IOException {
        LOGGER.info(INFO.GENERATING_REPORTS::format);

        // Generate HTML reports to gh-pages-ready/ using standard API
        // This will generate files in the deployment directory only
        ReportGenerator reportGen = new ReportGenerator();
        String deploymentPath = structure.getDeploymentDir().toString();
        reportGen.generateIndexPage(benchmarkData, benchmarkType, deploymentPath);
        reportGen.generateTrendsPage(deploymentPath);
        reportGen.generateDetailedPage(deploymentPath);
        reportGen.copySupportFiles(deploymentPath);
    }


    /**
     * Generates GitHub Pages deployment-specific assets.
     */
    private void generateGitHubPagesAssets(OutputDirectoryStructure structure) throws IOException {
        GitHubPagesGenerator ghGen = new GitHubPagesGenerator();

        LOGGER.info(INFO.GENERATING_GITHUB_PAGES::format);
        ghGen.generateDeploymentAssets(structure);
    }

}