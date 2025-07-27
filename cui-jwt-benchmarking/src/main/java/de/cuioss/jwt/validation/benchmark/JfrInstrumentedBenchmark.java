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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation.OperationRecorder;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;

import io.jsonwebtoken.Jwts;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JFR-instrumented benchmark for measuring JWT validation performance with detailed variance analysis.
 * <p>
 * This benchmark extends the standard performance measurements with JFR (Java Flight Recorder) events
 * to capture:
 * <ul>
 *   <li>Individual operation timing and metadata</li>
 *   <li>Periodic statistics including variance metrics</li>
 *   <li>Concurrent operation tracking</li>
 *   <li>Thread-level performance data</li>
 * </ul>
 * <p>
 * The JFR data enables analysis of:
 * <ul>
 *   <li>Operation time variance under concurrent load</li>
 *   <li>P50/P95/P99 latency percentiles over time</li>
 *   <li>Impact of different token sizes and issuers</li>
 *   <li>Cache hit rates and their effect on variance</li>
 * </ul>
 * <p>
 * JFR recording is automatically started when benchmarks run and saved to:
 * {@code target/benchmark-results/benchmark.jfr}
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class JfrInstrumentedBenchmark {

    private TokenValidator tokenValidator;
    private String validAccessToken;
    private JfrInstrumentation jfrInstrumentation;

    /**
     * Number of different issuers to simulate issuer config resolution overhead
     */
    private static final int ISSUER_COUNT = 3;

    /**
     * Shared token pool for reduced setup overhead.
     * Pre-generated tokens reduce benchmark setup time.
     */
    private static final int TOKEN_POOL_SIZE = 60; // 20 tokens per issuer
    private String[] tokenPool;
    private int tokenIndex = 0;

    private final Random random = new Random(42); // Fixed seed for reproducibility
    
    /**
     * Map to store issuer identifier for each token for JFR events
     */
    private Map<String, String> tokenToIssuer = new HashMap<>();

    @Setup(Level.Trial)
    public void setup() {
        // Initialize JFR instrumentation
        jfrInstrumentation = new JfrInstrumentation();

        // Generate multiple issuer key materials for benchmarking
        InMemoryKeyMaterialHandler.IssuerKeyMaterial[] issuers =
                InMemoryKeyMaterialHandler.createMultipleIssuers(ISSUER_COUNT);

        List<IssuerConfig> issuerConfigs = new ArrayList<>();
        List<String> allTokens = new ArrayList<>();

        // Create issuer configs and generate tokens for each issuer
        for (InMemoryKeyMaterialHandler.IssuerKeyMaterial issuer : issuers) {
            // Create issuer config with the issuer's JWKS
            IssuerConfig config = IssuerConfig.builder()
                    .issuerIdentifier(issuer.getIssuerIdentifier())
                    .jwksContent(issuer.getJwks())
                    .expectedAudience("benchmark-client")
                    .build();

            issuerConfigs.add(config);

            // Generate tokens for this issuer
            for (int j = 0; j < (TOKEN_POOL_SIZE / ISSUER_COUNT); j++) {
                String token = generateTokenForIssuer(issuer);
                allTokens.add(token);
                tokenToIssuer.put(token, issuer.getIssuerIdentifier());
            }
        }

        // Build validator with all metrics explicitly enabled
        TokenValidatorMonitorConfig monitorConfig = TokenValidatorMonitorConfig.builder()
                .measurementTypes(TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES)
                .windowSize(10000) // Large window for benchmark stability
                .build();

        tokenValidator = TokenValidator.builder()
                .issuerConfigs(issuerConfigs)
                .monitorConfig(monitorConfig)
                .build();

        // Convert token list to array and shuffle
        tokenPool = allTokens.toArray(new String[0]);
        shuffleArray(tokenPool);

        // Set primary validation token
        validAccessToken = tokenPool[0];
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Record benchmark phase event at iteration start
        // Note: We can't access BenchmarkParams directly in @Setup, so we use simpler phase tracking
        String benchmarkName = this.getClass().getSimpleName();
        String phase = "measurement"; // JMH handles warmup/measurement internally
        int threads = Thread.activeCount(); // Approximate thread count
        
        jfrInstrumentation.recordPhase(benchmarkName, phase, 0, 0, 1, threads);
    }

    /**
     * Generates a valid JWT token for the given issuer.
     */
    private String generateTokenForIssuer(InMemoryKeyMaterialHandler.IssuerKeyMaterial issuer) {
        Instant now = Instant.now();

        return Jwts.builder()
                .header()
                .keyId(issuer.getKeyId())
                .and()
                .issuer(issuer.getIssuerIdentifier())
                .subject("benchmark-user")
                .audience().add("benchmark-client").and()
                .expiration(Date.from(now.plusSeconds(3600)))
                .notBefore(Date.from(now))
                .issuedAt(Date.from(now))
                .id(UUID.randomUUID().toString())
                .claim("scope", "read write")
                .claim("roles", List.of("user", "admin"))
                .claim("groups", List.of("test-group"))
                .signWith(issuer.getPrivateKey(), issuer.getAlgorithm().getAlgorithm())
                .compact();
    }

    private void shuffleArray(String[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            String temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Shutdown JFR instrumentation
        if (jfrInstrumentation != null) {
            jfrInstrumentation.shutdown();
        }
    }

    /**
     * Measures average validation time for single-threaded token validation with JFR instrumentation.
     * <p>
     * This benchmark measures the baseline latency for validating a single
     * access token without concurrent load. JFR events capture individual operation timing.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureAverageTimeWithJfr() {
        String token = validAccessToken;

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureAverageTimeWithJfr", "validation")) {
            recorder.withTokenSize(token.getBytes(StandardCharsets.UTF_8).length)
                    .withIssuer(tokenToIssuer.get(token));

            AccessTokenContent result = tokenValidator.createAccessToken(token);
            recorder.withSuccess(true);
            return result;

        } catch (TokenValidationException e) {
            // This would be recorded in the finally block of try-with-resources
            throw new RuntimeException("Unexpected validation failure during average time measurement", e);
        }
    }

    /**
     * Measures token validation throughput under concurrent load with JFR instrumentation.
     * <p>
     * This benchmark uses multiple threads to measure how many token validations
     * can be performed per second under concurrent load. JFR events track concurrent operations.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureThroughputWithJfr() {
        String token = validAccessToken;

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureThroughputWithJfr", "validation")) {
            recorder.withTokenSize(token.getBytes(StandardCharsets.UTF_8).length)
                    .withIssuer(tokenToIssuer.get(token));

            AccessTokenContent result = tokenValidator.createAccessToken(token);
            recorder.withSuccess(true);
            return result;

        } catch (TokenValidationException e) {
            // This would be recorded in the finally block of try-with-resources
            throw new RuntimeException("Unexpected validation failure during throughput measurement", e);
        }
    }

    /**
     * Measures concurrent validation performance with token rotation and JFR instrumentation.
     * <p>
     * This benchmark tests validation performance using a pool of different tokens
     * to simulate real-world scenarios. JFR events capture variance in operation times.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureConcurrentValidationWithJfr() {
        // Rotate through token pool to simulate different tokens
        String token = tokenPool[tokenIndex++ % TOKEN_POOL_SIZE];

        try (OperationRecorder recorder = jfrInstrumentation.recordOperation("measureConcurrentValidationWithJfr", "validation")) {
            recorder.withTokenSize(token.getBytes(StandardCharsets.UTF_8).length)
                    .withIssuer(tokenToIssuer.get(token));

            AccessTokenContent result = tokenValidator.createAccessToken(token);
            recorder.withSuccess(true);
            return result;

        } catch (TokenValidationException e) {
            // This would be recorded in the finally block of try-with-resources
            throw new RuntimeException("Unexpected validation failure during concurrent validation", e);
        }
    }
}