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
import de.cuioss.jwt.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.jwt.quarkus.servlet.HttpServletRequestResolver;
import de.cuioss.jwt.quarkus.servlet.HttpServletRequestResolverMock;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.ClaimControlParameter;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.string.Joiner;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CDI tests for {@link BearerTokenProducer} using real CDI container.
 * Tests the producer method functionality with actual injection.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableWeld
@EnableTestLogger
@DisplayName("BearerTokenProducer CDI Tests")
@Disabled("CDI test requires complex Weld setup for ServletObjectsResolver qualifiers")
class BearerTokenProducerCdiTest {

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(BearerTokenProducer.class, TestConfiguration.class)
            .activate(RequestScoped.class)
            .build();

    @Inject
    @BearerToken
    BearerTokenResult tokenWithoutRequirements;

    @Inject
    @BearerToken(requiredScopes = {"read", "write"})
    BearerTokenResult tokenWithScopes;

    @Inject
    @BearerToken(requiredRoles = {"admin"})
    BearerTokenResult tokenWithRoles;

    @Inject
    @BearerToken(requiredGroups = {"managers"})
    BearerTokenResult tokenWithGroups;

    @Inject
    @BearerToken(requiredScopes = {"read", "write"}, requiredRoles = {"admin"}, requiredGroups = {"managers"})
    BearerTokenResult tokenWithAllRequirements;

    @Inject
    TestConfiguration testConfiguration;

    @BeforeEach
    void setUp() {
        testConfiguration.reset();
    }

    @Nested
    @DisplayName("No Token Available")
    class NoTokenAvailable {

        @Test
        @DisplayName("should not be successfully authorized when no request context")
        void shouldNotBeSuccessfullyAuthorizedWhenNoRequestContext() {
            testConfiguration.setRequestContextAvailable(false);

            assertFalse(tokenWithoutRequirements.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST, tokenWithoutRequirements.getStatus());

            assertFalse(tokenWithScopes.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST, tokenWithScopes.getStatus());

            assertFalse(tokenWithRoles.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST, tokenWithRoles.getStatus());

            assertFalse(tokenWithGroups.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST, tokenWithGroups.getStatus());

            assertFalse(tokenWithAllRequirements.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.COULD_NOT_ACCESS_REQUEST, tokenWithAllRequirements.getStatus());
        }

        @Test
        @DisplayName("should return parsing error when token validation fails")
        void shouldReturnParsingErrorWhenTokenValidationFails() {
            testConfiguration.setBearerToken("invalid.token")
                    .setShouldFail(true);

            assertFalse(tokenWithoutRequirements.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.PARSING_ERROR, tokenWithoutRequirements.getStatus());

            assertFalse(tokenWithScopes.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.PARSING_ERROR, tokenWithScopes.getStatus());

            assertFalse(tokenWithRoles.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.PARSING_ERROR, tokenWithRoles.getStatus());

            assertFalse(tokenWithGroups.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.PARSING_ERROR, tokenWithGroups.getStatus());

            assertFalse(tokenWithAllRequirements.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.PARSING_ERROR, tokenWithAllRequirements.getStatus());
        }
    }

    @Nested
    @DisplayName("Valid Token")
    class ValidToken {

        @Test
        @DisplayName("should be successfully authorized when no requirements and token is valid")
        void shouldBeSuccessfullyAuthorizedWhenNoRequirementsAndTokenValid() {
            var tokenContent = createTokenWithClaims(ClaimName.SCOPE, "read", "write");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithoutRequirements.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, tokenWithoutRequirements.getStatus());
            assertTrue(tokenWithoutRequirements.getAccessTokenContent().isPresent());
            assertEquals(tokenContent, tokenWithoutRequirements.getAccessTokenContent().get());
        }

