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
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ClaimMapper} implementation for mapping JSON values to collections.
 * This mapper handles the following cases:
 * <ul>
 *   <li>JSON arrays: Converts each element to a string and adds it to the list</li>
 *   <li>JSON strings: Wraps the string in a single-element list</li>
 *   <li>Other JSON types: Converts to string and wraps in a single-element list</li>
 * </ul>
 * This is particularly useful for {@link ClaimValueType#STRING_LIST} claims.
 *
 * @since 1.0
 */
public class JsonCollectionMapper implements ClaimMapper {
    @Override
    public ClaimValue map(@NonNull JsonObject jsonObject, @NonNull String claimName) {
        Optional<JsonValue> optionalJsonValue = ClaimMapperUtils.getJsonValue(jsonObject, claimName);
        if (optionalJsonValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }
        JsonValue jsonValue = optionalJsonValue.get();

        String originalValue;
        List<String> values;

        if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
            // Handle JSON array
            JsonArray arrayValue = jsonObject.getJsonArray(claimName);
            originalValue = arrayValue.toString();
            values = ClaimMapperUtils.extractStringsFromJsonArray(arrayValue);
        } else {
            // Handle all other types by wrapping them in a single-element list
            originalValue = ClaimMapperUtils.extractStringFromJsonValue(jsonObject, claimName, jsonValue);

            // Add the single value to the list if it's not null
            values = new ArrayList<>();
            if (originalValue != null) {
                values.add(originalValue);
            }
        }

        return ClaimValue.forList(originalValue, Collections.unmodifiableList(values));
    }
}
