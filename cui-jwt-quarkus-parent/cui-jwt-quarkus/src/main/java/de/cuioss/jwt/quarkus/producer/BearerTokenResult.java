/**
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import lombok.*;

import jakarta.ws.rs.core.Response;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
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
 * Usage example:
 * <pre>{@code
 * BearerTokenResult result = bearerTokenProducer.getBearerTokenResult(
 *     List.of("read"), List.of("user"), List.of("admin"));
 *
 * switch (result.getStatus()) {
 *     case FULLY_VERIFIED:
 *         AccessTokenContent token = result.getAccessTokenContent().get();
 *         // Use validated token
 *         break;
 *     case PARSING_ERROR:
 *         EventType eventType = result.getErrorEventType().get();
 *         String message = result.getErrorMessage().get();
 *         // Handle parsing error
 *         break;
 *     case CONSTRAINT_VIOLATION:
 *         Set<String> missingScopes = result.getMissingScopes();
 *         Set<String> missingRoles = result.getMissingRoles();
 *         Set<String> missingGroups = result.getMissingGroups();
 *         // Handle missing scopes/roles/groups
 *         break;
 *     default:
 *         // Handle other cases
 * }
 * }</pre>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EqualsAndHashCode
@ToString
@Getter
@RequiredArgsConstructor(access =  AccessLevel.PRIVATE)
public class BearerTokenResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BearerTokenStatus status;
    private final Set<String> missingScopes;
    private final Set<String> missingRoles;
    private final Set<String> missingGroups;
    private final AccessTokenContent accessTokenContent;
    private final EventType errorEventType;
    private final String errorMessage;


    /**
     * Creates a BearerTokenResult for successful token validation.
     *
     * @param accessTokenContent the validated access token content
     * @param requiredScopes the scopes that were required for validation
     * @param requiredRoles the roles that were required for validation
     * @param requiredGroups the groups that were required for validation
     * @return a BearerTokenResult indicating successful validation
     */
    @NonNull
    public static BearerTokenResult success(AccessTokenContent accessTokenContent,
                                            List<String> requiredScopes, List<String> requiredRoles, List<String> requiredGroups) {
        return builder()
            .status(BearerTokenStatus.FULLY_VERIFIED)
            .accessTokenContent(accessTokenContent, requiredScopes, requiredRoles, requiredGroups)
            .build();
    }


    /**
     * Creates a BearerTokenResult for failed token validation due to parsing error.
     *
     * @param exception the TokenValidationException that occurred during parsing
     * @param requiredScopes the scopes that were required for validation
     * @param requiredRoles the roles that were required for validation
     * @param requiredGroups the groups that were required for validation
     * @return a BearerTokenResult indicating parsing error
     */
    @NonNull
    public static BearerTokenResult parsingError(TokenValidationException exception,
                                                 List<String> requiredScopes, List<String> requiredRoles, List<String> requiredGroups) {
        return builder()
            .status(BearerTokenStatus.PARSING_ERROR)
            .error(exception)
            .missingScopes(Set.copyOf(requiredScopes))
            .missingRoles(Set.copyOf(requiredRoles))
            .missingGroups(Set.copyOf(requiredGroups))
            .build();
    }


    /**
     * Creates a BearerTokenResult for failed token validation due to constraint violations.
     *
     * @param missingScopes the scopes that are missing from the token
     * @param missingRoles the roles that are missing from the token
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
     * @param requiredRoles the roles that were required for validation
     * @param requiredGroups the groups that were required for validation
     * @return a BearerTokenResult indicating no token was given
     */
    @NonNull
    public static BearerTokenResult noTokenGiven(List<String> requiredScopes,
                                                 List<String> requiredRoles, List<String> requiredGroups) {
        return builder()
            .status(BearerTokenStatus.NO_TOKEN_GIVEN)
            .missingScopes(Set.copyOf(requiredScopes))
            .missingRoles(Set.copyOf(requiredRoles))
            .missingGroups(Set.copyOf(requiredGroups))
            .build();
    }


    /**
     * Creates a BearerTokenResult for cases where the request could not be accessed.
     *
     * @param requiredScopes the scopes that were required for validation
     * @param requiredRoles the roles that were required for validation
     * @param requiredGroups the groups that were required for validation
     * @return a BearerTokenResult indicating request access failure
     */
    @NonNull
    public static BearerTokenResult couldNotAccessRequest(List<String> requiredScopes,
                                                          List<String> requiredRoles, List<String> requiredGroups) {
        return builder()
            .status(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST)
            .missingScopes(Set.copyOf(requiredScopes))
            .missingRoles(Set.copyOf(requiredRoles))
            .missingGroups(Set.copyOf(requiredGroups))
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
     * Gets the scopes that are missing from the token.
     *
     * @return Set of missing scope names, empty if no scopes are missing
     */
    public Set<String> getMissingScopes() {
        return missingScopes;
    }

    /**
     * Gets the roles that are missing from the token.
     *
     * @return Set of missing role names, empty if no roles are missing
     */
    public Set<String> getMissingRoles() {
        return missingRoles;
    }

    /**
     * Gets the groups that are missing from the token.
     *
     * @return Set of missing group names, empty if no groups are missing
     */
    public Set<String> getMissingGroups() {
        return missingGroups;
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

    /**
     * Creates an appropriate HTTP error response based on the bearer token validation status.
     * <p>
     * This method delegates to the status-specific strategy implementation following
     * OAuth 2.0 Bearer Token specification (RFC 6750) and OAuth Step-Up Authentication
     * Challenge (draft-ietf-oauth-step-up-authn-challenge-17) best practices.
     * <p>
     * HTTP Status Code mappings:
     * <ul>
     *   <li>FULLY_VERIFIED: 200 (OK) - Should not be called for successful validation</li>
     *   <li>COULD_NOT_ACCESS_REQUEST: 500 (Internal Server Error) - Server-side issue</li>
     *   <li>NO_TOKEN_GIVEN: 401 (Unauthorized) - Missing Bearer token</li>
     *   <li>PARSING_ERROR: 401 (Unauthorized) - Invalid or expired token</li>
     *   <li>CONSTRAINT_VIOLATION: 401 (Unauthorized) for missing scopes, 403 (Forbidden) for missing roles/groups</li>
     * </ul>
     * <p>
     * The response includes appropriate WWW-Authenticate headers following RFC 6750 and RFC 7235 standards.
     * For scope-related constraint violations, the response follows OAuth Step-Up Authentication Challenge
     * specification to provide actionable information to the client.
     *
     * @return Response object with appropriate HTTP status code, headers, and body
     */
    public Response errorResponse() {
        return status.createResponse(this);
    }

    /**
     * Creates a new Builder instance for constructing BearerTokenResult objects.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing BearerTokenResult instances.
     * <p>
     * This builder provides a fluent API for creating BearerTokenResult objects
     * with different validation outcomes. It automatically determines missing
     * scopes, roles, and groups when a successful token validation result is created.
     * <p>
     * Usage example:
     * <pre>{@code
     * BearerTokenResult result = BearerTokenResult.builder()
     *     .status(BearerTokenStatus.FULLY_VERIFIED)
     *     .accessTokenContent(tokenContent)
     *     .expectedScopes(List.of("read", "write"))
     *     .expectedRoles(List.of("user"))
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private BearerTokenStatus status;
        private Set<String> missingScopes = Set.of();
        private Set<String> missingRoles = Set.of();
        private Set<String> missingGroups = Set.of();
        private AccessTokenContent accessTokenContent;
        private EventType errorEventType;
        private String errorMessage;

        private Builder() {}

        /**
         * Sets the validation status.
         *
         * @param status the validation status
         * @return this builder instance
         */
        public Builder status(BearerTokenStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the missing scopes.
         *
         * @param missingScopes the set of missing scopes
         * @return this builder instance
         */
        public Builder missingScopes(Set<String> missingScopes) {
            this.missingScopes = missingScopes != null ? missingScopes : Set.of();
            return this;
        }

        /**
         * Sets the missing roles.
         *
         * @param missingRoles the set of missing roles
         * @return this builder instance
         */
        public Builder missingRoles(Set<String> missingRoles) {
            this.missingRoles = missingRoles != null ? missingRoles : Set.of();
            return this;
        }

        /**
         * Sets the missing groups.
         *
         * @param missingGroups the set of missing groups
         * @return this builder instance
         */
        public Builder missingGroups(Set<String> missingGroups) {
            this.missingGroups = missingGroups != null ? missingGroups : Set.of();
            return this;
        }

        /**
         * Sets the validated access token content and automatically determines missing scopes/roles/groups.
         *
         * @param accessTokenContent the validated access token content
         * @param expectedScopes the expected scopes to check against
         * @param expectedRoles the expected roles to check against
         * @param expectedGroups the expected groups to check against
         * @return this builder instance
         */
        public Builder accessTokenContent(AccessTokenContent accessTokenContent, 
                                        List<String> expectedScopes, 
                                        List<String> expectedRoles, 
                                        List<String> expectedGroups) {
            this.accessTokenContent = accessTokenContent;
            if (accessTokenContent != null) {
                this.missingScopes = accessTokenContent.determineMissingScopes(expectedScopes);
                this.missingRoles = accessTokenContent.determineMissingRoles(expectedRoles);
                this.missingGroups = accessTokenContent.determineMissingGroups(expectedGroups);
            }
            return this;
        }

        /**
         * Sets the access token content without determining missing attributes.
         *
         * @param accessTokenContent the validated access token content
         * @return this builder instance
         */
        public Builder accessTokenContent(AccessTokenContent accessTokenContent) {
            this.accessTokenContent = accessTokenContent;
            return this;
        }

        /**
         * Sets the error event type from a TokenValidationException.
         *
         * @param errorEventType the error event type
         * @return this builder instance
         */
        public Builder errorEventType(EventType errorEventType) {
            this.errorEventType = errorEventType;
            return this;
        }

        /**
         * Sets the error message from a TokenValidationException.
         *
         * @param errorMessage the error message
         * @return this builder instance
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Sets error details from a TokenValidationException.
         *
         * @param exception the TokenValidationException
         * @return this builder instance
         */
        public Builder error(TokenValidationException exception) {
            if (exception != null) {
                this.errorEventType = exception.getEventType();
                this.errorMessage = exception.getMessage();
            }
            return this;
        }

        /**
         * Builds the BearerTokenResult instance.
         *
         * @return a new BearerTokenResult instance
         * @throws IllegalStateException if required fields are not set
         */
        public BearerTokenResult build() {
            if (status == null) {
                throw new IllegalStateException("Status must be set");
            }
            return new BearerTokenResult(status, missingScopes, missingRoles, missingGroups, 
                                       accessTokenContent, errorEventType, errorMessage);
        }
    }

}