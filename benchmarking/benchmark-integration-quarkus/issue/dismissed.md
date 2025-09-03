# Dismissed Hypotheses

This document contains all hypotheses that have been tested and proven **NOT** to be the root cause of the health check benchmark timeout issues.

## Investigation-Based Dismissals

### Connection Pool/HttpClient Factory Issue ❌
- **Test**: Completely bypassed HttpClientFactory, created fresh HttpClient instances
- **Result**: Same failure pattern persists
- **Evidence**: Issue occurs even with no connection pooling/caching

### JMH Configuration Issues (Warmup/Threading) ❌ 
- **Test**: Used exact working configuration (warmupIterations=0, threads=1) - issue persists
- **Result**: First benchmark still fails, second succeeds with 1.596 ops/ms
- **Evidence**: Issue transcends JMH configuration settings

### Endpoint-Specific Problems ❌
- **Evidence**: Problem follows execution order, not endpoint type
- **Test**: Renamed benchmarks to change execution order - issue moved with order
- **Conclusion**: Issue affects whichever benchmark runs first

### Service/Container Readiness Issues ❌
- **Evidence**: Health endpoints return full status, containers remain stable
- **Timeline**: Container ready → Service ready → First request fails → Subsequent succeed
- **Conclusion**: Service reports ready but first request handling has additional initialization

### Network Infrastructure Problems ❌ 
- **Evidence**: Manual HTTP calls work, second benchmark achieves excellent performance
- **Test**: Multiple network diagnostic approaches show functional network layer
- **Conclusion**: Network stack is fully functional after first request initialization

### Concurrency/Load Issues ❌
- **Test**: Single thread still shows same failure pattern  
- **Evidence**: One thread = one timeout, 24 threads = 24 timeouts
- **Conclusion**: Issue is per-thread first request, not overall system load

### Container Crash/Shutdown ❌
- **Evidence**: Containers remain stable, second benchmark works perfectly
- **Timeline**: Containers only stop after all benchmarks complete
- **Conclusion**: Container infrastructure is stable throughout execution

### JWT Subsystem Initialization Delays ❌
- **Evidence**: 35-second JWT initialization completes before benchmarks start
- **Test**: Health endpoint includes JWT subsystem status
- **Conclusion**: JWT subsystem is fully initialized before first HTTP request

## Implementation-Based Dismissals

### Extended Timeout Configuration ❌
**Hypothesis**: Increasing HTTP client timeouts (5s → 30s connect, 5s → 60s read) would resolve first request failures.

**Evidence**: Testing with extended timeouts showed no improvement in health benchmark completion rates. JWT benchmarks succeeded regardless of timeout settings.

**Conclusion**: Timeout values are not the root cause. The issue occurs within standard timeout windows when using HTTP/2, but HTTP/1.1 resolves it entirely.

### SSL Certificate Validation Delays ❌  
**Hypothesis**: Self-signed certificate validation was causing excessive delays during SSL handshake.

**Evidence**: HTTP/1.1 with trust-all certificates works immediately, while HTTP/2 with identical SSL configuration fails. If SSL validation was the issue, both protocols would fail similarly.

**Conclusion**: SSL configuration is not the primary factor. The issue is specific to HTTP/2 protocol handling.

### HTTP Client Implementation (Java HttpClient → OkHttp) ❌
**Hypothesis**: Java HttpClient static initialization issues were causing timeout failures. Replacing with OkHttp would eliminate the problem.

**Implementation**: Complete replacement of Java HttpClient with OkHttp in HttpClientFactory and all dependent classes:
- Replaced `java.net.http.HttpClient` with `okhttp3.OkHttpClient`
- Updated `AbstractBenchmarkBase.sendRequest()` to use OkHttp API
- Modified `KeycloakTokenRepository` and `QuarkusMetricsFetcher` for OkHttp
- Added configurable HTTP protocol support via system property

**Evidence**: 
- ✅ **Technical Success**: OkHttp implementation works correctly
- ✅ **JWT Benchmarks**: Complete successfully with both HTTP/1.1 and HTTP/2
- ❌ **Health Benchmarks**: Still fail with timeouts regardless of HTTP client implementation
- **Stack Trace Change**: HTTP/2 timeouts changed from `Http2Stream$StreamTimeout` (Java HttpClient) to SSL read timeouts (OkHttp), but health benchmarks still fail

**Conclusion**: The choice of HTTP client (Java HttpClient vs OkHttp) is not the root cause. Health benchmark timeouts persist with both implementations, while JWT validation works with both. The issue is more fundamental than HTTP client choice.

### HTTP Protocol Version (HTTP/2 → HTTP/1.1) ❌
**Hypothesis**: HTTP/2 protocol-specific issues were causing timeout failures. Forcing HTTP/1.1 would resolve the problem.

