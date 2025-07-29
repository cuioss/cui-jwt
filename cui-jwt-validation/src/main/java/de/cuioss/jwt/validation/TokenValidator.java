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

import de.cuioss.jwt.validation.cache.AccessTokenCache;
import de.cuioss.jwt.validation.cache.AccessTokenCacheConfig;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.domain.token.RefreshTokenContent;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.MetricsTicker;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.jwt.validation.pipeline.DecodedJwt;
import de.cuioss.jwt.validation.pipeline.NonValidatingJwtParser;
import de.cuioss.jwt.validation.pipeline.TokenBuilder;
import de.cuioss.jwt.validation.pipeline.TokenClaimValidator;
import de.cuioss.jwt.validation.pipeline.TokenHeaderValidator;
import de.cuioss.jwt.validation.pipeline.TokenSignatureValidator;
import de.cuioss.jwt.validation.pipeline.ValidationContext;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.util.HashMap;
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
 * // Create the token validator with custom metrics and cache configuration
 * TokenValidatorMonitorConfig metricsConfig = TokenValidatorMonitorConfig.builder()
 *     .windowSize(200)
 *     .measurementType(MeasurementType.SIGNATURE_VALIDATION)
 *     .measurementType(MeasurementType.COMPLETE_VALIDATION)
 *     .build();
 *
 * // Configure access token caching
 * AccessTokenCacheConfig cacheConfig = AccessTokenCacheConfig.builder()
 *     .maxSize(500)  // Set to 0 to disable caching
 *     .evictionIntervalSeconds(300L)
 *     .build();
 *
 * // The validator creates a SecurityEventCounter internally and passes it to all components
 * TokenValidator tokenValidator = TokenValidator.builder()
 *     .parserConfig(ParserConfig.builder().build())
 *     .issuerConfig(issuerConfig)
 *     .monitorConfig(metricsConfig)  // Optional: null means all types monitored
 *     .cacheConfig(cacheConfig)      // Optional: null means default caching enabled
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
 * StripedRingBufferStatistics metrics = performanceMonitor.getValidationMetrics(MeasurementType.SIGNATURE_VALIDATION);
 * Duration p50SignatureTime = metrics.p50();
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
     * Immutable map of signature validators per issuer to avoid creating new instances on every validation.
     * This optimization addresses the critical performance bottleneck in JWT signature validation.
     * Key: issuer identifier, Value: cached TokenSignatureValidator instance
     */
    private final Map<String, TokenSignatureValidator> signatureValidators;

    /**
     * Immutable map of token builders per issuer to avoid creating new instances on every token validation.
     * This optimization eliminates the 3.7ms p99 spikes from object allocation.
     * Key: issuer identifier, Value: cached TokenBuilder instance
     */
    private final Map<String, TokenBuilder> tokenBuilders;

    /**
     * Immutable map of claim validators per issuer to avoid creating new instances on every token validation.
     * This optimization eliminates the 1.3ms p99 spikes from object allocation.
     * Key: issuer identifier, Value: cached TokenClaimValidator instance
     */
    private final Map<String, TokenClaimValidator> claimValidators;

    /**
     * Immutable map of header validators per issuer to avoid creating new instances on every request.
     * This optimization reduces object allocation overhead in the validation pipeline.
     * Key: issuer identifier, Value: cached TokenHeaderValidator instance
     */
    private final Map<String, TokenHeaderValidator> headerValidators;

    /**
     * Optional cache for validated access tokens to avoid redundant validation.
     * This cache significantly improves performance for repeated token validations.
     */
    private final AccessTokenCache accessTokenCache;


    /**
     * Private constructor used by builder.
     */
    @Builder
    private TokenValidator(
            ParserConfig parserConfig,
            @Singular @NonNull List<IssuerConfig> issuerConfigs,
            TokenValidatorMonitorConfig monitorConfig,
            AccessTokenCacheConfig cacheConfig) {

        if (issuerConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one issuer configuration must be provided");
        }

        // Use default ParserConfig if not provided
        if (parserConfig == null) {
            parserConfig = ParserConfig.builder().build();
        }

        LOGGER.debug("Initialize token validator with %s and %s issuer configurations", parserConfig, issuerConfigs.size());

        // Always create new instances internally
        this.securityEventCounter = new SecurityEventCounter();

        // Create monitor based on configuration
        if (monitorConfig != null) {
            this.performanceMonitor = monitorConfig.createMonitor();
        } else {
            // Default: monitor all types with default window size
            this.performanceMonitor = TokenValidatorMonitorConfig.defaultEnabled().createMonitor();
        }

        this.jwtParser = NonValidatingJwtParser.builder()
                .config(parserConfig)
                .securityEventCounter(this.securityEventCounter)
                .build();

        // Let the IssuerConfigResolver handle all issuer config processing
        this.issuerConfigResolver = new IssuerConfigResolver(issuerConfigs, this.securityEventCounter);

        // Initialize immutable map of TokenSignatureValidator instances for each issuer
        // This eliminates the performance bottleneck of creating new instances on every validation
        Map<String, TokenSignatureValidator> signatureValidatorsMap = new HashMap<>();
        Map<String, TokenBuilder> tokenBuildersMap = new HashMap<>();
        Map<String, TokenClaimValidator> claimValidatorsMap = new HashMap<>();
        Map<String, TokenHeaderValidator> headerValidatorsMap = new HashMap<>();

        for (IssuerConfig issuerConfig : issuerConfigs) {
            String issuerIdentifier = issuerConfig.getIssuerIdentifier();

            // Initialize signature validator
            TokenSignatureValidator signatureValidator = new TokenSignatureValidator(
                    issuerConfig.getJwksLoader(),
                    this.securityEventCounter,
                    issuerConfig.getAlgorithmPreferences()
            );
            signatureValidatorsMap.put(issuerIdentifier, signatureValidator);
            LOGGER.debug("Pre-created TokenSignatureValidator for issuer: %s", issuerIdentifier);

            // Initialize token builder
            TokenBuilder tokenBuilder = new TokenBuilder(issuerConfig);
            tokenBuildersMap.put(issuerIdentifier, tokenBuilder);
            LOGGER.debug("Pre-created TokenBuilder for issuer: %s", issuerIdentifier);

            // Initialize claim validator
            TokenClaimValidator claimValidator = new TokenClaimValidator(issuerConfig, this.securityEventCounter);
            claimValidatorsMap.put(issuerIdentifier, claimValidator);
            LOGGER.debug("Pre-created TokenClaimValidator for issuer: %s", issuerIdentifier);

            // Initialize header validator
            TokenHeaderValidator headerValidator = new TokenHeaderValidator(issuerConfig, this.securityEventCounter);
            headerValidatorsMap.put(issuerIdentifier, headerValidator);
            LOGGER.debug("Pre-created TokenHeaderValidator for issuer: %s", issuerIdentifier);
        }

        this.signatureValidators = Map.copyOf(signatureValidatorsMap);
        this.tokenBuilders = Map.copyOf(tokenBuildersMap);
        this.claimValidators = Map.copyOf(claimValidatorsMap);
        this.headerValidators = Map.copyOf(headerValidatorsMap);

        // Initialize access token cache based on configuration
        if (cacheConfig == null) {
            cacheConfig = AccessTokenCacheConfig.defaultConfig();
        }

        this.accessTokenCache = cacheConfig.createCache(this.securityEventCounter);
        LOGGER.debug("AccessTokenCache initialized with maxSize=%d, evictionInterval=%ds",
                cacheConfig.getMaxSize(), cacheConfig.getEvictionIntervalSeconds());

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

        // Record complete validation time
        MetricsTicker completeTicker = MeasurementType.COMPLETE_VALIDATION.createTicker(performanceMonitor, true);
        completeTicker.startRecording();
        try {
            // Use cache-aware processing for access tokens
            AccessTokenContent result = processAccessTokenWithCache(tokenString);

            LOGGER.debug(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED::format);
            securityEventCounter.increment(SecurityEventCounter.EventType.ACCESS_TOKEN_CREATED);

            return result;
        } finally {
            completeTicker.stopAndRecord();
        }
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
                (decodedJwt, issuerConfig) -> {
                    TokenBuilder cachedBuilder = tokenBuilders.get(issuerConfig.getIssuerIdentifier());
                    return cachedBuilder.createIdToken(decodedJwt);
                },
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
        Map<String, ClaimValue> claims = Map.of();
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

        MetricsTicker pipelineTicker = MeasurementType.COMPLETE_VALIDATION.createTicker(performanceMonitor, recordMetrics);
        pipelineTicker.startRecording();
        try {
            validateTokenFormat(tokenString, MeasurementType.TOKEN_FORMAT_CHECK.createTicker(performanceMonitor, recordMetrics));
            DecodedJwt decodedJwt = decodeToken(tokenString, MeasurementType.TOKEN_PARSING.createTicker(performanceMonitor, recordMetrics));
            String issuer = validateAndExtractIssuer(decodedJwt, MeasurementType.ISSUER_EXTRACTION.createTicker(performanceMonitor, recordMetrics));
            IssuerConfig issuerConfig = resolveIssuerConfig(issuer, MeasurementType.ISSUER_CONFIG_RESOLUTION.createTicker(performanceMonitor, recordMetrics));

            validateTokenHeader(decodedJwt, issuerConfig, MeasurementType.HEADER_VALIDATION.createTicker(performanceMonitor, recordMetrics));
            validateTokenSignature(decodedJwt, issuerConfig, MeasurementType.SIGNATURE_VALIDATION.createTicker(performanceMonitor, recordMetrics));
            T token = buildToken(decodedJwt, issuerConfig, tokenBuilder, MeasurementType.TOKEN_BUILDING.createTicker(performanceMonitor, recordMetrics));
            T validatedToken = validateTokenClaims(token, issuerConfig, MeasurementType.CLAIMS_VALIDATION.createTicker(performanceMonitor, recordMetrics));

            LOGGER.debug("Token successfully validated");
            return validatedToken;
        } finally {
            // Record complete validation time (including error scenarios)
            pipelineTicker.stopAndRecord();
        }
    }

    /**
     * Process access token with cache support.
     * This method integrates caching into the validation pipeline for access tokens.
     *
     * @param tokenString the JWT token string
     * @return the validated access token
     * @throws TokenValidationException if validation fails
     */
    private AccessTokenContent processAccessTokenWithCache(@NonNull String tokenString) {
        // Perform minimal validation to get issuer for cache key
        validateTokenFormat(tokenString, MeasurementType.TOKEN_FORMAT_CHECK.createTicker(performanceMonitor, true));
        DecodedJwt decodedJwt = decodeToken(tokenString, MeasurementType.TOKEN_PARSING.createTicker(performanceMonitor, true));
        String issuer = validateAndExtractIssuer(decodedJwt, MeasurementType.ISSUER_EXTRACTION.createTicker(performanceMonitor, true));

        // Use transparent cache - handles enabled/disabled states internally
        return accessTokenCache.computeIfAbsent(
                tokenString,
                token -> {
                    // Continue with expensive validation steps
                    // We already have the decodedJwt and issuer, so continue from issuer config resolution
                    IssuerConfig issuerConfig = resolveIssuerConfig(issuer, MeasurementType.ISSUER_CONFIG_RESOLUTION.createTicker(performanceMonitor, true));
                    validateTokenHeader(decodedJwt, issuerConfig, MeasurementType.HEADER_VALIDATION.createTicker(performanceMonitor, true));
                    validateTokenSignature(decodedJwt, issuerConfig, MeasurementType.SIGNATURE_VALIDATION.createTicker(performanceMonitor, true));
                    
                    TokenBuilder cachedBuilder = tokenBuilders.get(issuerConfig.getIssuerIdentifier());
                    AccessTokenContent accessToken = buildToken(decodedJwt, issuerConfig, 
                            (jwt, config) -> cachedBuilder.createAccessToken(jwt),
                            MeasurementType.TOKEN_BUILDING.createTicker(performanceMonitor, true));
                    
                    AccessTokenContent validatedToken = validateTokenClaims(accessToken, issuerConfig, 
                            MeasurementType.CLAIMS_VALIDATION.createTicker(performanceMonitor, true));
                    
                    LOGGER.debug("Token successfully validated");
                    return validatedToken;
                },
                performanceMonitor
        );
    }

    private void validateTokenFormat(String tokenString, MetricsTicker ticker) {
        ticker.startRecording();
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
            ticker.stopAndRecord();
        }
    }

    private DecodedJwt decodeToken(String tokenString, MetricsTicker ticker) {
        ticker.startRecording();
        try {
            return jwtParser.decode(tokenString);
        } finally {
            ticker.stopAndRecord();
        }
    }

    private String validateAndExtractIssuer(DecodedJwt decodedJwt, MetricsTicker ticker) {
        ticker.startRecording();
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
            ticker.stopAndRecord();
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
    private IssuerConfig resolveIssuerConfig(String issuer, MetricsTicker ticker) {
        ticker.startRecording();
        try {
            return issuerConfigResolver.resolveConfig(issuer);
        } finally {
            ticker.stopAndRecord();
        }
    }

    private void validateTokenHeader(DecodedJwt decodedJwt, IssuerConfig issuerConfig, MetricsTicker ticker) {
        ticker.startRecording();
        try {
            TokenHeaderValidator cachedValidator = headerValidators.get(issuerConfig.getIssuerIdentifier());
            cachedValidator.validate(decodedJwt);
        } finally {
            ticker.stopAndRecord();
        }
    }

    private void validateTokenSignature(DecodedJwt decodedJwt, IssuerConfig issuerConfig, MetricsTicker ticker) {
        ticker.startRecording();
        try {
            // Get pre-created TokenSignatureValidator for this issuer
            String issuerIdentifier = issuerConfig.getIssuerIdentifier();
            TokenSignatureValidator signatureValidator = signatureValidators.get(issuerIdentifier);

            if (signatureValidator == null) {
                throw new IllegalStateException("No signature validator found for issuer: " + issuerIdentifier);
            }

            // Use the cached signature validator for validation
            signatureValidator.validateSignature(decodedJwt);
        } finally {
            ticker.stopAndRecord();
        }
    }

    @NonNull
    private <T extends TokenContent> T buildToken(
            DecodedJwt decodedJwt,
            IssuerConfig issuerConfig,
            TokenBuilderFunction<T> tokenBuilder,
            MetricsTicker ticker) {
        ticker.startRecording();
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
            ticker.stopAndRecord();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends TokenContent> T validateTokenClaims(TokenContent token, IssuerConfig issuerConfig, MetricsTicker ticker) {
        ticker.startRecording();
        try {
            // Create ValidationContext with cached current time to eliminate synchronous OffsetDateTime.now() calls
            // Use clock skew of 60 seconds as per ExpirationValidator.CLOCK_SKEW_SECONDS
            ValidationContext context = new ValidationContext(60);

            TokenClaimValidator cachedValidator = claimValidators.get(issuerConfig.getIssuerIdentifier());
            TokenContent validatedContent = cachedValidator.validate(token, context);
            return (T) validatedContent;
        } finally {
            ticker.stopAndRecord();
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