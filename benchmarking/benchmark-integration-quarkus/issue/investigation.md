# Investigation Plan and Research

This document contains detailed investigation plans, web research findings, and historical analysis for the health check benchmark timeout issue.

## Investigation Plan - Initialization Focus

### Priority 1: JMH Warmup vs Priming Investigation

#### Task 1.1: JMH Warmup State Preservation Analysis
- **Objective**: Understand why JMH warmup doesn't preserve initialization state for measurement phase
- **Key Insight**: Warmup IS designed for priming, but it's not working - warmup succeeds but measurement phase fails
- **Critical Evidence**: 
  - Working JSON: `"warmupIterations": 0` (no warmup) - worked
  - Current failing: `jmh.warmupIterations=1` (with warmup) - warmup succeeds (0.459 ops/ms), iteration 1 fails
  - This suggests warmup state is not preserved for measurement phase
- **Method**:
  - Analyze if there's a state reset between warmup and measurement phases
  - Check if warmup and measurement use different execution contexts
  - Investigate JMH lifecycle and state management between phases
- **Expected Finding**: JMH framework resets some state between warmup and measurement that undoes initialization
- **Success Criteria**: Understand why JMH's built-in priming mechanism fails for this initialization

#### Task 1.2: Manual Pre-Setup Priming Implementation
- **Objective**: Implement manual initialization in `@Setup(Level.Trial)` before JMH warmup starts
- **Rationale**: Since JMH's warmup mechanism fails, implement priming at JMH framework level
- **Status**: **COMPLETED - CRITICAL EVIDENCE OBTAINED**
- **Implementation**:
  - Added `performAdditionalSetup()` override methods to both benchmark classes
  - Health benchmark: Makes real `/q/health` request during setup
  - JWT benchmark: Makes real `/jwt/validate` request during setup (after token repository initialization)
  - Added proper INFO/ERROR logging for priming success/failure
- **Results**: **BOTH priming requests timeout with `java.net.http.HttpTimeoutException`**
- **Critical Finding**: The priming requests themselves experience the exact same first HTTP request initialization failure
- **Evidence Significance**: This definitively proves the issue is client-side first HTTP request initialization bottleneck, not JMH measurement phase timing
- **Additional Finding**: Non-blocking priming reveals intermittent failure pattern - system works for ~20 seconds after initial timeout, then completely fails again
- **Next Steps**: Focus investigation on what causes the intermittent complete failure after successful initialization

#### Task 1.3: Intermittent Failure Pattern Investigation
- **Objective**: Understand why system works for ~20 seconds then completely fails again
- **Rationale**: Non-blocking priming revealed system oscillates between working and complete failure states
- **Critical Evidence**:
  - Health benchmark: Works for iterations 1-2 (~20 seconds), completely fails on iteration 3
  - JWT benchmark: Works for all 5 iterations (benefits from health benchmark initialization)
  - Pattern suggests some state expires or gets invalidated after ~20 seconds
- **Hypotheses to Test**:
  - SSL session cache expiry or invalidation
  - HttpClient connection pool state corruption
  - Certificate validation cache timeout
  - DNS cache entry expiration
  - System resource exhaustion (file descriptors, threads)
- **Method**:
  - Add detailed timing logs between iterations
  - Monitor SSL session state and connection pool metrics
  - Test with extended iteration delays to identify time-based triggers
  - Check for resource leaks or exhaustion patterns
- **Success Criteria**: Identify the specific cause of the ~20-second working window

### Priority 2: SSL/Trust Store Initialization Analysis

#### Task 2.1: SSL Context First-Time Loading Investigation
- **Objective**: Identify if SSL context/trust store loading is the 10-second bottleneck
- **Method**: 
  - Enable comprehensive SSL debug logging (`-Djavax.net.debug=ssl,handshake,trustmanager`)
  - Measure trust store loading time during first HTTPS request
  - Compare trust store access patterns: first vs subsequent requests
- **Expected Evidence**: Trust store loading takes >10 seconds on first access
- **Focus**: Self-signed certificate trust store configuration and loading time

