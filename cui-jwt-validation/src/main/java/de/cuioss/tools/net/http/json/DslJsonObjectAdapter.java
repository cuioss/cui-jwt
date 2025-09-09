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

import jakarta.json.*;
import lombok.EqualsAndHashCode;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jakarta JSON API JsonObject implementation backed by DSL-JSON parsing results.
 * <p>
 * This adapter allows existing code using Jakarta JSON API to work unchanged
 * while getting the security benefits of DSL-JSON parsing underneath.
 * <p>
 * The adapter wraps a Map&lt;String, Object&gt; from DSL-JSON and converts
 * values to appropriate Jakarta JSON API types on demand.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = false, of = "dslJsonData")
public class DslJsonObjectAdapter extends AbstractMap<String, JsonValue> implements JsonObject {

    private final Map<String, Object> dslJsonData;

    public DslJsonObjectAdapter(Map<String, Object> dslJsonData) {
        this.dslJsonData = dslJsonData != null ? dslJsonData : Map.of();
    }

    @Override
    public JsonValue get(Object key) {
        if (!(key instanceof String)) {
            return null;
        }
        Object value = dslJsonData.get(key);
        return DslJsonValueFactory.createJsonValue(value);
    }

    @Override
    public boolean containsKey(Object key) {
        return dslJsonData.containsKey(key);
    }

    @Override
    public Set<String> keySet() {
        return dslJsonData.keySet();
    }

    @Override
    public Set<Entry<String, JsonValue>> entrySet() {
        return dslJsonData.entrySet().stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry -> DslJsonValueFactory.createJsonValue(entry.getValue()),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                )).entrySet();
    }

    @Override
    public int size() {
        return dslJsonData.size();
    }

    // Jakarta JSON API specific methods

    @Override
    public JsonArray getJsonArray(String name) {
        JsonValue value = get(name);
        if (value == null || value.getValueType() != ValueType.ARRAY) {
            throw new ClassCastException("JsonValue at '" + name + "' is not a JsonArray");
        }
        return (JsonArray) value;
    }

    @Override
    public JsonObject getJsonObject(String name) {
        JsonValue value = get(name);
        if (value == null || value.getValueType() != ValueType.OBJECT) {
            throw new ClassCastException("JsonValue at '" + name + "' is not a JsonObject");
        }
        return (JsonObject) value;
    }

    @Override
    public JsonNumber getJsonNumber(String name) {
        JsonValue value = get(name);
        if (value == null || value.getValueType() != ValueType.NUMBER) {
            throw new ClassCastException("JsonValue at '" + name + "' is not a JsonNumber");
        }
        return (JsonNumber) value;
    }

    @Override
    public JsonString getJsonString(String name) {
        JsonValue value = get(name);
        if (value == null || value.getValueType() != ValueType.STRING) {
            throw new ClassCastException("JsonValue at '" + name + "' is not a JsonString");
        }
        return (JsonString) value;
    }

    @Override
    public String getString(String name) {
        JsonString jsonString = getJsonString(name);
        return jsonString.getString();
    }

    @Override
    public String getString(String name, String defaultValue) {
        try {
            return getString(name);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public int getInt(String name) {
        JsonNumber jsonNumber = getJsonNumber(name);
        return jsonNumber.intValue();
    }

    @Override
    public int getInt(String name, int defaultValue) {
        try {
            return getInt(name);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String name) {
        JsonValue value = get(name);
        if (value == JsonValue.TRUE) {
            return true;
        }
        if (value == JsonValue.FALSE) {
            return false;
        }
        throw new ClassCastException("JsonValue at '" + name + "' is not a boolean");
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue) {
        try {
            return getBoolean(name);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean isNull(String name) {
        // Only return true if key exists and value is explicitly null
        if (!dslJsonData.containsKey(name)) {
            return false;
        }
        Object value = dslJsonData.get(name);
        return value == null;
    }

    @Override
    public ValueType getValueType() {
        return ValueType.OBJECT;
    }

    @Override
    public String toString() {
        // Return proper JSON object representation instead of object toString
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : dslJsonData.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;

            // Escape key with quotes
            sb.append('"').append(entry.getKey().replace("\"", "\\\"")).append('"');
            sb.append(':');

            // Add value
            Object value = entry.getValue();
            if (value instanceof String) {
                // Escape string values with quotes
                sb.append('"').append(value.toString().replace("\"", "\\\"")).append('"');
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append(value.toString());
            }
        }
        sb.append('}');
        return sb.toString();
    }

}