# JWT Performance Optimization Roadmap

## Current Performance Status
- **Baseline**: ~200 ops/s → **Optimized**: 248-260 ops/s (25-30% improvement)
- **Virtual Threads**: ✅ Enabled on JWT validation endpoints
- **Native Startup**: 0.212s (excellent)
- **Threading**: Optimized from 2 to 8 JMH threads

## Build and Verification Workflow

### Standard Build & Verify Cycle
```bash
# 1. Build integration tests module (~6 seconds)
./mvnw clean install -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests

# 2. Build native executable for benchmarks (~1m 30s)
./mvnw clean package -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests -Pintegration-tests

# 3. Run benchmark with comprehensive logging (~2 minutes minimum, can be cancelled after)
./mvnw clean verify -pl cui-jwt-quarkus-parent/quarkus-integration-benchmark -Pintegration-benchmarks 2>&1 | tee benchmark-results.log
```

### Build Performance Metrics

**Step 1 - Integration Tests Build:**
- **Duration**: ~6 seconds
- **Success Indicators**: 
  - BUILD SUCCESS message
  - No compilation errors
  - Clean Docker container cleanup
- **Performance Target**: Under 10 seconds

**Step 2 - Native Executable Build:**
- **Duration**: ~1m 30s (90 seconds)
- **Success Indicators**:
  - Native image generation completed
  - Final executable size: ~61.5MB
  - GraalVM build without errors
- **Performance Target**: Under 2 minutes
- **Key Metrics**:
  - Peak RSS: ~2.5GB during build
  - CPU load: ~7x during compilation
  - Final artifact: `cui-jwt-quarkus-integration-tests-*-runner`

**Step 3 - Benchmark Execution:**
- **Duration**: Run for minimum 2 minutes, then can be cancelled
- **Success Indicators**:
  - Container startup: ~1 second
  - Native app startup: ~0.2 seconds
  - Benchmark throughput: 248-260 ops/s (target baseline)
- **Performance Targets**:
  - Container startup: Under 2 seconds
  - Application startup: Under 0.3 seconds
  - Throughput: 250+ ops/s sustained
  - JMH warmup: 5 iterations, stable performance

### Log Analysis Requirements
**CRITICAL**: After every build and benchmark run, analyze ALL logs for warnings and anomalies:

- **Maven Build Logs**: Check for compilation warnings, dependency conflicts, plugin issues
- **Native Image Build Logs**: Look for GraalVM warnings, reflection configuration issues, resource inclusion problems
- **Container Startup Logs**: Monitor Docker build warnings, health check failures, service startup issues
- **Benchmark Execution Logs**: Watch for JMH warnings, test failures, performance anomalies
- **Application Runtime Logs**: Check for configuration warnings, authentication errors, JWT validation issues

**Log Analysis Command**:
```bash
# Extract warnings and errors from all logs
grep -E "(WARN|WARNING|ERROR|Exception|Failed)" benchmark-results.log | sort | uniq
```

### Git Commit Strategy

**CRITICAL**: After each successful step that introduces no unresolvable warnings or errors:

1. **Verify Success Criteria**:
   - Build completes successfully
   - No critical warnings in logs (only ignorable configuration warnings acceptable)
   - Performance targets met or exceeded
   - No new test failures introduced

2. **Commit Working Changes**:
   ```bash
   git add -A
   git commit -m "$(cat <<'EOF'
   feat: [description of optimization implemented]
   
   - [specific changes made]
   - [performance improvements achieved]
   - [metrics: before → after ops/s]
   
   🤖 Generated with [Claude Code](https://claude.ai/code)
   
   Co-Authored-By: Claude <noreply@anthropic.com>
   EOF
   )"
   ```

