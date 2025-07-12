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
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.NonNull;

import java.util.Optional;

/**
 * A {@link ClaimMapper} implementation that maps a claim from a {@link JsonObject} to a
 * {@link ClaimValue} without any transformation.
 * This is useful for claims that are already in the desired format.
 *
 * @since 1.0
 */
public class IdentityMapper implements ClaimMapper {
    @Override
    public ClaimValue map(@NonNull JsonObject jsonObject, @NonNull String claimName) {

        Optional<JsonValue> optionalJsonValue = ClaimMapperUtils.getJsonValue(jsonObject, claimName);
        if (optionalJsonValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING);
        }
        JsonValue jsonValue = optionalJsonValue.get();

        // According to JWT specification, we should only handle the following value types:
        // STRING, NUMBER, BOOLEAN, ARRAY, OBJECT
        // For IdentityMapper, we convert all types to string representation
        String value;
        switch (jsonValue.getValueType()) {
            case STRING:
                value = jsonObject.getString(claimName);
                break;
            case NUMBER, TRUE, FALSE, ARRAY, OBJECT:
                value = jsonValue.toString();
                break;
            default:
                // This should never happen as we've already checked for NULL
                return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING);
        }

        return ClaimValue.forPlainString(value);
    }
}
