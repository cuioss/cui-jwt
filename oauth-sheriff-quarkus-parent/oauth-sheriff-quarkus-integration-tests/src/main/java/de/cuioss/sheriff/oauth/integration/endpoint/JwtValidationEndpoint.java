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
package de.cuioss.sheriff.oauth.integration.endpoint;

import de.cuioss.sheriff.oauth.core.TokenValidator;
import de.cuioss.sheriff.oauth.core.domain.token.AccessTokenContent;
import de.cuioss.sheriff.oauth.core.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.quarkus.annotation.BearerAuth;
import de.cuioss.sheriff.oauth.quarkus.annotation.BearerToken;
import de.cuioss.sheriff.oauth.quarkus.producer.BearerTokenResult;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint for JWT validation operations.
 * Focused on core JWT validation use cases for integration testing.
 */
@Path("/jwt")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RegisterForReflection
@RunOnVirtualThread
public class JwtValidationEndpoint {

    private static final CuiLogger LOGGER = new CuiLogger(JwtValidationEndpoint.class);

    private final TokenValidator tokenValidator;
    private final Instance<BearerTokenResult> basicToken;
    private final Instance<BearerTokenResult> tokenWithScopes;
    private final Instance<BearerTokenResult> tokenWithRoles;
    private final Instance<BearerTokenResult> tokenWithGroups;
    private final Instance<BearerTokenResult> tokenWithAll;

    public JwtValidationEndpoint(
            TokenValidator tokenValidator,
            @BearerToken Instance<BearerTokenResult> basicToken,
            @BearerToken(requiredScopes = {"read"}) Instance<BearerTokenResult> tokenWithScopes,
            @BearerToken(requiredRoles = {"user"}) Instance<BearerTokenResult> tokenWithRoles,
            @BearerToken(requiredGroups = {"test-group"}) Instance<BearerTokenResult> tokenWithGroups,
            @BearerToken(requiredScopes = {"read"}, requiredRoles = {"user"}, requiredGroups = {"test-group"}) Instance<BearerTokenResult> tokenWithAll) {
        this.tokenValidator = tokenValidator;
        this.basicToken = basicToken;
        this.tokenWithScopes = tokenWithScopes;
        this.tokenWithRoles = tokenWithRoles;
        this.tokenWithGroups = tokenWithGroups;
        this.tokenWithAll = tokenWithAll;
        LOGGER.debug("JwtValidationEndpoint initialized with TokenValidator and lazy BearerTokenResult instances");
    }

