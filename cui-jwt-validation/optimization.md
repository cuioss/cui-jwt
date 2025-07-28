# JWT Signature Validation Performance Optimization Plan

## ⚠️Comply Project rule
Always comply with the project rules: [CLAUDE.md]

## ⚠️ Breaking Changes Notice (Pre-1.0)

Since this library is in pre-1.0 state, these optimizations may introduce breaking changes without deprecation warnings or migration paths or transitionary comments Changes include:

- Package reorganization: Moving classes to `de.cuioss.jwt.validation.pipeline.signature`
- New classes: `ScopedSignatureVerifier`, `CachedProviderSignatureFactory` in pipeline package
- API modifications in `SignatureTemplateManager`-> move to `de.cuioss.jwt.validation.pipeline.signature`
- Behavioral changes in thread management and caching strategies

## Executive Summary

This document outlines a comprehensive optimization strategy for improving JWT signature validation performance in the CUI-JWT library. The plan focuses on four key optimization areas: thread-safe verifier reuse, provider caching to eliminate contention, virtual thread optimization using Scoped Values, and elimination of virtual thread-incompatible concurrency patterns.

## 🚀 Actionable Tasks (Priority Order)

### Phase 1: Immediate Actions (Week 1-2)
- [ ] **Audit Concurrency Patterns** - Run comprehensive scan for virtual thread anti-patterns
  - [ ] Search for ThreadLocal usage: `grep -r "ThreadLocal" cui-jwt-validation/src/`
  - [ ] Identify synchronized blocks: `grep -r "synchronized" cui-jwt-validation/src/`
  - [ ] Check external dependencies for virtual thread compatibility
  - [ ] Add pinning detection: `-Djdk.tracePinnedThreads=full` to [quarkus-integration-benchmark](../cui-jwt-quarkus-parent/quarkus-integration-benchmark)
- [ ] **Implement ScopedSignatureVerifier** - Replace current signature management with Scoped Values
- [ ] **Add unit tests** for new ScopedSignatureVerifier class

### Phase 2: Core Optimizations  
- [ ] **Implement Provider Caching** - Cache Provider.Service instances per algorithm
- [ ] **Replace problematic synchronized blocks** with ReentrantLock (Java 21-23 compatibility)
- [ ] **Add unit tests** for CachedProviderSignatureFactory
- [ ] **Move SignatureTemplateManager** to `de.cuioss.jwt.validation.pipeline.signature` package

### Phase 3: Integration & Final Validation
- [ ] **Integration testing** with existing TokenSignatureValidator
- [ ] **Thread safety validation** under high concurrency using existing JMH benchmarks
- [ ] **Update package imports** throughout codebase
- [ ] **Finally verify using quarkus-integration-benchmark**

## Current State Analysis

### Existing Optimizations
- **SignatureTemplateManager**: Already implements template caching for algorithm mappings and PSS parameters
- **Per-request Signature instances**: Current implementation creates fresh Signature instances to ensure thread safety
- **Efficient algorithm mapping**: Switch expressions for O(1) algorithm lookups

### Performance Bottlenecks Identified
1. **Provider.getService() contention**: Each Signature.getInstance() call triggers Provider lookups
2. **Signature instance creation overhead**: Creating new instances for each validation
3. **Per-request instance creation**: Creating new Signature instances for each validation

## Optimization 1: Thread-Safe Verifier Reuse Implementation

### Technical Analysis

#### Auth0's Approach (Thread-Safe JWTVerifier)
```java
// Auth0 Pattern - Immutable, thread-safe verifier
public class JWTVerifier {
    private final Algorithm algorithm;
    private final Map<String, Object> claims;
    
    // Thread-safe verification method
    public synchronized DecodedJWT verify(String token) {
        // Verification logic
    }
}
```

**Pros:**
- Simple API for users
- Thread-safe by design
- Single instance can be shared

**Cons:**
- Synchronization bottleneck under high concurrency
- Not optimal for virtual threads
- Monolithic design

