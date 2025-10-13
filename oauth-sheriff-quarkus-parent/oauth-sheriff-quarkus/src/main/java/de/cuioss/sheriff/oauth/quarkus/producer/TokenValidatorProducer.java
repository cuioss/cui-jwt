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
package de.cuioss.sheriff.oauth.quarkus.producer;

import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.sheriff.oauth.library.IssuerConfig;
import de.cuioss.sheriff.oauth.library.ParserConfig;
import de.cuioss.sheriff.oauth.library.TokenValidator;
import de.cuioss.sheriff.oauth.library.cache.AccessTokenCacheConfig;
import de.cuioss.sheriff.oauth.library.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.quarkus.config.AccessTokenCacheConfigResolver;
import de.cuioss.sheriff.oauth.quarkus.config.IssuerConfigResolver;
import de.cuioss.sheriff.oauth.quarkus.config.ParserConfigResolver;
import de.cuioss.sheriff.oauth.quarkus.config.RetryStrategyConfigResolver;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;

import java.util.List;

import static de.cuioss.sheriff.oauth.quarkus.CuiJwtQuarkusLogMessages.INFO;

/**
 * CDI producer for JWT validation related instances.
 * <p>
 * This producer creates and manages JWT validation components from
 * configuration properties. Components are initialized during startup
 * via {@link PostConstruct} and exposed through field-based producers.
 * </p>
 * <p>
 * Configuration resolution is delegated to dedicated resolver classes for
 * better separation of concerns, while validation is handled by the underlying
 * builders to avoid logic duplication.
 * </p>
 * <p>
 * Produced components:
 * <ul>
 *   <li>{@link TokenValidator} - Main JWT validation component (includes SecurityEventCounter)</li>
 *   <li>{@link List}&lt;{@link IssuerConfig}&gt; - Resolved issuer configurations</li>
 *   <li>{@link RetryStrategy} - HTTP retry strategy for resilient operations</li>
 * </ul>
 *
 * @since 1.0
 */
@ApplicationScoped
public class TokenValidatorProducer {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidatorProducer.class);

    private final Config config;

    @Produces
    @ApplicationScoped
    TokenValidator tokenValidator;

    @Produces
    @ApplicationScoped
    List<IssuerConfig> issuerConfigs;

    @Produces
    @ApplicationScoped
    SecurityEventCounter securityEventCounter;

    @Produces
    @ApplicationScoped
    RetryStrategy retryStrategy;

    @SuppressWarnings("java:S2637") // False positive: fields are initialized in @PostConstruct
    public TokenValidatorProducer(Config config) {
        this.config = config;
    }

    /**
     * Initializes all JWT validation components from configuration.
     * <p>
     * This method is called during CDI container startup and creates all
     * the validation components that will be exposed through the field producers.
     * </p>
     */
    @PostConstruct
    void init() {
        LOGGER.info(INFO.INITIALIZING_JWT_VALIDATION_COMPONENTS);

        // Create RetryStrategy from configuration
        RetryStrategyConfigResolver retryResolver = new RetryStrategyConfigResolver(config);
        retryStrategy = retryResolver.resolveRetryStrategy();

        // Resolve issuer configurations using the dedicated resolver
        // (Keycloak mappers are now configured per-issuer in IssuerConfigResolver)
        IssuerConfigResolver issuerConfigResolver = new IssuerConfigResolver(config, retryStrategy);
        issuerConfigs = issuerConfigResolver.resolveIssuerConfigs();

        // Create SecurityEventCounter for proper initialization
        SecurityEventCounter eventCounter = new SecurityEventCounter();

        // Initialize each IssuerConfig with SecurityEventCounter so JwksLoader can be used
        for (IssuerConfig issuerConfig : issuerConfigs) {
            issuerConfig.initSecurityEventCounter(eventCounter);
        }

        // Resolve parser config using the dedicated resolver
        ParserConfigResolver parserConfigResolver = new ParserConfigResolver(config);
        ParserConfig parserConfig = parserConfigResolver.resolveParserConfig();


        // Resolve cache config using the dedicated resolver
        AccessTokenCacheConfigResolver cacheConfigResolver = new AccessTokenCacheConfigResolver(config);
        AccessTokenCacheConfig cacheConfig = cacheConfigResolver.resolveCacheConfig();

        // Create TokenValidator using builder pattern - it handles internal initialization
        TokenValidator.TokenValidatorBuilder builder = TokenValidator.builder()
                .parserConfig(parserConfig)
                .cacheConfig(cacheConfig);

        // Add each issuer config to the builder
        for (IssuerConfig issuerConfig : issuerConfigs) {
            builder.issuerConfig(issuerConfig);
        }

        tokenValidator = builder.build();

        // Use the same SecurityEventCounter instance we used for initialization
        // Note: tokenValidator.getSecurityEventCounter() returns the same instance
        this.securityEventCounter = eventCounter;

        LOGGER.info(INFO.JWT_VALIDATION_COMPONENTS_INITIALIZED, issuerConfigs.size());
    }


}
