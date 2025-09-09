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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DslJsonValueFactory}.
 *
 * @author Oliver Wolff
 */
class DslJsonValueFactoryTest {

    @Test
    @DisplayName("Should return JsonValue.NULL for null input")
    void shouldReturnJsonValueNullForNullInput() {
        JsonValue result = DslJsonValueFactory.createJsonValue(null);
        assertEquals(JsonValue.NULL, result);
    }

    @Test
    @DisplayName("Should create JsonObject from Map")
    void shouldCreateJsonObjectFromMap() {
        Map<String, Object> map = Map.of("key", "value", "number", 42);
        JsonValue result = DslJsonValueFactory.createJsonValue(map);

        assertInstanceOf(JsonObject.class, result);
        assertEquals(JsonValue.ValueType.OBJECT, result.getValueType());

        JsonObject jsonObject = (JsonObject) result;
        assertEquals("value", jsonObject.getString("key"));
        assertEquals(42, jsonObject.getInt("number"));
    }

    @Test
    @DisplayName("Should create JsonArray from List")
    void shouldCreateJsonArrayFromList() {
        List<Object> list = List.of("string", 42, true);
        JsonValue result = DslJsonValueFactory.createJsonValue(list);

        assertInstanceOf(JsonArray.class, result);
        assertEquals(JsonValue.ValueType.ARRAY, result.getValueType());

        JsonArray jsonArray = (JsonArray) result;
        assertEquals(3, jsonArray.size());
        assertEquals("string", jsonArray.getString(0));
        assertEquals(42, jsonArray.getInt(1));
        assertTrue(jsonArray.getBoolean(2));
    }

    @Test
    @DisplayName("Should create JsonString from String")
    void shouldCreateJsonStringFromString() {
        String str = "test string";
        JsonValue result = DslJsonValueFactory.createJsonValue(str);

        assertInstanceOf(JsonString.class, result);
        assertEquals(JsonValue.ValueType.STRING, result.getValueType());

        JsonString jsonString = (JsonString) result;
        assertEquals("test string", jsonString.getString());
    }

    @Test
    @DisplayName("Should create JsonNumber from Integer")
    void shouldCreateJsonNumberFromInteger() {
        Integer number = 42;
        JsonValue result = DslJsonValueFactory.createJsonValue(number);

        assertInstanceOf(JsonNumber.class, result);
        assertEquals(JsonValue.ValueType.NUMBER, result.getValueType());

        JsonNumber jsonNumber = (JsonNumber) result;
        assertEquals(42, jsonNumber.intValue());
    }

    @Test
    @DisplayName("Should create JsonNumber from Double")
    void shouldCreateJsonNumberFromDouble() {
        Double number = 3.14;
        JsonValue result = DslJsonValueFactory.createJsonValue(number);

        assertInstanceOf(JsonNumber.class, result);
        assertEquals(JsonValue.ValueType.NUMBER, result.getValueType());

        JsonNumber jsonNumber = (JsonNumber) result;
        assertEquals(3.14, jsonNumber.doubleValue(), 0.001);
    }

    @Test
    @DisplayName("Should create JsonNumber from BigDecimal")
    void shouldCreateJsonNumberFromBigDecimal() {
        BigDecimal number = new BigDecimal("123.456");
        JsonValue result = DslJsonValueFactory.createJsonValue(number);

        assertInstanceOf(JsonNumber.class, result);
        assertEquals(JsonValue.ValueType.NUMBER, result.getValueType());

        JsonNumber jsonNumber = (JsonNumber) result;
        assertEquals(new BigDecimal("123.456"), jsonNumber.bigDecimalValue());
    }

    @Test
    @DisplayName("Should return JsonValue.TRUE for Boolean true")
    void shouldReturnJsonValueTrueForBooleanTrue() {
        Boolean bool = true;
        JsonValue result = DslJsonValueFactory.createJsonValue(bool);

        assertEquals(JsonValue.TRUE, result);
        assertEquals(JsonValue.ValueType.TRUE, result.getValueType());
    }

