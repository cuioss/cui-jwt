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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AbstractBaseBenchmarkTest {

    private TestBenchmark benchmark;

    @BeforeEach void setUp() {
        benchmark = new TestBenchmark();
    }

    @Test void setupBenchmark() {
        // Setup with default values
        assertDoesNotThrow(() -> benchmark.setupBenchmark());

        assertNotNull(benchmark.serviceUrl);
        assertNotNull(benchmark.quarkusMetricsUrl);
        assertNotNull(benchmark.metricsExporter);
        assertNotNull(benchmark.benchmarkResultsDir);

        // Check default values
        assertEquals("https://localhost:10443", benchmark.serviceUrl);
        assertEquals("https://localhost:10443", benchmark.quarkusMetricsUrl);
    }

    @Test void setupBenchmarkWithSystemProperties() {
        // Set system properties
        System.setProperty("integration.service.url", "https://test:8080");
        System.setProperty("quarkus.metrics.url", "https://metrics:9090");
        System.setProperty("benchmark.results.dir", "/test/results");

        try {
            benchmark.setupBenchmark();

            assertEquals("https://test:8080", benchmark.serviceUrl);
            assertEquals("https://metrics:9090", benchmark.quarkusMetricsUrl);
            assertEquals("/test/results", benchmark.benchmarkResultsDir);
        } finally {
            // Clean up system properties
            System.clearProperty("integration.service.url");
            System.clearProperty("quarkus.metrics.url");
            System.clearProperty("benchmark.results.dir");
        }
    }

    @Test void createBaseRequest() {
        benchmark.setupBenchmark();

        HttpRequest.Builder requestBuilder = benchmark.createBaseRequest("/test/path");
        HttpRequest request = requestBuilder.build();

        assertNotNull(request);
        assertEquals("https://localhost:10443/test/path", request.uri().toString());

        // Check headers
        assertTrue(request.headers().map().containsKey("Content-Type"));
        assertTrue(request.headers().map().containsKey("Accept"));
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
        assertEquals("application/json", request.headers().firstValue("Accept").orElse(""));

        // Check timeout is set
        assertTrue(request.timeout().isPresent());
        assertEquals(30, request.timeout().get().getSeconds());
    }

    @Test void validateResponse() {
        benchmark.setupBenchmark();

        // Create a mock response with expected status
        TestHttpResponse response200 = new TestHttpResponse(200, "OK");
        assertDoesNotThrow(() -> benchmark.validateResponse(response200, 200));

        // Test with unexpected status
        TestHttpResponse response404 = new TestHttpResponse(404, "Not Found");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> benchmark.validateResponse(response404, 200));

        assertTrue(exception.getMessage().contains("Expected status 200 but got 404"));
        assertTrue(exception.getMessage().contains("Not Found"));
    }

    @Test void validateResponseWithDifferentStatuses() {
        benchmark.setupBenchmark();

        // Test various status codes
        TestHttpResponse response201 = new TestHttpResponse(201, "Created");
        assertDoesNotThrow(() -> benchmark.validateResponse(response201, 201));

        TestHttpResponse response400 = new TestHttpResponse(400, "Bad Request");
        assertDoesNotThrow(() -> benchmark.validateResponse(response400, 400));

        TestHttpResponse response500 = new TestHttpResponse(500, "Internal Server Error");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> benchmark.validateResponse(response500, 200));
        assertTrue(exception.getMessage().contains("500"));
    }

    @Test void loggingManagerProperty() {
        // Verify that the static block sets the logging manager property
        assertEquals("java.util.logging.LogManager",
                System.getProperty("java.util.logging.manager"));
    }

    // Test implementation of AbstractBaseBenchmark
    private static class TestBenchmark extends AbstractBaseBenchmark {
        // Expose protected methods for testing
        @Override public HttpRequest.Builder createBaseRequest(String path) {
            return super.createBaseRequest(path);
        }

        @Override public void validateResponse(HttpResponse<String> response, int expectedStatus) {
            super.validateResponse(response, expectedStatus);
        }
    }

    // Simple HttpResponse implementation for testing
    private static class TestHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        TestHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() {
            return statusCode;
        }

        @Override public HttpRequest request() {
            return null;
        }

        @Override public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (s1, s2) -> true);
        }

        @Override public String body() {
            return body;
        }

        @Override public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override public URI uri() {
            return URI.create("https://localhost");
        }

        @Override public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}