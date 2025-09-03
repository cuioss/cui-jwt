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
import okhttp3.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Factory for creating OkHttp client instances for benchmarking.
 * Replaces Java HttpClient to avoid static initialization timeout bug.
 * Uses singleton pattern following OkHttp best practices.
 */
public class HttpClientFactory {

    private HttpClientFactory() {
        // Utility class
    }

    private static final CuiLogger LOGGER = new CuiLogger(HttpClientFactory.class);

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 5;
    private static final int WRITE_TIMEOUT_SECONDS = 5;

    private static final OkHttpClient CLIENT = createClient();

    public static OkHttpClient getInsecureClient() {
        return CLIENT;
    }

    private static OkHttpClient createClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            // Accept all client certificates for benchmark testing
                        }

                        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            // Accept all server certificates for benchmark testing with self-signed certs
                        }

                        @Override public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true) // Accept all hostnames for benchmark testing
                    .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                    .build();

            LOGGER.info("Created OkHttp client with HTTP/2, timeouts: {}s", CONNECT_TIMEOUT_SECONDS);
            return client;

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to create OkHttp client", e);
        }
    }
}