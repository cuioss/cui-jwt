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
        // Check cache first for performance (lock-free fast path)
        IssuerConfig cachedConfig = configCache.get().get(issuer);
        if (cachedConfig != null) {
            LOGGER.debug("Using cached issuer config for: %s", issuer);
            return cachedConfig;
        }

        // Lock-free warm-up: try to populate cache
        if (!isOptimized) {
            tryLockFreeWarmup();

            // Check cache again after warmup
            cachedConfig = configCache.get().get(issuer);
            if (cachedConfig != null) {
                LOGGER.debug("Using cached issuer config after warmup for: %s", issuer);
                return cachedConfig;
            }
        }

        // No healthy matching issuer configuration found
        handleIssuerNotFound(issuer);
        throw new IllegalStateException("This line should not be reached");
    }


    /**
     * Attempts lock-free warmup by processing pending configs.
     * Uses ConcurrentHashMap operations to avoid synchronization.
     */
    private void tryLockFreeWarmup() {
        Map<String, IssuerConfig> currentCache = configCache.get();
        if (!(currentCache instanceof ConcurrentHashMap<String, IssuerConfig> concurrentMap)) {
            return; // Already optimized
        }

        // Process all pending configs in a lock-free manner
        Iterator<IssuerConfig> iterator = pendingConfigs.iterator();
        while (iterator.hasNext()) {
            IssuerConfig issuerConfig = iterator.next();

            // Check if the issuer config is healthy
            if (LoaderStatus.OK.equals(issuerConfig.isHealthy())) {
                // Use putIfAbsent for lock-free insertion
                IssuerConfig existing = concurrentMap.putIfAbsent(issuerConfig.getIssuerIdentifier(), issuerConfig);
                if (existing == null) {
                    // Successfully inserted, remove from pending queue
                    iterator.remove();
                    LOGGER.debug("Found healthy issuer config, cached and removed from queue for: %s", issuerConfig.getIssuerIdentifier());
                }
            } else {
                LOGGER.warn(JWTValidationLogMessages.WARN.ISSUER_CONFIG_UNHEALTHY.format(issuerConfig.getIssuerIdentifier()));
            }
        }

        // Check if we can optimize - only one thread will succeed
        if (pendingConfigs.isEmpty() && !isOptimized) {
            synchronized (this) {
                // Double-check pattern
                if (pendingConfigs.isEmpty() && !isOptimized) {
                    optimizeForReadOnlyAccess();
                    isOptimized = true;
                    LOGGER.debug("Issuer config cache optimized for read-only access");
                }
            }
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