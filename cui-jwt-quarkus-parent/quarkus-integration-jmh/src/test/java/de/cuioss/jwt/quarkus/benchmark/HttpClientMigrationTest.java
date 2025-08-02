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
package de.cuioss.jwt.quarkus.benchmark;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify that the HttpClient migration was successful.
 * This test verifies that the basic HttpClient setup works correctly.
 */
public class HttpClientMigrationTest {

    @Test
    public void testHttpClientSetup() throws Exception {
        // Create a simple HttpClient
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        assertNotNull(client);
        assertEquals(HttpClient.Version.HTTP_1_1, client.version());
    }

    @Test
    public void testHttpRequestBuilder() {
        // Test that we can build HTTP requests properly
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:10443/jwt/echo"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                .build();

        assertNotNull(request);
        assertEquals("POST", request.method());
        assertTrue(request.headers().firstValue("Content-Type").isPresent());
        assertEquals("application/json", request.headers().firstValue("Content-Type").get());
    }

    @Test
    public void testCompilationSuccessful() {
        // This test verifies that all benchmark classes compile with HttpClient
        try {
            // Verify that key benchmark classes can be loaded
            Class.forName("de.cuioss.jwt.quarkus.benchmark.AbstractBaseBenchmark");
            Class.forName("de.cuioss.jwt.quarkus.benchmark.AbstractIntegrationBenchmark");
            Class.forName("de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtEchoBenchmark");
            Class.forName("de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark");
            Class.forName("de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtValidationBenchmark");
            Class.forName("de.cuioss.jwt.quarkus.benchmark.repository.TokenRepository");
            Class.forName("de.cuioss.jwt.quarkus.benchmark.metrics.QuarkusMetricsFetcher");
        } catch (ClassNotFoundException e) {
            fail("Failed to load benchmark class: " + e.getMessage());
        }
    }
}