        @Test
        @DisplayName("should be successfully authorized when all required scopes are present")
        void shouldBeSuccessfullyAuthorizedWhenAllRequiredScopesPresent() {
            var tokenContent = createTokenWithClaims(ClaimName.SCOPE, "read", "write", "admin");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithScopes.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, tokenWithScopes.getStatus());
            assertTrue(tokenWithScopes.getAccessTokenContent().isPresent());
            assertEquals(tokenContent, tokenWithScopes.getAccessTokenContent().get());
        }

        @Test
        @DisplayName("should be successfully authorized when all required roles are present")
        void shouldBeSuccessfullyAuthorizedWhenAllRequiredRolesPresent() {
            var tokenContent = createTokenWithClaims(ClaimName.ROLES, "admin", "user");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithRoles.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, tokenWithRoles.getStatus());
            assertTrue(tokenWithRoles.getAccessTokenContent().isPresent());
            assertEquals(tokenContent, tokenWithRoles.getAccessTokenContent().get());
        }

        @Test
        @DisplayName("should be successfully authorized when all required groups are present")
        void shouldBeSuccessfullyAuthorizedWhenAllRequiredGroupsPresent() {
            var tokenContent = createTokenWithClaims(ClaimName.GROUPS, "managers", "employees");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithGroups.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, tokenWithGroups.getStatus());
            assertTrue(tokenWithGroups.getAccessTokenContent().isPresent());
            assertEquals(tokenContent, tokenWithGroups.getAccessTokenContent().get());
        }

        @Test
        @DisplayName("should be successfully authorized when all requirements are met")
        void shouldBeSuccessfullyAuthorizedWhenAllRequirementsMet() {
            var tokenContent = createTokenWithMultipleClaims();
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithAllRequirements.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.FULLY_VERIFIED, tokenWithAllRequirements.getStatus());
            assertTrue(tokenWithAllRequirements.getAccessTokenContent().isPresent());
            assertEquals(tokenContent, tokenWithAllRequirements.getAccessTokenContent().get());
        }
    }

    @Nested
    @DisplayName("Requirements Not Met")
    class RequirementsNotMet {

        @Test
        @DisplayName("should return constraint violation when required scopes are missing")
        void shouldReturnConstraintViolationWhenRequiredScopesMissing() {
            var tokenContent = createTokenWithClaims(ClaimName.SCOPE, "read");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertFalse(tokenWithScopes.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, tokenWithScopes.getStatus());
            assertFalse(tokenWithScopes.getAccessTokenContent().isPresent());
        }

        @Test
        @DisplayName("should return constraint violation when required roles are missing")
        void shouldReturnConstraintViolationWhenRequiredRolesMissing() {
            var tokenContent = createTokenWithClaims(ClaimName.ROLES, "user");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertFalse(tokenWithRoles.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, tokenWithRoles.getStatus());
            assertFalse(tokenWithRoles.getAccessTokenContent().isPresent());
        }

        @Test
        @DisplayName("should return constraint violation when required groups are missing")
        void shouldReturnConstraintViolationWhenRequiredGroupsMissing() {
            var tokenContent = createTokenWithClaims(ClaimName.GROUPS, "employees");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertFalse(tokenWithGroups.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, tokenWithGroups.getStatus());
            assertFalse(tokenWithGroups.getAccessTokenContent().isPresent());
        }

        @Test
        @DisplayName("should return constraint violation when any requirement is missing")
        void shouldReturnConstraintViolationWhenAnyRequirementMissing() {
            // Token has scopes and roles but missing groups
            var tokenContent = createTokenWithClaims(ClaimName.SCOPE, "read", "write");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertFalse(tokenWithAllRequirements.isSuccessfullyAuthorized());
            assertEquals(BearerTokenStatus.CONSTRAINT_VIOLATION, tokenWithAllRequirements.getStatus());
            assertFalse(tokenWithAllRequirements.getAccessTokenContent().isPresent());
        }
    }

    /**
     * Test configuration that provides mocked dependencies.
     */
    @Alternative
    public static class TestConfiguration {

        private HttpServletRequestResolverMock requestResolverMock = new HttpServletRequestResolverMock();
        private MockTokenValidator mockTokenValidator = new MockTokenValidator();

        @Produces
        public TokenValidator tokenValidator() {
            return mockTokenValidator.getDelegate();
        }

        @Produces
        @ServletObjectsResolver(ServletObjectsResolver.Variant.VERTX)
        public HttpServletRequestResolver httpServletRequestResolver() {
            return requestResolverMock;
        }

        public TestConfiguration setBearerToken(String token) {
            requestResolverMock.setBearerToken(token);
            return this;
        }

        public TestConfiguration setRequestContextAvailable(boolean available) {
            requestResolverMock.setRequestContextAvailable(available);
            return this;
        }

        public TestConfiguration setAccessTokenContent(AccessTokenContent content) {
            mockTokenValidator.setAccessTokenContent(content);
            return this;
        }

        public TestConfiguration setShouldFail(boolean shouldFail) {
            mockTokenValidator.setShouldFail(shouldFail);
            return this;
        }

        public void reset() {
            requestResolverMock.reset();
            mockTokenValidator.setShouldFail(false);
            mockTokenValidator.setAccessTokenContent(null);
        }
    }

    private AccessTokenContent createTokenWithClaims(ClaimName claimName, String... values) {
        TestTokenHolder holder = new TestTokenHolder(TokenType.ACCESS_TOKEN,
                ClaimControlParameter.defaultForTokenType(TokenType.ACCESS_TOKEN));
        List<String> elements = Arrays.asList(values);
        String concatenated = Joiner.on(",").join(elements);
        holder.withClaim(claimName.getName(), ClaimValue.forList(concatenated, elements));
        return holder.asAccessTokenContent();
    }

    private AccessTokenContent createTokenWithMultipleClaims() {
        TestTokenHolder holder = new TestTokenHolder(TokenType.ACCESS_TOKEN,
                ClaimControlParameter.defaultForTokenType(TokenType.ACCESS_TOKEN));

        // Add scopes
        holder.withClaim(ClaimName.SCOPE.getName(),
                ClaimValue.forList("read,write", List.of("read", "write")));

        // Add roles
        holder.withClaim(ClaimName.ROLES.getName(),
                ClaimValue.forList("admin", List.of("admin")));

        // Add groups
        holder.withClaim(ClaimName.GROUPS.getName(),
                ClaimValue.forList("managers", List.of("managers")));

        return holder.asAccessTokenContent();
    }
}