#### Tomitribe's Approach (Immutable Signer)
```java
// Tomitribe Pattern - Functional, immutable approach
public class Signer {
    private final Key key;
    private final Algorithm algorithm;
    
    // Creates new Signature instance per operation
    public byte[] sign(byte[] data) {
        Signature sig = Signature.getInstance(algorithm);
        sig.initSign((PrivateKey) key);
        sig.update(data);
        return sig.sign();
    }
}
```

**Pros:**
- No shared mutable state
- Naturally thread-safe
- Works well with virtual threads

**Cons:**
- Instance creation overhead
- No reuse of expensive objects

### Proposed Implementation for CUI-JWT

We'll implement a modern approach using Scoped Values for optimal virtual thread performance:

```java
/**
 * Signature verifier using Scoped Values for thread-safe operation.
 * Works with both virtual threads and platform threads.
 * Eliminates manual resource management overhead.
 */
public class ScopedSignatureVerifier {
    private static final ScopedValue<Signature> SIGNATURE_SCOPE = ScopedValue.newInstance();
    private final String algorithm;
    private final SignatureTemplate template;
    
    public ScopedSignatureVerifier(String algorithm) {
        this.algorithm = algorithm;
        this.template = SignatureTemplate.forAlgorithm(algorithm);
    }
    
    /**
     * Verifies signature using scoped Signature instance.
     * Works efficiently with both virtual and platform threads.
     */
    public boolean verify(PublicKey key, byte[] data, byte[] signature) {
        // Create signature instance for this scope
        Signature sig = template.createSignature();
        
        return ScopedValue.where(SIGNATURE_SCOPE, sig)
            .call(() -> {
                try {
                    Signature scopedSig = SIGNATURE_SCOPE.get();
                    scopedSig.initVerify(key);
                    scopedSig.update(data);
                    return scopedSig.verify(signature);
                } catch (InvalidKeyException | SignatureException e) {
                    throw new VerificationException("Signature verification failed", e);
                }
            });
        // Automatic cleanup - no explicit resource management needed
    }
}
```

### Implementation Steps

1. **Create ScopedSignatureVerifier class** in `de.cuioss.jwt.validation.pipeline.signature`
   - Implement Scoped Value-based signature management
   - Add proper error handling with automatic cleanup
   - Include unit tests for thread safety

2. **Move and modify SignatureTemplateManager** to `de.cuioss.jwt.validation.pipeline.signature`
   ```java
   package de.cuioss.jwt.validation.pipeline;
   
   public class SignatureTemplateManager {
       private final ConcurrentHashMap<String, ScopedSignatureVerifier> verifierCache;
       
       public ScopedSignatureVerifier getVerifier(String algorithm) {
           return verifierCache.computeIfAbsent(algorithm, 
               alg -> new ScopedSignatureVerifier(alg));
       }
   }
   ```

3. **Update TokenSignatureValidator**
   ```java
   private void verifySignature(DecodedJwt jwt, PublicKey key, String algorithm) {
       ThreadSafeSignatureVerifier verifier = signatureTemplateManager.getVerifier(algorithm);
       
       byte[] data = jwt.getDataToVerify().getBytes(StandardCharsets.UTF_8);
       byte[] signature = convertSignatureIfNeeded(jwt.getSignatureAsDecodedBytes(), algorithm);
       
       if (!verifier.verify(key, data, signature)) {
           throw new TokenValidationException("Invalid signature");
       }
   }
   ```

## Optimization 2: Provider Caching Implementation

### Technical Analysis

Based on SmallRye JWT issue #179, Provider.getService() is a significant bottleneck:

```java
// Current bottleneck in Signature.getInstance()
public static Signature getInstance(String algorithm) {
    // This call causes contention
    Provider.Service service = Provider.getService("Signature", algorithm);
    return (Signature) service.newInstance();
}
```

### Proposed Implementation

