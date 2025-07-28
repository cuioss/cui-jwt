# Benchmark Performance Fix Summary

## Changes Made

### 1. Removed Synchronization from BenchmarkMetricsAggregator
- **File**: `BenchmarkMetricsAggregator.java`
- **Changes**:
  - Removed `synchronized` keyword from `registerBenchmarks()` method
  - Replaced with `ConcurrentHashMap.computeIfAbsent()` for lock-free initialization
  - Changed shutdown hook registration to use `AtomicBoolean.compareAndSet()`
  - Made `clearMetrics()` use parallel streams instead of synchronized

### 2. Reduced Thread Count
- **File**: `pom.xml`
- **Change**: Reduced `jmh.threads` from 200 to 100
- **Reason**: Excessive threads cause contention and degrade P99 performance

## Code Changes

### Before (Problematic):
```java
public static synchronized void registerBenchmarks(String... benchmarkNames) {
    // All 200 threads wait here, causing 4.3 second delays
}
```

### After (Fixed):
```java
public static void registerBenchmarks(String... benchmarkNames) {
    for (String benchmarkName : benchmarkNames) {
        // Lock-free initialization
        GLOBAL_METRICS.computeIfAbsent(benchmarkName, k -> {
            // Initialize metrics
            return benchmarkMetrics;
        });
    }
}
```

## Expected Results

| Metric | Before Fix | Expected After | Improvement |
|--------|------------|----------------|-------------|
| P50 Latency | 116-171μs | 116-171μs | No change |
| P99 Latency | 138-174ms | <5ms | ~97% reduction |
| Lock Wait Time | 4.3 seconds | 0 seconds | Eliminated |
| Thread Efficiency | ~7% | ~15-20% | 2-3x better |

## How to Test

```bash
# Run standard benchmarks
./mvnw clean verify -pl cui-jwt-benchmarking

# Run with custom thread count
./mvnw clean verify -pl cui-jwt-benchmarking -Djmh.threads=50

# Run JFR benchmarks
./mvnw clean verify -pl cui-jwt-benchmarking -Pbenchmark-jfr
```

## What Was the Problem?

1. **Static initializers** in benchmark classes called `synchronized registerBenchmarks()`
2. **All 200 threads** had to wait for this lock during JMH initialization
3. This created a **cascading effect** where JWT operations that normally take 116μs would spike to 40ms
4. The **library code was fine** - it was purely a benchmark design issue

## Verification

After running the benchmarks, check:
1. P99 latency should be <5ms (not 138-174ms)
2. No more 4.3 second lock waits in JFR recordings
3. Better thread efficiency (ops/s per thread)
4. More consistent performance across runs