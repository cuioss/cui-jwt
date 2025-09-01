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
package de.cuioss.benchmarking.common.metrics.pipeline.processors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsContext;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessingException;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessor;
import de.cuioss.tools.logging.CuiLogger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Processor that formats metrics data for output in various formats
 * such as JSON, CSV, or custom formats.
 *
 * @since 1.0
 */
public class FormatProcessor implements MetricsProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(FormatProcessor.class);

    /**
     * Output format type
     */
    public enum Format {
        JSON,
        PRETTY_JSON,
        CSV,
        PROPERTIES,
        CUSTOM
    }

    /**
     * The format to use for output
     */
    private final Format format;

    /**
     * Custom formatter function (used when format is CUSTOM)
     */
    private final FormatterFunction customFormatter;

    /**
     * Whether to include metadata in the output
     */
    private final boolean includeMetadata;

    /**
     * Gson instance for JSON formatting
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
            .create();

    /**
     * Gson instance for pretty JSON formatting
     */
    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
            .create();

    /**
     * Default constructor for JSON format
     */
    public FormatProcessor() {
        this(Format.PRETTY_JSON, true);
    }

    /**
     * Constructor with format specification
     */
    public FormatProcessor(Format format) {
        this(format, true);
    }

    /**
     * Constructor with format and metadata options
     */
    public FormatProcessor(Format format, boolean includeMetadata) {
        this(format, includeMetadata, null);
    }

    /**
     * Full constructor with custom formatter
     */
    public FormatProcessor(Format format, boolean includeMetadata, FormatterFunction customFormatter) {
        this.format = format;
        this.includeMetadata = includeMetadata;
        this.customFormatter = customFormatter;
    }

    @Override public MetricsContext process(MetricsContext context) throws MetricsProcessingException {
        LOGGER.debug("Formatting metrics from source: {} as {}", context.getSource(), format);

        try {
            String formattedOutput = switch (format) {
                case JSON -> formatAsJson(context, false);
                case PRETTY_JSON -> formatAsJson(context, true);
                case CSV -> formatAsCsv(context);
                case PROPERTIES -> formatAsProperties(context);
                case CUSTOM -> formatCustom(context);
            };

            // Store the formatted output in context
            context.addMetadata("formatted_output", formattedOutput);
            context.addMetadata("format_type", format.toString());

            LOGGER.debug("Formatted metrics as {} (length: {} chars)", format, formattedOutput.length());
            return context;

        } catch (Exception e) {
            throw new MetricsProcessingException(getName(),
                    "Failed to format metrics as " + format, e);
        }
    }

    /**
     * Format metrics as JSON
     */
    private String formatAsJson(MetricsContext context, boolean pretty) {
        Map<String, Object> output = new LinkedHashMap<>();

        // Add timestamp and source
        output.put("timestamp", context.getTimestamp());
        output.put("source", context.getSource());

        // Add metrics
        output.put("metrics", context.getMetrics());

        // Add metadata if enabled
        if (includeMetadata) {
            output.put("metadata", context.getMetadata());
        }

        return pretty ? PRETTY_GSON.toJson(output) : GSON.toJson(output);
    }

    /**
     * Format metrics as CSV
     */
    private String formatAsCsv(MetricsContext context) {
        StringBuilder csv = new StringBuilder();

        // Header row
        csv.append("key,value,type\n");

        // Add metrics
        for (Map.Entry<String, Object> entry : context.getMetrics().entrySet()) {
            csv.append(escapeCSV(entry.getKey())).append(",");
            csv.append(escapeCSV(String.valueOf(entry.getValue()))).append(",");
            csv.append(entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
            csv.append("\n");
        }

        // Add metadata if enabled
        if (includeMetadata) {
            for (Map.Entry<String, Object> entry : context.getMetadata().entrySet()) {
                csv.append("metadata.").append(escapeCSV(entry.getKey())).append(",");
                csv.append(escapeCSV(String.valueOf(entry.getValue()))).append(",");
                csv.append("metadata");
                csv.append("\n");
            }
        }

        return csv.toString();
    }

    /**
     * Format metrics as properties
     */
    private String formatAsProperties(MetricsContext context) {
        StringBuilder props = new StringBuilder();

        // Add header comment
        props.append("# Metrics from ").append(context.getSource());
        props.append(" at ").append(context.getTimestamp()).append("\n");

        // Add metrics
        for (Map.Entry<String, Object> entry : context.getMetrics().entrySet()) {
            props.append("metric.").append(escapeProperty(entry.getKey()));
            props.append("=").append(escapeProperty(String.valueOf(entry.getValue())));
            props.append("\n");
        }

        // Add metadata if enabled
        if (includeMetadata) {
            props.append("\n# Metadata\n");
            for (Map.Entry<String, Object> entry : context.getMetadata().entrySet()) {
                props.append("metadata.").append(escapeProperty(entry.getKey()));
                props.append("=").append(escapeProperty(String.valueOf(entry.getValue())));
                props.append("\n");
            }
        }

        return props.toString();
    }

    /**
     * Format using custom formatter
     */
    private String formatCustom(MetricsContext context) {
        if (customFormatter == null) {
            throw new IllegalStateException("Custom formatter not provided for CUSTOM format");
        }
        return customFormatter.format(context);
    }

    /**
     * Escape CSV value
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Escape property value
     */
    private String escapeProperty(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("=", "\\=")
                .replace(" ", "\\ ");
    }

    @Override public String getName() {
        return "FormatProcessor";
    }

    /**
     * Interface for custom formatter functions
     */
    @FunctionalInterface public interface FormatterFunction {
        /**
         * Format the metrics context to a string
         *
         * @param context The metrics context
         * @return The formatted string
         */
        String format(MetricsContext context);
    }

    /**
     * Common formatter functions
     */
    public static class Formatters {

        /**
         * Create a simple key-value formatter
         */
        public static FormatterFunction keyValue(String delimiter) {
            return context -> {
                StringBuilder sb = new StringBuilder();
                context.getMetrics().forEach((key, value) ->
                        sb.append(key).append(delimiter).append(value).append("\n"));
                return sb.toString();
            };
        }

        /**
         * Create a formatter that only includes specific keys
         */
        public static FormatterFunction selective(String... keys) {
            return context -> {
                Map<String, Object> selected = new LinkedHashMap<>();
                for (String key : keys) {
                    if (context.getMetrics().containsKey(key)) {
                        selected.put(key, context.getMetrics().get(key));
                    }
                }
                return PRETTY_GSON.toJson(selected);
            };
        }

        /**
         * Create a markdown table formatter
         */
        public static FormatterFunction markdownTable() {
            return context -> {
                StringBuilder md = new StringBuilder();
                md.append("| Metric | Value |\n");
                md.append("|--------|-------|\n");
                context.getMetrics().forEach((key, value) ->
                        md.append("| ").append(key).append(" | ").append(value).append(" |\n"));
                return md.toString();
            };
        }
    }
}