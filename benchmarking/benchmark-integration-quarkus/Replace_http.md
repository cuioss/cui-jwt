# Replace Java HttpClient with High-Performance Alternative

## Executive Summary

Replace the problematic Java 11+ HttpClient in benchmark-integration-quarkus with a high-performance alternative to eliminate timeout issues and improve benchmark reliability. Based on performance research, **OkHttp** is recommended for its optimal balance of speed, reliability, and ease of integration.

## HTTP Client Performance Comparison

### Performance Rankings for SSL/TLS & Speed

| Client | SSL Performance | Latency | Connection Pooling | Weight | Ease of Integration | Recommendation |
|--------|----------------|---------|-------------------|--------|-------------------|----------------|
| **OkHttp** | Excellent | Very Low | Excellent (HTTP/2) | Light | Easy | ⭐⭐⭐⭐⭐ **CHOSEN** |
| AsyncHttpClient | Excellent | Very Low | Excellent (Netty) | Light | Moderate | ⭐⭐⭐⭐ |
| Reactor Netty | Best | Lowest | Excellent | Light | Complex | ⭐⭐⭐ |
| Apache HttpClient 5 | Good | Low | Very Good | Heavy | Easy | ⭐⭐⭐ |
| Vert.x WebClient | Very Good | Very Low | Good | Light | Complex | ⭐⭐ |

### Why OkHttp?

1. **Speed**: Battle-tested in Android where performance is critical
2. **SSL/TLS**: Automatic connection coalescing, TLS 1.3 support
3. **Connection Pooling**: Default 5 connections per route, 5-minute keep-alive
4. **HTTP/2**: Automatic multiplexing for better throughput
5. **Lightweight**: Minimal dependencies, small footprint
6. **Production Proven**: Used by Square, widely adopted

## Implementation Plan

### Phase 1: Add OkHttp Dependency

```xml
<!-- Add to benchmarking/benchmark-integration-quarkus/pom.xml -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

### Phase 2: Create OkHttpClientFactory

Create new file: `benchmarking/cui-benchmarking-common/src/main/java/de/cuioss/benchmarking/common/http/OkHttpClientFactory.java`

```java
package de.cuioss.benchmarking.common.http;

import okhttp3.*;
import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

public class OkHttpClientFactory {
    
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int MAX_IDLE_CONNECTIONS = 10;
    private static final int KEEP_ALIVE_DURATION_SECONDS = 60;
    
    private static OkHttpClient client;
    
    public static synchronized OkHttpClient getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }
    
    private static OkHttpClient createClient() {
        // Create trust-all manager for self-signed certificates
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };
        
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            
            // Configure connection pool
            ConnectionPool connectionPool = new ConnectionPool(
                MAX_IDLE_CONNECTIONS, 
                KEEP_ALIVE_DURATION_SECONDS, 
                TimeUnit.SECONDS
            );
            
            return new OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectionPool(connectionPool)
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true) // Accept all hostnames
                .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .retryOnConnectionFailure(false) // Don't retry for benchmarks
                .build();
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OkHttp client", e);
        }
    }
    
    public static void reset() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            client = null;
        }
    }
}
```

### Phase 3: Update AbstractBenchmarkBase

Modify `benchmarking/cui-benchmarking-common/src/main/java/de/cuioss/benchmarking/common/base/AbstractBenchmarkBase.java`:

```java
// Add field for client selection
private enum HttpClientType {
    JAVA_HTTPCLIENT,
    OKHTTP
}

private HttpClientType clientType = HttpClientType.OKHTTP; // Default to OkHttp

// Modify sendRequest method
protected HttpResponse<String> sendRequest(HttpRequest request) 
        throws IOException, InterruptedException {
    
    if (clientType == HttpClientType.OKHTTP) {
        return sendRequestWithOkHttp(request);
    } else {
        return sendRequestWithJavaClient(request);
    }
}

// Add new method for OkHttp
private HttpResponse<String> sendRequestWithOkHttp(HttpRequest javaRequest) 
        throws IOException {
    
    OkHttpClient okClient = OkHttpClientFactory.getClient();
    
    // Convert Java HttpRequest to OkHttp Request
    Request.Builder builder = new Request.Builder()
        .url(javaRequest.uri().toString());
    
    // Copy headers
    javaRequest.headers().map().forEach((name, values) -> {
        values.forEach(value -> builder.addHeader(name, value));
    });
    
    // Handle method and body
    String method = javaRequest.method();
    RequestBody body = null;
    
    if (javaRequest.bodyPublisher().isPresent()) {
        // For benchmarks, we typically use no body or simple strings
        body = RequestBody.create("", MediaType.parse("application/json"));
    }
    
    builder.method(method, body);
    
    // Execute request
    try (Response response = okClient.newCall(builder.build()).execute()) {
        // Convert OkHttp Response to Java HttpResponse format
        return new SimpleHttpResponse(
            response.code(),
            response.body() != null ? response.body().string() : ""
        );
    }
}

