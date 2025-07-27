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
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    private String validAccessToken;
    private TokenRepository tokenRepository;
    private int tokenIndex = 0;
    
    /**
     * Thread-safe metrics aggregator for collecting measurements across all threads
     * Now stores comprehensive metrics: sample count, p50, p95, p99
     */
    private static final Map<String, Map<MeasurementType, ConcurrentHashMap<String, AtomicLong>>> GLOBAL_METRICS = new ConcurrentHashMap<>();

    /**
     * Shutdown hook to ensure metrics are exported when JVM exits
     */
    static {
        // Initialize metrics maps for each benchmark
        String[] benchmarkNames = {"measureAverageTime", "measureThroughput", "measureConcurrentValidation"};

        for (String benchmarkName : benchmarkNames) {
            Map<MeasurementType, ConcurrentHashMap<String, AtomicLong>> benchmarkMetrics = new ConcurrentHashMap<>();

            // Initialize metrics for each measurement type
            for (MeasurementType type : MeasurementType.values()) {
                ConcurrentHashMap<String, AtomicLong> typeMetrics = new ConcurrentHashMap<>();
                typeMetrics.put("sampleCount", new AtomicLong(0));
                typeMetrics.put("p50_sum", new AtomicLong(0));
                typeMetrics.put("p95_sum", new AtomicLong(0));
                typeMetrics.put("p99_sum", new AtomicLong(0));
                benchmarkMetrics.put(type, typeMetrics);
            }

            GLOBAL_METRICS.put(benchmarkName, benchmarkMetrics);
        }

        // Add shutdown hook to export metrics when JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                exportGlobalMetrics();
            } catch (Exception e) {
                // Silent failure - metrics export is optional
            }
        }));
    }

    @Setup(Level.Trial)
    public void setup() {
        // Initialize token repository with default configuration
        tokenRepository = new TokenRepository();
        
        // Create pre-configured token validator
        tokenValidator = tokenRepository.createTokenValidator();
        
        // Set primary validation token
        validAccessToken = tokenRepository.getPrimaryToken();
    }


    @TearDown(Level.Iteration)
    public void collectMetrics() {
        // Get the performance monitor from the validator
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        if (monitor != null) {
            // Determine which benchmark is running based on thread name
            String benchmarkName = getCurrentBenchmarkName();

            if (benchmarkName != null) {
                Map<MeasurementType, ConcurrentHashMap<String, AtomicLong>> benchmarkMetrics = GLOBAL_METRICS.get(benchmarkName);

                // Collect metrics for each measurement type
                int metricsCollected = 0;
                for (MeasurementType type : monitor.getEnabledTypes()) {
                    Optional<StripedRingBufferStatistics> metricsOpt = monitor.getValidationMetrics(type);

                    if (metricsOpt.isPresent()) {
                        StripedRingBufferStatistics metrics = metricsOpt.get();
                        if (metrics.sampleCount() > 0) {
                            ConcurrentHashMap<String, AtomicLong> typeMetrics = benchmarkMetrics.get(type);

                            // Accumulate all statistics
                            typeMetrics.get("sampleCount").addAndGet(metrics.sampleCount());
                            typeMetrics.get("p50_sum").addAndGet(metrics.p50().toNanos() * metrics.sampleCount());
                            typeMetrics.get("p95_sum").addAndGet(metrics.p95().toNanos() * metrics.sampleCount());
                            typeMetrics.get("p99_sum").addAndGet(metrics.p99().toNanos() * metrics.sampleCount());

                            metricsCollected++;
                        }
                    }
                }

                // Successfully collected metrics
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
     * Exports collected metrics to a JSON file
     */
    private static void exportGlobalMetrics() {
        try {
            Map<String, Map<String, Object>> aggregatedMetrics = new LinkedHashMap<>();

            for (Map.Entry<String, Map<MeasurementType, ConcurrentHashMap<String, AtomicLong>>> entry : GLOBAL_METRICS.entrySet()) {
                String benchmarkName = entry.getKey();
                Map<MeasurementType, ConcurrentHashMap<String, AtomicLong>> benchmarkMetrics = entry.getValue();

                Map<String, Object> benchmarkResults = new LinkedHashMap<>();

                for (MeasurementType type : MeasurementType.values()) {
                    ConcurrentHashMap<String, AtomicLong> typeMetrics = benchmarkMetrics.get(type);
                    long sampleCount = typeMetrics.get("sampleCount").get();

                    if (sampleCount > 0) {
                        Map<String, Object> stepMetrics = new LinkedHashMap<>();
                        stepMetrics.put("sample_count", sampleCount);

                        // Calculate averages from accumulated sums
                        double p50Ms = (typeMetrics.get("p50_sum").get() / (double) sampleCount) / 1_000_000.0;
                        double p95Ms = (typeMetrics.get("p95_sum").get() / (double) sampleCount) / 1_000_000.0;
                        double p99Ms = (typeMetrics.get("p99_sum").get() / (double) sampleCount) / 1_000_000.0;

                        stepMetrics.put("p50_ms", Math.round(p50Ms * 1000) / 1000.0);
                        stepMetrics.put("p95_ms", Math.round(p95Ms * 1000) / 1000.0);
                        stepMetrics.put("p99_ms", Math.round(p99Ms * 1000) / 1000.0);

                        benchmarkResults.put(type.name().toLowerCase(), stepMetrics);
                    }
                }

                if (!benchmarkResults.isEmpty()) {
                    aggregatedMetrics.put(benchmarkName, benchmarkResults);
                }
            }

            // Export to file
            BenchmarkMetricsCollector.exportAggregatedMetrics(aggregatedMetrics);

        } catch (Exception e) {
            // Silent failure - metrics export is optional
        }
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
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during average time measurement", e);
        }
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
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during throughput measurement", e);
        }
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
        try {
            // Rotate through token pool to simulate different tokens
            String token = tokenRepository.getToken(tokenIndex++);
            return tokenValidator.createAccessToken(token);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during concurrent validation", e);
        }
    }

}