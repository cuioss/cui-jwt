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
package de.cuioss.jwt.quarkus.producer;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Enumeration representing the different states of bearer token validation.
 * <p>
 * This enum provides structured information about the outcome of bearer token
 * processing, allowing consumers to understand exactly what happened during
 * token validation and respond appropriately.
 * <p>
 * Each status implements the strategy pattern to create appropriate HTTP responses
 * following OAuth 2.0 Bearer Token specification (RFC 6750) and OAuth Step-Up
 * Authentication Challenge (draft-ietf-oauth-step-up-authn-challenge-17) best practices.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RegisterForReflection(methods = false, fields = false)
public enum BearerTokenStatus {

    /**
     * The bearer token was successfully validated and all requirements were met.
     * <p>
     * This indicates that:
     * <ul>
     *   <li>The token was extracted from the Authorization header</li>
     *   <li>The token was successfully parsed and validated</li>
     *   <li>All required scopes, roles, and groups were present</li>
     *   <li>An AccessTokenContent instance was created</li>
     * </ul>
     */
    FULLY_VERIFIED {
        public Response createResponse(BearerTokenResult result) {
            return Response.ok()
                    .type(MediaType.APPLICATION_JSON)
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_VALUE)
                    .header(HEADER_PRAGMA, PRAGMA_VALUE)
                    .build();
        }
    },

    /**
     * The HTTP request could not be accessed to extract the bearer token.
     * <p>
     * This occurs when the HttpServletRequest is not available in the current
     * context, typically in non-web environments or when the servlet resolver
     * fails to provide the request object.
     */
    COULD_NOT_ACCESS_REQUEST {
        public Response createResponse(BearerTokenResult result) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_VALUE)
                    .header(HEADER_PRAGMA, PRAGMA_VALUE)
                    .entity(createErrorEntity(ERROR_MSG_SERVER_ERROR))
                    .build();
        }
    },

    /**
     * No bearer token was provided in the Authorization header.
     * <p>
     * This indicates that either:
     * <ul>
     *   <li>The Authorization header is missing</li>
     *   <li>The Authorization header is empty</li>
     *   <li>The Authorization header does not start with "Bearer "</li>
     * </ul>
     */
    NO_TOKEN_GIVEN {
        public Response createResponse(BearerTokenResult result) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .header(HEADER_WWW_AUTHENTICATE, BEARER_REALM)
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_VALUE)
                    .header(HEADER_PRAGMA, PRAGMA_VALUE)
                    .entity(createErrorEntity(TOKEN_NOT_PRESENT))
                    .build();
        }
    },

    /**
     * The bearer token could not be parsed or validated.
     * <p>
     * This occurs when a TokenValidationException is thrown during token
     * validation, indicating issues such as:
     * <ul>
     *   <li>Invalid JWT structure</li>
     *   <li>Expired token</li>
     *   <li>Invalid signature</li>
     *   <li>Missing required claims</li>
     * </ul>
     */
    PARSING_ERROR {
        public Response createResponse(BearerTokenResult result) {
            String wwwAuthenticate = buildInvalidTokenHeader();
            return Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .header(HEADER_WWW_AUTHENTICATE, wwwAuthenticate)
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_VALUE)
                    .header(HEADER_PRAGMA, PRAGMA_VALUE)
                    .entity(createErrorEntity(TOKEN_NOT_PRESENT))
                    .build();
        }
    },

    /**
     * The bearer token was valid but did not meet the specified requirements.
     * <p>
     * This indicates that the token was successfully parsed and validated,
     * but failed to meet one or more of the BearerToken annotation requirements:
     * <ul>
     *   <li>Missing required scopes</li>
     *   <li>Missing required roles</li>
     *   <li>Missing required groups</li>
     * </ul>
     */
    CONSTRAINT_VIOLATION {
        public Response createResponse(BearerTokenResult result) {
            // Check if it's a scope violation (401) or role/group violation (403)
            boolean hasScopeViolation = !result.getMissingScopes().isEmpty();

            if (hasScopeViolation) {
                // OAuth Step-Up Authentication Challenge for insufficient scope
                String wwwAuthenticate = buildInsufficientScopeHeader(result.getMissingScopes());
                return Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .header(HEADER_WWW_AUTHENTICATE, wwwAuthenticate)
                        .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_VALUE)
                        .header(HEADER_PRAGMA, PRAGMA_VALUE)
                        .entity(createErrorEntity(TOKEN_NOT_PRESENT))
                        .build();
            } else {
                // Role/group violation - 403 Forbidden with OAuth-style error structure
                String wwwAuthenticate = buildInsufficientPrivilegesHeader(result.getMissingRoles(), result.getMissingGroups());
                return Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .header(HEADER_WWW_AUTHENTICATE, wwwAuthenticate)
                        .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_VALUE)
                        .header(HEADER_PRAGMA, PRAGMA_VALUE)
                        .entity(createErrorEntity(TOKEN_NOT_PRESENT))
                        .build();
            }
        }
    };

    public static final String TOKEN_NOT_PRESENT = "Bearer token validation failed or token not present";
    // HTTP Header Constants
    private static final String HEADER_WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String HEADER_CACHE_CONTROL = "Cache-Control";
    private static final String HEADER_PRAGMA = "Pragma";

    // Header Values
    private static final String CACHE_CONTROL_VALUE = "no-store, no-cache, must-revalidate";
    private static final String PRAGMA_VALUE = "no-cache";

    // OAuth Constants
    private static final String BEARER_REALM = "Bearer realm=\"protected-resource\"";
    private static final String BEARER_SCHEME = "Bearer";
    private static final String REALM_PARAMETER = "realm=\"protected-resource\"";

    // OAuth Error Codes
    private static final String ERROR_INVALID_TOKEN = "invalid_token";
    private static final String ERROR_INSUFFICIENT_SCOPE = "insufficient_scope";
    private static final String ERROR_INSUFFICIENT_PRIVILEGES = "insufficient_privileges";

    // OAuth Parameters
    private static final String PARAM_ERROR = "error";
    private static final String PARAM_ERROR_DESCRIPTION = "error_description";
    private static final String PARAM_SCOPE = "scope";
    private static final String PARAM_REQUIRED_ROLES = "required_roles";
    private static final String PARAM_REQUIRED_GROUPS = "required_groups";

    // Error Messages
    private static final String ERROR_MSG_INVALID_TOKEN = "The access token is invalid";
    private static final String ERROR_MSG_HIGHER_PRIVILEGES = "The request requires higher privileges than provided by the access token";
    private static final String ERROR_MSG_SERVER_ERROR = "Internal server error: Unable to access request context";

    /**
     * Creates an appropriate HTTP response for the given bearer token validation result.
     *
     * @param result The bearer token validation result containing status and context information
     * @return Response object with appropriate HTTP status code, headers, and body
     */
    public abstract Response createResponse(BearerTokenResult result);

    /**
     * Builds a WWW-Authenticate header for invalid token errors.
     *
     * @return Formatted WWW-Authenticate header value for invalid tokens
     */
    private static String buildInvalidTokenHeader() {
        return "%s %s, %s=\"%s\", %s=\"%s\"".formatted(
                BEARER_SCHEME, REALM_PARAMETER, PARAM_ERROR, ERROR_INVALID_TOKEN,
                PARAM_ERROR_DESCRIPTION, ERROR_MSG_INVALID_TOKEN);
    }

    /**
     * Builds a WWW-Authenticate header for insufficient scope errors.
     * Follows OAuth Step-Up Authentication Challenge specification.
     *
     * @param missingScopes The collection of missing scopes
     * @return Formatted WWW-Authenticate header value for insufficient scope
     */
    private static String buildInsufficientScopeHeader(Collection<String> missingScopes) {
        StringBuilder sb = new StringBuilder();
        sb.append(BEARER_SCHEME).append(" ").append(REALM_PARAMETER);
        sb.append(", ").append(PARAM_ERROR).append("=\"").append(ERROR_INSUFFICIENT_SCOPE).append("\"");
        sb.append(", ").append(PARAM_ERROR_DESCRIPTION).append("=\"").append(ERROR_MSG_HIGHER_PRIVILEGES).append("\"");

        if (!missingScopes.isEmpty()) {
            String scopeValue = missingScopes.stream()
                    .map(BearerTokenStatus::escapeQuotes)
                    .collect(Collectors.joining(" "));
            sb.append(", ").append(PARAM_SCOPE).append("=\"").append(scopeValue).append("\"");
        }

        return sb.toString();
    }

    /**
     * Builds a WWW-Authenticate header for insufficient privileges errors.
     * Uses OAuth-style error structure adapted for role/group privileges.
     *
     * @param missingRoles The collection of missing roles
     * @param missingGroups The collection of missing groups
     * @return Formatted WWW-Authenticate header value for insufficient privileges
     */
    private static String buildInsufficientPrivilegesHeader(Collection<String> missingRoles, Collection<String> missingGroups) {
        StringBuilder sb = new StringBuilder();
        sb.append(BEARER_SCHEME).append(" ").append(REALM_PARAMETER);
        sb.append(", ").append(PARAM_ERROR).append("=\"").append(ERROR_INSUFFICIENT_PRIVILEGES).append("\"");
        sb.append(", ").append(PARAM_ERROR_DESCRIPTION).append("=\"").append(ERROR_MSG_HIGHER_PRIVILEGES).append("\"");

        // Add missing roles and groups as custom parameters
        if (!missingRoles.isEmpty()) {
            String rolesValue = missingRoles.stream()
                    .map(BearerTokenStatus::escapeQuotes)
                    .collect(Collectors.joining(" "));
            sb.append(", ").append(PARAM_REQUIRED_ROLES).append("=\"").append(rolesValue).append("\"");
        }

        if (!missingGroups.isEmpty()) {
            String groupsValue = missingGroups.stream()
                    .map(BearerTokenStatus::escapeQuotes)
                    .collect(Collectors.joining(" "));
            sb.append(", ").append(PARAM_REQUIRED_GROUPS).append("=\"").append(groupsValue).append("\"");
        }

        return sb.toString();
    }

    /**
     * Escapes double quotes in strings for use in HTTP header values.
     *
     * @param input The input string
     * @return String with escaped quotes
     */
    private static String escapeQuotes(String input) {
        return input == null ? "" : input.replace("\"", "\\\"");
    }

    /**
     * Creates a standardized error entity for JSON responses.
     * This ensures consistent error response format across all status types.
     *
     * @param message The error message
     * @return A formatted error entity with consistent structure
     */
    private static ErrorEntity createErrorEntity(String message) {
        return new ErrorEntity(false, message);
    }

    /**
     * Simple error entity for JSON responses.
     * Provides a consistent structure for error responses.
     */
    @RegisterForReflection
    public record ErrorEntity(boolean valid, String message) {
    }
}