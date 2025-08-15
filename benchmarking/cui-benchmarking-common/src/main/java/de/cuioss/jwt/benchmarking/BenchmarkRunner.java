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
package de.cuioss.jwt.benchmarking;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

/**
 * Standardized JMH benchmark runner with integrated artifact generation.
 * <p>
 * This runner executes JMH benchmarks and generates all artifacts (badges, reports, 
 * metrics, GitHub Pages structure) during the benchmark execution itself, eliminating 
 * the need for post-processing shell scripts.
 * <p>
 * Key features:
 * <ul>
 *   <li>Badge generation during benchmark execution</li>
 *   <li>HTML report generation with embedded CSS</li>
 *   <li>GitHub Pages deployment structure</li>
 *   <li>Performance metrics export</li>
 *   <li>Smart benchmark type detection</li>
 * </ul>
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 */
public class BenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkRunner.class);

    /**
     * Runs benchmarks with comprehensive artifact generation.
     * 
     * @param includePattern regex pattern for benchmark classes to include
     * @param outputDir directory for generated artifacts
     * @param options additional JMH options
     * @throws Exception if benchmark execution fails
     */
    public static void runWithArtifactGeneration(String includePattern, String outputDir, Options options) throws Exception {
        LOGGER.info("Starting benchmark execution with artifact generation");
        LOGGER.info("Include pattern: %s", includePattern);
        LOGGER.info("Output directory: %s", outputDir);

        // Build final options with artifact generation
        Options finalOptions = new OptionsBuilder()
                .parent(options)
                .include(includePattern)
                .resultFormat(ResultFormatType.JSON)
                .result(outputDir + "/raw-result.json")
                .build();

        // Execute benchmarks
        Collection<RunResult> results = new Runner(finalOptions).run();

        if (results.isEmpty()) {
            throw new IllegalStateException("No benchmark results were produced");
        }

        LOGGER.info("Benchmark execution completed with %d results", results.size());

        // Process results and generate all artifacts
        BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
        processor.processResults(results, outputDir);

        LOGGER.info("Artifact generation completed successfully");
    }

    /**
     * Main method for standalone execution.
     * 
     * @param args command line arguments (not used)
     * @throws Exception if execution fails
     */
    public static void main(String[] args) throws Exception {
        String includePattern = System.getProperty("jmh.include", ".*");
        String outputDir = System.getProperty("benchmark.output.dir", "target/benchmark-results");
        
        Options options = new OptionsBuilder()
                .forks(1)
                .warmupIterations(2)
                .measurementIterations(3)
                .threads(1)
                .build();

        runWithArtifactGeneration(includePattern, outputDir, options);
    }
}