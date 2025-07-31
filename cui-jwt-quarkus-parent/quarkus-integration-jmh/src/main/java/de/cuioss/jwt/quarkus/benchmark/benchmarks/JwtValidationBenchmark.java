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
import io.restassured.response.Response;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;

/**
 * Benchmark class for JWT validation endpoints against live Quarkus service.
 * Tests various JWT validation scenarios including success and error cases.
 * 
 * @author Generated
 * @since 1.0
 */
public class JwtValidationBenchmark extends AbstractIntegrationBenchmark {

    /**
     * Benchmark for successful JWT validation with valid tokens.
     * Measures throughput and latency of the /jwt/validate endpoint.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateJwtThroughput() {
        Response response = createAuthenticatedRequest()
                .when()
                .post("/jwt/validate");

        validateResponse(response, 200);
    }

    /**
     * Benchmark for JWT validation latency measurement.
     * Uses AverageTime mode to focus on latency characteristics.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void validateJwtLatency() {
        Response response = createAuthenticatedRequest()
                .when()
                .post("/jwt/validate");

        validateResponse(response, 200);
    }

    /**
     * Benchmark for explicit access token validation.
     * Tests the /jwt/validate-explicit endpoint with access tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateAccessTokenThroughput() {
        String tokenPayload = "{\"token\": \"" + tokenRepository.getNextToken() + "\"}";
        Response response = createBaseRequest()
                .body(tokenPayload)
                .when()
                .post("/jwt/validate-explicit");

        validateResponse(response, 200);
    }

    /**
     * Benchmark for ID token validation.
     * Tests the /jwt/validate/id-token endpoint.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateIdTokenThroughput() {
        String tokenPayload = "{\"token\": \"" + tokenRepository.getNextToken() + "\"}";
        Response response = createBaseRequest()
                .body(tokenPayload)
                .when()
                .post("/jwt/validate/id-token");

        validateResponse(response, 200);
    }

    /**
     * Benchmark for refresh token validation.
     * Tests the /jwt/validate/refresh-token endpoint.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateRefreshTokenThroughput() {
        String tokenPayload = "{\"token\": \"" + tokenRepository.getNextToken() + "\"}";
        Response response = createBaseRequest()
                .body(tokenPayload)
                .when()
                .post("/jwt/validate/refresh-token");

        validateResponse(response, 200);
    }

    /**
     * Benchmark for validation with missing Authorization header.
     * Tests error handling for requests without authentication.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateMissingAuthHeader() {
        Response response = createBaseRequest()
                .when()
                .post("/jwt/validate");

        validateResponse(response, 401);
    }

    /**
     * Benchmark for validation with invalid/malformed tokens.
     * Tests error handling for malformed JWT tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateInvalidToken() {
        Response response = createAuthenticatedRequest(tokenRepository.getInvalidToken())
                .when()
                .post("/jwt/validate");

        validateResponse(response, 401);
    }

    /**
     * Benchmark for validation with expired tokens.
     * Tests error handling for expired JWT tokens.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateExpiredToken() {
        Response response = createAuthenticatedRequest(tokenRepository.getExpiredToken())
                .when()
                .post("/jwt/validate");

        validateResponse(response, 401);
    }

    /**
     * Benchmark for validation with wrong signature tokens.
     * Uses an invalid token to test signature verification.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateWrongSignatureToken() {
        // Use an invalid token with wrong signature
        String wrongSignatureToken = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJrZXkifQ.eyJleHAiOjk5OTk5OTk5OTksImlhdCI6MTY3MDAwMDAwMCwianRpIjoidGVzdC10b2tlbiIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9yZWFsbXMvY3Vpand0LXJlYWxtIiwiYXVkIjoiY3Vpand0LWNsaWVudCIsInN1YiI6InRlc3R1c2VyIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiY3Vpand0LWNsaWVudCIsInNlc3Npb25fc3RhdGUiOiJzZXNzaW9uLXN0YXRlIiwiYWNyIjoiMSIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJ1c2VyIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnt9LCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwic2lkIjoic2Vzc2lvbi1pZCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJ0ZXN0dXNlciJ9.wrong_signature_here";

        Response response = createAuthenticatedRequest(wrongSignatureToken)
                .when()
                .post("/jwt/validate");

        validateResponse(response, 401);
    }

    /**
     * Benchmark with token rotation to test cache behavior.
     * Uses random tokens to simulate varying cache hit ratios.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void validateJwtWithRotation() {
        String token = tokenRepository.getRandomToken();
        Response response = createAuthenticatedRequest(token)
                .when()
                .post("/jwt/validate");

        validateResponse(response, 200);
    }

    @Override
    protected String getBenchmarkName() {
        return "JwtValidation";
    }
}