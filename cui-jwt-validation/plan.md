# TokenValidator Refactoring Plan - Issue #132

## Overview

This refactoring extracts inline pipeline logic from TokenValidator (557 lines → ~180 lines) into dedicated pipeline classes for each token type. This enables issue #131 optimization (early cache checks) and improves maintainability.

**Pre-1.0 Breaking Changes Policy**: Breaking changes are WELCOME and ENCOURAGED. No deprecation annotations, no transitional compatibility layers. Clean refactoring over incremental migration.

---

## Implementation Tasks

### Task 0: Create TokenStringValidator

**File**: `cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/pipeline/validator/TokenStringValidator.java`

**Purpose**: Pre-pipeline validation of raw token string (null, blank, max size)

**Implementation**:
- [ ] Create `pipeline/validator/` subdirectory
- [ ] Create `TokenStringValidator.java` with:
  - [ ] Constructor accepting `ParserConfig` and `SecurityEventCounter`
  - [ ] `validate(String tokenString)` method that checks:
    - [ ] Null check → throw `TokenValidationException(TOKEN_EMPTY)`
    - [ ] Blank check → throw `TokenValidationException(TOKEN_EMPTY)`
    - [ ] Size limit check → throw `TokenValidationException(TOKEN_TOO_LARGE)`
  - [ ] Proper logging using CuiLogger
  - [ ] Security event counter increments for each violation
- [ ] Add class javadoc explaining pre-pipeline validation purpose
- [ ] Note in javadoc: Future enhancement for `MeasurementType.TOKEN_FORMAT_CHECK` metrics

**Integration**: Will be called by `TokenValidator` before delegating to pipelines

---

### Task 1: Create RefreshTokenValidationPipeline

**File**: `cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/pipeline/RefreshTokenValidationPipeline.java`

**Purpose**: Minimal validation for refresh tokens (opaque or lightly validated)

**Implementation**:
- [ ] Create `RefreshTokenValidationPipeline.java` with:
  - [ ] Constructor accepting `NonValidatingJwtParser` and `SecurityEventCounter`
  - [ ] `validate(@NonNull String tokenString)` method that:
    - [ ] Assumes tokenString is already validated by TokenStringValidator
    - [ ] Attempts to parse as JWT (failure allowed)
    - [ ] Extracts claims if JWT, otherwise empty map
    - [ ] Returns `RefreshTokenContent(tokenString, claims)`
  - [ ] No metrics instrumentation (uses `NoOpMetricsTicker`)
  - [ ] Proper error handling for JWT parsing failures
- [ ] Add class javadoc explaining minimal validation approach
- [ ] Note: TokenStringValidator has already checked null/blank/size

**Integration**: Will be called by `TokenValidator.createRefreshToken()` after `TokenStringValidator`

---

### Task 2: Create IdTokenValidationPipeline

**File**: `cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/pipeline/IdTokenValidationPipeline.java`

**Purpose**: Full validation pipeline for ID tokens without caching

**Implementation**:
- [ ] Create `IdTokenValidationPipeline.java` with:
  - [ ] Constructor accepting:
    - [ ] `NonValidatingJwtParser jwtParser`
    - [ ] `IssuerConfigResolver issuerConfigResolver`
    - [ ] `Map<String, TokenSignatureValidator> signatureValidators`
    - [ ] `Map<String, TokenBuilder> tokenBuilders`
    - [ ] `Map<String, TokenClaimValidator> claimValidators`
    - [ ] `Map<String, TokenHeaderValidator> headerValidators`
    - [ ] `SecurityEventCounter securityEventCounter`
  - [ ] `validate(@NonNull String tokenString)` method with pipeline steps:
    1. [ ] Parse token (uses `jwtParser.decode()`)
    2. [ ] Extract issuer from decoded JWT
    3. [ ] Resolve issuer config via `issuerConfigResolver`
    4. [ ] Validate header using cached validator
    5. [ ] Validate signature using cached validator
    6. [ ] Build token using cached builder
    7. [ ] Validate claims using cached validator with `ValidationContext`
    8. [ ] Return `IdTokenContent`
  - [ ] No metrics instrumentation (uses `NoOpMetricsTicker`)
  - [ ] Proper exception handling and security event counting
- [ ] Add class javadoc explaining full validation pipeline
- [ ] Note: TokenStringValidator has already checked null/blank/size

