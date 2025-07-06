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

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.domain.token.RefreshTokenContent;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.pipeline.DecodedJwt;
import de.cuioss.jwt.validation.pipeline.NonValidatingJwtParser;
import de.cuioss.jwt.validation.pipeline.TokenBuilder;
import de.cuioss.jwt.validation.pipeline.TokenClaimValidator;
import de.cuioss.jwt.validation.pipeline.TokenHeaderValidator;
import de.cuioss.jwt.validation.pipeline.TokenSignatureValidator;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import lombok.Getter;
import lombok.NonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
 * TokenValidator tokenValidator = new TokenValidator(
 *     ParserConfig.builder().build(),
 *     issuerConfig
 * );
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
     * Queue of enabled issuer configurations.
     * Since issuer identifiers are now dynamically resolved, we store configurations
     * in a concurrent queue and match them during token validation.
     * Healthy configurations are removed from this queue after being cached.
     */
    @Getter
    @NonNull
    private final ConcurrentLinkedQueue<IssuerConfig> issuerConfigs;

    /**
     * Cache of resolved issuer identifier to IssuerConfig mappings for performance.
     * This cache is populated dynamically as issuer identifiers are resolved.
     * Uses AtomicReference to safely transition from ConcurrentHashMap to immutable map
     * for optimal read performance once all issuer configs are processed.
     */
    private final AtomicReference<Map<String, IssuerConfig>> validatedIssuerConfig =
            new AtomicReference<>(new ConcurrentHashMap<>());

    /**
     * Flag to track whether the issuer config cache has been optimized for read-only access.
     * Once set to true, no more modifications to the cache are allowed.
     */
    private final AtomicBoolean isOptimized = new AtomicBoolean(false);

    /**
     * Counter for security events that occur during token processing.
     * This counter is thread-safe and can be accessed from outside to monitor security events.
     */
    @Getter
    @NonNull
    private final SecurityEventCounter securityEventCounter;


    /**
     * Creates a new TokenValidator with the given issuer configurations.
     * It is used for standard use cases where no special configuration is needed.
     *
     * @param issuerConfigs varargs of issuer configurations, must not be null
     */
    public TokenValidator(@NonNull IssuerConfig... issuerConfigs) {
        this(ParserConfig.builder().build(), issuerConfigs);
    }

    /**
     * Creates a new TokenValidator with the given issuer configurations and parser configuration.
     *
     * @param config        configuration for the parser, must not be null
     * @param issuerConfigs varargs of issuer configurations, must not be null and must contain at least one configuration
     */
    public TokenValidator(@NonNull ParserConfig config, @NonNull IssuerConfig... issuerConfigs) {
        LOGGER.debug("Initialize token validator with %s and %s issuer configurations", config, issuerConfigs.length);
        this.securityEventCounter = new SecurityEventCounter();
        this.jwtParser = NonValidatingJwtParser.builder()
                .config(config)
                .securityEventCounter(securityEventCounter)
                .build();

        ConcurrentLinkedQueue<IssuerConfig> enabledConfigs = new ConcurrentLinkedQueue<>();
        int enabledCount = 0;
        for (IssuerConfig issuerConfig : issuerConfigs) {
            // Only process enabled issuers (constructor filtering as per I1 requirements)
            if (issuerConfig.isEnabled()) {
                // Initialize the JwksLoader with the SecurityEventCounter
                issuerConfig.initSecurityEventCounter(securityEventCounter);
                enabledConfigs.add(issuerConfig);
                enabledCount++;
                LOGGER.debug("Added enabled issuer configuration");
            } else {
                LOGGER.info(JWTValidationLogMessages.INFO.ISSUER_CONFIG_SKIPPED.format(issuerConfig));
            }
        }
        this.issuerConfigs = enabledConfigs;

        LOGGER.debug("Created TokenValidator with %s enabled issuer configurations (%s total)", enabledCount, issuerConfigs.length);
        LOGGER.info(JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED.format(enabledCount));
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
                (decodedJwt, issuerConfig) -> new TokenBuilder(issuerConfig).createAccessToken(decodedJwt)
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
                (decodedJwt, issuerConfig) -> new TokenBuilder(issuerConfig).createIdToken(decodedJwt)
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
     *
     * @param tokenString  the token string to process
     * @param tokenBuilder function to build the token from the decoded JWT and issuer config
     * @param <T>          the type of token to create
     * @return the validated token
     * @throws TokenValidationException if validation fails
     */
    private <T extends TokenContent> T processTokenPipeline(
            String tokenString,
            TokenBuilderFunction<T> tokenBuilder) {

        validateTokenFormat(tokenString);
        DecodedJwt decodedJwt = decodeToken(tokenString);
        String issuer = validateAndExtractIssuer(decodedJwt);
        IssuerConfig issuerConfig = resolveIssuerConfig(issuer);

        validateTokenHeader(decodedJwt, issuerConfig);
        validateTokenSignature(decodedJwt, issuerConfig);
        T token = buildToken(decodedJwt, issuerConfig, tokenBuilder);
        T validatedToken = validateTokenClaims(token, issuerConfig);

        LOGGER.debug("Token successfully validated");
        return validatedToken;
    }

    private void validateTokenFormat(String tokenString) {
        if (MoreStrings.isBlank(tokenString)) {
            LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY::format);
            securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EMPTY);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.TOKEN_EMPTY,
                    "Token is empty or null"
            );
        }
    }

    private DecodedJwt decodeToken(String tokenString) {
        return jwtParser.decode(tokenString);
    }

    private String validateAndExtractIssuer(DecodedJwt decodedJwt) {
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
    }

    /**
     * Resolves the appropriate issuer configuration for the given issuer.
     * <p>
     * This method implements a performance optimization strategy with thread safety:
     * <ol>
     *   <li>First checks the cache for already-validated configurations</li>
     *   <li>If not found, uses synchronized access to process issuer configs</li>
     *   <li>Double-check pattern to avoid unnecessary synchronization</li>
     *   <li>Once all configs are processed, optimizes the cache for read-only access</li>
     *   <li>Returns the resolved configuration for the specific issuer</li>
     * </ol>
     * <p>
     * Thread Safety:
     * This method is thread-safe using selective synchronization. The fast path
     * (cache hit) is lock-free for optimal performance. Only cache misses when
     * not yet optimized require synchronization.
     * <p>
     * Performance:
     * Optimized for read-heavy workloads with minimal synchronization overhead.
     * </p>
     *
     * @param issuer the issuer to resolve configuration for
     * @return the issuer configuration if healthy and available
     * @throws TokenValidationException if no configuration found or issuer is unhealthy
     */
    private IssuerConfig resolveIssuerConfig(String issuer) {
        // Check cache first for performance (lock-free fast path)
        IssuerConfig cachedConfig = validatedIssuerConfig.get().get(issuer);
        if (cachedConfig != null) {
            LOGGER.debug("Using cached issuer config for: %s", issuer);
            return cachedConfig;
        }

        // Use synchronized block only for the critical section
        synchronized (this) {
            // Double-check pattern: recheck cache after acquiring lock
            cachedConfig = validatedIssuerConfig.get().get(issuer);
            if (cachedConfig != null) {
                LOGGER.debug("Using cached issuer config after sync for: %s", issuer);
                return cachedConfig;
            }

            // Only proceed if not yet optimized
            if (!isOptimized.get()) {
                // Scan and process issuer configs
                Iterator<IssuerConfig> iterator = issuerConfigs.iterator();
                while (iterator.hasNext()) {
                    IssuerConfig issuerConfig = iterator.next();

                    // Check if the issuer config is healthy
                    if (LoaderStatus.OK.equals(issuerConfig.isHealthy())) {
                        // Found healthy issuer config, cache it and remove from queue
                        validatedIssuerConfig.get().put(issuerConfig.getIssuerIdentifier(), issuerConfig);
                        iterator.remove(); // Thread-safe removal during iteration
                        LOGGER.debug("Found healthy issuer config, cached and removed from queue for: %s", issuer);
                    } else {
                        LOGGER.warn(JWTValidationLogMessages.WARN.ISSUER_CONFIG_UNHEALTHY.format(issuer));
                    }
                }

                // Optimize only after all processing is complete
                if (issuerConfigs.isEmpty()) {
                    optimizeForReadOnlyAccess();
                    isOptimized.set(true);
                    LOGGER.debug("Issuer config cache optimized for read-only access");
                }
            }
        }

        // Final resolution after synchronized processing
        IssuerConfig resolvedConfig = validatedIssuerConfig.get().get(issuer);
        if (resolvedConfig != null) {
            LOGGER.debug("Using resolved issuer config for: %s", issuer);
            return resolvedConfig;
        }

        // No healthy matching issuer configuration found
        LOGGER.warn(JWTValidationLogMessages.WARN.NO_ISSUER_CONFIG.format(issuer));
        securityEventCounter.increment(SecurityEventCounter.EventType.NO_ISSUER_CONFIG);
        throw new TokenValidationException(
                SecurityEventCounter.EventType.NO_ISSUER_CONFIG,
                "No healthy issuer configuration found for issuer: " + issuer
        );
    }

    private void validateTokenHeader(DecodedJwt decodedJwt, IssuerConfig issuerConfig) {
        TokenHeaderValidator headerValidator = new TokenHeaderValidator(issuerConfig, securityEventCounter);
        headerValidator.validate(decodedJwt);
    }

    private void validateTokenSignature(DecodedJwt decodedJwt, IssuerConfig issuerConfig) {
        JwksLoader jwksLoader = issuerConfig.getJwksLoader();
        TokenSignatureValidator signatureValidator = new TokenSignatureValidator(jwksLoader, securityEventCounter);
        signatureValidator.validateSignature(decodedJwt);
    }

    @NonNull
    private <T extends TokenContent> T buildToken(
            DecodedJwt decodedJwt,
            IssuerConfig issuerConfig,
            TokenBuilderFunction<T> tokenBuilder) {
        Optional<T> token = tokenBuilder.apply(decodedJwt, issuerConfig);
        if (token.isEmpty()) {
            LOGGER.debug("Token building failed");
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Failed to build token from decoded JWT"
            );
        }
        return token.get();
    }

    @SuppressWarnings("unchecked")
    private <T extends TokenContent> T validateTokenClaims(TokenContent token, IssuerConfig issuerConfig) {
        TokenClaimValidator claimValidator = new TokenClaimValidator(issuerConfig, securityEventCounter);
        TokenContent validatedContent = claimValidator.validate(token);
        return (T) validatedContent;
    }

    /**
     * Optimizes the issuer config cache for read-only access when all configs are processed.
     * <p>
     * Once the issuerConfigs queue is empty, no more writes to validatedIssuerConfig will occur.
     * At this point, we can safely replace the ConcurrentHashMap with a high-performance
     * immutable map that offers faster reads and lower memory overhead.
     * </p>
     * <p>
     * Performance benefits:
     * <ul>
     *   <li>Read latency: ~5-10ns (vs ~20-50ns for ConcurrentHashMap)</li>
     *   <li>Memory overhead: ~20-30% reduction</li>
     *   <li>No synchronization overhead for reads</li>
     *   <li>Better CPU cache performance</li>
     * </ul>
     * </p>
     */
    private void optimizeForReadOnlyAccess() {
        if (issuerConfigs.isEmpty()) {
            // Modern lock-free optimization using atomic compare-and-swap
            Map<String, IssuerConfig> currentMap = validatedIssuerConfig.get();
            if (currentMap instanceof ConcurrentHashMap && validatedIssuerConfig.compareAndSet(currentMap, Map.copyOf(currentMap))) {
                LOGGER.debug("Optimized issuer config cache for read-only access with {} entries", currentMap.size());
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