#### Task 2.2: Trust Store Configuration Analysis  
- **Objective**: Identify trust store setup issues causing initialization delays
- **Method**:
  - Analyze how self-signed localhost certificates are configured in trust store
  - Check if trust store is properly pre-loaded vs loaded on-demand
  - Measure PKIX path building time for self-signed certificates
- **Expected Finding**: Trust store misconfiguration causing expensive validation on first use
- **Files**: Trust store configuration in cui-benchmarking-common

### Priority 3: HttpClient Internal State Investigation

#### Task 3.1: HttpClient Lazy Initialization Analysis
- **Objective**: Identify HttpClient internal state causing first-request delays
- **Method**:
  - Analyze HttpClient internal initialization during first request
  - Check DNS resolution caching, SSL provider loading, internal state setup
  - Compare fresh HttpClient vs reused HttpClient first-request behavior
- **Expected Finding**: HttpClient internal lazy initialization taking >10 seconds

#### Task 3.2: DNS Resolution and Network Stack Investigation
- **Objective**: Rule out DNS/network stack initialization as bottleneck
- **Method**:
  - Pre-resolve localhost to 127.0.0.1 in hosts file or programmatically
  - Test with IP address instead of hostname
  - Measure DNS resolution time during first vs subsequent requests
- **Expected Outcome**: Either eliminate DNS as factor or identify it as bottleneck

#### Task 3.3: HttpClient Builder Configuration Deep Dive
- **Objective**: Identify specific HttpClient configuration causing initialization delays
- **Method**:
  - Analyze current HttpClient builder parameters in HttpClientFactory
  - Test with minimal HttpClient configuration (no timeouts, no custom settings)
  - Test different HttpClient versions if available
- **Focus**: Connection timeout vs request timeout vs specific builder settings

### Priority 4: System-Level Initialization Investigation 

#### Task 4.1: Operating System SSL/Network Stack Analysis
- **Objective**: Rule out system-level initialization as bottleneck  
- **Method**:
  - Test on different operating systems (if possible)
  - Analyze system-level SSL library loading during first HTTPS request
  - Check if issue persists with different JDK versions
- **Expected Outcome**: Identify if issue is OS/JDK specific or application-level

#### Task 4.2: Controlled Initialization Timing Test
- **Objective**: Measure exact initialization timing breakdown
- **Method**:
  - Create isolated test making single HTTPS request to localhost:10443
  - Measure timing of each initialization phase (SSL, DNS, connection, handshake)
  - Identify which specific phase takes >10 seconds
- **Expected Finding**: One specific phase consuming majority of the 10+ second delay

### Priority 5: Solution Implementation and Validation

#### Task 5.1: Trust Store Optimization (High Impact) 
- **Objective**: Optimize trust store configuration to eliminate initialization delays
- **Method**:
  - Pre-configure trust store with localhost certificates
  - Optimize trust store loading and caching strategies
  - Test alternative trust store configurations
- **Success Criteria**: First request completes within normal timeout (<5 seconds)

#### Task 5.2: Alternative Initialization Strategies (Fallback)
- **Objective**: Test alternative approaches if manual priming doesn't work
- **Method**:
  - Increase request timeout to accommodate initialization delay
  - Implement retry logic with exponential backoff for first requests
  - Test with HTTP instead of HTTPS to isolate SSL as factor
- **Success Criteria**: Reliable benchmark execution without timeout failures

## Web Research Findings (2025-09-03)

### Critical JDK Bug Reports Identified

#### JDK-8312433: Connection Pool "No Active Streams" Failures
- **Issue**: HttpClient requests fail with "HttpConnectTimeoutException: HTTP connection idle, no active streams"
- **Affected Versions**: Java 20+ (regression from Java 19)
- **Pattern**: Connections incorrectly considered idle and closed, leading to request failures
- **Relevance**: Matches our exact error pattern with intermittent timeouts

#### JDK-8297030: Default Keep-Alive Timeout Reduced
- **Change**: Default keep-alive timeout reduced from 1200 seconds to 30 seconds
- **Rationale**: Better alignment with cloud load balancer configurations (typically 60-350 seconds)
- **Impact**: More aggressive connection cycling, increased SSL handshake overhead
- **Configuration**: Can override with `-Djdk.httpclient.keepalive.timeout=X`

