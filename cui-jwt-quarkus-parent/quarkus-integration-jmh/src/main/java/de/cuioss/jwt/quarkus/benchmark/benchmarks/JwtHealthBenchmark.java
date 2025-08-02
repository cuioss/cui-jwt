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
 * Benchmark class for health endpoints to establish baseline performance.
 * These benchmarks provide a baseline for comparison with JWT validation endpoints.
 * 
 * @since 1.0
 */
public class JwtHealthBenchmark extends AbstractBaseBenchmark {

    /**
     * Benchmark for Quarkus health endpoint throughput.
     * Provides baseline throughput measurement without JWT processing.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void healthCheckThroughput() throws Exception {
        HttpRequest request = createBaseRequest("/q/health")
                .GET()
                .build();
        
        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Benchmark for health endpoint latency measurement.
     * Provides baseline latency without JWT processing overhead.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void healthCheckLatency() throws Exception {
        HttpRequest request = createBaseRequest("/q/health")
                .GET()
                .build();
        
        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Benchmark for liveness probe endpoint.
     * Tests the most minimal health check endpoint.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void livenessCheckThroughput() throws Exception {
        HttpRequest request = createBaseRequest("/q/health/live")
                .GET()
                .build();
        
        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Benchmark for readiness probe endpoint.
     * Tests readiness check which may include additional checks.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void readinessCheckThroughput() throws Exception {
        HttpRequest request = createBaseRequest("/q/health/ready")
                .GET()
                .build();
        
        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Benchmark for startup probe endpoint.
     * Tests startup health check endpoint.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void startupCheckThroughput() throws Exception {
        HttpRequest request = createBaseRequest("/q/health/started")
                .GET()
                .build();
        
        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    /**
     * Combined benchmark measuring both throughput and latency.
     * Uses Mode.All to get comprehensive baseline metrics.
     */
    @Benchmark
    @BenchmarkMode(Mode.All)
    public void healthCheckAll() throws Exception {
        HttpRequest request = createBaseRequest("/q/health")
                .GET()
                .build();
        
        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }

    @Override
    protected String getBenchmarkName() {
        return "JwtHealth";
    }
}