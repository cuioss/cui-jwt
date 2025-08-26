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
package de.cuioss.benchmarking.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Test helper for benchmark result validation.
 */
public class BenchmarkTestHelper {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Loads raw JMH JSON results from a resource file.
     */
    public static List<Map<String, Object>> loadJsonResults(String resourcePath) throws IOException {
        try (InputStream is = BenchmarkTestHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            return GSON.fromJson(new InputStreamReader(is), List.class);
        }
    }

    /**
     * Writes JMH JSON results to a file for processing.
     */
    public static void writeJsonResultsToFile(List<Map<String, Object>> results, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, GSON.toJson(results));
    }

    /**
     * Copies test resource to target directory.
     */
    public static void copyResourceToPath(String resourcePath, Path targetPath) throws IOException {
        try (InputStream is = BenchmarkTestHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath);
        }
    }
}