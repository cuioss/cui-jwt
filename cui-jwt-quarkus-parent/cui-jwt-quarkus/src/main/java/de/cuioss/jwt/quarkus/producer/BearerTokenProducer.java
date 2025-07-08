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

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO.BEARER_TOKEN_VALIDATION_SUCCESS;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_ANNOTATION_MISSING;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_MISSING_OR_INVALID;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_REQUIREMENTS_NOT_MET;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_VALIDATION_FAILED;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_MISSING_SCOPES;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_MISSING_ROLES;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_MISSING_GROUPS;
import de.cuioss.jwt.quarkus.annotation.BearerToken;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * CDI producer for creating request-scoped AccessTokenContent instances from HTTP Authorization headers.
 * <p>
 * This producer extracts bearer tokens from the HTTP Authorization header, validates them using the
 * configured TokenValidator, and checks for required scopes, roles, and groups as specified by the
 * {@link BearerToken} annotation.
 * <p>
 * The producer returns an {@link Optional}&lt;{@link AccessTokenContent}&gt; that:
 * <ul>
 *   <li>Contains the validated token content when all validations pass</li>
 *   <li>Is empty when the token is missing, invalid, or doesn't meet the requirements</li>
 * </ul>
 * <p>
 * Usage example:
 * <pre>{@code
 * @Inject
 * @BearerToken(requiredScopes = {"read"}, requiredRoles = {"user"})
 * private Optional<AccessTokenContent> accessToken;
 * 
 * public void someMethod() {
 *     if (accessToken.isPresent()) {
 *         AccessTokenContent token = accessToken.get();
 *         // Use validated token
 *     } else {
 *         // Handle missing or invalid token
 *     }
 * }
 * }</pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@Dependent
public class BearerTokenProducer {

    private static final CuiLogger LOGGER = new CuiLogger(BearerTokenProducer.class);
    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    TokenValidator tokenValidator;

    @Inject
    HttpContextService httpContextService;

    /**
     * Produces a basic request-scoped Optional&lt;AccessTokenContent&gt; from the HTTP Authorization header.
     * This producer handles tokens without specific requirements.
     *
     * @return Optional containing validated AccessTokenContent, or empty if validation fails
     */
    @Produces
    @BearerToken
    @RequestScoped
    public Optional<AccessTokenContent> produceBasicAccessTokenContent() {
        return produceAccessTokenContentWithRequirements(new String[0], new String[0], new String[0]);
    }

    /**
     * Common method to produce AccessTokenContent with specific requirements.
     *
     * @param requiredScopes Required scopes for the token
     * @param requiredRoles Required roles for the token
     * @param requiredGroups Required groups for the token
     * @return Optional containing validated AccessTokenContent, or empty if validation fails
     */
    private Optional<AccessTokenContent> produceAccessTokenContentWithRequirements(
            String[] requiredScopes, String[] requiredRoles, String[] requiredGroups) {
        
        String bearerToken = extractBearerToken();
        if (bearerToken == null) {
            LOGGER.debug(BEARER_TOKEN_MISSING_OR_INVALID::format);
            return Optional.empty();
        }

        try {
            AccessTokenContent tokenContent = tokenValidator.createAccessToken(bearerToken);
            
            if (validateRequirements(tokenContent, requiredScopes, requiredRoles, requiredGroups)) {
                LOGGER.debug(BEARER_TOKEN_VALIDATION_SUCCESS::format);
                return Optional.of(tokenContent);
            } else {
                LOGGER.debug(BEARER_TOKEN_REQUIREMENTS_NOT_MET::format);
                return Optional.empty();
            }
        } catch (Exception e) {
            LOGGER.debug(e, BEARER_TOKEN_VALIDATION_FAILED.format(e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Extracts the bearer token from the HTTP Authorization header.
     * 
     * @return the bearer token string without the "Bearer " prefix, or null if not present/invalid
     */
    private String extractBearerToken() {
        Optional<String> authHeaderOpt = httpContextService.getAuthorizationHeader();
        if (authHeaderOpt.isEmpty()) {
            return null;
        }

        String authHeader = authHeaderOpt.get();
        if (!authHeader.startsWith(BEARER_PREFIX)) {
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
            String[] requiredScopes, String[] requiredRoles, String[] requiredGroups) {
        
        if (requiredScopes.length > 0) {
            List<String> requiredScopesList = Arrays.asList(requiredScopes);
            if (!tokenContent.providesScopes(requiredScopesList)) {
                LOGGER.debug(BEARER_TOKEN_MISSING_SCOPES.format(requiredScopesList, tokenContent.getScopes()));
                return false;
            }
        }

        if (requiredRoles.length > 0) {
            List<String> requiredRolesList = Arrays.asList(requiredRoles);
            if (!tokenContent.providesRoles(requiredRolesList)) {
                LOGGER.debug(BEARER_TOKEN_MISSING_ROLES.format(requiredRolesList, tokenContent.getRoles()));
                return false;
            }
        }

        if (requiredGroups.length > 0) {
            List<String> requiredGroupsList = Arrays.asList(requiredGroups);
            if (!tokenContent.providesGroups(requiredGroupsList)) {
                LOGGER.debug(BEARER_TOKEN_MISSING_GROUPS.format(requiredGroupsList, tokenContent.getGroups()));
                return false;
            }
        }

        return true;
    }
}