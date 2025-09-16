# JWT Validation Architecture Analysis

## Executive Summary

The current JWT validation architecture has evolved organically, resulting in a complex initialization flow with multiple synchronization issues, redundant loading patterns, and unclear responsibilities. The introduction of `ResilientHttpHandler` improved retry capabilities but didn't address fundamental architectural issues.

**Critical Constraint**: The `cui-jwt-validation` module is used in multiple contexts:
- **Quarkus applications** (current primary use)
- **Apache NiFi processors** (already deployed at `/Users/oliver/git/nifi-extensions`)
- **Other Java applications** (future use cases)

This multi-framework requirement mandates that **all core functionality must reside in the `cui-jwt-validation` module** with no framework dependencies, while Quarkus and other frameworks provide only thin integration layers.

## Current Architecture Analysis

### Component Relationships

```
┌────────────────────────────────────────────────────────────────────────────┐
│                           INITIALIZATION FLOW                              │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Application Startup                                                       │
│      ↓                                                                     │
│  TokenValidatorProducer.init() [@PostConstruct]                          │
│      ├─> Creates IssuerConfigs (via IssuerConfigResolver)                │
│      ├─> Creates SecurityEventCounter                                     │
│      ├─> Initializes each IssuerConfig.initSecurityEventCounter()        │
│      │   └─> HttpJwksLoader.initJWKSLoader(counter) [marks initialized]  │
│      └─> Creates TokenValidator (contains all IssuerConfigs)             │
│                                                                            │
│  JwksStartupService.initializeJwks() [@PostConstruct + @Startup]         │
│      ├─> Injects List<IssuerConfig> (same instances from Producer)       │
│      └─> Triggers async loading via ManagedExecutor                      │
│          └─> For each HttpJwksLoader:                                    │
│              └─> loader.getKeyInfo("startup-trigger")                    │
│                  └─> Triggers first synchronous load                     │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### HttpJwksLoader Loading Patterns

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      HttpJwksLoader LOADING PATTERNS                       │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  Pattern 1: SYNCHRONOUS LOADING (via getKeyInfo)                          │
│  ═══════════════════════════════════════════════════════════              │
│                                                                            │
│  getKeyInfo(kid)                                                          │
│      ↓                                                                     │
│  ensureLoaded() [checks initialized flag]                                │
│      ↓                                                                     │
│  loadKeysIfNeeded() [double-checked locking]                             │
│      ↓                                                                     │
│  loadKeys() [synchronized]                                                │
│      ├─> ensureHttpCache()                                               │
│      │   ├─> [HTTP Type] Creates ResilientHttpHandler directly           │
│      │   └─> [WELL_KNOWN Type] Creates HttpWellKnownResolver             │
│      │       └─> Resolves JWKS URI → Creates ResilientHttpHandler        │
│      ├─> cache.load() [BLOCKING]                                         │
│      ├─> Updates keyLoader if successful                                 │
│      └─> startBackgroundRefreshIfNeeded()                                │
│          └─> Schedules periodic refresh (if not already started)         │
│                                                                            │
│  Pattern 2: ASYNCHRONOUS LOADING (via loadAsync)                          │
│  ════════════════════════════════════════════════════════               │
│                                                                            │
│  loadAsync() [Returns CompletableFuture<LoaderStatus>]                    │
│      ↓                                                                     │
│  CompletableFuture.supplyAsync()                                         │
│      └─> loadKeys() [Same as synchronous path]                          │
│          └─> Returns final LoaderStatus                                  │
│                                                                            │
│  Pattern 3: BACKGROUND REFRESH (Scheduled)                                │
│  ══════════════════════════════════════════════                         │
│                                                                            │
│  ScheduledExecutorService (every N seconds)                              │
│      ↓                                                                     │
│  backgroundRefresh()                                                      │
│      ├─> cache.load() [Uses existing ResilientHttpHandler]              │
│      └─> Updates keyLoader only if fresh data (200 status)              │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### ResilientHttpHandler - Critical Caching & Resilience Layer

```
┌────────────────────────────────────────────────────────────────────────────┐
│              ResilientHttpHandler - PERSISTENT CACHING & RETRY             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  KEY CAPABILITIES:                                                        │
│  ────────────────                                                         │
│  1. ETag-based HTTP caching (304 Not Modified support)                   │
│  2. Persistent cache across failures (returns cached on error)           │
│  3. Retry with exponential backoff via RetryStrategy                     │
│  4. Content conversion via HttpContentConverter<T>                       │
│  5. Thread-safe operations with ReentrantLock                            │
│                                                                            │
│  load() Flow:                                                             │
│  ───────────                                                              │
│  Set LoaderStatus.LOADING                                                 │
│      ↓                                                                     │
│  RetryStrategy.execute(fetchJwksContentWithCache)                         │
│      ├─> Build request with If-None-Match header (if cached ETag)        │
│      ├─> Send HTTP request                                               │
│      └─> Response handling:                                              │
│          ├─> 200 OK → Update cache, return fresh content                 │
│          ├─> 304 Not Modified → Return cached content (no update)        │
│          ├─> 4xx Client Error → No retry, return cached if available     │
│          ├─> 5xx Server Error → Retry with backoff, use cache on fail    │
│          └─> Network Error → Retry with backoff, use cache on fail       │
│      ↓                                                                     │
│  HttpContentConverter<T>.convert() [Data-specific adaptation]             │
│      ↓                                                                     │
│  Update LoaderStatus (OK or ERROR)                                        │
│      ↓                                                                     │
│  Return HttpResultObject<T> [Never null, uses emptyValue() fallback]      │
│                                                                            │
│  CRITICAL FEATURES:                                                       │
│  ─────────────────                                                        │
│  • PERSISTENT CACHE: Survives failures, returns stale data vs. nothing   │
│  • CONTENT ISOLATION: JwksHttpContentConverter handles all JWKS parsing  │
│  • GRACEFUL DEGRADATION: Always returns data if any cached              │
│  • STATEFUL RESILIENCE: Maintains last known good state                 │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Call Flow Sequences

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         STARTUP SEQUENCE TIMELINE                          │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  T0: Application Start                                                     │
│  ══════════════════                                                       │
│                                                                            │
│  T1: CDI Container Initialization                                          │
│      │                                                                     │
│      ├─[SYNC]─> TokenValidatorProducer.init()                           │
│      │          ├─> Create IssuerConfigs                                 │
│      │          ├─> Initialize HttpJwksLoaders                           │
│      │          └─> Build TokenValidator                                 │
│      │                                                                     │
│      └─[SYNC]─> JwksStartupService.initializeJwks()                     │
│                 └─[ASYNC]─> ManagedExecutor.runAsync()                   │
│                            └─> For each IssuerConfig:                    │
│                                └─[ASYNC]─> loadIssuerJwks()              │
│                                                                            │
│  T2: First JWT Validation Request                                          │
│      │                                                                     │
│      └─[SYNC]─> TokenValidator.validate(token)                           │
│                 ├─> Extract issuer from token                            │
│                 ├─> Find matching IssuerConfig                           │
│                 └─> HttpJwksLoader.getKeyInfo(kid)                       │
│                     └─> May block if keys not loaded yet                 │
│                                                                            │
│  T3: Background Refresh (every N seconds)                                  │
│      │                                                                     │
│      └─[ASYNC]─> ScheduledExecutorService                                │
│                  └─> backgroundRefresh()                                 │
│                      └─> Updates keys if changed                         │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Concurrency & Synchronization Issues

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      CONCURRENCY PROBLEM AREAS                             │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. INITIALIZATION RACE CONDITION                                          │
│  ─────────────────────────────────                                        │
│  Thread A: JwksStartupService.loadIssuerJwks()                           │
│      └─> HttpJwksLoader.getKeyInfo("startup-trigger")                    │
│          └─> Checks initialized flag                                     │
│                                                                            │
│  Thread B: First JWT validation request                                   │
│      └─> HttpJwksLoader.getKeyInfo(actualKid)                            │
│          └─> Checks initialized flag                                     │
│                                                                            │
│  Problem: If TokenValidatorProducer hasn't completed initialization,      │
│          both threads throw IllegalStateException                         │
│                                                                            │
│  2. DOUBLE LOADING                                                        │
│  ──────────────                                                           │
│  Thread A: JwksStartupService triggers async load                        │
│      └─> HttpJwksLoader.loadKeys() [acquires lock]                       │
│                                                                            │
│  Thread B: First JWT validation                                           │
│      └─> HttpJwksLoader.loadKeys() [waits for lock]                      │
│          └─> Loads again after Thread A completes                        │
│                                                                            │
│  3. BACKGROUND REFRESH START RACE                                         │
│  ────────────────────────────────                                        │
│  Multiple threads can call startBackgroundRefreshIfNeeded()               │
│  Only first one starts scheduler (AtomicBoolean guard)                    │
│  But: If first load fails, background refresh never starts                │
│                                                                            │
│  4. WELL-KNOWN RESOLVER PERMANENT FAILURE                                 │
│  ─────────────────────────────────────────                                │
│  Initial load fails → WellKnownResolver status = ERROR                    │
│  ensureHttpCache() returns Optional.empty()                              │
│  Background refresh never starts → Permanent failure                      │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

