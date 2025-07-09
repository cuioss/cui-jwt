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
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


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
 * validation information and CDI producer methods that return {@link AccessTokenContent} directly for injection.
 * <p>
 * CDI injection usage example:
 * <pre>{@code
 * @Inject
 * @BearerToken(requiredScopes = {"read", "write"})
 * Instance<AccessTokenContent> tokenInstance;
 *
 * public void someMethod() {
 *     if (tokenInstance.isResolvable()) {
 *         AccessTokenContent token = tokenInstance.get();
 *         // Use validated token
 *     } else {
 *         // Handle missing or invalid token
 *     }
 * }
 * }</pre>
 * <p>
 * Direct service usage example:
 * <pre>{@code
 * @Inject
 * BearerTokenProducer tokenService;
 *
 * public void someMethod() {
 *     BearerTokenResult result = tokenService.getBearerTokenResult(
 *         List.of("read"), List.of("user"), List.of("admin"));
 *
 *     if (result.isSuccessfulAuthorized()) {
 *         AccessTokenContent content = result.getAccessTokenContent().get();
 *         // Use validated token
 *     } else {
 *         // Handle validation failure with detailed status information
 *         switch (result.getStatus()) {
 *             case PARSING_ERROR:
 *                 // Handle parsing errors
 *                 break;
 *             case CONSTRAINT_VIOLATION:
 *                 // Handle missing scopes/roles/groups
 *                 break;
 *             default:
 *                 // Handle other cases
 *         }
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
        return getAccessTokenContentWithRequirements(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Gets AccessTokenContent with specific requirements validation.
     *
     * @param requiredScopes Required scopes for the token
     * @param requiredRoles Required roles for the token
     * @param requiredGroups Required groups for the token
     * @return Optional containing validated AccessTokenContent, or empty if validation fails
     */
    public Optional<AccessTokenContent> getAccessTokenContentWithRequirements(
            List<String> requiredScopes, List<String> requiredRoles, List<String> requiredGroups) {
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
    public BearerTokenResult getBearerTokenResult(
            List<String> requiredScopes, List<String> requiredRoles, List<String> requiredGroups) {

        // Check if we can access the HTTP request
        Optional<HttpServletRequest> httpServletRequest = servletObjectsResolver.resolveHttpServletRequest();
        if (httpServletRequest.isEmpty()) {
            return BearerTokenResult.couldNotAccessRequest(requiredScopes, requiredRoles, requiredGroups);
        }

        String bearerToken = extractBearerToken();
        if (bearerToken == null) {
            LOGGER.debug(BEARER_TOKEN_MISSING_OR_INVALID::format);
            return BearerTokenResult.noTokenGiven(requiredScopes, requiredRoles, requiredGroups);
        }

        try {
            AccessTokenContent tokenContent = tokenValidator.createAccessToken(bearerToken);

            if (validateRequirements(tokenContent, requiredScopes, requiredRoles, requiredGroups)) {
                LOGGER.debug(BEARER_TOKEN_VALIDATION_SUCCESS::format);
                return BearerTokenResult.success(tokenContent, requiredScopes, requiredRoles, requiredGroups);
            } else {
                LOGGER.debug(BEARER_TOKEN_REQUIREMENTS_NOT_MET::format);
                return BearerTokenResult.constraintViolation(requiredScopes, requiredRoles, requiredGroups);
            }
        } catch (TokenValidationException e) {
            LOGGER.debug(e, BEARER_TOKEN_VALIDATION_FAILED.format(e.getMessage()));
            return BearerTokenResult.parsingError(e, requiredScopes, requiredRoles, requiredGroups);
        }
    }

    /**
     * Gets comprehensive bearer token validation result for the current request without specific requirements.
     *
     * @return BearerTokenResult containing detailed validation information
     */
    public BearerTokenResult getBearerTokenResult() {
        return getBearerTokenResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Extracts the bearer token from the HTTP Authorization header.
     *
     * @return the bearer token string without the "Bearer " prefix, or null if not present/invalid
     */
    private String extractBearerToken() {
        Optional<HttpServletRequest> httpServletRequest = servletObjectsResolver.resolveHttpServletRequest();
        if (httpServletRequest.isEmpty()) {
            return null;
        }

        String authHeader = httpServletRequest.get().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authHeader.substring(BEARER_PREFIX.length());
    }

    /**
     * Validates that the token content meets all specified requirements.
     *
     * @param tokenContent the validated token content
     * @param requiredScopes Required scopes for the token
     * @param requiredRoles Required roles for the token
     * @param requiredGroups Required groups for the token
     * @return true if all requirements are met, false otherwise
     */
    private boolean validateRequirements(AccessTokenContent tokenContent,
            List<String> requiredScopes, List<String> requiredRoles, List<String> requiredGroups) {

        if (!requiredScopes.isEmpty()) {
            if (!tokenContent.providesScopes(requiredScopes)) {
                LOGGER.debug(BEARER_TOKEN_MISSING_SCOPES.format(requiredScopes, tokenContent.getScopes()));
                return false;
            }
        }

        if (!requiredRoles.isEmpty()) {
            if (!tokenContent.providesRoles(requiredRoles)) {
                LOGGER.debug(BEARER_TOKEN_MISSING_ROLES.format(requiredRoles, tokenContent.getRoles()));
                return false;
            }
        }

        if (!requiredGroups.isEmpty()) {
            if (!tokenContent.providesGroups(requiredGroups)) {
                LOGGER.debug(BEARER_TOKEN_MISSING_GROUPS.format(requiredGroups, tokenContent.getGroups()));
                return false;
            }
        }

        return true;
    }

    /**
     * Produces the current request's AccessTokenContent as a CDI bean.
     * <p>
     * This producer method extracts the bearer token from the HTTP Authorization header
     * and validates it using the configured TokenValidator. The validation includes
     * checking for required scopes, roles, and groups specified in the {@link BearerToken}
     * annotation.
     * <p>
     * The producer method is @Dependent scoped, which means it will be created fresh
     * for each injection point. If validation fails or the token is missing, this method
     * returns null, which will cause CDI injection to fail. Consumers should use
     * {@link jakarta.enterprise.inject.Instance} to safely inject the token and check for availability.
     * <p>
     * Usage example:
     * <pre>{@code
     * @Inject
     * @BearerToken(requiredScopes = {"read", "write"})
     * Instance<AccessTokenContent> tokenInstance;
     *
     * public void someMethod() {
     *     if (tokenInstance.isResolvable()) {
     *         AccessTokenContent token = tokenInstance.get();
     *         // Use validated token
     *     } else {
     *         // Handle missing or invalid token
     *     }
     * }
     * }</pre>
     *
     * @param injectionPoint the CDI injection point containing the BearerToken annotation
     * @return the validated AccessTokenContent or null if validation fails
     */
    @Produces
    @BearerToken
    public AccessTokenContent produceAccessTokenContent(InjectionPoint injectionPoint) {
        BearerToken annotation = injectionPoint.getAnnotated().getAnnotation(BearerToken.class);

        // Apply pre-1.0 rule: Use collection as early as possible
        List<String> requiredScopes = annotation != null ? List.of(annotation.requiredScopes()) : Collections.emptyList();
        List<String> requiredRoles = annotation != null ? List.of(annotation.requiredRoles()) : Collections.emptyList();
        List<String> requiredGroups = annotation != null ? List.of(annotation.requiredGroups()) : Collections.emptyList();

        BearerTokenResult result = getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);
        return result.getAccessTokenContent().orElse(null);
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
    @Produces
    @BearerToken
    public BearerTokenResult produceBearerTokenResult(InjectionPoint injectionPoint) {
        BearerToken annotation = injectionPoint.getAnnotated().getAnnotation(BearerToken.class);

        // Apply pre-1.0 rule: Use collection as early as possible
        List<String> requiredScopes = annotation != null ? List.of(annotation.requiredScopes()) : Collections.emptyList();
        List<String> requiredRoles = annotation != null ? List.of(annotation.requiredRoles()) : Collections.emptyList();
        List<String> requiredGroups = annotation != null ? List.of(annotation.requiredGroups()) : Collections.emptyList();

        return getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);
    }
}