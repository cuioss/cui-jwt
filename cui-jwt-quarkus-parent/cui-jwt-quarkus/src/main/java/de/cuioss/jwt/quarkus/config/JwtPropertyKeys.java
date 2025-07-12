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
package de.cuioss.jwt.quarkus.config;

import de.cuioss.jwt.validation.jwks.JwksLoader;
import lombok.experimental.UtilityClass;

/**
 * Constants for JWT property keys used in the cui-jwt-quarkus module.
 * <p>
 * This class follows the DSL-style nested constants pattern to organize
 * property keys in a hierarchical, discoverable manner.
 * </p>
 * <p>
 * All properties are prefixed with "cui.jwt".
 * </p>
 *
 * @since 1.0
 */
@UtilityClass
public final class JwtPropertyKeys {

    /**
     * The common prefix for all JWT properties.
     */
    public static final String PREFIX = "cui.jwt";
    public static final String DOT_JWKS = ".jwks";

    /**
     * Properties related to JWT parser configuration.
     */
    @UtilityClass
    public static final class PARSER {
        /**
         * Base path for parser configurations.
         */
        public static final String BASE = PREFIX + ".parser";

        /**
         * Maximum size of a JWT token in bytes to prevent overflow attacks.
         */
        public static final String MAX_TOKEN_SIZE = BASE + ".max-token-size";

        /**
         * Maximum size of decoded JSON payload in bytes.
         */
        public static final String MAX_PAYLOAD_SIZE = BASE + ".max-payload-size";

        /**
         * Maximum string size for JSON parsing.
         */
        public static final String MAX_STRING_SIZE = BASE + ".max-string-size";

        /**
         * Maximum array size for JSON parsing.
         */
        public static final String MAX_ARRAY_SIZE = BASE + ".max-array-size";

        /**
         * Maximum depth for JSON parsing.
         */
        public static final String MAX_DEPTH = BASE + ".max-depth";
    }

    /**
     * Properties related to JWT issuers configuration.
     * <p>
     * These keys use a template system for dynamic issuer configuration.
     * Usage pattern: KEY.formatted("issuerName") where "issuerName" is an arbitrary string.
     * </p>
     * <p>
     * Example: ENABLED.formatted("default") -> "cui.jwt.issuers.default.enabled"
     * </p>
     * <p>
     * <strong>JWKS Source Configuration (Mutually Exclusive):</strong>
     * Only one JWKS source may be configured per issuer:
     * <ul>
     *   <li>{@link #JWKS_URL} - Direct JWKS endpoint (requires {@link #ISSUER_IDENTIFIER})</li>
     *   <li>{@link #WELL_KNOWN_URL} - Well-known discovery (provides issuer identifier automatically)</li>
     *   <li>{@link #JWKS_FILE_PATH} - Local file (requires {@link #ISSUER_IDENTIFIER})</li>
     *   <li>{@link #JWKS_CONTENT} - Inline content (requires {@link #ISSUER_IDENTIFIER})</li>
     * </ul>
     */
    @UtilityClass
    public static final class ISSUERS {
        /**
         * Base template for issuer configurations.
         */
        public static final String BASE = PREFIX + ".issuers.%s.";

        /**
         * Base template for JWKS configurations.
         */
        public static final String JWKS_BASE = BASE + "jwks.";

        /**
         * Base template for HTTP configurations.
         */
        public static final String HTTP_BASE = JWKS_BASE + "http.";

        // === Core Configuration ===

        /**
         * Whether this issuer configuration is enabled.
         * Template: "cui.jwt.issuers.%s.enabled"
         * <p>
         * When set to {@code false}, this issuer configuration will be ignored during
         * token validation and will not attempt to use the underlying {@link JwksLoader}.
         * This allows for easy enabling/disabling of specific issuers without removing
         * their configuration.
         * Default value is {@code true}.
         * </p>
         *
         * @see de.cuioss.jwt.validation.IssuerConfig
         */
        public static final String ENABLED = BASE + "enabled";

        /**
         * The issuer identifier that will be matched against the "iss" claim in JWT tokens.
         * Template: "cui.jwt.issuers.%s.issuer-identifier"
         * <p>
         * This field is required for all JWKS loading variants except well-known discovery.
         * For well-known discovery, the issuer identifier is automatically extracted from
         * the discovery document and this field is optional.
         * This identifier must match the "iss" claim in validated tokens.
         * </p>
         * <p>
         * <strong>Required</strong> for {@link #JWKS_URL}, {@link #JWKS_FILE_PATH}, and {@link #JWKS_CONTENT}.
         * <strong>Optional</strong> for {@link #WELL_KNOWN_URL} (extracted from discovery document).
         * </p>
         *
         * @see de.cuioss.jwt.validation.IssuerConfig
         */
        public static final String ISSUER_IDENTIFIER = BASE + "issuer-identifier";

        /**
         * Set of expected audience values (comma-separated).
         * Template: "cui.jwt.issuers.%s.expected-audience"
         * <p>
         * These values are matched against the "aud" claim in the token.
         * If the token's audience claim matches any of these values, it is considered valid.
         * </p>
         *
         * @see de.cuioss.jwt.validation.IssuerConfig
         */
        public static final String EXPECTED_AUDIENCE = BASE + "expected-audience";

