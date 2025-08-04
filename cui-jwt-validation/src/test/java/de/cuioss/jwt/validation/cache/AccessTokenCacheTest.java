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
package de.cuioss.jwt.validation.cache;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.InternalCacheException;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
class AccessTokenCacheTest {

    private static final CuiLogger LOGGER = new CuiLogger(AccessTokenCacheTest.class);

    private AccessTokenCache cache;
    private SecurityEventCounter securityEventCounter;
    private TokenValidatorMonitor performanceMonitor;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        performanceMonitor = TokenValidatorMonitorConfig.builder()
                .measurementTypes(TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES)
                .build()
                .createMonitor();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(10)
                .evictionIntervalSeconds(300L) // Use longer interval to avoid race conditions in tests
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
    }

    @Test
    void cacheMiss() {
        // Given
        String issuer = "https://example.com";
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwiZXhwIjoxOTk5OTk5OTk5fQ.signature";
        AtomicInteger validationCount = new AtomicInteger(0);
        AccessTokenContent expectedContent = createAccessToken("https://example.com",
                OffsetDateTime.now().plusHours(1));

        // When
        AccessTokenContent result = cache.computeIfAbsent(token, t -> {
            validationCount.incrementAndGet();
            return expectedContent;
        }, performanceMonitor);

        // Then
        assertNotNull(result);
        assertEquals(expectedContent, result);
        assertEquals(1, validationCount.get());
        assertEquals(1, cache.size());
        // No cache hit on first access
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT));
    }

    @Test
    void cacheHit() {
        // Given
        String issuer = "https://example.com";
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwiZXhwIjoxOTk5OTk5OTk5fQ.signature";
        AtomicInteger validationCount = new AtomicInteger(0);
        AccessTokenContent expectedContent = createAccessToken("https://example.com",
                OffsetDateTime.now().plusHours(1));

        // First access - cache miss
        cache.computeIfAbsent(token, t -> {
            validationCount.incrementAndGet();
            return expectedContent;
        }, performanceMonitor);

        // When - second access should be cache hit
        AccessTokenContent result = cache.computeIfAbsent(token, t -> {
            validationCount.incrementAndGet();
            return expectedContent;
        }, performanceMonitor);

        // Then
        assertNotNull(result);
        assertEquals(expectedContent, result);
        assertEquals(1, validationCount.get()); // Validation function called only once
        assertEquals(1, cache.size());
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT));
    }

    @Test
    void expiredTokenNotReturned() {
        // This test verifies that expired tokens are detected and throw exception
        
        // Given
        String issuer = "https://example.com";
        AtomicInteger validationCount = new AtomicInteger(0);

        // Create test data with consistent token
        String testToken = "test-jwt-token";

        // First access - cache a token that will expire soon
        AccessTokenContent expiredContent = createAccessTokenWithRawToken(
                "https://example.com",
                OffsetDateTime.now().plusSeconds(1), // Will expire in 1 second
                testToken
        );

        AccessTokenContent result1 = cache.computeIfAbsent(testToken, t -> {
            validationCount.incrementAndGet();
            return expiredContent;
        }, performanceMonitor);

        assertEquals(expiredContent, result1);
        assertEquals(1, validationCount.get());
        assertEquals(1, cache.size());

        // Wait for token to expire
        try {
            Thread.sleep(1100); // Wait 1.1 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When - second access should detect expiration and throw exception
        TokenValidationException exception =
                assertThrows(TokenValidationException.class, () ->
                        cache.computeIfAbsent(testToken, t -> {
                            validationCount.incrementAndGet();
                            fail("Validation function should not be called for expired cached token");
                            return null;
                        }, performanceMonitor));

        // Then
        assertEquals(SecurityEventCounter.EventType.TOKEN_EXPIRED, exception.getEventType());
        assertEquals(1, validationCount.get()); // Validation function called only once
        assertEquals(0, cache.size()); // Cache cleared after detecting expiration
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EXPIRED));
    }

    @Test
    void concurrentAccess() throws InterruptedException {
        // Given
        String issuer = "https://example.com";
        String token = "concurrent-token";
        AtomicInteger validationCount = new AtomicInteger(0);
        AccessTokenContent content = createAccessToken(issuer, OffsetDateTime.now().plusHours(1));

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - multiple threads access the same token concurrently
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    AccessTokenContent result = cache.computeIfAbsent(token, t -> {
                        validationCount.incrementAndGet();
                        // Simulate some processing time
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return content;
                    }, performanceMonitor);
                    assertNotNull(result);
                    assertEquals(content, result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads at once
        startLatch.countDown();

        // Wait for completion
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Then - validation should happen only once despite concurrent access
        assertEquals(1, validationCount.get());
        assertEquals(1, cache.size());

        // Cache hits are only counted for pre-existing cached values.
        // When multiple threads concurrently compute the same key, only the first
        // thread actually performs validation. The other threads wait for the result
        // but this is not counted as a cache hit since the value wasn't pre-existing.
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT));

        // Now access the token again - this should be a true cache hit
        AccessTokenContent cachedResult = cache.computeIfAbsent(token, t -> {
            validationCount.incrementAndGet();
            fail("Validation function should not be called for cached token");
            return null;
        }, performanceMonitor);

        assertNotNull(cachedResult);
        assertEquals(content, cachedResult);
        assertEquals(1, validationCount.get()); // Still only called once
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT));
    }

    private AccessTokenContent createAccessToken(String issuer, OffsetDateTime expiration) {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString(issuer));
        if (expiration != null) {
            tokenHolder.withClaim(ClaimName.EXPIRATION.getName(), ClaimValue.forDateTime(String.valueOf(expiration.toEpochSecond()), expiration));
        }
        tokenHolder.withClaim(ClaimName.ISSUED_AT.getName(), ClaimValue.forDateTime(String.valueOf(OffsetDateTime.now().minusMinutes(5).toEpochSecond()), OffsetDateTime.now().minusMinutes(5)));
        tokenHolder.withClaim(ClaimName.TOKEN_ID.getName(), ClaimValue.forPlainString("jti-" + System.nanoTime()));
        return tokenHolder.asAccessTokenContent();
    }

    private AccessTokenContent createAccessTokenWithRawToken(String issuer, OffsetDateTime expiration, String rawToken) {
        // Create a simple AccessTokenContent with the specified raw token
        // This simulates what would happen in real JWT validation where the raw token matches the input
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString(issuer));
        if (expiration != null) {
            tokenHolder.withClaim(ClaimName.EXPIRATION.getName(), ClaimValue.forDateTime(String.valueOf(expiration.toEpochSecond()), expiration));
        }
        tokenHolder.withClaim(ClaimName.ISSUED_AT.getName(), ClaimValue.forDateTime(String.valueOf(OffsetDateTime.now().minusMinutes(5).toEpochSecond()), OffsetDateTime.now().minusMinutes(5)));
        tokenHolder.withClaim(ClaimName.TOKEN_ID.getName(), ClaimValue.forPlainString("jti-" + System.nanoTime()));

        // Get the generated content
        AccessTokenContent generated = tokenHolder.asAccessTokenContent();

        // Create a new instance with our specified raw token
        return new AccessTokenContent(generated.getClaims(), rawToken, TokenType.ACCESS_TOKEN.getTypeClaimName());
    }

    @Test
    void validationFunctionReturnsNull() {
        // Given
        String token = "test-token";

        // When & Then
        InternalCacheException exception = assertThrows(InternalCacheException.class, () ->
                cache.computeIfAbsent(token, t -> null, performanceMonitor));

        assertEquals("Validation function returned null - expected exception on failure", exception.getMessage());
        LogAsserts.assertLogMessagePresent(TestLogLevel.ERROR,
                JWTValidationLogMessages.ERROR.CACHE_VALIDATION_FUNCTION_NULL.format());
    }

    @Test
    void cacheEvictionUnderLoad() {
        // Given - cache with maxSize 10
        assertEquals(0, cache.size());

        // When - fill cache to capacity
        for (int i = 0; i < 10; i++) {
            String token = "token-" + i;
            AccessTokenContent content = createAccessToken("https://example.com",
                    OffsetDateTime.now().plusHours(1));
            AccessTokenContent result = cache.computeIfAbsent(token, t -> content, performanceMonitor);
            assertNotNull(result);
        }

        // Then - cache should be at capacity
        assertEquals(10, cache.size());

        // When - add 11th token to trigger eviction
        String overflowToken = "token-10";
        AccessTokenContent overflowContent = createAccessToken("https://example.com",
                OffsetDateTime.now().plusHours(1));
        AccessTokenContent result = cache.computeIfAbsent(overflowToken, t -> overflowContent, performanceMonitor);

        // Then - should not throw ConcurrentModificationException
        assertNotNull(result);
        assertEquals(overflowContent, result);
        // Cache size should still be 10 (one token was evicted)
        assertEquals(10, cache.size());
    }

    @Test
    void concurrentCacheEviction() throws InterruptedException {
        // Given - smaller cache to make eviction more likely
        cache.shutdown();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(5)
                .evictionIntervalSeconds(300L)
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);

        int threadCount = 10;
        int tokensPerThread = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // When - multiple threads add different tokens concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < tokensPerThread; j++) {
                        String token = "thread-" + threadId + "-token-" + j;
                        try {
                            AccessTokenContent content = createAccessToken("https://example.com",
                                    OffsetDateTime.now().plusHours(1));
                            AccessTokenContent result = cache.computeIfAbsent(token, t -> content, performanceMonitor);
                            assertNotNull(result);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            LOGGER.error("Error during concurrent cache eviction test", e);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads at once
        startLatch.countDown();

        // Wait for completion
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Then - no errors should occur
        assertEquals(0, errorCount.get());
        assertEquals(threadCount * tokensPerThread, successCount.get());
        // Cache size should be at max
        assertTrue(cache.size() <= 5);
        assertTrue(cache.size() > 0);
    }

    @Test
    void cacheEvictionBatchRemoval() {
        // Given - cache that evicts 10% when full
        cache.shutdown();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(100)
                .evictionIntervalSeconds(300L)
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);

        // When - fill cache to capacity
        for (int i = 0; i < 100; i++) {
            String token = "token-" + i;
            AccessTokenContent content = createAccessToken("https://example.com",
                    OffsetDateTime.now().plusHours(1));
            cache.computeIfAbsent(token, t -> content, performanceMonitor);
        }

        assertEquals(100, cache.size());

        // When - add one more token to trigger batch eviction
        cache.computeIfAbsent("overflow-token", t ->
                        createAccessToken("https://example.com", OffsetDateTime.now().plusHours(1)),
                performanceMonitor);

        // Then - should have evicted ~10% of entries
        assertTrue(cache.size() <= 91); // Should have evicted at least 10 entries
        assertTrue(cache.size() >= 90); // But not too many
    }

    @Test
    void cacheDisabledNoEvictionLogic() {
        // Given - cache with size 0 (disabled)
        cache.shutdown();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(0)
                .evictionIntervalSeconds(300L)
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);

        // When - try to cache tokens
        for (int i = 0; i < 10; i++) {
            String token = "token-" + i;
            AtomicInteger validationCount = new AtomicInteger(0);
            AccessTokenContent content = createAccessToken("https://example.com",
                    OffsetDateTime.now().plusHours(1));

            AccessTokenContent result = cache.computeIfAbsent(token, t -> {
                validationCount.incrementAndGet();
                return content;
            }, performanceMonitor);

            // Then - validation happens every time (no caching)
            assertNotNull(result);
            assertEquals(1, validationCount.get());
        }

        // Cache should remain empty
        assertEquals(0, cache.size());
    }

    @Test
    void evictionExecutorRemovesExpiredTokens() {
        // Given - cache with eviction interval of 1 second
        cache.shutdown();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(30) // Enough space for all tokens
                .evictionIntervalSeconds(1L) // Run eviction every second
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);

        // When - add 20 tokens that will expire in 5 seconds
        OffsetDateTime expirationTime = OffsetDateTime.now().plusSeconds(5);
        for (int i = 0; i < 20; i++) {
            String token = "eviction-test-token-" + i;
            String rawToken = "raw-token-" + i; // Unique raw token for each entry
            AccessTokenContent content = createAccessTokenWithRawToken(
                    "https://example.com",
                    expirationTime,
                    rawToken
            );

            cache.computeIfAbsent(rawToken, t -> content, performanceMonitor);
        }

        // Then - verify all 20 tokens are in cache
        assertEquals(20, cache.size());

        // Use awaitility to verify that after max 10 seconds all entries are evicted
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertEquals(0, cache.size(), "All expired tokens should be evicted"));

        // Verify we can still add new tokens after eviction
        String newToken = "new-token-after-eviction";
        AccessTokenContent newContent = createAccessToken("https://example.com",
                OffsetDateTime.now().plusHours(1));
        AccessTokenContent result = cache.computeIfAbsent(newToken, t -> newContent, performanceMonitor);

        assertNotNull(result);
        assertEquals(1, cache.size());
    }


}