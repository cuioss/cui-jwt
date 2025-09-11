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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.client.ETagAwareHttpHandler;
import de.cuioss.tools.net.http.client.LoaderStatus;
import de.cuioss.tools.net.http.converter.HttpContentConverter;
import de.cuioss.tools.net.http.result.HttpResultObject;
import de.cuioss.jwt.validation.json.Jwks;
import org.jspecify.annotations.NonNull;

import java.net.URL;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP-based implementation of WellKnownResolver that discovers OIDC endpoints.
 * <p>
 * This class provides lazy loading of well-known endpoints with built-in retry logic
 * and health checking capabilities. It follows the same pattern as HttpJwksLoader
 * with thread-safe operations and status reporting.
 * <p>
 * Features:
 * <ul>
 *   <li>Lazy loading - HTTP requests are deferred until first access</li>
 *   <li>Thread-safe initialization using double-checked locking</li>
 *   <li>Built-in retry logic for transient failures</li>
 *   <li>Health checking with status reporting</li>
 *   <li>Caching of successfully resolved endpoints</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class HttpWellKnownResolver implements WellKnownResolver {

    private static final CuiLogger LOGGER = new CuiLogger(HttpWellKnownResolver.class);

    private static final String ISSUER_KEY = "issuer";
    private static final String JWKS_URI_KEY = "jwks_uri";
    private static final String AUTHORIZATION_ENDPOINT_KEY = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT_KEY = "token_endpoint";
    private static final String USERINFO_ENDPOINT_KEY = "userinfo_endpoint";

    private final URL wellKnownUrl;
    private final ETagAwareHttpHandler<de.cuioss.jwt.validation.json.WellKnownConfiguration> discoveryEtagHandler;
    private final ETagAwareHttpHandler<Jwks> jwksEtagHandler;
    private final WellKnownParser parser;
    private final WellKnownEndpointMapper mapper;

    private final Map<String, HttpHandler> endpoints = new ConcurrentHashMap<>();
    private volatile String issuerIdentifier;
    private volatile LoaderStatus status = LoaderStatus.UNDEFINED;

    /**
     * Creates a new HTTP well-known resolver from WellKnownConfig.
     *
     * @param config the well-known configuration containing HTTP handler and parser settings
     */
    public HttpWellKnownResolver(@NonNull WellKnownConfig config) {
        HttpHandler httpHandler = config.getHttpHandler();
        this.wellKnownUrl = httpHandler.getUrl();
        this.discoveryEtagHandler = new ETagAwareHttpHandler<>(config, new WellKnownConfigurationConverter(config.getParserConfig().getDslJson()));
        // Create Jwks converter for JWKS using DSL-JSON directly
        var dslJson = config.getParserConfig().getDslJson();
        HttpContentConverter<Jwks> jwksHttpConverter = new HttpContentConverter<Jwks>() {
            @Override
            public Optional<Jwks> convert(Object rawContent) {
                String body = (rawContent instanceof String s) ? s :
                        (rawContent != null) ? rawContent.toString() : null;
                if (body == null || body.trim().isEmpty()) {
                    return Optional.of(emptyValue());
                }
                try {
                    // Use DSL-JSON to parse to Jwks
                    byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                    Jwks jwks = dslJson.deserialize(Jwks.class, bodyBytes, bodyBytes.length);
                    return Optional.ofNullable(jwks);
                } catch (java.io.IOException | IllegalArgumentException e) {
                    return Optional.empty();
                }
            }

            @Override
            public HttpResponse.BodyHandler<?> getBodyHandler() {
                return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
            }

            @Override
            public Jwks emptyValue() {
                return Jwks.empty();
            }
        };
        this.jwksEtagHandler = new ETagAwareHttpHandler<>(config, jwksHttpConverter);
        this.parser = new WellKnownParser(config.getParserConfig());
        this.mapper = new WellKnownEndpointMapper(httpHandler);
        LOGGER.debug("Created HttpWellKnownResolver for URL: %s (not yet loaded)", wellKnownUrl);
    }

    @Override
    public Optional<HttpHandler> getJwksUri() {
        ensureLoaded();
        return Optional.ofNullable(endpoints.get(JWKS_URI_KEY));
    }


    @Override
    public Optional<ETagAwareHttpHandler<Jwks>> getJwksETagHandler() {
        ensureLoaded();
        if (endpoints.containsKey(JWKS_URI_KEY) && status == LoaderStatus.OK) {
            return Optional.of(jwksEtagHandler);
        }
        return Optional.empty();
    }

    @Override
    public Optional<HttpHandler> getAuthorizationEndpoint() {
        ensureLoaded();
        return Optional.ofNullable(endpoints.get(AUTHORIZATION_ENDPOINT_KEY));
    }

    @Override
    public Optional<HttpHandler> getTokenEndpoint() {
        ensureLoaded();
        return Optional.ofNullable(endpoints.get(TOKEN_ENDPOINT_KEY));
    }

    @Override
    public Optional<HttpHandler> getUserinfoEndpoint() {
        ensureLoaded();
        return Optional.ofNullable(endpoints.get(USERINFO_ENDPOINT_KEY));
    }

    @Override
    public Optional<String> getIssuer() {
        ensureLoaded();
        return Optional.ofNullable(issuerIdentifier);
    }

    @Override
    public LoaderStatus isHealthy() {
        if (endpoints.isEmpty()) {
            ensureLoaded();
        }
        return status;
    }


    private void ensureLoaded() {
        if (endpoints.isEmpty() && issuerIdentifier == null) {
            loadEndpointsIfNeeded();
        }
    }

    private void loadEndpointsIfNeeded() {
        // Double-checked locking pattern with ConcurrentHashMap
        if (endpoints.isEmpty() && issuerIdentifier == null) {
            synchronized (this) {
                if (endpoints.isEmpty() && issuerIdentifier == null) {
                    loadEndpoints();
                }
            }
        }
    }

    private void loadEndpoints() {
        LOGGER.debug("Loading well-known endpoints from %s", wellKnownUrl);

        // Fetch discovery document (directly parsed as WellKnownConfiguration)
        HttpResultObject<de.cuioss.jwt.validation.json.WellKnownConfiguration> result = discoveryEtagHandler.load();
        if (!result.isValid() || result.getResult() == null) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        de.cuioss.jwt.validation.json.WellKnownConfiguration discoveryDocument = result.getResult();
        LOGGER.debug("Discovery document load state: %s", result.getState());
        LOGGER.debug(JWTValidationLogMessages.DEBUG.DISCOVERY_DOCUMENT_FETCHED.format(discoveryDocument));

        Map<String, HttpHandler> parsedEndpoints = new HashMap<>();

        // Parse all endpoints - using direct typed access instead of JSON parsing
        String issuerString = discoveryDocument.issuer;
        if (issuerString == null || issuerString.trim().isEmpty()) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        if (!parser.validateIssuer(issuerString, wellKnownUrl)) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        // JWKS URI (Required)
        if (!mapper.addHttpHandlerToMap(parsedEndpoints, JWKS_URI_KEY,
                discoveryDocument.jwksUri, wellKnownUrl, true)) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        // Required endpoints
        if (!mapper.addHttpHandlerToMap(parsedEndpoints, AUTHORIZATION_ENDPOINT_KEY,
                discoveryDocument.authorizationEndpoint, wellKnownUrl, true)) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        if (!mapper.addHttpHandlerToMap(parsedEndpoints, TOKEN_ENDPOINT_KEY,
                discoveryDocument.tokenEndpoint, wellKnownUrl, true)) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        // Optional endpoints
        mapper.addHttpHandlerToMap(parsedEndpoints, USERINFO_ENDPOINT_KEY,
                discoveryDocument.userinfoEndpoint, wellKnownUrl, false);

        // Accessibility check for jwks_uri
        mapper.performAccessibilityCheck(JWKS_URI_KEY, parsedEndpoints.get(JWKS_URI_KEY));

        // Success - save the endpoints and issuer identifier
        this.endpoints.clear();
        this.endpoints.putAll(parsedEndpoints);
        this.issuerIdentifier = issuerString;
        this.status = LoaderStatus.OK;

        LOGGER.info(JWTValidationLogMessages.INFO.WELL_KNOWN_ENDPOINTS_LOADED.format(wellKnownUrl));
    }
}