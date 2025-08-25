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

import de.cuioss.benchmarking.common.config.BenchmarkConfiguration;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;

/**
 * JFR-enabled benchmark runner for JWT validation performance analysis with variance metrics.
 * <p>
 * This runner specifically executes the JFR-instrumented benchmarks that capture:
 * <ul>
 *   <li>Individual operation timing with metadata</li>
 *   <li>Periodic statistics including variance</li>
 *   <li>Concurrent operation tracking</li>
 * </ul>
 * <p>
 * To run: {@code mvn verify -Pbenchmark-jfr}
 * <p>
 * JFR data is automatically saved to: {@code target/benchmark-results/}
 *
 * @see de.cuioss.jwt.validation.benchmark.jfr.benchmarks.CoreJfrBenchmark
 * @see de.cuioss.jwt.validation.benchmark.jfr.benchmarks.ErrorJfrBenchmark
 * @see de.cuioss.jwt.validation.benchmark.jfr.benchmarks.MixedJfrBenchmark
 * @see de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer
 */
public class JfrBenchmarkRunner {

    private static final CuiLogger log = new CuiLogger(JfrBenchmarkRunner.class);

    /**
     * Main method to run JFR-instrumented benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {

        // Initialize key cache before benchmarks start
        log.info("Initializing benchmark key cache...");
        BenchmarkKeyCache.initialize();
        log.info("Key cache initialized. Starting JFR benchmarks...\n");

        // Configure JMH options using BenchmarkConfiguration
        String resultsDir = getBenchmarkResultsDir();

        BenchmarkConfiguration config = BenchmarkConfiguration.fromSystemProperties()
                .withIncludePattern("de\\.cuioss\\.jwt\\.validation\\.benchmark\\.jfr\\.benchmarks\\..*")
                .withForks(1)
                .withWarmupIterations(5)
                .withMeasurementIterations(5)
                .withMeasurementTime(TimeValue.seconds(5))
                .withWarmupTime(TimeValue.seconds(3))
                .withThreads(16)
                .withResultFile(getJfrResultFile())
                .withResultsDirectory(resultsDir)
                .build();

        // Build options with JFR-specific JVM arguments
        Options options = new OptionsBuilder()
                .include(config.includePattern())
                .resultFormat(config.resultFormat())
                .result(config.resultFile())
                .forks(config.forks())
                .warmupIterations(config.warmupIterations())
                .measurementIterations(config.measurementIterations())
                .measurementTime(config.measurementTime())
                .warmupTime(config.warmupTime())
                .threads(config.threads())
                .jvmArgs("-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+DebugNonSafepoints",
                        "-XX:StartFlightRecording=filename=" + resultsDir + "/jfr-benchmark.jfr,settings=profile",
                        "-Djava.util.logging.config.file=src/main/resources/benchmark-logging.properties",
                        "-Dbenchmark.results.dir=" + resultsDir)
                .build();

        // Run the benchmarks
        log.info("Running JFR-instrumented benchmarks...");
        log.info("JFR recording will be saved to: " + getBenchmarkResultsDir() + "/jfr-benchmark.jfr");

        new Runner(options).run();

        log.info("Benchmark completed. To analyze variance:");
        log.info("java -cp \"target/classes:target/dependency/*\" de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer " + getBenchmarkResultsDir() + "/jfr-benchmark.jfr");
    }


    /**
     * Gets the JFR-specific result file from system property or defaults to jfr-benchmark-result.json.
     * This method appends "-jfr" to the file prefix to distinguish from regular benchmark results.
     */
    private static String getJfrResultFile() {
        String filePrefix = System.getProperty("jmh.result.filePrefix");
        if (filePrefix != null && !filePrefix.isEmpty()) {
            String resultFile = filePrefix + "-jfr.json";
            // Ensure parent directory exists
            File file = new File(resultFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            return resultFile;
        }
        return getBenchmarkResultsDir() + "/jfr-benchmark-result.json";
    }

    /**
     * Gets the benchmark results directory from system property or defaults to target/benchmark-jfr-results.
     * 
     * @return the benchmark results directory path
     */
    private static String getBenchmarkResultsDir() {
        return System.getProperty("benchmark.results.dir", "target/benchmark-jfr-results");
    }
}