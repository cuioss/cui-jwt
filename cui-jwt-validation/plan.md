# Resilient HTTP Handler Implementation Plan

## Executive Summary

**Objective**: Implement a resilient HTTP handler with exponential backoff retry capability to solve the WellKnownResolver permanent failure issue and improve JWKS loading reliability.

**Current Problem**: ETagAwareHttpHandler has no retry mechanism → WellKnownResolver fails permanently → Background refresh never starts → Integration tests fail.

**Solution**: Create pluggable retry architecture with framework-agnostic interface, supporting exponential backoff with jitter.

## Task Processing & Completion ⚡ MANDATORY PROCESS

**ALWAYS ONE TASK AT A TIME**: Follow this exact sequence for EVERY task:

### 1. 🔧 **Implement**
- Write the code/feature for ONE specific task only
- Focus on single functionality - no multi-task implementation
- Complete the implementation fully before moving to next step

### 2. 🧪 **Test**  
- Create and run tests to verify the implementation works
- Unit tests for new components
- Integration tests where applicable
- Verify expected behavior and edge cases

### 3. ✅ **Verify**
- **MANDATORY**: Run quality checks: `./mvnw -Ppre-commit clean verify -pl <module>`
- **Fix ALL errors and warnings** - no exceptions
- Address code quality, formatting, and linting issues
- **Never commit with warnings** - fix or suppress with justification
- **Zero tolerance** for quality check failures

### 4. 📝 **Document Status/Progress**
- Update implementation status in this plan document
- Mark completed tasks with ✅
- Update any architectural decisions or learnings
- Note any issues or blockers encountered

### 5. 💾 **Commit** 
- Create focused commit with proper descriptive message
- **Only commit after ALL steps 1-4 are completed**
- Include Co-Authored-By: Claude footer
- One task = one commit (generally)

**🚨 CRITICAL RULES:**
- **NEVER skip verification step** - quality checks are mandatory
- **NEVER commit with failing builds** - fix all issues first  
- **NEVER work on multiple tasks simultaneously** - complete full cycle per task
- **ALWAYS fix warnings and errors** - no technical debt accumulation

## Package Structure & Future Migration

**Target Package**: `de.cuioss.tools.net.http` (and sub-packages)
**Migration Path**: Code will be moved to `java-tools` project after implementation
**Isolation Strategy**: Keep all retry/resilience code separate from JWT-specific logic

### Proposed Package Layout
```
de.cuioss.tools.net.http/
├── retry/
│   ├── RetryStrategy.java
│   ├── RetryContext.java  
│   ├── ExponentialBackoffRetryStrategy.java
│   └── RetryException.java
├── resilient/
│   ├── ResilientHttpHandler.java
│   └── ResilientETagAwareHttpHandler.java
└── util/
    └── RetryUtils.java (if needed)
```

### Current Problem Analysis

```
BROKEN: Well-Known Discovery Flow
═════════════════════════════════

HttpWellKnownResolver.loadEndpoints()
    ↓
ETagAwareHttpHandler.load() (single attempt)
    ↓
[Keycloak not ready] → ConnectException
    ↓
result.content() == null → status = LoaderStatus.ERROR
    ↓ 
❌ PERMANENT FAILURE - Never retries again
```

**vs**

```
WORKING: Direct JWKS Flow  
═════════════════════════

HttpJwksLoader.getKeyInfo()
    ↓
ETagAwareHttpHandler.load() (single attempt fails)
    ↓
startBackgroundRefreshIfNeeded() → ✅ STARTS ANYWAY
    ↓
Background thread retries every 10s → ✅ Eventually succeeds
```

### The Core Issue

**HttpWellKnownResolver.loadEndpoints():140-148** sets permanent ERROR status on first failure:
```java
ETagAwareHttpHandler.LoadResult result = etagHandler.load();
if (result.content() == null) {
    this.status = LoaderStatus.ERROR;  // ❌ PERMANENT!
    LOGGER.error(...);
    return;
}
```

**No retry mechanism exists** - unlike HttpJwksLoader which has background refresh.

## Custom Implementation Approach (🎯 CHOSEN)

**Decision**: Create custom retry architecture with framework-agnostic interface, supporting exponential backoff with jitter.