3. **Acceptable vs Unacceptable Log Issues**:
   - ✅ **Acceptable**: Configuration property warnings (like HTTP client settings)
   - ✅ **Acceptable**: Minor GraalVM recommendations (heap settings, CPU features)
   - ✅ **Acceptable**: JMH initialization logging messages
   - ❌ **Unacceptable**: Build failures, compilation errors
   - ❌ **Unacceptable**: Container startup failures
   - ❌ **Unacceptable**: Authentication/JWT validation errors
   - ❌ **Unacceptable**: Performance regression below baseline

## Priority Order and Actionable Tasks

### 🥇 HIGH PRIORITY (Immediate Impact)

#### 1. Profile JWT Validation Hot Paths (Autonomous Implementation Possible) ⭐
**Research Summary**: JFR support for Quarkus native images is mature in 2025, offering comprehensive profiling with minimal overhead.

**Tasks:**
- [ ] **1.1** Add JFR support to native build configuration
  - Modify `application.properties`: `quarkus.native.additional-build-args=--enable-monitoring=jfr`
  - Add runtime JFR recording: `-XX:StartFlightRecording=duration=30s,filename=jwt-profile.jfr`
  - **Build & Verify**: Run full cycle, commit if successful
- [ ] **1.2** Create profiling script for JWT validation workload
  - Script to run benchmark with JFR recording
  - Target: Identify CPU hotspots in token validation pipeline
  - **Build & Verify**: Test script execution, commit working script
- [ ] **1.3** Analyze JFR results autonomously
  - Focus on: Object allocation, method profiling, I/O operations
  - Generate actionable optimization recommendations
  - **Verification**: JFR file generation and analysis completion
- [ ] **1.4** Implement top 3 identified optimizations
  - Based on profiling results (allocation reduction, method inlining, etc.)
  - **Build & Verify**: Each optimization individually tested and committed

**Expected Impact**: 10-20% performance improvement
**Effort**: Medium
**Risk**: Low (non-breaking changes)

#### 2. Create API-Compatible Reactive Endpoint for Performance Comparison ⭐
**Research Summary**: Virtual threads (current) vs Reactive (Mutiny) performance depends on workload characteristics. Both approaches have merit in 2025.

**Design Requirement**: Maintain 100% API compatibility - same URL paths, request/response format, and behavior. Only the internal implementation changes from blocking to reactive.

**Tasks:**
- [ ] **2.1** Create `EndpointType` enum and `ReactiveJwtValidationEndpoint` class
  - Create `EndpointType` enum similar to `TestRealm` pattern with factory methods
  - Implement `ReactiveJwtValidationEndpoint` at `@Path("/reactive/jwt")`
  - **API Compatibility**: Same request/response format, identical behavior
  - Convert return types: `Response` → `Uni<Response>` (transparent to clients)
  - **Build & Verify**: Both endpoints compile and deploy, commit enum and reactive endpoint
- [ ] **2.2** Implement reactive TokenValidator wrapper
  - Create `ReactiveTokenValidator` interface with identical validation logic
  - Methods: `validateAccessToken()` → `Uni<AccessTokenContent>`
  - Wrap existing synchronous validation with `Uni.createFrom().item()`
  - Ensure identical error handling and response format
  - **Build & Verify**: Unit tests pass for both implementations, commit wrapper
- [ ] **2.3** Add reactive HTTP client for JWKS loading
  - Replace blocking HTTP calls with Quarkus REST Client Reactive
  - Implement reactive JWKS caching strategy (same cache behavior)
  - Maintain ETag support and background refresh patterns
  - **Build & Verify**: Integration tests pass, caching identical, commit changes
- [ ] **2.4** Convert tests to always run in parallel against both endpoints
  - Modify existing integration tests to use `@ParameterizedTest` with `EndpointType`
  - Update benchmark classes to test both implementations simultaneously
  - Same test logic, different endpoint URLs via enum
  - **Build & Verify**: All tests run against both endpoints, results identical, commit tests
- [ ] **2.5** Create always-parallel benchmark suite
  - JMH benchmarks that measure both blocking and reactive simultaneously
  - Side-by-side performance comparison in every benchmark run
  - Track performance changes over time for both approaches
  - **Build & Verify**: Parallel benchmarks execute successfully, commit benchmark suite
