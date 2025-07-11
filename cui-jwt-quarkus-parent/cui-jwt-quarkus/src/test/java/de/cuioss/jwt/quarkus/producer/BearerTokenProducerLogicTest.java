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
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.string.Joiner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.ERROR.BEARER_TOKEN_HEADER_MAP_ACCESS_FAILED;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN.BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED;
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
@EnableTestLogger
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

            // Only test the public API now

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
        @DisplayName("should throw IllegalStateException when no request context available")
        void shouldThrowIllegalStateExceptionWhenNoRequestContext() {
            requestResolverMock.setRequestContextAvailable(false);
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            // IllegalStateException should now bubble up
            assertThrows(IllegalStateException.class, () -> underTest.getAccessTokenContent());
        }

        @Test
        @DisplayName("should return empty when no authorization header present")
        void shouldReturnEmptyWhenNoAuthorizationHeader() {
            requestResolverMock.clearHeaders();
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            // Only test the public API now

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

            // Only test the public API now

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
        @DisplayName("should handle empty bearer token as token given")
        void shouldHandleEmptyBearerTokenAsTokenGiven() {
            requestResolverMock.setHeader("Authorization", "Bearer ");
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            mockTokenValidator.setAccessTokenContent(expected);

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
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
    @DisplayName("Authorization Status Methods")
    class AuthorizationStatusMethods {


        @Test
        @DisplayName("should correctly identify unsuccessful authorization for each failure type")
        void shouldIdentifyUnsuccessfulAuthorizationForEachFailureType() {
            // Test NO_TOKEN_GIVEN
            BearerTokenResult noTokenResult = BearerTokenResult.noTokenGiven(
                    Collections.emptySet(), Collections.emptySet(), Collections.emptySet()
            );
            assertFalse(noTokenResult.isSuccessfullyAuthorized(), "NO_TOKEN_GIVEN should not be successfully authorized");
            assertTrue(noTokenResult.isNotSuccessfullyAuthorized(), "NO_TOKEN_GIVEN should be unsuccessfully authorized");

            // Test PARSING_ERROR
            TokenValidationException exception = new TokenValidationException(
                    SecurityEventCounter.EventType.INVALID_JWT_FORMAT, "Test error"
            );
            BearerTokenResult parsingErrorResult = BearerTokenResult.parsingError(
                    exception, Collections.emptySet(), Collections.emptySet(), Collections.emptySet()
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
                    Collections.emptySet(), Collections.emptySet(), Collections.emptySet()
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
                                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                        break;
                    case NO_TOKEN_GIVEN:
                        result = BearerTokenResult.noTokenGiven(
                                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                        break;
                    case PARSING_ERROR:
                        TokenValidationException ex = new TokenValidationException(
                                SecurityEventCounter.EventType.INVALID_JWT_FORMAT, "Test");
                        result = BearerTokenResult.parsingError(ex,
                                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                        break;
                    case CONSTRAINT_VIOLATION:
                        result = BearerTokenResult.constraintViolation(
                                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                        break;
                    case COULD_NOT_ACCESS_REQUEST:
                        result = BearerTokenResult.couldNotAccessRequest(
                                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
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
    @DisplayName("Logging Behavior")
    class LoggingBehavior {

        @Test
        @DisplayName("should log WARN when bearer token requirements are not met")
        void shouldLogWarnWhenRequirementsNotMet() {
            // Setup a token with insufficient claims
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "user");
            mockTokenValidator.setAccessTokenContent(tokenContent);
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Use the CDI producer with requirements that will fail
            BearerTokenProducer producer = new BearerTokenProducer(mockTokenValidator, requestResolverMock);
            var result = producer.produceBearerTokenResult(new MockInjectionPoint(
                    Set.of("read"), Set.of("admin"), Set.of("managers")));

            // Verify we got a constraint violation
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertFalse(result.getMissingScopes().isEmpty());
            assertFalse(result.getMissingRoles().isEmpty());
            assertFalse(result.getMissingGroups().isEmpty());

            // Verify the specific WARN log message was logged
            LogAsserts.assertLogMessagePresent(TestLogLevel.WARN, BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED.format(
                    Set.of("read"), Set.of("admin"), Set.of("managers")));
        }

        @Test
        @DisplayName("should throw IllegalStateException when header map access fails")
        void shouldThrowIllegalStateExceptionWhenHeaderMapAccessFails() {
            // Setup mock to fail header map access
            requestResolverMock.setRequestContextAvailable(false);
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "user");
            mockTokenValidator.setAccessTokenContent(tokenContent);

            // Use the CDI producer
            BearerTokenProducer producer = new BearerTokenProducer(mockTokenValidator, requestResolverMock);
            
            // IllegalStateException should now bubble up
            assertThrows(IllegalStateException.class, () -> producer.produceBearerTokenResult(new MockInjectionPoint(
                    Set.of("read"), Set.of("admin"), Set.of("managers"))));
        }

        @Test
        @DisplayName("should log WARN with detailed missing requirements for mixed violations")
        void shouldLogWarnWithDetailedMissingRequirementsForMixedViolations() {
            // Setup a token with partial claims
            AccessTokenContent tokenContent = getAccessTokenWithMultipleClaims(Map.of(
                    ClaimName.SCOPE, List.of("read"),
                    ClaimName.ROLES, List.of("admin"),
                    ClaimName.GROUPS, List.of("testers")
            ));
            mockTokenValidator.setAccessTokenContent(tokenContent);
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Use the CDI producer with requirements that will partially fail
            BearerTokenProducer producer = new BearerTokenProducer(mockTokenValidator, requestResolverMock);
            var result = producer.produceBearerTokenResult(new MockInjectionPoint(
                    Set.of("read", "write"), Set.of("admin"), Set.of("developers")));

            // Verify we got a constraint violation with specific missing items
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertEquals(Set.of("write"), result.getMissingScopes());
            assertTrue(result.getMissingRoles().isEmpty());
            assertEquals(Set.of("developers"), result.getMissingGroups());

            // Verify the specific WARN log message was logged
            LogAsserts.assertLogMessagePresent(TestLogLevel.WARN, BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED.format(
                    Set.of("write"), Set.of(), Set.of("developers")));
        }

        @Test
        @DisplayName("should log WARN with empty collections when no specific requirements are missing")
        void shouldLogWarnWithEmptyCollectionsWhenNoSpecificRequirementsMissing() {
            // Setup a token without required scopes
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "admin");
            mockTokenValidator.setAccessTokenContent(tokenContent);
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Use the CDI producer with only scope requirements
            BearerTokenProducer producer = new BearerTokenProducer(mockTokenValidator, requestResolverMock);
            var result = producer.produceBearerTokenResult(new MockInjectionPoint(
                    Set.of("read"), Set.of(), Set.of()));

            // Verify we got a constraint violation with only scope missing
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertEquals(Set.of("read"), result.getMissingScopes());
            assertTrue(result.getMissingRoles().isEmpty());
            assertTrue(result.getMissingGroups().isEmpty());

            // Verify the specific WARN log message was logged
            LogAsserts.assertLogMessagePresent(TestLogLevel.WARN, BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED.format(
                    Set.of("read"), Set.of(), Set.of()));
        }
    }

    @Nested
    @DisplayName("Performance Optimizations")
    class PerformanceOptimizations {

        @Test
        @DisplayName("should throw IllegalStateException for infrastructure errors")
        void shouldThrowIllegalStateExceptionForInfrastructureErrors() {
            // Setup mock to track calls
            HttpServletRequestResolverMock trackingMock = new HttpServletRequestResolverMock();
            trackingMock.setRequestContextAvailable(false);

            // Create producer with tracking mock
            BearerTokenProducer producer = new BearerTokenProducer(mockTokenValidator, trackingMock);

            // IllegalStateException should now bubble up
            assertThrows(IllegalStateException.class, () -> producer.produceBearerTokenResult(new MockInjectionPoint(
                    Set.of("read"), Set.of("admin"), Set.of("managers"))));
        }

        @Test
        @DisplayName("should properly distinguish between infrastructure errors and missing tokens")
        void shouldProperlyDistinguishBetweenInfrastructureErrorsAndMissingTokens() {
            // Test infrastructure error case - should throw IllegalStateException
            requestResolverMock.setRequestContextAvailable(false);
            BearerTokenProducer producer1 = new BearerTokenProducer(mockTokenValidator, requestResolverMock);
            assertThrows(IllegalStateException.class, () -> producer1.produceBearerTokenResult(new MockInjectionPoint(
                    Set.of("read"), Set.of(), Set.of())));

            // Test missing token case
            requestResolverMock.setRequestContextAvailable(true);
            requestResolverMock.clearHeaders(); // No Authorization header
            BearerTokenProducer producer2 = new BearerTokenProducer(mockTokenValidator, requestResolverMock);
            var result2 = producer2.produceBearerTokenResult(new MockInjectionPoint(
                    Set.of("read"), Set.of(), Set.of()));
            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result2.getStatus());
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
