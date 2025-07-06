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
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive REST endpoint for JWT validation operations using Mutiny Uni.
 * This endpoint provides non-blocking, reactive JWT validation for performance comparison
 * against the traditional blocking approach with virtual threads.
 */
@Path("/jwt/reactive")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RegisterForReflection
public class ReactiveJwtValidationEndpoint {

    private static final CuiLogger LOGGER = new CuiLogger(ReactiveJwtValidationEndpoint.class);

    private final TokenValidator tokenValidator;

    @Inject
    public ReactiveJwtValidationEndpoint(TokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
        LOGGER.info("ReactiveJwtValidationEndpoint initialized with TokenValidator: %s", (tokenValidator != null));
    }

    /**
     * Reactively validates a JWT access token using Mutiny Uni.
     *
     * @param token JWT token from Authorization header
     * @return Uni containing validation result
     */
    @POST
    @Path("/validate")
    public Uni<Response> validateToken(@HeaderParam("Authorization") String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            LOGGER.warn("Missing or invalid Authorization header");
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "Missing or invalid Authorization header"))
                    .build()
            );
        }

        String jwtToken = token.substring(7); // Remove "Bearer " prefix
        LOGGER.debug("Reactive access token validation: %s", jwtToken);

        return Uni.createFrom().item(() -> {
            try {
                tokenValidator.createAccessToken(jwtToken);
                return Response.ok(new JwtValidationEndpoint.ValidationResponse(true, "Access token is valid"))
                    .build();
            } catch (TokenValidationException e) {
                LOGGER.warn("Reactive access token validation error: %s", e.getMessage());
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "Access token validation failed: " + e.getMessage()))
                    .build();
            }
        }).emitOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Reactively validates a JWT ID token using Mutiny Uni.
     *
     * @param tokenRequest Request containing the ID token
     * @return Uni containing validation result
     */
    @POST
    @Path("/validate/id-token")
    public Uni<Response> validateIdToken(JwtValidationEndpoint.TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.token() == null || tokenRequest.token().trim().isEmpty()) {
            LOGGER.warn("Missing or empty ID token in request body");
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "Missing or empty ID token in request body"))
                    .build()
            );
        }

        String jwtToken = tokenRequest.token().trim();
        LOGGER.debug("Reactive ID token validation: %s", jwtToken);

        return Uni.createFrom().item(() -> {
            try {
                tokenValidator.createIdToken(jwtToken);
                return Response.ok(new JwtValidationEndpoint.ValidationResponse(true, "ID token is valid"))
                    .build();
            } catch (TokenValidationException e) {
                LOGGER.warn("Reactive ID token validation error: %s", e.getMessage());
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "ID token validation failed: " + e.getMessage()))
                    .build();
            }
        }).emitOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Reactively validates a JWT refresh token using Mutiny Uni.
     *
     * @param tokenRequest Request containing the refresh token
     * @return Uni containing validation result
     */
    @POST
    @Path("/validate/refresh-token")
    public Uni<Response> validateRefreshToken(JwtValidationEndpoint.TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.token() == null || tokenRequest.token().trim().isEmpty()) {
            LOGGER.warn("Missing or empty refresh token in request body");
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "Missing or empty refresh token in request body"))
                    .build()
            );
        }

        String jwtToken = tokenRequest.token().trim();
        LOGGER.debug("Reactive refresh token validation: %s", jwtToken);

        return Uni.createFrom().item(() -> {
            try {
                tokenValidator.createRefreshToken(jwtToken);
                return Response.ok(new JwtValidationEndpoint.ValidationResponse(true, "Refresh token is valid"))
                    .build();
            } catch (TokenValidationException e) {
                LOGGER.warn("Reactive refresh token validation error: %s", e.getMessage());
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "Refresh token validation failed: " + e.getMessage()))
                    .build();
            }
        }).emitOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }
}