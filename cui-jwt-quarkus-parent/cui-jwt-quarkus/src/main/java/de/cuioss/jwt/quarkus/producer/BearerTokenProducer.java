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

import de.cuioss.jwt.quarkus.annotation.BearerToken;
import de.cuioss.jwt.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.jwt.quarkus.servlet.HttpServletRequestResolver;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import lombok.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO.BEARER_TOKEN_VALIDATION_SUCCESS;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.*;

/**
 * CDI producer for extracting and validating bearer tokens from HTTP Authorization headers.
 * <p>
 * This producer extracts bearer tokens from the HTTP Authorization header using HttpServletRequestResolver,
 * validates them using the configured TokenValidator, and checks for required scopes, roles, and groups.
 * <p>
 * The producer provides both service methods that return {@link BearerTokenResult} with comprehensive
 * validation information and a CDI producer method that returns {@link BearerTokenResult} directly for injection.
 * <p>
 * CDI injection usage example:
 * <pre>{@code
 * @RequestScoped
 * @Path("/api")
 * public class MyResource {
 *
 *     @Inject
 *     @BearerToken(requiredScopes = {"read", "write"})
 *     BearerTokenResult tokenResult;
 *
 *     @GET
 *     public Response getData() {
 *         if (tokenResult.isSuccessfullyAuthorized()) {
 *             AccessTokenContent token = tokenResult.getAccessTokenContent().get();
 *             // Use validated token
 *             return Response.ok(token.getSubject()).build();
 *         } else {
 *             // Return appropriate error response
 *             return tokenResult.errorResponse();
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>Important:</strong> For Application-Scoped beans, use {@link jakarta.inject.Provider} since
 * BearerTokenResult is RequestScoped. The preferred way is for the containing class to be RequestScoped
 * as well, then you can use constructor injection:
 * <pre>{@code
 * @RequestScoped
 * public class MyService {
 *     private final BearerTokenResult tokenResult;
 *
 *     @Inject
 *     public MyService(@BearerToken(requiredRoles = {"admin"}) BearerTokenResult tokenResult) {
 *         this.tokenResult = tokenResult;
 *     }
 * }
 * }</pre>
 * <p>
 * For direct service usage, use the CDI producer method with annotations:
 * <pre>{@code
 * @Inject
 * @BearerToken(requiredScopes = {"read"}, requiredRoles = {"user"})
 * BearerTokenResult tokenResult;
 *
 * public void someMethod() {
 *     if (tokenResult.isSuccessfullyAuthorized()) {
 *         AccessTokenContent content = tokenResult.getAccessTokenContent().get();
 *         // Use validated token
 *     } else {
 *         // Handle validation failure with detailed status information
 *         return tokenResult.errorResponse();
 *     }
 * }
 * }</pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ApplicationScoped
@RegisterForReflection
public class BearerTokenProducer {

    static final CuiLogger LOGGER = new CuiLogger(BearerTokenProducer.class);
    static final String BEARER_PREFIX = "Bearer ";

    private final TokenValidator tokenValidator;
    private final HttpServletRequestResolver servletObjectsResolver;

    @Inject
    public BearerTokenProducer(TokenValidator tokenValidator,
            @ServletObjectsResolver(ServletObjectsResolver.Variant.RESTEASY) HttpServletRequestResolver servletObjectsResolver) {
        this.tokenValidator = tokenValidator;
        this.servletObjectsResolver = servletObjectsResolver;
    }

    /**
     * Gets the current request's AccessTokenContent from the HTTP Authorization header.
     * This method handles tokens without specific requirements.
     *
     * @return Optional containing validated AccessTokenContent, or empty if validation fails
     */
    public Optional<AccessTokenContent> getAccessTokenContent() {
        return getAccessTokenContentWithRequirements(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Gets AccessTokenContent with specific requirements validation.
     *
     * @param requiredScopes Required scopes for the token
     * @param requiredRoles Required roles for the token
     * @param requiredGroups Required groups for the token
     * @return Optional containing validated AccessTokenContent, or empty if validation fails
     */
    private Optional<AccessTokenContent> getAccessTokenContentWithRequirements(
            Set<String> requiredScopes, Set<String> requiredRoles, Set<String> requiredGroups) {
        BearerTokenResult result = getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);
        return result.getAccessTokenContent();
    }

    /**
     * Gets comprehensive bearer token validation result with detailed status information.
     *
     * @param requiredScopes Required scopes for the token
     * @param requiredRoles Required roles for the token
     * @param requiredGroups Required groups for the token
     * @return BearerTokenResult containing detailed validation information
     */
    @NonNull
    private BearerTokenResult getBearerTokenResult(
            Set<String> requiredScopes, Set<String> requiredRoles, Set<String> requiredGroups) {

        String bearerToken = extractBearerTokenFromHeaderMap();
        if (bearerToken == null) {
            // extractBearerTokenFromHeaderMap returns null for both "no headers accessible" and "no token given"
            // We need to check if we can access headers to distinguish between these cases
            Optional<Map<String, List<String>>> headerMap = servletObjectsResolver.resolveHeaderMap();
            if (headerMap.isEmpty()) {
                return BearerTokenResult.couldNotAccessRequest(requiredScopes, requiredRoles, requiredGroups);
            } else {
                LOGGER.debug(BEARER_TOKEN_MISSING_OR_INVALID::format);
                return BearerTokenResult.noTokenGiven(requiredScopes, requiredRoles, requiredGroups);
            }
        }

        try {
            AccessTokenContent tokenContent = tokenValidator.createAccessToken(bearerToken);

            // Determine missing scopes, roles, and groups
            Set<String> missingScopes = tokenContent.determineMissingScopes(requiredScopes);
            Set<String> missingRoles = tokenContent.determineMissingRoles(requiredRoles);
            Set<String> missingGroups = tokenContent.determineMissingGroups(requiredGroups);

            if (missingScopes.isEmpty() && missingRoles.isEmpty() && missingGroups.isEmpty()) {
                LOGGER.debug(BEARER_TOKEN_VALIDATION_SUCCESS::format);
                return BearerTokenResult.withAccessTokenContent(tokenContent, requiredScopes, requiredRoles, requiredGroups)
                    .status(BearerTokenStatus.FULLY_VERIFIED)
                    .build();
            } else {
                LOGGER.debug(BEARER_TOKEN_REQUIREMENTS_NOT_MET::format);
                return BearerTokenResult.builder()
                    .status(BearerTokenStatus.CONSTRAINT_VIOLATION)
                    .missingScopes(missingScopes)
                    .missingRoles(missingRoles)
                    .missingGroups(missingGroups)
                    .build();
            }
        } catch (TokenValidationException e) {
            LOGGER.debug(e, BEARER_TOKEN_VALIDATION_FAILED.format(e.getMessage()));
            return BearerTokenResult.fromException(e)
                .status(BearerTokenStatus.PARSING_ERROR)
                .missingScopes(Set.copyOf(requiredScopes))
                .missingRoles(Set.copyOf(requiredRoles))
                .missingGroups(Set.copyOf(requiredGroups))
                .build();
        }
    }

    /**
     * Extracts the bearer token from the HTTP Authorization header using header map resolution.
     *
     * @return the bearer token string without the "Bearer " prefix, or null if not present/invalid
     */
    private String extractBearerTokenFromHeaderMap() {
        Optional<Map<String, List<String>>> headerMap = servletObjectsResolver.resolveHeaderMap();
        return headerMap.map(this::extractBearerTokenFromHeaders).orElse(null);
    }

    /**
     * Extracts the bearer token from the given header map's Authorization header.
     *
     * @param headers the HTTP headers map
     * @return the bearer token string without the "Bearer " prefix, or null if not present/invalid
     */
    private String extractBearerTokenFromHeaders(Map<String, List<String>> headers) {
        List<String> authHeaders = headers.get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return null;
        }

        String authHeader = authHeaders.getFirst(); // Use first Authorization header
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authHeader.substring(BEARER_PREFIX.length());
    }



    /**
     * Produces the current request's BearerTokenResult as a CDI bean.
     * <p>
     * This producer method provides comprehensive bearer token validation information
     * including detailed status, error information, and the validated token content.
     * This method extracts the bearer token from the HTTP Authorization header
     * and validates it using the configured TokenValidator.
     * <p>
     * The producer method is @Dependent scoped, which means it will be created fresh
     * for each injection point. Unlike the AccessTokenContent producer, this method
     * always returns a valid BearerTokenResult object containing detailed information
     * about the validation outcome.
     * <p>
     * Usage example:
     * <pre>{@code
     * @Inject
     * @BearerToken(requiredScopes = {"read", "write"})
     * BearerTokenResult tokenResult;
     *
     * public void someMethod() {
     *     switch (tokenResult.getStatus()) {
     *         case FULLY_VERIFIED:
     *             AccessTokenContent token = tokenResult.getAccessTokenContent().get();
     *             // Use validated token
     *             break;
     *         case PARSING_ERROR:
     *             // Handle parsing errors with detailed information
     *             EventType eventType = tokenResult.getEventType().get();
     *             String message = tokenResult.getMessage().get();
     *             break;
     *         default:
     *             // Handle other validation failures
     *     }
     * }
     * }</pre>
     *
     * @param injectionPoint the CDI injection point containing the BearerToken annotation
     * @return the BearerTokenResult containing detailed validation information
     */
    @NonNull
    @Produces
    @BearerToken
    public BearerTokenResult produceBearerTokenResult(InjectionPoint injectionPoint) {
        BearerToken annotation = injectionPoint.getAnnotated().getAnnotation(BearerToken.class);

        // Apply pre-1.0 rule: Use collection as early as possible
        Set<String> requiredScopes = annotation != null ? Set.of(annotation.requiredScopes()) : Collections.emptySet();
        Set<String> requiredRoles = annotation != null ? Set.of(annotation.requiredRoles()) : Collections.emptySet();
        Set<String> requiredGroups = annotation != null ? Set.of(annotation.requiredGroups()) : Collections.emptySet();

        return getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);
    }
}