# JWT Validation Architecture - Alternative Design Options

## Option A Implementation: Conservative Enhancement

### Overview
Fix specific issues within existing architecture with minimal disruption.

**Pros:**
- Minimal disruption to existing code
- NiFi and Quarkus continue working unchanged
- Faster to implement
- Lower risk

**Cons:**
- Doesn't address architectural complexity
- Multiple state variables remain
- Initialization flow stays complex

### Implementation Details

#### 1. Enhanced HttpJwksLoader (Option A)

```java
// Option A: Enhance existing HttpJwksLoader
public class HttpJwksLoader implements JwksLoader, LoadingStatusProvider {
    private final HttpJwksLoaderConfig config;
    private final ResilientHttpHandler<Jwks> httpHandler;
    private final AtomicReference<JWKSKeyLoader> keyLoader = new AtomicReference<>();
    private final KeyRotationManager rotationManager;

    // Single atomic status - starts as UNDEFINED
    private final AtomicReference<LoaderStatus> status =
        new AtomicReference<>(LoaderStatus.UNDEFINED);

    // Get key with rotation support
    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        // Try current keys
        JWKSKeyLoader current = keyLoader.get();
        if (current != null) {
            Optional<KeyInfo> key = current.getKeyInfo(kid);
            if (key.isPresent()) return key;
        }

        // Try retired keys during grace period
        return rotationManager.findInRetiredKeys(config.getIssuerIdentifier(), kid);
    }

    // Lock-free status check - instant return
    @Override
    public LoaderStatus getLoaderStatus() {
        return status.get();
    }

    // Internal load that updates status atomically
    private void loadKeys() {
        status.set(LoaderStatus.LOADING);

        HttpResultObject<Jwks> result = httpHandler.load();

        if (result.isValid() && result.getResult() != null) {
            JWKSKeyLoader newLoader = createKeyLoader(result.getResult());
            JWKSKeyLoader oldLoader = keyLoader.getAndSet(newLoader);

            // Register old loader for grace period
            if (oldLoader != null) {
                rotationManager.onKeyRotation(
                    config.getIssuerIdentifier(),
                    oldLoader,
                    newLoader
                );
            }

            status.set(LoaderStatus.OK);
        } else if (keyLoader.get() != null) {
            // Keep existing status if we have cached keys
            status.compareAndSet(LoaderStatus.LOADING, LoaderStatus.OK);
        } else {
            status.set(LoaderStatus.ERROR);
        }
    }
}
```

#### 2. KeyRotationManager - Grace Period Support (Issue #110)
```java
// Manages key rotation with grace period to prevent validation failures
public class KeyRotationManager {
    private final Duration gracePeriod;
    private final int maxRetiredKeySets;
    private final ConcurrentHashMap<String, TimestampedKeyLoaders> retiredKeys;

    public KeyRotationManager(Duration gracePeriod, int maxRetiredKeySets) {
        this.gracePeriod = gracePeriod;
        this.maxRetiredKeySets = maxRetiredKeySets;
        this.retiredKeys = new ConcurrentHashMap<>();
    }

    // Called when keys are rotated
    public void onKeyRotation(String issuer, JWKSKeyLoader oldLoader, JWKSKeyLoader newLoader) {
        // Add old loader to retired set with timestamp
        retiredKeys.compute(issuer, (k, v) -> {
            if (v == null) v = new TimestampedKeyLoaders();
            v.add(oldLoader, Instant.now());
            v.cleanupExpired(gracePeriod, maxRetiredKeySets);
            return v;
        });
    }

    // Check retired keys during lookup
    public Optional<KeyInfo> findInRetiredKeys(String issuer, String kid) {
        TimestampedKeyLoaders retired = retiredKeys.get(issuer);
        if (retired != null) {
            return retired.findKey(kid, gracePeriod);
        }
        return Optional.empty();
    }

    // Periodic cleanup of expired keys
    public void cleanupExpiredKeys() {
        retiredKeys.forEach((issuer, loaders) ->
            loaders.cleanupExpired(gracePeriod, maxRetiredKeySets));
    }

    private static class TimestampedKeyLoaders {
        private final LinkedList<TimestampedLoader> loaders = new LinkedList<>();

        void add(JWKSKeyLoader loader, Instant timestamp) {
            loaders.addFirst(new TimestampedLoader(loader, timestamp));
        }

        Optional<KeyInfo> findKey(String kid, Duration gracePeriod) {
            Instant cutoff = Instant.now().minus(gracePeriod);
            return loaders.stream()
                .filter(tl -> tl.timestamp.isAfter(cutoff))
                .map(tl -> tl.loader.getKeyInfo(kid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        }

        void cleanupExpired(Duration gracePeriod, int maxCount) {
            Instant cutoff = Instant.now().minus(gracePeriod);
            loaders.removeIf(tl -> tl.timestamp.isBefore(cutoff));
            while (loaders.size() > maxCount) {
                loaders.removeLast();
            }
        }
    }

    record TimestampedLoader(JWKSKeyLoader loader, Instant timestamp) {}
}
```

