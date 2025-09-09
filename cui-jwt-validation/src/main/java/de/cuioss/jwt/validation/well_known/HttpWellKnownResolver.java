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
import de.cuioss.tools.net.http.result.HttpResultObject;
import jakarta.json.JsonObject;
import lombok.NonNull;

import java.net.URL;
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
    private final ETagAwareHttpHandler<String> etagHandler;
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
        this.etagHandler = ETagAwareHttpHandler.forString(config); // Using HttpHandlerProvider pattern
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

        // Fetch and parse discovery document
        HttpResultObject<String> result = etagHandler.load();
        if (!result.isValid() || result.getResult() == null) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        Optional<JsonObject> parseResult = parser.parseJsonResponse(result.getResult(), wellKnownUrl);
        if (parseResult.isEmpty()) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        JsonObject discoveryDocument = parseResult.get();
        LOGGER.debug("Discovery document load state: %s", result.getState());
        LOGGER.debug(JWTValidationLogMessages.DEBUG.DISCOVERY_DOCUMENT_FETCHED.format(discoveryDocument));

        Map<String, HttpHandler> parsedEndpoints = new HashMap<>();

        // Parse all endpoints
        String issuerString = parser.getString(discoveryDocument, ISSUER_KEY)
                .orElse(null);
        if (issuerString == null) {
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
                parser.getString(discoveryDocument, JWKS_URI_KEY).orElse(null), wellKnownUrl, true)) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        // Required endpoints
        if (!mapper.addHttpHandlerToMap(parsedEndpoints, AUTHORIZATION_ENDPOINT_KEY,
                parser.getString(discoveryDocument, AUTHORIZATION_ENDPOINT_KEY).orElse(null), wellKnownUrl, true)) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        if (!mapper.addHttpHandlerToMap(parsedEndpoints, TOKEN_ENDPOINT_KEY,
                parser.getString(discoveryDocument, TOKEN_ENDPOINT_KEY).orElse(null), wellKnownUrl, true)) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(JWTValidationLogMessages.ERROR.WELL_KNOWN_LOAD_FAILED.format(wellKnownUrl, 1));
            return;
        }

        // Optional endpoints
        mapper.addHttpHandlerToMap(parsedEndpoints, USERINFO_ENDPOINT_KEY,
                parser.getString(discoveryDocument, USERINFO_ENDPOINT_KEY).orElse(null), wellKnownUrl, false);

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