**Why**: 
- Zero external dependencies
- Perfect fit for existing architecture
- Framework-agnostic interface design
- Maximum flexibility and control
- Easy to unit test and mock

### Proposed Architecture

#### 1. Core Retry Interface

```java
package de.cuioss.tools.net.http.retry;

/**
 * Framework-agnostic retry strategy interface.
 * Implementations can be provided by different frameworks (Quarkus, Spring, etc.)
 * or custom implementations for specific retry behaviors.
 */
@FunctionalInterface
public interface RetryStrategy {
    
    /**
     * Executes the given supplier with retry logic.
     *
     * @param supplier the operation to retry
     * @param context retry context with operation name and attempt info
     * @return result of successful operation
     * @throws Exception if all retry attempts fail
     */
    <T> T execute(Supplier<T> supplier, RetryContext context) throws Exception;
    
    /**
     * Creates a no-op retry strategy (single attempt only).
     */
    static RetryStrategy none() {
        return (supplier, context) -> supplier.get();
    }
    
    /**
     * Creates exponential backoff retry strategy with sensible defaults.
     */
    static RetryStrategy exponentialBackoff() {
        return ExponentialBackoffRetryStrategy.builder().build();
    }
}
```

#### 2. Retry Context

```java
package de.cuioss.tools.net.http.retry;

/**
 * Context information for retry operations.
 */
public record RetryContext(String operationName, int attemptNumber, Throwable lastException) {
}
```

#### 3. Exponential Backoff Implementation

```java
package de.cuioss.tools.net.http.retry;

/**
 * Exponential backoff retry strategy with jitter to prevent thundering herd.
 * 
 * Algorithm based on AWS Architecture Blog recommendations:
 * - Base delay starts at initialDelay
 * - Each retry multiplies by backoffMultiplier
 * - Random jitter applied: delay * (1 ± jitterFactor)
 * - Maximum delay capped at maxDelay
 * - Total attempts limited by maxAttempts
 */
public class ExponentialBackoffRetryStrategy implements RetryStrategy {
    
    private final int maxAttempts;
    private final Duration initialDelay;
    private final double backoffMultiplier;
    private final Duration maxDelay;
    private final double jitterFactor;
    private final Predicate<Throwable> retryPredicate;
    
    @Override
    public <T> T execute(Supplier<T> supplier, RetryContext context) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (Exception e) {
                lastException = e;
                
                if (!retryPredicate.test(e) || attempt == maxAttempts) {
                    throw e;
                }
                
                Duration delay = calculateDelay(attempt);
                LOGGER.debug("Attempt {}/{} failed for {}: {}. Retrying in {}ms", 
                    attempt, maxAttempts, context.operationName(), e.getMessage(), delay.toMillis());
                
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
        
        throw new RuntimeException("All retry attempts failed for " + context.operationName(), lastException);
    }
    
    private Duration calculateDelay(int attemptNumber) {
        // Exponential backoff: initialDelay * (backoffMultiplier ^ (attempt - 1))
        double exponentialDelay = initialDelay.toMillis() * Math.pow(backoffMultiplier, attemptNumber - 1);
        
        // Apply jitter: delay * (1 ± jitterFactor)
        double jitter = 1.0 + (2.0 * Math.random() - 1.0) * jitterFactor;
        long delayMs = Math.round(exponentialDelay * jitter);
        
        // Cap at maximum delay
        return Duration.ofMillis(Math.min(delayMs, maxDelay.toMillis()));
    }
    
    public static class Builder {
        private int maxAttempts = 5;
        private Duration initialDelay = Duration.ofSeconds(1);
        private double backoffMultiplier = 2.0;
        private Duration maxDelay = Duration.ofMinutes(1);
        private double jitterFactor = 0.1; // ±10% jitter
        private Predicate<Throwable> retryPredicate = ExponentialBackoffRetryStrategy::isRetryableException;
        
        // Builder methods...
        
        public ExponentialBackoffRetryStrategy build() {
            return new ExponentialBackoffRetryStrategy(maxAttempts, initialDelay, backoffMultiplier, 
                maxDelay, jitterFactor, retryPredicate);
        }
    }
    
    /**
     * Determines if an exception should trigger a retry.
     */
    private static boolean isRetryableException(Throwable throwable) {
        return throwable instanceof ConnectException ||
               throwable instanceof SocketTimeoutException ||
               throwable instanceof HttpConnectTimeoutException ||
               (throwable instanceof IOException && !(throwable instanceof FileNotFoundException));
    }
}
```

