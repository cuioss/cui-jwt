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
package de.cuioss.sheriff.oauth.quarkus.config;

import de.cuioss.sheriff.oauth.quarkus.test.TestConfig;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static de.cuioss.sheriff.oauth.quarkus.config.JwtPropertyKeys.ISSUERS.KEYCLOAK_DEFAULT_GROUPS_ENABLED;
import static de.cuioss.sheriff.oauth.quarkus.config.JwtPropertyKeys.ISSUERS.KEYCLOAK_DEFAULT_ROLES_ENABLED;
import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests KeycloakMapperConfigResolver functionality")
class KeycloakMapperConfigResolverTest {

    private static final String TEST_ISSUER = "test-issuer";

    private Config config;
    private KeycloakMapperConfigResolver underTest;

    @BeforeEach
    void setUp() {
        Map<String, String> properties = new HashMap<>();
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);
    }

    @Test
    @DisplayName("Resolve configuration with defaults disabled")
    void shouldResolveDefaultsDisabled() {
        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve(TEST_ISSUER);

        assertNotNull(result);
        assertFalse(result.isDefaultRolesEnabled());
        assertFalse(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration with roles enabled")
    void shouldResolveRolesEnabled() {
        Map<String, String> properties = new HashMap<>();
        properties.put(KEYCLOAK_DEFAULT_ROLES_ENABLED.formatted(TEST_ISSUER), "true");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve(TEST_ISSUER);

        assertNotNull(result);
        assertTrue(result.isDefaultRolesEnabled());
        assertFalse(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration with groups enabled")
    void shouldResolveGroupsEnabled() {
        Map<String, String> properties = new HashMap<>();
        properties.put(KEYCLOAK_DEFAULT_GROUPS_ENABLED.formatted(TEST_ISSUER), "true");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve(TEST_ISSUER);

        assertNotNull(result);
        assertFalse(result.isDefaultRolesEnabled());
        assertTrue(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration with both enabled")
    void shouldResolveBothEnabled() {
        Map<String, String> properties = new HashMap<>();
        properties.put(KEYCLOAK_DEFAULT_ROLES_ENABLED.formatted(TEST_ISSUER), "true");
        properties.put(KEYCLOAK_DEFAULT_GROUPS_ENABLED.formatted(TEST_ISSUER), "true");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve(TEST_ISSUER);

        assertNotNull(result);
        assertTrue(result.isDefaultRolesEnabled());
        assertTrue(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration with explicit false values")
    void shouldResolveExplicitFalse() {
        Map<String, String> properties = new HashMap<>();
        properties.put(KEYCLOAK_DEFAULT_ROLES_ENABLED.formatted(TEST_ISSUER), "false");
        properties.put(KEYCLOAK_DEFAULT_GROUPS_ENABLED.formatted(TEST_ISSUER), "false");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve(TEST_ISSUER);

        assertNotNull(result);
        assertFalse(result.isDefaultRolesEnabled());
        assertFalse(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration for different issuers")
    void shouldResolveDifferentIssuers() {
        Map<String, String> properties = new HashMap<>();
        properties.put(KEYCLOAK_DEFAULT_ROLES_ENABLED.formatted("issuer1"), "true");
        properties.put(KEYCLOAK_DEFAULT_GROUPS_ENABLED.formatted("issuer2"), "true");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result1 = underTest.resolve("issuer1");
        KeycloakMapperConfigResolver.KeycloakMapperConfig result2 = underTest.resolve("issuer2");
        KeycloakMapperConfigResolver.KeycloakMapperConfig result3 = underTest.resolve("issuer3");

        // issuer1 has roles enabled
        assertNotNull(result1);
        assertTrue(result1.isDefaultRolesEnabled());
        assertFalse(result1.isDefaultGroupsEnabled());

        // issuer2 has groups enabled
        assertNotNull(result2);
        assertFalse(result2.isDefaultRolesEnabled());
        assertTrue(result2.isDefaultGroupsEnabled());

        // issuer3 has nothing enabled
        assertNotNull(result3);
        assertFalse(result3.isDefaultRolesEnabled());
        assertFalse(result3.isDefaultGroupsEnabled());
    }
}