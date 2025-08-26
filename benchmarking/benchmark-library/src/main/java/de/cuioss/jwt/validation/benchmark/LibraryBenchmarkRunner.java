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
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.runner.AbstractBenchmarkRunner;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Main class for running JWT validation library micro benchmarks.
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
public class LibraryBenchmarkRunner extends AbstractBenchmarkRunner {

    private static final CuiLogger LOGGER = new CuiLogger(LibraryBenchmarkRunner.class);

    @Override protected BenchmarkType getBenchmarkType() {
        return BenchmarkType.MICRO;
    }

    @Override protected String getIncludePattern() {
        return "de\\.cuioss\\.jwt\\.validation\\.benchmark\\.standard\\..*";
    }

    @Override protected String getResultFileName() {
        return "micro-benchmark-result.json";
    }

    @Override protected void beforeBenchmarks() throws Exception {
        // Initialize key cache before benchmarks start
        BenchmarkKeyCache.initialize();
        LOGGER.info("JWT validation micro benchmarks starting - Key cache initialized");
    }

    @Override protected BenchmarkConfiguration.Builder configureBenchmark(BenchmarkConfiguration.Builder builder) {
        return builder
                .withForks(1)
                .withWarmupIterations(5)
                .withMeasurementIterations(5)
                .withMeasurementTime(TimeValue.seconds(2))
                .withWarmupTime(TimeValue.seconds(2))
                .withThreads(8);
    }

    /**
     * Main method to run all benchmarks.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during benchmark execution
     */
    public static void main(String[] args) throws Exception {
        new LibraryBenchmarkRunner().run();
    }
}