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
package de.cuioss.benchmarking.common.util;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Common JSON serialization utilities for benchmark results.
 * Provides consistent formatting and serialization across all benchmark modules.
 *
 * @since 1.0
 */
public final class JsonSerializationHelper {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Gson instance configured for benchmark result serialization.
     * Features:
     * - Pretty printing
     * - Smart number formatting (integers without .0)
     * - ISO instant formatting
     */
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Double.class, new DoubleSerializer())
            .registerTypeAdapter(Instant.class, new InstantSerializer())
            .create();

    private JsonSerializationHelper() {
        // Utility class
    }

    /**
     * Writes an object to a JSON file.
     *
     * @param path the file path to write to
     * @param object the object to serialize
     * @throws IOException if an I/O error occurs
     */
    public static void writeJsonFile(Path path, Object object) throws IOException {
        String json = GSON.toJson(object);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json);
    }

    /**
     * Reads a JSON file into an object.
     *
     * @param <T> the type to deserialize to
     * @param path the file path to read from
     * @param type the class of the type to deserialize
     * @return the deserialized object
     * @throws IOException if an I/O error occurs
     */
    public static <T> T readJsonFile(Path path, Class<T> type) throws IOException {
        String json = Files.readString(path);
        return GSON.fromJson(json, type);
    }

    /**
     * Formats a double value for display.
     * Returns integer representation if the value is a whole number.
     *
     * @param value the value to format
     * @return formatted string
     */
    public static String formatDouble(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    /**
     * Custom serializer for Double values.
     * Serializes whole numbers without decimal point.
     */
    private static class DoubleSerializer implements JsonSerializer<Double> {
        @Override public JsonElement serialize(Double src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }
            if (src.isNaN() || src.isInfinite()) {
                return new JsonPrimitive(src.toString());
            }
            if (src == src.longValue()) {
                return new JsonPrimitive(src.longValue());
            }
            return new JsonPrimitive(src);
        }
    }

    /**
     * Custom serializer for Instant values.
     * Serializes to ISO-8601 format.
     */
    private static class InstantSerializer implements JsonSerializer<Instant> {
        @Override public JsonElement serialize(Instant src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }
            return new JsonPrimitive(ISO_FORMATTER.format(src));
        }
    }

}