```java
/**
 * Caches Provider.Service instances to eliminate getService() contention.
 * This optimization is based on findings from production deployments showing
 * significant contention on Provider locks.
 */
public class CachedProviderSignatureFactory {
    private static final ConcurrentHashMap<String, Provider.Service> SERVICE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Gets a Signature instance using cached Provider.Service and Constructor.
     * This eliminates the Provider.getService() bottleneck entirely.
     */
    public static Signature getInstance(String jdkAlgorithm) throws NoSuchAlgorithmException {
        try {
            Constructor<?> constructor = CONSTRUCTOR_CACHE.computeIfAbsent(jdkAlgorithm, alg -> {
                Provider.Service service = SERVICE_CACHE.computeIfAbsent(alg, algorithm -> {
                    // One-time lookup per algorithm
                    for (Provider provider : Security.getProviders()) {
                        Provider.Service svc = provider.getService("Signature", algorithm);
                        if (svc != null) {
                            return svc;
                        }
                    }
                    return null;
                });
                
                if (service == null) {
                    throw new IllegalStateException("No provider for: " + alg);
                }
                
                try {
                    Class<?> clazz = Class.forName(service.getClassName());
                    return clazz.getDeclaredConstructor();
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot load signature class", e);
                }
            });
            
            constructor.setAccessible(true);
            return (Signature) constructor.newInstance();
            
        } catch (Exception e) {
            throw new NoSuchAlgorithmException("Algorithm not available: " + jdkAlgorithm, e);
        }
    }
}
```

### Implementation Steps

1. **Create CachedProviderSignatureFactory** in `de.cuioss.jwt.validation.pipeline.signature`
   - Implement provider service caching
   - Add constructor caching for direct instantiation
   - Include monitoring for cache effectiveness

2. **Integrate with SignatureTemplate**
   ```java
   private record SignatureTemplate(String jdkAlgorithm, PSSParameterSpec pssParams) {
       Signature createSignature() {
           try {
               // Use cached provider factory
               Signature signature = CachedProviderSignatureFactory.getInstance(jdkAlgorithm);
               if (pssParams != null) {
                   signature.setParameter(pssParams);
               }
               return signature;
           } catch (Exception e) {
               // Log error and rethrow
               throw new SignatureException("Failed to create cached signature", e);
           }
       }
   }
   ```

3. **Add monitoring and metrics**
   ```java
   public class ProviderCacheMetrics {
       private final AtomicLong cacheHits = new AtomicLong();
       private final AtomicLong cacheMisses = new AtomicLong();
       private final AtomicLong errors = new AtomicLong();
       
       public void recordCacheHit() { cacheHits.incrementAndGet(); }
       public void recordCacheMiss() { cacheMisses.incrementAndGet(); }
       public void recordError() { errors.incrementAndGet(); }
   }
   ```

## Optimization 3: Scoped Values for Thread-Safe Signature Management

### Technical Analysis

Scoped Values (JEP 429, standard since Java 21) provide a modern approach that works efficiently with both virtual and platform threads:

#### Thread Compatibility
- **Virtual Threads**: Optimal performance due to lightweight scoped value binding
- **Platform Threads**: Works correctly with traditional thread pools
- **Mixed Environments**: Can handle both thread types seamlessly
- **No Thread Detection**: Implementation doesn't need to detect thread type

#### Performance Characteristics
- **Virtual Threads**: Near-zero overhead for scope creation/cleanup
- **Platform Threads**: Similar performance to ThreadLocal but with better memory management
- **Automatic Cleanup**: No manual resource management needed for either thread type

```java
// Scoped Values for virtual thread optimization
static final ScopedValue<Signature> SIGNATURE = ScopedValue.newInstance();

// Usage pattern - automatic cleanup
ScopedValue.where(SIGNATURE, signature)
           .call(() -> {
               Signature sig = SIGNATURE.get();
               // Use signature in this scope
               return sig.verify(data);
           });
```

