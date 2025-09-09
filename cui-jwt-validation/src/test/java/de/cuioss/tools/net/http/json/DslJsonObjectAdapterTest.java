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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DslJsonObjectAdapter}.
 *
 * @author Oliver Wolff
 */
class DslJsonObjectAdapterTest {

    @Test
    @DisplayName("Should create adapter with valid map")
    void shouldCreateAdapterWithValidMap() {
        Map<String, Object> data = Map.of("key1", "value1", "key2", 42);
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertNotNull(adapter);
        assertEquals(2, adapter.size());
        assertTrue(adapter.containsKey("key1"));
        assertTrue(adapter.containsKey("key2"));
    }

    @Test
    @DisplayName("Should handle null map input")
    void shouldHandleNullMapInput() {
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(null);

        assertNotNull(adapter);
        assertEquals(0, adapter.size());
        assertTrue(adapter.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty map")
    void shouldHandleEmptyMap() {
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(Map.of());

        assertNotNull(adapter);
        assertEquals(0, adapter.size());
        assertTrue(adapter.isEmpty());
    }

    @Test
    @DisplayName("Should return correct string values")
    void shouldReturnCorrectStringValues() {
        Map<String, Object> data = Map.of("name", "test", "count", 10);
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertEquals("test", adapter.getString("name"));
        assertEquals("default", adapter.getString("missing", "default"));
    }

    @Test
    @DisplayName("Should handle ClassCastException gracefully for getString with default")
    void shouldHandleClassCastExceptionGracefullyForGetStringWithDefault() {
        Map<String, Object> data = Map.of("number", 42);
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertEquals("default", adapter.getString("number", "default"));
    }

    @Test
    @DisplayName("Should return correct integer values")
    void shouldReturnCorrectIntegerValues() {
        Map<String, Object> data = Map.of("count", 42);
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertEquals(42, adapter.getInt("count"));
        assertEquals(100, adapter.getInt("missing", 100));
    }

    @Test
    @DisplayName("Should handle ClassCastException gracefully for getInt with default")
    void shouldHandleClassCastExceptionGracefullyForGetIntWithDefault() {
        Map<String, Object> data = Map.of("text", "not a number");
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertEquals(100, adapter.getInt("text", 100));
    }

    @Test
    @DisplayName("Should return correct boolean values")
    void shouldReturnCorrectBooleanValues() {
        Map<String, Object> data = Map.of("flag", true);
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertTrue(adapter.getBoolean("flag"));
        assertFalse(adapter.getBoolean("missing", false));
    }

    @Test
    @DisplayName("Should handle ClassCastException gracefully for getBoolean with default")
    void shouldHandleClassCastExceptionGracefullyForGetBooleanWithDefault() {
        Map<String, Object> data = Map.of("text", "not a boolean");
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertTrue(adapter.getBoolean("text", true));
    }

    @Test
    @DisplayName("Should check null values correctly")
    void shouldCheckNullValuesCorrectly() {
        Map<String, Object> data = new HashMap<>();
        data.put("nullValue", null);
        data.put("nonNull", "value");
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertTrue(adapter.isNull("nullValue"));
        assertFalse(adapter.isNull("nonNull"));
        assertFalse(adapter.isNull("missing"));
    }

    @Test
    @DisplayName("Should return correct value type")
    void shouldReturnCorrectValueType() {
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(Map.of());
        assertEquals(JsonValue.ValueType.OBJECT, adapter.getValueType());
    }

    @Test
    @DisplayName("Should support equality based on data")
    void shouldSupportEqualityBasedOnData() {
        Map<String, Object> data1 = Map.of("key", "value");
        Map<String, Object> data2 = Map.of("key", "value");
        Map<String, Object> data3 = Map.of("key", "different");

        DslJsonObjectAdapter adapter1 = new DslJsonObjectAdapter(data1);
        DslJsonObjectAdapter adapter2 = new DslJsonObjectAdapter(data2);
        DslJsonObjectAdapter adapter3 = new DslJsonObjectAdapter(data3);

        assertEquals(adapter1, adapter2);
        assertNotEquals(adapter1, adapter3);
        assertEquals(adapter1.hashCode(), adapter2.hashCode());
    }

    @Test
    @DisplayName("Should provide meaningful toString representation")
    void shouldProvideMeaningfulToStringRepresentation() {
        Map<String, Object> data = Map.of("key", "value");
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        String toString = adapter.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("key"));
        assertTrue(toString.contains("value"));
    }

    @Test
    @DisplayName("Should handle complex nested structures")
    void shouldHandleComplexNestedStructures() {
        Map<String, Object> nested = Map.of("inner", "value");
        Map<String, Object> data = Map.of("outer", nested, "array", List.of(1, 2, 3));
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        assertEquals(2, adapter.size());
        assertTrue(adapter.containsKey("outer"));
        assertTrue(adapter.containsKey("array"));

        JsonValue outerValue = adapter.get("outer");
        assertNotNull(outerValue);
        assertEquals(JsonValue.ValueType.OBJECT, outerValue.getValueType());

        JsonValue arrayValue = adapter.get("array");
        assertNotNull(arrayValue);
        assertEquals(JsonValue.ValueType.ARRAY, arrayValue.getValueType());
    }

    @Test
    @DisplayName("Should return keySet properly")
    void shouldReturnKeySetProperly() {
        Map<String, Object> data = Map.of("key1", "value1", "key2", "value2");
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        var keySet = adapter.keySet();
        assertEquals(2, keySet.size());
        assertTrue(keySet.contains("key1"));
        assertTrue(keySet.contains("key2"));
    }

    @Test
    @DisplayName("Should handle non-string keys gracefully")
    void shouldHandleNonStringKeysGracefully() {
        Map<String, Object> data = Map.of("validKey", "value");
        DslJsonObjectAdapter adapter = new DslJsonObjectAdapter(data);

        // get() with non-string key should return null
        assertNull(adapter.get(123));
        assertNull(adapter.get(null));
    }
}