# Benchmark Integration Quarkus - First HTTP Request Timeout Issue

## Critical Finding

**The problem is first HTTP request initialization - regardless of concurrency.**

**Initialization Pattern:**
- **24 threads** = **24 first requests** = **24 timeout failures (all fail)**
- **1 thread** = **1 first request** = **1 timeout failure (100% fail rate)**
- **Subsequent requests** = **all succeed** (100% success rate)
- **Pattern**: Global initialization issue, not concurrency issue

**Root Cause**: Something broke during refactoring that affects first HTTP request initialization. There's a gap between service "ready" signal and actual first request handling capability - occurs even with single thread.

**Critical Priming Evidence** (2025-09-03):
- **Manual priming implementation**: Added `performAdditionalSetup()` methods to both benchmarks to make real HTTPS requests during setup
- **Priming timeout results**: Both health endpoint priming AND JWT validation endpoint priming timeout with `java.net.http.HttpTimeoutException`
- **Key insight**: The priming requests themselves experience the exact same first HTTP request initialization failure
- **Validation**: This definitively proves the issue is client-side first HTTP request initialization, not benchmark measurement phase timing

## Problem Pattern

1. **First HTTP request per thread**: Times out after 10 seconds
2. **All subsequent HTTP requests**: Excellent performance (1.5+ ops/ms)  
3. **Service readiness check**: Passes - containers report ready (0.19s startup)
4. **Manual testing**: Works after initial request succeeds

## High Priority - Verification Requirements

**When running verification builds:**
1. Use exact command: `./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark`
2. Wait for complete build (up to 12 minutes)
3. Do not interrupt the build process

## Technical Details

### HTTP Request Behavior
- **First request per thread**: Always times out (10-second timeout)
- **Subsequent requests**: Excellent performance (1.5+ ops/ms)
- **Concurrent access**: All threads fail simultaneously on first request
- **Sequential access**: First fails, subsequent succeed
- **Threading impact**: Problem scales with thread count

### Container and Service Status  
- **Startup**: Containers start successfully (0.19s Quarkus startup)
- **Readiness**: Service reports ready, health checks pass
- **JWT initialization**: 35-second subsystem init completes before benchmarks
- **Gap identified**: Service "ready" ≠ first request handling ready

### HTTP Configuration
- **Connection timeout**: 5 seconds  
- **Request timeout**: 10 seconds (where failure occurs)
- **Protocol**: HTTP/2 with HTTP/1.1 fallback
- **SSL**: Trust-all certificates for self-signed certs

### Initialization Pattern Verification (2025-09-03)
**Default JMH Configuration Test:**
- `jmh.threads=24`, `jmh.warmupIterations=1`, `jmh.forks=2`
- **Expected**: 24 threads × 1 warmup × 2 forks = **48 HttpTimeoutExceptions**
- **Result**: Exactly 48 timeout failures as predicted
- **Conclusion**: Every first HTTP request fails - pure initialization issue, not concurrency

**Single Thread Test:**
- `jmh.threads=1`, `jmh.warmupIterations=0`
- **Result**: 1 timeout failure, subsequent requests in other benchmarks work perfectly
- **Critical Evidence**: Even single thread fails first request - proves not concurrency issue
- **Performance**: Second benchmark achieves 1.596 ops/ms (excellent)

**Working Configuration Analysis:**
**Historical Evidence** (from `integration-benchmark-result.json`):
- **Working version used**: `"warmupIterations": 0`, `"threads": 1`
- **All benchmarks completed successfully**: 13+ successful executions
- **Performance**: 0.1+ ops/ms across all methods
- **Key insight**: Single thread had fewer first-request failures, but issue still existed

## Root Cause Analysis

### Global Initialization Failure

**Primary Cause: First HTTP Request Initialization Bottleneck (95% Confidence)**
- **Pattern**: All first requests fail (100%), all subsequent requests succeed (100%)
- **Not Contention**: If contention, we'd see mixed success/failure - some threads would win, some would lose
- **Actual Behavior**: Perfect failure pattern indicates global initialization issue
- **Evidence**: Something must be initialized on the very first HTTP request system-wide

**Initialization Failure Characteristics:**
- **Timeout Duration**: Exactly 10 seconds (request timeout) - initialization takes longer than timeout
- **Global State**: Once first requests fail, the system is "warmed up" for subsequent requests  
- **Thread-Independent**: 1 thread or 24 threads - all first requests fail, all subsequent succeed
- **Service-Independent**: Affects both health and JWT endpoints - it's client-side initialization

**Most Likely Initialization Bottlenecks:**

1. **SSL Context/Trust Store First-Time Loading (80% confidence)**
   - Loading trust store for self-signed certificates
   - SSL context initialization with localhost certificates
   - Certificate validation chain setup

2. **HttpClient Internal State Initialization (60% confidence)**  
   - Even fresh HttpClient instances may share internal state
   - DNS resolution caching, SSL providers, internal connection managers
   - Java HttpClient internal lazy initialization

