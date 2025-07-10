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
import de.cuioss.jwt.quarkus.producer.BearerTokenProducer;
import de.cuioss.jwt.quarkus.producer.BearerTokenResult;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.tools.logging.CuiLogger;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import java.util.Map;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Provider;

/**
 * REST endpoint for JWT validation operations.
 * This endpoint provides the real application functionality that is used by
 * both integration tests and performance benchmarks.
 */
@Path("/jwt")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RegisterForReflection
@RunOnVirtualThread
public class JwtValidationEndpoint {

    private static final CuiLogger LOGGER = new CuiLogger(JwtValidationEndpoint.class);

    private final TokenValidator tokenValidator;
    private final BearerTokenProducer bearerTokenProducer;

    // CDI producer injection fields for testing @BearerToken annotation
    @Inject
    @BearerToken
    BearerTokenResult basicToken;

    @Inject
    @BearerToken(requiredScopes = {"read"})
    BearerTokenResult tokenWithScopes;

    @Inject
    @BearerToken(requiredRoles = {"user"})
    BearerTokenResult tokenWithRoles;

    @Inject
    @BearerToken(requiredGroups = {"test-group"})
    BearerTokenResult tokenWithGroups;

    @Inject
    @BearerToken(requiredScopes = {"read"}, requiredRoles = {"user"}, requiredGroups = {"test-group"})
    BearerTokenResult tokenWithAll;

    @Inject
    public JwtValidationEndpoint(TokenValidator tokenValidator, BearerTokenProducer bearerTokenProducer) {
        this.tokenValidator = tokenValidator;
        this.bearerTokenProducer = bearerTokenProducer;
        LOGGER.info("JwtValidationEndpoint initialized with TokenValidator: %s, BearerTokenProducer: %s",
                (tokenValidator != null), (bearerTokenProducer != null));
        LOGGER.info("BearerTokenProducer class: %s", bearerTokenProducer.getClass().getName());
    }

    /**
     * Validates a JWT access token - primary endpoint for integration testing and benchmarking.
     * Uses BearerTokenProducer's public getAccessTokenContent() method for Bearer header validation.
     *
     * @param tokenRequest Request containing the access token (optional if using Bearer header)
     * @return Validation result
     */
    @POST
    @Path("/validate")
    public Response validateToken(TokenRequest tokenRequest) {
        // First try to use the bearer token service if available
        try {
            var accessTokenOpt = bearerTokenProducer.getAccessTokenContent();
            if (accessTokenOpt.isPresent()) {
                AccessTokenContent token = accessTokenOpt.get();
                LOGGER.info("Access token validation successful via BearerTokenProducer for subject: %s", token.getSubject());
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
                LOGGER.info("BearerTokenProducer validation failed - no valid token found");
            }
        } catch (Exception e) {
            LOGGER.warn("BearerTokenProducer failed with exception: %s", e.getMessage());
        }

        // Fall back to traditional token validation from request body
        if (tokenRequest != null && tokenRequest.token() != null && !tokenRequest.token().trim().isEmpty()) {
            String jwtToken = tokenRequest.token().trim();
            LOGGER.debug("Access token validation: %s", jwtToken);
            try {
                AccessTokenContent token = tokenValidator.createAccessToken(jwtToken);
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
            } catch (TokenValidationException e) {
                LOGGER.warn("Access token validation error: %s", e.getMessage());
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ValidationResponse(false, "Bearer token validation failed or token not present"))
                        .build();
            }
        }

