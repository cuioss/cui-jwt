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

import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation.OperationRecorder;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JFR-instrumented benchmark for measuring JWT validation performance with detailed variance analysis.
 * <p>
 * This benchmark extends the standard performance measurements with JFR (Java Flight Recorder) events
 * to capture:
 * <ul>
 *   <li>Individual operation timing and metadata</li>
 *   <li>Periodic statistics including variance metrics</li>
 *   <li>Concurrent operation tracking</li>
 *   <li>Thread-level performance data</li>
 * </ul>
 * <p>
 * The JFR data enables analysis of:
 * <ul>
 *   <li>Operation time variance under concurrent load</li>
 *   <li>P50/P95/P99 latency percentiles over time</li>
 *   <li>Impact of different token sizes and issuers</li>
 *   <li>Cache hit rates and their effect on variance</li>
 * </ul>
 * <p>
 * JFR recording is automatically started when benchmarks run and saved to:
 * {@code target/benchmark-results/benchmark.jfr}
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class JfrInstrumentedBenchmark {

    private TokenValidator tokenValidator;
    private String validAccessToken;
    private JfrInstrumentation jfrInstrumentation;
    private TokenRepository tokenRepository;
    private int tokenIndex = 0;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize JFR instrumentation
        jfrInstrumentation = new JfrInstrumentation();
        
        // Initialize token repository
        tokenRepository = new TokenRepository();
        
        // Create pre-configured token validator
        tokenValidator = tokenRepository.createTokenValidator();
        
        // Set primary validation token
        validAccessToken = tokenRepository.getPrimaryToken();
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Record benchmark phase event at iteration start
        // Note: We can't access BenchmarkParams directly in @Setup, so we use simpler phase tracking
        String benchmarkName = this.getClass().getSimpleName();
        String phase = "measurement"; // JMH handles warmup/measurement internally
        int threads = Thread.activeCount(); // Approximate thread count
        
        jfrInstrumentation.recordPhase(benchmarkName, phase, 0, 0, 1, threads);
    }


    @TearDown(Level.Trial)
    public void tearDown() {
        // Shutdown JFR instrumentation
        if (jfrInstrumentation != null) {
            jfrInstrumentation.shutdown();
        }
    }

    /**
     * Measures average validation time for single-threaded token validation with JFR instrumentation.
     * <p>
     * This benchmark measures the baseline latency for validating a single
     * access token without concurrent load. JFR events capture individual operation timing.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTimeWithJfr() {
        String token = validAccessToken;

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureAverageTimeWithJfr", "validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = tokenValidator.createAccessToken(token);
            recorder.withSuccess(true);
            return result;

        } catch (TokenValidationException e) {
            // This would be recorded in the finally block of try-with-resources
            throw new RuntimeException("Unexpected validation failure during average time measurement", e);
        }
    }

    /**
     * Measures token validation throughput under concurrent load with JFR instrumentation.
     * <p>
     * This benchmark uses multiple threads to measure how many token validations
     * can be performed per second under concurrent load. JFR events track concurrent operations.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughputWithJfr() {
        String token = validAccessToken;

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureThroughputWithJfr", "validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = tokenValidator.createAccessToken(token);
            recorder.withSuccess(true);
            return result;

        } catch (TokenValidationException e) {
            // This would be recorded in the finally block of try-with-resources
            throw new RuntimeException("Unexpected validation failure during throughput measurement", e);
        }
    }

    /**
     * Measures concurrent validation performance with token rotation and JFR instrumentation.
     * <p>
     * This benchmark tests validation performance using a pool of different tokens
     * to simulate real-world scenarios. JFR events capture variance in operation times.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureConcurrentValidationWithJfr() {
        // Rotate through token pool to simulate different tokens
        String token = tokenRepository.getToken(tokenIndex++);

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureConcurrentValidationWithJfr", "validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = tokenValidator.createAccessToken(token);
            recorder.withSuccess(true);
            return result;

        } catch (TokenValidationException e) {
            // This would be recorded in the finally block of try-with-resources
            throw new RuntimeException("Unexpected validation failure during concurrent validation", e);
        }
    }
}