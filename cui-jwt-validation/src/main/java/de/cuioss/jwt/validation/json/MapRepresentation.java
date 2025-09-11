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
package de.cuioss.jwt.validation.json;

import com.dslplatform.json.DslJson;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A type-safe wrapper for Map<String, Object> that provides convenient access methods
 * for JWT claim processing.
 * <p>
 * This class encapsulates access to the underlying Map<String, Object> representation
 * of JWT claims, providing type-safe methods for extracting common claim types such as
 * strings, numbers, lists, and nested objects.
 * <p>
 * The class follows the same access patterns as the previous JsonObject-based approach
 * but works with the DSL-JSON deserialized Map<String, Object> structure.
 * <p>
 * Key features:
 * <ul>
 *   <li>Type-safe access to string, number, boolean, and list claims</li>
 *   <li>Support for nested object access</li>
 *   <li>Null-safe operations with Optional returns</li>
 *   <li>Direct compatibility with ClaimMapper implementations</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class MapRepresentation {

    private final Map<String, Object> data;

    /**
     * Creates a new MapRepresentation wrapping the given immutable map.
     * Package-private constructor - use factory methods for creation.
     *
     * @param data the underlying immutable map data, must not be null
     */
    MapRepresentation(@NonNull Map<String, Object> data) {
        this.data = data; // Assume data is already immutable
    }

    /**
     * Factory method to create MapRepresentation from JSON string using DSL-JSON.
     * <p>
     * This method encapsulates the deserialization logic within MapRepresentation,
     * allowing for future customization of parsing behavior and reducing coupling
     * with external deserialization code.
     *
     * @param dslJson the DSL-JSON instance configured for deserialization
     * @param jsonContent the JSON string content to parse
     * @return a new MapRepresentation containing the parsed data
     * @throws IOException if the JSON content cannot be parsed
     */
    @NonNull
    public static MapRepresentation fromJson(@NonNull DslJson<Object> dslJson, @NonNull String jsonContent) throws IOException {
        byte[] jsonBytes = jsonContent.getBytes();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedData = dslJson.deserialize(Map.class, jsonBytes, jsonBytes.length);

        if (parsedData == null) {
            // Return empty MapRepresentation for null/empty JSON
            return new MapRepresentation(Map.of());
        }

        // Create immutable copy to ensure immutability
        return new MapRepresentation(Map.copyOf(parsedData));
    }

    /**
     * Factory method to create MapRepresentation from JSON byte array using DSL-JSON.
     * <p>
     * This overload provides direct byte array processing without string conversion,
     * which can be more efficient when working with pre-encoded JSON data.
     *
     * @param dslJson the DSL-JSON instance configured for deserialization
     * @param jsonBytes the JSON byte array to parse
     * @return a new MapRepresentation containing the parsed data
     * @throws IOException if the JSON content cannot be parsed
     */
    @NonNull
    public static MapRepresentation fromJson(@NonNull DslJson<Object> dslJson, byte @NonNull [] jsonBytes) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedData = dslJson.deserialize(Map.class, jsonBytes, jsonBytes.length);

        if (parsedData == null) {
            // Return empty MapRepresentation for null/empty JSON
            return new MapRepresentation(Map.of());
        }

        // Create immutable copy to ensure immutability
        return new MapRepresentation(Map.copyOf(parsedData));
    }

    /**
     * Factory method to create an empty MapRepresentation.
     *
     * @return an empty MapRepresentation
     */
    @NonNull
    public static MapRepresentation empty() {
        return new MapRepresentation(Map.of());
    }


    /**
     * Checks if the map contains a key.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean containsKey(@NonNull String key) {
        return data.containsKey(key);
    }

    /**
     * Gets a value as an Object.
     *
     * @param key the key to look up
     * @return Optional containing the value, or empty if not found
     */
    public Optional<Object> getValue(@NonNull String key) {
        return Optional.ofNullable(data.get(key));
    }

    /**
     * Gets a value as a String.
     *
     * @param key the key to look up
     * @return Optional containing the string value, or empty if not found or not a string
     */
    public Optional<String> getString(@NonNull String key) {
        Object value = data.get(key);
        if (value instanceof String) {
            return Optional.of((String) value);
        }
        return Optional.empty();
    }

    /**
     * Gets a value as a Number.
     *
     * @param key the key to look up
     * @return Optional containing the number value, or empty if not found or not a number
     */
    public Optional<Number> getNumber(@NonNull String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return Optional.of((Number) value);
        }
        return Optional.empty();
    }

    /**
     * Gets a value as a Boolean.
     *
     * @param key the key to look up
     * @return Optional containing the boolean value, or empty if not found or not a boolean
     */
    public Optional<Boolean> getBoolean(@NonNull String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return Optional.of((Boolean) value);
        }
        return Optional.empty();
    }

    /**
     * Gets a value as a List.
     *
     * @param key the key to look up
     * @return Optional containing the list value, or empty if not found or not a list
     */
    @SuppressWarnings("unchecked")
    public Optional<List<Object>> getList(@NonNull String key) {
        Object value = data.get(key);
        if (value instanceof List) {
            return Optional.of((List<Object>) value);
        }
        return Optional.empty();
    }

    /**
     * Gets a value as a nested Map (for nested objects).
     *
     * @param key the key to look up
     * @return Optional containing the nested map, or empty if not found or not a map
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getMap(@NonNull String key) {
        Object value = data.get(key);
        if (value instanceof Map) {
            return Optional.of((Map<String, Object>) value);
        }
        return Optional.empty();
    }

    /**
     * Gets a nested MapRepresentation for accessing nested objects.
     *
     * @param key the key to look up
     * @return Optional containing the nested MapRepresentation, or empty if not found or not a map
     */
    public Optional<MapRepresentation> getNestedMap(@NonNull String key) {
        return getMap(key).map(MapRepresentation::new);
    }

    /**
     * Gets a string list from an array claim.
     *
     * @param key the key to look up
     * @return Optional containing the string list, or empty if not found or not convertible
     */
    public Optional<List<String>> getStringList(@NonNull String key) {
        return getList(key).map(list ->
            list.stream()
                .filter(item -> item instanceof String)
                .map(String.class::cast)
                .toList()
        );
    }

    /**
     * Checks if the map is empty.
     *
     * @return true if the map is empty, false otherwise
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * Gets the size of the map.
     *
     * @return the number of key-value pairs
     */
    public int size() {
        return data.size();
    }

    /**
     * Returns the key set of the underlying map.
     *
     * @return the set of keys in the map
     */
    @NonNull
    public java.util.Set<String> keySet() {
        return data.keySet();
    }

    /**
     * Gets the underlying map data (for advanced use cases).
     *
     * @return the underlying map, never null
     */
    public Map<String, Object> getUnderlyingMap() {
        return data;
    }

    @Override
    public String toString() {
        return "MapRepresentation{size=" + data.size() + "}";
    }
}