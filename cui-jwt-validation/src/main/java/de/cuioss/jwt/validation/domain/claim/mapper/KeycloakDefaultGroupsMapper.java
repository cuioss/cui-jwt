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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ClaimMapper} implementation for mapping Keycloak's default groups structure.
 * <p>
 * This mapper extracts groups from Keycloak's standard {@code groups} claim and ensures
 * they are properly formatted for the CUI JWT library's authorization mechanisms.
 * <p>
 * Keycloak typically includes groups in the token as:
 * <pre>
 * {
 *   "groups": ["/test-group", "/admin-group"]
 * }
 * </pre>
 * <p>
 * This mapper processes the groups array and optionally removes leading path separators
 * if configured with full path set to false in Keycloak's group membership mapper.
 * <p>
 * The mapper handles:
 * <ul>
 *   <li>Missing {@code groups} claim - returns empty list</li>
 *   <li>Empty groups array - returns empty list</li>
 *   <li>Non-array groups value - returns empty list</li>
 *   <li>Group names with or without path prefixes</li>
 * </ul>
 * <p>
 * This mapper provides a bridge between Keycloak's default group claim structure
 * and the CUI JWT library's expected format, ensuring seamless integration
 * without requiring custom protocol mappers in Keycloak.
 *
 * @since 1.0
 * @see <a href="https://www.keycloak.org/docs/latest/server_admin/index.html#_group-mappers">Keycloak Group Mappers</a>
 */
public class KeycloakDefaultGroupsMapper implements ClaimMapper {

    private static final String GROUPS_CLAIM = "groups";

    @Override
    public ClaimValue map(@NonNull JsonObject jsonObject, @NonNull String claimName) {
        Optional<JsonValue> groupsValue = ClaimMapperUtils.getJsonValue(jsonObject, GROUPS_CLAIM);
        if (groupsValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }

        JsonValue groups = groupsValue.get();
        if (groups.getValueType() != JsonValue.ValueType.ARRAY) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }

        JsonArray groupsArray = groups.asJsonArray();
        String originalValue = groupsArray.toString();
        List<String> groupsList = ClaimMapperUtils.extractStringsFromJsonArray(groupsArray);

        return ClaimValue.forList(originalValue, Collections.unmodifiableList(groupsList));
    }
}