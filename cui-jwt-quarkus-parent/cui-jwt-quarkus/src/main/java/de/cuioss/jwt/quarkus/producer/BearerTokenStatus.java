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
package de.cuioss.jwt.quarkus.producer;

import jakarta.ws.rs.core.Response;
import java.util.List;
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
public enum BearerTokenStatus implements BearerTokenResponseStrategy {

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
        @Override
        public Response createResponse(BearerTokenResult result) {
            return Response.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
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
        @Override
        public Response createResponse(BearerTokenResult result) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
                .entity("Internal server error: Unable to access request context")
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
        @Override
        public Response createResponse(BearerTokenResult result) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer realm=\"protected-resource\"")
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
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
        @Override
        public Response createResponse(BearerTokenResult result) {
            String wwwAuthenticate = buildWwwAuthenticateHeader("invalid_token", 
                result.getErrorMessage().orElse("The access token is invalid"));
            return Response.status(Response.Status.UNAUTHORIZED)
                .header("WWW-Authenticate", wwwAuthenticate)
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
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
        @Override
        public Response createResponse(BearerTokenResult result) {
            // Check if it's a scope violation (401) or role/group violation (403)
            boolean hasScopeViolation = !result.getRequiredScopes().isEmpty();
            
            if (hasScopeViolation) {
                // OAuth Step-Up Authentication Challenge for insufficient scope
                String wwwAuthenticate = buildWwwAuthenticateHeaderWithScope("insufficient_scope", 
                    "The request requires higher privileges than provided by the access token",
                    result.getRequiredScopes());
                return Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", wwwAuthenticate)
                    .header("Cache-Control", "no-store, no-cache, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build();
            } else {
                // Role/group violation - 403 Forbidden with OAuth-style error structure
                String wwwAuthenticate = buildWwwAuthenticateHeaderWithPrivileges("insufficient_privileges", 
                    "The request requires higher privileges than provided by the access token",
                    result.getRequiredRoles(), result.getRequiredGroups());
                return Response.status(Response.Status.FORBIDDEN)
                    .header("WWW-Authenticate", wwwAuthenticate)
                    .header("Cache-Control", "no-store, no-cache, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build();
            }
        }
    };

    /**
     * Builds a standard WWW-Authenticate header for Bearer token errors.
     *
     * @param error The OAuth error code
     * @param errorDescription The human-readable error description
     * @return Formatted WWW-Authenticate header value
     */
    private static String buildWwwAuthenticateHeader(String error, String errorDescription) {
        return String.format("Bearer realm=\"protected-resource\", error=\"%s\", error_description=\"%s\"",
            escapeQuotes(error), escapeQuotes(errorDescription));
    }

    /**
     * Builds a WWW-Authenticate header for scope-related constraint violations.
     * Follows OAuth Step-Up Authentication Challenge specification.
     *
     * @param error The OAuth error code
     * @param errorDescription The human-readable error description
     * @param requiredScopes The list of required scopes
     * @return Formatted WWW-Authenticate header value
     */
    private static String buildWwwAuthenticateHeaderWithScope(String error, String errorDescription, List<String> requiredScopes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bearer realm=\"protected-resource\"");
        sb.append(", error=\"").append(escapeQuotes(error)).append("\"");
        sb.append(", error_description=\"").append(escapeQuotes(errorDescription)).append("\"");
        
        if (!requiredScopes.isEmpty()) {
            String scopeValue = requiredScopes.stream()
                .map(BearerTokenStatus::escapeQuotes)
                .collect(Collectors.joining(" "));
            sb.append(", scope=\"").append(scopeValue).append("\"");
        }
        
        return sb.toString();
    }

    /**
     * Builds a WWW-Authenticate header for role/group-related constraint violations.
     * Uses OAuth-style error structure adapted for role/group privileges.
     *
     * @param error The OAuth error code
     * @param errorDescription The human-readable error description
     * @param requiredRoles The list of required roles
     * @param requiredGroups The list of required groups
     * @return Formatted WWW-Authenticate header value
     */
    private static String buildWwwAuthenticateHeaderWithPrivileges(String error, String errorDescription, 
                                                           List<String> requiredRoles, List<String> requiredGroups) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bearer realm=\"protected-resource\"");
        sb.append(", error=\"").append(escapeQuotes(error)).append("\"");
        sb.append(", error_description=\"").append(escapeQuotes(errorDescription)).append("\"");
        
        // Add required roles and groups as custom parameters
        if (!requiredRoles.isEmpty()) {
            String rolesValue = requiredRoles.stream()
                .map(BearerTokenStatus::escapeQuotes)
                .collect(Collectors.joining(" "));
            sb.append(", required_roles=\"").append(rolesValue).append("\"");
        }
        
        if (!requiredGroups.isEmpty()) {
            String groupsValue = requiredGroups.stream()
                .map(BearerTokenStatus::escapeQuotes)
                .collect(Collectors.joining(" "));
            sb.append(", required_groups=\"").append(groupsValue).append("\"");
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
}