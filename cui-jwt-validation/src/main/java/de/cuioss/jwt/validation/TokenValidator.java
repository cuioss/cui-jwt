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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.domain.token.RefreshTokenContent;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.pipeline.DecodedJwt;
import de.cuioss.jwt.validation.pipeline.NonValidatingJwtParser;
import de.cuioss.jwt.validation.pipeline.TokenBuilder;
import de.cuioss.jwt.validation.pipeline.TokenClaimValidator;
import de.cuioss.jwt.validation.pipeline.TokenHeaderValidator;
import de.cuioss.jwt.validation.pipeline.TokenSignatureValidator;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main entry point for creating and validating JWT tokens.
 * <p>
 * This class provides methods for creating different types of tokens from
 * JWT strings, handling the validation and parsing process.
 * <p>
 * The validator uses a pipeline approach to validate tokens:
 * <ol>
 *   <li>Basic format validation</li>
 *   <li>Issuer validation</li>
 *   <li>Header validation</li>
 *   <li>Signature validation</li>
 *   <li>Token building</li>
 *   <li>Claim validation</li>
 * </ol>
 * <p>
 * This class is thread-safe after construction.
 * All validation methods can be called concurrently from multiple threads.
 * <p>
 * Usage example:
 * <pre>
 * // Configure HTTP-based JWKS loading
 * HttpJwksLoaderConfig httpConfig = HttpJwksLoaderConfig.builder()
 *     .jwksUrl("https://example.com/.well-known/jwks.json")
 *     .refreshIntervalSeconds(60)
 *     .build();
 *
 * // Create an issuer configuration
 * IssuerConfig issuerConfig = IssuerConfig.builder()
 *     .issuerIdentifier("https://example.com")
 *     .expectedAudience("my-client")
 *     .httpJwksLoaderConfig(httpConfig)
 *     .build(); // Validation happens automatically
 *
 * // Create the token validator
 * // The validator creates a SecurityEventCounter internally and passes it to all components
 * TokenValidator tokenValidator = TokenValidator.builder()
 *     .parserConfig(ParserConfig.builder().build())
 *     .issuerConfig(issuerConfig)
 *     .build();
 *
 * // Parse an access token
 * Optional&lt;AccessTokenContent&gt; accessToken = tokenValidator.createAccessToken(tokenString);
 *
 * // Parse an ID token
 * Optional&lt;IdTokenContent&gt; idToken = tokenValidator.createIdToken(tokenString);
 *
 * // Parse a refresh token
 * Optional&lt;RefreshTokenContent&gt; refreshToken = tokenValidator.createRefreshToken(tokenString);
 *
 * // Access the security event counter for monitoring
 * SecurityEventCounter securityEventCounter = tokenValidator.getSecurityEventCounter();
 *
 * // Access the performance monitor for detailed pipeline metrics
 * TokenValidatorMonitor performanceMonitor = tokenValidator.getPerformanceMonitor();
 * Duration avgSignatureTime = performanceMonitor.getAverageDuration(MeasurementType.SIGNATURE_VALIDATION);
 * </pre>
 * <p>
 * Implements requirements:
 * <ul>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-1">CUI-JWT-1: Token Parsing and Validation</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-2">CUI-JWT-2: Token Representation</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-3">CUI-JWT-3: Multi-Issuer Support</a></li>
 * </ul>
 * <p>
 * For more detailed specifications, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#_tokenvalidator">Technical Components Specification</a>
 *
 * @since 1.0
 */
