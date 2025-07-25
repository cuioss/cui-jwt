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

import de.cuioss.jwt.quarkus.servlet.HttpServletRequestResolverMock;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

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
    private TokenValidator tokenValidator;
    private HttpServletRequestResolverMock requestResolverMock;

    @BeforeEach
    void setup() {
        requestResolverMock = new HttpServletRequestResolverMock();
        // Create a real TokenValidator with the same issuer as TestTokenHolder
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TestTokenHolder.TEST_ISSUER)
                .jwksContent(InMemoryKeyMaterialHandler.createDefaultJwks())
                .build();
        tokenValidator = TokenValidator.builder()
                .issuerConfig(issuerConfig)
                .build();
        underTest = new BearerTokenProducer(tokenValidator, requestResolverMock);
    }

    @Nested
    @DisplayName("Basic Token Extraction")
    class BasicTokenExtraction {

        @Test
        @DisplayName("should extract and validate token successfully")
        void shouldHandleHappyCase() {
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1", "role2");
            requestResolverMock.setBearerToken(expected.getRawToken());

            // Only test the public API now

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            // Compare the actual claim values instead of the entire object
            var actual = resolved.get();
            assertEquals(expected.getRawToken(), actual.getRawToken());
            assertEquals(expected.getRoles(), actual.getRoles());
        }

        @ParameterizedTest
        @CsvSource({
                "ROLES, 'admin,user,viewer'",
                "SCOPE, 'read,write,admin'",
                "GROUPS, 'developers,admins'"
        })
        @DisplayName("should handle token with different claim types")
        void shouldHandleTokenWithDifferentClaimTypes(ClaimName claimName, String claimValues) {
            String[] values = claimValues.split(",");
            AccessTokenContent expected = getAccessTokenWithClaims(claimName, values);
            requestResolverMock.setBearerToken(expected.getRawToken());

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            // Compare the actual claim values instead of the entire object
            var actual = resolved.get();
            assertEquals(expected.getRawToken(), actual.getRawToken());
            // Compare the relevant claim based on type (sort to avoid order issues)
            switch (claimName) {
                case ROLES -> assertEquals(new ArrayList<>(expected.getRoles()).stream().sorted().toList(),
                                           new ArrayList<>(actual.getRoles()).stream().sorted().toList());
                case SCOPE -> assertEquals(new ArrayList<>(expected.getScopes()).stream().sorted().toList(),
                                           new ArrayList<>(actual.getScopes()).stream().sorted().toList());
                case GROUPS -> assertEquals(new ArrayList<>(expected.getGroups()).stream().sorted().toList(),
                                           new ArrayList<>(actual.getGroups()).stream().sorted().toList());
                default -> fail("Test is not configured to handle claim type: " + claimName);
            }
        }
    }

    @Nested
    @DisplayName("Error Scenarios")
    class ErrorScenarios {

        @Test
        @DisplayName("should throw IllegalStateException when no request context available")
        void shouldThrowIllegalStateExceptionWhenNoRequestContext() {
            requestResolverMock.setRequestContextAvailable(false);


            // IllegalStateException should now bubble up
            assertThrows(IllegalStateException.class, () -> underTest.getAccessTokenContent());
        }

        @Test
        @DisplayName("should return empty when no authorization header present")
        void shouldReturnEmptyWhenNoAuthorizationHeader() {
            requestResolverMock.clearHeaders();
            getAccessTokenWithClaims(ClaimName.ROLES, "role1");

            // Only test the public API now

            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }

        @Test
        @DisplayName("should return empty when authorization header is not Bearer type")
        void shouldReturnEmptyWhenAuthorizationHeaderIsNotBearer() {
            requestResolverMock.setHeader("Authorization", "Basic dXNlcjpwYXNz");
            getAccessTokenWithClaims(ClaimName.ROLES, "role1");

            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }

        @Test
        @DisplayName("should return empty when token validation fails")
        void shouldReturnEmptyWhenTokenValidationFails() {
            getAccessTokenWithClaims(ClaimName.ROLES, "role1");
            // Use an invalid token to trigger validation failure
            requestResolverMock.setBearerToken("invalid-jwt-token");

            // Only test the public API now

            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }

        @Test
        @DisplayName("should handle null authorization header")
        void shouldHandleNullAuthorizationHeader() {
            requestResolverMock.setHeader("Authorization", null);
            getAccessTokenWithClaims(ClaimName.ROLES, "role1");

            var resolved = underTest.getAccessTokenContent();
            assertFalse(resolved.isPresent());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @ParameterizedTest
        @CsvSource({
                "'Bearer ', false, 'should handle empty bearer token as invalid'",
                "'Bearer   invalid-token', false, 'should handle invalid bearer token'"
        })
        @DisplayName("should handle Bearer token edge cases")
        void shouldHandleBearerTokenEdgeCases(String authorizationHeader, boolean shouldBePresent, String description) {
            requestResolverMock.setHeader("Authorization", authorizationHeader);

            var resolved = underTest.getAccessTokenContent();
            assertEquals(shouldBePresent, resolved.isPresent(), description);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Authorization", "AUTHORIZATION", "authorization", "AuThOrIzAtIoN"})
        @DisplayName("should handle Authorization header in various cases with normalized keys")
        void shouldHandleAuthorizationHeaderInVariousCasesWithNormalizedKeys(String headerCase) {
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");

            requestResolverMock.clearHeaders();
            requestResolverMock.setHeader(headerCase, "Bearer " + expected.getRawToken());

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent(),
                    "Should find authorization header regardless of case: " + headerCase);
            // Compare the actual claim values instead of the entire object
            var actual = resolved.get();
            assertEquals(expected.getRawToken(), actual.getRawToken());
            assertEquals(expected.getRoles(), actual.getRoles());
        }

        @Test
        @DisplayName("should use direct lowercase lookup for authorization header")
        void shouldUseDirectLowercaseLookupForAuthorizationHeader() {
            AccessTokenContent expected = getAccessTokenWithClaims(ClaimName.ROLES, "role1");

            // Set Authorization header in mixed case
            requestResolverMock.setHeader("Authorization", "Bearer " + expected.getRawToken());

            var resolved = underTest.getAccessTokenContent();
            assertTrue(resolved.isPresent());
            // Compare the actual claim values instead of the entire object
            var actual = resolved.get();
            assertEquals(expected.getRawToken(), actual.getRawToken());
            assertEquals(expected.getRoles(), actual.getRoles());

            // Verify that the header normalization happens at HttpServletRequestResolver level
            // by checking that the header map contains lowercase keys
            var headerMap = requestResolverMock.resolveHeaderMap();
            assertTrue(headerMap.containsKey("authorization"),
                    "Header map should contain lowercase 'authorization' key");
            assertFalse(headerMap.containsKey("Authorization"),
                    "Header map should not contain mixed-case 'Authorization' key");
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

        @ParameterizedTest
        @EnumSource(BearerTokenStatus.class)
        @DisplayName("should have consistent authorization status across all scenarios")
        void shouldHaveConsistentAuthorizationStatus(BearerTokenStatus status) {
            BearerTokenResult result = switch (status) {
                case FULLY_VERIFIED -> {
                    AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "admin");
                    yield BearerTokenResult.success(tokenContent,
                            Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                }
                case NO_TOKEN_GIVEN -> BearerTokenResult.noTokenGiven(
                        Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                case PARSING_ERROR -> {
                    TokenValidationException ex = new TokenValidationException(
                            SecurityEventCounter.EventType.INVALID_JWT_FORMAT, "Test");
                    yield BearerTokenResult.parsingError(ex,
                            Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                }
                case CONSTRAINT_VIOLATION -> BearerTokenResult.constraintViolation(
                        Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
                case COULD_NOT_ACCESS_REQUEST -> BearerTokenResult.couldNotAccessRequest(
                        Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
            };

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

    @Nested
    @DisplayName("Logging Behavior")
    class LoggingBehavior {

        @Test
        @DisplayName("should log WARN when bearer token requirements are not met")
        void shouldLogWarnWhenRequirementsNotMet() {
            // Setup a token with insufficient claims
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "user");
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Use the CDI producer with requirements that will fail
            BearerTokenProducer producer = new BearerTokenProducer(tokenValidator, requestResolverMock);
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
            getAccessTokenWithClaims(ClaimName.ROLES, "user");

            // Use the CDI producer
            BearerTokenProducer producer = new BearerTokenProducer(tokenValidator, requestResolverMock);
            MockInjectionPoint injectionPoint = new MockInjectionPoint(Set.of("read"), Set.of("admin"), Set.of("managers"));

            // IllegalStateException should now bubble up
            assertThrows(IllegalStateException.class, () -> producer.produceBearerTokenResult(injectionPoint));
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
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Use the CDI producer with requirements that will partially fail
            BearerTokenProducer producer = new BearerTokenProducer(tokenValidator, requestResolverMock);
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
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Use the CDI producer with only scope requirements
            BearerTokenProducer producer = new BearerTokenProducer(tokenValidator, requestResolverMock);
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
            BearerTokenProducer producer = new BearerTokenProducer(tokenValidator, trackingMock);
            MockInjectionPoint injectionPoint = new MockInjectionPoint(Set.of("read"), Set.of("admin"), Set.of("managers"));

            // IllegalStateException should now bubble up
            assertThrows(IllegalStateException.class, () -> producer.produceBearerTokenResult(injectionPoint));
        }

        @Test
        @DisplayName("should properly distinguish between infrastructure errors and missing tokens")
        void shouldProperlyDistinguishBetweenInfrastructureErrorsAndMissingTokens() {
            // Test infrastructure error case - should throw IllegalStateException
            requestResolverMock.setRequestContextAvailable(false);
            BearerTokenProducer producer1 = new BearerTokenProducer(tokenValidator, requestResolverMock);
            MockInjectionPoint injectionPoint1 = new MockInjectionPoint(Set.of("read"), Set.of(), Set.of());
            assertThrows(IllegalStateException.class, () -> producer1.produceBearerTokenResult(injectionPoint1));

            // Test missing token case
            requestResolverMock.setRequestContextAvailable(true);
            requestResolverMock.clearHeaders(); // No Authorization header
            BearerTokenProducer producer2 = new BearerTokenProducer(tokenValidator, requestResolverMock);
            MockInjectionPoint injectionPoint2 = new MockInjectionPoint(Set.of("read"), Set.of(), Set.of());
            var result2 = producer2.produceBearerTokenResult(injectionPoint2);
            assertEquals(BearerTokenStatus.NO_TOKEN_GIVEN, result2.getStatus());
        }
    }

    @Nested
    @DisplayName("Branch Coverage Tests")
    class BranchCoverageTests {

        @Test
        @DisplayName("should handle null annotation in produceBearerTokenResult")
        void shouldHandleNullAnnotationInProduceBearerTokenResult() {
            // Create a mock injection point with null annotation
            MockInjectionPoint injectionPoint = new MockInjectionPoint(null);

            // Setup token content
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "admin");
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Should handle null annotation gracefully
            var result = underTest.produceBearerTokenResult(injectionPoint);
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, result.getStatus());
            assertTrue(result.getAccessTokenContent().isPresent());
        }

        @Test
        @DisplayName("should handle empty authorization header list")
        void shouldHandleEmptyAuthorizationHeaderList() {
            // Create empty list for authorization header by manipulating the mock directly
            requestResolverMock.clearHeaders();
            // Add empty authorization header list - this requires direct access to headers map
            requestResolverMock.getHttpServletRequestMock().getHeaders().put("authorization", new ArrayList<>());

            getAccessTokenWithClaims(ClaimName.ROLES, "admin");

            // Should return empty result for empty authorization header list
            var result = underTest.getAccessTokenContent();
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should handle constraint violation with mixed empty/non-empty missing requirements")
        void shouldHandleMixedConstraintViolations() {
            // Setup token with only some claims
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "user");
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Test case 1: Only missing scopes (roles and groups empty)
            MockInjectionPoint injectionPoint1 = new MockInjectionPoint(
                    Set.of("admin"), Set.of(), Set.of());
            var result1 = underTest.produceBearerTokenResult(injectionPoint1);
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result1.getStatus());
            assertEquals(Set.of("admin"), result1.getMissingScopes());
            assertTrue(result1.getMissingRoles().isEmpty());
            assertTrue(result1.getMissingGroups().isEmpty());

            // Test case 2: Only missing roles (scopes and groups empty)
            MockInjectionPoint injectionPoint2 = new MockInjectionPoint(
                    Set.of(), Set.of("admin"), Set.of());
            var result2 = underTest.produceBearerTokenResult(injectionPoint2);
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result2.getStatus());
            assertTrue(result2.getMissingScopes().isEmpty());
            assertEquals(Set.of("admin"), result2.getMissingRoles());
            assertTrue(result2.getMissingGroups().isEmpty());

            // Test case 3: Only missing groups (scopes and roles empty)
            MockInjectionPoint injectionPoint3 = new MockInjectionPoint(
                    Set.of(), Set.of(), Set.of("admin"));
            var result3 = underTest.produceBearerTokenResult(injectionPoint3);
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result3.getStatus());
            assertTrue(result3.getMissingScopes().isEmpty());
            assertTrue(result3.getMissingRoles().isEmpty());
            assertEquals(Set.of("admin"), result3.getMissingGroups());
        }

        @Test
        @DisplayName("should handle constraint violation where only one type is empty")
        void shouldHandleConstraintViolationWithSingleEmptyType() {
            // Setup token with no claims
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "none");
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Test: scopes empty, but roles and groups not empty
            MockInjectionPoint injectionPoint = new MockInjectionPoint(
                    Set.of(), Set.of("admin"), Set.of("managers"));
            var result = underTest.produceBearerTokenResult(injectionPoint);
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertTrue(result.getMissingScopes().isEmpty());
            assertEquals(Set.of("admin"), result.getMissingRoles());
            assertEquals(Set.of("managers"), result.getMissingGroups());
        }

        @Test
        @DisplayName("should handle constraint violation where only two types are empty")
        void shouldHandleConstraintViolationWithTwoEmptyTypes() {
            // Setup token with no claims
            AccessTokenContent tokenContent = getAccessTokenWithClaims(ClaimName.ROLES, "none");
            requestResolverMock.setBearerToken(tokenContent.getRawToken());

            // Test: scopes and roles empty, but groups not empty
            MockInjectionPoint injectionPoint = new MockInjectionPoint(
                    Set.of(), Set.of(), Set.of("managers"));
            var result = underTest.produceBearerTokenResult(injectionPoint);
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, result.getStatus());
            assertTrue(result.getMissingScopes().isEmpty());
            assertTrue(result.getMissingRoles().isEmpty());
            assertEquals(Set.of("managers"), result.getMissingGroups());
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