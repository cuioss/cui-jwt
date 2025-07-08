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
package de.cuioss.jwt.integration.endpoint;

import de.cuioss.jwt.quarkus.annotation.BearerToken;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.tools.logging.CuiLogger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * REST endpoint for JWT validation operations.
 * This endpoint provides the real application functionality that is used by
 * both integration tests and performance benchmarks.
 */
@Path("/jwt")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RegisterForReflection
@RunOnVirtualThread
public class JwtValidationEndpoint {

    private static final CuiLogger LOGGER = new CuiLogger(JwtValidationEndpoint.class);

    private final TokenValidator tokenValidator;

    @Inject
    @BearerToken(requiredScopes = "plattform", requiredRoles = "superRolle")
    Optional<AccessTokenContent> basicToken;

    @Inject
    public JwtValidationEndpoint(TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
        LOGGER.info("JwtValidationEndpoint initialized with TokenValidator: %s", (tokenValidator != null));
    }

    /**
     * Validates a JWT access token - primary endpoint for integration testing and benchmarking.
     * Now uses BearerTokenProducer for simplified token handling.
     *
     * @return Validation result
     */
    @POST
    @Path("/validate")
    public Response validateToken() {
        if (basicToken.isPresent()) {
            AccessTokenContent token = basicToken.get();
            LOGGER.debug("Access token validation successful for subject: %s", token.getSubject());
            return Response.ok(new ValidationResponse(true, "Access token is valid",
                    Map.of(
                        "subject", token.getSubject(),
                        "scopes", token.getScopes(),
                        "roles", token.getRoles(),
                        "groups", token.getGroups(),
                        "email", token.getEmail().orElse("not-present")
                    )))
                    .build();
        } else {
            LOGGER.warn("Bearer token validation failed or token not present");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ValidationResponse(false, "Bearer token validation failed or token not present", null))
                    .build();
        }
    }

    /**
     * Validates a JWT ID token.
     *
     * @param tokenRequest Request containing the ID token
     * @return Validation result
     */
    @POST
    @Path("/validate/id-token")
    public Response validateIdToken(TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.token() == null || tokenRequest.token().trim().isEmpty()) {
            LOGGER.warn("Missing or empty ID token in request body");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ValidationResponse(false, "Missing or empty ID token in request body"))
                    .build();
        }

        String jwtToken = tokenRequest.token().trim();
        LOGGER.debug("ID token validation: %s", jwtToken);
        try {
            tokenValidator.createIdToken(jwtToken);
            return Response.ok(new ValidationResponse(true, "ID token is valid"))
                    .build();
        } catch (TokenValidationException e) {
            LOGGER.warn("ID token validation error: %s", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ValidationResponse(false, "ID token validation failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Validates a JWT refresh token.
     *
     * @param tokenRequest Request containing the refresh token
     * @return Validation result
     */
    @POST
    @Path("/validate/refresh-token")
    public Response validateRefreshToken(TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.token() == null || tokenRequest.token().trim().isEmpty()) {
            LOGGER.warn("Missing or empty refresh token in request body");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ValidationResponse(false, "Missing or empty refresh token in request body"))
                    .build();
        }

        String jwtToken = tokenRequest.token().trim();
        LOGGER.debug("Refresh token validation: %s", jwtToken);
        try {
            tokenValidator.createRefreshToken(jwtToken);
            return Response.ok(new ValidationResponse(true, "Refresh token is valid"))
                    .build();
        } catch (TokenValidationException e) {
            LOGGER.warn("Refresh token validation error: %s", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ValidationResponse(false, "Refresh token validation failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Tests BearerToken injection with scope requirements.
     *
     * @return Validation result for token with required scopes
     */
    @GET
    @Path("/bearer-token/with-scopes")
    public Response testTokenWithScopes() {
        if (basicToken.isPresent()) {
            AccessTokenContent token = basicToken.get();
            boolean hasRequiredScope = token.providesScopes(List.of("read"));
            return Response.ok(new ValidationResponse(hasRequiredScope,
                    hasRequiredScope ? "Token has required scopes" : "Token does not have required scopes",
                    Map.of(
                        "subject", token.getSubject(),
                        "scopes", token.getScopes(),
                        "hasRequiredScope", hasRequiredScope
                    )))
                    .build();
        } else {
            return Response.ok(new ValidationResponse(false, "Token missing or invalid"))
                    .build();
        }
    }

    /**
     * Tests BearerToken injection with role requirements.
     *
     * @return Validation result for token with required roles
     */
    @GET
    @Path("/bearer-token/with-roles")
    public Response testTokenWithRoles() {
        if (basicToken.isPresent()) {
            AccessTokenContent token = basicToken.get();
            boolean hasRequiredRole = token.providesRoles(List.of("user"));
            return Response.ok(new ValidationResponse(hasRequiredRole,
                    hasRequiredRole ? "Token has required roles" : "Token does not have required roles",
                    Map.of(
                        "subject", token.getSubject(),
                        "roles", token.getRoles(),
                        "hasRequiredRole", hasRequiredRole
                    )))
                    .build();
        } else {
            return Response.ok(new ValidationResponse(false, "Token missing or invalid"))
                    .build();
        }
    }

    /**
     * Tests BearerToken injection with group requirements.
     *
     * @return Validation result for token with required groups
     */
    @GET
    @Path("/bearer-token/with-groups")
    public Response testTokenWithGroups() {
        if (basicToken.isPresent()) {
            AccessTokenContent token = basicToken.get();
            boolean hasRequiredGroup = token.providesGroups(List.of("test-group"));
            return Response.ok(new ValidationResponse(hasRequiredGroup,
                    hasRequiredGroup ? "Token has required groups" : "Token does not have required groups",
                    Map.of(
                        "subject", token.getSubject(),
                        "groups", token.getGroups(),
                        "hasRequiredGroup", hasRequiredGroup
                    )))
                    .build();
        } else {
            return Response.ok(new ValidationResponse(false, "Token missing or invalid"))
                    .build();
        }
    }

    /**
     * Tests BearerToken injection with all requirements.
     *
     * @return Validation result for token with all requirements
     */
    @GET
    @Path("/bearer-token/with-all")
    public Response testTokenWithAll() {
        if (basicToken.isPresent()) {
            AccessTokenContent token = basicToken.get();
            boolean hasAllRequirements = token.providesScopes(List.of("read")) &&
                                        token.providesRoles(List.of("user")) &&
                                        token.providesGroups(List.of("test-group"));
            return Response.ok(new ValidationResponse(hasAllRequirements,
                    hasAllRequirements ? "Token meets all requirements" : "Token does not meet all requirements",
                    Map.of(
                        "subject", token.getSubject(),
                        "scopes", token.getScopes(),
                        "roles", token.getRoles(),
                        "groups", token.getGroups(),
                        "hasAllRequirements", hasAllRequirements
                    )))
                    .build();
        } else {
            return Response.ok(new ValidationResponse(false, "Token missing or invalid"))
                    .build();
        }
    }


    // Request and Response DTOs
    public record TokenRequest(String token) {
    }

    public record ValidationResponse(boolean valid, String message, Map<String, Object> data) {
        // Convenience constructor for backwards compatibility
        public ValidationResponse(boolean valid, String message) {
            this(valid, message, null);
        }
    }
}