        /**
         * Set of expected client ID values (comma-separated).
         * Template: "cui.jwt.issuers.%s.expected-client-id"
         * <p>
         * These values are matched against the "azp" or "client_id" claim in the token.
         * If the token's client ID claim matches any of these values, it is considered valid.
         * </p>
         *
         * @see de.cuioss.jwt.validation.IssuerConfig
         */
        public static final String EXPECTED_CLIENT_ID = BASE + "expected-client-id";

        /**
         * Signature algorithm preferences (comma-separated).
         * Template: "cui.jwt.issuers.%s.algorithm-preferences"
         * <p>
         * This configuration controls which signature algorithms are preferred and allowed
         * during token validation. It can be used to enforce security policies, such as
         * requiring stronger algorithms or blacklisting weak ones.
         * </p>
         *
         * @see de.cuioss.jwt.validation.IssuerConfig
         */
        public static final String ALGORITHM_PREFERENCES = BASE + "algorithm-preferences";

        // === JWKS Source Configuration (Mutually Exclusive) ===

        /**
         * The URL of the JWKS endpoint for direct loading.
         * Template: "cui.jwt.issuers.%s.jwks.http.url"
         * <p>
         * This method configures the issuer to load JWKS (JSON Web Key Set) from a remote HTTP endpoint.
         * This is the most common configuration for production environments where JWKS are served
         * by an identity provider or authorization server.
         * </p>
         * <p>
         * <strong>Mutually exclusive</strong> with {@link #WELL_KNOWN_URL}, {@link #JWKS_FILE_PATH}, and {@link #JWKS_CONTENT}.
         * <strong>Requires</strong> {@link #ISSUER_IDENTIFIER}.
         * </p>
         *
         * @see de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig
         */
        public static final String JWKS_URL = HTTP_BASE + "url";

        /**
         * The URL of the OpenID Connect discovery document (well-known endpoint).
         * Template: "cui.jwt.issuers.%s.jwks.http.well-known-url"
         * <p>
         * This method configures the JWKS loading using well-known endpoint discovery from a URL string.
         * This method creates a WellKnownConfig internally for dynamic JWKS URI resolution.
         * The JWKS URI will be extracted at runtime from the well-known discovery document.
         * </p>
         * <p>
         * <strong>Mutually exclusive</strong> with {@link #JWKS_URL}, {@link #JWKS_FILE_PATH}, and {@link #JWKS_CONTENT}.
         * Provides {@link #ISSUER_IDENTIFIER} automatically from discovery document.
         * </p>
         *
         * @see de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig
         */
        public static final String WELL_KNOWN_URL = HTTP_BASE + "well-known-url";

        /**
         * File path for loading JWKS from a local file.
         * Template: "cui.jwt.issuers.%s.jwks.file-path"
         * <p>
         * This method configures the issuer to load JWKS (JSON Web Key Set) from a local file.
         * This is useful for development environments, testing, or scenarios where JWKS are
         * distributed as part of the application deployment.
         * The file should contain a valid JWKS JSON structure with public keys only.
         * </p>
         * <p>
         * <strong>Mutually exclusive</strong> with {@link #JWKS_URL}, {@link #WELL_KNOWN_URL}, and {@link #JWKS_CONTENT}.
         * <strong>Requires</strong> {@link #ISSUER_IDENTIFIER}.
         * </p>
         *
         * @see de.cuioss.jwt.validation.IssuerConfig
         */
        public static final String JWKS_FILE_PATH = JWKS_BASE + "file-path";

        /**
         * JWKS content directly as a JSON string.
         * Template: "cui.jwt.issuers.%s.jwks.content"
         * <p>
         * This method configures the issuer to use JWKS (JSON Web Key Set) provided directly
         * as a JSON string. This is useful for testing, embedded configurations, or scenarios
         * where JWKS are generated or provided programmatically.
         * The content should be a valid JWKS JSON structure containing public keys only.
         * </p>
         * <p>
         * <strong>Mutually exclusive</strong> with {@link #JWKS_URL}, {@link #WELL_KNOWN_URL}, and {@link #JWKS_FILE_PATH}.
         * <strong>Requires</strong> {@link #ISSUER_IDENTIFIER}.
         * </p>
         *
         * @see de.cuioss.jwt.validation.IssuerConfig
         */
        public static final String JWKS_CONTENT = JWKS_BASE + "content";

        // === HTTP Configuration (Only for JWKS_URL and WELL_KNOWN_URL) ===

        /**
         * The refresh interval in seconds for HTTP-based JWKS loading.
         * Template: "cui.jwt.issuers.%s.jwks.http.refresh-interval-seconds"
         * <p>
         * The interval in seconds at which to refresh the keys.
         * If set to 0, no time-based caching will be used.
         * It defaults to 10 minutes (600 seconds).
         * </p>
         * <p>
         * <strong>Only applicable</strong> for {@link #JWKS_URL} and {@link #WELL_KNOWN_URL}.
         * </p>
         *
         * @see de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig
         */
        public static final String REFRESH_INTERVAL_SECONDS = HTTP_BASE + "refresh-interval-seconds";

