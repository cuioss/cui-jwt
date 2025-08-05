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
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Consolidated core validation benchmark that measures essential JWT validation performance metrics.
 * <p>
 * This benchmark combines the most critical performance measurements:
 * <ul>
 *   <li><strong>Average Time</strong>: Single-threaded validation latency</li>
 *   <li><strong>Throughput</strong>: Operations per second under concurrent load</li>
 *   <li><strong>Concurrent Validation</strong>: Multi-threaded validation performance</li>
 * </ul>
 * <p>
 * Performance expectations:
 * <ul>
 *   <li>Access token validation: &lt; 100 μs per operation</li>
 *   <li>Concurrent throughput: Linear scalability up to 8 threads</li>
 *   <li>Throughput: &gt; 10,000 operations/second</li>
 * </ul>
 * <p>
 * This benchmark is optimized for fast execution while retaining essential performance insights.
 * It replaces the functionality of TokenValidatorBenchmark, ConcurrentTokenValidationBenchmark,
 * and PerformanceIndicatorBenchmark with streamlined implementations.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class PerformanceIndicatorBenchmark {

    private TokenValidator tokenValidator;
    private CoreValidationDelegate validationDelegate;

    @Setup(Level.Trial)
    public void setup() {
        TokenRepository tokenRepository;
        // Initialize token repository with cache size configured for 10% of tokens
        TokenRepository.Config config = TokenRepository.Config.builder()
                .cacheSize(60) // 10% of default 600 tokens
                .build();
        tokenRepository = new TokenRepository(config);

        // Create pre-configured token validator with cache configuration
        tokenValidator = tokenRepository.createTokenValidator(
                TokenValidatorMonitorConfig.builder()
                        .measurementTypes(TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES)
                        .windowSize(10000)
                        .build(),
                config);

        // Initialize validation delegate
        validationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
    }


    @TearDown(Level.Trial)
    public void exportMetrics() {
        // Export metrics using SimplifiedMetricsExporter
        if (tokenValidator != null && tokenValidator.getPerformanceMonitor() != null) {
            try {
                SimplifiedMetricsExporter.exportMetrics(tokenValidator.getPerformanceMonitor());
            } catch (Exception e) {
                // Ignore errors during metrics export
            }
        }
    }

    /**
     * Determines the current benchmark name from the thread name or stack trace
     */
    private String getCurrentBenchmarkName() {
        // JMH typically includes the benchmark method name in the thread name
        String threadName = Thread.currentThread().getName();

        if (threadName.contains("measureAverageTime")) {
            return "measureAverageTime";
        } else if (threadName.contains("measureThroughput")) {
            return "measureThroughput";
        } else if (threadName.contains("measureConcurrentValidation")) {
            return "measureConcurrentValidation";
        }

        // Fallback: check stack trace
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String methodName = element.getMethodName();
            if ("measureAverageTime".equals(methodName) ||
                    "measureThroughput".equals(methodName) ||
                    "measureConcurrentValidation".equals(methodName)) {
                return methodName;
            }
        }

        return null;
    }


    /**
     * Measures average validation time for single-threaded token validation using full token spectrum.
     * <p>
     * This benchmark measures the baseline latency for validating tokens while rotating
     * through all 600 tokens to test cache effectiveness. Lower values indicate better performance.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTime() {
        return validationDelegate.validateWithFullSpectrum();
    }

    /**
     * Measures token validation throughput under concurrent load using full token spectrum.
     * <p>
     * This benchmark uses 8 threads to measure how many token validations
     * can be performed per second under concurrent load, rotating through all 600 tokens
     * to test cache effectiveness. Higher values indicate better throughput.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughput() {
        return validationDelegate.validateWithFullSpectrum();
    }

    /**
     * Measures concurrent validation performance with token rotation.
     * <p>
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