#### 3. JwksHttpContentConverter - Data Isolation Layer
```java
// Isolates all JWKS-specific data handling and parsing
public class JwksHttpContentConverter implements HttpContentConverter<Jwks> {

    @Override
    public HttpResponse.BodyHandler<?> getBodyHandler() {
        return HttpResponse.BodyHandlers.ofString();
    }

    @Override
    public Optional<Jwks> convert(Object rawContent) {
        // All JWKS-specific parsing logic here
        // Handles malformed JSON, missing fields, etc.
        // Returns Optional.empty() for invalid content
        return parseJwks((String) rawContent);
    }

    @Override
    public Jwks emptyValue() {
        // Semantically correct empty JWKS
        return Jwks.empty();
    }

    // Future enhancements can add:
    // - Schema validation
    // - Key filtering
    // - Transform/normalization
}
```

#### 4. Quarkus Integration Layer (cui-jwt-quarkus module)
```java
@ApplicationScoped
public class TokenValidatorProducer {

    @Inject
    ManagedExecutor managedExecutor;

    @Inject
    Config config;

    @PostConstruct
    void init() {
        // Configure key rotation grace period globally
        KeyRotationManager.setDefaultGracePeriod(
            config.getValue("jwt.key-rotation.grace-period", Duration.class)
        );

        // Existing initialization continues...
        // Each HttpJwksLoader created with rotation support built-in
    }

    @Produces
    @ApplicationScoped
    public TokenValidator produceTokenValidator(List<IssuerConfig> issuerConfigs) {
        // TokenValidator uses enhanced HttpJwksLoaders
        return TokenValidator.builder()
            .issuerConfigs(issuerConfigs)
            .build();
    }
}

// Health check integration - simplified
@ApplicationScoped
@Readiness
public class JwksEndpointHealthCheck implements HealthCheck {

    @Inject
    List<IssuerConfig> issuerConfigs;

    @Override
    public HealthCheckResponse call() {
        // Simple lock-free status check for each issuer
        boolean allHealthy = issuerConfigs.stream()
            .map(config -> config.getJwksLoader())
            .allMatch(loader -> loader.getLoaderStatus() == LoaderStatus.OK);

        // Count statuses for diagnostics
        long okCount = issuerConfigs.stream()
            .map(config -> config.getJwksLoader().getLoaderStatus())
            .filter(status -> status == LoaderStatus.OK)
            .count();

        return HealthCheckResponse.named("jwks-endpoints")
            .status(allHealthy)
            .withData("healthy", okCount)
            .withData("total", issuerConfigs.size())
            .build();
    }
}
```

#### 5. NiFi Integration (in nifi-extensions)
```java
// Option A: NiFi continues to work with minimal changes
public class MultiIssuerJWTTokenAuthenticator {

    @OnScheduled
    public void onScheduled(ProcessContext context) {
        // Configure key rotation if desired
        Duration gracePeriod = Duration.parse(
            context.getProperty("key.rotation.grace.period").getValue()
        );

        // Create validator - HttpJwksLoaders now have rotation built-in
        TokenValidator validator = TokenValidator.builder()
            .issuerConfigs(parseIssuerConfigs(context))
            .build();
    }
}
```

### Implementation Steps for Option A

1. **Add recovery mechanism for failed well-known discovery**
   - Add retry loop in `HttpJwksLoader.ensureHttpCache()`
   - Schedule retry if well-known discovery fails initially

2. **Unify status methods with lock-free implementation**
   - Keep only `getLoaderStatus()` from LoadingStatusProvider interface
   - Remove duplicate `getCurrentStatus()`
   - Add `AtomicReference<LoaderStatus>` for instant status checks

3. **Add KeyRotationManager for grace period**
   - Implement Issue #110 requirements
   - Configurable grace period for retired keys
   - Automatic cleanup of expired keys

4. **Keep everything else as-is**
   - Minimal disruption to existing code
   - Preserve current initialization flow
   - Maintain backward compatibility where possible

## Option C Implementation: Hybrid Approach

### Overview
Major improvements without full redesign. Implement key architectural improvements while maintaining compatibility.

**Strategy:**
1. Enhanced HttpJwksLoader with cleaner internals
2. Optional orchestrator for advanced use cases
3. Gradual migration path

### Implementation Details

#### 1. Enhanced Components with Migration Path
```java
// Option C: Enhanced loader with optional orchestrator support
public class HttpJwksLoader implements JwksLoader, LoadingStatusProvider {
    // Can work standalone (backward compatible)
    // OR register with orchestrator (new pattern)

    private final HttpJwksLoaderConfig config;
    private final AtomicReference<LoaderStatus> status;
    private final KeyRotationManager rotationManager;
    private JwksLoadingOrchestrator orchestrator; // Optional

    public HttpJwksLoader(HttpJwksLoaderConfig config) {
        this.config = config;
        this.status = new AtomicReference<>(LoaderStatus.UNDEFINED);
        this.rotationManager = new KeyRotationManager();
    }

    // New method for orchestrator integration
    public void registerWithOrchestrator(JwksLoadingOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        // Delegate lifecycle to orchestrator
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        if (orchestrator != null) {
            // New pattern: delegate to orchestrator
            return orchestrator.getKeyInfo(config.getIssuerIdentifier(), kid);
        } else {
            // Legacy pattern: self-contained operation
            return getKeyInfoDirect(kid);
        }
    }

    // Supports both patterns
    @Override
    public LoaderStatus getLoaderStatus() {
        return orchestrator != null
            ? orchestrator.getIssuerStatus(config.getIssuerIdentifier())
            : status.get();
    }
}
```

