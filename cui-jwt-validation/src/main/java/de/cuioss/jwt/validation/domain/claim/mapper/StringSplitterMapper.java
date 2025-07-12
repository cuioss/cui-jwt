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
import de.cuioss.tools.string.Splitter;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ClaimMapper} implementation for splitting string claims by a specified character.
 * <p>
 * This mapper only works with {@link JsonValue.ValueType#STRING} values and splits the string
 * using the provided split character. It trims the results and omits empty strings.
 * <p>
 * This is particularly useful for claims that contain multiple values in a single string
 * separated by a specific character (e.g., comma-separated roles, colon-separated groups).
 * <p>
 * Example usage:
 * <pre>
 * // Create a mapper that splits by comma
 * StringSplitterMapper commaMapper = new StringSplitterMapper(',');
 * 
 * // Use with a claim that contains comma-separated values
 * // e.g., "roles": "admin,user,manager"
 * ClaimValue roles = commaMapper.map(jsonObject, "roles");
 * </pre>
 *
 * @since 1.0
 */
@RequiredArgsConstructor
public class StringSplitterMapper implements ClaimMapper {

    /**
     * The character to split the string by.
     */
    @NonNull
    private final Character splitChar;

    @Override
    public ClaimValue map(@NonNull JsonObject jsonObject, @NonNull String claimName) {
        Optional<JsonValue> optionalJsonValue = ClaimMapperUtils.getJsonValue(jsonObject, claimName);
        if (optionalJsonValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }
        JsonValue jsonValue = optionalJsonValue.get();

        // Only handle STRING value types
        if (jsonValue.getValueType() != JsonValue.ValueType.STRING) {
            throw new IllegalArgumentException("Unsupported JSON value type for StringSplitterMapper: " +
                    jsonValue.getValueType() + ". Only STRING values are supported.");
        }

        String originalValue = ClaimMapperUtils.extractStringFromJsonValue(jsonObject, claimName, jsonValue);
        List<String> values = Splitter.on(splitChar).trimResults().omitEmptyStrings().splitToList(originalValue);

        return ClaimValue.forList(originalValue, Collections.unmodifiableList(values));
    }
}