#### 4. Replace ETagAwareHttpHandler (Breaking Change)

```java
package de.cuioss.tools.net.http.resilient;

/**
 * BREAKING CHANGE: Replace existing ETagAwareHttpHandler with retry-capable version.
 * All existing code will be updated to use this implementation.
 */
public class ETagAwareHttpHandler {
    
    private final HttpHandler httpHandler;
    private final RetryStrategy retryStrategy;
    private final ReentrantLock lock = new ReentrantLock();
    
    // ... existing cache fields ...
    
    public ETagAwareHttpHandler(HttpHandler httpHandler, RetryStrategy retryStrategy) {
        this.httpHandler = httpHandler;
        this.retryStrategy = Objects.requireNonNull(retryStrategy, "retryStrategy");
    }
    
    /**
     * Loads HTTP content with retry logic applied.
     */
    public LoadResult load() {
        lock.lock();
        try {
            RetryContext context = new RetryContext("http-load-" + httpHandler.getUrl(), 0, null);
            
            HttpFetchResult result = retryStrategy.execute(
                this::fetchJwksContentWithCache, 
                context
            );
            
            if (result.error) {
                return handleErrorResult();
            }
            
            return result.notModified ? handleNotModifiedResult() : handleSuccessResult(result);
            
        } catch (Exception e) {
            LOGGER.error("All retry attempts failed for {}: {}", httpHandler.getUrl(), e.getMessage(), e);
            return new LoadResult(cachedContent, cachedContent != null ? LoadState.ERROR_WITH_CACHE : LoadState.ERROR_NO_CACHE);
        } finally {
            lock.unlock();
        }
    }
    
    // ... rest of existing implementation unchanged ...
}
```

## Quarkus CDI Integration

### Current Architecture Analysis

The Quarkus module uses CDI producers to create JWT validation components:

**Key Files:**
- `TokenValidatorProducer.java` - Main CDI producer for JWT components
- `IssuerConfigResolver.java` - Resolves IssuerConfig from properties  
- `JwtPropertyKeys.java` - Property key constants
- `config/` package - Configuration resolvers

### Integration Requirements

#### 1. RetryStrategy Producer

**Location:** `cui-jwt-quarkus/src/main/java/de/cuioss/jwt/quarkus/producer/TokenValidatorProducer.java`

**Changes:**
```java
@ApplicationScoped  
@RegisterForReflection(fields = true, methods = false)
public class TokenValidatorProducer {
    
    // Add new producer field
    @Produces
    @ApplicationScoped
    @NonNull
    RetryStrategy retryStrategy;
    
    @PostConstruct
    void init() {
        // ... existing code ...
        
        // Create RetryStrategy from configuration
        RetryStrategyConfigResolver retryResolver = new RetryStrategyConfigResolver(config);
        retryStrategy = retryResolver.resolveRetryStrategy();
        
        // Pass to IssuerConfigResolver
        IssuerConfigResolver issuerConfigResolver = new IssuerConfigResolver(config, retryStrategy);
        issuerConfigs = issuerConfigResolver.resolveIssuerConfigs();
        
        // ... rest of existing code ...
    }
}
```

#### 2. RetryStrategy Configuration Resolver

**Location:** `cui-jwt-quarkus/src/main/java/de/cuioss/jwt/quarkus/config/RetryStrategyConfigResolver.java`