#### 2. Optional Orchestrator for Advanced Use Cases
```java
// Option C: Optional orchestrator - use only when needed
public class JwksLoadingOrchestrator {
    private final Map<String, ManagedLoader> loaders;
    private final ScheduledExecutorService scheduler;

    // Advanced features when using orchestrator:
    // - Centralized retry logic
    // - Coordinated refresh scheduling
    // - Cross-issuer key sharing
    // - Advanced metrics and monitoring

    public void manage(String issuer, HttpJwksLoader loader) {
        // Take over lifecycle management
        loaders.put(issuer, new ManagedLoader(loader));
        scheduleRefresh(issuer);
    }

    // Centralized operations
    public Optional<KeyInfo> getKeyInfo(String issuer, String kid) {
        // Can implement advanced features:
        // - Cross-issuer key lookup
        // - Fallback strategies
        // - Load balancing
        return loaders.get(issuer).getKeyInfo(kid);
    }
}
```

#### 3. Gradual Migration Strategy
```java
// Phase 1: Use enhanced loaders without orchestrator
TokenValidator validator1 = TokenValidator.builder()
    .issuerConfigs(enhancedIssuerConfigs) // Works today
    .build();

// Phase 2: Opt-in to orchestrator for specific use cases
JwksLoadingOrchestrator orchestrator = new JwksLoadingOrchestrator();
enhancedIssuerConfigs.forEach(config ->
    config.getJwksLoader().registerWithOrchestrator(orchestrator)
);

// Phase 3: Full orchestrator adoption (if beneficial)
TokenValidator validator3 = TokenValidator.builder()
    .jwksOrchestrator(orchestrator) // Future API
    .build();
```

### Migration Benefits

1. **No Breaking Changes Initially**
   - Existing code continues to work
   - Opt-in to new features

2. **Gradual Adoption**
   - Teams can migrate at their own pace
   - Test new patterns in non-critical paths first

3. **Feature Discovery**
   - Learn which orchestrator features are valuable
   - Drop unnecessary complexity

4. **Risk Mitigation**
   - Rollback is easy
   - A/B testing possible

## Decision Matrix

| Criterion | Option A (Conservative) | Option B (Clean Redesign) | Option C (Hybrid) |
|-----------|-------------------------|---------------------------|-------------------|
| Implementation Time | Low (1-2 weeks) | High (3-4 weeks) | Medium (2-3 weeks) |
| Risk | Low | Medium | Low-Medium |
| Breaking Changes | None | Yes | Optional |
| Code Clarity | Moderate | High | High |
| Future Flexibility | Moderate | High | High |
| Testing Effort | Low | High | Medium |
| Migration Effort | None | High | Gradual |
| NiFi Impact | None | Requires changes | Optional changes |
| Quarkus Impact | Minimal | Requires changes | Optional changes |

## Recommendation Decision Criteria

### Choose Option A if:
- Time is critical (need fixes within 1-2 weeks)
- Stability is paramount
- Team is not ready for architectural changes
- Want to minimize risk
- Current architecture is "good enough"

### Choose Option B if:
- Clean architecture is the priority
- Willing to invest in proper redesign
- Team ready for migration
- Want best long-term maintainability
- Can accept 3-4 week timeline

### Choose Option C if:
- Want architectural improvements
- Need gradual transition
- Want to test new patterns safely
- Have diverse deployment scenarios
- Need flexibility in adoption

## Implementation Priorities (All Options)

### Phase 1: Critical Fixes (Week 1)
Applicable to all options:
1. Fix well-known discovery retry mechanism
2. Implement lock-free status checks
3. Unify status methods (remove duplication)

### Phase 2: Key Features (Week 2)
Varies by option:
- **Option A**: Add KeyRotationManager, enhance existing loader
- **Option B**: Implement JwksLoadingOrchestrator
- **Option C**: Create enhanced loader with optional orchestrator

### Phase 3: Integration (Week 3+)
- **Option A**: Minimal integration changes
- **Option B**: Update Quarkus and NiFi integrations
- **Option C**: Gradual migration support

### Phase 4: Testing & Documentation (Ongoing)
All options require:
- Comprehensive test coverage
- Performance benchmarks
- Migration guides
- API documentation

## Summary

The three options provide different trade-offs between effort and benefit:

- **Option A** provides quick fixes with minimal disruption but doesn't address architectural debt
- **Option B** offers the cleanest long-term solution but requires significant migration effort
- **Option C** balances improvements with compatibility, allowing gradual adoption

Given the pre-1.0 status and the importance of framework independence, the team should evaluate their priorities regarding timeline, risk tolerance, and long-term architectural goals to make the best choice.