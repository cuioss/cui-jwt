# Replace Java HttpClient with OkHttp

## Objective

Replace Java 11+ HttpClient with OkHttp in cui-benchmarking-common to eliminate the static initialization timeout bug and improve performance for all benchmarks.

## Implementation Plan

### Step 1: Add OkHttp Dependency

```xml
<!-- Add to benchmarking/cui-benchmarking-common/pom.xml -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>5.1.0</version>
</dependency>
```

### Step 2: Replace HttpClientFactory

Replace the entire content of `benchmarking/cui-benchmarking-common/src/main/java/de/cuioss/benchmarking/common/http/HttpClientFactory.java`:

```java
package de.cuioss.benchmarking.common.http;

import de.cuioss.tools.logging.CuiLogger;
import okhttp3.*;
import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating and managing OkHttp client instances for benchmarking.
 * Replaces Java HttpClient to avoid static initialization timeout bug.
 */
public class HttpClientFactory {
    
    private static final CuiLogger LOGGER = new CuiLogger(HttpClientFactory.class);
    
    // Cache clients by URL for better isolation
    private static final ConcurrentHashMap<String, OkHttpClient> clientCache = new ConcurrentHashMap<>();
    
    // Single shared insecure client for backward compatibility
    private static volatile OkHttpClient sharedInsecureClient;
    
    /**
     * Get an insecure OkHttp client for the specified URL.
     * Clients are cached per URL for better isolation.
     */
    public static OkHttpClient getInsecureClientForUrl(String url) {
        return clientCache.computeIfAbsent(url, k -> createInsecureClient());
    }
    
    /**
     * Get a shared insecure OkHttp client.
     * For backward compatibility when no specific URL is provided.
     */
    public static OkHttpClient getInsecureClient() {
        if (sharedInsecureClient == null) {
            synchronized (HttpClientFactory.class) {
                if (sharedInsecureClient == null) {
                    sharedInsecureClient = createInsecureClient();
                }
            }
        }
        return sharedInsecureClient;
    }
    
    private static OkHttpClient createInsecureClient() {
        try {
            // Trust all certificates for benchmark testing
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(10, 60, TimeUnit.SECONDS))
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .protocols(Collections.singletonList(Protocol.HTTP_2))
                .build();
                
            LOGGER.info("Created new OkHttp client with HTTP/2 protocol");
            return client;
            
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to create insecure OkHttp client", e);
        }
    }
}
```

### Step 3: Update AbstractBenchmarkBase

Modify `benchmarking/cui-benchmarking-common/src/main/java/de/cuioss/benchmarking/common/base/AbstractBenchmarkBase.java`:

1. Change the httpClient field type from HttpClient to OkHttpClient
2. Update initializeHttpClient() method
3. Replace sendRequest() implementation

```java
// Change field declaration (around line 56)
protected OkHttpClient httpClient;

// Replace initializeHttpClient() method (around line 95)
protected void initializeHttpClient() {
    if (serviceUrl != null && !serviceUrl.isEmpty()) {
        // Use URL-based caching for better isolation
        httpClient = HttpClientFactory.getInsecureClientForUrl(serviceUrl);
        logger.debug("Using URL-specific OkHttpClient for: {}", serviceUrl);
    } else {
        // Fallback to shared client if no serviceUrl is set
        httpClient = HttpClientFactory.getInsecureClient();
        logger.debug("Using shared OkHttpClient (no serviceUrl specified)");
    }
}

// Replace sendRequest() method (around line 168)
protected HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
    if (httpClient == null) {
        throw new IllegalStateException("HTTP client not initialized. Ensure setupBase() was called.");
    }
    
    // Build OkHttp request
    Request.Builder builder = new Request.Builder()
        .url(request.uri().toString());
    
    // Copy headers
    request.headers().map().forEach((name, values) -> 
        values.forEach(value -> builder.addHeader(name, value))
    );
    
    // Handle body
    RequestBody body = null;
    if (request.bodyPublisher().isPresent()) {
        // For benchmarks we only use JSON bodies
        body = RequestBody.create("", MediaType.parse("application/json"));
    }
    
    String method = request.method();
    builder.method(method, body);
    
    // Execute request
    try (Response response = httpClient.newCall(builder.build()).execute()) {
        String responseBody = response.body() != null ? response.body().string() : "";
        int statusCode = response.code();
        
        // Create HttpResponse wrapper
        return new HttpResponseWrapper(request, statusCode, responseBody);
    }
}

// Add inner class for HttpResponse wrapper (at end of class)
private static class HttpResponseWrapper implements HttpResponse<String> {
    private final HttpRequest request;
    private final int statusCode;
    private final String body;
    
    HttpResponseWrapper(HttpRequest request, int statusCode, String body) {
        this.request = request;
        this.statusCode = statusCode;
        this.body = body;
    }
    
    @Override
    public int statusCode() { return statusCode; }
    
    @Override
    public HttpRequest request() { return request; }
    
    @Override
    public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
    
    @Override
    public HttpHeaders headers() {
        return HttpHeaders.of(new HashMap<>(), (a, b) -> true);
    }
    
    @Override
    public String body() { return body; }
    
    @Override
    public Optional<SSLSession> sslSession() { return Optional.empty(); }
    
    @Override
    public URI uri() { return request.uri(); }
    
    @Override
    public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
}
```

### Step 4: Add Required Imports

Add these imports to AbstractBenchmarkBase.java:

```java
import okhttp3.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.util.HashMap;
import java.util.Optional;
import javax.net.ssl.SSLSession;
```

### Step 5: Build and Install cui-benchmarking-common

```bash
# Build and install the cui-benchmarking-common module with the OkHttp changes
./mvnw clean install -pl benchmarking/cui-benchmarking-common -DskipTests
```

### Step 6: Test

```bash
# Test with OkHttp - should work immediately without timeouts
./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark
```

## Expected Results

**With OkHttp:**
- No initial timeout
- Both benchmarks work immediately  
- Consistent performance throughout
- HTTP/2 protocol for better performance

## Success Criteria

✅ Zero timeouts during any benchmark  
✅ All iterations complete successfully  
✅ Both health and JWT benchmarks work