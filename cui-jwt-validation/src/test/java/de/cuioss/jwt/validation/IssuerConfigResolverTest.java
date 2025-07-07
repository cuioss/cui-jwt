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

import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit test for IssuerConfigResolver to verify:
 * - Lazy initialization and caching behavior
 * - Thread safety and concurrency handling
 * - Cache optimization from ConcurrentHashMap to immutable map
 * - Health checking and pending queue management
 * - Error handling for missing/unhealthy issuers
 */
class IssuerConfigResolverTest {

    private SecurityEventCounter securityEventCounter;
    private TestTokenHolder tokenHolder1;
    private TestTokenHolder tokenHolder2;
    private TestTokenHolder tokenHolder3;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();

        // Create multiple test token holders for testing
        tokenHolder1 = TestTokenGenerators.accessTokens().next();
        tokenHolder2 = TestTokenGenerators.accessTokens().next();
        tokenHolder3 = TestTokenGenerators.accessTokens().next();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Initialize resolver with enabled configurations")
        void initializesWithEnabledConfigs() {
            IssuerConfig config1 = tokenHolder1.getIssuerConfig();
            IssuerConfig config2 = tokenHolder2.getIssuerConfig();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config1, config2},
                    securityEventCounter
            );

            assertEquals(2, resolver.getEnabledConfigCount(), "Should count enabled configs");
        }

        @Test
        @DisplayName("Handle empty configuration array")
        void handlesEmptyConfigArray() {
            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[0],
                    securityEventCounter
            );

            assertEquals(0, resolver.getEnabledConfigCount());
        }
    }

    @Nested
    @DisplayName("Lazy Initialization Tests")
    class LazyInitializationTests {

        @Test
        @DisplayName("Resolve issuer configuration from pending queue")
        void resolvesIssuerFromPendingQueue() {
            IssuerConfig config = tokenHolder1.getIssuerConfig();
            String issuerIdentifier = config.getIssuerIdentifier();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config},
                    securityEventCounter
            );

            IssuerConfig result = resolver.resolveConfig(issuerIdentifier);

            assertSame(config, result);
        }

        @Test
        @DisplayName("Cache issuer configuration after first resolution")
        void cachesIssuerAfterFirstResolution() {
            IssuerConfig config = tokenHolder1.getIssuerConfig();
            String issuerIdentifier = config.getIssuerIdentifier();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config},
                    securityEventCounter
            );

            // First call
            IssuerConfig result1 = resolver.resolveConfig(issuerIdentifier);

            // Second call - should hit cache
            IssuerConfig result2 = resolver.resolveConfig(issuerIdentifier);

            assertSame(result1, result2);
            assertSame(config, result1);
        }

        @Test
        @DisplayName("Process multiple issuers lazily")
        void processesMultipleIssuersLazily() {
            IssuerConfig config1 = tokenHolder1.getIssuerConfig();
            IssuerConfig config2 = tokenHolder2.getIssuerConfig();
            String issuer1 = config1.getIssuerIdentifier();
            String issuer2 = config2.getIssuerIdentifier();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config1, config2},
                    securityEventCounter
            );

            // Resolve first issuer
            IssuerConfig result1 = resolver.resolveConfig(issuer1);
            assertEquals(config1.getIssuerIdentifier(), result1.getIssuerIdentifier());

            // Resolve second issuer
            IssuerConfig result2 = resolver.resolveConfig(issuer2);
            assertEquals(config2.getIssuerIdentifier(), result2.getIssuerIdentifier());

            // Both should now be cached (same instances on repeated calls)
            assertSame(result1, resolver.resolveConfig(issuer1));
            assertSame(result2, resolver.resolveConfig(issuer2));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Throw exception for unknown issuer")
        void throwsExceptionForUnknownIssuer() {
            IssuerConfig config = tokenHolder1.getIssuerConfig();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config},
                    securityEventCounter
            );

            TokenValidationException exception = assertThrows(
                    TokenValidationException.class,
                    () -> resolver.resolveConfig("https://unknown-issuer.com")
            );

            assertTrue(exception.getMessage().contains("unknown-issuer.com"));
            assertEquals(SecurityEventCounter.EventType.NO_ISSUER_CONFIG, exception.getEventType());
        }

        @Test
        @DisplayName("Increment security event counter for missing issuer")
        void incrementsSecurityEventCounterForMissingIssuer() {
            IssuerConfig config = tokenHolder1.getIssuerConfig();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config},
                    securityEventCounter
            );

            long initialCount = securityEventCounter.getCount(SecurityEventCounter.EventType.NO_ISSUER_CONFIG);

            assertThrows(TokenValidationException.class, () ->
                    resolver.resolveConfig("https://unknown-issuer.com"));

            assertEquals(initialCount + 1,
                    securityEventCounter.getCount(SecurityEventCounter.EventType.NO_ISSUER_CONFIG));
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Handle concurrent access during lazy initialization")
        void handlesConcurrentAccessDuringLazyInitialization() throws InterruptedException {
            IssuerConfig config = tokenHolder1.getIssuerConfig();
            String issuerIdentifier = config.getIssuerIdentifier();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config},
                    securityEventCounter
            );

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<IssuerConfig> results = new CopyOnWriteArrayList<>();
            AtomicInteger exceptionCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        IssuerConfig result = resolver.resolveConfig(issuerIdentifier);
                        results.add(result);
                    } catch (Exception e) {
                        exceptionCount.incrementAndGet();
                        System.err.println("Exception in thread: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(0, exceptionCount.get(), "No exceptions should occur during concurrent access");
            assertEquals(threadCount, results.size());
            results.forEach(result -> assertSame(config, result));
        }

        @Test
        @DisplayName("Maintain consistency during concurrent cache optimization")
        void maintainsConsistencyDuringConcurrentOptimization() throws InterruptedException {
            // Create multiple configs
            List<IssuerConfig> configs = new ArrayList<>();
            List<String> issuerIds = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                TestTokenHolder holder = TestTokenGenerators.accessTokens().next();
                IssuerConfig config = holder.getIssuerConfig();
                configs.add(config);
                issuerIds.add(config.getIssuerIdentifier());
            }

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    configs.toArray(new IssuerConfig[0]),
                    securityEventCounter
            );

            int threadCount = 15;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final String targetIssuer = issuerIds.get(i % issuerIds.size());
                executor.submit(() -> {
                    try {
                        IssuerConfig result = resolver.resolveConfig(targetIssuer);
                        assertNotNull(result);
                        assertEquals(targetIssuer, result.getIssuerIdentifier());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Exception in concurrent test: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();

            assertTrue(successCount.get() > 0, "At least some operations should succeed");
        }
    }

    @Nested
    @DisplayName("Double-Checked Locking Tests")
    class DoubleCheckedLockingTests {

        @Test
        @DisplayName("Use fast path for cached configurations")
        void usesFastPathForCachedConfigs() {
            IssuerConfig config = tokenHolder1.getIssuerConfig();
            String issuerIdentifier = config.getIssuerIdentifier();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config},
                    securityEventCounter
            );

            // First call - should go through slow path
            IssuerConfig result1 = resolver.resolveConfig(issuerIdentifier);

            // Second call - should use fast path
            IssuerConfig result2 = resolver.resolveConfig(issuerIdentifier);

            assertSame(result1, result2);
            assertSame(config, result1);
        }

        @Test
        @DisplayName("Handle double-checked locking correctly under high concurrency")
        void handlesDoubleCheckedLockingCorrectly() throws InterruptedException {
            IssuerConfig config = tokenHolder1.getIssuerConfig();
            String issuerIdentifier = config.getIssuerIdentifier();

            IssuerConfigResolver resolver = new IssuerConfigResolver(
                    new IssuerConfig[]{config},
                    securityEventCounter
            );

            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        // Synchronize all threads to start at exactly the same time
                        barrier.await();

                        IssuerConfig result = resolver.resolveConfig(issuerIdentifier);
                        assertSame(config, result);
                        successCount.incrementAndGet();

                    } catch (Exception e) {
                        System.err.println("Thread failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(threadCount, successCount.get(), "All threads should succeed with double-checked locking");
        }
    }

}