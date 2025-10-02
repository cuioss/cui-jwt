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
package de.cuioss.jwt.validation.pipeline;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.IssuerConfigResolver;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.cache.AccessTokenCache;
import de.cuioss.jwt.validation.domain.context.ValidationContext;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.MetricsTicker;
import de.cuioss.jwt.validation.metrics.MetricsTickerFactory;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.pipeline.validator.TokenClaimValidator;
import de.cuioss.jwt.validation.pipeline.validator.TokenHeaderValidator;
import de.cuioss.jwt.validation.pipeline.validator.TokenSignatureValidator;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * Pipeline for validating access tokens with caching support.
 * <p>
 * Access tokens are the most frequently validated token type and benefit from
 * aggressive caching and early cache checks. This pipeline performs an early
 * cache lookup after parsing and issuer extraction, but before expensive
 * cryptographic signature validation.
 * <p>
 * <strong>Validation Steps:</strong>
 * <ol>
 *   <li>Parse token into header and payload (metrics: TOKEN_PARSING)</li>
 *   <li>Extract and validate issuer claim (metrics: ISSUER_EXTRACTION)</li>
 *   <li><strong>CHECK CACHE - EARLY</strong> (metrics: CACHE_LOOKUP) ← Issue #131 optimization</li>
 *   <li>If cache miss, resolve issuer configuration (metrics: ISSUER_CONFIG_RESOLUTION)</li>
 *   <li>If cache miss, validate JWT header (metrics: HEADER_VALIDATION)</li>
 *   <li>If cache miss, validate JWT signature (metrics: SIGNATURE_VALIDATION) ← Most expensive</li>
 *   <li>If cache miss, build typed AccessTokenContent object (metrics: TOKEN_BUILDING)</li>
 *   <li>If cache miss, validate token claims (metrics: CLAIMS_VALIDATION)</li>
 *   <li>If cache miss, store in cache (metrics: CACHE_STORE)</li>
 * </ol>
 * <p>
 * <strong>Note:</strong> TokenStringValidator has already validated that the token
 * is non-null, non-blank, and within size limits before this pipeline is called.
 * <p>
 * <strong>Full Metrics Instrumentation:</strong> Access token validation uses
 * {@code TokenValidatorMonitor} to record detailed performance metrics for each
 * validation step, enabling fine-grained performance analysis.
 * <p>
 * <strong>Critical Optimization:</strong> The cache check happens at step 3, after
 * minimal parsing but before expensive cryptographic operations. This addresses
 * <a href="https://github.com/cuioss/cui-jwt/issues/131">issue #131</a> by maximizing
 * cache hit performance.
 * <p>
 * This class is thread-safe after construction. All validators are pre-created
 * and cached in immutable maps for optimal performance.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class AccessTokenValidationPipeline {

    private static final CuiLogger LOGGER = new CuiLogger(AccessTokenValidationPipeline.class);

    private final NonValidatingJwtParser jwtParser;
    private final IssuerConfigResolver issuerConfigResolver;
    private final Map<String, TokenSignatureValidator> signatureValidators;
    private final Map<String, TokenBuilder> tokenBuilders;
    private final Map<String, TokenClaimValidator> claimValidators;
    private final Map<String, TokenHeaderValidator> headerValidators;
    private final AccessTokenCache cache;
    private final SecurityEventCounter securityEventCounter;
    private final TokenValidatorMonitor performanceMonitor;

    /**
     * Creates a new AccessTokenValidationPipeline.
     *
     * @param jwtParser the JWT parser for decoding tokens
     * @param issuerConfigResolver the resolver for issuer configurations
     * @param signatureValidators pre-created signature validators keyed by issuer
     * @param tokenBuilders pre-created token builders keyed by issuer
     * @param claimValidators pre-created claim validators keyed by issuer
     * @param headerValidators pre-created header validators keyed by issuer
     * @param cache the cache for validated access tokens
     * @param securityEventCounter the security event counter for tracking operations
     * @param performanceMonitor the monitor for recording performance metrics
     */
    public AccessTokenValidationPipeline(@NonNull NonValidatingJwtParser jwtParser,
            @NonNull IssuerConfigResolver issuerConfigResolver,
            @NonNull Map<String, TokenSignatureValidator> signatureValidators,
            @NonNull Map<String, TokenBuilder> tokenBuilders,
            @NonNull Map<String, TokenClaimValidator> claimValidators,
            @NonNull Map<String, TokenHeaderValidator> headerValidators,
            @NonNull AccessTokenCache cache,
            @NonNull SecurityEventCounter securityEventCounter,
            @NonNull TokenValidatorMonitor performanceMonitor) {
        this.jwtParser = jwtParser;
        this.issuerConfigResolver = issuerConfigResolver;
        this.signatureValidators = signatureValidators;
        this.tokenBuilders = tokenBuilders;
        this.claimValidators = claimValidators;
        this.headerValidators = headerValidators;
        this.cache = cache;
        this.securityEventCounter = securityEventCounter;
        this.performanceMonitor = performanceMonitor;
    }

    /**
     * Validates and returns an access token with caching support.
     * <p>
     * This method performs early cache checks before expensive signature validation
     * to optimize performance for repeated token validation (addresses issue #131).
     * <p>
     * If the token is found in cache and is still valid, it is returned immediately
     * without performing expensive cryptographic operations.
     *
     * @param tokenString the token string to validate (guaranteed non-null, non-blank, within size limits)
     * @return the validated access token content
     * @throws TokenValidationException if any validation step fails
     */
    @NonNull
    public AccessTokenContent validate(@NonNull String tokenString) {
        LOGGER.debug("Validating access token");

        // TokenStringValidator has already checked: null, blank, size

        // 1. Parse token (with TOKEN_PARSING metrics)
        MetricsTicker parsingTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.TOKEN_PARSING, performanceMonitor);
        DecodedJwt decodedJwt;
        try {
            decodedJwt = jwtParser.decode(tokenString);
        } finally {
            parsingTicker.stopAndRecord();
        }

        // 2. Extract issuer (with ISSUER_EXTRACTION metrics)
        MetricsTicker extractionTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.ISSUER_EXTRACTION, performanceMonitor);
        String issuer;
        try {
            Optional<String> issuerOpt = decodedJwt.getIssuer();
            if (issuerOpt.isEmpty()) {
                LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format("iss"));
                securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.MISSING_CLAIM,
                        "Missing required issuer (iss) claim in token"
                );
            }
            issuer = issuerOpt.get();
        } finally {
            extractionTicker.stopAndRecord();
        }

        // 3. CHECK CACHE HERE ← EARLY, before expensive operations (issue #131 optimization)
        // Use transparent cache - handles enabled/disabled states internally
        // Cache metrics (CACHE_LOOKUP, CACHE_STORE) are handled inside AccessTokenCache.computeIfAbsent
        return cache.computeIfAbsent(
                tokenString,
                token -> {
                    // Continue with expensive validation steps
                    // We already have the decodedJwt and issuer, so continue from issuer config resolution

                    // 4. Resolve issuer config (with ISSUER_CONFIG_RESOLUTION metrics)
                    MetricsTicker configTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.ISSUER_CONFIG_RESOLUTION, performanceMonitor);
                    IssuerConfig issuerConfig;
                    try {
                        issuerConfig = issuerConfigResolver.resolveConfig(issuer);
                    } finally {
                        configTicker.stopAndRecord();
                    }

                    // 5. Validate header (with HEADER_VALIDATION metrics)
                    MetricsTicker headerTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.HEADER_VALIDATION, performanceMonitor);
                    try {
                        TokenHeaderValidator headerValidator = headerValidators.get(issuerConfig.getIssuerIdentifier());
                        headerValidator.validate(decodedJwt);
                    } finally {
                        headerTicker.stopAndRecord();
                    }

                    // 6. Validate signature (with SIGNATURE_VALIDATION metrics) ← MOST expensive operation
                    MetricsTicker signatureTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.SIGNATURE_VALIDATION, performanceMonitor);
                    try {
                        TokenSignatureValidator signatureValidator = signatureValidators.get(issuerConfig.getIssuerIdentifier());
                        if (signatureValidator == null) {
                            throw new IllegalStateException("No signature validator found for issuer: " + issuerConfig.getIssuerIdentifier());
                        }
                        signatureValidator.validateSignature(decodedJwt);
                    } finally {
                        signatureTicker.stopAndRecord();
                    }

                    // 7. Build token (with TOKEN_BUILDING metrics)
                    MetricsTicker buildingTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.TOKEN_BUILDING, performanceMonitor);
                    AccessTokenContent accessToken;
                    try {
                        TokenBuilder tokenBuilder = tokenBuilders.get(issuerConfig.getIssuerIdentifier());
                        Optional<AccessTokenContent> tokenOpt = tokenBuilder.createAccessToken(decodedJwt);
                        if (tokenOpt.isEmpty()) {
                            LOGGER.debug("Access token building failed");
                            throw new TokenValidationException(
                                    SecurityEventCounter.EventType.MISSING_CLAIM,
                                    "Failed to build access token from decoded JWT"
                            );
                        }
                        accessToken = tokenOpt.get();
                    } finally {
                        buildingTicker.stopAndRecord();
                    }

                    // 8. Validate claims (with CLAIMS_VALIDATION metrics)
                    MetricsTicker claimsTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.CLAIMS_VALIDATION, performanceMonitor);
                    AccessTokenContent validatedToken;
                    try {
                        // Create ValidationContext with cached current time to eliminate synchronous OffsetDateTime.now() calls
                        // Use clock skew of 60 seconds as per ExpirationValidator.CLOCK_SKEW_SECONDS
                        ValidationContext context = new ValidationContext(60);
                        TokenClaimValidator claimValidator = claimValidators.get(issuerConfig.getIssuerIdentifier());
                        validatedToken = (AccessTokenContent) claimValidator.validate(accessToken, context);
                    } finally {
                        claimsTicker.stopAndRecord();
                    }

                    LOGGER.debug("Token successfully validated");

                    // 9. Return validated token (cache.computeIfAbsent will store it with CACHE_STORE metrics)
                    return validatedToken;
                },
                performanceMonitor
        );
    }
}