**Integration**: Will be called by `TokenValidator.createIdToken()` after `TokenStringValidator`

---

### Task 3: Create AccessTokenValidationPipeline with Early Cache

**File**: `cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/pipeline/AccessTokenValidationPipeline.java`

**Purpose**: Full validation pipeline with early cache check (issue #131 optimization)

**Implementation**:
- [ ] Create `AccessTokenValidationPipeline.java` with:
  - [ ] Constructor accepting all IdToken dependencies PLUS:
    - [ ] `AccessTokenCache cache`
    - [ ] `TokenValidatorMonitor performanceMonitor`
  - [ ] `validate(@NonNull String tokenString)` method with pipeline steps:
    1. [ ] Start `COMPLETE_VALIDATION` metrics
    2. [ ] Parse token (with `TOKEN_PARSING` metrics)
    3. [ ] Extract issuer (with `ISSUER_EXTRACTION` metrics)
    4. [ ] **CHECK CACHE HERE** (with `CACHE_LOOKUP` metrics) ← CRITICAL: Early, before expensive ops
       - [ ] If cache hit: return cached result immediately
    5. [ ] Resolve issuer config (with `ISSUER_CONFIG_RESOLUTION` metrics)
    6. [ ] Validate header (with `HEADER_VALIDATION` metrics)
    7. [ ] Validate signature (with `SIGNATURE_VALIDATION` metrics) ← Most expensive
    8. [ ] Build token (with `TOKEN_BUILDING` metrics)
    9. [ ] Validate claims (with `CLAIMS_VALIDATION` metrics)
    10. [ ] Store in cache (with `CACHE_STORE` metrics)
    11. [ ] Stop `COMPLETE_VALIDATION` metrics
    12. [ ] Return `AccessTokenContent`
  - [ ] Full metrics instrumentation using `MetricsTickerFactory`
  - [ ] Proper exception handling and security event counting
- [ ] Add class javadoc highlighting early cache optimization
- [ ] Note: Cache check at step 4 (after parse/issuer, before signature) fixes issue #131

**Integration**: Will be called by `TokenValidator.createAccessToken()` after `TokenStringValidator`

---

### 📊 VERIFICATION CHECKPOINT 1 (After Tasks 0-3)

Run quality verification build:
```bash
./mvnw -Ppre-commit clean verify -pl cui-jwt-validation
```

**Must verify**:
- [ ] All new pipeline and validator classes compile successfully
- [ ] Existing tests still pass (pipelines not yet integrated into TokenValidator)
- [ ] Code quality checks pass (checkstyle, spotbugs, PMD)
- [ ] No warnings or errors in build output

**If failures occur**: Fix ALL issues before proceeding to Task 4

---

### Task 4: Simplify TokenValidator to Thin Orchestrator

**File**: `cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/TokenValidator.java`

**Purpose**: Refactor from 557 lines to ~180 lines, delegate to pipelines

**Implementation**:
- [ ] Add new private fields:
  - [ ] `TokenStringValidator tokenStringValidator`
  - [ ] `AccessTokenValidationPipeline accessTokenPipeline`
  - [ ] `IdTokenValidationPipeline idTokenPipeline`
  - [ ] `RefreshTokenValidationPipeline refreshTokenPipeline`
- [ ] Update constructor (`@Builder` method) to:
  - [ ] Keep existing validator map construction (signatureValidators, tokenBuilders, etc.)
  - [ ] Construct `tokenStringValidator = new TokenStringValidator(parserConfig, securityEventCounter)`
  - [ ] Construct `refreshTokenPipeline = new RefreshTokenValidationPipeline(jwtParser, securityEventCounter)`
  - [ ] Construct `idTokenPipeline = new IdTokenValidationPipeline(jwtParser, issuerConfigResolver, signatureValidators, tokenBuilders, claimValidators, headerValidators, securityEventCounter)`
  - [ ] Construct `accessTokenPipeline = new AccessTokenValidationPipeline(jwtParser, issuerConfigResolver, signatureValidators, tokenBuilders, claimValidators, headerValidators, cache, securityEventCounter, performanceMonitor)`
- [ ] Refactor `createAccessToken()`:
  - [ ] Call `tokenStringValidator.validate(tokenString)` first
  - [ ] Call `return accessTokenPipeline.validate(tokenString)`
  - [ ] Keep existing logging and security event counter increment
  - [ ] Remove metrics ticker (now in pipeline)
- [ ] Refactor `createIdToken()`:
  - [ ] Call `tokenStringValidator.validate(tokenString)` first
  - [ ] Call `return idTokenPipeline.validate(tokenString)`
  - [ ] Keep existing logging and security event counter increment
- [ ] Refactor `createRefreshToken()`:
  - [ ] Call `tokenStringValidator.validate(tokenString)` first
  - [ ] Call `return refreshTokenPipeline.validate(tokenString)`
  - [ ] Keep existing logging and security event counter increment
- [ ] **DELETE all private validation methods**:
  - [ ] Remove `processAccessTokenWithCache()`
  - [ ] Remove `validateTokenFormat()`
  - [ ] Remove `decodeToken()`
  - [ ] Remove `validateAndExtractIssuer()`
  - [ ] Remove `resolveIssuerConfig()`
  - [ ] Remove `validateTokenHeader()`
  - [ ] Remove `validateTokenSignature()`
  - [ ] Remove `buildIdToken()`
  - [ ] Remove `buildAccessToken()`
  - [ ] Remove `validateTokenClaims()`
- [ ] Update class javadoc to reflect new orchestrator role
- [ ] Verify final line count is ~180 lines

**Critical**: Breaking changes are acceptable. Delete old code cleanly, no deprecation.

---

### 📊 VERIFICATION CHECKPOINT 2 (After Task 4)

Run quality verification build:
```bash
./mvnw -Ppre-commit clean verify -pl cui-jwt-validation
```

**Must verify**:
- [ ] TokenValidator compiles successfully
- [ ] Integration tests pass with new architecture
- [ ] No regressions in functionality (all existing tests pass)
- [ ] Code quality checks pass (checkstyle, spotbugs, PMD)
- [ ] Final TokenValidator size is ~180 lines
- [ ] No warnings or errors in build output

**If failures occur**: Fix ALL issues before proceeding to Task 5

---

### Task 5: Move Existing Validators to Subpackage

**Purpose**: Organize validators in dedicated `pipeline/validator/` subpackage

**Implementation**:
- [ ] Move validators to `pipeline/validator/`:
  - [ ] `TokenSignatureValidator.java` → `pipeline/validator/TokenSignatureValidator.java`
  - [ ] `TokenHeaderValidator.java` → `pipeline/validator/TokenHeaderValidator.java`
  - [ ] `TokenClaimValidator.java` → `pipeline/validator/TokenClaimValidator.java`
  - [ ] `AudienceValidator.java` → `pipeline/validator/AudienceValidator.java`
  - [ ] `ExpirationValidator.java` → `pipeline/validator/ExpirationValidator.java`
  - [ ] `MandatoryClaimsValidator.java` → `pipeline/validator/MandatoryClaimsValidator.java`
  - [ ] `AuthorizedPartyValidator.java` → `pipeline/validator/AuthorizedPartyValidator.java`
- [ ] Update package declarations in moved files:
  - [ ] Change `package de.cuioss.jwt.validation.pipeline;`
  - [ ] To `package de.cuioss.jwt.validation.pipeline.validator;`
- [ ] Update imports across codebase:
  - [ ] Update `TokenValidator.java` imports
  - [ ] Update pipeline class imports (IdTokenValidationPipeline, AccessTokenValidationPipeline)
  - [ ] Update any other files importing validators
- [ ] **Note**: `TokenBuilder` stays at `pipeline/` level (it's a builder, not a validator)
- [ ] **Note**: `DecodedJwt`, `NonValidatingJwtParser`, `SignatureTemplateManager` stay at `pipeline/` level
- [ ] Create `pipeline/validator/package-info.java` with package documentation

**Critical**: Move files, not copy. Delete old locations.

---

### Task 6: Reorder MeasurementType by Pipeline Execution

**File**: `cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/metrics/MeasurementType.java`

**Purpose**: Order enum constants by pipeline execution sequence, use natural ordinals

**Implementation**:
- [ ] Reorder enum constants to match pipeline execution order (ordinals 0-14):
  ```java
  // Pipeline execution order (ordinal 0-10)
  COMPLETE_VALIDATION("0. Complete JWT validation"),
  TOKEN_FORMAT_CHECK("1. Token format validation"),        // Future: TokenStringValidator metrics
  TOKEN_PARSING("2. JWT token parsing"),
  ISSUER_EXTRACTION("3. Issuer extraction"),
  CACHE_LOOKUP("4. Cache lookup operation"),               // Access tokens only
  ISSUER_CONFIG_RESOLUTION("5. Issuer config resolution"),
  HEADER_VALIDATION("6. JWT header validation"),
  SIGNATURE_VALIDATION("7. JWT signature validation"),
  TOKEN_BUILDING("8. Token building"),
  CLAIMS_VALIDATION("9. JWT claims validation"),
  CACHE_STORE("10. Cache store operation"),                // Access tokens only

  // JWKS operations (can happen at various points)
  JWKS_OPERATIONS("11. JWKS key operations"),

  // Retry operations (cross-cutting concern)
  RETRY_ATTEMPT("12. HTTP retry single attempt"),
  RETRY_COMPLETE("13. HTTP retry complete operation"),
  RETRY_DELAY("14. HTTP retry delay time");
  ```
- [ ] Update all descriptions to include ordinal number prefix
- [ ] Update class javadoc to explain ordinal-based execution order
- [ ] Note in javadoc which pipelines use which metrics:
  - [ ] AccessTokenValidationPipeline: Uses ordinals 0-10
  - [ ] IdTokenValidationPipeline: No metrics (NoOpMetricsTicker)
  - [ ] RefreshTokenValidationPipeline: No metrics (NoOpMetricsTicker)
  - [ ] JWKS and Retry: Cross-cutting concerns

**Benefits**: Uses natural enum `ordinal()`, clear visual representation, easier to identify bottlenecks

---

### 📊 VERIFICATION CHECKPOINT 3 (After Tasks 5-6)

Run quality verification build:
```bash
./mvnw -Ppre-commit clean verify -pl cui-jwt-validation
```

**Must verify**:
- [ ] All imports resolved correctly after package reorganization
- [ ] Metrics enum compiles successfully
- [ ] All tests pass with new package structure
- [ ] Code quality checks pass (checkstyle, spotbugs, PMD)
- [ ] No warnings or errors in build output

**If failures occur**: Fix ALL issues before proceeding to Task 7

---

### Task 7: Restructure Unit Tests (One Test Class Per Production Class)

**Purpose**: Create dedicated test classes for each production class, ensuring complete coverage

**Implementation**:

#### New Pipeline Test Classes (CREATE)
- [ ] Create `AccessTokenValidationPipelineTest.java`:
  - [ ] Test full validation pipeline
  - [ ] Test cache hit behavior (early return)
  - [ ] Test cache miss behavior (full validation)
  - [ ] Test metrics instrumentation for all steps
  - [ ] Test exception handling
  - [ ] Test security event counting
  - [ ] Mock all dependencies (parser, resolver, validators, cache, monitor)
- [ ] Create `IdTokenValidationPipelineTest.java`:
  - [ ] Test full validation pipeline
  - [ ] Test all validation steps execute in order
  - [ ] Test exception handling
  - [ ] Test security event counting
  - [ ] Verify no metrics instrumentation (NoOpMetricsTicker)
  - [ ] Mock all dependencies
- [ ] Create `RefreshTokenValidationPipelineTest.java`:
  - [ ] Test minimal validation
  - [ ] Test JWT parsing success case (claims extracted)
  - [ ] Test JWT parsing failure case (empty claims)
  - [ ] Test security event counting
  - [ ] Verify no metrics instrumentation
  - [ ] Mock dependencies

#### New Validator Test Classes (CREATE)
- [ ] Create `TokenStringValidatorTest.java`:
  - [ ] Test null token → throws `TokenValidationException(TOKEN_EMPTY)`
  - [ ] Test blank token → throws `TokenValidationException(TOKEN_EMPTY)`
  - [ ] Test empty string → throws `TokenValidationException(TOKEN_EMPTY)`
  - [ ] Test token exceeding max size → throws `TokenValidationException(TOKEN_TOO_LARGE)`
  - [ ] Test valid token within size limit → passes
  - [ ] Test security event counter increments correctly
  - [ ] Test logging output
- [ ] Create `MeasurementTypeTest.java`:
  - [ ] Test ordinal order matches description numbers (0-14)
  - [ ] Test pipeline execution sequence correctness
  - [ ] Test toString() returns description
  - [ ] Test all enum values present

#### Move Existing Test Classes (MOVE)
- [ ] Move test files to `pipeline/validator/` package:
  - [ ] `TokenSignatureValidatorTest.java` → update package to `pipeline.validator`
  - [ ] `TokenSignatureValidatorAlgorithmTest.java` → update package
  - [ ] `TokenSignatureValidatorES256FormatTest.java` → update package
  - [ ] `TokenSignatureValidatorEdgeCasesTest.java` → update package
  - [ ] `TokenHeaderValidatorTest.java` → update package
  - [ ] `TokenClaimValidatorTest.java` → update package
  - [ ] `TokenClaimValidatorEdgeCaseTest.java` → update package
  - [ ] `AudienceValidatorTest.java` → update package
  - [ ] `ExpirationValidatorTest.java` → update package
  - [ ] `MandatoryClaimsValidatorTest.java` → update package
  - [ ] `AuthorizedPartyValidatorTest.java` → update package
- [ ] Update package declarations in all moved test files

#### Refactor Existing Test Classes (REFACTOR)
- [ ] Refactor `TokenValidatorTest.java`:
  - [ ] Update tests to verify delegation to TokenStringValidator
  - [ ] Update tests to verify delegation to pipelines
  - [ ] Update tests to verify builder initialization of pipelines
  - [ ] Keep integration tests (end-to-end behavior)
  - [ ] Update imports for new package structure

#### Test Coverage Requirements
Each test class must cover:
- [ ] Happy path (valid inputs)
- [ ] Validation failures (invalid inputs throw correct exceptions)
- [ ] Edge cases (boundary conditions)
- [ ] Security events (verify SecurityEventCounter increments)
- [ ] Metrics (where applicable, verify correct MeasurementType)

---

### 📊 VERIFICATION CHECKPOINT 4 (After Task 7)

Run quality verification build:
```bash
./mvnw -Ppre-commit clean verify -pl cui-jwt-validation
```

**Must verify**:
- [ ] All test classes compile successfully
- [ ] All tests pass with complete coverage
- [ ] Test package structure mirrors production structure
- [ ] Coverage metrics meet or exceed previous levels
- [ ] Code quality checks pass (checkstyle, spotbugs, PMD)
- [ ] No warnings or errors in build output

**If failures occur**: Fix ALL issues before proceeding to final verification

---

## 🎯 FINAL VERIFICATION & COMMIT

Use custom command to run full quality + integration build cycle:

```bash
verifyAndCommit cui-jwt-validation
```

This command will:
1. [ ] Run pre-commit profile build: `./mvnw -Ppre-commit clean verify -pl cui-jwt-validation`
   - Fix ALL errors and warnings before proceeding
2. [ ] Run full integration build: `./mvnw clean install -pl cui-jwt-validation`
   - Fix ALL test failures and build errors
   - Wait for completion (~8-10 minutes)
3. [ ] Verify no build artifacts in source directories:
   - [ ] No `.class` files in `src/main/java` or `src/test/java`
   - [ ] No `.jar` or `.war` files in `src/`
   - [ ] No `target/` directories in `src/`
4. [ ] Create git commit with descriptive message
   - Include Co-Authored-By: Claude footer

**Critical Rules**:
- ✅ NEVER skip error fixes - Every warning and error must be resolved
- ✅ NEVER use shortcuts - Run complete verification cycles
- ✅ NEVER commit with failing builds - Only commit when everything passes
- ✅ NEVER commit with source artifacts - Source directories must be clean
- ✅ ALWAYS fix issues systematically - Address root causes, not symptoms

---

## Summary

**End Result**: Clean separation of concerns, dedicated pipelines per token type, early cache optimization, comprehensive test coverage.

- TokenValidator: 557 lines → ~180 lines
- New pipelines: 3 dedicated classes for access/id/refresh tokens
- New validator: TokenStringValidator for pre-pipeline validation
- Organized package structure: `pipeline/validator/` subpackage
- Ordered metrics: Execution sequence reflected in enum ordinals
- Complete test coverage: One test class per production class

**Breaking Changes**: Acceptable (pre-1.0 project). Clean refactoring over backwards compatibility.
