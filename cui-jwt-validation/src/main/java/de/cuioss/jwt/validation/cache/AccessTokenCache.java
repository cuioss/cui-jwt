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
import de.cuioss.jwt.validation.metrics.NoOpMetricsTicker;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
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
     * <p>
     * Note on cache hit counting: Cache hits are only counted when a pre-existing cached
     * value is found. When multiple threads concurrently request the same uncached token,
     * only the first thread performs validation while others wait for the result. These
     * waiting threads are not counted as cache hits since no pre-existing value was found.
     *
     * @param issuer the issuer of the token (used in cache key)
     * @param tokenString the raw JWT token string
     * @param validationFunction the function to validate the token if not cached
     * @param performanceMonitor the monitor for recording cache metrics (may be null)
     * @return the cached or newly validated access token content, never null
     * @throws TokenValidationException if the cached token is expired
     * @throws InternalCacheException if an internal cache error occurs
     */
    public AccessTokenContent computeIfAbsent(
            @NonNull String issuer,
            @NonNull String tokenString,
            @NonNull Function<String, AccessTokenContent> validationFunction,
            TokenValidatorMonitor performanceMonitor) {

        // If cache size is 0, caching is disabled - call validation function directly without caching
        if (maxSize == 0) {
            return validationFunction.apply(tokenString);
        }

        // Create metrics ticker for cache lookup
        MetricsTicker lookupTicker = performanceMonitor != null ? 
                MeasurementType.CACHE_LOOKUP.createTicker(performanceMonitor, true) : 
                NoOpMetricsTicker.INSTANCE;

        // Generate cache key
        String cacheKey = generateCacheKey(issuer, tokenString);
        if (cacheKey == null) {
            // Failed to generate key, skip caching
            return validationFunction.apply(tokenString);
        }

        // First, try to get existing cached value
        lookupTicker.startRecording();
        CachedToken existing = cache.get(cacheKey);
        lookupTicker.stopAndRecord();
        
        if (existing != null) {
            OffsetDateTime now = OffsetDateTime.now();
            if (existing.verifyToken(tokenString) && !existing.isExpired(now)) {
                // True cache hit - valid cached token
                LOGGER.debug(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CACHE_HIT::format);
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

        // Create metrics ticker for cache store
        MetricsTicker storeTicker = performanceMonitor != null ? 
                MeasurementType.CACHE_STORE.createTicker(performanceMonitor, true) : 
                NoOpMetricsTicker.INSTANCE;

        // Cache miss - use computeIfAbsent to ensure only one thread validates
        CachedToken newCached = cache.computeIfAbsent(cacheKey, key -> {
            // Validate the token (only happens once for concurrent requests)
            AccessTokenContent validated = validationFunction.apply(tokenString);
            if (validated != null) {
                storeTicker.startRecording();
                try {
                    // Get expiration time - this should always be present for valid tokens
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
                    // This should not happen as TokenContent.getExpirationTime() throws
                    // IllegalStateException only when expiration claim is missing,
                    // which should have been caught during token validation
                    LOGGER.error(e, "Token passed validation but has no expiration time - this indicates a validation bug");
                    throw new InternalCacheException(
                            "Token passed validation but has no expiration time", e);
                } catch (Exception e) {
                    // Any other unexpected exception
                    LOGGER.error(e, "Unexpected error while caching token");
                    throw new InternalCacheException(
                            "Failed to cache validated token", e);
                } finally {
                    storeTicker.stopAndRecord();
                }
            }
            // Validation returned null - don't cache
            return null;
        });

        // Return the content if we got a valid cached token
        if (newCached != null) {
            return newCached.getContent();
        }
        
        // If we reach here, the validation function returned null.
        // This should not happen as validation functions should throw on failure.
        LOGGER.error("Validation function returned null instead of throwing exception");
        throw new InternalCacheException(
                "Validation function returned null - expected exception on failure"
        );
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