# Benchmark Integration Quarkus - First HTTP Request Timeout Issue

## ❌ ROOT CAUSE STILL UNKNOWN

**Current Status**: Despite extensive investigation and implementation of multiple technical solutions, health benchmark timeouts persist.

## Critical Finding - THE SMOKING GUN 🔍

**The problem is JVM-wide initialization that ONLY affects the FIRST benchmark that runs.**

**Definitive Evidence:**
- **Health Benchmark (runs first)**: Iterations 1-2 work, iteration 3 times out
- **JWT Benchmark (runs second)**: ALL 5 iterations work perfectly
- **Key Insight**: The second benchmark ALWAYS succeeds completely!
- **Proof**: Issue follows execution order, NOT benchmark type

**Root Cause Pattern:**
- **First benchmark**: Triggers buggy JVM-wide initialization (10s timeout)
- **Gets broken state**: Works for ~20 seconds then fails
- **Second benchmark**: Uses already-initialized JVM state, works perfectly
- **Pattern**: JVM-wide static/global initialization bug, NOT connection issue

## Problem Pattern

1. **First HTTP request per thread**: Times out after 10 seconds
2. **All subsequent HTTP requests**: Excellent performance (1.5+ ops/ms)  
3. **Service readiness check**: Passes - containers report ready (0.19s startup)
4. **Manual testing**: Works after initial request succeeds

## Current Evidence

**Consistent Success**:
- **JWT Validation Benchmarks**: Work consistently with any HTTP client, any protocol, any timeout configuration

**Consistent Failure**:
- **Health Check Benchmarks**: Fail consistently regardless of HTTP client, protocol, or timeout settings

**Key Pattern**: Issue is specific to health check endpoint behavior, not HTTP infrastructure.

## What Has Been Proven NOT to be the Root Cause

- **HTTP client implementation** (Java HttpClient vs OkHttp) ❌
- **HTTP protocol version** (HTTP/2 vs HTTP/1.1) ❌
- **Connection timeout configuration** (5s vs 30s+ timeouts) ❌
- **Keep-alive timeout settings** (JDK httpclient.keepalive.timeout) ❌
- **SSL certificate validation delays** (trust-all certificates tested) ❌
- **SSL session cache configuration** (24-hour timeout vs 20s working window) ❌
- **Trust store optimization** (bypassed entirely with trust-all) ❌
- **JMH configuration settings** (warmup iterations, thread count) ❌
- **Connection pool/factory issues** (fresh instances tested) ❌
- **Service readiness timing** (containers report ready before failures) ❌

*See [issue/dismissed.md](issue/dismissed.md) for detailed analysis of all failed hypotheses.*

## Untested Solutions to Evaluate

### Priority 1: First Request Priming
```java
// Add test request in @Setup(Level.Trial) to initialize service state
@Setup(Level.Trial)
public void performAdditionalSetup() {
    // Make priming request to initialize all service state
    sendHealthCheckRequest();
}
```

### Priority 2: Enhanced Service Readiness
```java
// Wait for deeper readiness criteria beyond basic health endpoint
// Check service internals, not just HTTP response status
```

### Priority 3: Service-Side Investigation
```bash
# Analyze what happens on Quarkus service during first request handling
# Focus on service initialization vs client initialization
```

## Detailed Documentation

- **[issue/dismissed.md](issue/dismissed.md)** - All dismissed hypotheses and failed approaches
- **[issue/investigation.md](issue/investigation.md)** - Investigation plans, research findings, and history
- **[issue/technical.md](issue/technical.md)** - Technical details, timeout analysis, and architecture comparison

## Next Investigation Required

Focus on health check endpoint-specific issues, possibly service-side initialization or endpoint-specific configuration problems.