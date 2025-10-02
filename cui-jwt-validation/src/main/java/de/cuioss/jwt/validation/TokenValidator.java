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
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.domain.token.RefreshTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.*;
import de.cuioss.jwt.validation.pipeline.*;
import de.cuioss.jwt.validation.pipeline.validator.TokenClaimValidator;
import de.cuioss.jwt.validation.pipeline.validator.TokenHeaderValidator;
import de.cuioss.jwt.validation.pipeline.validator.TokenSignatureValidator;
import de.cuioss.jwt.validation.pipeline.validator.TokenStringValidator;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *     .monitorConfig(metricsConfig)  // Optional: null means no types monitored
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
     * Validator for pre-pipeline token string validation (null, blank, size checks).
     * This validator runs before any pipeline processing to fail fast on invalid inputs.
     */
    private final TokenStringValidator tokenStringValidator;

    /**
     * Pipeline for validating access tokens with caching support.
     * Handles full validation including early cache checks before expensive operations.
     */
    private final AccessTokenValidationPipeline accessTokenPipeline;

    /**
     * Pipeline for validating ID tokens without caching.
     * Handles full validation for ID tokens used during authentication flows.
     */
    private final IdTokenValidationPipeline idTokenPipeline;

    /**
     * Pipeline for validating refresh tokens with minimal validation.
     * Handles opaque or lightly validated refresh tokens.
     */
    private final RefreshTokenValidationPipeline refreshTokenPipeline;


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
            // Default: disabled monitoring
            this.performanceMonitor = TokenValidatorMonitorConfig.disabled().createMonitor();
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

        this.accessTokenCache = new AccessTokenCache(cacheConfig, this.securityEventCounter);
        LOGGER.debug("AccessTokenCache initialized with maxSize=%s, evictionInterval=%ss",
                cacheConfig.getMaxSize(), cacheConfig.getEvictionIntervalSeconds());

        // Construct TokenStringValidator for pre-pipeline validation
        this.tokenStringValidator = new TokenStringValidator(
                parserConfig, this.securityEventCounter);
        LOGGER.debug("TokenStringValidator initialized");

        // Construct RefreshTokenValidationPipeline (minimal validation, no cache, no metrics)
        this.refreshTokenPipeline = new RefreshTokenValidationPipeline(
                this.jwtParser, this.securityEventCounter);
        LOGGER.debug("RefreshTokenValidationPipeline initialized");

        // Construct IdTokenValidationPipeline (full validation, no cache, no metrics)
        this.idTokenPipeline = new IdTokenValidationPipeline(
                this.jwtParser,
                this.issuerConfigResolver,
                this.signatureValidators,
                this.tokenBuilders,
                this.claimValidators,
                this.headerValidators,
                this.securityEventCounter);
        LOGGER.debug("IdTokenValidationPipeline initialized");

        // Construct AccessTokenValidationPipeline (full validation, with cache and metrics)
        this.accessTokenPipeline = new AccessTokenValidationPipeline(
                this.jwtParser,
                this.issuerConfigResolver,
                this.signatureValidators,
                this.tokenBuilders,
                this.claimValidators,
                this.headerValidators,
                this.accessTokenCache,
                this.securityEventCounter,
                this.performanceMonitor);
        LOGGER.debug("AccessTokenValidationPipeline initialized");

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
        MetricsTicker completeTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.COMPLETE_VALIDATION, performanceMonitor);
        try {
            // Pre-pipeline validation (null, blank, size)
            tokenStringValidator.validate(tokenString);

            // Delegate to access token pipeline (handles caching, metrics, full validation)
            AccessTokenContent result = accessTokenPipeline.validate(tokenString);

            LOGGER.debug("Successfully created access token");
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

        // Pre-pipeline validation (null, blank, size)
        tokenStringValidator.validate(tokenString);

        // Delegate to ID token pipeline (handles full validation, no caching, no metrics)
        IdTokenContent validatedToken = idTokenPipeline.validate(tokenString);

        LOGGER.debug("Successfully created ID-Token");
        securityEventCounter.increment(SecurityEventCounter.EventType.ID_TOKEN_CREATED);

        return validatedToken;
    }

    /**
     * Creates a refresh token from the given token string.
     *
     * @param tokenString The token string to parse, must not be null
     * @return The parsed refresh token
     * @throws TokenValidationException if the token is invalid
     */
    @NonNull
    public RefreshTokenContent createRefreshToken(@NonNull String tokenString) {
        LOGGER.debug("Creating refresh token");

        // Pre-pipeline validation (null, blank, size)
        tokenStringValidator.validate(tokenString);

        // Delegate to refresh token pipeline (handles minimal validation, no caching, no metrics)
        RefreshTokenContent refreshToken = refreshTokenPipeline.validate(tokenString);

        LOGGER.debug("Successfully created Refresh-Token");
        securityEventCounter.increment(SecurityEventCounter.EventType.REFRESH_TOKEN_CREATED);

        return refreshToken;
    }
}