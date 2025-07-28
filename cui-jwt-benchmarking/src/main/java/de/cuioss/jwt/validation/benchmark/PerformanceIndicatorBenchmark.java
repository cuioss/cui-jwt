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
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;
import org.openjdk.jmh.annotations.*;

import java.util.Optional;
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

    static {
        // Register benchmarks for metrics collection
        BenchmarkMetricsAggregator.registerBenchmarks(
            "measureAverageTime", 
            "measureThroughput", 
            "measureConcurrentValidation"
        );
    }

    @Setup(Level.Trial)
    public void setup() {
        TokenRepository tokenRepository;
        // Initialize token repository with default configuration
        tokenRepository = new TokenRepository();

        // Create pre-configured token validator
        tokenValidator = tokenRepository.createTokenValidator();

        // Initialize validation delegate
        validationDelegate = new CoreValidationDelegate(tokenValidator, tokenRepository);
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
     * Measures average validation time for single-threaded token validation.
     * <p>
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
     * <p>
     * This benchmark uses 8 threads to measure how many token validations
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