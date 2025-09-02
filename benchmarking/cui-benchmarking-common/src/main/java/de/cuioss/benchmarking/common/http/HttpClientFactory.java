/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.benchmarking.common.http;

import de.cuioss.tools.logging.CuiLogger;
import lombok.experimental.UtilityClass;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Factory class for creating and caching HttpClient instances.
 * Provides centralized configuration for all HTTP clients used in benchmarks.
 *
 * <p>Features:
 * <ul>
 * <li>Caching of HttpClient instances to avoid recreating them</li>
 * <li>Support for both secure (HTTPS with certificate validation) and insecure (trust-all) clients</li>
 * <li>HTTP/2 as default protocol with fallback to HTTP/1.1</li>
 * <li>Optimized connection settings for benchmark workloads</li>
 * </ul>
 *
 * @since 1.0
 */
@UtilityClass public class HttpClientFactory {

    private static final CuiLogger LOGGER = new CuiLogger(HttpClientFactory.class);

    /**
     * Cache for HttpClient instances to avoid recreating them.
     * Key format: "secure" or "insecure"
     */
    private static final ConcurrentHashMap<String, HttpClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * Cache for URL-specific HttpClient instances to provide isolation per endpoint.
     * Key format: "insecure-<baseUrl>" or "secure-<baseUrl>"
     */
    private static final ConcurrentHashMap<String, HttpClient> URL_CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * Default connection timeout for all clients.
     */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Number of threads for the executor service.
     * Set to match typical benchmark thread count.
     */
    private static final int EXECUTOR_THREADS = 50;

