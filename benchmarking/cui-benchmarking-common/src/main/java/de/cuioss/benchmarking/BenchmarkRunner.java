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
package de.cuioss.benchmarking;

import de.cuioss.benchmarking.processor.BenchmarkResultProcessor;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Main benchmark runner that integrates JMH execution with comprehensive artifact generation.
 * <p>
 * This runner executes benchmarks and generates all required artifacts during the run:
 * <ul>
 *     <li>Performance badges (Shields.io format)</li>
 *     <li>Trend badges with performance history</li>
 *     <li>HTML reports with embedded data</li>
 *     <li>GitHub Pages deployment structure</li>
 *     <li>Performance metrics in JSON format</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class BenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkRunner.class);

    /**
     * Main method to run benchmarks with comprehensive artifact generation.
     *
     * @param args command line arguments (not used)
     * @throws Exception if benchmark execution fails
     */
    public static void main(String[] args) throws Exception {
        String outputDir = BenchmarkOptionsHelper.getOutputDirectory();
        String includePattern = BenchmarkOptionsHelper.getIncludePattern();
        
        LOGGER.info("Starting benchmark execution with artifact generation");
        LOGGER.info("Output directory: %s", outputDir);
        LOGGER.info("Include pattern: %s", includePattern);

        // Configure JMH options with result processing
        Options options = new OptionsBuilder()
                .include(includePattern)
                .forks(BenchmarkOptionsHelper.getForks())
                .warmupIterations(BenchmarkOptionsHelper.getWarmupIterations())
                .measurementIterations(BenchmarkOptionsHelper.getMeasurementIterations())
                .warmupTime(BenchmarkOptionsHelper.getWarmupTime())
                .measurementTime(BenchmarkOptionsHelper.getMeasurementTime())
                .threads(BenchmarkOptionsHelper.getThreadCount())
                .resultFormat(ResultFormatType.JSON)
                .result(outputDir + "/raw-result.json")
                .build();

        // Execute benchmarks
        LOGGER.info("Executing benchmarks...");
        Collection<RunResult> results = new Runner(options).run();

        if (results.isEmpty()) {
            LOGGER.error("No benchmark results were produced");
            throw new IllegalStateException("Benchmark execution failed: No results produced");
        }

        LOGGER.info("Benchmarks completed successfully: %d benchmarks executed", results.size());

        // Process results to generate all artifacts
        LOGGER.info("Processing results and generating artifacts...");
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        processor.processResults(results, outputDir);

        LOGGER.info("Benchmark execution and artifact generation completed successfully");
    }
}