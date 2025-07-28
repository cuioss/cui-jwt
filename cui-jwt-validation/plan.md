# JWT Validation Performance Optimization Tasks

## Current Performance Issues

Following the setup isolation fix, true performance bottlenecks are revealed:

1. **Signature Validation**: 17.9ms p99 spikes (262x p50 ratio)
2. **Token Building**: 3.7ms p99 spikes (335x p50 ratio)  
3. **Claims Validation**: 1.3ms p99 spikes (217x p50 ratio)

**Current Baseline**: 102,956 ops/s throughput with p99 latencies reaching 32.3ms

## Prioritized Optimization Tasks

### 1. Signature Validation Optimization (Highest Priority)

**Location**: `TokenSignatureValidator.java:189-201` - `verifySignature()` method
**Issue**: Each validation calls `signatureTemplateManager.getSignatureInstance(algorithm)` which may have internal synchronization

**Alternative Optimization Approaches**:
- **Provider Pre-resolution**: Cache JCA Provider instances to avoid Provider.getService() lookups
- **Algorithm String Interning**: Ensure algorithm strings are interned to speed up map lookups
- **ECDSA Conversion Optimization**: Pre-compute ECDSA signature format conversion tables
- **Direct Provider Access**: Use provider-specific APIs to bypass JCA abstraction layer

```java
// Example: Direct provider access for common algorithms
private static final Provider SUN_RSA_SIGN = Security.getProvider("SunRsaSign");
private static final Provider SUN_EC = Security.getProvider("SunEC");

// In verifySignature method - use direct provider
Signature verifier = algorithm.startsWith("RS") || algorithm.startsWith("PS") 
    ? Signature.getInstance(algorithm, SUN_RSA_SIGN)
    : Signature.getInstance(algorithm, SUN_EC);
```

### 2. Token Building Field-Based Architecture (High Priority)

**Location**: `TokenBuilder.java:110-136` - `extractClaims()` method  
**Issue**: New `TokenBuilder` instance created per request in `TokenValidator.java:232,254`

**Task**: Cache TokenBuilder Instances (Check for Sharing Opportunities)
- Check if claim mappers are actually different per issuer
- If claim mappers are identical across issuers, use single shared instance
- Otherwise, cache per unique claim mapper configuration, not per issuer

```java
// In TokenValidator constructor - deduplicate by configuration
Map<Map<String, ClaimMapper>, TokenBuilder> uniqueBuilders = new HashMap<>();
Map<String, TokenBuilder> tokenBuilders = new HashMap<>();

for (IssuerConfig issuerConfig : issuerConfigs) {
    Map<String, ClaimMapper> mappers = issuerConfig.getClaimMappers();
    TokenBuilder builder = uniqueBuilders.computeIfAbsent(
        mappers, 
        k -> new TokenBuilder(issuerConfig)
    );
    tokenBuilders.put(issuerConfig.getIssuerIdentifier(), builder);
}
this.tokenBuilders = Map.copyOf(tokenBuilders);
```

### 3. Claims Validation Field-Based Architecture (High Priority)

**Location**: `TokenClaimValidator.java:118-127` - `validate()` method
**Issue**: New `TokenClaimValidator` instance created per request in `TokenValidator.java:492`

**Task**: Cache TokenClaimValidator Instances (Deduplicate by Configuration)
- TokenClaimValidator uses expectedAudience and expectedClientId from IssuerConfig
- Cache by unique combination of these values, not necessarily per issuer
- Share instances where audience/clientId requirements are identical

```java
// In TokenValidator constructor - deduplicate by validation requirements
record ValidationConfig(Set<String> expectedAudience, Set<String> expectedClientId) {}
Map<ValidationConfig, TokenClaimValidator> uniqueValidators = new HashMap<>();
Map<String, TokenClaimValidator> claimValidators = new HashMap<>();

for (IssuerConfig issuerConfig : issuerConfigs) {
    ValidationConfig config = new ValidationConfig(
        issuerConfig.getExpectedAudience(),
        issuerConfig.getExpectedClientId()
    );
    TokenClaimValidator validator = uniqueValidators.computeIfAbsent(
        config,
        k -> new TokenClaimValidator(issuerConfig, securityEventCounter)
    );
    claimValidators.put(issuerConfig.getIssuerIdentifier(), validator);
}
this.claimValidators = Map.copyOf(claimValidators);
```

### 4. Header Validation Field-Based Architecture (Medium Priority)

**Location**: `TokenValidator.java:428` - `validateTokenHeader()` method
**Issue**: New `TokenHeaderValidator` instance created per request

**Task**: Cache TokenHeaderValidator Instances
- Add `TokenHeaderValidator` as field per issuer
- Follow same pattern as other validators

### 5. Claim Processing Optimization (Medium Priority)

**Location**: `TokenBuilder.java:111-136` - `extractClaims()` method
**Issue**: Repeated map lookups and claim name resolution per token

**Task**: Pre-resolve Claim Mappers
- Cache claim mapper lookups during TokenBuilder construction
- Pre-build ClaimName resolution maps
- Eliminate `ClaimName.fromString(key)` expensive lookups

```java
// In TokenBuilder constructor
private final Map<String, ClaimMapper> resolvedMappers;
private final Map<String, ClaimName> resolvedClaimNames;

public TokenBuilder(IssuerConfig issuerConfig) {
    // Pre-resolve all claim mappers and names during construction
    this.resolvedMappers = preResolveMappers(issuerConfig);
    this.resolvedClaimNames = preResolveClaimNames();
}
```

### 6. Enhanced Validation Context (Medium Priority)

**Location**: `TokenValidator.java:490` - `validateTokenClaims()` method
**Current Issue**: ValidationContext only caches current time, but could cache more validation state

**Task**: Extend ValidationContext for Pipeline State
- Store already validated information during token processing
- Pass context through entire pipeline to avoid repeated work
- Cache expensive computations like algorithm lookups, key resolutions

```java
// Enhanced ValidationContext
public class ValidationContext {
    private final OffsetDateTime currentTime;
    private final long clockSkewSeconds;
    
    // Add pipeline state caching
    private String resolvedAlgorithm;
    private PublicKey resolvedKey;
    private Map<String, Object> validationCache = new HashMap<>();
    
    public void cacheValidation(String key, Object result) {
        validationCache.put(key, result);
    }
    
    public Optional<Object> getCachedValidation(String key) {
        return Optional.ofNullable(validationCache.get(key));
    }
}
```

### 7. Algorithm String Optimization (Low Priority)

**Location**: Throughout signature validation and header validation
**Issue**: String comparisons and lookups for algorithm names

**Task**: Intern Algorithm Strings
- Use String.intern() for all algorithm constants
- Convert to enum where possible for faster comparisons
- Pre-compute algorithm type flags (isRSA, isECDSA, etc.)