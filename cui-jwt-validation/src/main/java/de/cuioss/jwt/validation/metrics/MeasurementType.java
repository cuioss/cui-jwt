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
package de.cuioss.jwt.validation.metrics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum defining all measurement types for JWT validation pipeline steps.
 * <p>
 * Each measurement type represents a specific phase of the JWT validation process
 * that can be independently monitored and analyzed for metrics optimization.
 * <p>
 * The measurements follow the validation pipeline order:
 * <ol>
 *   <li>{@link #TOKEN_PARSING} - Basic JWT structure validation</li>
 *   <li>{@link #HEADER_VALIDATION} - Header claims validation</li>
 *   <li>{@link #SIGNATURE_VALIDATION} - Cryptographic verification (typically most expensive)</li>
 *   <li>{@link #CLAIMS_VALIDATION} - Token claims validation</li>
 *   <li>{@link #JWKS_OPERATIONS} - Key loading and caching</li>
 *   <li>{@link #COMPLETE_VALIDATION} - End-to-end validation including errors</li>
 * </ol>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor
@Getter
public enum MeasurementType {
    /**
     * Complete token validation from start to finish.
     * <p>
     * Includes all pipeline steps, error handling, and represents the total
     * time taken for a JWT validation request from the perspective of the caller.
     * This is the most important metric for end-to-end metrics analysis.
     */
    COMPLETE_VALIDATION("Complete JWT validation"),

    /**
     * JWT token parsing and structure validation.
     * <p>
     * Measures time to decode Base64URL segments, parse JSON structures,
     * and validate basic JWT format. This step typically has minimal overhead
     * but can indicate issues with malformed tokens or JSON parsing metrics.
     */
    TOKEN_PARSING("JWT token parsing"),

    /**
     * JWT header validation.
     * <p>
     * Measures time to validate header claims and structure, including
     * algorithm verification, key ID extraction, and header claim validation.
     * Performance issues here may indicate problems with header processing logic.
     */
    HEADER_VALIDATION("JWT header validation"),

    /**
     * JWT signature verification.
     * <p>
     * Measures time for cryptographic signature validation using RSA/ECDSA algorithms.
     * This is typically the most expensive operation in JWT validation, often consuming
     * 90%+ of total validation time. Performance optimization efforts should focus here.
     */
    SIGNATURE_VALIDATION("JWT signature validation"),

    /**
     * JWT claims validation.
     * <p>
     * Measures time to validate token claims including expiration (exp),
     * not-before (nbf), audience (aud), issuer (iss), and other standard/custom claims.
     * This step is typically fast but can indicate issues with claim processing logic.
     */
    CLAIMS_VALIDATION("JWT claims validation"),

    /**
     * JWKS key retrieval and processing operations.
     * <p>
     * Measures time to fetch JWKS keys from remote endpoints, parse key material,
     * and perform key caching operations. High values here may indicate network
     * latency issues, JWKS endpoint problems, or key processing inefficiencies.
     */
    JWKS_OPERATIONS("JWKS key operations"),

    /**
     * Token format validation.
     * <p>
     * Measures time to validate basic token format including empty/blank checks
     * before any parsing occurs. This is typically very fast but helps identify
     * the overhead of initial validation checks.
     */
    TOKEN_FORMAT_CHECK("Token format validation"),

    /**
     * Issuer extraction from decoded JWT.
     * <p>
     * Measures time to extract and validate the presence of the issuer (iss) claim
     * from the decoded JWT. This includes claim lookup and validation that the
     * issuer is present.
     */
    ISSUER_EXTRACTION("Issuer extraction"),

    /**
     * Issuer configuration resolution.
     * <p>
     * Measures time to look up the appropriate issuer configuration based on the
     * issuer claim. This includes configuration cache lookups and health checks.
     * High values may indicate configuration lookup inefficiencies or cache misses.
     */
    ISSUER_CONFIG_RESOLUTION("Issuer config resolution"),

    /**
     * Token object building.
     * <p>
     * Measures time to construct the typed token objects (AccessTokenContent,
     * IdTokenContent) from the validated JWT claims. This includes claim extraction,
     * type conversion, and object instantiation.
     */
    TOKEN_BUILDING("Token building"),

    /**
     * Cache lookup operation.
     * <p>
     * Measures time to look up a token in the cache, including key generation
     * and hash computation. This metric helps identify cache lookup overhead.
     */
    CACHE_LOOKUP("Cache lookup operation"),

    /**
     * Cache store operation.
     * <p>
     * Measures time to store a validated token in the cache, including
     * serialization and LRU management. This metric helps identify caching overhead.
     */
    CACHE_STORE("Cache store operation"),

    /**
     * HTTP retry operation - single attempt.
     * <p>
     * Measures time for individual retry attempts within the retry strategy.
     * This includes the actual operation execution time but excludes retry delays.
     * Useful for analyzing per-attempt performance patterns.
     */
    RETRY_ATTEMPT("HTTP retry single attempt"),

    /**
     * HTTP retry operation - complete with all attempts.
     * <p>
     * Measures total time for retry operations including all attempts, delays,
     * and eventual success or failure. This represents the complete retry cycle
     * from the caller's perspective and includes exponential backoff delays.
     */
    RETRY_COMPLETE("HTTP retry complete operation"),

    /**
     * HTTP retry delay time.
     * <p>
     * Measures actual delay time between retry attempts as calculated by the
     * exponential backoff algorithm including jitter. This helps analyze
     * retry timing patterns and backoff effectiveness.
     */
    RETRY_DELAY("HTTP retry delay time");

    /**
     * Human-readable description of this measurement type for logging and monitoring.
     */
    private final String description;
}