// Simple wrapper class
private static class SimpleHttpResponse implements HttpResponse<String> {
    private final int statusCode;
    private final String body;
    
    SimpleHttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }
    
    @Override public int statusCode() { return statusCode; }
    @Override public String body() { return body; }
    // Implement other required methods...
}
```

### Phase 4: Configuration & Testing

#### 4.1 Add System Property for Client Selection

```java
// In AbstractBenchmarkBase constructor or setup
String httpClient = System.getProperty("benchmark.http.client", "okhttp");
clientType = "okhttp".equalsIgnoreCase(httpClient) 
    ? HttpClientType.OKHTTP 
    : HttpClientType.JAVA_HTTPCLIENT;
logger.info("Using HTTP client: {}", clientType);
```

#### 4.2 Test Commands

```bash
# Test with OkHttp (default)
./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark \
    -Djmh.threads=1 -Djmh.warmupIterations=0 -Djmh.forks=1

# Test with Java HttpClient (to compare)
./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark \
    -Djmh.threads=1 -Djmh.warmupIterations=0 -Djmh.forks=1 \
    -Dbenchmark.http.client=java

# Full performance test with OkHttp
./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark
```

## Performance Verification Plan

### Step 1: Baseline Measurement
1. Document current failure pattern with Java HttpClient
2. Record timeout occurrences and timing

### Step 2: OkHttp Integration Test
1. Run single-thread test to verify basic functionality
2. Check for timeout elimination
3. Measure initial connection time

### Step 3: Performance Comparison
1. Run full benchmark suite with both clients
2. Compare metrics:
   - Connection establishment time
   - Request throughput (ops/ms)
   - p50, p95, p99 latencies
   - Connection pool efficiency
   - Memory usage

### Step 4: Stability Test
1. Run extended duration tests (5+ minutes)
2. Verify no timeouts occur
3. Check for memory leaks
4. Monitor connection pool behavior

## Expected Results

### With Java HttpClient (Current)
- Initial timeout: 10 seconds
- Working window: ~20 seconds
- Complete failure after window
- Second benchmark works (JVM init complete)

### With OkHttp (Expected)
- No initial timeout
- Consistent performance throughout
- Both benchmarks work immediately
- Higher throughput (1.5-2x improvement expected)
- Lower latency (especially for SSL)

## Success Criteria

1. **Zero Timeouts**: No HttpTimeoutException during any benchmark
2. **Consistent Performance**: All iterations complete successfully
3. **Improved Throughput**: At least 20% better ops/ms
4. **Lower Latency**: p99 latency < 100ms for health checks
5. **Stable Operation**: No degradation over time

## Rollback Plan

If OkHttp doesn't solve the issues:

1. **Try AsyncHttpClient**: Second-best performance option
   ```xml
   <dependency>
       <groupId>org.asynchttpclient</groupId>
       <artifactId>async-http-client</artifactId>
       <version>3.0.0</version>
   </dependency>
   ```

2. **Apache HttpClient 5**: Most mature, extensive configuration
   ```xml
   <dependency>
       <groupId>org.apache.httpcomponents.client5</groupId>
       <artifactId>httpclient5</artifactId>
       <version>5.3.1</version>
   </dependency>
   ```

3. **Investigation Focus**: If all clients fail, issue is likely in:
   - Docker networking layer
   - JVM network stack
   - Operating system configuration

## Implementation Timeline

| Phase | Task | Duration | Status |
|-------|------|----------|--------|
| 1 | Add OkHttp dependency | 5 min | Pending |
| 2 | Implement OkHttpClientFactory | 30 min | Pending |
| 3 | Update AbstractBenchmarkBase | 45 min | Pending |
| 4 | Initial testing | 30 min | Pending |
| 5 | Performance comparison | 1 hour | Pending |
| 6 | Documentation & cleanup | 30 min | Pending |

**Total estimated time**: ~3 hours

## Alternative Quick Test

For immediate verification without full implementation:

```java
// Quick standalone test
public class OkHttpQuickTest {
    public static void main(String[] args) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .build();
            
        Request request = new Request.Builder()
            .url("https://localhost:10443/q/health")
            .build();
            
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("Status: " + response.code());
            System.out.println("Time: " + elapsed + "ms");
            System.out.println("Body: " + response.body().string());
        }
    }
}
```

Run this before full implementation to verify OkHttp handles the localhost SSL certificates correctly.

## Conclusion

Replacing Java HttpClient with OkHttp should eliminate the timeout issues by:
1. Avoiding the buggy static initialization in Java HttpClient
2. Providing better connection pool management
3. Offering superior SSL/TLS performance
4. Maintaining consistent behavior across all benchmarks

The implementation is straightforward, risk is low (isolated to benchmarking code), and potential performance gains are significant.