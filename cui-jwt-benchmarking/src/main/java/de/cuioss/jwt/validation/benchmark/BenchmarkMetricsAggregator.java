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

import de.cuioss.jwt.validation.metrics.MeasurementType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared metrics aggregator for all benchmark classes.
 * This class centralizes the metrics collection logic to avoid code duplication
 * between PerformanceIndicatorBenchmark and UnifiedJfrBenchmark.
 * 
 * <p>Features:
 * <ul>
 *   <li>Thread-safe metrics aggregation across all benchmark threads</li>
 *   <li>Automatic registration of benchmark methods</li>
 *   <li>Shutdown hook for exporting metrics on JVM exit</li>
 *   <li>Simplified percentile tracking (p50, p95, p99)</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public final class BenchmarkMetricsAggregator {
    
    /**
     * Thread-safe metrics aggregator for collecting measurements across all threads
     * Stores comprehensive metrics: sample count, p50, p95, p99
     */
    private static final Map<String, Map<MeasurementType, ConcurrentHashMap<String, AtomicLong>>> GLOBAL_METRICS = new ConcurrentHashMap<>();
    
    /**
     * Atomic flag to ensure shutdown hook is only registered once
     */
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    
    /**
     * Cache the output directory when the shutdown hook is registered to ensure 
     * we use the correct system property value from the main thread context
     */
    private static volatile String cachedOutputDir = null;
    
    /**
     * Private constructor to prevent instantiation
     */
    private BenchmarkMetricsAggregator() {
        // Utility class
    }
    
    /**
     * Register benchmark methods for metrics collection.
     * This method is thread-safe and can be called multiple times.
     * Uses lock-free initialization to avoid thread contention.
     * 
     * @param benchmarkNames Array of benchmark method names to track
     */
    public static void registerBenchmarks(String... benchmarkNames) {
        for (String benchmarkName : benchmarkNames) {
            // Use computeIfAbsent for thread-safe, lock-free initialization
            GLOBAL_METRICS.computeIfAbsent(benchmarkName, k -> {
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

                return benchmarkMetrics;
            });
        }
        
        // Register shutdown hook only once using compareAndSet
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            // Capture the output directory from the main thread context before creating the shutdown hook
            // This ensures the shutdown hook uses the correct directory even if system properties
            // are not properly inherited in the shutdown thread context
            cachedOutputDir = System.getProperty("benchmark.results.dir", "target/benchmark-results");
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    exportGlobalMetrics();
                } catch (Exception e) {
                    // Silent failure - metrics export is optional
                }
            }));
        }
    }
    
    /**
     * Aggregate metrics for later export.
     * Thread-safe method to accumulate metrics from benchmark executions.
     * 
     * @param benchmarkName Name of the benchmark method
     * @param type Type of measurement (e.g., TOKEN_PARSING, SIGNATURE_VALIDATION)
     * @param durationNanos Duration of the operation in nanoseconds
     * @deprecated Use {@link #aggregatePreCalculatedMetrics} instead for accurate percentiles
     */
    @Deprecated
    public static void aggregateMetrics(String benchmarkName, MeasurementType type, long durationNanos) {
        Map<MeasurementType, ConcurrentHashMap<String, AtomicLong>> benchmarkMetrics = GLOBAL_METRICS.get(benchmarkName);
        if (benchmarkMetrics != null) {
            ConcurrentHashMap<String, AtomicLong> typeMetrics = benchmarkMetrics.get(type);
            if (typeMetrics != null) {
                // Increment sample count
                typeMetrics.get("sampleCount").incrementAndGet();
                
                // For simplified percentiles, we'll use the duration directly
                // In a real implementation, you would use HdrHistogram or similar
                // This is a simplification that assumes all values are equal to the average
                typeMetrics.get("p50_sum").addAndGet(durationNanos);
                typeMetrics.get("p95_sum").addAndGet(durationNanos);
                typeMetrics.get("p99_sum").addAndGet(durationNanos);
            }
        }
    }
    
    /**
     * Aggregate pre-calculated percentile metrics from TokenValidatorMonitor.
     * This method uses actual percentile values instead of trying to reconstruct them.
     * 
     * @param benchmarkName Name of the benchmark method
     * @param type Type of measurement (e.g., TOKEN_PARSING, SIGNATURE_VALIDATION)
     * @param sampleCount Number of samples in this aggregation
     * @param p50Nanos 50th percentile (median) in nanoseconds
     * @param p95Nanos 95th percentile in nanoseconds
     * @param p99Nanos 99th percentile in nanoseconds
     */
    public static void aggregatePreCalculatedMetrics(String benchmarkName, MeasurementType type, 
                                                     long sampleCount, long p50Nanos, long p95Nanos, long p99Nanos) {
        Map<MeasurementType, ConcurrentHashMap<String, AtomicLong>> benchmarkMetrics = GLOBAL_METRICS.get(benchmarkName);
        if (benchmarkMetrics != null) {
            ConcurrentHashMap<String, AtomicLong> typeMetrics = benchmarkMetrics.get(type);
            if (typeMetrics != null) {
                // Add sample count
                typeMetrics.get("sampleCount").addAndGet(sampleCount);
                
                // Accumulate weighted percentiles
                typeMetrics.get("p50_sum").addAndGet(p50Nanos * sampleCount);
                typeMetrics.get("p95_sum").addAndGet(p95Nanos * sampleCount);
                typeMetrics.get("p99_sum").addAndGet(p99Nanos * sampleCount);
            }
        }
    }
    
    /**
     * Export aggregated metrics to jwt-validation-metrics.json.
     * This method is called automatically via shutdown hook.
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

                        // Calculate averages from accumulated sums (convert nanos to microseconds)
                        double p50Us = (typeMetrics.get("p50_sum").get() / (double) sampleCount) / 1_000.0;
                        double p95Us = (typeMetrics.get("p95_sum").get() / (double) sampleCount) / 1_000.0;
                        double p99Us = (typeMetrics.get("p99_sum").get() / (double) sampleCount) / 1_000.0;

                        // Round appropriately: integers for >= 1, one decimal for < 1
                        stepMetrics.put("p50_us", roundMicroseconds(p50Us));
                        stepMetrics.put("p95_us", roundMicroseconds(p95Us));
                        stepMetrics.put("p99_us", roundMicroseconds(p99Us));

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
     * Force export of metrics. Useful for testing or manual triggers.
     * This method is idempotent and thread-safe.
     */
    public static void forceExport() {
        exportGlobalMetrics();
    }
    
    /**
     * Clear all metrics. Useful for testing.
     * WARNING: This will clear metrics from all benchmarks!
     * Thread-safe implementation without synchronization.
     */
    public static void clearMetrics() {
        // Iterate through all metrics and reset atomically
        GLOBAL_METRICS.values().parallelStream().forEach(benchmarkMetrics -> {
            benchmarkMetrics.values().forEach(typeMetrics -> {
                typeMetrics.values().forEach(counter -> counter.set(0));
            });
        });
    }
    
    /**
     * Get the cached output directory path. Used by BenchmarkMetricsCollector 
     * to ensure consistent directory usage between main thread and shutdown hook.
     * 
     * @return cached output directory path, or null if not cached
     */
    static String getCachedOutputDir() {
        return cachedOutputDir;
    }
    
    /**
     * Round microseconds appropriately:
     * - Values >= 1: round to integer
     * - Values < 1: round to 1 decimal place
     * 
     * @param microseconds value in microseconds
     * @return appropriately rounded value
     */
    private static double roundMicroseconds(double microseconds) {
        if (microseconds >= 1.0) {
            // Round to integer for values >= 1
            return Math.round(microseconds);
        } else {
            // Round to 1 decimal place for values < 1
            return Math.round(microseconds * 10) / 10.0;
        }
    }
}