### Proposed Implementation

```java
/**
 * Signature verifier using Scoped Values for optimal thread performance.
 * Works with both virtual threads and platform threads.
 * Requires Java 24+ for Scoped Values support.
 */
public class ScopedSignatureVerifier {
    private static final ScopedValue<Signature> SIGNATURE_VALUE = ScopedValue.newInstance();
    private final SignatureTemplate template;
    private final String algorithm;
    
    public ScopedSignatureVerifier(String algorithm) {
        this.algorithm = algorithm;
        this.template = SignatureTemplateManager.getTemplate(algorithm);
    }
    
    /**
     * Execute signature operation with scoped value binding.
     * Works efficiently with both virtual and platform threads.
     */
    public <T> T executeWithSignature(SignatureOperation<T> operation, PublicKey key) 
            throws Exception {
        // Create fresh signature instance for this scope
        Signature signature = template.createSignature();
        
        // Bind signature to this virtual thread's scope
        return ScopedValue.where(SIGNATURE_VALUE, signature)
            .call(() -> {
                Signature sig = SIGNATURE_VALUE.get();
                sig.initVerify(key);
                return operation.execute(sig);
            });
    }
} 
 ```

### Implementation Steps

1. **Implement Scoped Value patterns**
   ```java
   public class ScopedSignaturePatterns {
       private static final ScopedValue<Signature> SIGNATURE = ScopedValue.newInstance();
       
       /**
        * Pattern 1: Simple operation with fresh signature
        */
       public static boolean verifyToken(String token, PublicKey key, String algorithm) 
               throws Exception {
           Signature sig = Signature.getInstance(algorithm);
           return ScopedValue.where(SIGNATURE, sig)
               .call(() -> {
                   Signature s = SIGNATURE.get();
                   s.initVerify(key);
                   s.update(token.getBytes(StandardCharsets.UTF_8));
                   return s.verify(extractSignature(token));
               });
       }
       
       /**
        * Pattern 2: Reusable carrier for batch operations
        */
       public static class BatchProcessor {
           private final ScopedValue.Carrier carrier;
           
           public BatchProcessor(Signature signature) {
               this.carrier = ScopedValue.where(SIGNATURE, signature);
           }
           
           public void processBatch(List<Operation> operations) {
               carrier.run(() -> {
                   Signature sig = SIGNATURE.get();
                   operations.forEach(op -> op.execute(sig));
               });
           }
       }
   }
   ```

3. **Update TokenSignatureValidator integration**
   ```java
   package de.cuioss.jwt.validation.pipeline;
   
   public class TokenSignatureValidator {
       private final ScopedSignatureVerifier verifier;
       
       public TokenSignatureValidator(String algorithm) {
           this.verifier = new ScopedSignatureVerifier(algorithm);
       }
       
       private void verifySignature(DecodedJwt jwt, PublicKey key) throws Exception {
           boolean isValid = verifier.executeWithSignature(
               signature -> {
                   signature.update(jwt.getDataBytes());
                   return signature.verify(jwt.getSignatureBytes());
               },
               key
           );
           
           if (!isValid) {
               throw new TokenValidationException("Invalid signature");
           }
       }
   }
   ```

## Optimization 4: Virtual Thread Concurrency Pattern Audit & Migration

### Technical Analysis

Based on comprehensive research (see [Virtual Threads Concurrency Analysis](../virtual-threads-concurrency-analysis.md)), certain traditional concurrency patterns can negatively impact virtual thread performance or cause pinning. While Java 24+ has resolved many synchronized-related pinning issues, other anti-patterns remain problematic.

#### Current Codebase Assessment ✅

**Excellent Patterns Already in Use:**
- **Instance-based Caching**: `SignatureTemplateManager` uses `ConcurrentHashMap` instead of ThreadLocal
- **Lock-free Operations**: `SecurityEventCounter` uses atomic operations
- **Immutable Data Publishing**: `IssuerConfigResolver` properly handles concurrent updates
- **Minimal Synchronization**: Brief synchronized blocks for initialization only

