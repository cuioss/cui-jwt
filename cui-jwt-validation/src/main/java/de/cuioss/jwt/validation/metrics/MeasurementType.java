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
    JWKS_OPERATIONS("JWKS key operations");

    /**
     * Human-readable description of this measurement type for logging and monitoring.
     */
    private final String description;
}