3. **System-Level Network Stack Initialization (40% confidence)**
   - First HTTPS connection triggering system SSL library loading
   - Network interface initialization for localhost connections
   - Operating system socket/SSL stack first-time setup

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
- **Next Steps**: Focus investigation on client-side HTTP initialization (SSL, DNS, HttpClient internal state)

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

## Dismissed Hypotheses

### Connection Pool/HttpClient Factory Issue
- **Test**: Completely bypassed HttpClientFactory, created fresh HttpClient instances
- **Result**: Same failure pattern persists
- **Evidence**: Issue occurs even with no connection pooling/caching

### JMH Configuration Issues (Warmup/Threading)  
- **Test**: Used exact working configuration (warmupIterations=0, threads=1) - issue persists
- **Result**: First benchmark still fails, second succeeds with 1.596 ops/ms
- **Evidence**: Issue transcends JMH configuration settings

### Endpoint-Specific Problems
- **Evidence**: Problem follows execution order, not endpoint type
- **Test**: Renamed benchmarks to change execution order - issue moved with order
- **Conclusion**: Issue affects whichever benchmark runs first

### Service/Container Readiness Issues
- **Evidence**: Health endpoints return full status, containers remain stable
- **Timeline**: Container ready → Service ready → First request fails → Subsequent succeed
- **Conclusion**: Service reports ready but first request handling has additional initialization

### Network Infrastructure Problems  
- **Evidence**: Manual HTTP calls work, second benchmark achieves excellent performance
- **Test**: Multiple network diagnostic approaches show functional network layer
- **Conclusion**: Network stack is fully functional after first request initialization

### Concurrency/Load Issues
- **Test**: Single thread still shows same failure pattern  
- **Evidence**: One thread = one timeout, 24 threads = 24 timeouts
- **Conclusion**: Issue is per-thread first request, not overall system load

### Container Crash/Shutdown
- **Evidence**: Containers remain stable, second benchmark works perfectly
- **Timeline**: Containers only stop after all benchmarks complete
- **Conclusion**: Container infrastructure is stable throughout execution

### JWT Subsystem Initialization Delays
- **Evidence**: 35-second JWT initialization completes before benchmarks start
- **Test**: Health endpoint includes JWT subsystem status
- **Conclusion**: JWT subsystem is fully initialized before first HTTP request

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

## Architecture Comparison

### Library Benchmarks (Working)
- **Location**: `benchmarking/benchmark-library`
- **Type**: Direct library calls, no network layer
- **Execution**: In-process, no external dependencies
- **Status**: All benchmarks complete successfully

### Integration Benchmarks (Failing)
- **Location**: `benchmarking/benchmark-integration-quarkus`  
- **Type**: HTTP calls to containerized service
- **Execution**: Network calls via HttpClient to Docker containers
- **Status**: First HTTP request per thread fails with timeout

### Key Architectural Differences
1. **Network Layer**: Integration requires HTTP client connections
2. **Container Dependency**: Integration depends on Docker service availability
3. **SSL/TLS Handshake**: Integration uses HTTPS with certificate negotiation
4. **Connection Management**: Integration manages HTTP connection lifecycle
5. **Service Dependencies**: Integration depends on external Keycloak + Quarkus services

## Files Involved

**Benchmark Classes:**
- `benchmarking/benchmark-integration-quarkus/src/main/java/de/cuioss/jwt/quarkus/benchmark/benchmarks/JwtHealthBenchmark.java`
- `benchmarking/benchmark-integration-quarkus/src/main/java/de/cuioss/jwt/quarkus/benchmark/benchmarks/JwtValidationBenchmark.java`
- `benchmarking/benchmark-integration-quarkus/src/main/java/de/cuioss/jwt/quarkus/benchmark/AbstractBaseBenchmark.java`

**HTTP Infrastructure:**
- `benchmarking/cui-benchmarking-common/src/main/java/de/cuioss/benchmarking/common/http/HttpClientFactory.java`
- `benchmarking/cui-benchmarking-common/src/main/java/de/cuioss/benchmarking/common/base/AbstractBenchmarkBase.java`

**Configuration:**
- `benchmarking/benchmark-integration-quarkus/pom.xml` (JMH settings)
- `benchmarking/cui-benchmarking-common/src/main/resources/benchmark-logging.properties`

**Evidence:**
- `benchmarking/benchmark-integration-quarkus/src/test/resources/integration-benchmark-result.json` (working version)

## Potential Solutions to Evaluate

1. **First Request Priming**: Add test request in `@Setup(Level.Trial)` to initialize service state
2. **Enhanced Service Readiness**: Wait for deeper readiness criteria beyond basic health endpoint
3. **Connection Pre-warming**: Pre-establish HTTP connections before benchmark execution  
4. **Retry Strategy**: Implement exponential backoff for first requests
5. **Service-Side Investigation**: Analyze what happens on Quarkus service during first request handling