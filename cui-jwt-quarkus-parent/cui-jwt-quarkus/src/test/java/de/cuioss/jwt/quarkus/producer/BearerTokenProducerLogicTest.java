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

import de.cuioss.jwt.quarkus.servlet.HttpServletRequestResolverMock;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.ClaimControlParameter;
import de.cuioss.tools.string.Joiner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BearerTokenProducer} business logic.
 * Tests the essential token extraction logic.
 *
 * Note: These are basic compilation and null-safety tests.
 * Full integration testing is done in QuarkusTest classes.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@DisplayName("BearerTokenProducer Logic")
class BearerTokenProducerLogicTest {

    private BearerTokenProducer underTest;
    private MockTokenValidator mockTokenValidator;
    private HttpServletRequestResolverMock requestResolverMock;

    @BeforeEach
    void setup() {
        requestResolverMock = new HttpServletRequestResolverMock();
        mockTokenValidator = new MockTokenValidator();
        underTest = new BearerTokenProducer(mockTokenValidator, requestResolverMock);
    }

    @Nested
    @DisplayName("Basic Token Extraction")
    class BasicTokenExtraction {

        @Test
        @DisplayName("should extract and validate token successfully")
        void shouldHandleHappyCase() {
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1", "role2");
            mockTokenValidator.setAccessTokenContent(expected);
            requestResolverMock.setBearerToken(expected.getRawToken());

            // Test new BearerTokenResult method
            BearerTokenResult result = underTest.getBearerTokenResult();
            assertTrue(result.isSuccessfullyAuthorized());
            assertFalse(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertTrue(result.getAccessTokenContent().isPresent());
            assertEquals(expected, result.getAccessTokenContent().get());

            // Test deprecated method still works
            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            assertEquals(expected, resolved.get());
        }

        @Test
        @DisplayName("should handle token with multiple roles")
        void shouldHandleTokenWithMultipleRoles() {
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "admin", "user", "viewer");
            mockTokenValidator.setAccessTokenContent(expected);
            requestResolverMock.setBearerToken(expected.getRawToken());

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            assertEquals(expected, resolved.get());
        }

        @Test
        @DisplayName("should handle token with scopes")
        void shouldHandleTokenWithScopes() {
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.SCOPE, "read", "write", "admin");
            mockTokenValidator.setAccessTokenContent(expected);
            requestResolverMock.setBearerToken(expected.getRawToken());

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            assertEquals(expected, resolved.get());
        }

