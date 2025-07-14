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
package de.cuioss.jwt.integration.endpoint;

import de.cuioss.jwt.quarkus.annotation.BearerToken;
import de.cuioss.jwt.quarkus.producer.BearerTokenResult;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.tools.logging.CuiLogger;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


import jakarta.enterprise.context.RequestScoped;

/**
 * REST endpoint for JWT validation operations.
 * Focused on core JWT validation use cases for integration testing.
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
    private final BearerTokenResult basicToken;
    private final BearerTokenResult tokenWithScopes;
    private final BearerTokenResult tokenWithRoles;
    private final BearerTokenResult tokenWithGroups;
    private final BearerTokenResult tokenWithAll;

    public JwtValidationEndpoint(
            TokenValidator tokenValidator,
            @BearerToken BearerTokenResult basicToken,
            @BearerToken(requiredScopes = {"read"}) BearerTokenResult tokenWithScopes,
            @BearerToken(requiredRoles = {"user"}) BearerTokenResult tokenWithRoles,
            @BearerToken(requiredGroups = {"test-group"}) BearerTokenResult tokenWithGroups,
            @BearerToken(requiredScopes = {"read"}, requiredRoles = {"user"}, requiredGroups = {"test-group"}) BearerTokenResult tokenWithAll) {
        this.tokenValidator = tokenValidator;
        this.basicToken = basicToken;
        this.tokenWithScopes = tokenWithScopes;
        this.tokenWithRoles = tokenWithRoles;
        this.tokenWithGroups = tokenWithGroups;
        this.tokenWithAll = tokenWithAll;
        LOGGER.info("JwtValidationEndpoint initialized with TokenValidator and BearerTokenResult instances");
    }

    /**
     * Validates a JWT access token using BearerTokenResult.
     * Primary endpoint for integration testing and benchmarking.
     *
     * @return Validation result
     */
    @POST
    @Path("/validate")
    public Response validateToken() {
        LOGGER.debug("validateToken called - checking basicToken authorization");
        if (basicToken.isSuccessfullyAuthorized()) {
            var tokenOpt = basicToken.getAccessTokenContent();
            if (tokenOpt.isPresent()) {
                AccessTokenContent token = tokenOpt.get();
                LOGGER.debug("Access token validated successfully - Subject: %s, Roles: %s, Groups: %s, Scopes: %s",
                    token.getSubject().orElse("none"), token.getRoles(), token.getGroups(), token.getScopes());
                return Response.ok(createTokenResponse(token, "Access token is valid")).build();
            } else {
                LOGGER.debug("BasicToken authorized but no AccessTokenContent present");
            }
        } else {
            LOGGER.debug("BasicToken authorization failed");
        }
        // Return consistent JSON format for authorization header tests
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(new ValidationResponse(false, "Bearer token validation failed or token not present"))
            .build();
    }

    /**
     * Validates a JWT access token from request body.
     * Fallback endpoint for testing with explicit token.
     *
     * @param tokenRequest Request containing the access token
     * @return Validation result
     */
    @POST
    @Path("/validate-explicit")
    public Response validateExplicitToken(TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ValidationResponse(false, "Missing or empty access token in request body"))
                .build();
        }

        try {
            AccessTokenContent token = tokenValidator.createAccessToken(tokenRequest.token().trim());
            LOGGER.debug("Explicit token validated successfully - Subject: %s, Roles: %s, Groups: %s, Scopes: %s",
                token.getSubject().orElse("none"), token.getRoles(), token.getGroups(), token.getScopes());
            return Response.ok(createTokenResponse(token, "Access token is valid")).build();
        } catch (TokenValidationException e) {
            LOGGER.warn("Explicit token validation failed: %s", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ValidationResponse(false, "Token validation failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Validates a JWT ID token.
     */
    @POST
    @Path("/validate/id-token")
    public Response validateIdToken(TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ValidationResponse(false, "Missing or empty ID token in request body"))
                .build();
        }

        try {
            tokenValidator.createIdToken(tokenRequest.token().trim());
            return Response.ok(new ValidationResponse(true, "ID token is valid")).build();
        } catch (TokenValidationException e) {
            LOGGER.warn("ID token validation failed: %s", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ValidationResponse(false, "ID token validation failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Validates a JWT refresh token.
     */
    @POST
    @Path("/validate/refresh-token")
    public Response validateRefreshToken(TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ValidationResponse(false, "Missing or empty refresh token in request body"))
                .build();
        }

        try {
            tokenValidator.createRefreshToken(tokenRequest.token().trim());
            return Response.ok(new ValidationResponse(true, "Refresh token is valid")).build();
        } catch (TokenValidationException e) {
            LOGGER.warn("Refresh token validation failed: %s", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ValidationResponse(false, "Refresh token validation failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Tests BearerToken injection with scope requirements.
     */
    @GET
    @Path("/bearer-token/with-scopes")
    public Response testTokenWithScopes() {
        return processBearerTokenResult(tokenWithScopes, "Token with scopes");
    }

    /**
     * Tests BearerToken injection with role requirements.
     */
    @GET
    @Path("/bearer-token/with-roles")
    public Response testTokenWithRoles() {
        return processBearerTokenResult(tokenWithRoles, "Token with roles");
    }

    /**
     * Tests BearerToken injection with group requirements.
     */
    @GET
    @Path("/bearer-token/with-groups")
    public Response testTokenWithGroups() {
        return processBearerTokenResult(tokenWithGroups, "Token with groups");
    }

    /**
     * Tests BearerToken injection with all requirements.
     */
    @GET
    @Path("/bearer-token/with-all")
    public Response testTokenWithAll() {
        return processBearerTokenResult(tokenWithAll, "Token with all requirements");
    }

    /**
     * Tests basic BearerToken injection without requirements.
     */
    @GET
    @Path("/bearer-token/basic")
    public Response testBasicToken() {
        return processBearerTokenResult(basicToken, "Basic token");
    }


    // Helper methods

    /**
     * Processes a BearerTokenResult and returns appropriate response.
     */
    private Response processBearerTokenResult(BearerTokenResult tokenResult, String description) {
        LOGGER.debug("processBearerTokenResult called for: %s", description);
        if (tokenResult.isSuccessfullyAuthorized()) {
            var tokenOpt = tokenResult.getAccessTokenContent();
            if (tokenOpt.isPresent()) {
                AccessTokenContent token = tokenOpt.get();
                LOGGER.debug("Bearer token authorized successfully - Subject: %s, Roles: %s, Groups: %s, Scopes: %s",
                    token.getSubject().orElse("none"), token.getRoles(), token.getGroups(), token.getScopes());
                return Response.ok(createTokenResponse(token, description + " is valid")).build();
            } else {
                LOGGER.debug("Bearer token authorized but no AccessTokenContent present for: %s", description);
            }
        } else {
            LOGGER.debug("Bearer token authorization failed for: %s", description);
        }
        // Use the improved BearerTokenStatus.createResponse() method
        return tokenResult.errorResponse();
    }

    /**
     * Creates a standardized token response.
     */
    private ValidationResponse createTokenResponse(AccessTokenContent token, String message) {
        // Use HashMap to handle Optional values from getSubject() and getEmail()
        var data = new HashMap<String, Object>();
        data.put("subject", token.getSubject().orElse("not-present"));
        data.put("scopes", token.getScopes());
        data.put("roles", token.getRoles());
        data.put("groups", token.getGroups());
        data.put("email", token.getEmail().orElse("not-present"));

        return new ValidationResponse(true, message, data);
    }


    // Request and Response DTOs
    public record TokenRequest(String token) {

        /**
         * Checks if the token request is missing or empty.
         *
         * @return true if the token is null or empty, false otherwise
         */
        public boolean isEmpty() {
            return token == null || token.trim().isEmpty();
        }
    }

    public record ValidationResponse(boolean valid, String message, Map<String, Object> data) {
        // Convenience constructor for backwards compatibility
        public ValidationResponse(boolean valid, String message) {
            this(valid, message, Collections.emptyMap());
        }
    }
}