#### Anti-patterns to Monitor

### 1. ThreadLocal Anti-pattern (Not present - ✅ Good)
```java
// BAD: Would cause massive memory usage with virtual threads
private static final ThreadLocal<Signature> SIGNATURE_CACHE = 
    ThreadLocal.withInitial(() -> createSignature());

// GOOD: Current approach in SignatureTemplateManager
private final ConcurrentHashMap<String, SignatureTemplate> cache = new ConcurrentHashMap<>();
```

### 2. Synchronized Patterns Analysis

**Current codebase synchronized usage assessment:**

```java
// ACCEPTABLE: Short-lived cache operations (IssuerConfigResolver.java)
synchronized (this) {
    IssuerConfig result = mutableCache.get(issuer);  // Fast in-memory only
    // ... double-check locking pattern
}

// REVIEW NEEDED: HTTP operations in synchronized (ETagAwareHttpHandler.java)
public synchronized LoadResult load() {
    HttpFetchResult result = fetchJwksContentWithCache();  // I/O operation
}

// BETTER FOR JAVA 21-23: Use ReentrantLock for I/O operations
private final ReentrantLock lock = new ReentrantLock();
public LoadResult load() {
    lock.lock();
    try {
        return fetchJwksContentWithCache();  // Virtual thread can unmount
    } finally {
        lock.unlock();
    }
}
```

**Key Findings:**
- **Java 24+**: JEP 491 resolves synchronized pinning - no migration needed
- **Java 21-23**: Consider ReentrantLock for HTTP-related synchronized blocks
- **Current code**: Mostly acceptable patterns, minimal virtual thread impact

### 3. Connection Pool Issues (External dependencies)
Traditional connection pools like HikariCP may have internal synchronization that affects virtual threads in Java 21-23.

### Implementation Steps

1. **Audit Current Concurrency Patterns**
   ```bash
   # Search for potential anti-patterns
   grep -r "ThreadLocal\|synchronized\|wait()\|notify()" cui-jwt-validation/src/
   ```

2. **Add Virtual Thread Pinning Detection** (Java 21-23)
   ```java
   // JVM argument for pinning detection
   -Djdk.tracePinnedThreads=full
   ```

4. **Migration Checklist**
   - [ ] **Replace ThreadLocal patterns** with Scoped Values (already done)
   - [ ] **Convert synchronized methods** to ReentrantLock where needed
   - [ ] **Audit external dependencies** for virtual thread compatibility
   - [ ] **Add pinning detection** in development/testing environments
   - [ ] **Performance testing** with virtual threads vs platform threads

### Virtual Thread Compatibility Matrix

#### ✅ **Positive Patterns (Current Code)**
- `ConcurrentHashMap` for caching
- `AtomicLong`/`AtomicReference` for counters
- Immutable objects with volatile publication
- Brief synchronized blocks for initialization
- `CompletableFuture` for async operations

#### ❌ **Anti-patterns to Avoid**
- `ThreadLocal` caching with virtual threads
- **Synchronized blocks with I/O operations** (Java 21-23) - causes virtual thread pinning
- **Lombok @Synchronized annotation** - uses synchronized internally, prefer @Locked for virtual threads
- **Long-running synchronized blocks** (Java 21-23) - resolved in Java 24+ with JEP 491
- Traditional connection pooling patterns
- `wait()`/`notify()` in high-contention scenarios

#### ⚠️ **Patterns Requiring Analysis**
- External HTTP client libraries
- Database connection pools
- Third-party caching libraries
- Native library integrations

### Integration with Existing Optimizations

This audit complements the other optimizations:
- **Optimization 1**: Ensures verifier reuse patterns are virtual thread compatible
- **Optimization 2**: Validates provider caching doesn't introduce pinning
- **Optimization 3**: Scoped Values are inherently virtual thread optimized
