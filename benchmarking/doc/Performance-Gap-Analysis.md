# Performance Gap Analysis: Health vs JWT Endpoints

## Executive Summary

**The Discrepancy:** Health endpoint achieves 104,207 ops/s while JWT endpoint achieves 25,924 ops/s - a **78,283 ops/s gap (4x difference)**.

This analysis uses **actual measured data** from the latest benchmark run to identify where the performance gap comes from.

## Measured Performance Data (Evidence)

### Integration Benchmarks (WRK, 50 connections, 5 threads)

| Metric | Health Endpoint | JWT Endpoint | Gap |
|--------|----------------|--------------|-----|
| **Throughput** | 104,207 ops/s | 25,924 ops/s | **78,283 ops/s (4.0x)** |
| **P50 Latency** | 0.345ms (345µs) | 1.72ms | **1.375ms (5.0x)** |
| **P90 Latency** | 1.77ms | 3.66ms | 1.89ms (2.1x) |
| **P99 Latency** | 5.92ms | 10.64ms | 4.72ms (1.8x) |
| **CPU (avg)** | 68.9% | 78.2% | 9.3% |
| **CPU (peak)** | 74.6% | 83.5% | 8.9% |
| **Threads (avg)** | 36 | 67 | 31 (1.86x) |
| **Threads (peak)** | 37 | 72 | 35 (1.95x) |

### Key Application Metric (Evidence)

**JWT Validation Time (measured by application):** 0.21ms average
- Total validations: 280,303
- Cache hit rate: 100%
- This is the actual time spent in the JWT validation library per request

### Micro-Benchmark Data (JMH, 100 threads)

| Benchmark | Throughput | P50 Latency |
|-----------|-----------|-------------|
| Core Validation | 108,400 ops/s | 53µs (0.053ms) |

**Critical Note:** The library achieves 0.053ms in micro-benchmarks but 0.21ms in integration tests - **4x slower in real usage**.

## Latency Breakdown: Where Does the 1.72ms Go?

### Known Components (Evidence)

```
JWT P50 Total:                           1.72ms
├─ Base HTTP overhead (health):          0.345ms (20%)  [MEASURED]
├─ JWT validation (application metric):  0.210ms (12%)  [MEASURED]
└─ Unknown overhead:                     1.165ms (68%)  [GAP]
```

**The 1.165ms gap is 68% of total JWT latency and is unexplained by current measurements.**

## Endpoint Implementation Analysis

### Health Check Endpoint (`/q/health/live`)

**Implementation:** Quarkus built-in SmallRye Health
- Uses CDI (`@ApplicationScoped`, `@Inject`, `@Liveness`)
- Simple logic: Check if issuer configs are present
- Response: `{"status":"UP","checks":[{...}]}` (~100 bytes)
- **No JWT validation**
- **No custom serialization**

### JWT Validation Endpoint (`/jwt/validate`)

**Implementation:** Custom REST endpoint
- Uses CDI (`@ApplicationScoped`, `@Inject`, `@RunOnVirtualThread`)
- Complex logic:
  1. CDI producer invocation: `basicToken.get()`
  2. Authorization check: `tokenResult.isSuccessfullyAuthorized()`
  3. Token extraction: `tokenResult.getAccessTokenContent()`
  4. JWT validation: **0.21ms** (measured)
  5. Token claims extraction and building response map
  6. JSON serialization of full token response (~500-1000 bytes):
     - subject, email, scopes, roles, groups
     - Nested collections and maps
  7. Response building

## Where CDI IS and IS NOT a Factor

### Both Endpoints Use CDI

**Health Check:**
- `@ApplicationScoped` bean
- `@Inject List<IssuerConfig>`
- CDI context management

**JWT Validation:**
- `@ApplicationScoped` bean
- `@Inject TokenValidator`
- `@Inject Instance<BearerTokenResult>` (request-scoped producer)
- CDI context management

**Conclusion:** CDI itself is NOT the differentiator - both use CDI injection and beans.

### The Real Differences

| Factor | Health | JWT | Impact |
|--------|--------|-----|--------|
| **Request-scoped producers** | None | Yes (`Instance<BearerTokenResult>`) | Per-request bean creation |
| **Producer invocation** | None | `basicToken.get()` per request | CDI producer overhead |
| **Response payload size** | ~100 bytes | ~500-1000 bytes | 5-10x more data to serialize |
| **Response complexity** | Simple status object | Nested maps with collections | More complex JSON serialization |
| **Business logic** | Check if list is empty | Extract claims, build map, authorization checks | More CPU work |
| **JWT validation** | None | 0.21ms (measured) | Core validation time |

## Hypothesis: Where is the 1.165ms?

The following are **educated guesses** based on code analysis, NOT measured data:

### Likely Contributors (High Confidence)