        /**
         * The connection timeout in seconds for HTTP requests.
         * Template: "cui.jwt.issuers.%s.jwks.http.connect-timeout-seconds"
         * <p>
         * Sets the connection timeout in seconds for HTTP requests to JWKS endpoints.
         * This timeout controls how long to wait when establishing a connection to the remote server.
         * </p>
         * <p>
         * <strong>Only applicable</strong> for {@link #JWKS_URL} and {@link #WELL_KNOWN_URL}.
         * </p>
         *
         * @see de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig
         */
        public static final String CONNECT_TIMEOUT_SECONDS = HTTP_BASE + "connect-timeout-seconds";

        /**
         * The read timeout in seconds for HTTP requests.
         * Template: "cui.jwt.issuers.%s.jwks.http.read-timeout-seconds"
         * <p>
         * Sets the read timeout in seconds for HTTP requests to JWKS endpoints.
         * This timeout controls how long to wait for data to be received from the remote server
         * after the connection has been established.
         * </p>
         * <p>
         * <strong>Only applicable</strong> for {@link #JWKS_URL} and {@link #WELL_KNOWN_URL}.
         * </p>
         *
         * @see de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig
         */
        public static final String READ_TIMEOUT_SECONDS = HTTP_BASE + "read-timeout-seconds";
    }

    /**
     * Properties related to health checks.
     */
    @UtilityClass
    public static final class HEALTH {
        /**
         * Base path for health check configurations.
         */
        public static final String BASE = PREFIX + ".health";

        /**
         * Whether health checks are enabled.
         */
        public static final String ENABLED = BASE + ".enabled";

        /**
         * Properties related to JWKS endpoint health checks.
         */
        @UtilityClass
        public static final class JWKS {
            /**
             * Base path for JWKS health check configurations.
             */
            public static final String BASE = HEALTH.BASE + DOT_JWKS;

            /**
             * The cache time-to-live in seconds for health check results.
             */
            public static final String CACHE_SECONDS = BASE + ".cache-seconds";

            /**
             * The timeout in seconds for health check requests.
             */
            public static final String TIMEOUT_SECONDS = BASE + ".timeout-seconds";

        }
    }

    /**
     * Properties related to metrics.
     */
    @UtilityClass
    public static final class METRICS {
        /**
         * Base path for metrics configurations.
         */
        public static final String BASE = PREFIX + ".validation";

        /**
         * Counter for validation errors by type.
         */
        public static final String VALIDATION_ERRORS = BASE + ".errors";

        /**
         * Base path for JWKS metrics.
         */
        public static final String JWKS_BASE = PREFIX + DOT_JWKS;

        /**
         * Gauge for JWKS cache size.
         */
        public static final String JWKS_CACHE_SIZE = JWKS_BASE + ".cache.size";
    }

    /**
     * Properties related to Keycloak integration defaults.
     * <p>
     * These properties control optional claim mappers that provide compatibility
     * with Keycloak's default claim structure without requiring custom protocol mappers.
     * </p>
     */
    @UtilityClass
    public static final class KEYCLOAK {
        /**
         * Base path for Keycloak configurations.
         */
        public static final String BASE = PREFIX + ".keycloak";

        /**
         * Properties related to default claim mappers.
         */
        @UtilityClass
        public static final class MAPPERS {
            /**
             * Base path for mapper configurations.
             */
            public static final String BASE = KEYCLOAK.BASE + ".mappers";

            /**
             * Enable Keycloak default roles mapper for realm_access.roles claim.
             * Template: "cui.jwt.keycloak.mappers.default-roles.enabled"
             * <p>
             * When enabled, this mapper extracts roles from Keycloak's standard
             * {@code realm_access.roles} claim and maps them to the expected
             * {@code roles} claim format used by the CUI JWT library.
             * </p>
             * <p>
             * This eliminates the need for custom protocol mappers in Keycloak
             * to expose realm roles in the expected format.
             * </p>
             * <p>
             * Default value is {@code false}.
             * </p>
             *
             * @see de.cuioss.jwt.validation.domain.claim.mapper.KeycloakDefaultRolesMapper
             */
            public static final String DEFAULT_ROLES_ENABLED = BASE + ".default-roles.enabled";

            /**
             * Enable Keycloak default groups mapper for groups claim.
             * Template: "cui.jwt.keycloak.mappers.default-groups.enabled"
             * <p>
             * When enabled, this mapper processes Keycloak's standard {@code groups}
             * claim and ensures they are properly formatted for the CUI JWT library's
             * authorization mechanisms.
             * </p>
             * <p>
             * This provides compatibility with Keycloak's default group membership
             * mapper without requiring additional configuration.
             * </p>
             * <p>
             * Default value is {@code false}.
             * </p>
             *
             * @see de.cuioss.jwt.validation.domain.claim.mapper.KeycloakDefaultGroupsMapper
             */
            public static final String DEFAULT_GROUPS_ENABLED = BASE + ".default-groups.enabled";
        }
    }
}
