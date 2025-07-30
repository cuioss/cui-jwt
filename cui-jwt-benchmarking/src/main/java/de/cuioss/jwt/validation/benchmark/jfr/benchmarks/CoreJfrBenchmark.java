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
package de.cuioss.jwt.validation.benchmark.jfr.benchmarks;

import de.cuioss.jwt.validation.benchmark.base.AbstractJfrBenchmark;
import de.cuioss.jwt.validation.benchmark.delegates.CoreValidationDelegate;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation.OperationRecorder;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Core JFR-instrumented benchmark for essential JWT validation performance.
 * Split from UnifiedJfrBenchmark to eliminate JMH threading contention.
 * Contains only core validation benchmarks (3 methods maximum).
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Thread)
@SuppressWarnings("java:S112")
public class CoreJfrBenchmark extends AbstractJfrBenchmark {

    private static final String[] BENCHMARK_NAMES = {
            "measureAverageTimeWithJfr", "measureThroughputWithJfr", "measureConcurrentValidationWithJfr"
    };

    private CoreValidationDelegate coreValidationDelegate;

    @Override
    protected String[] getBenchmarkMethodNames() {
        return BENCHMARK_NAMES;
    }

    @Setup(Level.Trial)
    public void setup() {
        // Use base class setup with our benchmark names
        setupJfrBase(BENCHMARK_NAMES);

        // Initialize delegates
        coreValidationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
    }

    // ========== Core Validation Benchmarks ==========

    /**
     * Measures average validation time for single-threaded token validation with JFR instrumentation using full token spectrum.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTimeWithJfr() {
        String token = coreValidationDelegate.getCurrentToken("full_spectrum");

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureAverageTimeWithJfr", "validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = coreValidationDelegate.validateWithFullSpectrum();
            recorder.withSuccess(true);
            return result;
        }
    }

    /**
     * Measures token validation throughput under concurrent load with JFR instrumentation using full token spectrum.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughputWithJfr() {
        String token = coreValidationDelegate.getCurrentToken("full_spectrum");

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureThroughputWithJfr", "validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = coreValidationDelegate.validateWithFullSpectrum();
            recorder.withSuccess(true);
            return result;
        }
    }

    /**
     * Measures concurrent validation performance with token rotation and JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureConcurrentValidationWithJfr() {
        String token = coreValidationDelegate.getCurrentToken("rotation");

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureConcurrentValidationWithJfr", "validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = coreValidationDelegate.validateWithRotation();
            recorder.withSuccess(true);
            return result;
        }
    }
}