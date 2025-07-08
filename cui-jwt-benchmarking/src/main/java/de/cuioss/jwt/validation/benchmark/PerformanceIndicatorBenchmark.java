/**
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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Comprehensive benchmark for measuring key JWT validation performance indicators.
 * <p>
 * This benchmark focuses on the two most important performance metrics:
 * <ul>
 *   <li><strong>Throughput</strong>: Operations per second under concurrent load</li>
 *   <li><strong>Average Validation Time</strong>: Time per operation for single-threaded validation</li>
 * </ul>
 * <p>
 * The results from these benchmarks are used to calculate a weighted performance score
 * that provides a single indicator of overall JWT validation performance.
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class PerformanceIndicatorBenchmark {

    private TokenValidator tokenValidator;
    private String validAccessToken;

    @Setup(Level.Trial)
    public void setup() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig issuerConfig = tokenHolder.getIssuerConfig();
        tokenValidator = new TokenValidator(issuerConfig);
        validAccessToken = tokenHolder.getRawToken();
    }

    /**
     * Measures token validation throughput under concurrent load.
     * <p>
     * This benchmark uses maximum available threads to measure how many
     * token validations can be performed per second under high concurrency.
     * Higher values indicate better throughput performance.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughput() {
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during throughput measurement", e);
        }
    }

    /**
     * Measures average validation time for single-threaded token validation.
     * <p>
     * This benchmark measures the average time required to validate a single
     * token without concurrent load. Lower values indicate better latency performance.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTime() {
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during average time measurement", e);
        }
    }

    /**
     * Calculates a comprehensive weighted performance score based on multiple metrics.
     * <p>
     * The performance score is calculated as:
     * <code>Score = (Throughput * 0.57) + ((1000000 / AvgTimeInMicros) * 0.40) + (ErrorResilienceScore * 0.03)</code>
     * <p>
     * This formula weights:
     * <ul>
     *   <li><strong>Throughput (57%)</strong>: Operations per second under concurrent load</li>
     *   <li><strong>Latency (40%)</strong>: Inverted average time per operation</li>
     *   <li><strong>Error Resilience (3%)</strong>: Performance stability under error conditions</li>
     * </ul>
     * The error resilience component ensures the system maintains good performance
     * even when processing invalid tokens, which is important for production stability.
     *
     * @param throughputOpsPerSec Operations per second from throughput benchmark
     * @param avgTimeInMicros Average time per operation in microseconds
     * @param errorResilienceOpsPerSec Operations per second under mixed error conditions (0% error scenario)
     * @return Weighted performance score
     */
    public static double calculatePerformanceScore(double throughputOpsPerSec, double avgTimeInMicros, double errorResilienceOpsPerSec) {
        // Convert average time to operations per second (inverted metric)
        double latencyOpsPerSec = 1_000_000.0 / avgTimeInMicros;

        // Weighted score: 57% throughput, 40% latency, 3% error resilience
        return (throughputOpsPerSec * 0.57) + (latencyOpsPerSec * 0.40) + (errorResilienceOpsPerSec * 0.03);
    }

    /**
     * Backward compatibility method for calculating performance score without error resilience.
     * Uses the original 60/40 weighting between throughput and latency.
     *
     * @param throughputOpsPerSec Operations per second from throughput benchmark
     * @param avgTimeInMicros Average time per operation in microseconds
     * @return Weighted performance score
     */
    public static double calculatePerformanceScore(double throughputOpsPerSec, double avgTimeInMicros) {
        // Convert average time to operations per second (inverted metric)
        double latencyOpsPerSec = 1_000_000.0 / avgTimeInMicros;

        // Weighted score: 60% throughput, 40% latency (original formula)
        return (throughputOpsPerSec * 0.6) + (latencyOpsPerSec * 0.4);
    }
}