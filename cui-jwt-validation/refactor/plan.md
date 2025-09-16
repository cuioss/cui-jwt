# JWT Validation Architecture Refactoring Plan

## Task Completion Process (Mandatory for Each Task)

After implementing any task, **strictly follow this completion process**:

1. **Quality Verification**: `./mvnw -Ppre-commit clean verify -pl cui-jwt-validation`
   - ⚠️ **Do not cancel** - let it run through completely
   - ⚠️ **Examine all build output** - read every warning and error
   - ⚠️ **Fix ALL failures/errors/warnings** (mandatory) - no shortcuts allowed

2. **Handle Build Artifacts**:
   - Some recipes may add markers - either fix them or suppress with proper justification
   - **Never commit markers** - they must be resolved
   - Address code quality, formatting, and linting issues

3. **Final Verification**: `./mvnw clean install -pl cui-jwt-validation`
   - Must complete without errors or warnings
   - All tests must pass

4. **Update Progress**: Mark task as completed `[x]` in this plan.md

5. **Commit**: After verified build, commit changes with task identifier in message

---

## Code Structure and Design Tasks

### C1. Update JwksLoader Interface for Async Initialization
[x] **Priority:** High

**Description:** Modify the JwksLoader interface to return CompletableFuture from initJWKSLoader method, enabling unified async loading pattern for all loader types.

**Rationale:** This is the foundation change that enables the entire async architecture. All loaders (HTTP, memory, file) need unified async initialization.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/redesign.md` (lines 13-34)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/consistency-analysis.md` (Issue 4 analysis)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/jwks/JwksLoader.java`

---

### C2. Implement AtomicReference Status Management
[ ] **Priority:** High

**Description:** Replace complex state management in HttpJwksLoader with single AtomicReference<LoaderStatus> for lock-free status checks. Remove duplicate status methods.

**Rationale:** Health checks require instant, non-blocking status responses. Current implementation may block on I/O operations.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (Issues 2 & 3)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (lines 13, 127)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/test-driven-approach.md` (Lock-Free Status Tests)

---

### C3. Implement HttpJwksLoader Complete Redesign
[ ] **Priority:** High

**Description:** Replace existing HttpJwksLoader with clean implementation: simple constructor, async initialization, Optional return types, leverages ResilientHttpHandler.

**Rationale:** Current implementation has complex state management, race conditions, and blocks in constructor. Clean redesign addresses all architectural issues.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (complete implementation)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (all identified issues)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/jwks/http/HttpJwksLoader.java`

---

### C4. Implement Key Rotation Grace Period (Issue #110)
[ ] **Priority:** High

**Description:** Add RetiredKeySet management in HttpJwksLoader to maintain old keys during grace period, preventing immediate validation failures during key rotation.

**Rationale:** Critical security requirement to support key rotation without service disruption. Addresses Issue #110 directly.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (lines 114-121, 149-160, 207-216)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (Issue 5)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/test-driven-approach.md` (Key Rotation tests)

---

### C5. Enhance HttpJwksLoaderConfig with Overloaded Methods
[ ] **Priority:** Medium

**Description:** Add getHttpHandler(String url) overload and isBackgroundRefreshEnabled() method to centralize configuration logic and eliminate duplication.

**Rationale:** Reduces code duplication in handler creation and encapsulates background refresh decision logic in configuration.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (lines 222-248)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/jwks/http/HttpJwksLoaderConfig.java`

---

### C6. Enhance IssuerConfigResolver with Async Loading Management
[ ] **Priority:** High

**Description:** Modify IssuerConfigResolver constructor to trigger async loading for all enabled configs and manage CompletableFuture lifecycle. Only return configs with LoaderStatus.OK.

**Rationale:** Eliminates initialization race conditions and provides central coordination point. Replaces JwksStartupService functionality.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/redesign.md` (lines 36-107)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (Issue 7)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/IssuerConfigResolver.java`

---

### C7. Remove JwksStartupService
[ ] **Priority:** Medium

**Description:** Delete JwksStartupService class as async loading is now handled by enhanced IssuerConfigResolver.

**Rationale:** Eliminates redundant loading triggers and race conditions. Functionality is now centralized in IssuerConfigResolver.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (Issue 4 - Multiple Redundant Loading Triggers)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/consistency-analysis.md` (Issue 4 verification)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/jwks/startup/JwksStartupService.java`

---

### C8. Update Quarkus Integration - Remove JwksStartupService Usage
[ ] **Priority:** High

**Description:** Remove JwksStartupService references from Quarkus module CDI configuration, TokenValidatorProducer, and any related Quarkus-specific startup logic.

**Rationale:** JwksStartupService is being deleted, so Quarkus integration must be updated to rely on enhanced IssuerConfigResolver for async loading.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (startup sequence analysis)
- Quarkus module TokenValidatorProducer class
- Any Quarkus @Startup or @PostConstruct annotations referencing JwksStartupService

---

### C9. Update Quarkus Health Checks for Lock-Free Status
[ ] **Priority:** High

**Description:** Update Quarkus MicroProfile Health integration to use new lock-free getLoaderStatus() method and remove any blocking status check patterns.

**Rationale:** Health checks must be instant and non-blocking. New AtomicReference status approach enables proper health check implementation.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (Issue 3 - Not Lock-Free Status Checks)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (lock-free status implementation)
- Quarkus module health check implementations
- MicroProfile Health specification requirements

---

## Testing Improvements Tasks

### T1. Add Async Initialization Tests
[ ] **Priority:** High

**Description:** Create comprehensive tests for new async initialization behavior: constructor non-blocking, CompletableFuture handling, well-known discovery in async context.

**Rationale:** New async functionality must be thoroughly tested to prevent regressions and ensure correct behavior.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/test-driven-approach.md` (Async Initialization section)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/test/java/de/cuioss/jwt/validation/jwks/http/HttpJwksLoaderTest.java`

---

### T2. Add Lock-Free Status Check Tests
[ ] **Priority:** High

**Description:** Implement high-contention concurrent tests to verify status checks are truly lock-free and atomic state transitions work correctly.

**Rationale:** Lock-free behavior is critical for health checks and system performance. Must be verified under concurrent load.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/test-driven-approach.md` (Lock-Free Status Tests)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/test/java/de/cuioss/jwt/validation/IssuerConfigResolverConcurrencyTest.java`

---

### T3. Add Key Rotation Grace Period Tests
[ ] **Priority:** High

**Description:** Create tests for RetiredKeySet functionality: grace period behavior, cleanup after expiration, maximum retired sets limit enforcement.

**Rationale:** Key rotation is a critical security feature that must work correctly. Grace period logic needs thorough validation.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/test-driven-approach.md` (Key Rotation Grace Period section)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (RetiredKeySet implementation)

