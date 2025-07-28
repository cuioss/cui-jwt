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

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

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

    /**
     * Main method to run all benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {
        
        // Initialize key cache before benchmarks start
        System.out.println("Initializing benchmark key cache...");
        BenchmarkKeyCache.initialize();
        System.out.println("Key cache initialized. Starting benchmarks...\n");

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
                .result(BenchmarkOptionsHelper.getResultFile("target/benchmark-results/micro-benchmark-result.json"))
                // Add logging configuration to suppress verbose logs
                .jvmArgs("-Djava.util.logging.config.file=src/main/resources/benchmark-logging.properties")
                .build();

        // Run the benchmarks
        new Runner(options).run();

        // Metrics are now exported by PerformanceIndicatorBenchmark @TearDown
    }
}
