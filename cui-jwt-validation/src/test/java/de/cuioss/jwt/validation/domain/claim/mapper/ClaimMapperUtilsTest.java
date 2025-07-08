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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests ClaimMapperUtils functionality")
class ClaimMapperUtilsTest {

    private static final String CLAIM_NAME = "testClaim";

    @Test
    @DisplayName("Return true when claim is missing")
    void doesNotContainClaimShouldReturnTrueWhenClaimIsMissing() {
        JsonObject jsonObject = Json.createObjectBuilder().build();

        boolean result = ClaimMapperUtils.doesNotContainClaim(jsonObject, CLAIM_NAME);

        assertTrue(result, "Should return true when claim is missing");
    }

    @Test
    @DisplayName("Return false when claim exists")
    void doesNotContainClaimShouldReturnFalseWhenClaimExists() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, "value")
                .build();

        boolean result = ClaimMapperUtils.doesNotContainClaim(jsonObject, CLAIM_NAME);

        assertFalse(result, "Should return false when claim exists");
    }

    @Test
    @DisplayName("Return false when claim exists but is null")
    void doesNotContainClaimShouldReturnFalseWhenClaimExistsButIsNull() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .addNull(CLAIM_NAME)
                .build();

        boolean result = ClaimMapperUtils.doesNotContainClaim(jsonObject, CLAIM_NAME);

        assertFalse(result, "Should return false when claim exists but is null");
    }

    @Test
    @DisplayName("Return empty Optional when claim is missing")
    void getJsonValueShouldReturnEmptyOptionalWhenClaimIsMissing() {
        JsonObject jsonObject = Json.createObjectBuilder().build();

        Optional<JsonValue> result = ClaimMapperUtils.getJsonValue(jsonObject, CLAIM_NAME);

        assertFalse(result.isPresent(), "Should return empty Optional when claim is missing");
    }

    @Test
    @DisplayName("Return empty Optional when claim is null")
    void getJsonValueShouldReturnEmptyOptionalWhenClaimIsNull() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .addNull(CLAIM_NAME)
                .build();

        Optional<JsonValue> result = ClaimMapperUtils.getJsonValue(jsonObject, CLAIM_NAME);

        assertFalse(result.isPresent(), "Should return empty Optional when claim is null");
    }

    @Test
    @DisplayName("Return Optional with value when claim exists and is not null")
    void getJsonValueShouldReturnOptionalWithValueWhenClaimExistsAndIsNotNull() {
        String value = "test-value";
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, value)
                .build();

        Optional<JsonValue> result = ClaimMapperUtils.getJsonValue(jsonObject, CLAIM_NAME);

        assertTrue(result.isPresent(), "Should return Optional with value when claim exists and is not null");
        assertEquals(JsonValue.ValueType.STRING, result.get().getValueType(), "Value type should be STRING");
    }

    @Test
    @DisplayName("Return true when JsonValue is null")
    void isNullValueShouldReturnTrueWhenJsonValueIsNull() {
        boolean result = ClaimMapperUtils.isNullValue(null);

        assertTrue(result, "Should return true when JsonValue is null");
    }

    @Test
    @DisplayName("Return true when JsonValue is JSON null")
    void isNullValueShouldReturnTrueWhenJsonValueIsJsonNull() {
        JsonValue jsonValue = JsonValue.NULL;

        boolean result = ClaimMapperUtils.isNullValue(jsonValue);

        assertTrue(result, "Should return true when JsonValue is JSON null");
    }

    @Test
    @DisplayName("Return false when JsonValue is not null")
    void isNullValueShouldReturnFalseWhenJsonValueIsNotNull() {
        JsonValue jsonValue = Json.createObjectBuilder()
                .add("key", "value")
                .build();

        boolean result = ClaimMapperUtils.isNullValue(jsonValue);

        assertFalse(result, "Should return false when JsonValue is not null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"string", "123", "true"})
    @DisplayName("Return false for various non-null JSON values")
    void isNullValueShouldReturnFalseForVariousNonNullJsonValues(String value) {
        JsonValue jsonValue = Json.createObjectBuilder()
                .add("key", value)
                .build()
                .get("key");

        boolean result = ClaimMapperUtils.isNullValue(jsonValue);

        assertFalse(result, "Should return false for non-null JSON value: " + value);
    }

    @Test
    @DisplayName("Extract string from STRING value")
    void extractStringFromJsonValueShouldExtractStringFromStringValue() {
        String value = "test-string";
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, value)
                .build();
        JsonValue jsonValue = jsonObject.get(CLAIM_NAME);

        String result = ClaimMapperUtils.extractStringFromJsonValue(jsonObject, CLAIM_NAME, jsonValue);

        assertEquals(value, result, "Should extract string value correctly");
    }

    @Test
    @DisplayName("Extract string from NUMBER value")
    void extractStringFromJsonValueShouldExtractStringFromNumberValue() {
        int value = 12345;
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, value)
                .build();
        JsonValue jsonValue = jsonObject.get(CLAIM_NAME);

        String result = ClaimMapperUtils.extractStringFromJsonValue(jsonObject, CLAIM_NAME, jsonValue);

        assertEquals(String.valueOf(value), result, "Should extract number as string correctly");
    }

    @Test
    @DisplayName("Extract string from BOOLEAN value")
    void extractStringFromJsonValueShouldExtractStringFromBooleanValue() {
        boolean value = true;
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, value)
                .build();
        JsonValue jsonValue = jsonObject.get(CLAIM_NAME);

        String result = ClaimMapperUtils.extractStringFromJsonValue(jsonObject, CLAIM_NAME, jsonValue);

        assertEquals(String.valueOf(value), result, "Should extract boolean as string correctly");
    }

    @Test
    @DisplayName("Extract string from OBJECT value")
    void extractStringFromJsonValueShouldExtractStringFromObjectValue() {
        JsonObject nestedObject = Json.createObjectBuilder()
                .add("key", "value")
                .build();
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, nestedObject)
                .build();
        JsonValue jsonValue = jsonObject.get(CLAIM_NAME);

        String result = ClaimMapperUtils.extractStringFromJsonValue(jsonObject, CLAIM_NAME, jsonValue);

        assertEquals(nestedObject.toString(), result, "Should extract object as string correctly");
    }

    @Test
    @DisplayName("Extract strings from array of strings")
    void extractStringsFromJsonArrayShouldExtractStringsFromArrayOfStrings() {
        List<String> expectedValues = List.of("value1", "value2", "value3");
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (String value : expectedValues) {
            arrayBuilder.add(value);
        }
        JsonArray jsonArray = arrayBuilder.build();

        List<String> result = ClaimMapperUtils.extractStringsFromJsonArray(jsonArray);

        assertEquals(expectedValues.size(), result.size(), "Result size should match expected");
        for (int i = 0; i < expectedValues.size(); i++) {
            assertEquals(expectedValues.get(i), result.get(i), "Element at index " + i + " should match");
        }
    }

    @Test
    @DisplayName("Handle mixed types in array")
    void extractStringsFromJsonArrayShouldHandleMixedTypesInArray() {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        arrayBuilder.add("string-value");
        arrayBuilder.add(123);
        arrayBuilder.add(true);
        arrayBuilder.add(Json.createObjectBuilder().add("key", "value").build());
        JsonArray jsonArray = arrayBuilder.build();

        List<String> result = ClaimMapperUtils.extractStringsFromJsonArray(jsonArray);

        assertEquals(4, result.size(), "Result size should be 4");
        assertEquals("string-value", result.getFirst(), "First element should be string-value");
    }

    @Test
    @DisplayName("Handle empty array")
    void extractStringsFromJsonArrayShouldHandleEmptyArray() {
        JsonArray jsonArray = Json.createArrayBuilder().build();

        List<String> result = ClaimMapperUtils.extractStringsFromJsonArray(jsonArray);

        assertTrue(result.isEmpty(), "Result should be empty");
    }
}