- [ ] **2.6** Performance analysis and continuous tracking
  - Document performance trends for both implementations
  - Provide recommendations based on continuous parallel testing
  - Include API compatibility verification results
  - **Verification**: Continuous performance tracking documented, migration guide created

**Enum-Based Configuration Strategy** (Similar to `TestRealm` pattern):

Create `EndpointType` enum with factory methods:
```java
public enum EndpointType {
    BLOCKING("/jwt", "Virtual Threads"),
    REACTIVE("/reactive/jwt", "Mutiny Reactive");
    
    private final String basePath;
    private final String description;
    
    // Factory methods
    public static EndpointType createBlocking() { return BLOCKING; }
    public static EndpointType createReactive() { return REACTIVE; }
    
    // URL builders
    public String validatePath() { return basePath + "/validate"; }
    public String validateIdTokenPath() { return basePath + "/validate/id-token"; }
    public String validateRefreshTokenPath() { return basePath + "/validate/refresh-token"; }
}
```

**Always Parallel Testing Strategy**:
- **Both implementations tested simultaneously** in every benchmark run
- **Side-by-side performance comparison** shows how they change over time
- **JMH parameterized tests** run same scenarios against both endpoints
- **Continuous performance tracking** of both approaches
- **Performance regression detection**: Any optimization that benefits one should be measured against both
- **Fair resource competition**: Both implementations compete for same system resources during testing
- **Trend analysis**: Performance evolution tracked for both approaches across all optimizations

**API Compatibility Verification**:
- Identical HTTP status codes (200, 400, 401)
- Same JSON response structure for `ValidationResponse`
- Identical error messages and validation behavior
- Same performance characteristics under normal load
- Compatible with existing client code (no changes required)

**Parallel Test Implementation**:
```java
@ParameterizedTest
@EnumSource(EndpointType.class)
void testTokenValidation(EndpointType endpointType) {
    // Same test logic, different endpoint
    String response = given()
        .header("Authorization", "Bearer " + token)
        .post(endpointType.validatePath());
    // Same assertions for both implementations
}
```

**Benchmark Parallel Execution**:
```java
@Benchmark
@BenchmarkMode(Mode.Throughput)
public void benchmarkBlockingEndpoint() { /* blocking implementation */ }

@Benchmark 
@BenchmarkMode(Mode.Throughput)
public void benchmarkReactiveEndpoint() { /* reactive implementation */ }
```

**Expected Impact**: Potential 15-25% improvement under high concurrency
**Effort**: High
**Risk**: Low (API compatibility maintained, fallback available)

### 🥈 MEDIUM PRIORITY (Significant Impact)

#### 3. Upgrade to Oracle GraalVM with PGO ⭐
**Research Summary**: Oracle GraalVM with PGO is now **FREE for commercial use** under GFTC license (2025). PGO can provide 15-30% performance improvements.

**Feasibility Assessment:**
- ✅ **License**: GFTC allows free commercial and production use
- ✅ **Redistribution**: Permitted without fees
- ✅ **Open Source Context**: Compatible with open-source projects
- ⚠️ **JDK Version**: JDK 21/24 under GFTC, JDK 17.0.13+ under OTN license

**Tasks:**
- [ ] **3.1** License compliance verification
  - Review GFTC license terms for project compatibility
  - Document license compliance in project README
  - **Verification**: Legal compliance documented, commit license updates
- [ ] **3.2** Oracle GraalVM integration
  - Update CI/CD to use Oracle GraalVM instead of Mandrel
  - Modify Docker builds to use Oracle GraalVM base images
  - **Build & Verify**: Native build succeeds with Oracle GraalVM, commit changes
- [ ] **3.3** PGO implementation workflow
  - Create instrumented native image: `--pgo-instrument`
  - Run representative workload to generate profile (`default.iprof`)
  - Build optimized native image: `--pgo=default.iprof`
  - **Build & Verify**: PGO workflow completes successfully, commit PGO configuration