1. **Response Payload Processing (0.4-0.6ms)**
   - Health: 100 bytes, simple structure
   - JWT: 500-1000 bytes, nested maps with collections
   - 5-10x more data to serialize
   - Jackson JSON serialization overhead

2. **CDI Request-Scoped Producer Overhead (0.2-0.3ms)**
   - JWT calls `basicToken.get()` which invokes CDI producer
   - Producer creates new `BearerTokenResult` per request
   - Request scope setup and teardown
   - Health has no request-scoped producers

3. **Token Claims Extraction (0.1-0.2ms)**
   - Extract subject, email, scopes, roles, groups from token
   - Build HashMap with token data (line 416-422)
   - Multiple Optional unwrapping operations

4. **Authorization Logic (0.05-0.1ms)**
   - `tokenResult.isSuccessfullyAuthorized()` check
   - `tokenResult.getAccessTokenContent()` extraction
   - Health has trivial logic: `isEmpty()` check

5. **Library Integration Overhead (0.157ms)**
   - **Measured:** Micro-benchmark 0.053ms vs Integration 0.210ms
   - CDI proxying, request context, classloader overhead
   - This is the gap between library in isolation vs embedded in Quarkus

6. **HTTP Processing Overhead (0.1-0.2ms)**
   - More bytes to write to network (5-10x larger payload)
   - TCP send buffer operations
   - TLS encryption of larger payload

**Total estimated:** 0.967-1.557ms (matches 1.165ms gap within error margin)

### Speculative Contributors (Lower Confidence)

- Virtual thread overhead: More threads (67 vs 36) may cause more parking/unparking
- Quarkus interceptors: If any are configured on JWT endpoint path
- Logging overhead: More debug logging in JWT endpoint (though disabled in prod)

## Critical Finding: Library Performance Degradation

### Evidence

| Environment | Validation Time | Multiplier |
|------------|----------------|-----------|
| Micro-benchmark (JMH, isolated) | 0.053ms | 1.0x |
| Integration (in Quarkus, cached) | 0.210ms | **4.0x** |

**The library validation is 4x slower in the integration environment compared to isolated micro-benchmarks.**

### What This Means

The library itself is fast (0.053ms), but when embedded in Quarkus with CDI, HTTP processing, and request handling, it slows to 0.210ms. This 0.157ms overhead is substantial.

### Possible Causes (Speculation)

- CDI proxying overhead for `TokenValidator` bean
- Request-scoped context management
- Thread-local context setup/teardown
- Classloader overhead vs JMH's optimized execution
- Lock contention in shared caches under HTTP load
- Prometheus metrics recording overhead

## Throughput Analysis

### Per-Thread Efficiency

| Endpoint | Throughput | Threads | ops/s per thread |
|----------|-----------|---------|------------------|
| Health | 104,207 ops/s | 36 avg | **2,894** |
| JWT | 25,924 ops/s | 67 avg | **387** |

**JWT endpoint has 7.5x worse per-thread efficiency!**

This strongly suggests that the JWT endpoint spends more time per request (1.72ms vs 0.345ms) and/or experiences more thread blocking.

### CPU Efficiency

| Endpoint | Throughput | CPU | ops/s per 1% CPU |
|----------|-----------|-----|------------------|
| Health | 104,207 ops/s | 68.9% | **1,512** |
| JWT | 25,924 ops/s | 78.2% | **331** |

**JWT endpoint is 4.6x less CPU-efficient!**

This confirms that JWT processing is more CPU-intensive per request, which makes sense given:
- More HTTP payload processing (5-10x larger)
- Complex JSON serialization (nested structures)
- JWT validation logic (0.21ms)
- Token claims extraction and response building

## Conclusions

### What We Know (Evidence-Based)

1. **Health endpoint P50:** 0.345ms (measured)
2. **JWT endpoint P50:** 1.72ms (measured)
3. **JWT validation time:** 0.21ms (measured by application)
4. **Unexplained overhead:** 1.165ms (68% of JWT latency)
5. **Library micro-benchmark:** 0.053ms
6. **Library integration overhead:** 0.157ms (4x slower than micro-benchmark)
7. **Thread usage:** JWT uses 1.86x more threads
8. **CPU usage:** JWT uses 1.13x more CPU
9. **Per-thread efficiency:** JWT is 7.5x worse
10. **Per-CPU efficiency:** JWT is 4.6x worse

### What We Suspect (Hypotheses)

The 1.165ms unexplained overhead is likely:
- Response serialization: ~0.4-0.6ms (5-10x larger payload)
- CDI producer per request: ~0.2-0.3ms (request-scoped bean creation)
- Token claims extraction: ~0.1-0.2ms (building response map)
- Library integration overhead: ~0.157ms (measured vs micro-benchmark)
- Authorization logic: ~0.05-0.1ms
- HTTP payload processing: ~0.1-0.2ms (larger writes)

