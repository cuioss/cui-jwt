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
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Optional;
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
    private TokenValidator tokenValidator;
    private CoreValidationDelegate coreValidationDelegate;
    private ErrorLoadDelegate errorLoadDelegate0;
    private ErrorLoadDelegate errorLoadDelegate50;
    private JfrInstrumentation jfrInstrumentation;

    @Setup(Level.Trial)
    public void setup() {
        // Register benchmarks for metrics collection (moved from static initializer to avoid contention)
        BenchmarkMetricsAggregator.registerBenchmarks(
                "measureAverageTimeWithJfr", "measureThroughputWithJfr", "measureConcurrentValidationWithJfr",
                "validateValidTokenWithJfr", "validateExpiredTokenWithJfr", "validateInvalidSignatureTokenWithJfr",
                "validateMalformedTokenWithJfr", "validateMixedTokens0WithJfr", "validateMixedTokens50WithJfr"
        );

        // Initialize JFR instrumentation
        jfrInstrumentation = new JfrInstrumentation();

        // Initialize token repository
        tokenRepository = new TokenRepository();

        // Create token validator
        tokenValidator = tokenRepository.createTokenValidator();

        // Initialize delegates
        coreValidationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
        errorLoadDelegate0 = new ErrorLoadDelegate(tokenValidator, tokenRepository, 0);
        errorLoadDelegate50 = new ErrorLoadDelegate(tokenValidator, tokenRepository, 50);
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Record benchmark phase event at iteration start
        String benchmarkName = this.getClass().getSimpleName();
        String phase = "measurement";
        int threads = Thread.activeCount();

        jfrInstrumentation.recordPhase(benchmarkName, phase, 0, 0, 1, threads);
    }

    @TearDown(Level.Iteration)
    public void collectMetrics() {
        // Get the performance monitor from the validator
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // Determine which benchmark is running based on thread name
        String benchmarkName = getCurrentBenchmarkName();

        if (benchmarkName != null) {
            // Collect metrics for each measurement type
            for (MeasurementType type : monitor.getEnabledTypes()) {
                Optional<StripedRingBufferStatistics> metricsOpt = monitor.getValidationMetrics(type);

                if (metricsOpt.isPresent()) {
                    StripedRingBufferStatistics metrics = metricsOpt.get();
                    if (metrics.sampleCount() > 0) {
                        // Use the pre-calculated percentiles from StripedRingBufferStatistics
                        long p50Nanos = metrics.p50().toNanos();
                        long p95Nanos = metrics.p95().toNanos();
                        long p99Nanos = metrics.p99().toNanos();

                        // Aggregate the actual percentile values
                        BenchmarkMetricsAggregator.aggregatePreCalculatedMetrics(
                                benchmarkName,
                                type,
                                metrics.sampleCount(),
                                p50Nanos,
                                p95Nanos,
                                p99Nanos
                        );
                    }
                }
            }
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Shutdown JFR instrumentation
        if (jfrInstrumentation != null) {
            jfrInstrumentation.shutdown();
        }
    }

    /**
     * Determines the current benchmark name from the thread name or stack trace
     */
    private String getCurrentBenchmarkName() {
        // JMH typically includes the benchmark method name in the thread name
        String threadName = Thread.currentThread().getName();

        // Check all registered benchmark names
        String[] benchmarkNames = {
                "measureAverageTimeWithJfr", "measureThroughputWithJfr", "measureConcurrentValidationWithJfr",
                "validateValidTokenWithJfr", "validateExpiredTokenWithJfr", "validateInvalidSignatureTokenWithJfr",
                "validateMalformedTokenWithJfr", "validateMixedTokens0WithJfr", "validateMixedTokens50WithJfr"
        };

        for (String name : benchmarkNames) {
            if (threadName.contains(name)) {
                return name;
            }
        }

        // Fallback: check stack trace
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String methodName = element.getMethodName();
            for (String name : benchmarkNames) {
                if (name.equals(methodName)) {
                    return methodName;
                }
            }
        }

        return null;
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

            AccessTokenContent result = errorLoadDelegate0.validateValid();
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

            Object result = errorLoadDelegate0.validateExpired();
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

            Object result = errorLoadDelegate0.validateMalformed();
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

            Object result = errorLoadDelegate0.validateInvalidSignature();
            recorder.withSuccess(false);
            return result;
        }
    }

    /**
     * Measures validation performance with mixed valid/invalid tokens (0% error rate) with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Object validateMixedTokens0WithJfr(Blackhole blackhole) {
        String token = errorLoadDelegate0.selectToken();
        String errorType = errorLoadDelegate0.getErrorType(token);
        boolean isValid = "valid".equals(errorType);

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateMixedTokens0WithJfr", "mixed-validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(isValid ? tokenRepository.getTokenIssuer(token) : "benchmark-issuer");

            if (!isValid) {
                recorder.withError(errorType);
            }

            Object result = errorLoadDelegate0.validateMixed(blackhole);
            recorder.withSuccess(isValid);
            return result;
        }
    }

    /**
     * Measures validation performance with mixed valid/invalid tokens (50% error rate) with JFR instrumentation.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Object validateMixedTokens50WithJfr(Blackhole blackhole) {
        String token = errorLoadDelegate50.selectToken();
        String errorType = errorLoadDelegate50.getErrorType(token);
        boolean isValid = "valid".equals(errorType);

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateMixedTokens50WithJfr", "mixed-validation")) {
            recorder.withTokenSize(token.length())
                    .withIssuer(isValid ? tokenRepository.getTokenIssuer(token) : "benchmark-issuer");

            if (!isValid) {
                recorder.withError(errorType);
            }

            Object result = errorLoadDelegate50.validateMixed(blackhole);
            recorder.withSuccess(isValid);
            return result;
        }
    }

}