# JWT Validation Performance Optimization Tasks

## Current Performance Issues

Following the setup isolation fix, true performance bottlenecks are revealed:

1. **Signature Validation**: 17.9ms p99 spikes (262x p50 ratio)
2. **Token Building**: 3.7ms p99 spikes (335x p50 ratio)  
3. **Claims Validation**: 1.3ms p99 spikes (217x p50 ratio)

**Current Baseline**: 102,956 ops/s throughput with p99 latencies reaching 32.3ms

## Prioritized Optimization Tasks with Action Items

### 1. Signature Validation Optimization 

**Location**: `TokenSignatureValidator.java:189-201` - `verifySignature()` method
**Real Issue**: The 17.9ms p99 spikes are from actual cryptographic operations that cannot be optimized further within JCA constraints

**Analysis**: 
- RSA/ECDSA signature verification is inherently CPU-intensive
- SignatureTemplateManager already optimizes provider lookups
- ECDSA conversion overhead is necessary for JCA compatibility

**Decision**: Accept that signature validation is the primary bottleneck and focus optimization efforts on other areas

### 2. Token Building Field-Based Architecture (High Priority)

**Location**: `TokenBuilder.java` - instance created per request at `TokenValidator.java:232,254`
**Issue**: New `TokenBuilder` instance created for every token validation
**Impact**: 3.7ms p99 spikes from object allocation

**Action Items:**
- [ ] Add `tokenBuilders` field to `TokenValidator`:
  ```java
  private final Map<String, TokenBuilder> tokenBuilders;
  ```
- [ ] In `TokenValidator` constructor, initialize token builders:
  ```java
  Map<String, TokenBuilder> builders = new HashMap<>();
  for (IssuerConfig issuerConfig : issuerConfigs) {
      builders.put(
          issuerConfig.getIssuerIdentifier(), 
          new TokenBuilder(issuerConfig)
      );
  }
  this.tokenBuilders = Map.copyOf(builders);
  ```
- [ ] Modify `createAccessToken` method to use cached builder:
  ```java
  TokenBuilder cachedBuilder = tokenBuilders.get(issuerConfig.getIssuerIdentifier());
  return cachedBuilder.createAccessToken(decodedJwt);
  ```
- [ ] Modify `createIdToken` method to use cached builder
- [ ] Update `processTokenPipeline` method signature to accept cached builder
- [ ] Write unit tests to verify thread safety of shared TokenBuilder instances
- [ ] Run `./mvnw -Ppre-commit clean install -DskipTests -pl cui-jwt-validation`
- [ ] Run `./mvnw clean install -pl cui-jwt-validation`
- [ ] Commit changes to git

### 3. Claims Validation Field-Based Architecture (High Priority)

**Location**: `TokenClaimValidator.java` - instance created per request at `TokenValidator.java:492`
**Issue**: New `TokenClaimValidator` instance created for every token validation
**Impact**: 1.3ms p99 spikes from object allocation

**Action Items:**
- [ ] Add `claimValidators` field to `TokenValidator`:
  ```java
  private final Map<String, TokenClaimValidator> claimValidators;
  ```
- [ ] In `TokenValidator` constructor, initialize claim validators:
  ```java
  Map<String, TokenClaimValidator> validators = new HashMap<>();
  for (IssuerConfig issuerConfig : issuerConfigs) {
      validators.put(
          issuerConfig.getIssuerIdentifier(),
          new TokenClaimValidator(issuerConfig, securityEventCounter)
      );
  }
  this.claimValidators = Map.copyOf(validators);
  ```
- [ ] Modify `validateTokenClaims` method to use cached validator:
  ```java
  TokenClaimValidator cachedValidator = claimValidators.get(issuerConfig.getIssuerIdentifier());
  TokenContent validatedContent = cachedValidator.validate(token, context);
  ```
