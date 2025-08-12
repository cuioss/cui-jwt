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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Standardized JMH benchmark runner with integrated artifact generation.
 * <p>
 * This runner executes JMH benchmarks and generates all required artifacts
 * during execution including badges, reports, metrics, and GitHub Pages structure.
 * </p>
 * 
 * @since 1.0
 */
public final class BenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkRunner.class);

    private BenchmarkRunner() {
        // Utility class
    }

    /**
     * Main method for running benchmarks with complete artifact generation.
     *
     * @param args command line arguments (not used)
     * @throws Exception if benchmark execution fails
     */
    public static void main(String[] args) throws Exception {
        var outputDir = getOutputDirectory();
        var includePattern = getIncludePattern();
        
        LOGGER.info("Starting JMH benchmarks with artifact generation...");
        LOGGER.info("Output directory: %s", outputDir);
        LOGGER.info("Include pattern: %s", includePattern);

        Options options = new OptionsBuilder()
            .include(includePattern)
            .forks(getForks())
            .warmupIterations(getWarmupIterations())
            .measurementIterations(getMeasurementIterations())
            .warmupTime(TimeValue.seconds(getWarmupTimeSeconds()))
            .measurementTime(TimeValue.seconds(getMeasurementTimeSeconds()))
            .threads(getThreads())
            .timeUnit(TimeUnit.MICROSECONDS)
            .resultFormat(org.openjdk.jmh.runner.options.ResultFormatType.JSON)
            .result(outputDir + "/raw-result.json")
            .jvmArgs(
                "-Djava.util.logging.config.file=src/main/resources/benchmark-logging.properties",
                "-Dbenchmark.output.dir=" + outputDir,
                "-Dbenchmark.generate.badges=true",
                "-Dbenchmark.generate.reports=true",
                "-Dbenchmark.generate.github.pages=true"
            )
            .build();

        Collection<RunResult> results = new Runner(options).run();

        // Process results to generate all artifacts
        var processor = new BenchmarkResultProcessor();
        processor.processResults(results, outputDir);

        LOGGER.info("Benchmark execution and artifact generation completed successfully");
    }

    private static String getOutputDirectory() {
        return System.getProperty("benchmark.output.dir", "target/benchmark-results");
    }

    private static String getIncludePattern() {
        return System.getProperty("benchmark.include", ".*Benchmark.*");
    }

    private static int getForks() {
        return Integer.parseInt(System.getProperty("benchmark.forks", "1"));
    }

    private static int getWarmupIterations() {
        return Integer.parseInt(System.getProperty("benchmark.warmup.iterations", "3"));
    }

    private static int getMeasurementIterations() {
        return Integer.parseInt(System.getProperty("benchmark.measurement.iterations", "5"));
    }

    private static int getWarmupTimeSeconds() {
        return Integer.parseInt(System.getProperty("benchmark.warmup.time", "2"));
    }

    private static int getMeasurementTimeSeconds() {
        return Integer.parseInt(System.getProperty("benchmark.measurement.time", "2"));
    }

    private static int getThreads() {
        return Integer.parseInt(System.getProperty("benchmark.threads", "4"));
    }
}