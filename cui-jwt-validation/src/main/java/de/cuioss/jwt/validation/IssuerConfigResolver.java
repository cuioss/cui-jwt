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
import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles the resolution and caching of issuer configurations in a thread-safe manner.
 * This class encapsulates the complex concurrency logic for managing issuer configs,
 * reducing the cyclomatic complexity of TokenValidator.
 * <p>
 * The resolver implements a two-phase approach:
 * <ol>
 *   <li>Lazy initialization phase: Configs are loaded and cached on-demand</li>
 *   <li>Optimized read-only phase: After all configs are loaded, the cache is optimized for reads</li>
 * </ol>
 * <p>
 * Thread Safety:
 * This class is thread-safe. Multiple threads can concurrently resolve issuer configs
 * with minimal synchronization overhead.
 */
public class IssuerConfigResolver {

    private static final CuiLogger LOGGER = new CuiLogger(IssuerConfigResolver.class);

    /**
     * Queue of pending issuer configurations to be processed.
     */
    private final ConcurrentLinkedQueue<IssuerConfig> pendingConfigs;

    /**
     * Cache of resolved issuer configurations.
     * Uses AtomicReference to allow atomic transition to immutable map.
     */
    private final AtomicReference<Map<String, IssuerConfig>> configCache;

    /**
     * Flag indicating whether all configs have been processed and cache is optimized.
     */
    private volatile boolean isOptimized = false;
    
    /**
     * Flag to ensure only one thread performs optimization.
     */
    private final AtomicBoolean optimizationInProgress = new AtomicBoolean(false);

    /**
     * Security event counter for tracking validation events.
     */
    private final SecurityEventCounter securityEventCounter;

    /**
     * Count of enabled configurations that were provided during construction.
     */
    private final int enabledConfigCount;

    /**
     * Creates a new resolver with the given configurations.
     * Only enabled configurations are processed and cached.
     *
     * @param issuerConfigs array of issuer configurations to manage
     * @param securityEventCounter counter for security events
     */
    IssuerConfigResolver(@NonNull IssuerConfig[] issuerConfigs,
            @NonNull SecurityEventCounter securityEventCounter) {
        this.securityEventCounter = securityEventCounter;
        this.configCache = new AtomicReference<>(new ConcurrentHashMap<>());

        // Initialize collections
        this.pendingConfigs = new ConcurrentLinkedQueue<>();

        // Add enabled configurations to pending queue for lazy processing
        int enabledCount = 0;
        for (IssuerConfig issuerConfig : issuerConfigs) {
            // Only process enabled issuers (constructor filtering as per I1 requirements)
            if (issuerConfig.isEnabled()) {
                // Initialize the JwksLoader with the SecurityEventCounter
                issuerConfig.initSecurityEventCounter(securityEventCounter);

                // Add to pending queue for lazy health checking and caching
                pendingConfigs.add(issuerConfig);
                enabledCount++;
                LOGGER.debug("Added enabled issuer configuration to pending queue");
            } else {
                LOGGER.info(JWTValidationLogMessages.INFO.ISSUER_CONFIG_SKIPPED.format(issuerConfig));
            }
        }

        this.enabledConfigCount = enabledCount;

        LOGGER.debug("IssuerConfigResolver initialized with %s enabled configurations (%s total)", enabledCount, issuerConfigs.length);
    }

    /**
     * Resolves the issuer configuration for the given issuer identifier.
     * <p>
     * This method provides:
     * <ol>
     *   <li>Lock-free fast path for cached configs</li>
     *   <li>Lock-free lazy initialization using ConcurrentHashMap</li>
     *   <li>Automatic cache optimization after all configs are processed</li>
     * </ol>
     *
     * @param issuer the issuer identifier to resolve
     * @return the resolved issuer configuration
     * @throws TokenValidationException if no healthy configuration is found
     */
    IssuerConfig resolveConfig(@NonNull String issuer) {
        // Fast path - check cache first (lock-free)
        Map<String, IssuerConfig> cache = configCache.get();
        IssuerConfig cachedConfig = cache.get(issuer);
        if (cachedConfig != null) {
            LOGGER.debug("Using cached issuer config for: %s", issuer);
            return cachedConfig;
        }

        // If already optimized and not found, it doesn't exist
        if (isOptimized) {
            handleIssuerNotFound(issuer);
        }

        // Slow path - need to process pending configs
        return resolveFromPending(issuer);
    }


