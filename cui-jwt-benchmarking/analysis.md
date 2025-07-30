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
| **Standard** | 100,672 | 1,007 ops/s/thread |
| **Error Handling (0%)** | 113,581 | 1,136 ops/s/thread |
| **Error Handling (50%)** | 178,651 | 1,787 ops/s/thread |

### 📈 Response Time Metrics

| Benchmark Type | Latency (μs) | Notes |
|----------------|--------------|-------|
| **Standard Average** | 860.66 | Average response time |
| **Concurrent** | 883.40 | Under concurrent load |

### 🎯 Component-Level Performance

| Operation | P50 (μs) | P95 (μs) | P99 (μs) | P99/P50 Ratio |
|-----------|----------|----------|----------|---------------|
| **Complete Validation** | 52-84 | 82-101 | 112-31,675 | 2.2x-377x |
| **Signature Validation** | 45-62 | 69-71 | 130-10,195 | 2.9x-164x |
| **Token Parsing** | 3.7-6.1 | 6.0-7.8 | 6.5-14.0 | 1.8x-2.3x |
| **Claims Validation** | 0.7-4.0 | 1.2-5.7 | 1.3-7.2 | 1.9x-1.8x |
| **Token Building** | 2.0-7.8 | 3.7-11.0 | 4.7-14.0 | 2.4x-1.8x |
| **Header Validation** | 0.1-0.5 | 0.4-1.1 | 0.5-1.6 | 5.0x-3.2x |
| **Cache Operations** | 0.0-0.4 | 0.2-0.8 | 0.3-1.1 | ∞-2.8x |

### 📊 Variance Analysis

The benchmark results show varying P99 latencies across different workloads:
- **Complete Validation**: P99 ranges from 112μs to 31,675μs depending on workload
- **Signature Validation**: P99 spikes up to 10,195μs in mixed token scenarios
- **Most Stable**: Token parsing and claims validation maintain consistent P99/P50 ratios < 3x

## Key Performance Characteristics

1. **Throughput Baseline**: 
   - Standard: 100,672 ops/s
   - Error 0%: 113,581 ops/s
   - Error 50%: 178,651 ops/s (best performance)

2. **Latency Profile**: 
   - Average: 860.66μs
   - Concurrent: 883.40μs
   - P50 range: 52-84μs (good median performance)

3. **P99 Variance**: 
   - Complete validation P99: 112μs to 31.7ms
   - Signature validation P99: up to 10.2ms
   - High P99/P50 ratios indicate optimization opportunities

4. **Component Performance**: 
   - Signature validation: Dominant component (45-62μs P50)
   - Token parsing: Consistent performance (3.7-6.1μs P50)
   - Cache operations: Minimal overhead (0.0-0.4μs P50)

## Optimization Opportunities

### High Priority
1. **P99 Latency Spikes**: Address 31.7ms spikes in complete validation
2. **Signature Validation**: Reduce 10ms+ P99 spikes
3. **P99/P50 Ratios**: Target < 50x for predictable performance

### Medium Priority
1. **Throughput Enhancement**: Target >200k ops/s baseline
2. **Thread Efficiency**: Improve from 1,007 to >2,000 ops/s/thread
3. **Average Latency**: Reduce from 860μs to <500μs

### Low Priority
1. **Cache Optimization**: Already performing well
2. **Token Parsing**: Stable performance, minor optimization potential
3. **Header Validation**: Small absolute times despite high ratios

## Conclusion

The JWT validation library baseline performance shows:
- **Solid throughput**: 100-178k ops/s depending on workload
- **Good median latency**: 52-84μs P50
- **High P99 variance**: Primary optimization target
- **Component bottlenecks**: Signature validation dominates

These baseline metrics establish the foundation for future optimization efforts, with focus areas clearly identified in P99 latency reduction and signature validation optimization.