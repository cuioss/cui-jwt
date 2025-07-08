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
package de.cuioss.jwt.validation.domain.claim.mapper;

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.ClaimValueType;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("Tests ScopeMapper functionality")
class ScopeMapperTest {

    private static final String CLAIM_NAME = "scope";
    private final ScopeMapper underTest = new ScopeMapper();

    @Test
    @DisplayName("Should correctly map space-separated scopes")
    void shouldMapSpaceSeparatedScopes() {

        String input = "openid profile email";
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, input);

        SortedSet<String> expected = new TreeSet<>();
        expected.add("openid");
        expected.add("profile");
        expected.add("email");
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(input, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(new ArrayList<>(expected), result.getAsList(), "Scopes should be correctly parsed");
    }

    @Test
    @DisplayName("Should correctly map array of scopes")
    void shouldMapArrayOfScopes() {

        JsonArray scopesArray = Json.createArrayBuilder()
                .add("openid")
                .add("profile")
                .add("email")
                .build();

        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, scopesArray)
                .build();

        SortedSet<String> expected = new TreeSet<>();
        expected.add("openid");
        expected.add("profile");
        expected.add("email");
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(scopesArray.toString(), result.getOriginalString(), "Original string should be the JSON array string");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(new ArrayList<>(expected), result.getAsList(), "Scopes should be correctly parsed from array");
    }

    @Test
    @DisplayName("Should handle duplicate scopes")
    void shouldHandleDuplicateScopes() {

        String input = "openid profile openid email profile";
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, input);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(input, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(3, result.getAsList().size(), "Should have 3 unique scopes");
    }

    @Test
    @DisplayName("Should handle duplicate scopes in array")
    void shouldHandleDuplicateScopesInArray() {

        JsonArray scopesArray = Json.createArrayBuilder()
                .add("openid")
                .add("profile")
                .add("openid")
                .add("email")
                .add("profile")
                .build();

        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, scopesArray)
                .build();

        SortedSet<String> expected = new TreeSet<>();
        expected.add("openid");
        expected.add("profile");
        expected.add("email");
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(scopesArray.toString(), result.getOriginalString(), "Original string should be the JSON array string");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(new ArrayList<>(expected), result.getAsList(), "Duplicate scopes should be removed");
        assertEquals(3, result.getAsList().size(), "Should have 3 unique scopes");
    }

    @Test
    @DisplayName("Should handle scopes with leading/trailing whitespace")
    void shouldHandleScopesWithWhitespace() {

        String input = "  openid   profile  email  ";
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, input);

        SortedSet<String> expected = new TreeSet<>();
        expected.add("openid");
        expected.add("profile");
        expected.add("email");
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(input, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(new ArrayList<>(expected), result.getAsList(), "Scopes should be correctly parsed with whitespace trimmed");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("Should handle null, empty, and whitespace inputs")
    void shouldHandleSpecialInputs(String input) {

        JsonObject jsonObject = input == null
                ? createJsonObjectWithNullClaim(CLAIM_NAME)
                : createJsonObjectWithStringClaim(CLAIM_NAME, input);
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(input, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertTrue(result.getAsList().isEmpty(), "Scope list should be empty");
    }

    @Test
    @DisplayName("Should handle special characters in scopes")
    void shouldHandleSpecialCharactersInScopes() {

        String input = "scope1 scope-with-dash scope_with_underscore scope.with.dots scope@with@at";
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, input);

        SortedSet<String> expected = new TreeSet<>();
        expected.add("scope1");
        expected.add("scope-with-dash");
        expected.add("scope_with_underscore");
        expected.add("scope.with.dots");
        expected.add("scope@with@at");
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(input, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(new ArrayList<>(expected), result.getAsList(), "Scopes with special characters should be correctly parsed");
    }

    @Test
    @DisplayName("Should handle special characters in array scopes")
    void shouldHandleSpecialCharactersInArrayScopes() {

        JsonArray scopesArray = Json.createArrayBuilder()
                .add("scope1")
                .add("scope-with-dash")
                .add("scope_with_underscore")
                .add("scope.with.dots")
                .add("scope@with@at")
                .build();

        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, scopesArray)
                .build();

        SortedSet<String> expected = new TreeSet<>();
        expected.add("scope1");
        expected.add("scope-with-dash");
        expected.add("scope_with_underscore");
        expected.add("scope.with.dots");
        expected.add("scope@with@at");
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(scopesArray.toString(), result.getOriginalString(), "Original string should be the JSON array string");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(new ArrayList<>(expected), result.getAsList(), "Scopes with special characters should be correctly parsed");
    }

    @Test
    @DisplayName("Should handle missing claim")
    void shouldHandleMissingClaim() {

        JsonObject jsonObject = Json.createObjectBuilder().build();
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertNull(result.getOriginalString(), "Original string should be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertTrue(result.getAsList().isEmpty(), "Scope list should be empty");
    }

    @Test
    @DisplayName("Should handle empty JsonObject")
    void shouldHandleEmptyJsonObject() {

        JsonObject emptyJsonObject = Json.createObjectBuilder().build();
        ClaimValue result = underTest.map(emptyJsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertNull(result.getOriginalString(), "Original string should be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertTrue(result.getAsList().isEmpty(), "Scope list should be empty");
    }

    @Test
    @DisplayName("Should handle non-string array elements")
    void shouldHandleNonStringArrayElements() {

        JsonArray scopesArray = Json.createArrayBuilder()
                .add("openid")
                .add(123)
                .add(true)
                .build();

        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, scopesArray)
                .build();

        SortedSet<String> expected = new TreeSet<>();
        expected.add("openid");
        expected.add("123");
        expected.add("true");
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);
        assertNotNull(result, "Result should not be null");
        assertEquals(scopesArray.toString(), result.getOriginalString(), "Original string should be the JSON array string");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(new ArrayList<>(expected), result.getAsList(), "Non-string array elements should be converted to strings");
    }

    // Helper methods

    private JsonObject createJsonObjectWithStringClaim(String claimName, String value) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (value != null) {
            builder.add(claimName, value);
        } else {
            builder.addNull(claimName);
        }
        return builder.build();
    }

    private JsonObject createJsonObjectWithNullClaim(String claimName) {
        return Json.createObjectBuilder()
                .addNull(claimName)
                .build();
    }
}