**New File:**
```java
package de.cuioss.jwt.quarkus.config;

import de.cuioss.tools.net.http.retry.RetryStrategy;
import de.cuioss.tools.net.http.retry.ExponentialBackoffRetryStrategy;
import org.eclipse.microprofile.config.Config;

/**
 * Resolver for creating RetryStrategy instances from Quarkus configuration.
 */
public class RetryStrategyConfigResolver {
    
    private final Config config;
    
    public RetryStrategyConfigResolver(Config config) {
        this.config = config;
    }
    
    public RetryStrategy resolveRetryStrategy() {
        // Check if retry is disabled globally
        boolean retryEnabled = config.getOptionalValue(JwtPropertyKeys.RETRY.ENABLED, Boolean.class)
                .orElse(true);
        
        if (!retryEnabled) {
            return RetryStrategy.none();
        }
        
        // Build exponential backoff strategy from properties
        return ExponentialBackoffRetryStrategy.builder()
                .maxAttempts(config.getOptionalValue(JwtPropertyKeys.RETRY.MAX_ATTEMPTS, Integer.class)
                        .orElse(5))
                .initialDelay(Duration.ofMillis(config.getOptionalValue(JwtPropertyKeys.RETRY.INITIAL_DELAY_MS, Long.class)
                        .orElse(1000L)))
                .maxDelay(Duration.ofMillis(config.getOptionalValue(JwtPropertyKeys.RETRY.MAX_DELAY_MS, Long.class)
                        .orElse(30000L)))
                .backoffMultiplier(config.getOptionalValue(JwtPropertyKeys.RETRY.BACKOFF_MULTIPLIER, Double.class)
                        .orElse(2.0))
                .jitterFactor(config.getOptionalValue(JwtPropertyKeys.RETRY.JITTER_FACTOR, Double.class)
                        .orElse(0.1))
                .build();
    }
}
```

#### 3. Property Keys Extension

**Location:** `cui-jwt-quarkus/src/main/java/de/cuioss/jwt/quarkus/config/JwtPropertyKeys.java`

**Addition:**
```java
@UtilityClass
public final class JwtPropertyKeys {
    // ... existing content ...
    
    /**
     * Properties related to HTTP retry configuration.
     */
    @UtilityClass
    public static final class RETRY {
        private static final String PREFIX_RETRY = PREFIX + ".retry";
        
        /**
         * Whether retry is enabled globally.
         * Default: true
         */
        public static final String ENABLED = PREFIX_RETRY + ".enabled";
        
        /**
         * Maximum number of retry attempts.
         * Default: 5
         */
        public static final String MAX_ATTEMPTS = PREFIX_RETRY + ".max-attempts";
        
        /**
         * Initial retry delay in milliseconds.
         * Default: 1000
         */
        public static final String INITIAL_DELAY_MS = PREFIX_RETRY + ".initial-delay-ms";
        
        /**
         * Maximum retry delay in milliseconds.
         * Default: 30000
         */
        public static final String MAX_DELAY_MS = PREFIX_RETRY + ".max-delay-ms";
        
        /**
         * Exponential backoff multiplier.
         * Default: 2.0
         */
        public static final String BACKOFF_MULTIPLIER = PREFIX_RETRY + ".backoff-multiplier";
        
        /**
         * Jitter factor for randomization.
         * Default: 0.1
         */
        public static final String JITTER_FACTOR = PREFIX_RETRY + ".jitter-factor";
    }
}
```

#### 4. IssuerConfigResolver Update

**Location:** `cui-jwt-quarkus/src/main/java/de/cuioss/jwt/quarkus/config/IssuerConfigResolver.java`

**Changes:**
```java
public class IssuerConfigResolver {
    
    private final Config config;
    private final RetryStrategy retryStrategy;  // NEW
    
    public IssuerConfigResolver(Config config, RetryStrategy retryStrategy) {
        this.config = config;
        this.retryStrategy = retryStrategy;  // NEW
    }
    
    // In HttpJwksLoaderConfig creation:
    HttpJwksLoaderConfig httpJwksConfig = HttpJwksLoaderConfig.builder()
            .jwksUrl(jwksUrl)
            .retryStrategy(retryStrategy)  // NEW - mandatory
            .scheduledExecutorService(managedExecutorService)
            // ... existing configuration ...
            .build();
}
```

#### 5. Configuration Examples

