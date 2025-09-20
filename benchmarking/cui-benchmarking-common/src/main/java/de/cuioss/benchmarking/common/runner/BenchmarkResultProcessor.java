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
import com.google.gson.JsonArray;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.report.BenchmarkMetrics;
import de.cuioss.benchmarking.common.report.GitHubPagesGenerator;
import de.cuioss.benchmarking.common.report.MetricsComputer;
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

    // Directory name constants
    private static final String BADGES_DIR = "/badges";
    private static final String DATA_DIR = "/data";
    private static final String GH_PAGES_DIR = "/gh-pages-ready";

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

        // Create output directories
        createOutputDirectories(outputDir);

        // Determine JSON result file path based on benchmark type
        String jsonFileName = benchmarkType == BenchmarkType.MICRO ?
                "micro-result.json" : "integration-result.json";
        Path jsonFile = Path.of(outputDir, jsonFileName);

        // Target location in data directory with unified name
        Path dataDir = Path.of(outputDir, DATA_DIR);
        Path targetJsonFile = dataDir.resolve("original-jmh-result.json");

        // FAIL FAST: JSON file must exist (created by runner in production)
        // For testing, tests must provide proper JSON files
        if (!Files.exists(jsonFile)) {
            throw new IllegalStateException("Benchmark JSON file not found: " + jsonFile +
                    ". The benchmark runner should have created this file.");
        }

        // Compute metrics once using the pipeline
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);

        MetricsComputer computer = new MetricsComputer(throughputBenchmarkName, latencyBenchmarkName);
        BenchmarkMetrics metrics = computer.computeMetrics(benchmarks);

        // Copy JMH result to data directory with unified name
        Files.copy(jsonFile, targetJsonFile, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info(INFO.JMH_RESULT_COPIED.format(targetJsonFile));

        // Generate HTML reports using pre-computed metrics
        // Note: Badge generation is now handled by ReportDataGenerator inside generateIndexPage
        generateReports(jsonFile, outputDir, metrics);

        // Generate GitHub Pages structure
        generateGitHubPagesStructure(outputDir);

        LOGGER.info(INFO.ARTIFACTS_GENERATED::format);
    }


    /**
     * Creates all necessary output directories.
     */
    private void createOutputDirectories(String outputDir) throws IOException {
        String[] directories = {
                outputDir + BADGES_DIR,
                outputDir + DATA_DIR,
                outputDir + GH_PAGES_DIR
        };

        for (String dir : directories) {
            Files.createDirectories(Path.of(dir));
        }
    }


    /**
     * Generates HTML reports with embedded CSS.
     */
    private void generateReports(Path jsonFile, String outputDir, BenchmarkMetrics metrics) throws IOException {
        ReportGenerator reportGen = new ReportGenerator(metrics);

        LOGGER.info(INFO.GENERATING_REPORTS::format);
        reportGen.generateIndexPage(jsonFile, benchmarkType, outputDir);
        reportGen.generateTrendsPage(outputDir);
        reportGen.generateDetailedPage(outputDir);
        reportGen.copySupportFiles(outputDir);
    }

    /**
     * Generates GitHub Pages ready deployment structure.
     */
    private void generateGitHubPagesStructure(String outputDir) throws IOException {
        GitHubPagesGenerator ghGen = new GitHubPagesGenerator();

        LOGGER.info(INFO.GENERATING_GITHUB_PAGES::format);
        ghGen.prepareDeploymentStructure(outputDir, outputDir + GH_PAGES_DIR);
    }

}