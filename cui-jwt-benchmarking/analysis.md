# JWT Benchmark Analysis - Baseline Performance (July 30, 2025)

## Executive Summary

This analysis documents the baseline performance metrics for the JWT validation library as of July 30, 2025. These results establish the current performance standards for optimization efforts.

## Test Configuration
- **Token Pool**: 600 tokens (200 per issuer)
- **Cache Size**: 60 entries (10% of token pool)
- **Token Variation**: Custom data claims (50-250 chars)
- **JVM**: OpenJDK 64-Bit Server VM 21.0.7+6-LTS  
- **Threads**: 100 concurrent threads
- **Iterations**: 3 measurement, 1 warmup

## Baseline Performance Metrics

### 📊 Throughput Performance

| Benchmark Type | Throughput (ops/s) | Per-Thread |
|----------------|-------------------|-------------|
| **Standard** | 100,673 | 1,007 ops/s/thread |
| **Error Handling (0%)** | 113,581 | 1,136 ops/s/thread |
| **Error Handling (50%)** | 178,652 | 1,787 ops/s/thread |

### 📈 Response Time Metrics

| Benchmark Type | Latency (μs) | Notes |
|----------------|--------------|-------|
| **Standard Average** | 860.66 | Average response time |
| **Concurrent** | 883.40 | Under concurrent load |

### 🎯 Component-Level Performance

| Operation | P50 (μs) | P95 (μs) | P99 (μs) | P99/P50 Ratio |
|-----------|----------|----------|----------|---------------|
| **Complete Validation** | 52-84 | 82-101 | 112-31,675 | 2.2x-377x |
| **Signature Validation** | 45-62 | 69-71 | 124-10,195 | 2.1x-221x |
| **Token Parsing** | 3.7-6.1 | 6.0-7.8 | 6.5-14.0 | 1.8x-3.4x |
| **Claims Validation** | 0.7-4.0 | 1.2-5.7 | 1.3-7.2 | 1.6x-2.3x |
| **Token Building** | 2.0-7.8 | 3.7-11.0 | 4.6-14.0 | 1.5x-2.3x |
| **Header Validation** | 0.1-0.5 | 0.4-1.1 | 0.5-1.6 | 2.5x-5x |
| **Cache Operations** | 0.0-0.4 | 0.2-0.8 | 0.3-1.1 | 2.8x-∞ |

### 📊 Variance Analysis

The benchmark results show varying P99 latencies across different workloads:
- **Complete Validation**: P99 ranges from 112μs to 31,675μs depending on workload (152μs in average-time mode)
- **Signature Validation**: P99 spikes up to 10,195μs in mixed token scenarios (124μs in average-time mode)
- **Most Stable**: Token parsing and claims validation maintain consistent P99/P50 ratios < 4x
- **Mode Comparison**: Average-time mode shows stable P99 of 152μs vs up to 31,675μs in throughput mode (both using 100 threads)

## Key Performance Characteristics

1. **Throughput Baseline**: 
   - Standard: 100,673 ops/s
   - Error 0%: 113,581 ops/s
   - Error 50%: 178,652 ops/s (best performance)

2. **Latency Profile**: 
   - Average: 860.66μs
   - Concurrent: 883.40μs
   - P50 range: 52-84μs (good median performance)

3. **P99 Variance**: 
   - Complete validation P99: 112μs to 31.7ms (152μs in average-time mode)
   - Signature validation P99: 124μs to 10.2ms (stable 124μs in average-time mode)
   - High P99/P50 ratios in throughput mode vs stable average-time mode (both with 100 threads)

4. **Component Performance**: 
   - Signature validation: Dominant component (45-62μs P50)
   - Token parsing: Consistent performance (3.7-6.1μs P50)
   - Cache operations: Minimal overhead (0.0-0.4μs P50)
   - Average-time mode shows more predictable P99 behavior than throughput mode

## Optimization Opportunities

### High Priority
1. **P99 Latency Spikes**: Address 31.7ms spikes in throughput mode (average-time mode shows stable 152μs with same 100 threads)
2. **Signature Validation**: Reduce 10.2ms+ P99 spikes in mixed scenarios (average-time mode shows stable 124μs) 
3. **P99/P50 Ratios**: Target < 50x for predictable performance (average-time mode already achieves this)

### Medium Priority
1. **Throughput Enhancement**: Target >200k ops/s baseline (current: 100,673 ops/s)
2. **Thread Efficiency**: Improve from 1,007 to >2,000 ops/s/thread
3. **Average Latency**: Reduce from 860μs to <500μs

### Low Priority
1. **Cache Optimization**: Already performing well (0.0-0.4μs P50)
2. **Token Parsing**: Stable performance with minor P99 variance (6.5-14.0μs)
3. **Header Validation**: Small absolute times despite ratios

