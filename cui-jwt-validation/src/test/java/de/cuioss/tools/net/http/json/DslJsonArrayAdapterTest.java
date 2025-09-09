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

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DslJsonArrayAdapter}.
 *
 * @author Oliver Wolff
 */
class DslJsonArrayAdapterTest {

    @Test
    @DisplayName("Should create adapter with valid list")
    void shouldCreateAdapterWithValidList() {
        List<Object> data = List.of("value1", 42, true);
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertNotNull(adapter);
        assertEquals(3, adapter.size());
    }

    @Test
    @DisplayName("Should handle null list input")
    void shouldHandleNullListInput() {
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(null);

        assertNotNull(adapter);
        assertEquals(0, adapter.size());
        assertTrue(adapter.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty list")
    void shouldHandleEmptyList() {
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(List.of());

        assertNotNull(adapter);
        assertEquals(0, adapter.size());
        assertTrue(adapter.isEmpty());
    }

    @Test
    @DisplayName("Should return correct values by index")
    void shouldReturnCorrectValuesByIndex() {
        List<Object> data = List.of("text", 42, true);
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        JsonValue value0 = adapter.getFirst();
        assertEquals(JsonValue.ValueType.STRING, value0.getValueType());

        JsonValue value1 = adapter.get(1);
        assertEquals(JsonValue.ValueType.NUMBER, value1.getValueType());

        JsonValue value2 = adapter.get(2);
        assertTrue(value2 == JsonValue.TRUE);
    }

    @Test
    @DisplayName("Should return correct string values")
    void shouldReturnCorrectStringValues() {
        List<Object> data = List.of("test", 10);
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertEquals("test", adapter.getString(0));
        assertEquals("default", adapter.getString(1, "default"));
    }

    @Test
    @DisplayName("Should handle ClassCastException gracefully for getString with default")
    void shouldHandleClassCastExceptionGracefullyForGetStringWithDefault() {
        List<Object> data = List.of(42); // number, not string
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertEquals("default", adapter.getString(0, "default"));
    }

    @Test
    @DisplayName("Should return correct integer values")
    void shouldReturnCorrectIntegerValues() {
        List<Object> data = List.of(42, "text");
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertEquals(42, adapter.getInt(0));
        assertEquals(100, adapter.getInt(1, 100));
    }

    @Test
    @DisplayName("Should handle ClassCastException gracefully for getInt with default")
    void shouldHandleClassCastExceptionGracefullyForGetIntWithDefault() {
        List<Object> data = List.of("not a number");
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertEquals(100, adapter.getInt(0, 100));
    }

    @Test
    @DisplayName("Should return correct boolean values")
    void shouldReturnCorrectBooleanValues() {
        List<Object> data = List.of(true, "text");
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertTrue(adapter.getBoolean(0));
        assertFalse(adapter.getBoolean(1, false));
    }

    @Test
    @DisplayName("Should handle ClassCastException gracefully for getBoolean with default")
    void shouldHandleClassCastExceptionGracefullyForGetBooleanWithDefault() {
        List<Object> data = List.of("not a boolean");
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertTrue(adapter.getBoolean(0, true));
    }

    @Test
    @DisplayName("Should check null values correctly")
    void shouldCheckNullValuesCorrectly() {
        List<Object> data = new ArrayList<>();
        data.add(null);
        data.add("value");
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertTrue(adapter.isNull(0));
        assertFalse(adapter.isNull(1));
    }

    @Test
    @DisplayName("Should return correct value type")
    void shouldReturnCorrectValueType() {
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(List.of());
        assertEquals(JsonValue.ValueType.ARRAY, adapter.getValueType());
    }

    @Test
    @DisplayName("Should support getValuesAs for JsonValue subtypes")
    void shouldSupportGetValuesAsForJsonValueSubtypes() {
        List<Object> data = List.of("string1", "string2", "string3");
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        List<JsonString> strings = adapter.getValuesAs(JsonString.class);
        assertEquals(3, strings.size());
        assertEquals("string1", strings.getFirst().getString());
        assertEquals("string2", strings.get(1).getString());
        assertEquals("string3", strings.get(2).getString());
    }

    @Test
    @DisplayName("Should filter out non-matching types in getValuesAs")
    void shouldFilterOutNonMatchingTypesInGetValuesAs() {
        List<Object> data = List.of("string", 42, "another string", true);
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        List<JsonString> strings = adapter.getValuesAs(JsonString.class);
        assertEquals(2, strings.size());
        assertEquals("string", strings.getFirst().getString());
        assertEquals("another string", strings.get(1).getString());
    }

    @Test
    @DisplayName("Should support equality based on data")
    void shouldSupportEqualityBasedOnData() {
        List<Object> data1 = List.of("value", 42);
        List<Object> data2 = List.of("value", 42);
        List<Object> data3 = List.of("different", 42);

        DslJsonArrayAdapter adapter1 = new DslJsonArrayAdapter(data1);
        DslJsonArrayAdapter adapter2 = new DslJsonArrayAdapter(data2);
        DslJsonArrayAdapter adapter3 = new DslJsonArrayAdapter(data3);

        assertEquals(adapter1, adapter2);
        assertNotEquals(adapter1, adapter3);
        assertEquals(adapter1.hashCode(), adapter2.hashCode());
    }

    @Test
    @DisplayName("Should provide meaningful toString representation")
    void shouldProvideMeaningfulToStringRepresentation() {
        List<Object> data = List.of("value", 42);
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        String toString = adapter.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("value"));
        assertTrue(toString.contains("42"));
    }

    @Test
    @DisplayName("Should handle nested objects and arrays")
    void shouldHandleNestedObjectsAndArrays() {
        List<Object> innerArray = List.of("inner1", "inner2");
        Map<String, Object> innerObject = Map.of("key", "value");
        List<Object> data = List.of(innerArray, innerObject);

        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertEquals(2, adapter.size());

        JsonValue arrayValue = adapter.getFirst();
        assertEquals(JsonValue.ValueType.ARRAY, arrayValue.getValueType());

        JsonValue objectValue = adapter.get(1);
        assertEquals(JsonValue.ValueType.OBJECT, objectValue.getValueType());
    }

    @Test
    @DisplayName("Should handle IndexOutOfBoundsException for invalid indices")
    void shouldHandleIndexOutOfBoundsExceptionForInvalidIndices() {
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(List.of("single"));

        assertThrows(IndexOutOfBoundsException.class, () -> adapter.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> adapter.get(-1));
    }

    @Test
    @DisplayName("Should return getJsonObject correctly")
    void shouldReturnGetJsonObjectCorrectly() {
        Map<String, Object> objectData = Map.of("key", "value");
        List<Object> data = List.of(objectData);
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        JsonObject jsonObject = adapter.getJsonObject(0);
        assertNotNull(jsonObject);
        assertEquals("value", jsonObject.getString("key"));
    }

    @Test
    @DisplayName("Should throw ClassCastException for wrong type in getJsonObject")
    void shouldThrowClassCastExceptionForWrongTypeInGetJsonObject() {
        List<Object> data = List.of("not an object");
        DslJsonArrayAdapter adapter = new DslJsonArrayAdapter(data);

        assertThrows(ClassCastException.class, () -> adapter.getJsonObject(0));
    }
}