    /**
     * Resolves configuration from pending queue with minimal synchronization.
     */
    private IssuerConfig resolveFromPending(@NonNull String issuer) {
        // Try lock-free processing first
        IssuerConfig result = tryLockFreeResolve(issuer);
        if (result != null) {
            return result;
        }
        
        // If still not found and not optimized, use synchronized fallback
        if (!isOptimized) {
            synchronized (this) {
                // Double-check after acquiring lock
                result = configCache.get().get(issuer);
                if (result != null) {
                    return result;
                }
                
                // Process all pending configs while we have the lock
                processAllPendingConfigs();
                
                // Try one more time
                result = configCache.get().get(issuer);
                if (result != null) {
                    return result;
                }
            }
        }
        
        // Not found
        handleIssuerNotFound(issuer);
        throw new IllegalStateException("This line should not be reached");
    }
    
    /**
     * Attempts lock-free resolution of a specific issuer.
     */
    private IssuerConfig tryLockFreeResolve(String targetIssuer) {
        Map<String, IssuerConfig> currentCache = configCache.get();
        if (!(currentCache instanceof ConcurrentHashMap<String, IssuerConfig> concurrentMap)) {
            return null; // Already optimized
        }

        
        // Look for the specific issuer in pending configs
        for (IssuerConfig issuerConfig : pendingConfigs) {
            if (targetIssuer.equals(issuerConfig.getIssuerIdentifier())) {
                // Check if healthy
                if (LoaderStatus.OK.equals(issuerConfig.isHealthy())) {
                    // Try to add to cache
                    IssuerConfig existing = concurrentMap.putIfAbsent(issuerConfig.getIssuerIdentifier(), issuerConfig);
                    if (existing != null) {
                        return existing; // Another thread beat us
                    }
                    // Remove from pending
                    pendingConfigs.remove(issuerConfig);
                    LOGGER.debug("Found healthy issuer config for: %s", targetIssuer);
                    return issuerConfig;
                }
            }
        }
        return null;
    }
    
    /**
     * Processes all pending configs - must be called while holding lock.
     */
    private void processAllPendingConfigs() {
        Map<String, IssuerConfig> currentCache = configCache.get();
        if (!(currentCache instanceof ConcurrentHashMap<String, IssuerConfig> concurrentMap)) {
            return; // Already optimized
        }
        
        Iterator<IssuerConfig> iterator = pendingConfigs.iterator();
        while (iterator.hasNext()) {
            IssuerConfig issuerConfig = iterator.next();
            if (LoaderStatus.OK.equals(issuerConfig.isHealthy())) {
                concurrentMap.put(issuerConfig.getIssuerIdentifier(), issuerConfig);
                iterator.remove();
                LOGGER.debug("Cached issuer config for: %s", issuerConfig.getIssuerIdentifier());
            } else {
                LOGGER.warn(JWTValidationLogMessages.WARN.ISSUER_CONFIG_UNHEALTHY.format(issuerConfig.getIssuerIdentifier()));
            }
        }
        
        // Optimize if all processed
        if (pendingConfigs.isEmpty() && !isOptimized) {
            optimizeForReadOnlyAccess();
            isOptimized = true;
            LOGGER.debug("Issuer config cache optimized for read-only access");
        }
    }

    /**
     * Optimizes the cache for read-only access by converting to an immutable map.
     * This improves performance for the common case where all configs are loaded.
     * This method is always called from within a synchronized block.
     */
    private void optimizeForReadOnlyAccess() {
        if (pendingConfigs.isEmpty()) {
            // Convert to immutable map for optimal read performance
            Map<String, IssuerConfig> currentMap = configCache.get();
            if (currentMap instanceof ConcurrentHashMap) {
                configCache.set(Map.copyOf(currentMap));
                LOGGER.debug("Optimized issuer config cache for read-only access with {} entries", currentMap.size());
            }
        }
    }

    /**
     * Handles the case where no issuer configuration is found.
     *
     * @param issuer the issuer that wasn't found
     * @throws TokenValidationException always
     */
    private void handleIssuerNotFound(String issuer) {
        LOGGER.warn(JWTValidationLogMessages.WARN.NO_ISSUER_CONFIG.format(issuer));
        securityEventCounter.increment(SecurityEventCounter.EventType.NO_ISSUER_CONFIG);
        throw new TokenValidationException(
                SecurityEventCounter.EventType.NO_ISSUER_CONFIG,
                "No healthy issuer configuration found for issuer: " + issuer
        );
    }

    /**
     * Gets the total count of enabled configurations that were processed.
     *
     * @return the total number of enabled configurations
     */
    int getEnabledConfigCount() {
        return enabledConfigCount;
    }
}