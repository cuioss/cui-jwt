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

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientFactoryTest {

    @Test void getInsecureClient() {
        HttpClient client1 = HttpClientFactory.getInsecureClient();
        HttpClient client2 = HttpClientFactory.getInsecureClient();

        assertNotNull(client1);
        assertNotNull(client2);
        // Should return cached instance
        assertSame(client1, client2);

        // Verify HTTP/2 is configured
        assertEquals(HttpClient.Version.HTTP_2, client1.version());

        // Verify redirect policy
        assertEquals(HttpClient.Redirect.NORMAL, client1.followRedirects());
    }

    @Test void getSecureClient() {
        HttpClient client1 = HttpClientFactory.getSecureClient();
        HttpClient client2 = HttpClientFactory.getSecureClient();

        assertNotNull(client1);
        assertNotNull(client2);
        // Should return cached instance
        assertSame(client1, client2);

        // Verify HTTP/2 is configured
        assertEquals(HttpClient.Version.HTTP_2, client1.version());

        // Verify redirect policy
        assertEquals(HttpClient.Redirect.NORMAL, client1.followRedirects());
    }

    @Test void secureAndInsecureClientsAreDifferent() {
        HttpClient secureClient = HttpClientFactory.getSecureClient();
        HttpClient insecureClient = HttpClientFactory.getInsecureClient();

        assertNotNull(secureClient);
        assertNotNull(insecureClient);
        // Should be different instances
        assertNotSame(secureClient, insecureClient);
    }

    @Test void shutdown() {
        // Get clients to ensure they're initialized
        HttpClient insecure1 = HttpClientFactory.getInsecureClient();
        HttpClient secure1 = HttpClientFactory.getSecureClient();

        assertNotNull(insecure1);
        assertNotNull(secure1);

        // Shutdown should clear cache and executor
        HttpClientFactory.shutdown();

        // After shutdown, new clients should be created
        HttpClient insecure2 = HttpClientFactory.getInsecureClient();
        HttpClient secure2 = HttpClientFactory.getSecureClient();

        assertNotNull(insecure2);
        assertNotNull(secure2);

        // New instances should be created after shutdown
        assertNotSame(insecure1, insecure2);
        assertNotSame(secure1, secure2);
    }

    @Test void concurrentAccess() throws InterruptedException {
        // Test thread safety of factory
        Thread[] threads = new Thread[10];
        HttpClient[] clients = new HttpClient[10];

        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> clients[index] = HttpClientFactory.getInsecureClient());
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All threads should get the same cached instance
        HttpClient expected = clients[0];
        for (HttpClient client : clients) {
            assertSame(expected, client);
        }
    }
}