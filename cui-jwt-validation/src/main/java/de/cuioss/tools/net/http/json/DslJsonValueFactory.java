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
package de.cuioss.tools.net.http.json;

import jakarta.json.JsonValue;

import java.util.List;
import java.util.Map;

/**
 * Factory for creating Jakarta JSON API JsonValue instances from DSL-JSON parsed objects.
 * <p>
 * This factory bridges the gap between DSL-JSON's Map/List/primitive results and 
 * Jakarta JSON API's typed JsonValue hierarchy.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public final class DslJsonValueFactory {

    private DslJsonValueFactory() {
        // Utility class
    }

    /**
     * Creates a Jakarta JSON API JsonValue from a DSL-JSON parsed value.
     *
     * @param dslJsonValue the value from DSL-JSON parsing (Map, List, String, Number, Boolean, or null)
     * @return the corresponding Jakarta JSON API JsonValue
     */
    @SuppressWarnings("unchecked")
    public static JsonValue createJsonValue(Object dslJsonValue) {
        if (dslJsonValue == null) {
            return JsonValue.NULL;
        }

        if (dslJsonValue instanceof Map) {
            return new DslJsonObjectAdapter((Map<String, Object>) dslJsonValue);
        }

        if (dslJsonValue instanceof List) {
            return new DslJsonArrayAdapter((List<Object>) dslJsonValue);
        }

        if (dslJsonValue instanceof String string) {
            return new DslJsonStringAdapter(string);
        }

        if (dslJsonValue instanceof Number number) {
            return new DslJsonNumberAdapter(number);
        }

        if (dslJsonValue instanceof Boolean boolean1) {
            return boolean1 ? JsonValue.TRUE : JsonValue.FALSE;
        }

        // Fallback for unknown types - convert to string
        return new DslJsonStringAdapter(dslJsonValue.toString());
    }
}