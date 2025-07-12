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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for {@link ClaimMapper} implementations.
 * Provides common methods for handling JSON objects and values.
 * <p>
 * This class is <b>public</b> due to Lombok's {@code @UtilityClass} annotation and intended for use only within the claim mapper package.
 *
 * @since 1.0
 */
@UtilityClass
class ClaimMapperUtils {

    /**
     * Checks if the given JSON object does not contain a claim with the given name.
     * Returns true if the JSON object does not contain the claim.
     *
     * @param jsonObject the JSON object to check
     * @param claimName  the name of the claim to check for
     * @return true if the JSON object does not contain the claim, false otherwise
     * @since 1.0
     */
    boolean doesNotContainClaim(@NonNull JsonObject jsonObject, @NonNull String claimName) {
        return !jsonObject.containsKey(claimName);
    }

    /**
     * Gets the JSON value for the given claim name from the JSON object.
     * Returns an empty Optional if the JSON object does not contain the claim
     * or if the value is a JSON null value.
     *
     * @param jsonObject the JSON object to get the value from
     * @param claimName  the name of the claim to get
     * @return an Optional containing the JSON value for the claim, or empty if not found or if the value is null
     * @since 1.0
     */
    Optional<JsonValue> getJsonValue(@NonNull JsonObject jsonObject, @NonNull String claimName) {
        if (doesNotContainClaim(jsonObject, claimName)) {
            return Optional.empty();
        }
        JsonValue jsonValue = jsonObject.get(claimName);
        if (isNullValue(jsonValue)) {
            return Optional.empty();
        }
        return Optional.of(jsonValue);
    }

    /**
     * Checks if the given JSON value is null or represents a JSON null value.
     *
     * @param jsonValue the JSON value to check
     * @return true if the JSON value is null or represents a JSON null value, false otherwise
     * @since 1.0
     */
    boolean isNullValue(JsonValue jsonValue) {
        return jsonValue == null || jsonValue.getValueType() == JsonValue.ValueType.NULL;
    }

    /**
     * Extracts a string from a JSON value, handling different value types.
     * For STRING values, returns the string value.
     * For other types (NUMBER, TRUE, FALSE, OBJECT), returns the string representation.
     * Returns null if the value type is not supported (e.g., NULL or ARRAY).
     *
     * @param jsonObject the JSON object containing the value
     * @param claimName the name of the claim in the JSON object
     * @param jsonValue the JSON value to extract a string from
     * @return the extracted string, or null if the value type is not supported
     * @since 1.0
     */
    String extractStringFromJsonValue(@NonNull JsonObject jsonObject, @NonNull String claimName, @NonNull JsonValue jsonValue) {
        return switch (jsonValue.getValueType()) {
            case STRING -> jsonObject.getString(claimName);
            case NUMBER, TRUE, FALSE, OBJECT -> jsonValue.toString();
            default ->
                // This should never happen as we've already checked for NULL
                    null;
        };
    }

    /**
     * Extracts a list of strings from a JSON array.
     * For each element in the array, if it's a STRING, adds the string value to the list.
     * For other types, adds the string representation to the list.
     *
     * @param jsonArray the JSON array to extract strings from
     * @return a list of strings extracted from the JSON array
     * @since 1.0
     */
    List<String> extractStringsFromJsonArray(@NonNull JsonArray jsonArray) {
        List<String> result = new ArrayList<>();
        for (JsonValue item : jsonArray) {
            if (item.getValueType() == JsonValue.ValueType.STRING) {
                result.add(((JsonString) item).getString());
            } else {
                // For non-string values, convert to string
                result.add(item.toString());
            }
        }
        return result;
    }
}
