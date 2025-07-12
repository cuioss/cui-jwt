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
package de.cuioss.jwt.quarkus.config;

import de.cuioss.jwt.quarkus.test.TestConfig;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static de.cuioss.jwt.quarkus.config.JwtPropertyKeys.KEYCLOAK.MAPPERS.DEFAULT_GROUPS_ENABLED;
import static de.cuioss.jwt.quarkus.config.JwtPropertyKeys.KEYCLOAK.MAPPERS.DEFAULT_ROLES_ENABLED;
import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests KeycloakMapperConfigResolver functionality")
class KeycloakMapperConfigResolverTest {

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
        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve();

        assertNotNull(result);
        assertFalse(result.isDefaultRolesEnabled());
        assertFalse(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration with roles enabled")
    void shouldResolveRolesEnabled() {
        Map<String, String> properties = new HashMap<>();
        properties.put(DEFAULT_ROLES_ENABLED, "true");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve();

        assertNotNull(result);
        assertTrue(result.isDefaultRolesEnabled());
        assertFalse(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration with groups enabled")
    void shouldResolveGroupsEnabled() {
        Map<String, String> properties = new HashMap<>();
        properties.put(DEFAULT_GROUPS_ENABLED, "true");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve();

        assertNotNull(result);
        assertFalse(result.isDefaultRolesEnabled());
        assertTrue(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration with both enabled")
    void shouldResolveBothEnabled() {
        Map<String, String> properties = new HashMap<>();
        properties.put(DEFAULT_ROLES_ENABLED, "true");
        properties.put(DEFAULT_GROUPS_ENABLED, "true");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve();

        assertNotNull(result);
        assertTrue(result.isDefaultRolesEnabled());
        assertTrue(result.isDefaultGroupsEnabled());
    }

    @Test
    @DisplayName("Resolve configuration with explicit false values")
    void shouldResolveExplicitFalse() {
        Map<String, String> properties = new HashMap<>();
        properties.put(DEFAULT_ROLES_ENABLED, "false");
        properties.put(DEFAULT_GROUPS_ENABLED, "false");
        config = new TestConfig(properties);
        underTest = new KeycloakMapperConfigResolver(config);

        KeycloakMapperConfigResolver.KeycloakMapperConfig result = underTest.resolve();

        assertNotNull(result);
        assertFalse(result.isDefaultRolesEnabled());
        assertFalse(result.isDefaultGroupsEnabled());
    }
}