@SuppressWarnings("JavadocLinkAsPlainText")
public class TokenValidator {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidator.class);

    private final NonValidatingJwtParser jwtParser;


    /**
     * Resolver for issuer configurations that handles caching and concurrency.
     * This encapsulates the complex logic for resolving issuer configs thread-safely.
     */
    private final IssuerConfigResolver issuerConfigResolver;

    /**
     * Counter for security events that occur during token processing.
     * This counter is thread-safe and can be accessed from outside to monitor security events.
     */
    @Getter
    @NonNull
    private final SecurityEventCounter securityEventCounter;

    /**
     * Monitor for performance metrics of JWT validation pipeline steps.
     * This monitor is thread-safe and provides detailed timing measurements for each validation step.
     */
    @Getter
    @NonNull
    private final TokenValidatorMonitor performanceMonitor;


    /**
     * Private constructor used by builder.
     */
    @Builder
    private TokenValidator(
            ParserConfig parserConfig,
            @Singular @NonNull List<IssuerConfig> issuerConfigs,
            SecurityEventCounter securityEventCounter,
            TokenValidatorMonitor performanceMonitor) {

        if (issuerConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one issuer configuration must be provided");
        }
        
        // Use default ParserConfig if not provided
        if (parserConfig == null) {
            parserConfig = ParserConfig.builder().build();
        }

        LOGGER.debug("Initialize token validator with %s and %s issuer configurations", parserConfig, issuerConfigs.size());

        this.securityEventCounter = securityEventCounter != null ? securityEventCounter : new SecurityEventCounter();
        this.performanceMonitor = performanceMonitor != null ? performanceMonitor : new TokenValidatorMonitor();

        this.jwtParser = NonValidatingJwtParser.builder()
                .config(parserConfig)
                .securityEventCounter(this.securityEventCounter)
                .build();

        // Let the IssuerConfigResolver handle all issuer config processing
        IssuerConfig[] configArray = issuerConfigs.toArray(new IssuerConfig[0]);
        this.issuerConfigResolver = new IssuerConfigResolver(configArray, this.securityEventCounter);

        LOGGER.info(JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED.format(issuerConfigResolver.toString()));
    }


    /**
     * Creates an access token from the given token string.
     *
     * @param tokenString The token string to parse, must not be null
     * @return The parsed access token
     * @throws TokenValidationException if the token is invalid
     */
    @NonNull
    public AccessTokenContent createAccessToken(@NonNull String tokenString) {
        LOGGER.debug("Creating access token");
        AccessTokenContent result = processTokenPipeline(
                tokenString,
                (decodedJwt, issuerConfig) -> new TokenBuilder(issuerConfig).createAccessToken(decodedJwt),
                true
        );

        LOGGER.debug(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED::format);
        securityEventCounter.increment(SecurityEventCounter.EventType.ACCESS_TOKEN_CREATED);

        return result;
    }

    /**
     * Creates an ID token from the given token string.
     *
     * @param tokenString The token string to parse, must not be null
     * @return The parsed ID token
     * @throws TokenValidationException if the token is invalid
     */
    @NonNull
    public IdTokenContent createIdToken(@NonNull String tokenString) {
        LOGGER.debug("Creating ID token");
        IdTokenContent result = processTokenPipeline(
                tokenString,
                (decodedJwt, issuerConfig) -> new TokenBuilder(issuerConfig).createIdToken(decodedJwt),
                false
        );

        LOGGER.debug(JWTValidationLogMessages.DEBUG.ID_TOKEN_CREATED::format);
        securityEventCounter.increment(SecurityEventCounter.EventType.ID_TOKEN_CREATED);

        return result;
    }

    /**
     * Creates a refresh token from the given token string.
     *
     * @param tokenString The token string to parse, must not be null
     * @return The parsed refresh token
     * @throws TokenValidationException if the token is invalid
     */
    @NonNull
    @SuppressWarnings("java:S3655") //owolff: False Positive: isPresent is checked
    public RefreshTokenContent createRefreshToken(@NonNull String tokenString) {
        LOGGER.debug("Creating refresh token");
        // For refresh tokens, we don't need the full pipeline
        if (MoreStrings.isBlank(tokenString)) {
            LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY::format);
            securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EMPTY);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.TOKEN_EMPTY,
                    "Token is empty or null"
            );
        }
        Map<String, ClaimValue> claims = Collections.emptyMap();
        try {
            DecodedJwt decoded = jwtParser.decode(tokenString, false);
            if (decoded.getBody().isPresent()) {
                LOGGER.debug("Adding claims, because of being a JWT");
                claims = TokenBuilder.extractClaimsForRefreshToken(decoded.getBody().get());
            }
        } catch (TokenValidationException e) {
            // Ignore validation exceptions for refresh tokens
            LOGGER.debug("Ignoring validation exception for refresh token: %s", e.getMessage());
        }
        var refreshToken = new RefreshTokenContent(tokenString, claims);
        LOGGER.debug(JWTValidationLogMessages.DEBUG.REFRESH_TOKEN_CREATED::format);
        securityEventCounter.increment(SecurityEventCounter.EventType.REFRESH_TOKEN_CREATED);
        return refreshToken;
    }

    /**
     * Processes a token through the token pipeline.
     * <p>
     * This method implements an optimized token pipeline with early termination
     * for common failure cases. The token steps are ordered to fail fast:
     * 1. Basic token format validation (empty check, decoding)
     * 2. Issuer validation (presence and configuration lookup)
     * 3. Header validation (algorithm)
     * 4. Signature validation
     * 5. Token building
     * 6. Claim validation
     * <p>
     * Validators are only created if needed, avoiding unnecessary object creation
     * for invalid tokens.
     * <p>
     * Performance measurements are recorded for each pipeline step to enable
     * detailed analysis of validation performance.
     *
     * @param tokenString  the token string to process
     * @param tokenBuilder function to build the token from the decoded JWT and issuer config
     * @param <T>          the type of token to create
     * @return the validated token
     * @throws TokenValidationException if validation fails
     */
    private <T extends TokenContent> T processTokenPipeline(
            String tokenString,
            TokenBuilderFunction<T> tokenBuilder,
            boolean recordMetrics) {

        long pipelineStartTime = recordMetrics ? System.nanoTime() : 0;
        try {
            validateTokenFormat(tokenString, recordMetrics);
            DecodedJwt decodedJwt = decodeToken(tokenString, recordMetrics);
            String issuer = validateAndExtractIssuer(decodedJwt, recordMetrics);
            IssuerConfig issuerConfig = resolveIssuerConfig(issuer, recordMetrics);

            validateTokenHeader(decodedJwt, issuerConfig, recordMetrics);
            validateTokenSignature(decodedJwt, issuerConfig, recordMetrics);
            T token = buildToken(decodedJwt, issuerConfig, tokenBuilder, recordMetrics);
            T validatedToken = validateTokenClaims(token, issuerConfig, recordMetrics);

            LOGGER.debug("Token successfully validated");
            return validatedToken;
        } finally {
            // Record complete validation time (including error scenarios)
            if (recordMetrics) {
                long pipelineEndTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.COMPLETE_VALIDATION, pipelineEndTime - pipelineStartTime);
            }
        }
    }

    private void validateTokenFormat(String tokenString, boolean recordMetrics) {
        long startTime = recordMetrics ? System.nanoTime() : 0;
        try {
            if (MoreStrings.isBlank(tokenString)) {
                LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY::format);
                securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EMPTY);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.TOKEN_EMPTY,
                        "Token is empty or null"
                );
            }
        } finally {
            if (recordMetrics) {
                long endTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.TOKEN_FORMAT_CHECK, endTime - startTime);
            }
        }
    }

    private DecodedJwt decodeToken(String tokenString, boolean recordMetrics) {
        long startTime = recordMetrics ? System.nanoTime() : 0;
        try {
            return jwtParser.decode(tokenString);
        } finally {
            if (recordMetrics) {
                long endTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.TOKEN_PARSING, endTime - startTime);
            }
        }
    }

    private String validateAndExtractIssuer(DecodedJwt decodedJwt, boolean recordMetrics) {
        long startTime = recordMetrics ? System.nanoTime() : 0;
        try {
            Optional<String> issuer = decodedJwt.getIssuer();
            if (issuer.isEmpty()) {
                LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format("iss"));
                securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.MISSING_CLAIM,
                        "Missing required issuer (iss) claim in token"
                );
            }
            return issuer.get();
        } finally {
            if (recordMetrics) {
                long endTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.ISSUER_EXTRACTION, endTime - startTime);
            }
        }
    }

    /**
     * Resolves the appropriate issuer configuration for the given issuer.
     * Delegates to the IssuerConfigResolver for thread-safe resolution.
     *
     * @param issuer the issuer to resolve configuration for
     * @return the issuer configuration if healthy and available
     * @throws TokenValidationException if no configuration found or issuer is unhealthy
     */
    private IssuerConfig resolveIssuerConfig(String issuer, boolean recordMetrics) {
        long startTime = recordMetrics ? System.nanoTime() : 0;
        try {
            return issuerConfigResolver.resolveConfig(issuer);
        } finally {
            if (recordMetrics) {
                long endTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.ISSUER_CONFIG_RESOLUTION, endTime - startTime);
            }
        }
    }

    private void validateTokenHeader(DecodedJwt decodedJwt, IssuerConfig issuerConfig, boolean recordMetrics) {
        long startTime = recordMetrics ? System.nanoTime() : 0;
        try {
            TokenHeaderValidator headerValidator = new TokenHeaderValidator(issuerConfig, securityEventCounter);
            headerValidator.validate(decodedJwt);
        } finally {
            if (recordMetrics) {
                long endTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.HEADER_VALIDATION, endTime - startTime);
            }
        }
    }

    private void validateTokenSignature(DecodedJwt decodedJwt, IssuerConfig issuerConfig, boolean recordMetrics) {
        long startTime = recordMetrics ? System.nanoTime() : 0;
        try {
            JwksLoader jwksLoader = issuerConfig.getJwksLoader();

            // Measure JWKS operations separately if loader access involves network/cache operations
            if (recordMetrics) {
                long jwksStartTime = System.nanoTime();
                TokenSignatureValidator signatureValidator = new TokenSignatureValidator(jwksLoader, securityEventCounter);
                long jwksEndTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.JWKS_OPERATIONS, jwksEndTime - jwksStartTime);
                signatureValidator.validateSignature(decodedJwt);
            } else {
                TokenSignatureValidator signatureValidator = new TokenSignatureValidator(jwksLoader, securityEventCounter);
                signatureValidator.validateSignature(decodedJwt);
            }
        } finally {
            if (recordMetrics) {
                long endTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.SIGNATURE_VALIDATION, endTime - startTime);
            }
        }
    }

    @NonNull
    private <T extends TokenContent> T buildToken(
            DecodedJwt decodedJwt,
            IssuerConfig issuerConfig,
            TokenBuilderFunction<T> tokenBuilder,
            boolean recordMetrics) {
        long startTime = recordMetrics ? System.nanoTime() : 0;
        try {
            Optional<T> token = tokenBuilder.apply(decodedJwt, issuerConfig);
            if (token.isEmpty()) {
                LOGGER.debug("Token building failed");
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.MISSING_CLAIM,
                        "Failed to build token from decoded JWT"
                );
            }
            return token.get();
        } finally {
            if (recordMetrics) {
                long endTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.TOKEN_BUILDING, endTime - startTime);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends TokenContent> T validateTokenClaims(TokenContent token, IssuerConfig issuerConfig, boolean recordMetrics) {
        long startTime = recordMetrics ? System.nanoTime() : 0;
        try {
            TokenClaimValidator claimValidator = new TokenClaimValidator(issuerConfig, securityEventCounter);
            TokenContent validatedContent = claimValidator.validate(token);
            return (T) validatedContent;
        } finally {
            if (recordMetrics) {
                long endTime = System.nanoTime();
                performanceMonitor.recordMeasurement(MeasurementType.CLAIMS_VALIDATION, endTime - startTime);
            }
        }
    }


    /**
     * Functional interface for building tokens with issuer config.
     *
     * @param <T> the type of token to create
     */
    @FunctionalInterface
    private interface TokenBuilderFunction<T> {
        Optional<T> apply(DecodedJwt decodedJwt, IssuerConfig issuerConfig);
    }
}