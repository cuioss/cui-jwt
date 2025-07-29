# JWT Validation Performance Optimization Tasks

## Current Performance Issues

Following the setup isolation fix, true performance bottlenecks are revealed:

1. **Signature Validation**: 17.9ms p99 spikes (262x p50 ratio)
2. **Token Building**: 3.7ms p99 spikes (335x p50 ratio)  
3. **Claims Validation**: 1.3ms p99 spikes (217x p50 ratio)

**Current Baseline**: 102,956 ops/s throughput with p99 latencies reaching 32.3ms

## Prioritized Optimization Tasks with Action Items
Consider
### 1. Metrics Recording Optimization with Ticker Object (High Priority)

**Location**: `TokenValidator.java` - multiple methods with metrics recording pattern
**Issue**: Repetitive pattern of `long startTime = recordMetrics ? System.nanoTime() : 0;` followed by `if (recordMetrics) { ... }`
**Impact**: Code duplication and potential overhead from conditional checks

**Action Items:**
- [x] Create a new `MetricsTicker` class that encapsulates timing logic:
  ```java
  public interface MetricsTicker {
      void startRecording();
      void stopAndRecord();
  }
  ```
- [x] Implement two versions:
  - `NoOpMetricsTicker` - does nothing when `recordMetrics` is false
  - `ActiveMetricsTicker` - tracks timing and records to `TokenValidatorMonitor`
- [x] Create a single ActiveMetricsTicker that encapsulates the MeasurementType:
  ```java
  public class ActiveMetricsTicker implements MetricsTicker {
      private final TokenValidatorMonitor monitor;
      private final MeasurementType measurementType;
      private long startTime;
      
      public void stopAndRecord() {
          monitor.recordMeasurement(measurementType, 
                                   System.nanoTime() - startTime);
      }
  }
  ```
- [x] Add factory method to MeasurementType enum to create appropriate ticker:
  ```java
  public MetricsTicker createTicker(TokenValidatorMonitor monitor, boolean recordMetrics) {
      if (!recordMetrics) {
          return NoOpMetricsTicker.INSTANCE;
      }
      return new ActiveMetricsTicker(monitor, this);
  }
  ```
- [x] Update method signatures to accept MetricsTicker instead of boolean recordMetrics:
  ```java
  private void validateTokenFormat(String tokenString, MetricsTicker ticker)
  private DecodedJwt decodeToken(String tokenString, MetricsTicker ticker)
  private String validateAndExtractIssuer(DecodedJwt decodedJwt, MetricsTicker ticker)
  // ... other validation methods
  ```
- [x] Update processTokenPipeline to create tickers and pass them to methods:
  ```java
  private <T extends TokenContent> T processTokenPipeline(
          String tokenString,
          TokenType tokenType,
          TokenBuilderFunction<T> tokenBuilder,
          boolean recordMetrics) {
      
      // Create all tickers at the beginning
      MetricsTicker formatTicker = MeasurementType.TOKEN_FORMAT_CHECK.createTicker(performanceMonitor, recordMetrics);
      MetricsTicker decodeTicker = MeasurementType.TOKEN_PARSING.createTicker(performanceMonitor, recordMetrics);
      MetricsTicker issuerTicker = MeasurementType.ISSUER_EXTRACTION.createTicker(performanceMonitor, recordMetrics);
      // ... other tickers
      
      MetricsTicker pipelineTicker = MeasurementType.COMPLETE_VALIDATION.createTicker(performanceMonitor, recordMetrics);
      pipelineTicker.startRecording();
      try {
          validateTokenFormat(tokenString, formatTicker);
          DecodedJwt decodedJwt = decodeToken(tokenString, decodeTicker);
          String issuer = validateAndExtractIssuer(decodedJwt, issuerTicker);
          // ... rest of pipeline
      } finally {
          pipelineTicker.stopAndRecord();
      }
  }
  ```
- [x] Update individual methods to use ticker pattern:
  ```java
  private void validateTokenFormat(String tokenString, MetricsTicker ticker) {
      ticker.startRecording();
      try {
          // existing validation logic
      } finally {
          ticker.stopAndRecord();
      }
  }
  ```
