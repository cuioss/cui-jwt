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
package de.cuioss.jwt.validation.jwks.http;

import de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;
import de.cuioss.jwt.validation.well_known.HttpWellKnownResolver;
import de.cuioss.jwt.validation.well_known.WellKnownConfig;
import de.cuioss.jwt.validation.well_known.WellKnownResolver;
import de.cuioss.tools.base.Preconditions;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.SecureSSLContextProvider;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Configuration parameters for {@link HttpJwksLoader}.
 * <p>
 * This class encapsulates configuration options for the HttpJwksLoader,
 * including JWKS endpoint URL, refresh interval, SSL context, and
 * background refresh parameters. The JWKS endpoint URL can be configured
 * directly or discovered via a {@link WellKnownResolver}.
 * <p>
 * Complex caching parameters (maxCacheSize, adaptiveWindowSize) have been
 * removed for simplification while keeping essential refresh functionality.
 * <p>
 * For more detailed information about the HTTP-based JWKS loading, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#_jwksloader">Technical Components Specification</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class HttpJwksLoaderConfig {

    private static final CuiLogger LOGGER = new CuiLogger(HttpJwksLoaderConfig.class);

    /**
     * A Default of 10 minutes (600 seconds).
     */
    private static final int DEFAULT_REFRESH_INTERVAL_IN_SECONDS = 60 * 10;

    /**
     * The interval in seconds at which to refresh the keys.
     * If set to 0, no time-based caching will be used.
     * It defaults to 10 minutes (600 seconds).
     */
    @Getter
    private final int refreshIntervalSeconds;

    /**
     * The HttpHandler used for HTTP requests.
     * <p>
     * This field is guaranteed to be non-null when {@code getJwksType() == JwksType.HTTP}.
     * It will be null only when using WellKnownResolver (i.e., {@code getJwksType() == JwksType.WELL_KNOWN}).
     * <p>
     * The non-null contract for HTTP configurations is enforced by the {@link HttpJwksLoaderConfigBuilder#build()}
     * method, which validates that the HttpHandler was successfully created before constructing the config.
     */
    @Getter
    @EqualsAndHashCode.Exclude
    private final HttpHandler httpHandler;

    /**
     * The WellKnownResolver used for well-known endpoint discovery.
     * Will be null if using direct HttpHandler.
     */
    @Getter
    @EqualsAndHashCode.Exclude
    private final WellKnownResolver wellKnownResolver;

    /**
     * The ScheduledExecutorService used for background refresh operations.
     * Can be null if no background refresh is needed.
     */
    @Getter
    @EqualsAndHashCode.Exclude
    private final ScheduledExecutorService scheduledExecutorService;


    /**
     * Creates a new builder for HttpJwksLoaderConfig.
     * <p>
     * This method provides a convenient way to create a new instance of
     * HttpJwksLoaderConfigBuilder, allowing for fluent configuration of the
     * HttpJwksLoaderConfig parameters.
     *
     * @return a new HttpJwksLoaderConfigBuilder instance
     */
    public static HttpJwksLoaderConfigBuilder builder() {
        return new HttpJwksLoaderConfigBuilder();
    }

    /**
     * Enum to track which endpoint configuration method was used.
     */
    private enum EndpointSource {
        JWKS_URI,
        JWKS_URL,
        WELL_KNOWN_URL,
        WELL_KNOWN_URI
    }

    /**
     * Builder for creating HttpJwksLoaderConfig instances with validation.
     */
    public static class HttpJwksLoaderConfigBuilder {
        private Integer refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_IN_SECONDS;
        private final HttpHandler.HttpHandlerBuilder httpHandlerBuilder;
        private ScheduledExecutorService scheduledExecutorService;
        private WellKnownConfig wellKnownConfig;

        // Track which endpoint configuration method was used to ensure mutual exclusivity
        private EndpointSource endpointSource = null;

        /**
         * Constructor initializing the HttpHandlerBuilder.
         */
        public HttpJwksLoaderConfigBuilder() {
            this.httpHandlerBuilder = HttpHandler.builder();
        }

        /**
         * Sets the JWKS URI directly.
         * <p>
         * This method is mutually exclusive with {@link #jwksUrl(String)}, {@link #wellKnownUrl(String)}, and {@link #wellKnownUri(URI)}.
         * Only one endpoint configuration method can be used per builder instance.
         * </p>
         *
         * @param jwksUri the URI of the JWKS endpoint. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         */
        public HttpJwksLoaderConfigBuilder jwksUri(@NonNull URI jwksUri) {
            validateEndpointExclusivity(EndpointSource.JWKS_URI);
            this.endpointSource = EndpointSource.JWKS_URI;
            httpHandlerBuilder.uri(jwksUri);
            return this;
        }

        /**
         * Sets the JWKS URL as a string, which will be converted to a URI.
         * <p>
         * This method is mutually exclusive with {@link #jwksUri(URI)}, {@link #wellKnownUrl(String)}, and {@link #wellKnownUri(URI)}.
         * Only one endpoint configuration method can be used per builder instance.
         * </p>
         *
         * @param jwksUrl the URL string of the JWKS endpoint. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         */
        public HttpJwksLoaderConfigBuilder jwksUrl(@NonNull String jwksUrl) {
            validateEndpointExclusivity(EndpointSource.JWKS_URL);
            this.endpointSource = EndpointSource.JWKS_URL;
            httpHandlerBuilder.url(jwksUrl);
            return this;
        }

        /**
         * Configures the JWKS loading using well-known endpoint discovery from a URL string.
         * <p>
         * This method creates a {@link WellKnownConfig} internally for dynamic JWKS URI resolution.
         * The JWKS URI will be extracted at runtime from the well-known discovery document.
         * </p>
         * <p>
         * This method is mutually exclusive with {@link #jwksUri(URI)}, {@link #jwksUrl(String)}, and {@link #wellKnownUri(URI)}.
         * Only one endpoint configuration method can be used per builder instance.
         * </p>
         *
         * @param wellKnownUrl The well-known discovery endpoint URL string. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         * @throws IllegalArgumentException if {@code wellKnownUrl} is null or invalid
         */
        public HttpJwksLoaderConfigBuilder wellKnownUrl(@NonNull String wellKnownUrl) {
            validateEndpointExclusivity(EndpointSource.WELL_KNOWN_URL);
            this.endpointSource = EndpointSource.WELL_KNOWN_URL;
            this.wellKnownConfig = WellKnownConfig.builder()
                    .wellKnownUrl(wellKnownUrl)
                    .build();
            return this;
        }

        /**
         * Configures the JWKS loading using well-known endpoint discovery from a URI.
         * <p>
         * This method creates a {@link WellKnownConfig} internally for dynamic JWKS URI resolution.
         * The JWKS URI will be extracted at runtime from the well-known discovery document.
         * </p>
         * <p>
         * This method is mutually exclusive with {@link #jwksUri(URI)}, {@link #jwksUrl(String)}, and {@link #wellKnownUrl(String)}.
         * Only one endpoint configuration method can be used per builder instance.
         * </p>
         *
         * @param wellKnownUri The well-known discovery endpoint URI. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         * @throws IllegalArgumentException if {@code wellKnownUri} is null
         */
        public HttpJwksLoaderConfigBuilder wellKnownUri(@NonNull URI wellKnownUri) {
            validateEndpointExclusivity(EndpointSource.WELL_KNOWN_URI);
            this.endpointSource = EndpointSource.WELL_KNOWN_URI;
            this.wellKnownConfig = WellKnownConfig.builder()
                    .wellKnownUri(wellKnownUri)
                    .build();
            return this;
        }

        /**
         * Sets the TLS versions configuration.
         *
         * @param secureSSLContextProvider the TLS versions configuration to use
         * @return this builder instance
         */
        public HttpJwksLoaderConfigBuilder tlsVersions(SecureSSLContextProvider secureSSLContextProvider) {
            httpHandlerBuilder.tlsVersions(secureSSLContextProvider);
            return this;
        }

        /**
         * Sets the refresh interval in seconds.
         * <p>
         * If set to 0, no time-based caching will be used. It defaults to 10 minutes (600 seconds).
         * </p>
         *
         * @param refreshIntervalSeconds the refresh interval in seconds
         * @return this builder instance
         * @throws IllegalArgumentException if a refresh interval is negative
         */
        public HttpJwksLoaderConfigBuilder refreshIntervalSeconds(int refreshIntervalSeconds) {
            Preconditions.checkArgument(refreshIntervalSeconds > -1, "refreshIntervalSeconds must be zero or positive");
            this.refreshIntervalSeconds = refreshIntervalSeconds;
            return this;
        }


        /**
         * Sets the SSL context to use for HTTPS connections.
         * <p>
         * If not set, a default secure SSL context will be created.
         * </p>
         *
         * @param sslContext The SSL context to use.
         * @return This builder instance.
         */
        public HttpJwksLoaderConfigBuilder sslContext(SSLContext sslContext) {
            httpHandlerBuilder.sslContext(sslContext);
            return this;
        }

        /**
         * Sets the connection timeout in seconds.
         *
         * @param connectTimeoutSeconds the connection timeout in seconds
         * @return this builder instance
         * @throws IllegalArgumentException if connectTimeoutSeconds is not positive
         */
        public HttpJwksLoaderConfigBuilder connectTimeoutSeconds(int connectTimeoutSeconds) {
            Preconditions.checkArgument(connectTimeoutSeconds > 0, "connectTimeoutSeconds must be > 0, but was %s", connectTimeoutSeconds);
            httpHandlerBuilder.connectionTimeoutSeconds(connectTimeoutSeconds);
            return this;
        }

        /**
         * Sets the read timeout in seconds.
         *
         * @param readTimeoutSeconds the read timeout in seconds
         * @return this builder instance
         * @throws IllegalArgumentException if readTimeoutSeconds is not positive
         */
        public HttpJwksLoaderConfigBuilder readTimeoutSeconds(int readTimeoutSeconds) {
            Preconditions.checkArgument(readTimeoutSeconds > 0, "readTimeoutSeconds must be > 0, but was %s", readTimeoutSeconds);
            httpHandlerBuilder.readTimeoutSeconds(readTimeoutSeconds);
            return this;
        }

        /**
         * Sets the ScheduledExecutorService for background refresh operations.
         *
         * @param scheduledExecutorService the executor service to use
         * @return this builder instance
         */
        public HttpJwksLoaderConfigBuilder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            this.scheduledExecutorService = scheduledExecutorService;
            return this;
        }

        /**
         * Validates that the proposed endpoint source doesn't conflict with an already configured one.
         * <p>
         * This validation ensures mutual exclusivity between direct JWKS endpoint configuration
         * and well-known discovery configuration. When using well-known discovery, the issuer identifier
         * is automatically provided by the discovery document and cannot be manually configured.
         * </p>
         *
         * @param proposedSource the endpoint source that is being configured
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         */
        private void validateEndpointExclusivity(EndpointSource proposedSource) {
            if (endpointSource != null && endpointSource != proposedSource) {
                throw new IllegalArgumentException(
                        ("Cannot use %s endpoint configuration when %s was already configured. " +
                                "Methods jwksUri(), jwksUrl(), wellKnownUrl(), and wellKnownUri() are mutually exclusive. " +
                                "When using well-known discovery, the issuer identifier is automatically provided by the discovery document.")
                                .formatted(proposedSource.name().toLowerCase().replace("_", ""), endpointSource.name().toLowerCase().replace("_", "")));
            }
        }

        /**
         * Builds a new HttpJwksLoaderConfig instance with the configured parameters.
         * Validates all parameters and applies default values where appropriate.
         *
         * @return a new HttpJwksLoaderConfig instance
         * @throws IllegalArgumentException if any parameter is invalid
         * @throws IllegalArgumentException if no endpoint was configured
         */
        public HttpJwksLoaderConfig build() {
            // Ensure at least one endpoint configuration method was used
            if (endpointSource == null) {
                throw new IllegalArgumentException(
                        "No JWKS endpoint configured. Must call one of: jwksUri(), jwksUrl(), wellKnownUrl(), or wellKnownUri()");
            }

            HttpHandler jwksHttpHandler = null;
            WellKnownResolver configuredWellKnownResolver = null;

            if (endpointSource == EndpointSource.WELL_KNOWN_URL || endpointSource == EndpointSource.WELL_KNOWN_URI) {
                // Create WellKnownResolver from WellKnownConfig
                configuredWellKnownResolver = createWellKnownResolver(this.wellKnownConfig);
            } else {
                // Build the HttpHandler for direct URL/URI configuration
                try {
                    jwksHttpHandler = httpHandlerBuilder.build();
                    if (jwksHttpHandler == null) {
                        throw new IllegalArgumentException("HttpHandler build() returned null - this indicates a programming error in the builder");
                    }
                } catch (IllegalArgumentException | IllegalStateException e) {
                    LOGGER.warn(WARN.INVALID_JWKS_URI::format);
                    throw new IllegalArgumentException("Invalid URL or HttpHandler configuration", e);
                }
            }

            // Create default ScheduledExecutorService if not provided and refresh interval > 0
            ScheduledExecutorService executor = this.scheduledExecutorService;
            if (executor == null && refreshIntervalSeconds > 0) {
                String hostName = jwksHttpHandler != null ? jwksHttpHandler.getUri().getHost() : "wellknown";
                executor = Executors.newScheduledThreadPool(1, r -> {
                    Thread t = new Thread(r, "jwks-refresh-" + hostName);
                    t.setDaemon(true);
                    return t;
                });
            }

            return new HttpJwksLoaderConfig(
                    refreshIntervalSeconds,
                    jwksHttpHandler,
                    configuredWellKnownResolver,
                    executor);
        }

        /**
         * Creates a WellKnownResolver from the given WellKnownConfig.
         * Uses the WellKnownConfig which internally manages HttpHandler creation.
         *
         * @param config the WellKnownConfig to create the resolver from
         * @return a configured WellKnownResolver instance
         * @throws IllegalArgumentException if the configuration is invalid
         */
        private WellKnownResolver createWellKnownResolver(WellKnownConfig config) {
            return new HttpWellKnownResolver(config);
        }
    }
}