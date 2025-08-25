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

    private static final CuiLogger LOGGER =
            new CuiLogger(BenchmarkRunner.class);

    /**
     * Main method to run benchmarks with complete artifact generation.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {
        // Use BenchmarkConfiguration for all configuration
        BenchmarkConfiguration config = BenchmarkConfiguration.fromSystemProperties()
                .withResultFile(null) // Let it use the default
                .build();

        String outputDir = config.resultsDirectory();

        LOGGER.info(INFO.BENCHMARK_RUNNER_STARTING.format() + " - Output: " + outputDir);

        // Ensure output directory exists
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        // Configure JMH options using BenchmarkConfiguration
        Options options = config.toBuilder()
                .withResultFile(outputDir + "/raw-result.json")
                .build()
                .toJmhOptions();

        // Run the benchmarks
        Collection<RunResult> results = new Runner(options).run();

        if (results.isEmpty()) {
            throw new IllegalStateException("No benchmark results produced");
        }

        // Process results to generate all artifacts
        // This is a generic runner - concrete implementations should specify their benchmark type
        BenchmarkType type = BenchmarkType.valueOf(
                System.getProperty("benchmark.type", "MICRO").toUpperCase()
        );
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor(type);
        processor.processResults(results, outputDir);

        LOGGER.info(INFO.BENCHMARKS_COMPLETED.format(results.size()) + ", artifacts in " + outputDir);
    }
}