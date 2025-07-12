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
 * A {@link ClaimMapper} implementation for mapping Keycloak's default roles structure.
 * <p>
 * This mapper extracts roles from Keycloak's standard {@code realm_access.roles} claim
 * and maps them to the expected {@code roles} claim format used by the CUI JWT library.
 * <p>
 * Keycloak by default stores realm roles in a nested structure:
 * <pre>
 * {
 *   "realm_access": {
 *     "roles": ["user", "admin"]
 *   }
 * }
 * </pre>
 * <p>
 * This mapper extracts the roles array and maps it to a standard {@code roles} claim,
 * making it compatible with CUI JWT library's authorization mechanisms.
 * <p>
 * The mapper handles:
 * <ul>
 *   <li>Missing {@code realm_access} object - returns empty list</li>
 *   <li>Missing {@code roles} array within {@code realm_access} - returns empty list</li>
 *   <li>Empty roles array - returns empty list</li>
 *   <li>Non-array roles value - returns empty list</li>
 * </ul>
 *
 * @since 1.0
 * @see <a href="https://www.keycloak.org/docs/latest/server_admin/index.html#_protocol-mappers">Keycloak Protocol Mappers</a>
 */
public class KeycloakDefaultRolesMapper implements ClaimMapper {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public ClaimValue map(@NonNull JsonObject jsonObject, @NonNull String claimName) {
        Optional<JsonValue> realmAccessValue = ClaimMapperUtils.getJsonValue(jsonObject, REALM_ACCESS_CLAIM);
        if (realmAccessValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }

        JsonValue realmAccess = realmAccessValue.get();
        if (realmAccess.getValueType() != JsonValue.ValueType.OBJECT) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }

        JsonObject realmAccessObject = realmAccess.asJsonObject();
        Optional<JsonValue> rolesValue = ClaimMapperUtils.getJsonValue(realmAccessObject, ROLES_CLAIM);
        if (rolesValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }

        JsonValue roles = rolesValue.get();
        if (roles.getValueType() != JsonValue.ValueType.ARRAY) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }

        JsonArray rolesArray = roles.asJsonArray();
        String originalValue = rolesArray.toString();
        List<String> rolesList = ClaimMapperUtils.extractStringsFromJsonArray(rolesArray);

        return ClaimValue.forList(originalValue, Collections.unmodifiableList(rolesList));
    }
}