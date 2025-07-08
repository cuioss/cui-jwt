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
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles the resolution and caching of issuer configurations in a thread-safe manner.
 * This class encapsulates the issuer config management logic, reducing the cyclomatic
 * complexity of TokenValidator.
 * <p>
 * The resolver implements a dual-cache approach to eliminate race conditions:
 * <ol>
 *   <li><strong>Mutable cache:</strong> A ConcurrentHashMap used during initialization
 *       for thread-safe writes while configs are being resolved from the pending queue</li>
 *   <li><strong>Immutable cache:</strong> A read-only Map created after all configs are
 *       processed, providing optimal performance for the fast path</li>
 * </ol>
 * <p>
 * <strong>Performance Characteristics:</strong>
 * <ul>
 *   <li><strong>Fast path:</strong> Direct lookup in the appropriate cache (lock-free)</li>
 *   <li><strong>Initialization:</strong> Uses mutable cache with ConcurrentHashMap semantics</li>
 *   <li><strong>Post-optimization:</strong> Uses immutable cache with immediate rejection for unknown issuers</li>
 * </ul>
 * <p>
 * <strong>Thread Safety:</strong>
 * This class is thread-safe using a dual-cache approach with proper memory ordering:
 * <ul>
 *   <li><strong>Mutable cache:</strong> ConcurrentHashMap provides thread-safe operations during initialization</li>
 *   <li><strong>Immutable cache:</strong> Published via volatile write, ensuring safe publication without locks</li>
 *   <li><strong>State determination:</strong> Null check on immutable cache determines optimization state</li>
 *   <li><strong>No race conditions:</strong> Threads never attempt to modify the immutable cache</li>
 * </ul>
 * The volatile immutableCache field serves both as the optimized cache and the optimization
 * state indicator, eliminating the need for a separate boolean flag.
 */
@EqualsAndHashCode
@ToString(of = {"mutableCache", "immutableCache", "pendingConfigs"})
public class IssuerConfigResolver {

    private static final CuiLogger LOGGER = new CuiLogger(IssuerConfigResolver.class);

    /**
     * Queue of pending issuer configurations to be processed.
     */
    private final ConcurrentLinkedQueue<IssuerConfig> pendingConfigs;

    /**
     * Mutable cache used during initialization phase.
     * This ConcurrentHashMap allows thread-safe writes while configs are being resolved.
     */
    private final ConcurrentHashMap<String, IssuerConfig> mutableCache;
    
    /**
     * Immutable cache used after optimization for optimal read performance.
     * This is set once when all configs have been processed and remains immutable thereafter.
     * Null value indicates the cache is still in initialization phase; non-null indicates optimization is complete.
     */
    private volatile Map<String, IssuerConfig> immutableCache;

    /**
     * Security event counter for tracking validation events.
     */
    private final SecurityEventCounter securityEventCounter;

    /**
     * Creates a new resolver with the given configurations.
     * Only enabled configurations are added to the pending queue for lazy processing.
     * Disabled configurations are logged and ignored.
     *
     * @param issuerConfigs array of issuer configurations to manage, must not be null
     * @param securityEventCounter counter for security events, must not be null
     */
    IssuerConfigResolver(@NonNull IssuerConfig[] issuerConfigs,
            @NonNull SecurityEventCounter securityEventCounter) {
        this.securityEventCounter = securityEventCounter;
        this.mutableCache = new ConcurrentHashMap<>();
        this.immutableCache = null; // Will be set after optimization

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

        LOGGER.debug("IssuerConfigResolver initialized with %s enabled configurations (%s total)", enabledCount, issuerConfigs.length);
    }