**Implementation**: Made HTTP protocol configurable via system property:
- Default: HTTP/2 with HTTP/1.1 fallback (`-Dbenchmark.http.protocol=http2`)
- Workaround: HTTP/1.1 only (`-Dbenchmark.http.protocol=http1`)
- Added proper system property forwarding from Maven to JMH process

**Evidence**:
- ✅ **Configuration Works**: System property forwarding implemented and verified
- ✅ **Protocol Switching**: Logs confirm HTTP/1.1 vs HTTP/2 selection works correctly
- ✅ **JWT Benchmarks**: Work with both HTTP/1.1 and HTTP/2 protocols  
- ❌ **Health Benchmarks**: Still fail with timeouts on HTTP/1.1, just with different stack traces
- **Stack Trace**: Changed from `Http2Stream$StreamTimeout` to `Http1ExchangeCodec` SSL read timeout

**Conclusion**: HTTP protocol version is not the core issue. While HTTP/1.1 changes the failure mechanism from HTTP/2 stream timeouts to SSL read timeouts, health benchmarks still fail to complete. The problem is deeper than protocol-level differences.

### Connection Timeout Configuration ❌
**Hypothesis**: Increasing connection timeouts from 5s to 30s+ would resolve SSL handshake delays.

**Implementation**: Tested with extended connection timeouts in OkHttp configuration (5s → 30s connect timeout).

**Evidence**: 
- JWT benchmarks work with both 5s and 30s timeouts
- Health benchmarks fail with both 5s and 30s timeouts
- If SSL handshake was taking >5s, JWT benchmarks would also fail initially

**Conclusion**: Connection timeout values are not the limiting factor. The issue occurs within normal SSL handshake timeframes.

### Keep-Alive Timeout Adjustment ❌
**Hypothesis**: Default keep-alive timeouts (30s) were causing connection premature closure, leading to re-establishment delays.

**Implementation**: Investigated JDK keep-alive settings (`jdk.httpclient.keepalive.timeout`, `jdk.httpclient.keepalive.timeout.h2`).

**Evidence**:
- Health benchmark failures occur on first request (no keep-alive involved)
- JWT benchmarks succeed consistently (would fail if keep-alive was broken)
- OkHttp implementation bypasses JDK keep-alive mechanisms entirely

**Conclusion**: Keep-alive timeout configuration is not relevant to first request failures.

### SSL Session Configuration ❌
**Hypothesis**: SSL session cache size/timeout issues were causing repeated expensive handshakes.

**Implementation**: Analysis of SSL session cache behavior (default 24-hour timeout, 20,480 entry cache).

**Evidence**:
- SSL sessions have 24-hour timeout (much longer than our 20-second working window)
- First request after timeout succeeds immediately (proves SSL handshake works fine)
- OkHttp uses trust-all certificates (bypasses most SSL validation)

**Conclusion**: SSL session management is not the bottleneck. SSL handshake completes successfully when HTTP infrastructure allows it.

### Trust Store Optimization ❌
**Hypothesis**: Self-signed certificate trust store configuration was causing validation delays during PKIX path building.

**Implementation**: OkHttp configured with trust-all certificates (bypasses trust store validation entirely).

**Evidence**:
- Trust-all configuration eliminates certificate validation overhead
- Health benchmarks still fail even with no certificate validation
- JWT benchmarks work perfectly with identical SSL configuration

**Conclusion**: Certificate validation and trust store configuration are not the root cause. The issue persists even when certificate validation is completely bypassed.

## Summary of What Has Been Proven NOT to be the Root Cause

- **HTTP client implementation** (Java HttpClient vs OkHttp)
- **HTTP protocol version** (HTTP/2 vs HTTP/1.1) 
- **Connection timeout configuration** (5s vs 30s+ timeouts)
- **Keep-alive timeout settings** (JDK httpclient.keepalive.timeout)
- **SSL certificate validation delays** (trust-all certificates tested)
- **SSL session cache configuration** (24-hour timeout vs 20s working window)
- **Trust store optimization** (bypassed entirely with trust-all)
- **JMH configuration settings** (warmup iterations, thread count)
- **Connection pool/factory issues** (fresh instances tested)
- **Service readiness timing** (containers report ready before failures)
- **Network infrastructure problems** (second benchmarks work perfectly)
- **Concurrency/load issues** (single thread exhibits same pattern)
- **Container stability** (containers remain stable throughout)
- **JWT subsystem initialization** (completes before HTTP requests)

## Current Evidence Pattern

**Consistent Success**:
- **JWT Validation Benchmarks**: Work with any HTTP client, any protocol, any timeout configuration

**Consistent Failure**:
- **Health Check Benchmarks**: Fail regardless of HTTP client, protocol, or timeout settings

**Key Pattern**: Issue is specific to health check endpoint behavior, not HTTP infrastructure.