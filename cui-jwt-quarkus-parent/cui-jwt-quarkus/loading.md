# JWKS Loading Analysis - Critical Architecture Issue

## Executive Summary 

**STATUS: ❌ NOT FIXED** - WellKnownResolver has no retry mechanism, creating permanent failure states.

**Root Issue**: WellKnownResolver fails during startup when Keycloak isn't ready → never retries → background refresh permanently disabled → integration tests fail.

## Architecture Flow Analysis

```
STARTUP SEQUENCE:
═══════════════════════════════════════════════════════════════════

Quarkus Startup (0.2s)
    ↓
JwksStartupService.initializeJwks() (immediate)
    ↓
┌─────────────────────────────────────────────────────────────────┐
│ For Each IssuerConfig:                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ ┌─ Integration Issuer (Direct JWKS URL) ────────────────────────┐│
│ │ HttpJwksLoader.getKeyInfo("startup-trigger")                 ││
│ │ ↓                                                            ││
│ │ ETagAwareHttpHandler.load() ← DIRECT to JWKS endpoint        ││
│ │ ↓                                                            ││
│ │ [KEYCLOAK NOT READY] → LoaderStatus.ERROR                    ││
│ │ ↓                                                            ││
│ │ ✅ startBackgroundRefreshIfNeeded() → SUCCESS                ││
│ │    ↓                                                         ││
│ │    Scheduler starts: jwks-refresh-keycloak (10s interval)    ││
│ │    ↓                                                         ││
│ │    [After ~20s] Keycloak ready → ✅ JWKS loaded successfully ││
│ └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│ ┌─ Keycloak Issuer (Well-Known Discovery) ──────────────────────┐│
│ │ HttpJwksLoader.getKeyInfo("startup-trigger")                 ││
│ │ ↓                                                            ││
│ │ HttpJwksLoader.ensureHttpCache()                             ││
│ │ ↓                                                            ││
│ │ WellKnownResolver.isHealthy()                                ││
│ │ ↓                                                            ││
│ │ WellKnownResolver.loadEndpoints()                            ││
│ │ ↓                                                            ││
│ │ ETagAwareHttpHandler.load() ← to WELL-KNOWN endpoint         ││
│ │ ↓                                                            ││
│ │ [KEYCLOAK NOT READY] → java.net.ConnectException             ││
│ │ ↓                                                            ││
│ │ WellKnownResolver.status = ERROR (PERMANENT!)                ││
│ │ ↓                                                            ││
│ │ HttpJwksLoader.ensureHttpCache() → Optional.empty()          ││
│ │ ↓                                                            ││
│ │ ❌ startBackgroundRefreshIfNeeded() → NO-OP                  ││
│ │    (No ScheduledExecutorService created!)                    ││
│ │ ↓                                                            ││
│ │ ❌ PERMANENTLY BROKEN - Never recovers                       ││
│ └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘

RUNTIME BEHAVIOR:
═════════════════════════════════════════════════════════════════

Every 10 seconds:
┌─ Integration Issuer ────────────────────────────────────────────┐
│ jwks-refresh-keycloak thread                                    │
│ → ETagAwareHttpHandler.load()                                   │
│ → ✅ SUCCESS (after Keycloak becomes ready)                     │
│ → Keys updated, JWT validation works                            │
└─────────────────────────────────────────────────────────────────┘

┌─ Keycloak Issuer ──────────────────────────────────────────────┐
│ jwks-refresh-wellknown thread                                   │
│ → backgroundRefresh()                                           │
│ → httpCache.get() == null                                       │
│ → ❌ "Background refresh skipped - no HTTP cache available"     │
│ → Permanently broken, never recovers                            │
└─────────────────────────────────────────────────────────────────┘
```

## Critical Problems Identified

### 1. WellKnownResolver Has No Retry Logic ❌
- **Issue**: Single failed attempt during startup → permanent ERROR status
- **Location**: `HttpWellKnownResolver.loadEndpoints()` 
- **Impact**: Once failed, never tries again