    /**
     * Lazy initialization holder class for the shared executor service.
     * This pattern provides thread-safe lazy initialization without explicit synchronization.
     * The JVM guarantees that the class initialization is thread-safe.
     */
    @SuppressWarnings("java:S1144") // ExecutorHolder is used via ExecutorHolder.INSTANCE in getSharedExecutor()
    private static class ExecutorHolder {
        static final ExecutorService INSTANCE = Executors.newFixedThreadPool(EXECUTOR_THREADS, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("http-client-" + thread.threadId());
            return thread;
        });
    }

    /**
     * Gets or creates a cached HttpClient for insecure connections (trust all certificates).
     * This is typically used for benchmark environments with self-signed certificates.
     *
     * @return HttpClient configured to trust all certificates
     */
    public static HttpClient getInsecureClient() {
        return CLIENT_CACHE.computeIfAbsent("insecure", key -> {
            LOGGER.debug("Creating new insecure HttpClient with trust-all configuration");
            try {
                SSLContext sslContext = createTrustAllSSLContext();
                return createHttpClient(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IllegalStateException("Failed to create insecure HttpClient", e);
            }
        });
    }

    /**
     * Gets or creates a cached HttpClient for secure connections with standard certificate validation.
     *
     * @return HttpClient with standard SSL configuration
     */
    public static HttpClient getSecureClient() {
        return CLIENT_CACHE.computeIfAbsent("secure", key -> {
            LOGGER.debug("Creating new secure HttpClient with standard SSL");
            try {
                SSLContext sslContext = SSLContext.getDefault();
                return createHttpClient(sslContext);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Failed to create secure HttpClient", e);
            }
        });
    }

    /**
     * Gets or creates a cached HttpClient for a specific base URL with insecure connections.
     * This ensures connection pools are isolated per endpoint while maintaining
     * the performance benefits of connection reuse within each benchmark.
     *
     * @param baseUrl the base URL (e.g., "https://localhost:10443")
     * @return HttpClient configured for the specific URL with trust-all certificates
     */
    public static HttpClient getInsecureClientForUrl(String baseUrl) {
        String cacheKey = "insecure-" + baseUrl;
        return URL_CLIENT_CACHE.computeIfAbsent(cacheKey, key -> {
            LOGGER.debug("Creating new insecure HttpClient for URL: {}", baseUrl);
            try {
                SSLContext sslContext = createTrustAllSSLContext();
                return createHttpClient(sslContext);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IllegalStateException("Failed to create insecure HttpClient for URL: " + baseUrl, e);
            }
        });
    }

    /**
     * Gets or creates a cached HttpClient for a specific base URL with secure connections.
     * This ensures connection pools are isolated per endpoint while maintaining
     * the performance benefits of connection reuse within each benchmark.
     *
     * @param baseUrl the base URL (e.g., "https://localhost:10443")
     * @return HttpClient configured for the specific URL with standard SSL
     */
    public static HttpClient getSecureClientForUrl(String baseUrl) {
        String cacheKey = "secure-" + baseUrl;
        return URL_CLIENT_CACHE.computeIfAbsent(cacheKey, key -> {
            LOGGER.debug("Creating new secure HttpClient for URL: {}", baseUrl);
            try {
                SSLContext sslContext = SSLContext.getDefault();
                return createHttpClient(sslContext);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Failed to create secure HttpClient for URL: " + baseUrl, e);
            }
        });
    }

    /**
     * Clears the cached HttpClient for a specific URL if needed.
     * This can be useful for cleanup or testing purposes.
     *
     * @param baseUrl the base URL to clear from cache
     * @param secure whether to clear the secure or insecure client
     */
    public static void clearClientForUrl(String baseUrl, boolean secure) {
        String cacheKey = (secure ? "secure-" : "insecure-") + baseUrl;
        URL_CLIENT_CACHE.remove(cacheKey);
    }

    /**
     * Shuts down the shared executor service and clears the client cache.
     * This method should be called when the application is shutting down to ensure
     * proper resource cleanup.
     * <p>
     * Note: Since we use daemon threads, this is not strictly necessary for JVM shutdown,
     * but it's good practice for proper resource management in long-running applications.
     * <p>
     * The executor will only be shut down if it has been initialized (i.e., if any
     * HTTP client has been created). This avoids unnecessary initialization during shutdown.
     */
    @SuppressWarnings("java:S1144") // Utility method for resource cleanup, may be called during shutdown
    public static void shutdown() {
        LOGGER.debug("Shutting down HttpClientFactory resources");

        // Clear both client caches
        CLIENT_CACHE.clear();
        URL_CLIENT_CACHE.clear();

        // Note: With the initialization-on-demand holder pattern, we cannot check if
        // ExecutorHolder has been initialized without triggering initialization.
        // Since we use daemon threads and the executor shutdown is graceful,
        // it's acceptable to potentially initialize just to shut down.
        // In practice, if shutdown() is called, the executor was likely already used.
        try {
            ExecutorService executor = ExecutorHolder.INSTANCE;
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while shutting down executor service", e);
        }
    }

    /**
     * Creates an HttpClient with the specified SSL context.
     *
     * @param sslContext the SSL context to use
     * @return configured HttpClient
     */
    private static HttpClient createHttpClient(SSLContext sslContext) {
        return HttpClient.newBuilder()
                // Use HTTP/2 by default (Quarkus supports it), will automatically downgrade to HTTP/1.1 if needed
                .version(HttpClient.Version.HTTP_2)
                .sslContext(sslContext)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                // Use shared executor service from holder pattern to avoid resource leaks
                .executor(ExecutorHolder.INSTANCE)
                // Follow redirects automatically
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Creates an SSL context that trusts all certificates.
     * WARNING: This should only be used for testing with self-signed certificates.
     *
     * @return SSLContext that trusts all certificates
     * @throws NoSuchAlgorithmException if TLS is not available
     * @throws KeyManagementException if key management fails
     */
    private static SSLContext createTrustAllSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
        return sslContext;
    }

    /**
     * Trust manager that accepts all certificates.
     * WARNING: Only for testing environments with self-signed certificates.
     */
    @SuppressWarnings("java:S4830") // Server certificate validation is intentionally disabled for benchmark testing
    private static class TrustAllManager implements X509TrustManager {
        @Override @SuppressWarnings("java:S4830") // Certificate validation disabled for self-signed certs in benchmarks
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Accept all - only for testing with self-signed certificates in benchmark environments
        }

        @Override @SuppressWarnings("java:S4830") // Certificate validation disabled for self-signed certs in benchmarks
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Accept all - only for testing with self-signed certificates in benchmark environments
        }

        @Override public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}