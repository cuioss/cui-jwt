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

import de.cuioss.benchmarking.common.metrics.pipeline.MetricsContext;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessingException;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessor;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processor that exports metrics data to files or external systems.
 * Supports multiple export targets and formats.
 *
 * @since 1.0
 */
public class ExportProcessor implements MetricsProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(ExportProcessor.class);

    /**
     * List of export targets
     */
    private final List<ExportTarget> targets;

    /**
     * Whether to create directories if they don't exist
     */
    private final boolean createDirectories;

    /**
     * Default constructor with no targets
     */
    public ExportProcessor() {
        this(new ArrayList<>(), true);
    }

    /**
     * Constructor with targets
     */
    public ExportProcessor(List<ExportTarget> targets) {
        this(targets, true);
    }

    /**
     * Full constructor
     */
    public ExportProcessor(List<ExportTarget> targets, boolean createDirectories) {
        this.targets = targets != null ? targets : new ArrayList<>();
        this.createDirectories = createDirectories;
    }

    @Override public MetricsContext process(MetricsContext context) throws MetricsProcessingException {
        LOGGER.debug("Exporting metrics from source: {} to {} targets",
                context.getSource(), targets.size());

        List<String> exportedFiles = new ArrayList<>();

        for (ExportTarget target : targets) {
            try {
                String result = target.export(context, createDirectories);
                if (result != null) {
                    exportedFiles.add(result);
                    LOGGER.debug("Exported metrics to: {}", result);
                }
            } catch (Exception e) {
                throw new MetricsProcessingException(getName(),
                        "Failed to export to target: " + target.getName(), e);
            }
        }

        // Add export results to metadata
        context.addMetadata("exported_files", exportedFiles);
        context.addMetadata("export_count", exportedFiles.size());
        context.addMetadata("export_timestamp", Instant.now().toString());

        LOGGER.debug("Export completed, wrote {} files", exportedFiles.size());
        return context;
    }

    /**
     * Add an export target
     */
    public ExportProcessor addTarget(ExportTarget target) {
        if (target != null) {
            targets.add(target);
        }
        return this;
    }

    @Override public String getName() {
        return "ExportProcessor";
    }

    /**
     * Interface for export targets
     */
    public interface ExportTarget {
        /**
         * Export the metrics context
         *
         * @param context The metrics context to export
         * @param createDirectories Whether to create directories if they don't exist
         * @return The export result (e.g., file path, URL, or status message)
         * @throws IOException if export fails
         */
        String export(MetricsContext context, boolean createDirectories) throws IOException;

        /**
         * Get the name of this export target
         *
         * @return The target name
         */
        String getName();
    }

    /**
     * Common export targets
     */
    public static class Targets {

        /**
         * Export to a file
         */
        public static ExportTarget file(String filePath, boolean append) {
            return new ExportTarget() {
                @Override public String export(MetricsContext context, boolean createDirectories) throws IOException {
                    Path path = Path.of(filePath);

                    // Create parent directories if needed
                    if (createDirectories) {
                        Path parent = path.getParent();
                        if (parent != null && !Files.exists(parent)) {
                            Files.createDirectories(parent);
                        }
                    }

                    // Get formatted output from context
                    String content = context.getMetadata("formatted_output", String.class);
                    if (content == null) {
                        // If no formatted output, use JSON format by default
                        FormatProcessor formatter = new FormatProcessor();
                        try {
                            formatter.process(context);
                            content = context.getMetadata("formatted_output", String.class);
                        } catch (MetricsProcessingException e) {
                            throw new IOException("Failed to format metrics", e);
                        }
                    }

                    // Write to file
                    if (append && Files.exists(path)) {
                        Files.write(path, (content + "\n").getBytes(), StandardOpenOption.APPEND);
                    } else {
                        Files.write(path, content.getBytes());
                    }

                    return path.toAbsolutePath().toString();
                }

                @Override public String getName() {
                    return "FileExport(" + filePath + ")";
                }
            };
        }

        /**
         * Export to a timestamped file
         */
        public static ExportTarget timestampedFile(String directory, String prefix, String extension) {
            return new ExportTarget() {
                @Override public String export(MetricsContext context, boolean createDirectories) throws IOException {
                    // Create directory if needed
                    Path dirPath = Path.of(directory);
                    if (createDirectories && !Files.exists(dirPath)) {
                        Files.createDirectories(dirPath);
                    }

                    // Generate timestamped filename
                    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                            .format(context.getTimestamp().atZone(ZoneId.systemDefault()));
                    String filename = "%s-%s.%s".formatted(prefix, timestamp, extension);
                    Path filePath = dirPath.resolve(filename);

                    // Get formatted output
                    String content = context.getMetadata("formatted_output", String.class);
                    if (content == null) {
                        FormatProcessor formatter = new FormatProcessor();
                        try {
                            formatter.process(context);
                            content = context.getMetadata("formatted_output", String.class);
                        } catch (MetricsProcessingException e) {
                            throw new IOException("Failed to format metrics", e);
                        }
                    }

                    // Write to file
                    Files.write(filePath, content.getBytes());
                    return filePath.toAbsolutePath().toString();
                }

                @Override public String getName() {
                    return "TimestampedFileExport(" + directory + ")";
                }
            };
        }

        /**
         * Export with custom file naming based on metrics
         */
        public static ExportTarget dynamicFile(String directory, FileNameGenerator nameGenerator) {
            return new ExportTarget() {
                @Override public String export(MetricsContext context, boolean createDirectories) throws IOException {
                    // Create directory if needed
                    Path dirPath = Path.of(directory);
                    if (createDirectories && !Files.exists(dirPath)) {
                        Files.createDirectories(dirPath);
                    }

                    // Generate filename
                    String filename = nameGenerator.generate(context);
                    Path filePath = dirPath.resolve(filename);

                    // Get formatted output
                    String content = context.getMetadata("formatted_output", String.class);
                    if (content == null) {
                        FormatProcessor formatter = new FormatProcessor();
                        try {
                            formatter.process(context);
                            content = context.getMetadata("formatted_output", String.class);
                        } catch (MetricsProcessingException e) {
                            throw new IOException("Failed to format metrics", e);
                        }
                    }

                    // Write to file
                    Files.write(filePath, content.getBytes());
                    return filePath.toAbsolutePath().toString();
                }

                @Override public String getName() {
                    return "DynamicFileExport(" + directory + ")";
                }
            };
        }

        /**
         * Export to multiple files based on metric categories
         */
        public static ExportTarget multiFile(String directory, String extension) {
            return new ExportTarget() {
                @Override public String export(MetricsContext context, boolean createDirectories) throws IOException {
                    // Create directory if needed
                    Path dirPath = Path.of(directory);
                    if (createDirectories && !Files.exists(dirPath)) {
                        Files.createDirectories(dirPath);
                    }

                    List<String> exportedFiles = new ArrayList<>();

                    // Export metrics by category (determined by prefix)
                    Map<String, Map<String, Object>> categorized = new HashMap<>();

                    for (Map.Entry<String, Object> entry : context.getMetrics().entrySet()) {
                        String key = entry.getKey();
                        String category = key.contains("_") ? key.substring(0, key.indexOf('_')) : "general";
                        categorized.computeIfAbsent(category, k -> new HashMap<>())
                                .put(key, entry.getValue());
                    }

                    // Write each category to a separate file
                    for (Map.Entry<String, Map<String, Object>> category : categorized.entrySet()) {
                        String filename = "%s-metrics.%s".formatted(category.getKey(), extension);
                        Path filePath = dirPath.resolve(filename);

                        FormatProcessor formatter = new FormatProcessor();
                        MetricsContext categoryContext = new MetricsContext(context.getSource());
                        categoryContext.getMetrics().putAll(category.getValue());

                        try {
                            formatter.process(categoryContext);
                            String content = categoryContext.getMetadata("formatted_output", String.class);
                            Files.write(filePath, content.getBytes());
                            exportedFiles.add(filePath.toAbsolutePath().toString());
                        } catch (MetricsProcessingException e) {
                            throw new IOException("Failed to format metrics for category: " + category.getKey(), e);
                        }
                    }

                    return String.join(", ", exportedFiles);
                }

                @Override public String getName() {
                    return "MultiFileExport(" + directory + ")";
                }
            };
        }
    }

    /**
     * Interface for generating dynamic file names
     */
    @FunctionalInterface public interface FileNameGenerator {
        /**
         * Generate a file name based on the metrics context
         *
         * @param context The metrics context
         * @return The generated file name
         */
        String generate(MetricsContext context);
    }
}