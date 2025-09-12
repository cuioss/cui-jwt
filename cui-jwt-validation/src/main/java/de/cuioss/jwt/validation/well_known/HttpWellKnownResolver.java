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

import de.cuioss.jwt.validation.json.WellKnownResult;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.client.LoaderStatus;
import de.cuioss.tools.net.http.client.LoadingStatusProvider;
import de.cuioss.tools.net.http.client.ResilientHttpHandler;
import de.cuioss.tools.net.http.result.HttpResultObject;

import java.util.Optional;

/**
 * HTTP-based implementation for resolving OpenID Connect well-known configuration endpoints.
 * <p>
 * This class provides a thin wrapper around {@link ResilientHttpHandler} for loading and
 * parsing well-known OIDC discovery documents. It handles HTTP operations, caching,
 * and provides convenient access to discovered endpoints.
 * <p>
 * The resolver loads the well-known configuration once and caches the result for
 * subsequent endpoint lookups. It provides methods to access common OIDC endpoints
 * like JWKS URI, issuer, authorization endpoint, etc.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class HttpWellKnownResolver implements LoadingStatusProvider {

    private static final CuiLogger LOGGER = new CuiLogger(HttpWellKnownResolver.class);

    private final ResilientHttpHandler<WellKnownResult> wellKnownHandler;
    private HttpResultObject<WellKnownResult> cachedResult;

    /**
     * Creates a new HttpWellKnownResolver with the specified configuration.
     *
     * @param config the well-known configuration containing HTTP handler and parser settings
     */
    public HttpWellKnownResolver(WellKnownConfig config) {
        var converter = new WellKnownConfigurationConverter(config.getParserConfig().getDslJson());
        this.wellKnownHandler = new ResilientHttpHandler<>(config.getHttpHandler(), converter);
        LOGGER.debug("Created HttpWellKnownResolver for well-known endpoint discovery");
    }

    /**
     * Ensures the well-known configuration is loaded and cached.
     *
     * @return Optional containing the WellKnownResult if available and valid, empty otherwise
     */
    private Optional<WellKnownResult> ensureLoaded() {
        if (cachedResult == null) {
            cachedResult = wellKnownHandler.load();
        }
        if (cachedResult.isValid() && cachedResult.getResult() != null) {
            return Optional.of(cachedResult.getResult());
        }
        return Optional.empty();
    }

    /**
     * Gets the JWKS URI from the well-known configuration.
     *
     * @return Optional containing the JWKS URI if available, empty otherwise
     */
    public Optional<String> getJwksUri() {
        return ensureLoaded().flatMap(WellKnownResult::getJwksUri);
    }

    /**
     * Gets the issuer from the well-known configuration.
     *
     * @return Optional containing the issuer if available, empty otherwise
     */
    public Optional<String> getIssuer() {
        return ensureLoaded().flatMap(WellKnownResult::getIssuer);
    }

    /**
     * Gets the authorization endpoint from the well-known configuration.
     *
     * @return Optional containing the authorization endpoint if available, empty otherwise
     */
    public Optional<String> getAuthorizationEndpoint() {
        return ensureLoaded().flatMap(WellKnownResult::getAuthorizationEndpoint);
    }

    /**
     * Gets the token endpoint from the well-known configuration.
     *
     * @return Optional containing the token endpoint if available, empty otherwise
     */
    public Optional<String> getTokenEndpoint() {
        return ensureLoaded().flatMap(WellKnownResult::getTokenEndpoint);
    }

    /**
     * Gets the userinfo endpoint from the well-known configuration.
     *
     * @return Optional containing the userinfo endpoint if available, empty otherwise
     */
    public Optional<String> getUserinfoEndpoint() {
        return ensureLoaded().flatMap(WellKnownResult::getUserinfoEndpoint);
    }

    /**
     * Gets the complete well-known configuration result.
     *
     * @return Optional containing the WellKnownResult if available, empty otherwise
     */
    public Optional<WellKnownResult> getWellKnownResult() {
        return ensureLoaded();
    }

    /**
     * Checks the health status of the well-known resolver.
     *
     * @return the current LoaderStatus indicating health state
     */
    @Override
    public LoaderStatus getLoaderStatus() {
        return wellKnownHandler.getLoaderStatus();
    }
}