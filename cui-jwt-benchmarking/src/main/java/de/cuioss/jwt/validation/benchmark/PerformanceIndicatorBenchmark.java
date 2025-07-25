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
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import io.jsonwebtoken.Jwts;
import org.openjdk.jmh.annotations.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consolidated core validation benchmark that measures essential JWT validation performance metrics.
 * <p>
 * This benchmark combines the most critical performance measurements:
 * <ul>
 *   <li><strong>Average Time</strong>: Single-threaded validation latency</li>
 *   <li><strong>Throughput</strong>: Operations per second under concurrent load</li>
 *   <li><strong>Concurrent Validation</strong>: Multi-threaded validation performance</li>
 * </ul>
 * <p>
 * Performance expectations:
 * <ul>
 *   <li>Access token validation: &lt; 100 μs per operation</li>
 *   <li>Concurrent throughput: Linear scalability up to 8 threads</li>
 *   <li>Throughput: &gt; 10,000 operations/second</li>
 * </ul>
 * <p>
 * This benchmark is optimized for fast execution while retaining essential performance insights.
 * It replaces the functionality of TokenValidatorBenchmark, ConcurrentTokenValidationBenchmark,
 * and PerformanceIndicatorBenchmark with streamlined implementations.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class PerformanceIndicatorBenchmark {

    private TokenValidator tokenValidator;
    private String validAccessToken;

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
     * Thread-safe metrics aggregator for collecting measurements across all threads
     */
    private static final Map<String, Map<MeasurementType, AtomicLong>> GLOBAL_METRICS = new ConcurrentHashMap<>();
    private static final Map<String, Map<MeasurementType, AtomicLong>> GLOBAL_COUNTS = new ConcurrentHashMap<>();

    /**
     * Shutdown hook to ensure metrics are exported when JVM exits
     */
    static {
        // Initialize metrics maps
        GLOBAL_METRICS.put("measureAverageTime", new ConcurrentHashMap<>());
        GLOBAL_METRICS.put("measureThroughput", new ConcurrentHashMap<>());
        GLOBAL_METRICS.put("measureConcurrentValidation", new ConcurrentHashMap<>());

        GLOBAL_COUNTS.put("measureAverageTime", new ConcurrentHashMap<>());
        GLOBAL_COUNTS.put("measureThroughput", new ConcurrentHashMap<>());
        GLOBAL_COUNTS.put("measureConcurrentValidation", new ConcurrentHashMap<>());

        // Initialize atomic counters for each measurement type
        for (String benchmarkName : GLOBAL_METRICS.keySet()) {
            for (MeasurementType type : MeasurementType.values()) {
                GLOBAL_METRICS.get(benchmarkName).put(type, new AtomicLong(0));
                GLOBAL_COUNTS.get(benchmarkName).put(type, new AtomicLong(0));
            }
        }

        // Add shutdown hook to export metrics when JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                exportGlobalMetrics();
            } catch (Exception e) {
                // Silent failure - metrics export is optional
            }
        }));
    }

    @Setup(Level.Trial)
    public void setup() {
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

    @TearDown(Level.Iteration)
    public void collectMetrics() {
        // Get the performance monitor from the validator
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        if (monitor != null) {
            // Determine which benchmark is running based on thread name
            String benchmarkName = getCurrentBenchmarkName();

            if (benchmarkName != null) {
                Map<MeasurementType, AtomicLong> metricsSums = GLOBAL_METRICS.get(benchmarkName);
                Map<MeasurementType, AtomicLong> metricsCounts = GLOBAL_COUNTS.get(benchmarkName);

                // Collect metrics for each measurement type
                int metricsCollected = 0;
                for (MeasurementType type : monitor.getEnabledTypes()) {
                    Duration avgDuration = monitor.getAverageDuration(type);
                    int sampleCount = monitor.getSampleCount(type);

                    if (sampleCount > 0) {
                        long totalNanos = avgDuration.toNanos() * sampleCount;
                        metricsSums.get(type).addAndGet(totalNanos);
                        metricsCounts.get(type).addAndGet(sampleCount);
                        metricsCollected++;
                    }
                }

                // Successfully collected metrics
            }
        }
    }

    /**
     * Determines the current benchmark name from the thread name or stack trace
     */
    private String getCurrentBenchmarkName() {
        // JMH typically includes the benchmark method name in the thread name
        String threadName = Thread.currentThread().getName();

        if (threadName.contains("measureAverageTime")) {
            return "measureAverageTime";
        } else if (threadName.contains("measureThroughput")) {
            return "measureThroughput";
        } else if (threadName.contains("measureConcurrentValidation")) {
            return "measureConcurrentValidation";
        }

        // Fallback: check stack trace
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String methodName = element.getMethodName();
            if ("measureAverageTime".equals(methodName) ||
                    "measureThroughput".equals(methodName) ||
                    "measureConcurrentValidation".equals(methodName)) {
                return methodName;
            }
        }

        return null;
    }

    /**
     * Exports collected metrics to a JSON file
     */
    private static void exportGlobalMetrics() {
        try {
            Map<String, Map<String, Double>> aggregatedMetrics = new LinkedHashMap<>();

            for (Map.Entry<String, Map<MeasurementType, AtomicLong>> entry : GLOBAL_METRICS.entrySet()) {
                String benchmarkName = entry.getKey();
                Map<MeasurementType, AtomicLong> sums = entry.getValue();
                Map<MeasurementType, AtomicLong> counts = GLOBAL_COUNTS.get(benchmarkName);

                Map<String, Double> benchmarkMetrics = new LinkedHashMap<>();
                int metricsFound = 0;

                for (MeasurementType type : MeasurementType.values()) {
                    long totalNanos = sums.get(type).get();
                    long sampleCount = counts.get(type).get();

                    if (sampleCount > 0) {
                        double avgMs = (totalNanos / (double) sampleCount) / 1_000_000.0;
                        benchmarkMetrics.put(type.name().toLowerCase(), avgMs);
                        benchmarkMetrics.put(type.name().toLowerCase() + "_count", (double) sampleCount);
                        metricsFound++;
                    }
                }

                if (!benchmarkMetrics.isEmpty()) {
                    aggregatedMetrics.put(benchmarkName, benchmarkMetrics);
                }
            }

            // Export to file
            BenchmarkMetricsCollector.exportAggregatedMetrics(aggregatedMetrics);

        } catch (Exception e) {
            // Silent failure - metrics export is optional
        }
    }

    /**
     * Measures average validation time for single-threaded token validation.
     * <p>
     * This benchmark measures the baseline latency for validating a single
     * access token without concurrent load. Lower values indicate better performance.
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
     * Measures token validation throughput under concurrent load.
     * <p>
     * This benchmark uses 8 threads to measure how many token validations
     * can be performed per second under concurrent load. Higher values indicate better throughput.
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
     * Measures concurrent validation performance with token rotation.
     * <p>
     * This benchmark tests validation performance using a pool of different tokens
     * to simulate real-world scenarios where multiple different tokens are validated
     * concurrently. This provides insight into caching behavior and token diversity impact.
     *
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public AccessTokenContent measureConcurrentValidation() {
        try {
            // Rotate through token pool to simulate different tokens
            String token = tokenPool[tokenIndex++ % TOKEN_POOL_SIZE];
            return tokenValidator.createAccessToken(token);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure during concurrent validation", e);
        }
    }

}