#### JDK-8288717: HTTP/2 Idle Connection Management
- **Issue**: HTTP/2 connections lacked idle timeout management
- **Fix**: Added configurable idle timeout for HTTP/2 (`jdk.httpclient.keepalive.timeout.h2`)
- **Default**: 30 seconds (was previously unlimited)
- **Impact**: HTTP/2 connections now closed after 30 seconds of inactivity

### SSL Session Cache and Self-Signed Certificate Issues

#### SSL Session Cache Changes
- **JDK 8u261+**: Default cache size changed from unlimited to 20,480 entries
- **Session Timeout**: Default 24 hours (86400 seconds)
- **Memory Impact**: Prevents GC pauses from millions of expired sessions
- **Configuration**: `javax.net.ssl.sessionCacheSize` system property

#### Self-Signed Certificate Validation Overhead
- **PKIX Path Building**: Can timeout during certificate validation
- **Default SSL Handshake Timeout**: ~10 seconds
- **Localhost Issues**: Hostname verification failures with self-signed certs
- **Docker Impact**: Additional network layer adds validation complexity

### Connection Pool Behavior Analysis

#### The 20-Second Pattern - Critical Analysis

**Important Contradiction**: SSL handshake to localhost should take milliseconds, not 10+ seconds on modern hardware. The fact that iteration 1 works IMMEDIATELY after the priming timeout proves this is NOT an SSL handshake issue.

**Actual Pattern Observed**:
1. **Initial Timeout (10 seconds)**: HttpClient internal initialization timeout (NOT SSL)
2. **Immediate Success**: First benchmark iteration works perfectly (2.283 ops/ms)
3. **Working Window (~20 seconds)**: Iterations 1-2 succeed with excellent performance
4. **Complete Failure**: After ~20 seconds, system completely fails with timeout
5. **Key Evidence**: If SSL took 10+ seconds, iteration 1 couldn't succeed immediately

**Alternative Root Causes to Investigate**:
- **HttpClient Initialization Bug**: First use triggers buggy initialization that times out but partially completes
- **Connection Pool Bootstrap**: Pool creation times out but completes in background
- **Docker Network Issue**: First connection through Docker bridge times out
- **DNS Resolution Cache**: First resolution times out, cache expires after 20s
- **NOT SSL/Certificate Issue**: Performance after timeout proves SSL works fine

#### Known Connection Pool Issues
- **Stale Connection Detection**: HttpClient may not detect server-closed connections
- **Race Conditions**: TLS 1.3 session resumption race conditions
- **Validation Failures**: Connections appear valid but fail when used
- **Pool Exhaustion**: All connections busy, new requests timeout waiting

### Infrastructure Interaction Issues

#### Load Balancer Conflicts
- **AWS ALB**: 60-second default idle timeout
- **Azure LB**: 4-15 minute timeouts
- **Mismatch**: Client timeout (30s) vs infrastructure timeouts creates failure windows
- **Silent Drops**: Connections closed server-side without client notification

#### Docker/Container Networking
- **Additional Latency**: Container networking adds SSL handshake overhead
- **DNS Resolution**: Container DNS can add significant delays
- **Certificate Access**: Trust stores must be properly mounted in containers
- **Network Isolation**: May prevent proper connection validation

## Investigation History

### Completed Investigations

#### JMH Configuration Root Cause Analysis
- **Discovery**: Found working results JSON with different JMH settings
- **Test**: Replicated exact working configuration (warmupIterations=0, threads=1)  
- **Result**: Issue persists even with exact working JMH settings
- **Conclusion**: Issue is not JMH configuration - it's first HTTP request initialization

#### Clean Build Verification  
- **Issue**: JMH compilation errors blocking proper benchmark execution
- **Resolution**: Complete clean rebuild resolved JMH generated code corruption
- **Verification**: Benchmarks now exhibit original HTTP timeout behavior (not compilation errors)

#### Detailed Logging Implementation
- **Added**: Debug logging to benchmark lifecycle and HTTP request execution
- **Configuration**: FINE level for benchmark packages, FINER for SSL
- **Format**: Timestamps with milliseconds for precise timing analysis