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

import de.cuioss.benchmarking.common.BenchmarkResultProcessor;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
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
     * Main method to run all benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {

        // Initialize key cache before benchmarks start
        LOGGER.info("Initializing benchmark key cache...");
        BenchmarkKeyCache.initialize();
        LOGGER.info("Key cache initialized. Starting benchmarks...");

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
                // Use benchmark mode specified in individual benchmark annotations
                // (removed .mode(Mode.AverageTime) to allow individual benchmarks to specify their own mode)
                // Configure result output - create a combined report for all benchmarks
                .resultFormat(BenchmarkOptionsHelper.getResultFormat())
                .result(BenchmarkOptionsHelper.getResultFile(getBenchmarkResultsDir() + "/micro-benchmark-result.json"))
                // Add logging configuration to suppress verbose logs
                .jvmArgs("-Djava.util.logging.config.file=src/main/resources/benchmark-logging.properties",
                        "-Dbenchmark.results.dir=" + getBenchmarkResultsDir())
                .build();

        // Run the benchmarks
        Collection<RunResult> results = new Runner(options).run();
        
        // Generate artifacts (badges, reports, metrics, GitHub Pages structure)
        try {
            LOGGER.info("Generating benchmark artifacts...");
            BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
            processor.processResults(results, getBenchmarkResultsDir());
            LOGGER.info("Benchmark artifacts generated successfully in: " + getBenchmarkResultsDir());
        } catch (Exception e) {
            LOGGER.error("Failed to generate benchmark artifacts", e);
            // Don't fail the benchmark run if artifact generation fails
        }

        // Metrics are now exported by PerformanceIndicatorBenchmark @TearDown
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
