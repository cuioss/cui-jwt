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

import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * NOOP (No-Operation) REST endpoint for JWT validation baseline performance measurement.
 * This endpoint provides the same API as the real JWT validation endpoints but performs
 * no actual validation - it simply logs the token and returns success.
 * 
 * This allows us to measure the pure overhead of the HTTP/REST framework and
 * understand how much time is spent in actual JWT validation vs infrastructure.
 */
@Path("/jwt/noop")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RegisterForReflection
@RunOnVirtualThread
public class NoopJwtValidationEndpoint {

    private static final CuiLogger LOGGER = new CuiLogger(NoopJwtValidationEndpoint.class);

    public NoopJwtValidationEndpoint() {
        LOGGER.info("NoopJwtValidationEndpoint initialized - this endpoint performs NO validation");
    }

    /**
     * NOOP validation of a JWT access token - always returns success.
     * This simulates the JWT validation endpoint without performing actual validation.
     *
     * @param token JWT token from Authorization header
     * @return Always returns successful validation response
     */
    @POST
    @Path("/validate")
    public Response validateToken(@HeaderParam("Authorization") String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            LOGGER.warn("NOOP: Missing or invalid Authorization header");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "Missing or invalid Authorization header"))
                    .build();
        }

        String jwtToken = token.substring(7); // Remove "Bearer " prefix
        LOGGER.debug("NOOP access token validation (no actual validation): %s", jwtToken);
        
        // This is our "business logic" - just logging the token
        // No JWT parsing, signature validation, or claims checking
        
        return Response.ok(new JwtValidationEndpoint.ValidationResponse(true, "Access token is valid (NOOP)"))
                .build();
    }

    /**
     * NOOP validation of a JWT ID token - always returns success.
     *
     * @param tokenRequest Request containing the ID token
     * @return Always returns successful validation response
     */
    @POST
    @Path("/validate/id-token")
    public Response validateIdToken(JwtValidationEndpoint.TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.token() == null || tokenRequest.token().trim().isEmpty()) {
            LOGGER.warn("NOOP: Missing or empty ID token in request body");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "Missing or empty ID token in request body"))
                    .build();
        }

        String jwtToken = tokenRequest.token().trim();
        LOGGER.debug("NOOP ID token validation (no actual validation): %s", jwtToken);
        
        // This is our "business logic" - just logging the token
        // No JWT parsing, signature validation, or claims checking
        
        return Response.ok(new JwtValidationEndpoint.ValidationResponse(true, "ID token is valid (NOOP)"))
                .build();
    }

    /**
     * NOOP validation of a JWT refresh token - always returns success.
     *
     * @param tokenRequest Request containing the refresh token
     * @return Always returns successful validation response
     */
    @POST
    @Path("/validate/refresh-token")
    public Response validateRefreshToken(JwtValidationEndpoint.TokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.token() == null || tokenRequest.token().trim().isEmpty()) {
            LOGGER.warn("NOOP: Missing or empty refresh token in request body");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JwtValidationEndpoint.ValidationResponse(false, "Missing or empty refresh token in request body"))
                    .build();
        }

        String jwtToken = tokenRequest.token().trim();
        LOGGER.debug("NOOP refresh token validation (no actual validation): %s", jwtToken);
        
        // This is our "business logic" - just logging the token
        // No JWT parsing, signature validation, or claims checking
        
        return Response.ok(new JwtValidationEndpoint.ValidationResponse(true, "Refresh token is valid (NOOP)"))
                .build();
    }
}