## ✅ Implemented Optimizations

### Completed Tasks

**Architecture & Performance**:
- ✅ Field-based TokenSignatureValidator with Provider bypass optimization
- ✅ Virtual thread compatibility with ReentrantLock patterns, immutable Map.copyOf()
- ✅ JFR instrumentation with variance analysis, ValidationContext time caching
- ✅ Thread count optimization - 100 threads configuration
- ✅ Benchmark profile separation with distinct output directories

**Library Analysis**:
- ✅ Analyzed jjwt, smallrye-jwt, jose4j, auth0 - all use JCA without Signature caching
- ✅ Component performance breakdown completed

## Optimization Roadmap

### High Priority - P99 Latency Reduction

- [x] **JFR-Based Load Analysis** - **Identify load-related P99 spike patterns** ✅
  - [x] Implement detailed JFR instrumentation for signature validation under load
  - [x] Capture thread contention, GC pressure, and CPU throttling metrics
  - [x] Analyze correlation between concurrent operations and P99 spikes
  - [x] Compare throughput vs average-time mode behavior under identical load

- [ ] **Signature Validation Optimization** - **67ms P99 spikes (1,290x P99/P50)**
  - [ ] Cache key: (token signature, public key) → boolean result

- [ ] **Complete Validation Stabilization** - **31.7ms P99 spikes (377x P99/P50)**
  - [ ] Profile validation hotspots causing extreme spikes
  - [ ] Implement circuit breaker for pathological cases

- [ ] **Token Building Object Pooling** - **14.0μs P99 spikes**
  - [ ] Implement Apache Commons Pool for TokenBuilder instances

- [ ] **Claims Validation Optimization** - **7.2μs P99 spikes**
  - [ ] Profile validation logic for expensive operations
  - [ ] Cache validation results for repeated claim patterns
  - [ ] Optimize date/time claim validation

### Medium Priority - Throughput Enhancement

- [ ] **Throughput Optimization** - **Current: 100k ops/s baseline**
  - [ ] Optimize synchronization points
  - [ ] Reduce allocation rates
  - [ ] Implement zero-copy token handling where possible

- [ ] **Thread Efficiency** - **Current: 1,007 ops/s/thread**
  - [ ] Reduce thread contention
  - [ ] Optimize work distribution
  - [ ] Consider work-stealing patterns

- [ ] **Async Architecture**
  - [ ] Implement CompletableFuture-based validation pipeline
  - [ ] Separate executors for parsing, signature, and claims validation
  - [ ] Non-blocking I/O for issuer configuration resolution

### Low Priority - Production Hardening

- [ ] **JFR Overhead Reduction**
  - [ ] Conditional recording (>100μs threshold)
  - [ ] Batch event recording

- [ ] **Memory & GC Optimization**
  - [ ] Reduce allocation rate
  - [ ] Optimize hot allocation sites
  - [ ] Test with different GC configurations

## Validation Methodology

### Benchmark Commands

```bash
# Standard benchmarks
mvn verify -Pbenchmark

# Component-level analysis
mvn verify -Pbenchmark-jfr

# Thread scaling analysis
mvn verify -Pbenchmark -Djmh.threads=1,50,100,150,200
```

## Conclusion

The JWT validation library baseline performance (July 30, 2025) shows:

**Current Strengths**:
1. **Good median latency**: 52-84μs P50 for complete validation
2. **Error handling efficiency**: 178,652 ops/s with 50% error rate
3. **Stable average-time mode performance**: P99 of 152μs in average-time mode vs 31.7ms in throughput mode
4. **Consistent core components**: Token parsing and claims validation show low variance

**Key Insights**:
1. **Mode-dependent variance**: Throughput mode shows extreme P99 spikes while average-time mode remains stable (both with 100 threads)
2. **Signature validation bottleneck**: 10.2ms P99 spikes in mixed scenarios vs 124μs in average-time mode
3. **Thread efficiency opportunity**: 1,007 ops/s/thread baseline performance

**Optimization Priorities**:
1. **P99 latency reduction**: From 31.7ms to <5ms in throughput scenarios (High Priority)
2. **Throughput enhancement**: From 100,673 to 200k ops/s (Medium Priority)
3. **Thread efficiency**: From 1,007 to 2,000+ ops/s/thread (Medium Priority)
4. **P99/P50 ratio**: From 377x to <50x for predictability (High Priority)

**Next Steps**:
1. Investigate why average-time mode shows stable P99 while throughput mode spikes (both with 100 threads)
2. Profile signature validation hotspots causing 10ms+ spikes
3. Consider async architecture for 2x throughput gain

**Production Readiness**: The library shows good performance characteristics in average-time mode. The extreme P99 spikes appear specific to throughput mode optimization and may not impact typical production scenarios.

