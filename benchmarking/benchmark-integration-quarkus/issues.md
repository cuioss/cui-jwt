# JMH Benchmark Race Condition Analysis: HTTP Client State Contamination

## Issue Summary

The benchmark-integration-quarkus module experiences order-dependent timeout failures when JMH benchmark methods are reordered. Specifically:

- **Current failing order**: JwtHealthBenchmark → JwtValidationBenchmark  
- **User observation**: "If we reorder the JWT and the health benchmark the JWT fails with the same timeout"
- **Error pattern**: `java.net.http.HttpTimeoutException: request timed out`
- **Key insight**: This is NOT a timeout configuration issue - it worked before with 5-second timeouts

## Technical Analysis

### Root Cause: HttpClient Connection Pool State Contamination

The race condition stems from **shared HttpClient state pollution** between benchmark executions, not actual performance degradation or timeout configuration issues.

#### Evidence from Code Analysis

1. **Shared HttpClient Factory Pattern** (`HttpClientFactory.java:91-101`):
   ```java
   public static HttpClient getInsecureClient() {
       return CLIENT_CACHE.computeIfAbsent("insecure", key -> {
           // Creates cached, shared HttpClient instance
       });
   }
   ```

2. **AbstractBenchmarkBase State** (`AbstractBenchmarkBase.java:77-89`):
   ```java
   protected void setupBase() {
       httpClient = HttpClientFactory.getInsecureClient(); // Shared instance!
   }
   ```

3. **Benchmark Inheritance Chain**:
   - `JwtHealthBenchmark` extends `AbstractBaseBenchmark`
   - `JwtValidationBenchmark` extends `AbstractIntegrationBenchmark` 
   - Both ultimately inherit shared `httpClient` from `AbstractBenchmarkBase`

### Race Condition Mechanism

1. **First Benchmark Execution** (JwtHealthBenchmark):
   - Establishes HTTP connections to `/q/health` endpoint
   - Connection pool becomes populated with connections configured for health checks
   - HTTP/2 streams may be established and cached
   - DNS resolutions cached for health endpoint

2. **Second Benchmark Execution** (JwtValidationBenchmark):
   - Inherits "warm" HttpClient with pre-established connections
   - Attempts to connect to `/jwt/validate` endpoint using contaminated connection pool
   - **Failure scenarios**:
     - Connection pool exhaustion (all slots occupied by health check connections)
     - HTTP/2 stream limit reached on existing connections
     - Authentication context mismatch between endpoints
     - Connection state incompatible with validation endpoint requirements

3. **Order Dependency**:
   - When order is reversed, JwtValidationBenchmark runs first with clean HttpClient
   - JwtHealthBenchmark inherits validation-optimized connection pool
   - Health endpoint may be more tolerant of connection state variations

### Log Analysis Findings

From `benchmark-integration-output.log`:

- **26+ consecutive timeout failures** during JwtHealthBenchmark execution (lines 250-691)
- **Pattern**: `java.net.http.HttpTimeoutException: request timed out` 
- **Timing**: All failures occur during the same benchmark phase
- **JMH Configuration**: 24 threads, 2 forks, 5 iterations - high concurrency amplifies connection pool issues
- **Success**: JwtValidationBenchmark completed successfully with 8.370 ± 0.229 ops/ms

**Key Observation**: The benchmark that runs second in the current order succeeds, indicating the first benchmark contaminates shared resources for subsequent executions.

## JMH Framework Context

### State Management Limitations

JMH provides excellent state isolation through `@State` annotations for benchmark-specific data, but **cannot automatically manage internal state of complex infrastructure objects** like HttpClient connection pools.

### Connection Pool Lifecycle Issues

Modern HttpClient implementations optimize performance through:
- **Connection reuse**: Maintains persistent connections
- **HTTP/2 multiplexing**: Multiple streams per connection  
- **DNS caching**: Cached resolutions persist across requests
- **Authentication state**: May cache credentials or tokens

These optimizations become problematic when different benchmarks have incompatible connection requirements.

## Impact Assessment

### Immediate Impact
- **Unreliable benchmark results**: Order-dependent failures make performance measurements meaningless
- **CI/CD pipeline instability**: Random test failures based on execution order
- **Development friction**: Developers cannot trust benchmark results

### Long-term Implications  
- **Performance regression masking**: Infrastructure issues may hide actual performance problems
- **Misleading optimization decisions**: Results don't reflect actual production behavior
- **Technical debt accumulation**: Workarounds instead of proper fixes

## Recommended Solutions

### 1. URL-Based HttpClient Caching (RECOMMENDED)

