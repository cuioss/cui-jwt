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
import de.cuioss.jwt.validation.json.MapRepresentation;
import de.cuioss.tools.string.Splitter;
import lombok.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A {@link ClaimMapper} implementation for mapping scope claims.
 * This class is responsible for converting a scope claim from a JSON object
 * into a {@link ClaimValue} containing a list of strings.
 * It handles both space-separated string scopes and JSON arrays of scopes.
 * <em>Note:</em> Although technically the result is a list, it is treated as a SortedSet
 *
 * @since 1.0
 */
public class ScopeMapper implements ClaimMapper {
    @Override
    public ClaimValue map(@NonNull MapRepresentation mapRepresentation, @NonNull String claimName) {
        Optional<Object> optionalValue = mapRepresentation.getValue(claimName);
        if (optionalValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        }
        Object value = optionalValue.get();

        String originalValue;
        List<String> scopes;

        // According to OAuth 2.0 specification (RFC 6749), the scope parameter is a space-delimited string.
        // However, some implementations use arrays for scopes, so we handle both formats.
        if (value instanceof String stringValue) {
            // Handle space-separated string of scopes (standard format per RFC 6749)
            originalValue = stringValue;
            scopes = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(originalValue);
        } else if (value instanceof List<?> listValue) {
            // Handle List of scopes (non-standard but common)
            // Convert to JSON format to maintain compatibility with previous behavior
            originalValue = listValue.stream()
                    .map(item -> {
                        if (item instanceof String) {
                            return "\"" + item + "\"";
                        } else if (item instanceof Boolean || item instanceof Number) {
                            return item.toString();
                        } else {
                            return "\"" + item.toString() + "\"";
                        }
                    })
                    .collect(Collectors.joining(",", "[", "]"));
            scopes = listValue.stream()
                    .map(Object::toString)
                    .toList();
        } else {
            // Reject other types as non-compliant with OAuth 2.0 specification
            throw new IllegalArgumentException("Unsupported value type for scope: " +
                    value.getClass().getSimpleName() + ". According to OAuth 2.0 specification, scope should be a space-delimited string.");
        }

        return ClaimValue.forList(originalValue, new TreeSet<>(scopes).stream().toList());
    }
}