---

### T4. Add IssuerConfigResolver Async Loading Tests
[ ] **Priority:** Medium

**Description:** Test enhanced IssuerConfigResolver: constructor triggers async loading, only returns healthy configs, CompletableFuture management, timeout handling.

**Rationale:** IssuerConfigResolver is now central to async loading coordination. Its behavior must be thoroughly tested.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/test-driven-approach.md` (IssuerConfigResolver Async Loading section)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/redesign.md` (Enhanced IssuerConfigResolver)

---

### T5. Extend Background Refresh Error Handling Tests
[ ] **Priority:** Medium

**Description:** Add tests for specific exception handling in background refresh: IOException, JwksParseException, InvalidKeySpecException. Verify no generic RuntimeException handling.

**Rationale:** Background refresh error handling was improved to use specific exceptions. This must be tested to ensure proper error recovery.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/test-driven-approach.md` (Background Refresh Extended Tests)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (background refresh error handling)

---

## Performance Improvements Tasks

### P1. Verify Lock-Free Performance Improvement
[ ] **Priority:** Medium

**Description:** Create benchmark tests to verify that AtomicReference status checks provide better performance than previous synchronized approach.

**Rationale:** Performance improvement is a key goal of the refactoring. Must be measurable and verified.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (Issue 3 - Not Lock-Free Status Checks)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/test/java/de/cuioss/jwt/validation/IssuerConfigResolverPerformanceTest.java`

---

### P2. Validate Non-Blocking Startup Performance
[ ] **Priority:** Medium

**Description:** Measure and verify that constructor-based initialization no longer blocks application startup. Compare startup times before and after refactoring.

**Rationale:** Non-blocking startup is a key architectural improvement. Must be verified to ensure goal is achieved.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (constructor simplicity)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/test-driven-approach.md` (constructor timing tests)

---

## Documentation Improvements Tasks

### DOC1. Update API Documentation for Async Changes
[ ] **Priority:** Medium

**Description:** Update JavaDoc for JwksLoader interface and HttpJwksLoader to reflect new async initialization pattern and CompletableFuture usage.

**Rationale:** API changes require updated documentation for developers using the library.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/redesign.md` (interface changes)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/src/main/java/de/cuioss/jwt/validation/jwks/JwksLoader.java`

---

### DOC2. Document Key Rotation Grace Period Configuration
[ ] **Priority:** Medium

**Description:** Add documentation explaining key rotation grace period configuration, RetiredKeySet behavior, and best practices for key rotation.

**Rationale:** New key rotation feature needs proper documentation for operations teams.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (RetiredKeySet functionality)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (Issue #110 context)

---

## Security Enhancements Tasks

### S1. Validate Framework Independence
[ ] **Priority:** High

**Description:** Verify that refactored implementation maintains framework independence and works correctly in Quarkus, NiFi, and plain Java environments.

**Rationale:** Critical requirement to maintain multi-framework compatibility. Must not break existing NiFi deployments.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (Framework Independence Requirement)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/consistency-analysis.md` (Framework Independence verification)

---

### S2. Verify ResilientHttpHandler Integration
[ ] **Priority:** Medium

**Description:** Ensure that new implementation properly leverages existing ResilientHttpHandler capabilities without reimplementing retry logic.

**Rationale:** Must not duplicate existing resilience functionality. ResilientHttpHandler provides critical caching and retry capabilities.

**Required Reading:**
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/analyzis.md` (ResilientHttpHandler analysis)
- `/Users/oliver/git/cui-jwt/cui-jwt-validation/refactor/httpjwksloader-clean.md` (ResilientHttpHandler usage)

---

