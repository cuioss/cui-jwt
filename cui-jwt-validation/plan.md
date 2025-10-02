# AccessTokenCache Performance Fix - Issue #132 Follow-up

## Problem Statement

After the refactoring in #132, the `AccessTokenCache` still suffers from a critical performance bottleneck at `AccessTokenCache.java:217` where `ConcurrentHashMap.computeIfAbsent()` is used.

### Root Cause
- `computeIfAbsent` synchronizes on the cache key
- Multiple threads validating the same token **block and wait** for expensive computation (signature validation)
- Under high concurrency with token re-validation, this creates 99.98% throughput degradation
- Example: 150 concurrent threads validating 100 unique tokens causes massive contention

### Current Behavior (BLOCKING)
```java
// Line 217: All threads block on key during expensive validation
CachedToken newCached = cache.computeIfAbsent(cacheKey, key -> {
    AccessTokenContent validated = validationFunction.apply(tokenString); // EXPENSIVE + BLOCKING
    // ... cache wrapping logic
});
```

### Desired Behavior (NON-BLOCKING)
```
1. Check cache (non-blocking read) ✓ Already done at line 184
2. If miss: Compute token OUTSIDE of locks (allow parallel computation)
3. Attempt to store with putIfAbsent (optimistic)
4. If putIfAbsent returns existing value (race): use cached value
5. If putIfAbsent returns null (we won): use our computed value
6. Accept potential duplicate computation - better than blocking
```

## Implementation Plan

### Phase 1: Core Cache Refactoring

- [ ] **Refactor `AccessTokenCache.computeIfAbsent()` method** (line 167-271)
  - [ ] Remove `cache.computeIfAbsent()` pattern (line 217)
  - [ ] Add non-blocking cache miss handling:
    - [ ] Compute token outside of any locks
    - [ ] Use `cache.putIfAbsent()` for optimistic insertion
    - [ ] Handle race condition: prefer cached value if another thread won
    - [ ] Ensure metrics are recorded correctly for both winner and losers
  - [ ] Update method JavaDoc to explain optimistic caching strategy
  - [ ] Note: Cache hit path (line 184-210) already optimized, keep as-is

- [ ] **Review and adjust metrics recording**
  - [ ] Ensure `CACHE_LOOKUP` metrics still accurate
  - [ ] Ensure `CACHE_STORE` metrics account for putIfAbsent race conditions
  - [ ] Consider adding metric for "cache store race lost" scenario
  - [ ] Verify metrics tickers start/stop at correct boundaries

- [ ] **Update cache comments and documentation**
  - [ ] Update class JavaDoc to describe optimistic caching approach
  - [ ] Document the race condition handling and why duplicate computation is acceptable
  - [ ] Update `computeIfAbsent` method JavaDoc on cache hit counting behavior
  - [ ] Add comment explaining performance trade-off (duplicate work vs blocking)

### Phase 2: Testing

- [ ] **Review existing `AccessTokenCacheTest.java`**
  - [ ] Identify tests affected by behavioral change
  - [ ] Ensure concurrency tests cover race conditions
  - [ ] Verify cache hit/miss counting still correct

