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

import de.cuioss.benchmarking.common.config.BenchmarkConfiguration;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Abstract base class for JMH benchmark runners that integrates with CUI benchmarking infrastructure.
 * <p>
 * This runner generates all artifacts (badges, reports, metrics, GitHub Pages structure)
 * during JMH execution, eliminating the need for post-processing shell scripts.
 * <p>
 * Concrete implementations should override {@link #getBenchmarkType()}, {@link #getIncludePattern()},
 * and {@link #beforeBenchmarks()} / {@link #afterBenchmarks(Collection)} for specific initialization
 * and cleanup logic.
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
public abstract class AbstractBenchmarkRunner {

    private static final CuiLogger LOGGER =
            new CuiLogger(AbstractBenchmarkRunner.class);

    /**
     * Gets the benchmark type for this runner.
     * 
     * @return the benchmark type (MICRO, INTEGRATION, etc.)
     */
    protected abstract BenchmarkType getBenchmarkType();

    /**
     * Gets the include pattern for benchmark classes.
     * 
     * @return the regex pattern for including benchmark classes
     */
    protected abstract String getIncludePattern();

    /**
     * Gets the result file name for this benchmark type.
     * 
     * @return the result file name
     */
    protected abstract String getResultFileName();

    /**
     * Hook for initialization before running benchmarks.
     * Override to add specific initialization logic.
     * 
     * @throws Exception if initialization fails
     */
    protected void beforeBenchmarks() throws Exception {
        // Default implementation does nothing
    }

    /**
     * Hook for cleanup or additional processing after benchmarks.
     * Override to add specific post-processing logic.
     * 
     * @param results the benchmark results
     * @param config the benchmark configuration
     * @throws Exception if post-processing fails
     */
    protected void afterBenchmarks(Collection<RunResult> results, BenchmarkConfiguration config) throws Exception {
        // Default implementation does nothing
    }

    /**
     * Configures the benchmark options.
     * Override to customize benchmark configuration.
     * 
     * @param builder the configuration builder
     * @return the modified configuration builder
     */
    protected BenchmarkConfiguration.Builder configureBenchmark(BenchmarkConfiguration.Builder builder) {
        // Default implementation returns the builder as-is
        return builder;
    }

    /**
     * Gets the benchmark results directory from system property or defaults.
     * 
     * @return the benchmark results directory path
     */
    protected String getBenchmarkResultsDir() {
        return System.getProperty("benchmark.results.dir", "target/benchmark-results");
    }

    /**
     * Main execution method that orchestrates the benchmark run.
     *
     * @throws Exception if an error occurs during benchmark execution
     */
    public void run() throws Exception {
        String outputDir = getBenchmarkResultsDir();

        LOGGER.info(INFO.BENCHMARK_RUNNER_STARTING.format() + " - Type: " + getBenchmarkType() + ", Output: " + outputDir);

        // Ensure output directory exists
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        // Run initialization hook
        beforeBenchmarks();

        // Configure benchmark using template method pattern
        BenchmarkConfiguration.Builder configBuilder = BenchmarkConfiguration.fromSystemProperties()
                .withIncludePattern(getIncludePattern())
                .withResultsDirectory(outputDir)
                .withResultFile(outputDir + "/" + getResultFileName());

        // Allow subclasses to customize configuration
        configBuilder = configureBenchmark(configBuilder);
        BenchmarkConfiguration config = configBuilder.build();

        Options options = config.toJmhOptions();

        // Run the benchmarks
        Collection<RunResult> results = new Runner(options).run();

        if (results.isEmpty()) {
            throw new IllegalStateException("No benchmark results produced");
        }

        // Process results to generate all artifacts
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(getBenchmarkType());
        processor.processResults(results, outputDir);

        // Run post-processing hook
        afterBenchmarks(results, config);

        LOGGER.info(INFO.BENCHMARKS_COMPLETED.format(results.size()) + ", artifacts in " + outputDir);
    }
}