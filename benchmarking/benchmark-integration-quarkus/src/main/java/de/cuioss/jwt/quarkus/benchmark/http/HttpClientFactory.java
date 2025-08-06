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
package de.cuioss.jwt.quarkus.benchmark.http;

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
@UtilityClass
public class HttpClientFactory {

    private static final CuiLogger LOGGER = new CuiLogger(HttpClientFactory.class);

    /**
     * Cache for HttpClient instances to avoid recreating them.
     * Key format: "secure" or "insecure"
     */
    private static final ConcurrentHashMap<String, HttpClient> CLIENT_CACHE = new ConcurrentHashMap<>();

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
     * Shared executor service for all HTTP clients.
     * Created lazily and reused to avoid resource leaks.
     */
    private static volatile ExecutorService sharedExecutor = null;
    private static final Object executorLock = new Object();

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
     * Gets or creates the shared executor service for HTTP clients.
     */
    private static ExecutorService getSharedExecutor() {
        if (sharedExecutor == null) {
            synchronized (executorLock) {
                if (sharedExecutor == null) {
                    sharedExecutor = Executors.newFixedThreadPool(EXECUTOR_THREADS, runnable -> {
                        Thread thread = new Thread(runnable);
                        thread.setDaemon(true);
                        thread.setName("http-client-" + thread.threadId());
                        return thread;
                    });
                }
            }
        }
        return sharedExecutor;
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
                // Use shared executor to avoid resource leaks
                .executor(getSharedExecutor())
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
    private static class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Accept all - only for testing
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Accept all - only for testing
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}