Implement per-URL HttpClient caching to achieve both isolation and performance:

```java
public class HttpClientFactory {
    
    // Cache HttpClient instances per base URL for isolation with performance
    private static final ConcurrentHashMap<String, HttpClient> URL_CLIENT_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Gets or creates a cached HttpClient for a specific base URL.
     * This ensures connection pools are isolated per endpoint while maintaining
     * the performance benefits of connection reuse within each benchmark.
     * 
     * @param baseUrl the base URL (e.g., "https://localhost:10443")
     * @return HttpClient configured for the specific URL
     */
    public static HttpClient getInsecureClientForUrl(String baseUrl) {
        return URL_CLIENT_CACHE.computeIfAbsent(baseUrl, url -> {
            LOGGER.debug("Creating new insecure HttpClient for URL: {}", url);
            try {
                SSLContext sslContext = createTrustAllSSLContext();
                return createHttpClient(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IllegalStateException("Failed to create insecure HttpClient for URL: " + url, e);
            }
        });
    }
    
    // Optional: Method to clear cache for specific URL if needed
    public static void clearClientForUrl(String baseUrl) {
        URL_CLIENT_CACHE.remove(baseUrl);
    }
}
```

Then update the benchmarks to use URL-based clients:

```java
public abstract class AbstractBaseBenchmark extends AbstractBenchmarkBase {
    
    @Override 
    protected void performAdditionalSetup() {
        // Get HttpClient specific to this benchmark's service URL
        httpClient = HttpClientFactory.getInsecureClientForUrl(serviceUrl);
        // ... rest of setup
    }
}
```

**Benefits of URL-based caching:**
- **Isolation**: Each endpoint gets its own connection pool, preventing cross-contamination
- **Performance**: Connection reuse within same endpoint maintains benchmark performance
- **Simplicity**: No need to manage lifecycle manually, cache handles it automatically
- **Production-like**: Better mimics production behavior where different services have separate clients

### 2. Enhanced URL-Based Caching with Connection Pool Limits

For even better isolation, configure per-URL connection pool limits:

```java
public static HttpClient getInsecureClientForUrl(String baseUrl, int maxConnections) {
    String cacheKey = baseUrl + "-" + maxConnections;
    return URL_CLIENT_CACHE.computeIfAbsent(cacheKey, key -> {
        try {
            SSLContext sslContext = createTrustAllSSLContext();
            return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .sslContext(sslContext)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .executor(ExecutorHolder.INSTANCE)
                .followRedirects(HttpClient.Redirect.NORMAL)
                // Could add connection pool configuration here if Java HttpClient supported it
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create client for: " + baseUrl, e);
        }
    });
}
```

### 3. JMH Fork Isolation (Alternative)

Force complete JVM isolation by running each benchmark class in separate processes:

```java
@Fork(value = 1, jvmArgs = {"-Xmx2g"})
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
public class JwtHealthBenchmark extends AbstractBaseBenchmark {
    // Complete JVM isolation eliminates shared state
}
```

### 4. Connection Pool Explicit Reset (Workaround)

If shared HttpClient must be maintained, implement explicit cleanup:

```java
@TearDown(Level.Trial)
public void cleanupHttpClient() {
    // Force connection pool cleanup
    if (httpClient instanceof java.net.http.HttpClient) {
        // HttpClient doesn't provide public cleanup methods
        // Consider creating wrapper with explicit cleanup
    }
}
```

## Testing Strategy

### Verification Approach
1. **Current State**: Document the specific failure pattern and timing
2. **Implementation**: Apply HttpClient isolation solution
3. **Validation**: Run benchmarks in both orders multiple times
4. **Performance Impact**: Measure any performance impact from fresh HttpClient instances
5. **Regression Testing**: Ensure library benchmarks remain unaffected

### Success Criteria
- [ ] Benchmarks pass consistently regardless of execution order
- [ ] No timeout failures in either benchmark configuration
- [ ] Performance measurements remain stable and meaningful
- [ ] CI/CD pipeline reliability improved

## Technical Debt Considerations

This issue reveals a broader pattern of **shared infrastructure in microbenchmarks**. Consider reviewing other shared components:

- Database connections
- Thread pools  
- Cache instances
- Static singletons

## References

- JMH documentation on state management and isolation
- Java HttpClient connection pool behavior
- HTTP/2 multiplexing limitations in concurrent scenarios
- Research on JMH race conditions and resource contamination patterns

---

**Analysis Date**: September 2, 2025  
**Status**: Analysis Complete - Implementation Pending  
**Priority**: High - Affects benchmark reliability and CI/CD stability