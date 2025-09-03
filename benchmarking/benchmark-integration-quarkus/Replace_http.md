# Replace Java HttpClient with OkHttp

## Objective

Replace Java 11+ HttpClient with OkHttp in benchmark-integration-quarkus to eliminate the static initialization timeout bug and improve performance.

## Why OkHttp?

- **Fast**: Battle-tested for performance-critical applications
- **Simple**: Easy to integrate, minimal dependencies
- **Reliable**: Mature, production-proven by Square
- **HTTP/2**: Automatic multiplexing for better throughput

## Implementation

### Step 1: Add OkHttp Dependency

```xml
<!-- Add to benchmarking/cui-benchmarking-common/pom.xml -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
```

### Step 2: Replace HttpClientFactory

Replace content of `benchmarking/cui-benchmarking-common/src/main/java/de/cuioss/benchmarking/common/http/HttpClientFactory.java`:

```java
package de.cuioss.benchmarking.common.http;

import okhttp3.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class HttpClientFactory {
    
    private static OkHttpClient client;
    
    public static synchronized OkHttpClient getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }
    
    private static OkHttpClient createClient() {
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
            
            return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(10, 60, TimeUnit.SECONDS))
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .build();
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OkHttp client", e);
        }
    }
}
```

### Step 3: Update AbstractBenchmarkBase

Replace the `sendRequest` method in `AbstractBenchmarkBase.java`:

```java
protected HttpResponse<String> sendRequest(HttpRequest request) throws IOException {
    OkHttpClient okClient = HttpClientFactory.getClient();
    
    // Build OkHttp request
    Request.Builder builder = new Request.Builder()
        .url(request.uri().toString());
    
    // Copy headers
    request.headers().map().forEach((name, values) -> 
        values.forEach(value -> builder.addHeader(name, value))
    );
    
    // Handle body
    RequestBody body = request.bodyPublisher().isPresent() 
        ? RequestBody.create("", MediaType.parse("application/json"))
        : null;
    
    builder.method(request.method(), body);
    
    // Execute and wrap response
    try (Response response = okClient.newCall(builder.build()).execute()) {
        String responseBody = response.body() != null ? response.body().string() : "";
        return new HttpResponse<>() {
            public int statusCode() { return response.code(); }
            public String body() { return responseBody; }
            public HttpRequest request() { return request; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(new HashMap<>(), (a, b) -> true); }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return request.uri(); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
        };
    }
}
```

### Step 4: Test

```bash
# Simple test - should work immediately without timeouts
./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark \
    -Djmh.threads=1 -Djmh.warmupIterations=0 -Djmh.forks=1
```

## Expected Results

**With OkHttp:**
- No initial timeout
- Both benchmarks work immediately
- Consistent performance throughout
- Better throughput and lower latency

## Success Criteria

✅ Zero timeouts during any benchmark  
✅ All iterations complete successfully  
✅ Both health and JWT benchmarks work

## Quick Verification

Create a simple test file to verify OkHttp works with our setup:

```java
// benchmarking/benchmark-integration-quarkus/src/test/java/OkHttpQuickTest.java
import okhttp3.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class OkHttpQuickTest {
    public static void main(String[] args) throws Exception {
        // Trust all certs
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }
        };
        
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, null);
        
        OkHttpClient client = new OkHttpClient.Builder()
            .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
            .hostnameVerifier((hostname, session) -> true)
            .build();
            
        Request request = new Request.Builder()
            .url("https://localhost:10443/q/health")
            .build();
            
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            System.out.println("Status: " + response.code());
            System.out.println("Time: " + (System.currentTimeMillis() - start) + "ms");
        }
    }
}
```

Run with: `java -cp "target/dependency/*" OkHttpQuickTest.java`