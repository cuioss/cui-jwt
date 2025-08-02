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
package de.cuioss.jwt.quarkus.benchmark.benchmarks;

import de.cuioss.jwt.quarkus.benchmark.AbstractBaseBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Benchmark class for echo endpoints to measure network and serialization baseline.
 * These benchmarks provide network overhead baseline for comparison with JWT operations.
 * 
 * @since 1.0
 */
public class JwtEchoBenchmark extends AbstractBaseBenchmark {

    /**
     * Simple echo benchmark for network baseline throughput.
     * Tests basic HTTP request/response without any business logic.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void echoGetThroughput() throws Exception {
        String jsonPayload = "{\"data\": {\"message\": \"benchmark-test\"}}";

        HttpRequest request = createBaseRequest("/jwt/echo")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Echo benchmark for network baseline latency.
     * Measures pure network and HTTP processing latency.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void echoGetLatency() throws Exception {
        String jsonPayload = "{\"data\": {\"message\": \"benchmark-test\"}}";

        HttpRequest request = createBaseRequest("/jwt/echo")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * POST echo benchmark with JSON payload.
     * Tests JSON serialization/deserialization overhead.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void echoPostJsonThroughput() throws Exception {
        String jsonPayload = """
                {
                  "data": {
                    "message": "benchmark-test",
                    "timestamp": "2025-01-01T00:00:00Z",
                    "key1": "value1",
                    "key2": "value2",
                    "numbers": [1, 2, 3, 4, 5]
                  }
                }\
                """;

        HttpRequest request = createBaseRequest("/jwt/echo")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * POST echo benchmark latency with JSON payload.
     * Measures JSON processing latency baseline.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void echoPostJsonLatency() throws Exception {
        String jsonPayload = """
                {
                  "data": {
                    "message": "benchmark-test",
                    "timestamp": "2025-01-01T00:00:00Z"
                  }
                }\
                """;

        HttpRequest request = createBaseRequest("/jwt/echo")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Large payload echo benchmark.
     * Tests performance with larger request/response bodies.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void echoLargePayloadThroughput() throws Exception {
        String jsonPayload = "{\"data\": {\"message\": \"hello\"}}";

        HttpRequest request = createBaseRequest("/jwt/echo")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Echo benchmark with headers to test header processing overhead.
     * Tests HTTP header processing baseline.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void echoWithHeadersThroughput() throws Exception {
        String jsonPayload = "{\"data\": {\"message\": \"header-test\"}}";

        HttpRequest request = createBaseRequest("/jwt/echo")
                .header("X-Benchmark-Test", "true")
                .header("X-Request-ID", "benchmark-" + System.nanoTime())
                .header("X-Client-Version", "1.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Comprehensive echo benchmark measuring all metrics.
     * Provides complete baseline performance profile.
     */
    @Benchmark
    @BenchmarkMode(Mode.All)
    public void echoComprehensive() throws Exception {
        String jsonPayload = "{\"data\": {\"message\": \"comprehensive-test\"}}";

        HttpRequest request = createBaseRequest("/jwt/echo")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Echo benchmark simulating realistic application payload.
     * Tests with a payload similar to typical application requests.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void echoRealisticPayloadThroughput() throws Exception {
        String realisticPayload = """
                {
                  "user": {
                    "id": "user-12345",
                    "name": "Test User",
                    "email": "test@example.com"
                  },
                  "request": {
                    "operation": "validate",
                    "timestamp": "2025-01-01T00:00:00Z",
                    "clientId": "benchmark-client",
                    "version": "1.0.0"
                  },
                  "metadata": {
                    "source": "benchmark",
                    "environment": "test"
                  }
                }\
                """;

        String wrappedPayload = "{\"data\": " + realisticPayload + "}";

        HttpRequest request = createBaseRequest("/jwt/echo")
                .POST(HttpRequest.BodyPublishers.ofString(wrappedPayload))
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    @Override
    protected String getBenchmarkName() {
        return "JwtEcho";
    }
}