**application.properties:**
```properties
# Global retry configuration
cui.jwt.retry.enabled=true
cui.jwt.retry.max-attempts=5
cui.jwt.retry.initial-delay-ms=1000
cui.jwt.retry.max-delay-ms=30000
cui.jwt.retry.backoff-multiplier=2.0
cui.jwt.retry.jitter-factor=0.1

# Per-issuer override (if needed in future)
cui.jwt.issuers.keycloak.retry.max-attempts=10
cui.jwt.issuers.keycloak.retry.initial-delay-ms=500
```

**Integration Test Configuration:**
```properties
# Aggressive retry for integration tests
cui.jwt.retry.max-attempts=10
cui.jwt.retry.initial-delay-ms=500
cui.jwt.retry.max-delay-ms=30000
cui.jwt.retry.backoff-multiplier=1.8
cui.jwt.retry.jitter-factor=0.15
```

### Breaking Changes Impact

#### TokenValidatorProducer
- **Constructor signature unchanged** - no breaking change
- **New producer field** - additive change
- **Existing CDI injection points** - unaffected

#### IssuerConfigResolver  
- **Constructor requires RetryStrategy** - breaking change (internal API)
- **Only called from TokenValidatorProducer** - contained impact
- **Public API unchanged** - no external impact

#### HttpJwksLoaderConfig
- **Constructor requires RetryStrategy** - breaking change
- **All creation sites updated** - controlled breaking change
- **Builder pattern maintained** - consistent API

### CDI Lifecycle

```
Quarkus Startup
    ↓
TokenValidatorProducer.@PostConstruct
    ↓
RetryStrategyConfigResolver.resolveRetryStrategy()
    ↓ 
@Produces RetryStrategy (CDI managed)
    ↓
IssuerConfigResolver(config, retryStrategy)
    ↓
HttpJwksLoaderConfig.builder().retryStrategy(retryStrategy)
    ↓
WellKnownResolver with retry-capable ETagAwareHttpHandler
    ↓
@Produces List<IssuerConfig> (CDI managed)
    ↓
JwksStartupService.@Inject List<IssuerConfig>
    ↓
Background refresh with retry capability
```

## Implementation Phases (Breaking Changes)

### Phase 1: Core Retry Infrastructure ✅ COMPLETED
- [x] Implement `RetryStrategy` interface (HTTP-specific with proper throws clause)
- [x] Implement `ExponentialBackoffRetryStrategy` with comprehensive unit tests (15 test cases)
- [x] Create `RetryContext` and supporting classes (`RetryException`, `RetryMetrics`)
- [x] Add comprehensive logging and metrics (`JwtRetryMetrics` with TokenValidatorMonitor integration)
- [x] **BREAKING CHANGE**: Replaced generic `Supplier<T>` with HTTP-specific `HttpOperation<T>` interface
- [x] **COMPLIANCE**: Fixed exception handling - no generic exceptions, only IOException/InterruptedException
- [x] **COMPLIANCE**: Used CuiLogger with structured LogRecords (INFO/WARN only, DEBUG uses direct strings)
- [x] **COMPLIANCE**: Used ScheduledExecutorService instead of Thread.sleep() for Quarkus compatibility
- [x] **TESTING**: All 1,253 tests pass, comprehensive coverage of retry scenarios
- [x] **QUALITY**: Pre-commit build passes with zero errors/warnings

**✨ Implementation Details:**
- **Package**: `de.cuioss.tools.net.http.retry` (ready for migration to java-tools)
- **Core Classes**: `RetryStrategy`, `HttpOperation<T>`, `ExponentialBackoffRetryStrategy`, `RetryContext`, `RetryException`, `RetryMetrics`
- **HTTP-Focused**: Only handles `IOException`/`InterruptedException` that HTTP operations actually throw
- **Exception Handling**: `IOException` wrapped in `RetryException`, `InterruptedException` propagated directly
- **Metrics Integration**: `JwtRetryMetrics` integrates with existing `TokenValidatorMonitor`
- **Configuration**: Builder pattern with sensible defaults (5 attempts, 1s initial delay, 2.0 multiplier, ±10% jitter)
- **Thread Safety**: Uses `ReentrantLock` for virtual thread compatibility, `ScheduledExecutorService` for delays

### Phase 2: Replace HTTP Handler (BREAKING)
- [ ] **Replace** `ETagAwareHttpHandler` with retry-capable version
- [ ] **Update all constructors** to require `RetryStrategy`
- [ ] **Break existing APIs** - no backward compatibility
- [ ] Update all tests to use new constructor signature

