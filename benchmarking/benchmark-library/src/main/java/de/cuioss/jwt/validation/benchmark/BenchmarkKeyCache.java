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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler.IssuerKeyMaterial;
import de.cuioss.tools.logging.CuiLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-generates and caches RSA key pairs for benchmarking to avoid key generation
 * during benchmark measurements.
 * <p>
 * This class ensures that expensive RSA key generation happens during class loading,
 * not during benchmark execution, preventing p99 latency spikes caused by setup costs.
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
public final class BenchmarkKeyCache {

    private static final CuiLogger LOGGER = new CuiLogger(BenchmarkKeyCache.class);

    /**
     * Cache of pre-generated issuer key materials by count
     */
    private static final Map<Integer, IssuerKeyMaterial[]> ISSUER_CACHE = new ConcurrentHashMap<>();

    /**
     * Maximum number of issuers to pre-generate
     */
    private static final int MAX_CACHED_ISSUERS = 10;

    static {
        // Pre-generate key materials for common issuer counts
        long startTime = System.currentTimeMillis();
        LOGGER.info("BenchmarkKeyCache: Starting RSA key pre-generation...");

        for (int count = 1; count <= MAX_CACHED_ISSUERS; count++) {
            ISSUER_CACHE.put(count, InMemoryKeyMaterialHandler.createMultipleIssuers(count));
        }

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info("BenchmarkKeyCache: Pre-generated keys for %s issuer configurations in %s ms",
                MAX_CACHED_ISSUERS, duration);
    }

    /**
     * Private constructor to prevent instantiation
     */
    private BenchmarkKeyCache() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Gets pre-generated issuer key materials for the specified count.
     * If the count exceeds the pre-generated cache, generates new keys (with a warning).
     * 
     * @param count The number of issuers needed
     * @return Array of pre-generated IssuerKeyMaterial instances
     */
    public static IssuerKeyMaterial[] getPreGeneratedIssuers(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Issuer count must be positive");
        }

        IssuerKeyMaterial[] cached = ISSUER_CACHE.get(count);
        if (cached != null) {
            // Return the cached array directly - no cloning needed as IssuerKeyMaterial is immutable
            return cached;
        }

        // Generate on demand if not cached (this should be rare in benchmarks)
        LOGGER.warn("BenchmarkKeyCache miss for count=%s. Generating keys during benchmark!", count);
        IssuerKeyMaterial[] generated = InMemoryKeyMaterialHandler.createMultipleIssuers(count);
        ISSUER_CACHE.put(count, generated);
        return generated;
    }


    /**
     * Forces initialization of the key cache.
     * This method can be called explicitly to ensure keys are generated
     * before any benchmarks start.
     */
    public static void initialize() {
        // The static initializer will run when this method is called
        LOGGER.info("BenchmarkKeyCache: Initialized with %s configurations", ISSUER_CACHE.size());
    }
}