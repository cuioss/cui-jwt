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
package de.cuioss.jwt.validation.domain.claim.mapper;

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.ClaimValueType;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests KeycloakDefaultGroupsMapper functionality")
class KeycloakDefaultGroupsMapperTest {

    private static final String CLAIM_NAME = "groups";
    private final KeycloakDefaultGroupsMapper underTest = new KeycloakDefaultGroupsMapper();

    @Test
    @DisplayName("Map groups claim to group list")
    void shouldMapGroupsClaim() {
        List<String> expectedGroups = List.of("/test-group", "/admin-group", "/user-group");
        JsonObject jsonObject = createKeycloakTokenWithGroups(expectedGroups);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertTrue(result.isPresent());
        assertEquals(expectedGroups, result.getAsList());
        assertNotNull(result.getOriginalString());
    }

    @Test
    @DisplayName("Handle missing groups claim")
    void shouldHandleMissingGroups() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("sub", "user123")
                .add("iss", "https://keycloak.example.com")
                .build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertFalse(result.isPresent());
        assertTrue(result.getAsList().isEmpty());
    }

    @Test
    @DisplayName("Handle empty groups array")
    void shouldHandleEmptyGroupsArray() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("sub", "user123")
                .add("groups", Json.createArrayBuilder().build())
                .build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertTrue(result.isPresent());
        assertTrue(result.getAsList().isEmpty());
        assertEquals("[]", result.getOriginalString());
    }

    @Test
    @DisplayName("Handle non-array groups value")
    void shouldHandleNonArrayGroups() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("sub", "user123")
                .add("groups", "test-group")
                .build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertFalse(result.isPresent());
        assertTrue(result.getAsList().isEmpty());
    }

    @Test
    @DisplayName("Handle null groups")
    void shouldHandleNullGroups() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("sub", "user123")
                .addNull("groups")
                .build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertFalse(result.isPresent());
        assertTrue(result.getAsList().isEmpty());
    }

    @Test
    @DisplayName("Handle single group in array")
    void shouldHandleSingleGroup() {
        List<String> expectedGroups = List.of("/admin-group");
        JsonObject jsonObject = createKeycloakTokenWithGroups(expectedGroups);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertTrue(result.isPresent());
        assertEquals(expectedGroups, result.getAsList());
        assertEquals(1, result.getAsList().size());
        assertEquals("/admin-group", result.getAsList().getFirst());
    }

    @Test
    @DisplayName("Handle groups without path prefix")
    void shouldHandleGroupsWithoutPathPrefix() {
        List<String> expectedGroups = List.of("test-group", "admin-group", "user-group");
        JsonObject jsonObject = createKeycloakTokenWithGroups(expectedGroups);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertTrue(result.isPresent());
        assertEquals(expectedGroups, result.getAsList());
    }

    @Test
    @DisplayName("Handle mixed group name formats")
    void shouldHandleMixedGroupNameFormats() {
        List<String> expectedGroups = List.of("/full-path-group", "simple-group", "/nested/path/group");
        JsonObject jsonObject = createKeycloakTokenWithGroups(expectedGroups);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertTrue(result.isPresent());
        assertEquals(expectedGroups, result.getAsList());
    }

    @Test
    @DisplayName("Handle complex group names")
    void shouldHandleComplexGroupNames() {
        List<String> expectedGroups = List.of("/realm-management", "/account-console", "/offline_access");
        JsonObject jsonObject = createKeycloakTokenWithGroups(expectedGroups);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertTrue(result.isPresent());
        assertEquals(expectedGroups, result.getAsList());
    }

    @Test
    @DisplayName("Handle groups with special characters")
    void shouldHandleGroupsWithSpecialCharacters() {
        List<String> expectedGroups = List.of("/test-group_123", "/admin@domain", "/group.with.dots");
        JsonObject jsonObject = createKeycloakTokenWithGroups(expectedGroups);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result);
        assertEquals(ClaimValueType.STRING_LIST, result.getType());
        assertTrue(result.isPresent());
        assertEquals(expectedGroups, result.getAsList());
    }

    private JsonObject createKeycloakTokenWithGroups(List<String> groups) {
        JsonArrayBuilder groupsArrayBuilder = Json.createArrayBuilder();
        groups.forEach(groupsArrayBuilder::add);

        return Json.createObjectBuilder()
                .add("sub", "user123")
                .add("iss", "https://keycloak.example.com")
                .add("groups", groupsArrayBuilder.build())
                .build();
    }
}