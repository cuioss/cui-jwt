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
import de.cuioss.jwt.validation.benchmark.delegates.CoreValidationDelegate;
import de.cuioss.jwt.validation.benchmark.delegates.ErrorLoadDelegate;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation.OperationRecorder;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Unified JFR-instrumented benchmark that includes all benchmark scenarios.
 * This class uses delegates to avoid code duplication and provides comprehensive
 * JFR instrumentation for all benchmark types.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class UnifiedJfrBenchmark {

    private TokenRepository tokenRepository;
    private CoreValidationDelegate coreValidationDelegate;
    private ErrorLoadDelegate errorLoadDelegate;
    private JfrInstrumentation jfrInstrumentation;
    
    private int errorPercentage = 0; // Default to 0% errors to match standard benchmarks

    @Setup(Level.Trial)
    public void setup() {
        // Initialize JFR instrumentation
        jfrInstrumentation = new JfrInstrumentation();
        
        // Initialize token repository
        tokenRepository = new TokenRepository();
        
        // Create token validator
        TokenValidator tokenValidator = tokenRepository.createTokenValidator();
        
        // Initialize delegates
        coreValidationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
        errorLoadDelegate = new ErrorLoadDelegate(tokenValidator, tokenRepository, errorPercentage);
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Record benchmark phase event at iteration start
        String benchmarkName = this.getClass().getSimpleName();
        String phase = "measurement";
        int threads = Thread.activeCount();
        
        jfrInstrumentation.recordPhase(benchmarkName, phase, 0, 0, 1, threads);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Shutdown JFR instrumentation
        if (jfrInstrumentation != null) {
            jfrInstrumentation.shutdown();
        }
    }

    // ========== Core Validation Benchmarks ==========

    /**
     * Measures average validation time for single-threaded token validation with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTimeWithJfr() {
        String token = tokenRepository.getPrimaryToken();

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureAverageTimeWithJfr", "validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = coreValidationDelegate.validatePrimaryToken();
            recorder.withSuccess(true);
            return result;
        }
    }

    /**
     * Measures token validation throughput under concurrent load with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughputWithJfr() {
        String token = tokenRepository.getPrimaryToken();

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureThroughputWithJfr", "validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = coreValidationDelegate.validatePrimaryToken();
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

    // ========== Error Load Benchmarks ==========

    /**
     * Measures validation performance for valid tokens with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent validateValidTokenWithJfr() {
        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateValidTokenWithJfr", "validation")) {
            String token = tokenRepository.getPrimaryToken();
            recorder.withTokenSize(token.length())
                    .withIssuer(tokenRepository.getTokenIssuer(token));

            AccessTokenContent result = errorLoadDelegate.validateValid();
            recorder.withSuccess(true);
            return result;
        }
    }

    /**
     * Measures validation performance for expired tokens with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Object validateExpiredTokenWithJfr() {
        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateExpiredTokenWithJfr", "error-validation")) {
            recorder.withTokenSize(200) // Approximate size
                    .withIssuer("benchmark-issuer")
                    .withError("expired");

            Object result = errorLoadDelegate.validateExpired();
            recorder.withSuccess(false);
            return result;
        }
    }

    /**
     * Measures validation performance for malformed tokens with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Object validateMalformedTokenWithJfr() {
        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateMalformedTokenWithJfr", "error-validation")) {
            recorder.withTokenSize(25) // Length of malformed token
                    .withIssuer("unknown")
                    .withError("malformed");

            Object result = errorLoadDelegate.validateMalformed();
            recorder.withSuccess(false);
            return result;
        }
    }

    /**
     * Measures validation performance for tokens with invalid signatures with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Object validateInvalidSignatureTokenWithJfr() {
        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateInvalidSignatureTokenWithJfr", "error-validation")) {
            recorder.withTokenSize(200) // Approximate size
                    .withIssuer("benchmark-issuer")
                    .withError("invalid_signature");

            Object result = errorLoadDelegate.validateInvalidSignature();
            recorder.withSuccess(false);
            return result;
        }
    }

    /**
     * Measures validation performance with mixed valid/invalid tokens with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Object validateMixedTokensWithJfr(Blackhole blackhole) {
        String token = errorLoadDelegate.selectToken();
        String errorType = errorLoadDelegate.getErrorType(token);
        boolean isValid = "valid".equals(errorType);

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateMixedTokensWithJfr", "mixed-validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(isValid ? tokenRepository.getTokenIssuer(token) : "benchmark-issuer");
            
            if (!isValid) {
                recorder.withError(errorType);
            }

            Object result = errorLoadDelegate.validateMixed(blackhole);
            recorder.withSuccess(isValid);
            return result;
        }
    }
}