        @Test
        @DisplayName("should handle token with groups")
        void shouldHandleTokenWithGroups() {
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.GROUPS, "developers", "admins");
            mockTokenValidator.setAccessTokenContent(expected);
            requestResolverMock.setBearerToken(expected.getRawToken());

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            assertEquals(expected, resolved.get());
        }
    }

    @Nested
    @DisplayName("Error Scenarios")
    class ErrorScenarios {

        @Test
        @DisplayName("should return empty when no request context available")
        void shouldReturnEmptyWhenNoRequestContext() {
            requestResolverMock.setRequestContextAvailable(false);
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            // Test new BearerTokenResult method
            BearerTokenResult result = underTest.getBearerTokenResult();
            assertFalse(result.isSuccessfullyAuthorized());
            assertTrue(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());

            // Test deprecated method still works
            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }

        @Test
        @DisplayName("should return empty when no authorization header present")
        void shouldReturnEmptyWhenNoAuthorizationHeader() {
            requestResolverMock.clearHeaders();
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            // Test new BearerTokenResult method
            BearerTokenResult result = underTest.getBearerTokenResult();
            assertFalse(result.isSuccessfullyAuthorized());
            assertTrue(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());

            // Test deprecated method still works
            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }

        @Test
        @DisplayName("should return empty when authorization header is not Bearer type")
        void shouldReturnEmptyWhenAuthorizationHeaderIsNotBearer() {
            requestResolverMock.setHeader("Authorization", "Basic dXNlcjpwYXNz");
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }

        @Test
        @DisplayName("should return empty when token validation fails")
        void shouldReturnEmptyWhenTokenValidationFails() {
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);
            mockTokenValidator.setShouldFail(true);
            requestResolverMock.setBearerToken("invalid-token");

            // Test new BearerTokenResult method
            BearerTokenResult result = underTest.getBearerTokenResult();
            assertFalse(result.isSuccessfullyAuthorized());
            assertTrue(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.PARSING_ERROR, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());
            assertTrue(result.getErrorEventType().isPresent());
            assertTrue(result.getErrorMessage().isPresent());

            // Test deprecated method still works
            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }

        @Test
        @DisplayName("should handle null authorization header")
        void shouldHandleNullAuthorizationHeader() {
            requestResolverMock.setHeader("Authorization", null);
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty bearer token")
        void shouldHandleEmptyBearerToken() {
            requestResolverMock.setHeader("Authorization", "Bearer ");
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            assertEquals(expected, resolved.get());
        }

        @Test
        @DisplayName("should handle bearer token with extra spaces")
        void shouldHandleBearerTokenWithSpaces() {
            requestResolverMock.setHeader("Authorization", "Bearer   token-with-spaces");
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            assertEquals(expected, resolved.get());
        }
    }

    @Nested
    @DisplayName("BearerTokenResult Methods")
    class BearerTokenResultMethods {

        @Test
        @DisplayName("should provide detailed information for successful validation")
        void shouldProvideDetailedInformationForSuccessfulValidation() {
            List<String> requiredScopes = List.of("read", "write");
            List<String> requiredRoles = List.of("admin");
            List<String> requiredGroups = List.of("developers");

            AccessTokenContent tokenContent = getAccessTokenWithMultipleClaims(
                    Map.of(
                            ClaimName.SCOPE, List.of("read", "write", "admin"),
                            ClaimName.ROLES, List.of("admin", "user"),
                            ClaimName.GROUPS, List.of("developers", "admins")
                    )
            );
            mockTokenValidator.setAccessTokenContent(tokenContent);
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            BearerTokenResult result = underTest.getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);

            assertTrue(result.isSuccessfullyAuthorized());
            assertFalse(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertTrue(result.getAccessTokenContent().isPresent());
            assertEquals(tokenContent, result.getAccessTokenContent().get());
            assertTrue(result.getMissingScopes().isEmpty());
            assertTrue(result.getMissingRoles().isEmpty());
            assertTrue(result.getMissingGroups().isEmpty());
            assertFalse(result.getErrorEventType().isPresent());
            assertFalse(result.getErrorMessage().isPresent());
        }

        @Test
        @DisplayName("should provide detailed information for constraint violation")
        void shouldProvideDetailedInformationForConstraintViolation() {
            List<String> requiredScopes = List.of("read", "write");
            List<String> requiredRoles = List.of("admin");
            List<String> requiredGroups = List.of("developers");

            AccessTokenContent tokenContent = getAccessTokenWithMultipleClaims(
                    Map.of(
                            ClaimName.SCOPE, List.of("read"),
                            ClaimName.ROLES, List.of("user"),
                            ClaimName.GROUPS, List.of("testers")
                    )
            );
            mockTokenValidator.setAccessTokenContent(tokenContent);
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            BearerTokenResult result = underTest.getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);

            assertFalse(result.isSuccessfullyAuthorized());
            assertTrue(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());
            assertEquals(Set.of("write"), result.getMissingScopes());
            assertEquals(Set.of("admin"), result.getMissingRoles());
            assertEquals(Set.of("developers"), result.getMissingGroups());
            assertFalse(result.getErrorEventType().isPresent());
            assertFalse(result.getErrorMessage().isPresent());
        }

        @Test
        @DisplayName("should provide detailed information for parsing error")
        void shouldProvideDetailedInformationForParsingError() {
            List<String> requiredScopes = List.of("read");
            List<String> requiredRoles = List.of("user");
            List<String> requiredGroups = List.of("developers");

            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(tokenContent);
            mockTokenValidator.setShouldFail(true);
            requestResolverMock.setBearerToken("invalid-token");

            BearerTokenResult result = underTest.getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);

            assertFalse(result.isSuccessfullyAuthorized());
            assertTrue(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.PARSING_ERROR, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());
            assertEquals(Set.copyOf(requiredScopes), result.getMissingScopes());
            assertEquals(Set.copyOf(requiredRoles), result.getMissingRoles());
            assertEquals(Set.copyOf(requiredGroups), result.getMissingGroups());
            assertTrue(result.getErrorEventType().isPresent());
            assertTrue(result.getErrorMessage().isPresent());
        }

        @Test
        @DisplayName("should provide detailed information for no token given")
        void shouldProvideDetailedInformationForNoTokenGiven() {
            List<String> requiredScopes = List.of("read");
            List<String> requiredRoles = List.of("user");
            List<String> requiredGroups = List.of("developers");

            requestResolverMock.clearHeaders();
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(tokenContent);

            BearerTokenResult result = underTest.getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);

            assertFalse(result.isSuccessfullyAuthorized());
            assertTrue(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());
            assertEquals(Set.copyOf(requiredScopes), result.getMissingScopes());
            assertEquals(Set.copyOf(requiredRoles), result.getMissingRoles());
            assertEquals(Set.copyOf(requiredGroups), result.getMissingGroups());
            assertFalse(result.getErrorEventType().isPresent());
            assertFalse(result.getErrorMessage().isPresent());
        }

        @Test
        @DisplayName("should provide detailed information for could not access request")
        void shouldProvideDetailedInformationForCouldNotAccessRequest() {
            List<String> requiredScopes = List.of("read");
            List<String> requiredRoles = List.of("user");
            List<String> requiredGroups = List.of("developers");

            requestResolverMock.setRequestContextAvailable(false);
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(tokenContent);

            BearerTokenResult result = underTest.getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);

            assertFalse(result.isSuccessfullyAuthorized());
            assertTrue(result.isNotSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST, result.getStatus());
            assertFalse(result.getAccessTokenContent().isPresent());
            assertEquals(Set.copyOf(requiredScopes), result.getMissingScopes());
            assertEquals(Set.copyOf(requiredRoles), result.getMissingRoles());
            assertEquals(Set.copyOf(requiredGroups), result.getMissingGroups());
            assertFalse(result.getErrorEventType().isPresent());
            assertFalse(result.getErrorMessage().isPresent());
        }
    }

    @Nested
    @DisplayName("Authorization Status Methods")
    class AuthorizationStatusMethods {
        
        @Test
        @DisplayName("should correctly identify successful authorization")
        void shouldIdentifySuccessfulAuthorization() {
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "admin", "user");
            mockTokenValidator.setAccessTokenContent(tokenContent);
            requestResolverMock.setBearerToken(tokenContent.getRawToken());
            
            BearerTokenResult result = underTest.getBearerTokenResult();
            
            assertTrue(result.isSuccessfullyAuthorized(), "Should be successfully authorized");
            assertFalse(result.isNotSuccessfullyAuthorized(), "Should not be unsuccessfully authorized");
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
        }
        
        @Test
        @DisplayName("should correctly identify unsuccessful authorization for each failure type")
        void shouldIdentifyUnsuccessfulAuthorizationForEachFailureType() {
            // Test NO_TOKEN_GIVEN
            BearerTokenResult noTokenResult = BearerTokenResult.noTokenGiven(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
            );
            assertFalse(noTokenResult.isSuccessfullyAuthorized(), "NO_TOKEN_GIVEN should not be successfully authorized");
            assertTrue(noTokenResult.isNotSuccessfullyAuthorized(), "NO_TOKEN_GIVEN should be unsuccessfully authorized");
            
            // Test PARSING_ERROR
            TokenValidationException exception = new TokenValidationException(
                SecurityEventCounter.EventType.INVALID_JWT_FORMAT, "Test error"
            );
            BearerTokenResult parsingErrorResult = BearerTokenResult.parsingError(
                exception, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
            );
            assertFalse(parsingErrorResult.isSuccessfullyAuthorized(), "PARSING_ERROR should not be successfully authorized");
            assertTrue(parsingErrorResult.isNotSuccessfullyAuthorized(), "PARSING_ERROR should be unsuccessfully authorized");
            
            // Test CONSTRAINT_VIOLATION
            BearerTokenResult constraintViolationResult = BearerTokenResult.constraintViolation(
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet()
            );
            assertFalse(constraintViolationResult.isSuccessfullyAuthorized(), "CONSTRAINT_VIOLATION should not be successfully authorized");
            assertTrue(constraintViolationResult.isNotSuccessfullyAuthorized(), "CONSTRAINT_VIOLATION should be unsuccessfully authorized");
            
            // Test COULD_NOT_ACCESS_REQUEST
            BearerTokenResult couldNotAccessResult = BearerTokenResult.couldNotAccessRequest(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
            );
            assertFalse(couldNotAccessResult.isSuccessfullyAuthorized(), "COULD_NOT_ACCESS_REQUEST should not be successfully authorized");
            assertTrue(couldNotAccessResult.isNotSuccessfullyAuthorized(), "COULD_NOT_ACCESS_REQUEST should be unsuccessfully authorized");
        }
        
        @Test
        @DisplayName("should have consistent authorization status across all scenarios")
        void shouldHaveConsistentAuthorizationStatus() {
            // For each possible status, verify that isSuccessfullyAuthorized and isNotSuccessfullyAuthorized are opposites
            for (BearerTokenStatus status : BearerTokenStatus.values()) {
                BearerTokenResult result;
                switch (status) {
                    case FULLY_VERIFIED:
                        AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "admin");
                        result = BearerTokenResult.success(tokenContent, 
                            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                        break;
                    case NO_TOKEN_GIVEN:
                        result = BearerTokenResult.noTokenGiven(
                            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                        break;
                    case PARSING_ERROR:
                        TokenValidationException ex = new TokenValidationException(
                            SecurityEventCounter.EventType.INVALID_JWT_FORMAT, "Test");
                        result = BearerTokenResult.parsingError(ex,
                            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                        break;
                    case CONSTRAINT_VIOLATION:
                        result = BearerTokenResult.constraintViolation(
                            Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                        break;
                    case COULD_NOT_ACCESS_REQUEST:
                        result = BearerTokenResult.couldNotAccessRequest(
                            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                        break;
                    default:
                        throw new IllegalStateException("Unknown status: " + status);
                }
                
                // Verify the methods are opposites
                assertNotEquals(result.isSuccessfullyAuthorized(), result.isNotSuccessfullyAuthorized(),
                    "Authorization methods should return opposite values for status: " + status);
                
                // Verify correct behavior based on status
                if (status == BearerTokenStatus.FULLY_VERIFIED) {
                    assertTrue(result.isSuccessfullyAuthorized(), 
                        "FULLY_VERIFIED should be successfully authorized");
                    assertFalse(result.isNotSuccessfullyAuthorized(), 
                        "FULLY_VERIFIED should not be unsuccessfully authorized");
                } else {
                    assertFalse(result.isSuccessfullyAuthorized(), 
                        status + " should not be successfully authorized");
                    assertTrue(result.isNotSuccessfullyAuthorized(), 
                        status + " should be unsuccessfully authorized");
                }
            }
        }
    }

    @Nested
    @DisplayName("Requirements Validation")
    class RequirementsValidation {

        @Nested
        @DisplayName("Scope Requirements")
        class ScopeRequirements {

            @Test
            @DisplayName("should validate when all required scopes are present")
            void shouldValidateRequiredScopes() {
                AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.SCOPE, "read", "write");
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                var resolved = underTest.getAccessTokenContentWithRequirements(
                        List.of("read", "write"), Collections.emptyList(), Collections.emptyList());
                assertTrue(resolved.isPresent());
                assertEquals(tokenContent, resolved.get());
            }

            @Test
            @DisplayName("should reject when required scopes are missing")
            void shouldRejectWhenRequiredScopesMissing() {
                AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.SCOPE, "read");
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                // Test new BearerTokenResult method
                BearerTokenResult result = underTest.getBearerTokenResult(List.of("read", "write"), Collections.emptyList(), Collections.emptyList());
                assertFalse(result.isSuccessfullyAuthorized());
            assertTrue(result.isNotSuccessfullyAuthorized());
                assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
                assertFalse(result.getAccessTokenContent().isPresent());
                assertEquals(Set.of("write"), result.getMissingScopes());

                // Test deprecated method still works
                var resolved = underTest.getAccessTokenContentWithRequirements(
                        List.of("read", "write"), Collections.emptyList(), Collections.emptyList());
                assertFalse(resolved.isPresent());
            }
        }

        @Nested
        @DisplayName("Role Requirements")
        class RoleRequirements {

            @Test
            @DisplayName("should validate when all required roles are present")
            void shouldValidateRequiredRoles() {
                AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "admin", "user");
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                var resolved = underTest.getAccessTokenContentWithRequirements(
                        Collections.emptyList(), List.of("admin"), Collections.emptyList());
                assertTrue(resolved.isPresent());
                assertEquals(tokenContent, resolved.get());
            }

            @Test
            @DisplayName("should reject when required roles are missing")
            void shouldRejectWhenRequiredRolesMissing() {
                AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "user");
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                var resolved = underTest.getAccessTokenContentWithRequirements(
                        Collections.emptyList(), List.of("admin"), Collections.emptyList());
                assertFalse(resolved.isPresent());
            }
        }

        @Nested
        @DisplayName("Group Requirements")
        class GroupRequirements {

            @Test
            @DisplayName("should validate when all required groups are present")
            void shouldValidateRequiredGroups() {
                AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.GROUPS, "developers", "testers");
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                var resolved = underTest.getAccessTokenContentWithRequirements(
                        Collections.emptyList(), Collections.emptyList(), List.of("developers"));
                assertTrue(resolved.isPresent());
                assertEquals(tokenContent, resolved.get());
            }

            @Test
            @DisplayName("should reject when required groups are missing")
            void shouldRejectWhenRequiredGroupsMissing() {
                AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.GROUPS, "testers");
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                var resolved = underTest.getAccessTokenContentWithRequirements(
                        Collections.emptyList(), Collections.emptyList(), List.of("developers"));
                assertFalse(resolved.isPresent());
            }
        }

        @Nested
        @DisplayName("Combined Requirements")
        class CombinedRequirements {

            @Test
            @DisplayName("should validate when all requirements are met")
            void shouldValidateAllRequirementsTogether() {
                AccessTokenContent tokenContent = getAccessTokenWithMultipleClaims(
                        Map.of(
                                ClaimName.SCOPE, List.of("read", "write", "admin"),
                                ClaimName.ROLES, List.of("admin", "user"),
                                ClaimName.GROUPS, List.of("developers", "admins")
                        )
                );
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                var resolved = underTest.getAccessTokenContentWithRequirements(
                        List.of("read", "write"),
                        List.of("admin"),
                        List.of("developers"));
                assertTrue(resolved.isPresent());
                assertEquals(tokenContent, resolved.get());
            }

            @Test
            @DisplayName("should reject when any requirement fails")
            void shouldRejectWhenAnyRequirementFails() {
                AccessTokenContent tokenContent = getAccessTokenWithMultipleClaims(
                        Map.of(
                                ClaimName.SCOPE, List.of("read", "write"),
                                ClaimName.ROLES, List.of("user"),
                                ClaimName.GROUPS, List.of("developers")
                        )
                );
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                var resolved = underTest.getAccessTokenContentWithRequirements(
                        List.of("read", "write"),
                        List.of("admin"),
                        List.of("developers"));
                assertFalse(resolved.isPresent());
            }

            @Test
            @DisplayName("should handle empty requirements")
            void shouldHandleEmptyRequirements() {
                AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "user");
                mockTokenValidator.setAccessTokenContent(tokenContent);
                requestResolverMock.setBearerToken(tokenContent.getRawToken());

                var resolved = underTest.getAccessTokenContentWithRequirements(
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
                assertTrue(resolved.isPresent());
                assertEquals(tokenContent, resolved.get());
            }
        }
    }


    private AccessTokenContent getAccessTokenWithClaims(ClaimName claimName, String... value) {
        TestTokenHolder holder = new TestTokenHolder(TokenType.ACCESS_TOKEN, ClaimControlParameter.defaultForTokenType(TokenType.ACCESS_TOKEN));
        List<String> elements = Arrays.asList(value);
        String concatenated = Joiner.on(",").join(elements);
        holder.withClaim(claimName.getName(), ClaimValue.forList(concatenated, elements));
        return holder.asAccessTokenContent();
    }

    private AccessTokenContent getAccessTokenWithMultipleClaims(Map<ClaimName, List<String>> claims) {
        TestTokenHolder holder = new TestTokenHolder(TokenType.ACCESS_TOKEN, ClaimControlParameter.defaultForTokenType(TokenType.ACCESS_TOKEN));
        claims.forEach((claimName, values) -> {
            String concatenated = Joiner.on(",").join(values);
            holder.withClaim(claimName.getName(), ClaimValue.forList(concatenated, values));
        });
        return holder.asAccessTokenContent();
    }
}
