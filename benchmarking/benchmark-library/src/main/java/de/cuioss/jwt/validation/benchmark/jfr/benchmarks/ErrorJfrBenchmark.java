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
import de.cuioss.jwt.validation.benchmark.delegates.ErrorLoadDelegate;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation.OperationRecorder;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Error handling JFR-instrumented benchmarks for JWT validation.
 * Split from UnifiedJfrBenchmark to eliminate JMH threading contention.
 * Contains only error scenario benchmarks (4 methods maximum).
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Thread)
@SuppressWarnings("java:S112")
public class ErrorJfrBenchmark extends AbstractJfrBenchmark {

    private static final String[] BENCHMARK_NAMES = {
            "validateValidTokenWithJfr", "validateExpiredTokenWithJfr",
            "validateInvalidSignatureTokenWithJfr", "validateMalformedTokenWithJfr"
    };
    
    private static final String ERROR_VALIDATION_OPERATION = "error-validation";

    private ErrorLoadDelegate errorLoadDelegate;

    @Override
    protected String[] getBenchmarkMethodNames() {
        return BENCHMARK_NAMES;
    }

    @Override
    protected String getJfrPhase() {
        return "error-measurement";
    }

    @Setup(Level.Trial)
    public void setup() {
        // Use base class setup with our benchmark names
        setupJfrBase(BENCHMARK_NAMES);

        // Initialize delegates (using 0% error rate for error scenario testing)
        errorLoadDelegate = new ErrorLoadDelegate(tokenValidator, tokenRepository, 0);
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
        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateExpiredTokenWithJfr", ERROR_VALIDATION_OPERATION)) {
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
        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateMalformedTokenWithJfr", ERROR_VALIDATION_OPERATION)) {
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
        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("validateInvalidSignatureTokenWithJfr", ERROR_VALIDATION_OPERATION)) {
            recorder.withTokenSize(200) // Approximate size
                    .withIssuer("benchmark-issuer")
                    .withError("invalid_signature");

            Object result = errorLoadDelegate.validateInvalidSignature();
            recorder.withSuccess(false);
            return result;
        }
    }
}