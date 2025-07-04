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

import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.tools.logging.CuiLogger;
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
    public JwtValidationEndpoint(TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
        LOGGER.info("JwtValidationEndpoint initialized with TokenValidator: %s", (tokenValidator != null));
    }

    /**
     * Validates a JWT access token - primary endpoint for integration testing and benchmarking.
     *
     * @param token JWT token from Authorization header
     * @return Validation result
     */
    @POST
    @Path("/validate")
    public Response validateToken(@HeaderParam("Authorization") String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            LOGGER.warn("Missing or invalid Authorization header");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ValidationResponse(false, "Missing or invalid Authorization header"))
                    .build();
        }

        String jwtToken = token.substring(7); // Remove "Bearer " prefix
        LOGGER.debug("Access token validation: %s", jwtToken);
        try {
            tokenValidator.createAccessToken(jwtToken);
            return Response.ok(new ValidationResponse(true, "Access token is valid"))
                    .build();
        } catch (TokenValidationException e) {
            LOGGER.warn("Access token validation error: %s", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ValidationResponse(false, "Access token validation failed: " + e.getMessage()))
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


    // Request and Response DTOs
    public record TokenRequest(String token) {
    }

    public record ValidationResponse(boolean valid, String message) {
    }
}