### 2. HttpJwksLoader Background Refresh Dependency ❌  
- **Issue**: Background refresh only works if HTTP cache was created successfully
- **Location**: `HttpJwksLoader.startBackgroundRefreshIfNeeded()`
- **Logic**: `if (config.getScheduledExecutorService() != null)` - but executor is never created if WellKnownResolver fails

### 3. Architecture Inconsistency ❌
- **Direct JWKS URL**: ✅ Always creates HTTP cache → Always gets background refresh
- **Well-Known Discovery**: ❌ Only creates HTTP cache if initial load succeeds → No recovery

## Test Failure Pattern Explained

```
Integration Tests (44 total):
├─ JwtValidationEndpointIntegrationIT (22 tests) 
│  └─ Uses integration issuer (direct JWKS URL)
│  └─ ✅ Run 1: PASS (background refresh works)
│  └─ ✅ Run 2: PASS (background refresh works)
│
└─ JwtValidationEndpointBenchmarkIT (22 tests)
   └─ Uses keycloak issuer (well-known discovery)  
   └─ ❌ Run 1: PASS (uses cached/initial tokens)
   └─ ❌ Run 2: FAIL (tokens expired, no JWKS refresh)
   
RESULT: 37/44 pass, 7/44 fail (the 7 failures are benchmark realm second runs)
```

## Component Analysis

### HttpJwksLoader Retry Behavior
- ✅ **Has background refresh**: 10-second scheduled retry
- ❌ **Conditional on successful initialization**: Only if HTTP cache exists

### WellKnownResolver Retry Behavior  
- ❌ **No background refresh**: Single attempt only
- ❌ **No retry mechanism**: Permanent failure state
- ❌ **No health check recovery**: Once ERROR, always ERROR

### ETagAwareHttpHandler Retry Behavior
- ✅ **Used by both paths**: Consistent HTTP handling
- ✅ **Has proper error handling**: Returns LoadResult with error states
- ❌ **No automatic retry**: Relies on caller for retry logic

## Required Fixes

### Priority 1: Fix WellKnownResolver Retry ⚠️ 
```java
// Current broken logic in HttpJwksLoader.ensureHttpCache():
Optional<ETagAwareHttpHandler> cacheOpt = ensureHttpCache();
if (cacheOpt.isEmpty()) {
    // ❌ NO RETRY - permanently broken
    return;
}

// Required fix - retry WellKnownResolver in background:
private void retryWellKnownIfNeeded() {
    if (wellKnownResolver != null && !wellKnownResolver.isHealthy()) {
        // Retry well-known discovery on background thread
        backgroundExecutor.schedule(() -> {
            wellKnownResolver.retryLoad();
            if (wellKnownResolver.isHealthy()) {
                startBackgroundRefreshIfNeeded();
            }
        }, retryInterval, TimeUnit.SECONDS);
    }
}
```

### Priority 2: Consistent Background Refresh Architecture ⚠️
- Both direct JWKS and well-known discovery should have same retry behavior
- Background refresh should always start, regardless of initial load success
- Failed WellKnownResolver should get its own retry scheduler

### Priority 3: Health Check Integration 💡
- WellKnownResolver needs periodic health check retry
- Integration with existing ScheduledExecutorService pattern
- Proper logging for retry attempts vs permanent failures

## Current Status Summary

| Component | Direct JWKS URL | Well-Known Discovery | Status |
|-----------|----------------|---------------------|---------|
| Initial Load | ❌ Fails (Keycloak not ready) | ❌ Fails (Keycloak not ready) | Expected |
| Background Refresh | ✅ Starts immediately | ❌ Never starts | **BROKEN** |
| Recovery Mechanism | ✅ 10s retry schedule | ❌ No retry | **BROKEN** |  
| Final Result | ✅ Works after ~20s | ❌ Permanently broken | **BROKEN** |
| Integration Tests | ✅ All pass | ❌ 7/22 fail | **BROKEN** |

**Conclusion**: The architecture has a fundamental flaw where well-known discovery has no retry mechanism, making it unsuitable for environments where external services (Keycloak) start after the application.