- [ ] Remove line creating new TokenClaimValidator instance
- [ ] Write unit tests to verify thread safety of shared validators
- [ ] Run `./mvnw -Ppre-commit clean install -DskipTests -pl cui-jwt-validation`
- [ ] Run `./mvnw clean install -pl cui-jwt-validation`
- [ ] Commit changes to git

### 4. Header Validation Field-Based Architecture (Medium Priority)

**Location**: `TokenValidator.java:428` - `validateTokenHeader()` method
**Issue**: New `TokenHeaderValidator` instance created per request

**Action Items:**
- [ ] Add `headerValidators` field to `TokenValidator`:
  ```java
  private final Map<String, TokenHeaderValidator> headerValidators;
  ```
- [ ] In `TokenValidator` constructor, initialize header validators:
  ```java
  Map<String, TokenHeaderValidator> validators = new HashMap<>();
  for (IssuerConfig issuerConfig : issuerConfigs) {
      validators.put(
          issuerConfig.getIssuerIdentifier(),
          new TokenHeaderValidator(issuerConfig, securityEventCounter)
      );
  }
  this.headerValidators = Map.copyOf(validators);
  ```
- [ ] Modify `validateTokenHeader` method to use cached validator:
  ```java
  TokenHeaderValidator cachedValidator = headerValidators.get(issuerConfig.getIssuerIdentifier());
  cachedValidator.validate(decodedJwt);
  ```
- [ ] Remove line creating new TokenHeaderValidator instance
- [ ] Write unit tests to verify thread safety
- [ ] Run `./mvnw -Ppre-commit clean install -DskipTests -pl cui-jwt-validation`
- [ ] Run `./mvnw clean install -pl cui-jwt-validation`
- [ ] Commit changes to git

### 5. Claim Processing Optimization (Medium Priority)

**Location**: `TokenBuilder.java:111-136` - `extractClaims()` method
**Issue**: Map lookups and claim resolution per token

**Analysis**: 
- `ClaimName.fromString()` already uses `ConcurrentHashMap` cache (line 273)
- Main overhead is from repeated `issuerConfig.getClaimMappers()` calls and map lookups

**Action Items:**
- [ ] Add `customMappers` field to `TokenBuilder`:
  ```java
  private final Map<String, ClaimMapper> customMappers;
  ```
- [ ] Update `TokenBuilder` constructor to cache custom mappers:
  ```java
  public TokenBuilder(IssuerConfig issuerConfig) {
      this.issuerConfig = issuerConfig;
      this.customMappers = issuerConfig.getClaimMappers() != null 
          ? Map.copyOf(issuerConfig.getClaimMappers()) 
          : Map.of();
  }
  ```
- [ ] Modify `extractClaims` method to use cached mappers:
  ```java
  ClaimMapper customMapper = customMappers.get(key);
  if (customMapper != null) {
      claims.put(key, customMapper.map(jsonObject, key));
  } else {
      // Continue with ClaimName resolution...
  }
  ```
- [ ] Remove redundant null check and map access in extractClaims
- [ ] Write unit tests to verify correct claim extraction
- [ ] Run `./mvnw -Ppre-commit clean install -DskipTests -pl cui-jwt-validation`
- [ ] Run `./mvnw clean install -pl cui-jwt-validation`
- [ ] Commit changes to git

### 6. Enhanced Validation Context (Low Priority)

**Location**: `ValidationContext.java` - currently only caches time
**Current State**: Already caches current time to avoid multiple `OffsetDateTime.now()` calls
**Analysis**: Further enhancement may not provide significant benefit

**Decision**: No action required - current implementation is sufficient

## Summary

The optimization plan focuses on:
1. **Field-based architecture** - Eliminate object allocation overhead for validators and builders (High Priority)
2. **Claim mapper caching** - Minor optimization to reduce repeated map lookups (Medium Priority)

These optimizations follow the existing patterns in the codebase and target the reducible bottlenecks identified through benchmark analysis. The signature validation p99 spikes are accepted as inherent to cryptographic operations.