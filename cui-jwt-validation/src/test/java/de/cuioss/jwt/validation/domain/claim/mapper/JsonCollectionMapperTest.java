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
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests JsonCollectionMapper functionality")
class JsonCollectionMapperTest {

    private static final String CLAIM_NAME = "testClaim";
    private final JsonCollectionMapper underTest = new JsonCollectionMapper();

    @Test
    @DisplayName("Map array of strings to list")
    void shouldMapArrayOfStrings() {
        List<String> expectedValues = List.of("value1", "value2", "value3");
        JsonObject jsonObject = createJsonObjectWithArrayClaim(CLAIM_NAME, expectedValues);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(expectedValues.size(), result.getAsList().size(), "List size should match");

        for (int i = 0; i < expectedValues.size(); i++) {
            assertEquals(expectedValues.get(i), result.getAsList().get(i),
                    "List element at index " + i + " should match");
        }
    }

    @Test
    @DisplayName("Map single string to single-element list")
    void shouldMapSingleString() {
        String input = "single-value";
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, input);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(1, result.getAsList().size(), "List should have exactly one element");
        assertEquals(input, result.getAsList().getFirst(), "List element should match input string");
    }

    @Test
    @DisplayName("Map numeric value to single-element list")
    void shouldMapNumericValue() {
        int input = 12345;
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, input)
                .build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(1, result.getAsList().size(), "List should have exactly one element");
        assertEquals(String.valueOf(input), result.getAsList().getFirst(),
                "List element should match string representation of input number");
    }

    @Test
    @DisplayName("Map boolean value to single-element list")
    void shouldMapBooleanValue() {
        boolean input = true;
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, input)
                .build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(1, result.getAsList().size(), "List should have exactly one element");
        assertEquals(String.valueOf(input), result.getAsList().getFirst(),
                "List element should match string representation of input boolean");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("Handle null, empty, and whitespace inputs")
    void shouldHandleSpecialInputs(String input) {
        JsonObject jsonObject = input == null
                ? createJsonObjectWithNullClaim(CLAIM_NAME)
                : createJsonObjectWithStringClaim(CLAIM_NAME, input);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");

        if (input == null) {
            assertTrue(result.getAsList().isEmpty(), "List should be empty for null input");
        } else if (input.trim().isEmpty()) {
            assertEquals(1, result.getAsList().size(), "List should have one element for empty/whitespace input");
            assertEquals(input, result.getAsList().getFirst(), "List element should match input string");
        }
    }

    @Test
    @DisplayName("Handle special characters in strings")
    void shouldHandleSpecialCharacters() {
        String input = "!@#$%^&*()_+{}|:<>?~`-=[]\\;',./";
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, input);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(1, result.getAsList().size(), "List should have exactly one element");
        assertEquals(input, result.getAsList().getFirst(), "List element should match input string");
    }

    @Test
    @DisplayName("Handle special characters in array elements")
    void shouldHandleSpecialCharactersInArray() {
        List<String> inputs = List.of("!@#$", "%^&*()", "_+{}|:", "<>?~`-=", "[]\\;',./");
        JsonObject jsonObject = createJsonObjectWithArrayClaim(CLAIM_NAME, inputs);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(inputs.size(), result.getAsList().size(), "List size should match");

        for (int i = 0; i < inputs.size(); i++) {
            assertEquals(inputs.get(i), result.getAsList().get(i),
                    "List element at index " + i + " should match");
        }
    }

    @Test
    @DisplayName("Handle missing claim")
    void shouldHandleMissingClaim() {
        JsonObject jsonObject = Json.createObjectBuilder().build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertTrue(result.getAsList().isEmpty(), "List should be empty for missing claim");
    }

    @Test
    @DisplayName("Handle empty JSON object")
    void shouldHandleEmptyJsonObject() {
        JsonObject emptyJsonObject = Json.createObjectBuilder().build();

        ClaimValue result = underTest.map(emptyJsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertTrue(result.getAsList().isEmpty(), "List should be empty for empty JSON object");
    }

    @Test
    @DisplayName("Handle non-string array elements")
    void shouldHandleNonStringArrayElements() {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        arrayBuilder.add("string-value");
        arrayBuilder.add(123);
        arrayBuilder.add(true);
        arrayBuilder.add(Json.createObjectBuilder().add("key", "value").build());

        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, arrayBuilder)
                .build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(ClaimValueType.STRING_LIST, result.getType(), "Type should be STRING_LIST");
        assertEquals(4, result.getAsList().size(), "List should have 4 elements");
        assertEquals("string-value", result.getAsList().getFirst(), "First element should be string-value");
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

    private JsonObject createJsonObjectWithArrayClaim(String claimName, List<String> values) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (String value : values) {
            arrayBuilder.add(value);
        }

        return Json.createObjectBuilder()
                .add(claimName, arrayBuilder)
                .build();
    }

    private JsonObject createJsonObjectWithNullClaim(String claimName) {
        return Json.createObjectBuilder()
                .addNull(claimName)
                .build();
    }
}