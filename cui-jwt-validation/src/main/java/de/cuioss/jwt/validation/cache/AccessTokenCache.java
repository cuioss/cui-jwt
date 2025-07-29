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
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Builder;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Thread-safe cache for validated access tokens.
 * <p>
 * This cache stores successfully validated access tokens to avoid redundant validation
 * of the same tokens. It uses SHA-256 hashing for cache keys to minimize memory usage
 * while maintaining security through raw token verification on cache hits.
 * <p>
 * Features:
 * <ul>
 *   <li>SHA-256 hashing of tokens for cache keys</li>
 *   <li>Configurable maximum cache size with LRU eviction</li>
 *   <li>Automatic expiration checking on retrieval</li>
 *   <li>Background thread for periodic expired token cleanup</li>
 *   <li>Security event tracking for cache hits</li>
 *   <li>No external dependencies (Quarkus compatible)</li>
 * </ul>
 * <p>
 * The cache respects issuer boundaries by including the issuer in the cache key.
 * This prevents tokens from different issuers with the same content from colliding.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class AccessTokenCache {

    private static final CuiLogger LOGGER = new CuiLogger(AccessTokenCache.class);

    /**
     * Default maximum number of tokens to cache.
     */
    public static final int DEFAULT_MAX_SIZE = 1000;

    /**
     * Default interval for background eviction in seconds.
     */
    public static final long DEFAULT_EVICTION_INTERVAL_SECONDS = 300; // 5 minutes

    /**
     * The maximum number of tokens to cache.
     */
    private final int maxSize;

    /**
     * The main cache storage using ConcurrentHashMap for thread safety.
     * Key: SHA-256 hash of (issuer + token)
     * Value: CachedToken wrapper
     */
    private final Map<String, CachedToken> cache;

    /**
     * LRU tracking map protected by read-write lock.
     * This separate map tracks access order for LRU eviction.
     */
    private final LinkedHashMap<String, Long> lruMap;

    /**
     * Read-write lock for protecting LRU operations.
     */
    private final ReadWriteLock lruLock = new ReentrantReadWriteLock();

    /**
     * Security event counter for tracking cache hits.
     */
    @NonNull
    private final SecurityEventCounter securityEventCounter;

    /**
     * Background executor for expired token eviction.
     */
    private final ScheduledExecutorService evictionExecutor;

    /**
     * Creates a new AccessTokenCache with the specified configuration.
     *
     * @param maxSize the maximum number of tokens to cache
     * @param evictionIntervalSeconds the interval between eviction runs in seconds
     * @param securityEventCounter the security event counter for tracking cache hits
     */
    @Builder
    private AccessTokenCache(
            Integer maxSize,
            Long evictionIntervalSeconds,
            @NonNull SecurityEventCounter securityEventCounter) {

        this.maxSize = maxSize != null ? maxSize : DEFAULT_MAX_SIZE;
        long evictionIntervalSeconds1 = evictionIntervalSeconds != null ? evictionIntervalSeconds : DEFAULT_EVICTION_INTERVAL_SECONDS;
        this.securityEventCounter = securityEventCounter;

        if (this.maxSize > 0) {
            // Only initialize cache structures when caching is enabled
            this.cache = new ConcurrentHashMap<>(this.maxSize);
            this.lruMap = new LinkedHashMap<>(this.maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > AccessTokenCache.this.maxSize;
                }
            };

            // Start background eviction thread only when caching is enabled
            this.evictionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "AccessTokenCache-Eviction");
                thread.setDaemon(true);
                return thread;
            });

            this.evictionExecutor.scheduleWithFixedDelay(
                    this::evictExpiredTokens,
                evictionIntervalSeconds1,
                evictionIntervalSeconds1,
                    TimeUnit.SECONDS);

            LOGGER.debug("AccessTokenCache initialized with maxSize=%d, evictionInterval=%ds",
                    this.maxSize, evictionIntervalSeconds1);
        } else {
            // Cache disabled - no cache structures or background threads needed
            this.cache = null;
            this.lruMap = null;
            this.evictionExecutor = null;
            LOGGER.debug("AccessTokenCache disabled (maxSize=0) - no executor started");
        }
    }

    /**
     * Computes and caches a token if absent, following the Map.computeIfAbsent pattern.
     * <p>
     * This method first checks if a valid cached token exists. If found and still valid,
     * it returns the cached token and increments the cache hit counter. Otherwise, it
     * calls the validation function to validate the token and caches the result.
     *
     * @param issuer the issuer of the token (used in cache key)
     * @param tokenString the raw JWT token string
     * @param validationFunction the function to validate the token if not cached
     * @return the cached or newly validated access token content, or null if validation fails
     */
    public AccessTokenContent computeIfAbsent(
            @NonNull String issuer,
            @NonNull String tokenString,
            @NonNull Function<String, AccessTokenContent> validationFunction) {

        // If cache size is 0, caching is disabled - call validation function directly without caching
        if (maxSize == 0) {
            return validationFunction.apply(tokenString);
        }

        // Generate cache key
        String cacheKey = generateCacheKey(issuer, tokenString);
        if (cacheKey == null) {
            // Failed to generate key, skip caching
            return validationFunction.apply(tokenString);
        }

        // Track if the validation function was called (indicates cache miss)
        final boolean[] functionWasCalled = {false};

        // Use ConcurrentHashMap's atomic computeIfAbsent for thread-safe operation
        CachedToken cachedResult = cache.computeIfAbsent(cacheKey, key -> {
            // This function is called atomically only if the key is not present
            functionWasCalled[0] = true;

            // Validate the token (only happens once for concurrent requests)
            AccessTokenContent validated = validationFunction.apply(tokenString);
            if (validated != null) {
                try {
                    OffsetDateTime expirationTime = validated.getExpirationTime();
                    // Enforce size before adding
                    enforceSize();

                    CachedToken newCachedToken = CachedToken.builder()
                            .rawToken(tokenString)
                            .content(validated)
                            .expirationTime(expirationTime)
                            .build();

                    updateLru(cacheKey);
                    LOGGER.debug("Token cached, current size: %d", cache.size());
                    return newCachedToken;
                } catch (IllegalStateException e) {
                    // No expiration claim, don't cache
                    LOGGER.debug("Token has no expiration time, not caching: %s", e.getMessage());
                } catch (Exception e) {
                    // Any other exception
                    LOGGER.error(e, "Unexpected error while caching token");
                }
            }

            // Don't cache - return null
            return null;
        });

        // Check if we got a cached result
        if (cachedResult != null) {
            OffsetDateTime now = OffsetDateTime.now();
            if (cachedResult.verifyToken(tokenString) && !cachedResult.isExpired(now)) {
                // Only increment cache hit if the function was NOT called (true cache hit)
                if (!functionWasCalled[0]) {
                    LOGGER.debug(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CACHE_HIT::format);
                    securityEventCounter.increment(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT);
                }
                updateLru(cacheKey);
                return cachedResult.getContent();
            } else {
                // Cached entry is invalid, remove and fallback to validation
                cache.remove(cacheKey, cachedResult);
                removeLru(cacheKey);
                return validationFunction.apply(tokenString);
            }
        } else {
            // The lambda was executed but returned null (no expiration or validation failed)
            // Check if the validation function was already called inside computeIfAbsent
            if (functionWasCalled[0]) {
                // Validation already happened inside the lambda, return null
                return null;
            } else {
                // This case shouldn't happen with ConcurrentHashMap.computeIfAbsent
                // but if it does, call the validation function
                LOGGER.debug("Unexpected: cachedResult is null but function was not called");
                return validationFunction.apply(tokenString);
            }
        }
    }

    /**
     * Generates a cache key using SHA-256 hash of issuer + token.
     *
     * @return the base64-encoded hash, or null if hashing fails
     */
    private String generateCacheKey(String issuer, String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(issuer.getBytes(StandardCharsets.UTF_8));
            digest.update(token.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error(e, "Failed to create SHA-256 digest");
            return null;
        }
    }

    /**
     * Updates LRU tracking for a cache key.
     */
    private void updateLru(String key) {
        if (lruMap != null) {
            lruLock.writeLock().lock();
            try {
                lruMap.put(key, System.currentTimeMillis());
            } finally {
                lruLock.writeLock().unlock();
            }
        }
    }

    /**
     * Removes a key from LRU tracking.
     */
    private void removeLru(String key) {
        if (lruMap != null) {
            lruLock.writeLock().lock();
            try {
                lruMap.remove(key);
            } finally {
                lruLock.writeLock().unlock();
            }
        }
    }

    /**
     * Enforces cache size limit using LRU eviction.
     */
    private void enforceSize() {
        if (cache != null && cache.size() >= maxSize) {
            lruLock.writeLock().lock();
            try {
                // Find oldest entry
                if (lruMap != null && !lruMap.isEmpty()) {
                    String oldestKey = lruMap.entrySet().iterator().next().getKey();
                    cache.remove(oldestKey);
                    lruMap.remove(oldestKey);
                    LOGGER.debug("Evicted oldest token from cache due to size limit");
                }
            } finally {
                lruLock.writeLock().unlock();
            }
        }
    }

    /**
     * Background task to evict expired tokens.
     */
    private void evictExpiredTokens() {
        if (cache == null) {
            return; // No cache to evict from
        }

        try {
            OffsetDateTime now = OffsetDateTime.now();
            int evicted = 0;

            Iterator<Map.Entry<String, CachedToken>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, CachedToken> entry = iterator.next();
                if (entry.getValue().isExpired(now)) {
                    iterator.remove();
                    removeLru(entry.getKey());
                    evicted++;
                }
            }

            if (evicted > 0) {
                LOGGER.debug("Evicted %d expired tokens from cache", evicted);
            }
        } catch (Exception e) {
            LOGGER.error(e, "Error during cache eviction");
        }
    }

    /**
     * Shuts down the cache and its background threads.
     * Should be called when the cache is no longer needed.
     */
    public void shutdown() {
        if (evictionExecutor != null) {
            evictionExecutor.shutdown();
            try {
                if (!evictionExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    evictionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                evictionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (cache != null) {
            cache.clear();
            lruLock.writeLock().lock();
            try {
                lruMap.clear();
            } finally {
                lruLock.writeLock().unlock();
            }
        }
        LOGGER.debug("AccessTokenCache shut down");
    }

    /**
     * Gets the current cache size.
     *
     * @return the number of tokens currently cached
     */
    public int size() {
        return cache != null ? cache.size() : 0;
    }


    /**
     * Clears all tokens from the cache.
     */
    public void clear() {
        if (cache != null) {
            cache.clear();
            lruLock.writeLock().lock();
            try {
                lruMap.clear();
            } finally {
                lruLock.writeLock().unlock();
            }
            LOGGER.debug("AccessTokenCache cleared");
        }
    }
}