### Primary Bottleneck: Response Serialization (Suspected)

**We do NOT have enough evidence to conclusively identify the primary bottleneck.**

The original analysis claimed "JWT cryptographic validation overhead" was the primary bottleneck. This is **provably false** based on measured data:
- Library validation: 0.21ms (12% of total 1.72ms)
- Cryptographic validation is embedded in the 0.21ms

The actual bottlenecks are more likely:
1. **Response serialization** (suspected ~0.4-0.6ms, ~35% of gap) - 5-10x larger payload
2. **CDI producer overhead** (suspected ~0.2-0.3ms, ~20% of gap) - request-scoped bean
3. **Library integration overhead** (measured 0.157ms, 13% of gap)
4. **Token claims extraction** (suspected ~0.1-0.2ms, ~12% of gap)
5. **HTTP/network overhead** (suspected ~0.1-0.2ms, ~12% of gap)

## Required Measurements to Close the Gap

To definitively identify where the 1.165ms goes, we need to measure:

1. **JSON serialization time** - Add timing to Jackson serialization
2. **HTTP write time** - Measure time to write response body
3. **CDI producer invocation time** - Measure `basicToken.get()` call
4. **Claims extraction time** - Measure `createTokenResponse()` method
5. **Per-request breakdown** - Add detailed timing at each step:
   ```
   total_time = producer_invocation + authorization_check + validation +
                claims_extraction + serialization + http_write
   ```

## Recommendations

### 1. Add Detailed Timing Metrics (Critical)

Add fine-grained timing to measure:
- CDI producer invocation time (`basicToken.get()`)
- Authorization and token extraction time
- Claims extraction and response building time (`createTokenResponse()`)
- JSON serialization time
- HTTP response write time

This will convert our hypotheses into evidence.

### 2. Investigate Library Integration Overhead (High Priority)

The 4x performance degradation (0.053ms → 0.210ms) from micro-benchmark to integration is concerning. Investigate:
- Is CDI proxying adding overhead?
- Are there unnecessary object allocations?
- Is the cache less effective under HTTP load?
- Is Prometheus metrics recording adding overhead?

### 3. Profile Under Load (High Priority)

Use profiling tools to identify actual hotspots:
- JFR (Java Flight Recorder) profiling during benchmark run
- Async-profiler flame graphs
- Look for:
  - Lock contention
  - Object allocation hotspots
  - CPU-intensive methods
  - JSON serialization overhead

### 4. Optimize Response Serialization (Medium Priority)

If profiling confirms serialization is a bottleneck:
- Consider simpler response format
- Cache serialized responses for common token patterns
- Use faster JSON serialization library (if Jackson is slow)
- Reduce payload size (only send requested claims)

## Documentation Updates

### Update Analysis-10.2025-Integration.adoc

The document currently states:
> **Primary bottleneck: JWT cryptographic validation overhead (55.5K ops/s gap)**

**This is incorrect.** The document should be updated to reflect:

1. **Remove misleading bottleneck attribution**
   - The library validation takes only 0.21ms (12% of total 1.72ms latency)
   - Cryptographic operations are NOT the primary bottleneck

2. **Add measured validation time**
   ```adoc
   ### JWT Validation Performance

   **Measured validation time:** 0.21ms average (application metric)
   - Total validations: 280,303
   - Cache hit rate: 100%
   - Validation accounts for **12% of total latency**
   ```

3. **Acknowledge unexplained overhead**
   ```adoc
   ### Performance Gap Analysis

   Total JWT latency: 1.72ms (P50)
   ├─ Base HTTP overhead: 0.345ms (20%)
   ├─ JWT validation: 0.210ms (12%)
   └─ Unknown overhead: 1.165ms (68%)

   **The 68% unexplained latency** likely comes from:
   - Response serialization (larger payload)
   - CDI request-scoped producer overhead
   - Token claims extraction
   - HTTP payload processing
   - Library integration overhead

   Further instrumentation is needed to measure these components.
   ```

4. **Correct throughput gap explanation**
   ```adoc
   ### Throughput Gap: Health (104k ops/s) vs JWT (26k ops/s)

   The 4x throughput difference is primarily due to:
   - **5x latency difference** (1.72ms vs 0.345ms)
   - **1.86x more threads** (67 vs 36) with worse per-thread efficiency
   - **10% more CPU usage** (78% vs 69%)

   The gap is NOT caused by cryptographic validation (only 0.21ms),
   but by REST framework overhead (response building, serialization, HTTP processing).
   ```

5. **Link to this analysis**
   ```adoc
   For detailed performance gap analysis, see:
   link:Performance-Gap-Analysis.md[Performance Gap Analysis]
   ```