        LOGGER.warn("No token provided in Authorization header or request body");
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ValidationResponse(false, "Bearer token validation failed or token not present"))
                .build();
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
        if (tokenWithScopes.isSuccessfullyAuthorized()) {
            AccessTokenContent token = tokenWithScopes.getAccessTokenContent().get();
            boolean hasRequiredScope = token.providesScopes(Set.of("read"));
            return Response.ok(new ValidationResponse(hasRequiredScope,
                    hasRequiredScope ? "Token has required scopes" : "Token does not have required scopes",
                    Map.of(
                            "subject", token.getSubject(),
                            "scopes", token.getScopes(),
                            "hasRequiredScope", hasRequiredScope
                    )))
                    .build();
        } else {
            return tokenWithScopes.errorResponse();
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
        if (tokenWithRoles.isSuccessfullyAuthorized()) {
            AccessTokenContent token = tokenWithRoles.getAccessTokenContent().get();
            boolean hasRequiredRole = token.providesRoles(Set.of("user"));
            return Response.ok(new ValidationResponse(hasRequiredRole,
                    hasRequiredRole ? "Token has required roles" : "Token does not have required roles",
                    Map.of(
                            "subject", token.getSubject(),
                            "roles", token.getRoles(),
                            "hasRequiredRole", hasRequiredRole
                    )))
                    .build();
        } else {
            return tokenWithRoles.errorResponse();
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
        if (tokenWithGroups.isSuccessfullyAuthorized()) {
            AccessTokenContent token = tokenWithGroups.getAccessTokenContent().get();
            boolean hasRequiredGroup = token.providesGroups(Set.of("test-group"));
            return Response.ok(new ValidationResponse(hasRequiredGroup,
                    hasRequiredGroup ? "Token has required groups" : "Token does not have required groups",
                    Map.of(
                            "subject", token.getSubject(),
                            "groups", token.getGroups(),
                            "hasRequiredGroup", hasRequiredGroup
                    )))
                    .build();
        } else {
            return tokenWithGroups.errorResponse();
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
        if (tokenWithAll.isSuccessfullyAuthorized()) {
            AccessTokenContent token = tokenWithAll.getAccessTokenContent().get();
            boolean hasAllRequirements = token.providesScopes(Set.of("read")) &&
                    token.providesRoles(Set.of("user")) &&
                    token.providesGroups(Set.of("test-group"));
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
            return tokenWithAll.errorResponse();
        }
    }

    /**
     * Tests CDI producer pattern with @BearerToken annotation (no requirements).
     *
     * @return Validation result for basic CDI producer injection
     */
    @GET
    @Path("/cdi-producer/basic")
    public Response testCdiProducerBasic() {
        if (basicToken.isSuccessfullyAuthorized()) {
            AccessTokenContent token = basicToken.getAccessTokenContent().get();
            return Response.ok(new ValidationResponse(true, "CDI producer injection successful",
                    Map.of(
                            "subject", token.getSubject(),
                            "scopes", token.getScopes(),
                            "roles", token.getRoles(),
                            "groups", token.getGroups(),
                            "email", token.getEmail().orElse("not-present")
                    )))
                    .build();
        } else {
            return basicToken.errorResponse();
        }
    }

    /**
     * Tests CDI producer pattern with scope requirements.
     *
     * @return Validation result for CDI producer with scope requirements
     */
    @GET
    @Path("/cdi-producer/with-scopes")
    public Response testCdiProducerWithScopes() {
        if (tokenWithScopes.isSuccessfullyAuthorized()) {
            AccessTokenContent token = tokenWithScopes.getAccessTokenContent().get();
            return Response.ok(new ValidationResponse(true, "CDI producer with scopes successful",
                    Map.of(
                            "subject", token.getSubject(),
                            "scopes", token.getScopes(),
                            "requiredScopes", Set.of("read")
                    )))
                    .build();
        } else {
            return tokenWithScopes.errorResponse();
        }
    }

    /**
     * Tests CDI producer pattern with role requirements.
     *
     * @return Validation result for CDI producer with role requirements
     */
    @GET
    @Path("/cdi-producer/with-roles")
    public Response testCdiProducerWithRoles() {
        if (tokenWithRoles.isSuccessfullyAuthorized()) {
            AccessTokenContent token = tokenWithRoles.getAccessTokenContent().get();
            return Response.ok(new ValidationResponse(true, "CDI producer with roles successful",
                    Map.of(
                            "subject", token.getSubject(),
                            "roles", token.getRoles(),
                            "requiredRoles", Set.of("user")
                    )))
                    .build();
        } else {
            return tokenWithRoles.errorResponse();
        }
    }

    /**
     * Tests CDI producer pattern with group requirements.
     *
     * @return Validation result for CDI producer with group requirements
     */
    @GET
    @Path("/cdi-producer/with-groups")
    public Response testCdiProducerWithGroups() {
        if (tokenWithGroups.isSuccessfullyAuthorized()) {
            AccessTokenContent token = tokenWithGroups.getAccessTokenContent().get();
            return Response.ok(new ValidationResponse(true, "CDI producer with groups successful",
                    Map.of(
                            "subject", token.getSubject(),
                            "groups", token.getGroups(),
                            "requiredGroups", Set.of("test-group")
                    )))
                    .build();
        } else {
            return tokenWithGroups.errorResponse();
        }
    }

    /**
     * Tests CDI producer pattern with all requirements.
     *
     * @return Validation result for CDI producer with all requirements
     */
    @GET
    @Path("/cdi-producer/with-all")
    public Response testCdiProducerWithAll() {
        if (tokenWithAll.isSuccessfullyAuthorized()) {
            AccessTokenContent token = tokenWithAll.getAccessTokenContent().get();
            return Response.ok(new ValidationResponse(true, "CDI producer with all requirements successful",
                    Map.of(
                            "subject", token.getSubject(),
                            "scopes", token.getScopes(),
                            "roles", token.getRoles(),
                            "groups", token.getGroups(),
                            "requiredScopes", Set.of("read"),
                            "requiredRoles", Set.of("user"),
                            "requiredGroups", Set.of("test-group")
                    )))
                    .build();
        } else {
            return tokenWithAll.errorResponse();
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