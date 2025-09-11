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
package de.cuioss.jwt.validation.json;

import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.JsonAttribute;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Java representation of OpenID Connect Well-Known Configuration response.
 * <p>
 * This class represents the standard OpenID Connect Discovery 1.0 well-known configuration
 * document structure. It uses DSL-JSON's @CompiledJson for compile-time code generation,
 * enabling direct deserialization and optimal performance for native compilation.
 * <p>
 * Key fields from RFC 8414 and OpenID Connect Discovery:
 * <ul>
 *   <li><strong>issuer</strong>: Authorization server's identifier URL</li>
 *   <li><strong>jwks_uri</strong>: URL of the authorization server's JWK Set document</li>
 *   <li><strong>authorization_endpoint</strong>: URL of the authorization endpoint</li>
 *   <li><strong>token_endpoint</strong>: URL of the token endpoint</li>
 * </ul>
 * <p>
 * This implementation prioritizes the core fields needed for JWT validation while
 * maintaining extensibility for additional OpenID Connect discovery fields.
 *
 * @author Oliver Wolff
 * @since 1.0
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery</a>
 * @see <a href="https://tools.ietf.org/html/rfc8414">RFC 8414 - OAuth 2.0 Authorization Server Metadata</a>
 */
@CompiledJson
public class WellKnownConfiguration {

    /**
     * The authorization server's issuer identifier.
     * <p>
     * This is a URL that the authorization server uses as its identifier.
     * The issuer identifier is used to prevent authorization server mix-up attacks.
     * <p>
     * <strong>Required:</strong> Yes (per OpenID Connect Discovery spec)
     */
    @JsonAttribute(name = "issuer")
    public String issuer;

    /**
     * URL of the authorization server's JWK Set document.
     * <p>
     * This document contains the signing key(s) the authorization server uses
     * to sign JWTs. This URL MUST use the https scheme and MAY contain port,
     * path and query parameter components.
     * <p>
     * <strong>Required:</strong> Yes (per OpenID Connect Discovery spec)
     */
    @JsonAttribute(name = "jwks_uri")
    public String jwksUri;

    /**
     * URL of the authorization server's authorization endpoint.
     * <p>
     * This is where clients direct users for authentication and authorization.
     * May be null if the authorization server doesn't support the authorization endpoint.
     * <p>
     * <strong>Required:</strong> No (optional for client_credentials flow)
     */
    @JsonAttribute(name = "authorization_endpoint")
    @Nullable
    public String authorizationEndpoint;

    /**
     * URL of the authorization server's token endpoint.
     * <p>
     * This is where clients exchange authorization codes for tokens,
     * or where they directly obtain tokens via client credentials flow.
     * <p>
     * <strong>Required:</strong> No (but recommended for most OAuth 2.0 flows)
     */
    @JsonAttribute(name = "token_endpoint")
    @Nullable
    public String tokenEndpoint;

    /**
     * The userinfo endpoint URL.
     * This is optional according to OpenID Connect specifications.
     */
    @JsonAttribute(name = "userinfo_endpoint")
    @Nullable
    public String userinfoEndpoint;

    // Default constructor for DSL-JSON
    public WellKnownConfiguration() {
    }

    public WellKnownConfiguration(@NonNull String issuer, @NonNull String jwksUri,
            @Nullable String authorizationEndpoint, @Nullable String tokenEndpoint) {
        this.issuer = issuer;
        this.jwksUri = jwksUri;
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
    }

    // Getters
    public String issuer() {
        return issuer;
    }

    public String jwksUri() {
        return jwksUri;
    }

    public String authorizationEndpoint() {
        return authorizationEndpoint;
    }

    public String tokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * Sentinel value representing an empty/invalid well-known configuration.
     * <p>
     * This special value is used when HttpContentConverter needs to provide a non-null
     * empty value but no valid configuration can be created. It uses a recognizable
     * placeholder that clearly indicates this is not a real configuration.
     * <p>
     * <strong>This should never be used for actual OpenID Connect operations.</strong>
     */
    public static final WellKnownConfiguration EMPTY = new WellKnownConfiguration(
            "about:empty", // RFC 6694 - special URI for empty content
            "about:empty",
            null,
            null
    );

    /**
     * Creates a minimal Well-Known Configuration with only required fields.
     * <p>
     * This factory method creates a configuration with only the issuer and jwks_uri,
     * which are the minimum required fields for JWT validation scenarios.
     *
     * @param issuer the authorization server's issuer identifier
     * @param jwksUri the URL of the JWK Set document
     * @return a WellKnownConfiguration with minimal required fields
     */
    public static WellKnownConfiguration minimal(@NonNull String issuer, @NonNull String jwksUri) {
        return new WellKnownConfiguration(issuer, jwksUri, null, null);
    }

    /**
     * Checks if this configuration is the special empty sentinel value.
     *
     * @return true if this is the EMPTY sentinel value
     */
    public boolean isEmpty() {
        return this == EMPTY ||
                "about:empty".equals(issuer) ||
                (issuer == null && jwksUri == null);
    }

    /**
     * Checks if this configuration has all fields required for full OAuth 2.0 flows.
     * <p>
     * Returns true if both authorization_endpoint and token_endpoint are present,
     * indicating this server supports full authorization code flow.
     *
     * @return true if configuration supports full OAuth 2.0 flows
     */
    public boolean supportsFullOAuthFlows() {
        return authorizationEndpoint != null && tokenEndpoint != null;
    }

    /**
     * Checks if this configuration is minimal (contains only required JWT validation fields).
     * <p>
     * Returns true if only issuer and jwks_uri are present, which is sufficient
     * for JWT token validation scenarios.
     *
     * @return true if configuration contains only minimal JWT validation fields
     */
    public boolean isMinimal() {
        return authorizationEndpoint == null && tokenEndpoint == null;
    }
}