- [x] Ensure NOOP implementation has zero overhead
- [ ] Write unit tests for both ticker implementations
- [x] Run `./mvnw -Ppre-commit clean verify -DskipTests -pl cui-jwt-validation`
- [x] Run `./mvnw clean install -pl cui-jwt-validation`
- [ ] Commit changes to git

### 2. Access Token Cache Implementation (High Priority)

**Location**: New `AccessTokenCache` class to be created
**Issue**: Currently validating the same access tokens repeatedly
**Impact**: Redundant cryptographic operations and validation overhead

**Integration Strategy**: Place cache check AFTER initial format validation to leverage existing length and format checks:
1. Token enters `processTokenPipeline` method
2. `validateTokenFormat` is called first - handles empty token validation
3. Token is decoded by `jwtParser.decode()` - handles length validation (maxTokenSize) and format validation
4. THEN check cache before expensive operations (signature validation, claim validation)
5. This ensures all security logging and monitoring happens correctly without duplication

**Action Items:**
- [ ] Create `AccessTokenCache` class with configuration:
  ```java
  public class AccessTokenCache {
      private final int maxSize;
      private final Map<String, CachedToken> cache;
      private final SecurityEventCounter securityEventCounter;
      // configuration for eviction thread timing
  }
  ```
- [ ] Design `CachedToken` wrapper:
  ```java
  public class CachedToken {
      private final String rawToken;
      private final AccessTokenContent content;
      private final OffsetDateTime expirationTime;
  }
  ```
- [ ] Implement main cache method using Map's computeIfAbsent pattern:
  ```java
  public AccessTokenContent computeIfAbsent(
      String tokenKey, 
      Function<String, AccessTokenContent> validationFunction
  )
  ```
- [ ] Key design considerations:
  - Use token hash (e.g., SHA-256) as key instead of full string
  - Length validation already handled by jwtParser.decode() before cache check
  - Compare actual raw token string on cache hit to prevent hash collisions
- [ ] Implement cache validation on retrieval:
  - Verify raw token matches the cached entry
  - Check if token is not expired using `TokenContent#isExpired`
  - Return null if validation fails, triggering revalidation
  - Increment security event counter on cache hit for monitoring
- [ ] Create background eviction thread:
  - Configurable execution interval
  - Remove all expired tokens from cache
  - Use ScheduledExecutorService for periodic cleanup
- [ ] Performance and security validation:
  - Ensure thread-safe implementation using ConcurrentHashMap
  - Implement size-based eviction (LRU or similar)
  - No external dependencies (no Caffeine due to Quarkus constraints)
  - Verify no sensitive data leakage in cache keys
- [ ] Integration with TokenValidator:
  - Add AccessTokenCache field to TokenValidator
  - Modify `processTokenPipeline` to check cache after `decodeToken` but before `validateTokenSignature`
  - Cache key should include issuer to respect issuer boundaries
  - Only cache successful validations of AccessTokenContent
  - Pass SecurityEventCounter to cache for event tracking
- [ ] Add new security events to JWTValidationLogMessages.DEBUG:
  ```java
  public static final LogRecord ACCESS_TOKEN_CACHE_HIT = LogRecordModel.builder()
      .prefix(PREFIX)
      .identifier(508)
      .template("Access token retrieved from cache")
      .build();
  ```
- [ ] Add corresponding EventType to SecurityEventCounter:
  ```java
  ACCESS_TOKEN_CACHE_HIT(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CACHE_HIT, null)
  ```
- [ ] Write comprehensive unit tests:
  - Test cache hit/miss scenarios
  - Test expiration handling
  - Test concurrent access
  - Test eviction policies
  - Test security event counter increments on cache hits
- [ ] Run `./mvnw -Ppre-commit clean verify -DskipTests -pl cui-jwt-validation`
- [ ] Run `./mvnw clean install -pl cui-jwt-validation`
- [ ] Commit changes to git

## Summary

The optimization plan focuses on:
1. **Metrics recording optimization** - Eliminate code duplication and conditional overhead with ticker pattern
2. **Token caching** - Avoid redundant validation of the same access tokens

These optimizations follow the existing patterns in the codebase and target performance improvements through reduced redundancy and cleaner code structure.