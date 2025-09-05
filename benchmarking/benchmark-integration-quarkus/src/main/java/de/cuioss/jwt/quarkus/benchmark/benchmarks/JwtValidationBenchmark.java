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

import de.cuioss.jwt.quarkus.benchmark.AbstractIntegrationBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Benchmark class for JWT validation endpoints against live Quarkus service.
 * Tests successful JWT validation scenario.
 *
 * @since 1.0
 */
public class JwtValidationBenchmark extends AbstractIntegrationBenchmark {

    public static final String PATH = "/jwt/validate";

    /**
     * Override integration-specific setup to add JWT endpoint priming after token repository is initialized.
     * Called by AbstractIntegrationBenchmark after token repository initialization.
     */
    @Override protected void performIntegrationSpecificSetup() {
        // First call readiness endpoint to ensure JWKS loading is complete before JWT validation
        try {
            long startTime = System.currentTimeMillis();
            HttpRequest readinessRequest = createRequestForPath("/q/health/ready")
                    .GET()
                    .build();
            HttpResponse<String> readinessResponse = sendRequest(readinessRequest);
            long elapsedTime = System.currentTimeMillis() - startTime;

            validateResponse(readinessResponse, 200);
            logger.info("Readiness check completed successfully in {}ms - JWKS loading ready", elapsedTime);
        } catch (IOException | AssertionError e) {
            logger.warn("Readiness check failed: {} - proceeding with JWT warmup anyway", e.getMessage());
            // Continue with JWT priming even if readiness fails
        } catch (InterruptedException e) {
            logger.warn("Readiness check was interrupted: {} - proceeding with JWT warmup anyway", e.getMessage());
            // Restore interrupt status and continue
            Thread.currentThread().interrupt();
        }

        // Prime with JWT validation endpoint (non-blocking - continue even if priming fails)
        try {
            long startTime = System.currentTimeMillis();
            HttpRequest request = createAuthenticatedRequest(PATH)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = sendRequest(request);
            long elapsedTime = System.currentTimeMillis() - startTime;

            validateResponse(response, 200);
            logger.info("Benchmark primed successfully with {} in {}ms", PATH, elapsedTime);
        } catch (IOException | AssertionError e) {
            logger.error("Benchmark priming FAILED for {}: {} - continuing with benchmark execution", PATH, e.getMessage());
            // DO NOT throw exception - allow benchmark to continue and demonstrate the pattern
        } catch (InterruptedException e) {
            logger.error("Benchmark priming was interrupted for {}: {} - continuing with benchmark execution", PATH, e.getMessage());
            // Restore interrupt status and continue - DO NOT throw exception
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Benchmark for successful JWT validation with valid tokens.
     * Measures throughput and latency of the /jwt/validate endpoint.
     */
    @Benchmark @BenchmarkMode({Mode.Throughput, Mode.SampleTime}) public void validateJwtThroughput() throws IOException, InterruptedException {
        HttpRequest request = createAuthenticatedRequest(PATH)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = sendRequest(request);
        validateResponse(response, 200);
    }
}