## JFR Load Analysis Results (July 31, 2025)

### Executive Summary

JFR analysis confirms that P99 latency spikes are **load-induced** rather than token-related. With identical 100-thread configuration, throughput mode shows 1,290x P99/P50 ratio while average-time mode maintains stable 6x ratio.

### Key Findings

#### 1. **Extreme P99 Spikes in Throughput Mode**

| Component | P50 (μs) | P99 (μs) | P99/P50 Ratio | Max (μs) |
|-----------|----------|----------|---------------|----------|
| **Signature Validation** | 52 | 67,066 | 1,290x | - |
| **Complete Validation** | 86 | 130,135 | 1,513x | 526,839 |

#### 2. **Stable Performance in Average-Time Mode**

| Component | P50 (μs) | P99 (μs) | P99/P50 Ratio |
|-----------|----------|----------|---------------|
| **Signature Validation** | 46 | 276 | 6x |
| **Complete Validation** | 60 | 345 | 5.8x |

#### 3. **Mode Comparison (100 threads)**

| Metric | Throughput Mode | Average-Time Mode | Difference |
|--------|-----------------|-------------------|------------|
| **Throughput** | 35,182 ops/s | ~465 ops/s | 76x |
| **Avg Latency** | 2,842 μs | 2,147 μs | 1.3x |
| **P99 Signature** | 67,066 μs | 276 μs | 243x |
| **P99/P50 Ratio** | 1,290x | 6x | 215x |
| **Max Latency** | 526,839 μs | ~500 μs | 1,054x |

### Root Cause Analysis

1. **JMH Scheduling Difference**: Throughput mode aggressively pushes for maximum operations, creating severe contention
2. **No Token Diversity Impact**: All tokens are similar, confirming spikes are purely load-related
3. **JCA Bottleneck**: Signature validation through JCA shows extreme sensitivity to concurrent load
4. **Coefficient of Variation**: 1,061% indicates extreme performance unpredictability under load

### Variance Metrics

- **Average CV**: 1,057% (extremely high variance)
- **CV Range**: 729% - 1,390%
- **Max Concurrent Operations**: Limited to 1 (suggesting serialization bottleneck)

### Implications

1. **Production Impact**: Limited if using request-scoped validation (average-time pattern)
2. **Batch Processing Risk**: High if using throughput-optimized patterns
3. **Primary Bottleneck**: Signature validation accounts for 77% of P99 latency
4. **Optimization Priority**: Signature validation caching is critical

## JFR Hotspot Analysis (July 31, 2025)

### Key Findings

#### 1. **Critical Lock Contention in Cache LRU Management**

JFR analysis reveals massive monitor wait times (200-263ms) in the cache access path:

| Component | Wait Time | Root Cause |
|-----------|-----------|------------|
| **AccessTokenCache** | 262ms | LRU lock contention |
| **Lock Type** | ReentrantReadWriteLock | Write lock for LRU updates |
| **Affected Threads** | 100+ | All threads blocked on same lock |

#### 2. **Stack Trace Analysis**

The monitor waits occur during cache operations:
```
AccessTokenCache.lambda$computeIfAbsent$2 (line 226)
TokenValidator.lambda$processAccessTokenWithCache$4 (line 410)
TokenValidator.buildAccessToken (line 533)
TokenBuilder.createAccessToken (line 98)
AccessTokenContent.<init> (line 83)
```

#### 3. **Root Cause Explanation**

- **Token Building is Fast**: Metrics show 2-21μs for token construction
- **Cache Contention is Slow**: LRU tracking requires write lock acquisition
- **Lock Implementation**: `LinkedHashMap` protected by `ReentrantReadWriteLock`
- **Contention Pattern**: All 100 threads compete for write lock on every cache access

#### 4. **Why Metrics Seem Contradictory**

- **token_building**: Measures only object creation (2-21μs) ✓
- **complete_validation**: Includes cache access with lock contention (67ms P99) ✗
- **Monitor Class**: `int[]` indicates internal JVM lock structures

#### 5. **GC Contribution**

Minor contributor compared to lock contention:
- G1 Young GC pauses: 1.3-2.9ms
- Not significant enough to explain 67ms P99 spikes
- GC is well-tuned and not the primary issue

### Recommendations

1. **Immediate Fix**: Replace LRU implementation
   - Use lock-free data structure (e.g., Caffeine cache)
   - Implement striped locks for LRU tracking
   - Consider removing LRU in favor of TTL-only eviction

2. **Alternative Approaches**:
   - **Striped Locks**: Partition LRU map by hash buckets
   - **Lock-Free LRU**: Use ConcurrentLinkedHashMap or similar
   - **Async LRU Updates**: Queue LRU updates for background processing

3. **Validation**: After fix, verify lock contention is eliminated with JFR