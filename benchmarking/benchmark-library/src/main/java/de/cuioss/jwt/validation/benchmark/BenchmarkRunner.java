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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.benchmarking.processor.BenchmarkResultProcessor;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Main class for running optimized benchmarks.
 * <p>
 * This class collects and runs all benchmark classes in the package.
 * It configures JMH with optimized settings for fast execution (&lt;10 minutes)
 * and produces a combined JSON report.
 * <p>
 * Optimized benchmark classes:
 * <ul>
 *   <li><strong>SimpleCoreValidationBenchmark</strong>: Essential validation performance metrics</li>
 *   <li><strong>SimpleErrorLoadBenchmark</strong>: Streamlined error handling scenarios</li>
 * </ul>
 */
public class BenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkRunner.class);

    /**
     * Main method to run all benchmarks with comprehensive artifact generation.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {

        // Initialize key cache before benchmarks start
        LOGGER.info("Initializing benchmark key cache...");
        BenchmarkKeyCache.initialize();
        LOGGER.info("Key cache initialized. Starting benchmarks...");

        String outputDir = getBenchmarkResultsDir();
        LOGGER.info("Output directory: %s", outputDir);

        // Configure JMH options
        Options options = new OptionsBuilder()
                // Include only standard benchmark classes
                .include("de\\.cuioss\\.jwt\\.validation\\.benchmark\\.standard\\..*")
                // Exclude JFR benchmarks
                .exclude(".*Jfr.*")
                // Set number of forks
                .forks(BenchmarkOptionsHelper.getForks(1))
                // Set warmup iterations
                .warmupIterations(BenchmarkOptionsHelper.getWarmupIterations(5))
                // Set measurement iterations
                .measurementIterations(BenchmarkOptionsHelper.getMeasurementIterations(5))
                // Set measurement time
                .measurementTime(BenchmarkOptionsHelper.getMeasurementTime("2s"))
                // Set warmup time
                .warmupTime(BenchmarkOptionsHelper.getWarmupTime("2s"))
                // Set number of threads
                .threads(BenchmarkOptionsHelper.getThreadCount(8))
                // Configure result output
                .resultFormat(ResultFormatType.JSON)
                .result(outputDir + "/raw-result.json")
                // Add logging configuration to suppress verbose logs
                .jvmArgs("-Djava.util.logging.config.file=src/main/resources/benchmark-logging.properties",
                        "-Dbenchmark.output.dir=" + outputDir,
                        "-Dbenchmark.generate.badges=true",
                        "-Dbenchmark.generate.reports=true",
                        "-Dbenchmark.generate.github.pages=true")
                .build();

        // Run the benchmarks
        LOGGER.info("Executing benchmarks...");
        Collection<RunResult> results = new Runner(options).run();

        if (results.isEmpty()) {
            LOGGER.error("No benchmark results were produced");
            throw new IllegalStateException("Benchmark execution failed: No results produced");
        }

        LOGGER.info("Benchmarks completed successfully: %d benchmarks executed", results.size());

        // Process results using new infrastructure
        LOGGER.info("Processing results and generating artifacts...");
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        processor.processResults(results, outputDir);

        LOGGER.info("Benchmark execution and artifact generation completed successfully");
    }

    /**
     * Gets the benchmark results directory from system property or defaults to target/benchmark-results.
     * 
     * @return the benchmark results directory path
     */
    private static String getBenchmarkResultsDir() {
        return System.getProperty("benchmark.results.dir", "target/benchmark-results");
    }
}
