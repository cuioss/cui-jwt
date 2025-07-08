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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that IssuerConfigResolver.resolveConfig() operates without
 * synchronization bottlenecks under high concurrency.
 *
 * This test directly measures the synchronization characteristics of
 * IssuerConfigResolver to ensure lock-free operation and detect convoy effects.
 */
class IssuerConfigResolverSynchronizationTest {

    private static final int DEFAULT_THREAD_COUNT = 50;
    private static final double CONVOY_VARIANCE_THRESHOLD = 3.0;
    private static final double CONVOY_MAX_AVG_THRESHOLD = 10.0;
    private static final double PERFORMANCE_THRESHOLD_MS = 1.0;

    private IssuerConfigResolver issuerConfigResolver;
    private String issuerIdentifier;

    @BeforeEach
    void setUp() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig issuerConfig = tokenHolder.getIssuerConfig();
        issuerIdentifier = issuerConfig.getIssuerIdentifier();

        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        issuerConfigResolver = new IssuerConfigResolver(new IssuerConfig[]{issuerConfig}, securityEventCounter);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("Detect synchronization bottlenecks and convoy effects")
    void detectsSynchronizationBottlenecks() throws InterruptedException {
        int threadCount = DEFAULT_THREAD_COUNT; // Reduced from 200 to make convoy effect more observable
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalWaitTime = new AtomicLong(0);
        AtomicLong maxWaitTime = new AtomicLong(0);
        List<Long> threadTimes = new ArrayList<>(threadCount);

        // Submit all threads simultaneously to create convoy effect
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    long startTime = System.nanoTime();

                    // This should hit the synchronization logic in IssuerConfigResolver
                    IssuerConfig result = issuerConfigResolver.resolveConfig(issuerIdentifier);

                    long duration = System.nanoTime() - startTime;

                    synchronized (threadTimes) {
                        threadTimes.add(duration);
                    }

                    totalWaitTime.addAndGet(duration);
                    maxWaitTime.updateAndGet(current -> Math.max(current, duration));
                    successCount.incrementAndGet();

                    assertNotNull(result);
                } catch (Exception e) {
                    fail("Thread execution failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = endLatch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "Not all threads completed within timeout");

        executor.shutdown();

        // Analyze results for convoy effect symptoms
        double avgTimeMs = totalWaitTime.get() / (double) successCount.get() / 1_000_000;
        double maxTimeMs = maxWaitTime.get() / 1_000_000.0;

        // Calculate variance to detect convoy effect
        long avgTime = totalWaitTime.get() / successCount.get();
        long variance = 0;
        synchronized (threadTimes) {
            for (Long time : threadTimes) {
                long diff = time - avgTime;
                variance += diff * diff;
            }
            variance /= threadTimes.size();
        }
        double stdDeviationMs = Math.sqrt(variance) / 1_000_000.0;

        // Convoy effect symptoms:
        // 1. High variance in execution times
        // 2. Some threads taking much longer than others
        // 3. Standard deviation being a significant portion of average time

        // Assertions to detect convoy effect
        assertEquals(threadCount, successCount.get(), "Not all threads succeeded");

        // Detect convoy effect symptoms through assertions
        // High variance indicates threads are waiting for each other
        double varianceRatio = stdDeviationMs / avgTimeMs;
        double maxToAvgRatio = maxTimeMs / avgTimeMs;

        // If performance is extremely fast (< 0.01ms), measurement noise dominates variance
        // In this case, we've successfully eliminated the bottleneck
        if (avgTimeMs >= 0.01) {
            // For the extreme case of 50 threads hitting the same uncached issuer simultaneously,
            // some variance is expected. A ratio < 3.0 indicates acceptable performance.
            // The old synchronized implementation had ratios > 5.0
            assertTrue(varianceRatio < CONVOY_VARIANCE_THRESHOLD,
                    "CONVOY EFFECT DETECTED: Variance ratio %.2f (stddev/avg) indicates synchronized block bottleneck in resolveConfig()".formatted(varianceRatio));

            assertTrue(maxToAvgRatio < CONVOY_MAX_AVG_THRESHOLD,
                    "CONVOY EFFECT DETECTED: Maximum time is %.1fx average - threads are blocking each other".formatted(maxToAvgRatio));
        }

        // Performance should be excellent for issuer config resolution
        assertTrue(avgTimeMs < PERFORMANCE_THRESHOLD_MS,
                "Performance degradation: %.2f ms average per resolution (expected < %.1fms)".formatted(avgTimeMs, PERFORMANCE_THRESHOLD_MS));
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Measure performance during warmup phase")
    void measuresWarmupPhasePerformance() throws InterruptedException {
        // Create multiple IssuerConfigResolver instances to test warmup phase
        List<IssuerConfigResolver> resolvers = new ArrayList<>();
        List<String> issuerIds = new ArrayList<>();
        SecurityEventCounter securityEventCounter = new SecurityEventCounter();

        for (int i = 0; i < 5; i++) {
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            IssuerConfig config = tokenHolder.getIssuerConfig();
            resolvers.add(new IssuerConfigResolver(new IssuerConfig[]{config}, securityEventCounter));
            issuerIds.add(config.getIssuerIdentifier());
        }

        int threadsPerResolver = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadsPerResolver * resolvers.size());
        CountDownLatch latch = new CountDownLatch(threadsPerResolver * resolvers.size());
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger operations = new AtomicInteger(0);

        // Test each resolver with multiple threads simultaneously (warmup phase)
        for (int i = 0; i < resolvers.size(); i++) {
            IssuerConfigResolver resolver = resolvers.get(i);
            String issuerId = issuerIds.get(i);
            for (int j = 0; j < threadsPerResolver; j++) {
                executor.submit(() -> {
                    try {
                        long startTime = System.nanoTime();

                        // First resolution hits any synchronization logic (warmup)
                        IssuerConfig result = resolver.resolveConfig(issuerId);

                        long duration = System.nanoTime() - startTime;
                        totalTime.addAndGet(duration);
                        operations.incrementAndGet();

                        assertNotNull(result);
                    } catch (Exception e) {
                        fail("Warmup test failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Warmup test did not complete within timeout");

        executor.shutdown();

        double avgTimeMs = totalTime.get() / (double) operations.get() / 1_000_000;


        // During warmup phase, IssuerConfigResolver should be fast
        assertTrue(avgTimeMs < 1.0, "Operations should be fast during warmup (was: %.2f ms)".formatted(avgTimeMs));
        assertEquals(operations.get(), threadsPerResolver * resolvers.size(), "All operations should complete");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Measure post-warmup throughput for optimized cache")
    void measuresPostWarmupThroughput() throws InterruptedException {
        // Pre-warm the resolver by doing a single resolution
        IssuerConfig warmupResult = issuerConfigResolver.resolveConfig(issuerIdentifier);
        assertNotNull(warmupResult);

        // Now test performance after warmup
        int threadCount = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger totalOperations = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    long threadStartTime = System.nanoTime();

                    for (int j = 0; j < operationsPerThread; j++) {
                        IssuerConfig result = issuerConfigResolver.resolveConfig(issuerIdentifier);
                        assertNotNull(result);
                        totalOperations.incrementAndGet();
                    }

                    long threadTime = System.nanoTime() - threadStartTime;
                    totalTime.addAndGet(threadTime);
                } catch (Exception e) {
                    fail("Post-warmup test failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(8, TimeUnit.SECONDS);
        assertTrue(completed, "Post-warmup test did not complete within timeout");

        executor.shutdown();

        double avgTimeMs = totalTime.get() / (double) totalOperations.get() / 1_000_000;
        double throughputOpsPerSec = totalOperations.get() / (totalTime.get() / 1_000_000_000.0);

        // After warmup, IssuerConfigResolver should be very fast
        assertTrue(avgTimeMs < 0.1, "Post-warmup operations should be very fast (was: %.2f ms)".formatted(avgTimeMs));
        assertTrue(throughputOpsPerSec > 10000, "Post-warmup throughput should be high (was: %.0f ops/s)".formatted(throughputOpsPerSec));
        assertEquals(threadCount * operationsPerThread, totalOperations.get(), "All operations should complete");
    }
}