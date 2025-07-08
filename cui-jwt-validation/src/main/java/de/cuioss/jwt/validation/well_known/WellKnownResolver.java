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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.jwt.validation.HealthStatusProvider;
import de.cuioss.tools.net.http.HttpHandler;

import java.util.Optional;

/**
 * Interface for resolving OpenID Connect well-known endpoints.
 * <p>
 * This interface provides a contract for discovering and accessing OIDC provider metadata
 * from .well-known/openid-configuration endpoints. It follows the same pattern as
 * {@link de.cuioss.jwt.validation.jwks.JwksLoader} with health checking and status reporting.
 * <p>
 * Implementations should provide:
 * <ul>
 *   <li>Lazy loading of well-known endpoints</li>
 *   <li>Health checking capabilities</li>
 *   <li>Status reporting</li>
 *   <li>Thread-safe access to endpoints</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public interface WellKnownResolver extends HealthStatusProvider {

    /**
     * Gets the JWKS URI endpoint handler.
     * This endpoint is required by the OpenID Connect Discovery specification.
     *
     * @return Optional containing the JWKS URI HttpHandler, empty if not available
     */
    Optional<HttpHandler> getJwksUri();

    /**
     * Gets the authorization endpoint handler.
     * This endpoint is required by the OpenID Connect Discovery specification.
     *
     * @return Optional containing the authorization endpoint HttpHandler, empty if not available
     */
    Optional<HttpHandler> getAuthorizationEndpoint();

    /**
     * Gets the token endpoint handler.
     * This endpoint is required by the OpenID Connect Discovery specification.
     *
     * @return Optional containing the token endpoint HttpHandler, empty if not available
     */
    Optional<HttpHandler> getTokenEndpoint();

    /**
     * Gets the userinfo endpoint handler.
     * This endpoint is optional according to the OpenID Connect Discovery specification.
     *
     * @return Optional containing the userinfo endpoint HttpHandler, empty if not available
     */
    Optional<HttpHandler> getUserinfoEndpoint();

    /**
     * Gets the issuer endpoint handler.
     * This represents the issuer identifier from the discovery document.
     *
     * @return Optional containing the issuer HttpHandler, empty if not available
     */
    Optional<HttpHandler> getIssuer();
}