    /**
     * Resolves the issuer configuration for the given issuer identifier.
     * <p>
     * This method implements a three-tier resolution strategy:
     * <ol>
     *   <li><strong>Fast path:</strong> Direct cache lookup (lock-free, constant time)</li>
     *   <li><strong>Early exit:</strong> Immediate failure if cache is optimized and issuer not found</li>
     *   <li><strong>Initialization path:</strong> Synchronized batch processing of all pending configs</li>
     * </ol>
     * <p>
     * <strong>Performance:</strong> After warm-up, all calls use the lock-free fast path.
     * During initialization, synchronization ensures consistent batch processing.
     *
     * @param issuer the issuer identifier to resolve, must not be null
     * @return the resolved issuer configuration, never null
     * @throws TokenValidationException if no healthy configuration is found for the issuer
     */
    IssuerConfig resolveConfig(@NonNull String issuer) {
        // Fast path - check immutable cache first if available (lock-free)
        Map<String, IssuerConfig> optimizedCache = immutableCache;
        if (optimizedCache != null) {
            // Use immutable cache for optimal performance
            IssuerConfig cachedConfig = optimizedCache.get(issuer);
            if (cachedConfig != null) {
                LOGGER.debug("Using cached issuer config from immutable cache for: %s", issuer);
                return cachedConfig;
            }
            // If optimized and not found, it doesn't exist
            handleIssuerNotFound(issuer);
        }
        
        // Fallback to mutable cache during initialization
        IssuerConfig cachedConfig = mutableCache.get(issuer);
        if (cachedConfig != null) {
            LOGGER.debug("Using cached issuer config from mutable cache for: %s", issuer);
            return cachedConfig;
        }

        // Slow path - need to process pending configs
        return resolveFromPending(issuer);
    }

    /**
     * Finds a matching issuer configuration in the pending queue.
     * <p>
     * This method searches the pending queue for a configuration that matches
     * the given issuer identifier. It does not modify the queue or perform
     * health checks.
     *
     * @param issuer the issuer identifier to match
     * @return the matching configuration, or null if not found
     */
    private IssuerConfig findMatchingPendingConfig(String issuer) {
        for (IssuerConfig config : pendingConfigs) {
            // For configs with static issuer identifier
            if (issuer.equals(config.issuerIdentifier)) {
                return config;
            }

            // For configs with dynamic issuer identifier (from JwksLoader)
            if (LoaderStatus.OK.equals(config.jwksLoader.isHealthy())) {
                Optional<String> jwksLoaderIssuer = config.jwksLoader.getIssuerIdentifier();
                if (jwksLoaderIssuer.isPresent() && issuer.equals(jwksLoaderIssuer.get())) {
                    return config;
                }
            }
        }
        return null;
    }

    /**
     * Resolves configuration from pending queue using optimized processing.
     * <p>
     * This method implements the "slow path" for issuer resolution when the config
     * is not found in the cache and the system is not yet optimized. It uses
     * a non-blocking approach to minimize synchronization overhead.
     *
     * @param issuer the issuer identifier to resolve
     * @return the resolved issuer configuration
     * @throws TokenValidationException if no healthy configuration is found
     */
    private IssuerConfig resolveFromPending(@NonNull String issuer) {
        // If still not found and not optimized, use optimized processing
        if (immutableCache == null) {
            // First, try to find a matching config in the pending queue
            IssuerConfig matchingConfig = findMatchingPendingConfig(issuer);

            if (matchingConfig != null) {
                // Process this specific config and add to cache if healthy
                if (LoaderStatus.OK.equals(matchingConfig.isHealthy())) {
                    // Use atomic operation to add to mutable cache if not present
                    IssuerConfig existing = mutableCache.putIfAbsent(issuer, matchingConfig);
                    if (existing != null) {
                        // Another thread already added it
                        LOGGER.debug("Another thread already cached issuer config for: %s", issuer);
                        return existing;
                    }

                    // Remove from pending queue
                    pendingConfigs.remove(matchingConfig);
                    LOGGER.debug("Cached issuer config for: %s", issuer);

                    // Check if we should optimize
                    checkAndOptimize();

                    return matchingConfig;
                } else {
                    LOGGER.warn(JWTValidationLogMessages.WARN.ISSUER_CONFIG_UNHEALTHY.format(issuer));
                }
            }

            // If we couldn't find or process a matching config, try synchronized fallback
            synchronized (this) {
                // Double-check after acquiring lock
                var result = mutableCache.get(issuer);
                if (result != null) {
                    LOGGER.debug("Double-checked cached issuer config for: %s", issuer);
                    return result;
                }

                // Process all remaining configs while we have the lock
                // This is a fallback for when we couldn't find a matching config
                processAllPendingConfigs();

                // Try one more time
                result = mutableCache.get(issuer);
                if (result != null) {
                    LOGGER.debug("Final try on cached issuer config for: %s", issuer);
                    return result;
                }
            }
        }

        // Not found
        handleIssuerNotFound(issuer);
        throw new IllegalStateException("This line should not be reached");
    }