### Phase 3: Update All Configurations (BREAKING)
- [ ] **Make RetryStrategy mandatory** in `HttpJwksLoaderConfig`
- [ ] **Remove optional retry patterns** entirely
- [ ] **Update all existing code** to provide retry strategies
- [ ] **Break configuration APIs** that don't include retry

### Phase 4: Quarkus CDI Integration
- [ ] **Create RetryStrategyConfigResolver** in `cui-jwt-quarkus/src/main/java/de/cuioss/jwt/quarkus/config/RetryStrategyConfigResolver.java`
- [ ] **Add RetryStrategy producer** to `TokenValidatorProducer.java`
- [ ] **Update IssuerConfigResolver** to inject and use RetryStrategy
- [ ] **Add retry property keys** to `JwtPropertyKeys.java`
- [ ] **Update HttpJwksLoaderConfig/WellKnownConfig** constructors to accept RetryStrategy

### Phase 5: Integration & Testing  
- [ ] Update integration tests for new behavior
- [ ] Verify 100% test pass rate
- [ ] Performance testing and optimization
- [ ] Clean up removed/deprecated code

## Success Criteria

### Functional Requirements ✅
- [ ] Well-known discovery recovers from startup failures  
- [ ] Background refresh works for all issuer types
- [ ] Integration tests achieve 100% pass rate
- [ ] No permanent failure states

### Non-Functional Requirements ✅
- [ ] Zero additional runtime dependencies
- [ ] Quarkus native image compatibility
- [ ] **Clean APIs without legacy constraints**
- [ ] Comprehensive logging and observability
- [ ] Thread-safe and virtual-thread compatible

### Performance Requirements ✅
- [ ] Recovery time under 30 seconds for typical scenarios
- [ ] No significant impact on successful operations
- [ ] Memory usage within acceptable bounds
- [ ] Proper resource cleanup and exception handling

## Summary

This plan provides a **comprehensive, breaking-change approach** to implementing resilient HTTP operations. Since we are pre-1.0:

✅ **No backward compatibility constraints**  
✅ **Clean architectural decisions**  
✅ **Mandatory resilience by default**  
✅ **Simplified APIs without legacy baggage**  

**Expected Outcome**: 100% reliable JWKS loading with exponential backoff retry, solving the WellKnownResolver permanent failure issue completely.

---

## Dismissed Alternatives

### Library Integration Approaches

**Reason for dismissal**: While these would work, custom implementation provides better control and no additional dependencies.

#### SmallRye Fault Tolerance ⭐ 
**Quarkus Native**: ✅ Built-in, guaranteed compatibility  
**Implementation**: Annotation-based + programmatic API  
**Exponential Backoff**: ✅ Configurable via @Retry  

**Pros**:
- Zero additional dependencies
- Fully integrated with Quarkus DI and config
- Native image support guaranteed
- Comprehensive fault tolerance patterns

**Cons**:
- Annotation-based approach may not fit current architecture
- Less flexible than custom implementations

**Usage Example**:
```java
@Retry(maxRetries = 5, delay = 1000, jitter = 500)
@ExponentialBackoff(multiplier = 2, maxDelay = 30000)
public LoadResult loadWithRetry() {
    return etagHandler.load();
}
```

#### Resilience4j Integration
**Quarkus Native**: ⚠️ Requires verification but likely supported  
**Implementation**: Programmatic API with fluent builder  
**Exponential Backoff**: ✅ IntervalFunction with randomization  

**Pros**:
- Excellent programmatic API
- Fine-grained control over retry behavior
- Rich set of resilience patterns
- Well-documented exponential backoff with jitter

**Cons**:
- Additional dependency
- Native image compatibility needs verification
- More complex integration

**Usage Example**:
```java
Retry retry = Retry.of("httpHandler", RetryConfig.custom()
    .maxAttempts(5)
    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
        Duration.ofSeconds(1), 2.0, 0.5, Duration.ofSeconds(30)))
    .build());

LoadResult result = retry.executeSupplier(() -> etagHandler.load());
```