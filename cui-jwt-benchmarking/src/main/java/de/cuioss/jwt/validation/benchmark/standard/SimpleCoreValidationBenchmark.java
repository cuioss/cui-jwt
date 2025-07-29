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
package de.cuioss.jwt.validation.benchmark.standard;

import de.cuioss.jwt.validation.benchmark.base.AbstractBenchmark;
import de.cuioss.jwt.validation.benchmark.delegates.CoreValidationDelegate;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
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
@State(Scope.Thread)
@SuppressWarnings("java:S112")
public class SimpleCoreValidationBenchmark extends AbstractBenchmark {

    private static final String[] BENCHMARK_NAMES = {
            "measureAverageTime", "measureThroughput", "measureConcurrentValidation"
    };

    private CoreValidationDelegate validationDelegate;

    @Override
    protected String[] getBenchmarkMethodNames() {
        return BENCHMARK_NAMES;
    }

    @Setup(Level.Trial)
    public void setup() {
        // Use base class setup with our benchmark names
        setupBase(BENCHMARK_NAMES);

        // Initialize validation delegate
        validationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
    }

    /**
     * Measures average validation time for single-threaded token validation.
     * This benchmark measures the baseline latency for validating a single
     * access token without concurrent load. Lower values indicate better performance.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTime() {
        return validationDelegate.validatePrimaryToken();
    }

    /**
     * Measures token validation throughput under concurrent load.
     * This benchmark uses multiple threads to measure how many token validations
     * can be performed per second under concurrent load. Higher values indicate better throughput.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughput() {
        return validationDelegate.validatePrimaryToken();
    }

    /**
     * Measures concurrent validation performance with token rotation.
     * This benchmark tests validation performance using a pool of different tokens
     * to simulate real-world scenarios where multiple different tokens are validated
     * concurrently. This provides insight into caching behavior and token diversity impact.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureConcurrentValidation() {
        return validationDelegate.validateWithRotation();
    }
}