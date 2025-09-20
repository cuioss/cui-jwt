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
package de.cuioss.jwt.quarkus.producer;

import de.cuioss.jwt.quarkus.annotation.BearerToken;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;
import java.util.Set;

/**
 * Result object containing comprehensive information about bearer token validation.
 * <p>
 * This class provides detailed information about the outcome of bearer token processing,
 * including the validation status, the validated token content (if successful),
 * missing scopes/roles/groups (if constraint violations occurred), and
 * error details (if validation failed).
 * <p>
 * The result includes:
 * <ul>
 *   <li>The validation status indicating what happened during processing</li>
 *   <li>The validated AccessTokenContent if successful</li>
 *   <li>Missing scopes, roles, and groups that caused constraint violations</li>
 *   <li>Error details from TokenValidationException if parsing failed</li>
 * </ul>
 * <p>
 * Usage example with CDI injection:
 * <pre>{@code
 * @Inject
 * @BearerToken(requiredScopes = {"read"}, requiredRoles = {"user"})
 * BearerTokenResult tokenResult;
 *
 * public Response handleRequest() {
 *     if (tokenResult.isSuccessfullyAuthorized()) {
 *         AccessTokenContent token = tokenResult.getAccessTokenContent().get();
 *         // Use validated token
 *         return Response.ok().build();
 *     } else {
 *         // Return appropriate error response
 *         return BearerTokenResponseFactory.createResponse(tokenResult);
 *     }
 * }
 * }</pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@Value
@Builder
@RegisterForReflection
@SuppressWarnings("java:S1948") // owolff: All implementations are Serializable
public class BearerTokenResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NonNull
    BearerTokenStatus status;

    /**
     * Gets the scopes that are missing from the token.
     */
    @Builder.Default
    Set<String> missingScopes = Set.of();

    /**
     * Gets the roles that are missing from the token.
     */
    @Builder.Default
    Set<String> missingRoles = Set.of();

    /**
     * -- GETTER --
     * Gets the groups that are missing from the token.
     */
    @Builder.Default
    Set<String> missingGroups = Set.of();

    AccessTokenContent accessTokenContent;

    EventType errorEventType;

    String errorMessage;


    /**
     * Creates a BearerTokenResult for successful token validation.
     *
     * @param accessTokenContent the validated access token content
     * @param requiredScopes     the scopes that were required for validation
     * @param requiredRoles      the roles that were required for validation
     * @param requiredGroups     the groups that were required for validation
     * @return a BearerTokenResult indicating successful validation
     */
    @NonNull
    public static BearerTokenResult success(AccessTokenContent accessTokenContent,
            Set<String> requiredScopes, Set<String> requiredRoles, Set<String> requiredGroups) {
        // For success case, calculate missing values (should all be empty)
        Set<String> missingScopes = accessTokenContent != null ? accessTokenContent.determineMissingScopes(requiredScopes) : Set.of();
        Set<String> missingRoles = accessTokenContent != null ? accessTokenContent.determineMissingRoles(requiredRoles) : Set.of();
        Set<String> missingGroups = accessTokenContent != null ? accessTokenContent.determineMissingGroups(requiredGroups) : Set.of();

        return builder()
                .status(BearerTokenStatus.FULLY_VERIFIED)
                .accessTokenContent(accessTokenContent)
                .missingScopes(missingScopes)
                .missingRoles(missingRoles)
                .missingGroups(missingGroups)
                .build();
    }


    /**
     * Creates a BearerTokenResult for failed token validation due to parsing error.
     *
     * @param exception      the TokenValidationException that occurred during parsing
     * @param requiredScopes the scopes that were required for validation
     * @param requiredRoles  the roles that were required for validation
     * @param requiredGroups the groups that were required for validation
     * @return a BearerTokenResult indicating parsing error
     */
    @NonNull
    public static BearerTokenResult parsingError(TokenValidationException exception,
            Set<String> requiredScopes, Set<String> requiredRoles, Set<String> requiredGroups) {
        var builder = builder();
        if (exception != null) {
            builder.errorEventType(exception.getEventType())
                    .errorMessage(exception.getMessage());
        }
        return builder
                .status(BearerTokenStatus.PARSING_ERROR)
                .missingScopes(requiredScopes)
                .missingRoles(requiredRoles)
                .missingGroups(requiredGroups)
                .build();
    }


    /**
     * Creates a BearerTokenResult for failed token validation due to constraint violations.
     *
     * @param missingScopes the scopes that are missing from the token
     * @param missingRoles  the roles that are missing from the token
     * @param missingGroups the groups that are missing from the token
     * @return a BearerTokenResult indicating constraint violation
     */
    @NonNull
    public static BearerTokenResult constraintViolation(Set<String> missingScopes,
            Set<String> missingRoles, Set<String> missingGroups) {
        return builder()
                .status(BearerTokenStatus.CONSTRAINT_VIOLATION)
                .missingScopes(missingScopes)
                .missingRoles(missingRoles)
                .missingGroups(missingGroups)
                .build();
    }


    /**
     * Creates a BearerTokenResult for cases where no token was provided.
     *
     * @param requiredScopes the scopes that were required for validation
     * @param requiredRoles  the roles that were required for validation
     * @param requiredGroups the groups that were required for validation
     * @return a BearerTokenResult indicating no token was given
     */
    @NonNull
    public static BearerTokenResult noTokenGiven(Set<String> requiredScopes,
            Set<String> requiredRoles, Set<String> requiredGroups) {
        return builder()
                .status(BearerTokenStatus.NO_TOKEN_GIVEN)
                .missingScopes(requiredScopes)
                .missingRoles(requiredRoles)
                .missingGroups(requiredGroups)
                .build();
    }


    /**
     * Creates a BearerTokenResult for cases where the request could not be accessed.
     *
     * @param requiredScopes the scopes that were required for validation
     * @param requiredRoles  the roles that were required for validation
     * @param requiredGroups the groups that were required for validation
     * @return a BearerTokenResult indicating request access failure
     */
    @NonNull
    public static BearerTokenResult couldNotAccessRequest(Set<String> requiredScopes,
            Set<String> requiredRoles, Set<String> requiredGroups) {
        return builder()
                .status(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST)
                .missingScopes(requiredScopes)
                .missingRoles(requiredRoles)
                .missingGroups(requiredGroups)
                .build();
    }

    /**
     * Creates a BearerTokenResult for invalid bearer token requests (RFC 6750 violation).
     * <p>
     * This is used when the Authorization header contains "Bearer " but the token
     * is empty or contains invalid characters according to RFC 6750 section 2.1.
     *
     * @param errorMessage   the error message describing the invalid request
     * @param requiredScopes the scopes that were required for validation
     * @param requiredRoles  the roles that were required for validation
     * @param requiredGroups the groups that were required for validation
     * @return a BearerTokenResult indicating an invalid request
     */
    @NonNull
    public static BearerTokenResult invalidRequest(String errorMessage, Set<String> requiredScopes,
            Set<String> requiredRoles, Set<String> requiredGroups) {
        return builder()
                .status(BearerTokenStatus.INVALID_REQUEST)
                .errorMessage(errorMessage)
                .missingScopes(requiredScopes)
                .missingRoles(requiredRoles)
                .missingGroups(requiredGroups)
                .build();
    }

    /**
     * Gets the validated AccessTokenContent if validation was successful.
     *
     * @return Optional containing the AccessTokenContent, or empty if validation failed
     */
    public Optional<AccessTokenContent> getAccessTokenContent() {
        return Optional.ofNullable(accessTokenContent);
    }

    /**
     * Gets the EventType from TokenValidationException if a parsing error occurred.
     *
     * @return Optional containing the EventType, or empty if no parsing error occurred
     */
    public Optional<EventType> getErrorEventType() {
        return Optional.ofNullable(errorEventType);
    }

    /**
     * Gets the error message from TokenValidationException if a parsing error occurred.
     *
     * @return Optional containing the error message, or empty if no parsing error occurred
     */
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Checks if the token validation was successful, and all configured claims like
     * {@link BearerToken#requiredRoles()}, {@link BearerToken#requiredScopes()} and {@link BearerToken#requiredGroups()}
     * are successfully verified.
     *
     * @return true if status is FULLY_VERIFIED, false otherwise
     */
    public boolean isSuccessfullyAuthorized() {
        return status == BearerTokenStatus.FULLY_VERIFIED;
    }

    /**
     * Checks if the token validation was unsuccessful.
     *
     * @return true if status is not FULLY_VERIFIED, false otherwise
     */
    public boolean isNotSuccessfullyAuthorized() {
        return status != BearerTokenStatus.FULLY_VERIFIED;
    }

}