    /**
     * Validates a JWT access token using BearerTokenResult.
     * Primary endpoint for integration testing and benchmarking.
     *
     * @return Validation result
     */
    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response validateToken() {
        LOGGER.debug("validateToken called - checking basicToken authorization");
        BearerTokenResult tokenResult = basicToken.get();
        if (tokenResult.isSuccessfullyAuthorized()) {
            var tokenOpt = tokenResult.getAccessTokenContent();
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
    @Consumes(MediaType.APPLICATION_JSON)
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
            LOGGER.debug("Explicit token validation failed: %s", e.getMessage());
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
    @Consumes(MediaType.APPLICATION_JSON)
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
            LOGGER.debug("ID token validation failed: %s", e.getMessage());
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
    @Consumes(MediaType.APPLICATION_JSON)
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
            LOGGER.debug("Refresh token validation failed: %s", e.getMessage());
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
        LOGGER.debug("testTokenWithScopes called");
        return processBearerTokenResult(tokenWithScopes.get(), "Token with scopes");
    }

    /**
     * Tests BearerToken injection with role requirements.
     */
    @GET
    @Path("/bearer-token/with-roles")
    public Response testTokenWithRoles() {
        return processBearerTokenResult(tokenWithRoles.get(), "Token with roles");
    }

    /**
     * Tests BearerToken injection with group requirements.
     */
    @GET
    @Path("/bearer-token/with-groups")
    public Response testTokenWithGroups() {
        return processBearerTokenResult(tokenWithGroups.get(), "Token with groups");
    }

    /**
     * Tests BearerToken injection with all requirements.
     */
    @GET
    @Path("/bearer-token/with-all")
    public Response testTokenWithAll() {
        return processBearerTokenResult(tokenWithAll.get(), "Token with all requirements");
    }

    /**
     * Tests basic BearerToken injection without requirements.
     */
    @GET
    @Path("/bearer-token/basic")
    public Response testBasicToken() {
        return processBearerTokenResult(basicToken.get(), "Basic token");
    }

    /**
     * Tests interceptor-based validation without requirements.
     * Demonstrates declarative security - no manual validation needed.
     */
    @GET
    @Path("/interceptor/basic")
    @BearerAuth
    public Response testInterceptorBasic() {
        LOGGER.debug("testInterceptorBasic - business logic executed (token already validated by interceptor)");
        return Response.ok(new ValidationResponse(true, "Interceptor validation successful (basic)")).build();
    }

    /**
     * Tests interceptor-based validation with scope requirements.
     */
    @GET
    @Path("/interceptor/with-scopes")
    @BearerAuth(requiredScopes = {"read"})
    public Response testInterceptorWithScopes() {
        LOGGER.debug("testInterceptorWithScopes - business logic executed");
        return Response.ok(new ValidationResponse(true, "Interceptor validation successful (with scopes)")).build();
    }

    /**
     * Tests interceptor-based validation with role requirements.
     */
    @GET
    @Path("/interceptor/with-roles")
    @BearerAuth(requiredRoles = {"user"})
    public Response testInterceptorWithRoles() {
        LOGGER.debug("testInterceptorWithRoles - business logic executed");
        return Response.ok(new ValidationResponse(true, "Interceptor validation successful (with roles)")).build();
    }

    /**
     * Tests interceptor-based validation with group requirements.
     */
    @GET
    @Path("/interceptor/with-groups")
    @BearerAuth(requiredGroups = {"test-group"})
    public Response testInterceptorWithGroups() {
        LOGGER.debug("testInterceptorWithGroups - business logic executed");
        return Response.ok(new ValidationResponse(true, "Interceptor validation successful (with groups)")).build();
    }

    /**
     * Tests interceptor-based validation with all requirements (scopes, roles, groups).
     */
    @GET
    @Path("/interceptor/with-all")
    @BearerAuth(requiredScopes = {"read"}, requiredRoles = {"user"}, requiredGroups = {"test-group"})
    public Response testInterceptorWithAll() {
        LOGGER.debug("testInterceptorWithAll - business logic executed");
        return Response.ok(new ValidationResponse(true, "Interceptor validation successful (with all requirements)")).build();
    }

    /**
     * Tests CDI-based token validation with token content access.
     * Demonstrates how to access validated token content using @BearerToken CDI injection.
     * This endpoint validates expected token structure and returns error responses if requirements are not met.
     * Unlike interceptor-based endpoints, this manually checks authorization status.
     */
    @GET
    @Path("/interceptor/with-token-access")
    public Response testInterceptorWithTokenAccess() {
        LOGGER.debug("testInterceptorWithTokenAccess - accessing token details via CDI injection");

        // Get BearerTokenResult from CDI-injected Instance
        BearerTokenResult tokenResult = tokenWithScopes.get();

        // Check authorization status and return error response if not authorized
        if (tokenResult.isNotSuccessfullyAuthorized()) {
            LOGGER.debug("Token authorization failed with status: %s", tokenResult.getStatus());
            return tokenResult.createErrorResponse();
        }

        // Get token content - at this point authorization was successful
        AccessTokenContent token = tokenResult.getAccessTokenContent()
                .orElseThrow(() -> new IllegalStateException("Token content missing after successful authorization"));

        // Extract token data
        String userId = token.getSubject().orElse("not-present");
        LOGGER.debug("Token subject: %s, scopes: %s", userId, token.getScopes());

        // Build response with token details
        var data = new HashMap<String, Object>();
        data.put("userId", userId);
        data.put("scopes", token.getScopes());
        data.put("roles", token.getRoles());
        data.put("groups", token.getGroups());

        return Response.ok(new ValidationResponse(true, "Token access successful", data)).build();
    }

    /**
     * Test endpoint with String return type to verify WebApplicationException handling.
     * This tests the fix for the ClassCastException bug - when validation fails,
     * the interceptor should throw WebApplicationException instead of trying to cast
     * the error Response to String.
     *
     * @return String message on success
     */
    @GET
    @Path("/interceptor/string-return")
    @BearerAuth(requiredScopes = {"read"})
    @Produces(MediaType.TEXT_PLAIN)
    public String testInterceptorWithStringReturn() {
        LOGGER.debug("testInterceptorWithStringReturn - business logic executed");
        return "String return type validation successful";
    }

    /**
     * Test endpoint with String return type requiring non-existent scope.
     * This will always fail validation, testing the WebApplicationException path.
     *
     * @return Never returns - should always throw WebApplicationException
     */
    @GET
    @Path("/interceptor/string-return-fail")
    @BearerAuth(requiredScopes = {"non-existent-scope"})
    @Produces(MediaType.TEXT_PLAIN)
    public String testInterceptorWithStringReturnFailure() {
        // cui-rewrite:disable CuiLogRecordPatternRecipe
        LOGGER.error("testInterceptorWithStringReturnFailure - this should never execute!");
        throw new IllegalStateException("This method should never be reached due to failed validation");
    }

    /**
     * Echo endpoint for performance analysis.
     * Touches the TokenValidator (via getSecurityEventCounter) to maintain similar dependency injection
     * overhead but skips actual JWT validation work.
     *
     * @param echoRequest Request containing data to echo back
     * @return Echo response with the same data
     */
    @POST
    @Path("/echo")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response echo(EchoRequest echoRequest) {
        // Touch the TokenValidator to simulate dependency usage
        var securityEventCounter = tokenValidator.getSecurityEventCounter();
        long totalEvents = securityEventCounter.getCounters().values().stream()
                .mapToLong(Long::longValue)
                .sum();
        LOGGER.debug("Echo endpoint called - total security events: %s", totalEvents);

        // Return the exact same data that was sent
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("echo", echoRequest != null ? echoRequest.data() : null);
        responseData.put("eventCounter", totalEvents);

        return Response.ok(new ValidationResponse(true, "Echo successful", responseData)).build();
    }


    // Helper methods

    /**
     * Processes a BearerTokenResult and returns appropriate response.
     */
    private Response processBearerTokenResult(BearerTokenResult tokenResult, String description) {
        LOGGER.debug("processBearerTokenResult called for: %s", description);
        if (tokenResult.isNotSuccessfullyAuthorized()) {
            LOGGER.debug("Bearer token authorization failed for: %s", description);
            return tokenResult.createErrorResponse();
        }

        // Process successful authorization
        var tokenOpt = tokenResult.getAccessTokenContent();
        if (tokenOpt.isPresent()) {
            AccessTokenContent token = tokenOpt.get();
            LOGGER.debug("Bearer token authorized successfully - Subject: %s, Roles: %s, Groups: %s, Scopes: %s",
                    token.getSubject().orElse("none"), token.getRoles(), token.getGroups(), token.getScopes());
            return Response.ok(createTokenResponse(token, description + " is valid")).build();
        } else {
            // This shouldn't happen in normal cases with successful authorization
            LOGGER.debug("Bearer token authorized but no AccessTokenContent present for: %s", description);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ValidationResponse(false, "Internal error: token content missing"))
                    .build();
        }
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

    public record EchoRequest(Map<String, Object> data) {
    }

    public record ValidationResponse(boolean valid, String message, Map<String, Object> data) {
        // Convenience constructor for backwards compatibility
        public ValidationResponse(boolean valid, String message) {
            this(valid, message, Collections.emptyMap());
        }
    }
}