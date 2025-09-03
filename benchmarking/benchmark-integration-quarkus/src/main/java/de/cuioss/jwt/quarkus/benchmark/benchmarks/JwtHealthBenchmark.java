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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Setup;

import java.io.IOException;
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
     * Override the base class setup to add endpoint-specific priming.
     * Primes the system with a real health check request after base setup.
     */
    @Override
    protected void performAdditionalSetup() {
        // Call parent's additional setup first
        super.performAdditionalSetup();
        
        // Prime with health endpoint (non-blocking - continue even if priming fails)
        try {
            long startTime = System.currentTimeMillis();
            HttpRequest request = createRequestForPath("/q/health")
                    .GET()
                    .build();
            HttpResponse<String> response = sendRequest(request);
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            validateResponse(response, 200);
            logger.info("Benchmark primed successfully with /q/health in {}ms", elapsedTime);
        } catch (Exception e) {
            logger.error("Benchmark priming FAILED for /q/health: {} - continuing with benchmark execution", e.getMessage());
            // DO NOT throw exception - allow benchmark to continue and demonstrate the pattern
        }
    }

    /**
     * Benchmark for Quarkus health endpoint throughput.
     * Provides baseline throughput measurement without JWT processing.
     */
    @Benchmark @BenchmarkMode({Mode.Throughput, Mode.SampleTime}) public void healthCheckThroughput() throws IOException, InterruptedException {
        logger.debug("Starting health check request");
        HttpRequest request = createRequestForPath("/q/health")
                .GET()
                .build();
        logger.debug("Sending request to {}", request.uri());
        HttpResponse<String> response = sendRequest(request);
        logger.debug("Received response with status: {}", response.statusCode());
        validateResponse(response, 200);
    }
}