## Identified Problems (Verified Against Code)

### 1. Well-Known Discovery Recovery Issue
- **Location**: HttpJwksLoader.java lines 344-346
- **Clarification**: HttpWellKnownResolver DOES have retry via ResilientHttpHandler (line 341 creates resolver with retry)
- **Real Issue**: If discovery fails after all retries, ensureHttpCache() returns empty → background refresh never starts
- **Impact**: No mechanism to retry discovery later if initially unsuccessful

### 2. Duplicate Status Methods
- **Location**: HttpJwksLoader.java lines 94, 111-112
- **Issue**: `getLoaderStatus()` just calls `getCurrentStatus()`
- **Impact**: Two methods doing exactly the same thing

### 3. Not Lock-Free Status Checks
`getCurrentStatus()` may trigger `ensureHttpCache()` which acquires locks and does I/O.
Health checks need instant response, not potential blocking.

### 4. Multiple Redundant Loading Triggers
- JwksStartupService: `getKeyInfo("startup-trigger")` (line 152)
- First JWT validation: `getKeyInfo(actualKid)`
- Background refresh: `backgroundRefresh()`
All trigger same loading logic with different patterns.

### 5. No Key Rotation Grace Period
When keys rotate, old tokens immediately fail validation.
No mechanism to keep retired keys for a grace period (Issue #110).

### 6. Complex State Management
- `initialized` flag
- `schedulerStarted` flag
- `keyLoader` presence
- `loaderStatus` in both HttpJwksLoader and ResilientHttpHandler
Too many state variables to track.

### 7. Initialization Race Conditions
TokenValidatorProducer and JwksStartupService both access loaders.
If timing is wrong, `IllegalStateException` from uninitialized loader.

## Framework Independence Requirement

**Critical Constraint**: The `cui-jwt-validation` module must work independently in multiple contexts:
- **Quarkus applications** (current primary use)
- **Apache NiFi processors** (already deployed at `/Users/oliver/git/nifi-extensions`)
- **Other Java applications** (future use cases)

This means:
1. Core functionality MUST reside in `cui-jwt-validation` module
2. NO framework-specific dependencies in validation module
3. Quarkus layer must be a THIN integration layer only
4. Solution must work with standard Java (JDK 17+)

### Current NiFi Usage Pattern

NiFi directly uses `cui-jwt-validation` components:
```java
// From MultiIssuerJWTTokenAuthenticator.java
TokenValidator validator = TokenValidator.builder()
    .parserConfig(parserConfig)
    .issuerConfigs(issuerConfigs)
    .build();
```

No Quarkus dependencies, direct builder pattern usage.

## Redesign Goals

- **Framework-agnostic core**: All JWKS loading logic in validation module
- **Standard Java patterns**: Builder pattern, ExecutorService, CompletableFuture
- **Thin framework adapters**: Minimal integration code for Quarkus/NiFi
- **Breaking changes allowed**: Pre-1.0 - no backward compatibility constraints
- **Resilient loading**: Automatic retry and recovery from failures
- **Persistent caching**: ResilientHttpHandler maintains cache across failures
- **Lock-free status checks**: Health checks must be non-blocking and instant
- **Key rotation support**: Grace period for rotated keys (Issue #110)
- **Clear separation**: Distinct components for loading, storage, scheduling, retry