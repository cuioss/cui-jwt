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

import de.cuioss.jwt.validation.HealthStatusProvider;
import de.cuioss.jwt.validation.json.Jwks;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.client.ETagAwareHttpHandler;

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
     * Gets the JWKS URI endpoint as an ETag-aware handler with retry capabilities.
     * <p>
     * This method provides direct access to an optimized ETagAwareHttpHandler for JWKS loading,
     * eliminating the need for HttpJwksLoader to wrap the handler again.
     * <p>
     * The returned handler includes:
     * <ul>
     *   <li>ETag-based HTTP caching (304 Not Modified support)</li>
     *   <li>Retry logic with exponential backoff</li>
     *   <li>Jwks content conversion for optimal JWKS processing</li>
     *   <li>Security event tracking and validation</li>
     * </ul>
     *
     * @return Optional containing the ETag-aware JWKS handler, empty if not available or unhealthy
     */
    Optional<ETagAwareHttpHandler<Jwks>> getJwksETagHandler();

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
     * Gets the issuer identifier.
     * This represents the issuer identifier string from the discovery document.
     * According to OpenID Connect Core 1.0 Section 2, the issuer is a case-sensitive URL
     * used as a unique identifier for the authorization server, not an HTTP endpoint.
     *
     * @return Optional containing the issuer identifier string, empty if not available
     */
    Optional<String> getIssuer();
}