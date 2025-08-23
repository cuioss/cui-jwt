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
package de.cuioss.benchmarking.common;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Standardized JMH benchmark runner that integrates with CUI benchmarking infrastructure.
 * <p>
 * This runner generates all artifacts (badges, reports, metrics, GitHub Pages structure)
 * during JMH execution, eliminating the need for post-processing shell scripts.
 * <p>
 * Features:
 * <ul>
 *   <li>Smart benchmark type detection (micro vs integration)</li>
 *   <li>Automatic badge generation with performance scoring</li>
 *   <li>Self-contained HTML reports with embedded CSS</li>
 *   <li>GitHub Pages ready deployment structure</li>
 *   <li>Structured metrics in JSON format</li>
 * </ul>
 */
public class BenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkRunner.class);

    /**
     * Main method to run benchmarks with complete artifact generation.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {
        String outputDir = getOutputDirectory();

        LOGGER.info("Starting CUI benchmark runner...");
        LOGGER.info("Output directory: {}", outputDir);

        // Ensure output directory exists
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        // Configure JMH options with artifact generation
        Options options = new OptionsBuilder()
                .include(BenchmarkOptionsHelper.getInclude())
                .forks(BenchmarkOptionsHelper.getForks())
                .warmupIterations(BenchmarkOptionsHelper.getWarmupIterations())
                .measurementIterations(BenchmarkOptionsHelper.getMeasurementIterations())
                .measurementTime(BenchmarkOptionsHelper.getMeasurementTime())
                .warmupTime(BenchmarkOptionsHelper.getWarmupTime())
                .threads(BenchmarkOptionsHelper.getThreadCount())
                .resultFormat(ResultFormatType.JSON)
                .result(outputDir + "/raw-result.json")
                .jvmArgs("-Dbenchmark.output.dir=" + outputDir,
                        "-Dbenchmark.generate.badges=true",
                        "-Dbenchmark.generate.reports=true",
                        "-Dbenchmark.generate.github.pages=true")
                .build();

        try {
            // Run the benchmarks
            Collection<RunResult> results = new Runner(options).run();

            if (results.isEmpty()) {
                throw new IllegalStateException("No benchmark results produced");
            }

            LOGGER.info("Benchmarks completed successfully with {} results", results.size());

            // Process results to generate all artifacts
            BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
            processor.processResults(results, outputDir);

            LOGGER.info("All artifacts generated successfully");

        } catch (Exception e) {
            LOGGER.error("Benchmark execution failed", e);
            throw e;
        }
    }

    /**
     * Gets the benchmark output directory from system property or defaults to target/benchmark-results.
     * 
     * @return the benchmark output directory path
     */
    private static String getOutputDirectory() {
        return System.getProperty("benchmark.output.dir", "target/benchmark-results");
    }
}