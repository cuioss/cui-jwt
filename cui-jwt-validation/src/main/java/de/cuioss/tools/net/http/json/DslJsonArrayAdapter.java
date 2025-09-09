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

import java.util.AbstractList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Jakarta JSON API JsonArray implementation backed by DSL-JSON parsing results.
 * <p>
 * This adapter allows existing code using Jakarta JSON API to work unchanged
 * while getting the security benefits of DSL-JSON parsing underneath.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = false, of = "dslJsonData")
public class DslJsonArrayAdapter extends AbstractList<JsonValue> implements JsonArray {

    private final List<Object> dslJsonData;

    public DslJsonArrayAdapter(List<Object> dslJsonData) {
        this.dslJsonData = dslJsonData != null ? dslJsonData : List.of();
    }

    @Override
    public JsonValue get(int index) {
        Object value = dslJsonData.get(index);
        return DslJsonValueFactory.createJsonValue(value);
    }

    @Override
    public int size() {
        return dslJsonData.size();
    }

    // Jakarta JSON API specific methods

    @Override
    public JsonObject getJsonObject(int index) {
        JsonValue value = get(index);
        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new ClassCastException("JsonValue at index " + index + " is not a JsonObject");
        }
        return (JsonObject) value;
    }

    @Override
    public JsonArray getJsonArray(int index) {
        JsonValue value = get(index);
        if (value.getValueType() != JsonValue.ValueType.ARRAY) {
            throw new ClassCastException("JsonValue at index " + index + " is not a JsonArray");
        }
        return (JsonArray) value;
    }

    @Override
    public JsonNumber getJsonNumber(int index) {
        JsonValue value = get(index);
        if (value.getValueType() != JsonValue.ValueType.NUMBER) {
            throw new ClassCastException("JsonValue at index " + index + " is not a JsonNumber");
        }
        return (JsonNumber) value;
    }

    @Override
    public JsonString getJsonString(int index) {
        JsonValue value = get(index);
        if (value.getValueType() != JsonValue.ValueType.STRING) {
            throw new ClassCastException("JsonValue at index " + index + " is not a JsonString");
        }
        return (JsonString) value;
    }

    @Override
    public String getString(int index) {
        JsonString jsonString = getJsonString(index);
        return jsonString.getString();
    }

    @Override
    public String getString(int index, String defaultValue) {
        try {
            return getString(index);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public int getInt(int index) {
        JsonNumber jsonNumber = getJsonNumber(index);
        return jsonNumber.intValue();
    }

    @Override
    public int getInt(int index, int defaultValue) {
        try {
            return getInt(index);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(int index) {
        JsonValue value = get(index);
        if (value == JsonValue.TRUE) {
            return true;
        }
        if (value == JsonValue.FALSE) {
            return false;
        }
        throw new ClassCastException("JsonValue at index " + index + " is not a boolean");
    }

    @Override
    public boolean getBoolean(int index, boolean defaultValue) {
        try {
            return getBoolean(index);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean isNull(int index) {
        JsonValue value = get(index);
        return value == JsonValue.NULL;
    }

    @Override
    public JsonValue.ValueType getValueType() {
        return JsonValue.ValueType.ARRAY;
    }

    @Override
    public <T extends JsonValue> List<T> getValuesAs(Class<T> clazz) {
        // Convert elements to the requested type
        return dslJsonData.stream()
                .map(obj -> {
                    JsonValue jsonValue = DslJsonValueFactory.createJsonValue(obj);
                    if (clazz.isInstance(jsonValue)) {
                        return clazz.cast(jsonValue);
                    }
                    return null;
                })
                .filter(obj -> obj != null)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        // Return proper JSON array representation instead of object toString
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < dslJsonData.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object value = dslJsonData.get(i);
            if (value instanceof String) {
                // Escape string values with quotes
                sb.append('"').append(value.toString().replace("\"", "\\\"")).append('"');
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append(value.toString());
            }
        }
        sb.append(']');
        return sb.toString();
    }
}