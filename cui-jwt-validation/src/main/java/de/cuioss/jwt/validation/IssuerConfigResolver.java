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
 * Thread-safe resolver for issuer configurations with dual-cache optimization.
 * <p>
 * Uses a mutable ConcurrentHashMap during initialization, then switches to an 
 * immutable Map for lock-free reads after all configs are processed.
 * <p>
 * The volatile immutableCache field serves as both cache and optimization state indicator.
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
        for (IssuerConfig config : issuerConfigs) {
            if (config.isEnabled()) {
                config.initSecurityEventCounter(securityEventCounter);
                pendingConfigs.add(config);
                enabledCount++;
                LOGGER.debug("Added enabled issuer configuration to pending queue");
            } else {
                LOGGER.info(JWTValidationLogMessages.INFO.ISSUER_CONFIG_SKIPPED.format(config));
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
        Optional<IssuerConfig> cachedFromImmutable = tryGetFromImmutableCache(issuer);
        if (cachedFromImmutable.isPresent()) {
            return cachedFromImmutable.get();
        }
        
        // Fallback to mutable cache during initialization
        IssuerConfig cachedFromMutable = mutableCache.get(issuer);
        if (cachedFromMutable != null) {
            LOGGER.debug("Using cached issuer config from mutable cache for: %s", issuer);
            return cachedFromMutable;
        }

        // Slow path - need to process pending configs
        return slowPathResolution(issuer);
    }

    /**
     * Attempts to retrieve configuration from the immutable cache.
     * 
     * @param issuer the issuer identifier to look up
     * @return Optional with cached config if found, empty if cache not optimized or issuer not found
     * @throws TokenValidationException if cache is optimized but issuer not found
     */
    private Optional<IssuerConfig> tryGetFromImmutableCache(String issuer) {
        if (immutableCache != null) {
            IssuerConfig cached = immutableCache.get(issuer);
            if (cached != null) {
                LOGGER.debug("Using cached issuer config from immutable cache for: %s", issuer);
                return Optional.of(cached);
            }
            // If optimized and not found, it doesn't exist
            handleIssuerNotFound(issuer);
        }
        return Optional.empty();
    }


    /**
     * Handles slow path resolution when config not found in cache.
     * Processes all pending configs synchronously for simplicity and correctness.
     *
     * @param issuer the issuer identifier to resolve
     * @return the resolved issuer configuration
     * @throws TokenValidationException if no healthy configuration is found
     */
    private IssuerConfig slowPathResolution(@NonNull String issuer) {
        // If not optimized yet, process all pending configs
        if (immutableCache == null) {
            synchronized (this) {
                // Double-check after acquiring lock
                IssuerConfig result = mutableCache.get(issuer);
                if (result != null) {
                    LOGGER.debug("Found issuer config in mutable cache during synchronized check: %s", issuer);
                    return result;
                }

                // Process all pending configs in one go
                processAllPendingConfigs();

                // Try again after processing
                result = mutableCache.get(issuer);
                if (result != null) {
                    LOGGER.debug("Found issuer config after processing pending configs: %s", issuer);
                    return result;
                }
            }
        }

        // Not found
        handleIssuerNotFound(issuer);
        throw new IllegalStateException("handleIssuerNotFound should have thrown an exception");
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