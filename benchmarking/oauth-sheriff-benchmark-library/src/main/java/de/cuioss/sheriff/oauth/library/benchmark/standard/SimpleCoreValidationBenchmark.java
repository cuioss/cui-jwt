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
package de.cuioss.sheriff.oauth.library.benchmark.standard;

import de.cuioss.sheriff.oauth.library.benchmark.base.AbstractBenchmark;
import de.cuioss.sheriff.oauth.library.benchmark.delegates.CoreValidationDelegate;
import de.cuioss.sheriff.oauth.library.domain.token.AccessTokenContent;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Simplified core validation benchmark - split from PerformanceIndicatorBenchmark.
 * Contains only essential JWT validation performance metrics (3 methods maximum).
 * Designed to eliminate JMH threading contention by keeping class complexity minimal.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Thread) @SuppressWarnings("java:S112") public class SimpleCoreValidationBenchmark extends AbstractBenchmark {

    private CoreValidationDelegate validationDelegate;


    @Setup(Level.Trial) public void setup() {
        // Use base class setup with our benchmark names
        setupBase();

        // Initialize validation delegate
        validationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
    }

    /**
     * Measures average validation time for single-threaded token validation using full token spectrum.
     * This benchmark measures the baseline latency for validating tokens while rotating
     * through all 600 tokens to test cache effectiveness. Lower values indicate better performance.
     *
     * @return validated access token content
     */
    @Benchmark @BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.MICROSECONDS) public AccessTokenContent measureAverageTime() {
        return validationDelegate.validateWithFullSpectrum();
    }

    /**
     * Measures token validation throughput under concurrent load using full token spectrum.
     * This benchmark uses multiple threads to measure how many token validations
     * can be performed per second under concurrent load, rotating through all 600 tokens
     * to test cache effectiveness. Higher values indicate better throughput.
     *
     * @return validated access token content
     */
    @Benchmark @BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS) public AccessTokenContent measureThroughput() {
        return validationDelegate.validateWithFullSpectrum();
    }

    /**
     * Measures concurrent validation performance with token rotation.
     * This benchmark tests validation performance using a pool of different tokens
     * to simulate real-world scenarios where multiple different tokens are validated
     * concurrently. This provides insight into caching behavior and token diversity impact.
     *
     * @return validated access token content
     */
    @Benchmark @BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.MICROSECONDS) public AccessTokenContent measureConcurrentValidation() {
        return validationDelegate.validateWithRotation();
    }
}