- [ ] **Add new concurrency stress tests**
  - [ ] Test: Multiple threads validating same token simultaneously
  - [ ] Test: Verify no blocking occurs (measure time, compare to serial execution)
  - [ ] Test: Verify race condition handling (both threads compute, one wins)
  - [ ] Test: Verify cache metrics are accurate under race conditions
  - [ ] Test: 150 concurrent threads, 100 unique tokens (reproduce #131 scenario)
  - [ ] Use `CountDownLatch` or similar to synchronize thread start for maximum contention

- [ ] **Create isolated `AccessTokenValidationPipelineTest.java`**
  - [ ] Test: Full pipeline validation with caching enabled
  - [ ] Test: Full pipeline validation with caching disabled (maxSize=0)
  - [ ] Test: Cache hit path (token already cached)
  - [ ] Test: Cache miss path (token not cached)
  - [ ] Test: Concurrent validation of same token through pipeline
  - [ ] Test: Verify metrics from pipeline integration
  - [ ] Test: Verify security event counters

### Phase 3: Integration and Performance Validation

- [ ] **Run existing concurrency tests**
  - [ ] `TokenValidatorConcurrencyTest.java` - verify still passes
  - [ ] `IssuerConfigResolverConcurrencyTest.java` - verify still passes
  - [ ] Review test output for any timing anomalies

- [ ] **Measure performance improvement**
  - [ ] Create micro-benchmark or use `IssuerConfigResolverPerformanceTest` pattern
  - [ ] Baseline: Current blocking behavior throughput
  - [ ] After fix: Non-blocking behavior throughput
  - [ ] Target: Restore near-original 17,271 ops/s (from #131)
  - [ ] Document results in commit message

- [ ] **Verify shutdown behavior**
  - [ ] Ensure no shutdown deadlock (original #131 issue)
  - [ ] Test graceful shutdown under load
  - [ ] Verify daemon threads terminate properly

### Phase 4: Quality Verification (verifyAndCommit Pattern)

- [ ] **Pre-commit build**
  ```bash
  ./mvnw -Ppre-commit clean verify -pl cui-jwt-validation
  ```
  - [ ] Fix ALL errors and warnings before proceeding
  - [ ] Verify code quality checks pass (checkstyle, spotbugs, PMD)
  - [ ] Ensure formatting compliance

- [ ] **Final verification build**
  ```bash
  ./mvnw clean install -pl cui-jwt-validation
  ```
  - [ ] All tests must pass (may take up to 10 minutes)
  - [ ] No build errors or warnings
  - [ ] Integration tests pass

- [ ] **Artifact cleanup verification**
  ```bash
  find cui-jwt-validation/src -name "*.class" -o -name "*.jar" -o -name "*.war" -o -name "target" -type d
  ```
  - [ ] Verify NO build artifacts in source directories
  - [ ] Clean any artifacts found

- [ ] **Git commit**
  - [ ] Create descriptive commit message explaining the fix
  - [ ] Reference issue #132 follow-up and #131 root cause
  - [ ] Include performance improvement metrics in message
  - [ ] Add Co-Authored-By: Claude footer

## Key Files to Modify

1. **`cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/cache/AccessTokenCache.java`**
   - Line 167-271: `computeIfAbsent()` method - PRIMARY CHANGE
   - Line 217: Remove blocking `cache.computeIfAbsent()`
   - Class JavaDoc: Update with optimistic caching strategy

2. **`cui-jwt-validation/src/test/java/de/cuioss/jwt/validation/cache/AccessTokenCacheTest.java`**
   - Add concurrency stress tests
   - Verify race condition handling

3. **`cui-jwt-validation/src/test/java/de/cuioss/jwt/validation/pipeline/AccessTokenValidationPipelineTest.java`**
   - CREATE NEW TEST FILE
   - Test pipeline independently from TokenValidator

## Success Criteria

✅ **Performance**: Throughput restored to near-original levels (>15,000 ops/s under high concurrency)
✅ **Correctness**: All cache hits return correct tokens, no data corruption
✅ **Concurrency**: No thread blocking on cache operations, race conditions handled gracefully
✅ **Metrics**: Cache metrics accurately reflect hits, misses, and stores
✅ **Tests**: All existing and new tests pass
✅ **Build**: Clean build with pre-commit and full verification
✅ **Shutdown**: No deadlocks, graceful termination

## Technical Notes

### Why Optimistic Caching is Better
- **Blocking approach**: N threads wait for 1 expensive computation → Total time = 1× computation + (N-1)× wait time
- **Optimistic approach**: N threads compute in parallel → Total time = 1× computation, wasted work = (N-1)× computation
- Under high load, wasted parallel work is MUCH cheaper than serial blocking
- Signature validation is CPU-bound, not I/O-bound, so parallel execution on multi-core systems is efficient

### Race Condition Scenario
```
Thread A: cache.get(key) → null
Thread B: cache.get(key) → null
Thread A: validate token (5ms)
Thread B: validate token (5ms)
Thread A: putIfAbsent(key, valueA) → null (won!)
Thread B: putIfAbsent(key, valueB) → valueA (lost, use A's value)
```
Result: Both threads computed, A's value stored, B discards its work. Total time: 5ms (parallel) vs 10ms (serial blocking).

### Metrics Considerations
- `CACHE_LOOKUP`: Record before any cache access
- `CACHE_STORE`: Record only for the thread that successfully stores (putIfAbsent returns null)
- Losing threads in race don't record CACHE_STORE (they didn't store anything)
- Consider adding `CACHE_STORE_RACE_LOST` metric for observability

## References
- **Issue #131**: Original performance collapse bug (99.98% throughput drop)
- **Issue #132**: Pipeline refactoring (completed)
- **AccessTokenValidationPipeline.java:174**: Where cache is called from pipeline
- **AccessTokenCache.java:217**: Current blocking implementation (TO FIX)