    @Test
    @DisplayName("Should return JsonValue.FALSE for Boolean false")
    void shouldReturnJsonValueFalseForBooleanFalse() {
        Boolean bool = false;
        JsonValue result = DslJsonValueFactory.createJsonValue(bool);

        assertEquals(JsonValue.FALSE, result);
        assertEquals(JsonValue.ValueType.FALSE, result.getValueType());
    }

    @Test
    @DisplayName("Should handle empty Map")
    void shouldHandleEmptyMap() {
        Map<String, Object> emptyMap = Map.of();
        JsonValue result = DslJsonValueFactory.createJsonValue(emptyMap);

        assertInstanceOf(JsonObject.class, result);
        JsonObject jsonObject = (JsonObject) result;
        assertTrue(jsonObject.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty List")
    void shouldHandleEmptyList() {
        List<Object> emptyList = List.of();
        JsonValue result = DslJsonValueFactory.createJsonValue(emptyList);

        assertInstanceOf(JsonArray.class, result);
        JsonArray jsonArray = (JsonArray) result;
        assertTrue(jsonArray.isEmpty());
    }

    @Test
    @DisplayName("Should handle empty String")
    void shouldHandleEmptyString() {
        String emptyStr = "";
        JsonValue result = DslJsonValueFactory.createJsonValue(emptyStr);

        assertInstanceOf(JsonString.class, result);
        JsonString jsonString = (JsonString) result;
        assertEquals("", jsonString.getString());
    }

    @Test
    @DisplayName("Should handle nested structures")
    void shouldHandleNestedStructures() {
        Map<String, Object> nested = Map.of("inner", "value");
        List<Object> array = List.of(1, 2, 3);
        Map<String, Object> complex = new HashMap<>();
        complex.put("object", nested);
        complex.put("array", array);
        complex.put("string", "text");
        complex.put("number", 42);
        complex.put("boolean", true);
        complex.put("null", null);

        JsonValue result = DslJsonValueFactory.createJsonValue(complex);
        assertInstanceOf(JsonObject.class, result);

        JsonObject jsonObject = (JsonObject) result;
        assertEquals(6, jsonObject.size());

        JsonObject innerObject = jsonObject.getJsonObject("object");
        assertEquals("value", innerObject.getString("inner"));

        JsonArray innerArray = jsonObject.getJsonArray("array");
        assertEquals(3, innerArray.size());
        assertEquals(1, innerArray.getInt(0));

        assertEquals("text", jsonObject.getString("string"));
        assertEquals(42, jsonObject.getInt("number"));
        assertTrue(jsonObject.getBoolean("boolean"));
        assertTrue(jsonObject.isNull("null"));
    }

    @Test
    @DisplayName("Should fallback to string for unknown types")
    void shouldFallbackToStringForUnknownTypes() {
        Object unknownType = new StringBuilder("test");
        JsonValue result = DslJsonValueFactory.createJsonValue(unknownType);

        assertInstanceOf(JsonString.class, result);
        JsonString jsonString = (JsonString) result;
        assertEquals("test", jsonString.getString());
    }

    @Test
    @DisplayName("Should handle various numeric types")
    void shouldHandleVariousNumericTypes() {
        // Test different numeric types
        JsonValue intResult = DslJsonValueFactory.createJsonValue(42);
        JsonValue longResult = DslJsonValueFactory.createJsonValue(42L);
        JsonValue floatResult = DslJsonValueFactory.createJsonValue(3.14f);
        JsonValue doubleResult = DslJsonValueFactory.createJsonValue(3.14);

        assertInstanceOf(JsonNumber.class, intResult);
        assertInstanceOf(JsonNumber.class, longResult);
        assertInstanceOf(JsonNumber.class, floatResult);
        assertInstanceOf(JsonNumber.class, doubleResult);

        assertEquals(42, ((JsonNumber) intResult).intValue());
        assertEquals(42L, ((JsonNumber) longResult).longValue());
        assertEquals(3.14f, ((JsonNumber) floatResult).doubleValue(), 0.001);
        assertEquals(3.14, ((JsonNumber) doubleResult).doubleValue(), 0.001);
    }
}