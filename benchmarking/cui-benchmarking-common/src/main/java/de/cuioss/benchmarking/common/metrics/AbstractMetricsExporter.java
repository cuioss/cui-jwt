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
package de.cuioss.benchmarking.common.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import de.cuioss.tools.logging.CuiLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Abstract base class for metrics exporters providing common JSON serialization
 * and file handling functionality.
 * 
 * @since 1.0
 */
public abstract class AbstractMetricsExporter implements MetricsExporter {

    protected static final CuiLogger LOGGER = new CuiLogger(AbstractMetricsExporter.class);

    protected static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(Double.class, (JsonSerializer<Double>)(src, typeOfSrc, context) -> {
                if (src == src.longValue()) {
                    return new JsonPrimitive(src.longValue());
                }
                return new JsonPrimitive(src);
            })
            .create();

    protected final String outputDirectory;

    /**
     * Constructor with output directory.
     * 
     * @param outputDirectory The directory where metrics files will be written
     */
    protected AbstractMetricsExporter(String outputDirectory) {
        this.outputDirectory = outputDirectory;
        File dir = new File(outputDirectory);
        dir.mkdirs();
        LOGGER.debug("{} initialized with output directory: {} (exists: {})",
                getClass().getSimpleName(), dir.getAbsolutePath(), dir.exists());
    }

    @Override public ExportFormat getExportFormat() {
        return ExportFormat.JSON;
    }

    @Override public void exportToFile(String filepath, Map<String, Object> metricsData) throws IOException {
        File outputFile = new File(filepath);
        outputFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(metricsData, writer);
            writer.flush();
            LOGGER.debug("Exported metrics to: {}", outputFile.getAbsolutePath());
        }
    }

    /**
     * Read existing metrics from a JSON file.
     * 
     * @param filepath The path to the JSON file
     * @return The parsed metrics map, or empty map if file doesn't exist or is empty
     */
    protected Map<String, Object> readExistingMetrics(String filepath) {
        Map<String, Object> existingMetrics = new LinkedHashMap<>();
        File file = new File(filepath);

        if (file.exists()) {
            try {
                String content = Files.readString(file.toPath());
                if (!content.trim().isEmpty()) {
                    Type mapType = new TypeToken<Map<String, Object>>(){
                    }.getType();
                    Map<String, Object> parsed = GSON.fromJson(content, mapType);
                    if (parsed != null) {
                        existingMetrics = parsed;
                    }
                }
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("Failed to read existing metrics file {}: {}", filepath, e.getMessage());
                // Try to delete corrupted file
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException deleteException) {
                    LOGGER.warn("Failed to delete corrupted metrics file", deleteException);
                }
            }
        }

        return existingMetrics;
    }

    /**
     * Update an aggregated metrics file with new benchmark data.
     * 
     * @param filepath The path to the aggregated metrics file
     * @param benchmarkName The name of the benchmark
     * @param benchmarkData The benchmark data to add/update
     * @throws IOException if writing fails
     */
    protected void updateAggregatedMetrics(String filepath, String benchmarkName, Map<String, Object> benchmarkData) throws IOException {
        Map<String, Object> allMetrics = readExistingMetrics(filepath);
        allMetrics.put(benchmarkName, benchmarkData);
        exportToFile(filepath, allMetrics);
        LOGGER.debug("Updated {} with {} benchmarks", filepath, allMetrics.size());
    }

    /**
     * Format number according to rules: 1 decimal for <10, no decimal for >=10.
     * 
     * @param value The value to format
     * @return Formatted number (Double for <10, Long for >=10)
     */
    protected Object formatNumber(double value) {
        if (value < 10) {
            DecimalFormat df = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
            return Double.parseDouble(df.format(value));
        } else {
            return Math.round(value);
        }
    }

    /**
     * Extract simple benchmark name from full class/method name.
     * 
     * @param fullBenchmarkName The full benchmark name (e.g., com.example.MyBenchmark.testMethod)
     * @return The simplified name (e.g., testMethod)
     */
    protected String extractSimpleBenchmarkName(String fullBenchmarkName) {
        if (fullBenchmarkName.contains(".")) {
            return fullBenchmarkName.substring(fullBenchmarkName.lastIndexOf('.') + 1);
        }
        return fullBenchmarkName;
    }

    /**
     * Ensure output directory exists.
     */
    protected void ensureOutputDirectoryExists() {
        Path outputPath = Path.of(outputDirectory);
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            LOGGER.warn("Failed to create output directory: {}", outputDirectory, e);
        }
    }
}