    /**
     * Processes all pending configurations in a batch operation.
     * <p>
     * This method must be called while holding the synchronization lock.
     * It processes all pending configs at once, checking their health status,
     * caching healthy ones, and removing them from the pending queue.
     * <p>
     * When all configs are processed (pending queue is empty), the cache is
     * optimized by converting from ConcurrentHashMap to an immutable Map.
     */
    private void processAllPendingConfigs() {
        Iterator<IssuerConfig> iterator = pendingConfigs.iterator();
        while (iterator.hasNext()) {
            IssuerConfig issuerConfig = iterator.next();
            if (LoaderStatus.OK.equals(issuerConfig.isHealthy())) {
                mutableCache.putIfAbsent(issuerConfig.getIssuerIdentifier(), issuerConfig);
                iterator.remove();
                LOGGER.debug("Cached issuer config for: %s", issuerConfig.getIssuerIdentifier());
            } else {
                LOGGER.warn(JWTValidationLogMessages.WARN.ISSUER_CONFIG_UNHEALTHY.format(issuerConfig.getIssuerIdentifier()));
            }
        }

        // Optimize if all processed
        checkAndOptimize();
    }

    /**
     * Checks if all pending configurations have been processed and optimizes the cache if needed.
     * <p>
     * This method checks if the pending queue is empty and, if so, creates an immutable
     * copy of the mutable cache for optimal read performance. The immutable cache is set
     * atomically via volatile write, ensuring thread-safe publication without requiring
     * additional synchronization for reads.
     * <p>
     * The mutable cache is kept unchanged to avoid any race conditions during the
     * transition period. Optimization state is determined by whether immutableCache is null.
     */
    private void checkAndOptimize() {
        if (pendingConfigs.isEmpty() && immutableCache == null) {
            synchronized (this) {
                if (pendingConfigs.isEmpty() && immutableCache == null) {
                    // Create immutable copy from mutable cache
                    Map<String, IssuerConfig> optimizedCache = Map.copyOf(mutableCache);
                    LOGGER.debug("Created immutable cache for read-only access with %s entries", optimizedCache.size());
                    
                    // Set immutable cache via volatile write for thread-safe publication
                    immutableCache = optimizedCache;
                    LOGGER.debug("Issuer config cache optimized for read-only access");
                }
            }
        }
    }

    /**
     * Handles the case where no issuer configuration is found.
     * <p>
     * This method logs a warning, increments the security event counter,
     * and throws a TokenValidationException with appropriate details.
     *
     * @param issuer the issuer identifier that wasn't found
     * @throws TokenValidationException always thrown with NO_ISSUER_CONFIG event type
     */
    private void handleIssuerNotFound(String issuer) {
        LOGGER.warn(JWTValidationLogMessages.WARN.NO_ISSUER_CONFIG.format(issuer));
        securityEventCounter.increment(SecurityEventCounter.EventType.NO_ISSUER_CONFIG);
        throw new TokenValidationException(
                SecurityEventCounter.EventType.NO_ISSUER_CONFIG,
                "No healthy issuer configuration found for issuer: " + issuer
        );
    }
}