- [ ] **3.4** Automated PGO pipeline
  - Script to automate: instrument → profile → optimize workflow
  - Integration with benchmark suite for profile generation
  - **Build & Verify**: Automated pipeline runs successfully, commit automation scripts
- [ ] **3.5** Performance validation
  - Compare Mandrel vs Oracle GraalVM performance
  - Measure PGO vs non-PGO performance impact
  - **Verification**: Performance improvements documented and committed

**Expected Impact**: 15-30% performance improvement
**Effort**: Medium
**Risk**: Medium (build system changes)

#### 4. Expand Benchmark Coverage ⭐

**Tasks:**
- [ ] **4.1** Higher concurrency testing
  - Test with 16, 32, 64 concurrent threads
  - Identify optimal thread count for different scenarios
  - **Build & Verify**: Each concurrency test completes, commit thread configuration updates
- [ ] **4.2** Latency percentile analysis
  - Add P50, P95, P99 latency measurements
  - Compare latency under different load patterns
  - **Verification**: Latency metrics captured and analyzed, commit benchmark enhancements
- [ ] **4.3** JWT complexity scenarios
  - Test with different JWT sizes (1KB, 4KB, 8KB)
  - Test with multiple claims and nested structures
  - **Build & Verify**: Complex JWT scenarios benchmark successfully, commit test cases
- [ ] **4.4** Error rate impact testing
  - Benchmark performance with 1%, 5%, 10% error rates
  - Measure degradation patterns
  - **Verification**: Error rate impact documented, commit error scenario tests
- [ ] **4.5** Long-running stability testing
  - Extended continuous load testing
  - Memory leak detection and GC performance analysis
  - **Verification**: Stability metrics captured, commit stability test results

**Expected Impact**: Better understanding of performance characteristics
**Effort**: Medium
**Risk**: Low (testing only)

### 🥉 LOW PRIORITY (Incremental Improvements)

#### 5. JWKS Caching Optimization
**Note**: Current implementation already has sophisticated caching with ETag support and background refresh.

**Tasks:**
- [ ] **5.1** Cache hit ratio analysis
  - Measure current cache effectiveness
  - Identify cache miss patterns
- [ ] **5.2** Cache tuning
  - Optimize cache TTL and refresh intervals
  - Implement cache warming strategies

**Expected Impact**: 5-10% improvement (if cache misses are frequent)
**Effort**: Low
**Risk**: Low

#### 6. Memory and GC Optimization

**Tasks:**
- [ ] **6.1** Object allocation profiling
  - Use JFR to identify allocation hotspots
  - Optimize object creation patterns
- [ ] **6.2** Native image memory tuning
  - Optimize heap settings for consistent performance
  - Investigate compressed OOPs impact

**Expected Impact**: 5-15% improvement in resource usage
**Effort**: Medium
**Risk**: Low

## Implementation Strategy

### Phase 1: Quick Wins
1. JFR Profiling Implementation (Task 1)
2. Benchmark Expansion (Task 4)

### Phase 2: Architecture Enhancements
1. Reactive Endpoint Implementation (Task 2)
2. Oracle GraalVM Migration (Task 3)

### Phase 3: Fine-tuning
1. PGO Optimization
2. Cache and Memory Optimizations (Tasks 5-6)

## Success Metrics

- **Target Performance**: 400+ ops/s (60% improvement from optimized baseline)
- **Latency Goals**: P95 < 50ms, P99 < 100ms
- **Resource Efficiency**: Maintain current memory footprint
- **Startup Time**: Keep under 0.3s
- **Stability**: 99.9% uptime under continuous load

## Risk Mitigation

- **Performance Regression**: Maintain current virtual thread implementation as fallback
- **License Issues**: Document GFTC compliance clearly
- **Build Complexity**: Provide migration scripts and detailed documentation
- **Testing**: Implement comprehensive before/after performance testing

---

**Next Action**: Start with Task 1.1 (JFR Profiling) for immediate actionable insights.