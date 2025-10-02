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
import de.cuioss.jwt.validation.exception.InternalCacheException;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.MetricsTicker;
import de.cuioss.jwt.validation.metrics.MetricsTickerFactory;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Thread-safe cache for validated access tokens using optimistic caching strategy.
 * <p>
 * This cache stores successfully validated access tokens to avoid redundant validation
 * of the same tokens. It uses integer hashCode for cache keys to optimize performance
 * while maintaining security through raw token verification on cache hits.
 * <p>
 * <strong>Optimistic Caching Strategy (Issue #131/#132 Fix):</strong>
 * <p>
 * To eliminate blocking contention under high concurrency, this cache uses an optimistic
 * approach where token validation happens <strong>outside of locks</strong>. When multiple
 * threads validate the same token concurrently:
 * <ul>
 *   <li>All threads perform validation in parallel (no blocking)</li>
 *   <li>First thread to complete stores the result via {@code putIfAbsent}</li>
 *   <li>Other threads discard their work and use the cached result</li>
 *   <li>Trade-off: Some duplicate computation vs. elimination of blocking wait times</li>
 * </ul>
 * <p>
 * This approach dramatically improves throughput under high concurrency (150+ threads)
 * compared to traditional {@code computeIfAbsent} which synchronizes on the cache key.
 * <p>
 * Features:
 * <ul>
 *   <li>Integer hashCode of tokens for cache keys</li>
 *   <li>Optimistic lock-free validation (parallel computation for cache misses)</li>
 *   <li>Configurable maximum cache size with LRU eviction</li>
 *   <li>Automatic expiration checking on retrieval</li>
 *   <li>Background thread for periodic expired token cleanup</li>
 *   <li>Security event tracking for cache hits</li>
 *   <li>No external dependencies (Quarkus compatible)</li>
 * </ul>
 * <p>
 * The cache verifies token content on each hit to handle potential hash collisions.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class AccessTokenCache {

    private static final CuiLogger LOGGER = new CuiLogger(AccessTokenCache.class);

    /**
     * The maximum number of tokens to cache.
     */
    private final int maxSize;

    /**
     * The main cache storage using ConcurrentHashMap for thread safety.
     * Key: hashCode of token string
     * Value: CachedToken wrapper
     */
    private final Map<Integer, CachedToken> cache;

    /**
     * LRU tracking map protected by read-write lock.
     * This separate map tracks access order for LRU eviction.
     */
    private final LinkedHashMap<Integer, Long> lruMap;

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
     * @param config the cache configuration
     * @param securityEventCounter the security event counter for tracking cache hits
     */
    public AccessTokenCache(
            @NonNull AccessTokenCacheConfig config,
            @NonNull SecurityEventCounter securityEventCounter) {

        this.maxSize = config.getMaxSize();
        this.securityEventCounter = securityEventCounter;

        if (this.maxSize > 0) {
            // Only initialize cache structures when caching is enabled
            this.cache = new ConcurrentHashMap<>(this.maxSize);
            this.lruMap = new LinkedHashMap<>(this.maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Long> eldest) {
                    return size() > AccessTokenCache.this.maxSize;
                }
            };

            // Use provided executor or create default
            this.evictionExecutor = config.getOrCreateScheduledExecutorService();

            if (this.evictionExecutor != null) {
                this.evictionExecutor.scheduleWithFixedDelay(
                        this::evictExpiredTokens,
                        config.getEvictionIntervalSeconds(),
                        config.getEvictionIntervalSeconds(),
                        TimeUnit.SECONDS);
            }

            LOGGER.debug("AccessTokenCache initialized with maxSize=%s, evictionInterval=%ss",
                    this.maxSize, config.getEvictionIntervalSeconds());
        } else {
            // Cache disabled - no cache structures or background threads needed
            this.cache = null;
            this.lruMap = null;
            this.evictionExecutor = null;
            LOGGER.debug("AccessTokenCache disabled (maxSize=0) - no executor started");
        }
    }

    /**
     * Computes and caches a token if absent using optimistic lock-free caching.
     * <p>
     * This method first checks if a valid cached token exists. If found and still valid,
     * it returns the cached token and increments the cache hit counter. Otherwise, it
     * validates the token <strong>outside of locks</strong> and attempts to store the result.
     * <p>
     * <strong>Optimistic Caching Behavior (Issue #131/#132 Fix):</strong>
     * <p>
     * When multiple threads concurrently validate the same uncached token:
     * <ul>
     *   <li>All threads execute validation <strong>in parallel</strong> (no blocking)</li>
     *   <li>All threads attempt to store their result via {@code putIfAbsent}</li>
     *   <li>The first thread to complete storage wins - their result is cached</li>
     *   <li>Other threads discard their computed result and use the cached winner's result</li>
     *   <li>This trades potential duplicate work for elimination of blocking contention</li>
     * </ul>
     * <p>
     * Note on cache hit counting: Cache hits are only counted when a pre-existing cached
     * value is found. Threads that lose the storage race are not counted as cache hits
     * since no pre-existing value was found when they started.
     * <p>
     * Note on metrics: {@code CACHE_STORE} metrics are only recorded by the winning thread
     * that successfully stores the result. Losing threads do not record store metrics.
     *
     * @param tokenString the raw JWT token string
     * @param validationFunction the function to validate the token if not cached
     * @param performanceMonitor the monitor for recording cache metrics
     * @return the cached or newly validated access token content, never null
     * @throws TokenValidationException if the cached token is expired
     * @throws InternalCacheException if an internal cache error occurs
     */
    @SuppressWarnings("java:S3776") // owolff: 16 instead of 15 is acceptable here due to complexity of cache logic
    public AccessTokenContent computeIfAbsent(
            @NonNull String tokenString,
            @NonNull Function<String, AccessTokenContent> validationFunction,
            @NonNull TokenValidatorMonitor performanceMonitor) {

        // If cache size is 0, caching is disabled - call validation function directly without caching
        if (maxSize == 0) {
            return validationFunction.apply(tokenString);
        }

        // Create metrics ticker for cache lookup
        MetricsTicker lookupTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.CACHE_LOOKUP, performanceMonitor);

        // Generate cache key from token string
        int cacheKey = tokenString.hashCode();

        // First, try to get existing cached value
        CachedToken existing = cache.get(cacheKey);
        lookupTicker.stopAndRecord();

        if (existing != null) {
            OffsetDateTime now = OffsetDateTime.now();
            if (existing.verifyToken(tokenString) && !existing.isExpired(now)) {
                // True cache hit - valid cached token
                LOGGER.debug("Access token retrieved from cache");
                securityEventCounter.increment(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT);
                updateLru(cacheKey);
                return existing.getContent();
            } else if (existing.isExpired(now)) {
                // Token is expired - remove from cache and throw exception
                cache.remove(cacheKey, existing);
                removeLru(cacheKey);
                LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_EXPIRED::format);
                securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EXPIRED);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.TOKEN_EXPIRED,
                        "Cached token is expired"
                );
            } else {
                // Token verification failed - different token with same hash (unlikely but possible)
                cache.remove(cacheKey, existing);
                removeLru(cacheKey);
                LOGGER.debug("Cached token verification failed - hash collision detected");
            }
        }

        // Cache miss - validate token OUTSIDE of locks to avoid blocking concurrent threads
        // This optimistic approach allows multiple threads to compute in parallel, trading
        // potential duplicate work for elimination of blocking contention (issue #131/#132)
        AccessTokenContent validated = validationFunction.apply(tokenString);

        // Validation function should never return null - it should throw on failure
        if (validated == null) {
            LOGGER.error(JWTValidationLogMessages.ERROR.CACHE_VALIDATION_FUNCTION_NULL.format());
            throw new InternalCacheException(
                    "Validation function returned null - expected exception on failure"
            );
        }

        // Start metrics for cache store operation (wrapping + insertion + LRU update)
        MetricsTicker storeTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.CACHE_STORE, performanceMonitor);

        // Wrap validated token in CachedToken for storage
        CachedToken newCachedToken;
        try {
            // Get expiration time - this should always be present for valid tokens
            OffsetDateTime expirationTime = validated.getExpirationTime();

            newCachedToken = CachedToken.builder()
                    .rawToken(tokenString)
                    .content(validated)
                    .expirationTime(expirationTime)
                    .build();
        } catch (IllegalStateException e) {
            // This should not happen as TokenContent.getExpirationTime() throws
            // IllegalStateException only when expiration claim is missing,
            // which should have been caught during token validation
            storeTicker.stopAndRecord();
            LOGGER.error(e, JWTValidationLogMessages.ERROR.CACHE_TOKEN_NO_EXPIRATION.format());
            throw new InternalCacheException(
                    "Token passed validation but has no expiration time", e);
        } catch (IllegalArgumentException | SecurityException e) {
            // Handle specific runtime exceptions that could occur during token caching
            storeTicker.stopAndRecord();
            LOGGER.error(e, JWTValidationLogMessages.ERROR.CACHE_TOKEN_STORE_FAILED.format());
            throw new InternalCacheException(
                    "Failed to cache validated token", e);
        }

        // Optimistic cache insert - multiple threads may reach here with the same token
        // putIfAbsent returns null if insertion succeeded, or existing value if another thread won the race
        CachedToken raceWinner = cache.putIfAbsent(cacheKey, newCachedToken);

        if (raceWinner == null) {
            // We won the race - our token was successfully stored
            updateLru(cacheKey);
            storeTicker.stopAndRecord(); // Record metrics for successful storage
            LOGGER.debug("Token cached, current size: %s", cache.size());

            // Enforce size limit after successful insertion
            if (cache.size() > maxSize) {
                enforceSize();
            }

            return validated;
        } else {
            // Another thread won the race - their token is already cached
            // We don't record CACHE_STORE metrics when we lose the race since we didn't actually store
            // Note: storeTicker is intentionally not stopped/recorded - it will be garbage collected

            // Verify the race winner's token is still valid
            OffsetDateTime now = OffsetDateTime.now();
            if (raceWinner.verifyToken(tokenString) && !raceWinner.isExpired(now)) {
                // Race winner's token is valid - use it and discard our computation
                LOGGER.debug("Token validation race lost - using cached result from concurrent thread");
                updateLru(cacheKey);
                return raceWinner.getContent();
            } else {
                // Race winner's token is invalid/expired - replace with our freshly validated token
                cache.replace(cacheKey, raceWinner, newCachedToken);
                updateLru(cacheKey);
                LOGGER.debug("Replaced invalid cached token after validation race, current size: %s", cache.size());
                return validated;
            }
        }
    }


    /**
     * Updates LRU tracking for a cache key.
     * Only called when caching is enabled (maxSize > 0).
     */
    private void updateLru(int key) {
        lruLock.writeLock().lock();
        try {
            lruMap.put(key, System.currentTimeMillis());
        } finally {
            lruLock.writeLock().unlock();
        }
    }

    /**
     * Removes a key from LRU tracking.
     * Only called when caching is enabled (maxSize > 0).
     */
    private void removeLru(int key) {
        lruLock.writeLock().lock();
        try {
            lruMap.remove(key);
        } finally {
            lruLock.writeLock().unlock();
        }
    }

    /**
     * Enforces cache size limit using LRU eviction.
     * Evicts oldest 10% of entries when cache is full to reduce eviction frequency.
     */
    private void enforceSize() {
        if (cache != null && cache.size() >= maxSize) {
            lruLock.writeLock().lock();
            try {
                if (!lruMap.isEmpty()) {
                    // Calculate batch size: evict 10% or at least 1 entry
                    int batchSize = Math.max(1, maxSize / 10);
                    int evicted = 0;

                    var iterator = lruMap.entrySet().iterator();
                    while (iterator.hasNext() && evicted < batchSize) {
                        Integer keyToEvict = iterator.next().getKey();
                        cache.remove(keyToEvict);
                        iterator.remove();
                        evicted++;
                    }

                    LOGGER.debug("Evicted %s oldest tokens from cache due to size limit", evicted);
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
            List<Integer> expiredKeys = new ArrayList<>();

            // Phase 1: Collect expired keys (no locking needed, ConcurrentHashMap is thread-safe for iteration)
            for (Map.Entry<Integer, CachedToken> entry : cache.entrySet()) {
                if (entry.getValue().isExpired(now)) {
                    expiredKeys.add(entry.getKey());
                }
            }

            if (!expiredKeys.isEmpty()) {
                // Phase 2: Batch remove from cache (thread-safe operations)
                expiredKeys.forEach(cache::remove);

                // Phase 3: Batch remove from LRU with single lock acquisition
                lruLock.writeLock().lock();
                try {
                    expiredKeys.forEach(lruMap::remove);
                } finally {
                    lruLock.writeLock().unlock();
                }

                LOGGER.debug("Evicted %s expired tokens from cache", expiredKeys.size());
            }
        } catch (IllegalStateException | SecurityException e) {
            LOGGER.error(e, JWTValidationLogMessages.ERROR.CACHE_EVICTION_FAILED.format());
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
     * Package-private for testing purposes.
     *
     * @return the number of tokens currently cached
     */
    int size() {
        return cache != null ? cache.size() : 0;
    }


}