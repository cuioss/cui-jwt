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


import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

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
@Disabled("CDI test causes conflicts with other tests - needs isolation")
class BearerTokenProducerCdiTest {

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(BearerTokenProducer.class, TestConfiguration.class)
            .activate(RequestScoped.class)
            .build();

    @Inject
    @BearerToken
    Instance<AccessTokenContent> tokenWithoutRequirements;

    @Inject
    @BearerToken(requiredScopes = {"read", "write"})
    Instance<AccessTokenContent> tokenWithScopes;

    @Inject
    @BearerToken(requiredRoles = {"admin"})
    Instance<AccessTokenContent> tokenWithRoles;

    @Inject
    @BearerToken(requiredGroups = {"managers"})
    Instance<AccessTokenContent> tokenWithGroups;

    @Inject
    @BearerToken(requiredScopes = {"read", "write"}, requiredRoles = {"admin"}, requiredGroups = {"managers"})
    Instance<AccessTokenContent> tokenWithAllRequirements;

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
        @DisplayName("should not be resolvable when no request context")
        void shouldNotBeResolvableWhenNoRequestContext() {
            testConfiguration.setRequestContextAvailable(false);

            // For producer methods, isResolvable() returns true if the bean definition exists
            // We need to check if get() returns null
            assertTrue(tokenWithoutRequirements.isResolvable());
            assertNull(tokenWithoutRequirements.get());

            assertTrue(tokenWithScopes.isResolvable());
            assertNull(tokenWithScopes.get());

            assertTrue(tokenWithRoles.isResolvable());
            assertNull(tokenWithRoles.get());

            assertTrue(tokenWithGroups.isResolvable());
            assertNull(tokenWithGroups.get());

            assertTrue(tokenWithAllRequirements.isResolvable());
            assertNull(tokenWithAllRequirements.get());
        }

        @Test
        @DisplayName("should return null when token validation fails")
        void shouldReturnNullWhenTokenValidationFails() {
            testConfiguration.setBearerToken("invalid.token")
                    .setShouldFail(true);

            assertTrue(tokenWithoutRequirements.isResolvable());
            assertNull(tokenWithoutRequirements.get());

            assertTrue(tokenWithScopes.isResolvable());
            assertNull(tokenWithScopes.get());

            assertTrue(tokenWithRoles.isResolvable());
            assertNull(tokenWithRoles.get());

            assertTrue(tokenWithGroups.isResolvable());
            assertNull(tokenWithGroups.get());

            assertTrue(tokenWithAllRequirements.isResolvable());
            assertNull(tokenWithAllRequirements.get());
        }
    }

    @Nested
    @DisplayName("Valid Token")
    class ValidToken {

        @Test
        @DisplayName("should be resolvable when no requirements and token is valid")
        void shouldBeResolvableWhenNoRequirementsAndTokenValid() {
            var tokenContent = createTokenWithClaims(ClaimName.SCOPE, "read", "write");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithoutRequirements.isResolvable());
            assertEquals(tokenContent, tokenWithoutRequirements.get());
        }

        @Test
        @DisplayName("should be resolvable when all required scopes are present")
        void shouldBeResolvableWhenAllRequiredScopesPresent() {
            var tokenContent = createTokenWithClaims(ClaimName.SCOPE, "read", "write", "admin");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithScopes.isResolvable());
            assertEquals(tokenContent, tokenWithScopes.get());
        }

        @Test
        @DisplayName("should be resolvable when all required roles are present")
        void shouldBeResolvableWhenAllRequiredRolesPresent() {
            var tokenContent = createTokenWithClaims(ClaimName.ROLES, "admin", "user");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithRoles.isResolvable());
            assertEquals(tokenContent, tokenWithRoles.get());
        }

        @Test
        @DisplayName("should be resolvable when all required groups are present")
        void shouldBeResolvableWhenAllRequiredGroupsPresent() {
            var tokenContent = createTokenWithClaims(ClaimName.GROUPS, "managers", "employees");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithGroups.isResolvable());
            assertEquals(tokenContent, tokenWithGroups.get());
        }

        @Test
        @DisplayName("should be resolvable when all requirements are met")
        void shouldBeResolvableWhenAllRequirementsMet() {
            var tokenContent = createTokenWithMultipleClaims();
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithAllRequirements.isResolvable());
            assertEquals(tokenContent, tokenWithAllRequirements.get());
        }
    }

    @Nested
    @DisplayName("Requirements Not Met")
    class RequirementsNotMet {

        @Test
        @DisplayName("should return null when required scopes are missing")
        void shouldReturnNullWhenRequiredScopesMissing() {
            var tokenContent = createTokenWithClaims(ClaimName.SCOPE, "read");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithScopes.isResolvable());
            assertNull(tokenWithScopes.get());
        }

        @Test
        @DisplayName("should return null when required roles are missing")
        void shouldReturnNullWhenRequiredRolesMissing() {
            var tokenContent = createTokenWithClaims(ClaimName.ROLES, "user");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithRoles.isResolvable());
            assertNull(tokenWithRoles.get());
        }

        @Test
        @DisplayName("should return null when required groups are missing")
        void shouldReturnNullWhenRequiredGroupsMissing() {
            var tokenContent = createTokenWithClaims(ClaimName.GROUPS, "employees");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithGroups.isResolvable());
            assertNull(tokenWithGroups.get());
        }

        @Test
        @DisplayName("should return null when any requirement is missing")
        void shouldReturnNullWhenAnyRequirementMissing() {
            // Token has scopes and roles but missing groups
            var tokenContent = createTokenWithClaims(ClaimName.SCOPE, "read", "write");
            testConfiguration.setBearerToken(tokenContent.getRawToken())
                    .setAccessTokenContent(tokenContent);

            assertTrue(tokenWithAllRequirements.isResolvable());
            assertNull(tokenWithAllRequirements.get());
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
            return mockTokenValidator;
        }

        @Produces
        @ServletObjectsResolver(ServletObjectsResolver.Variant.RESTEASY)
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