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

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientFactoryTest {

    @Test void getInsecureClient() {
        OkHttpClient client1 = HttpClientFactory.getInsecureClient();
        OkHttpClient client2 = HttpClientFactory.getInsecureClient();

        assertNotNull(client1);
        assertNotNull(client2);
        // Should return cached instance
        assertSame(client1, client2);

        // Verify HTTP/2 is configured with fallback to HTTP/1.1
        assertTrue(client1.protocols().contains(Protocol.HTTP_2));
        assertTrue(client1.protocols().contains(Protocol.HTTP_1_1));

        // Verify timeouts are configured (5 seconds)
        assertEquals(5000, client1.connectTimeoutMillis());
        assertEquals(5000, client1.readTimeoutMillis());
        assertEquals(5000, client1.writeTimeoutMillis());
    }

    @Test void concurrentAccess() throws InterruptedException {
        // Test thread safety of factory
        Thread[] threads = new Thread[10];
        OkHttpClient[] clients = new OkHttpClient[10];

        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> clients[index] = HttpClientFactory.getInsecureClient());
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All threads should get the same cached instance
        OkHttpClient expected = clients[0];
        for (OkHttpClient client : clients) {
            assertSame(expected, client);
        }
    }

    @Test void clientConfiguration() {
        OkHttpClient client = HttpClientFactory.getInsecureClient();

        assertNotNull(client);

        // Verify SSL configuration is insecure (trust all certificates)
        assertNotNull(client.sslSocketFactory());
        assertNotNull(client.hostnameVerifier());

        // Verify connection pool is configured
        assertNotNull(client.connectionPool());

        // Verify protocols include both HTTP/2 and HTTP/1.1
        assertEquals(2, client.protocols().size());
        assertTrue(client.protocols().contains(Protocol.HTTP_2));
        assertTrue(client.protocols().contains(Protocol.HTTP_1_1));
    }
}