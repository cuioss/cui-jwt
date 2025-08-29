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
package de.cuioss.jwt.validation.benchmark;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simplified metrics exporter that directly exports StripedRingBufferStatistics
 * without complex aggregation.
 */
public class SimplifiedMetricsExporter {

    private static final CuiLogger log = new CuiLogger(SimplifiedMetricsExporter.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Double.class, (JsonSerializer<Double>) (src, typeOfSrc, context) -> {
                if (src == src.longValue()) {
                    return new JsonPrimitive(src.longValue());
                }
                return new JsonPrimitive(src);
            })
            .create();
    public static final String MEASURE = "measure";
    public static final String VALIDATE = "validate";
    public static final String BENCHMARK = "benchmark";

    private SimplifiedMetricsExporter() {
        // Utility class
    }


    /**
     * Export metrics from a TokenValidatorMonitor directly to JSON file.
     *
     * @param monitor The monitor containing the metrics
     * @throws IOException if writing fails
     */
    public static synchronized void exportMetrics(TokenValidatorMonitor monitor) throws IOException {
        if (monitor == null) {
            log.debug("No monitor provided, skipping metrics export");
            return;
        }

        // Get current benchmark name from thread or stack trace
        String benchmarkName = getCurrentBenchmarkName();
        if (benchmarkName == null) {
            benchmarkName = "unknown_benchmark";
        }

        String outputDir = "target/benchmark-results";
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        // Single file for all metrics
        Path outputFile = outputPath.resolve("jwt-validation-metrics.json");

        // Read existing data if file exists
        Map<String, Object> allMetrics = new LinkedHashMap<>();
        if (Files.exists(outputFile)) {
            try {
                String existingContent = Files.readString(outputFile);
                if (existingContent != null && !existingContent.trim().isEmpty()) {
                    TypeToken<Map<String, Object>> typeToken = new TypeToken<>() {
                    };
                    Map<String, Object> parsedMetrics = GSON.fromJson(existingContent, typeToken.getType());
                    if (parsedMetrics != null) {
                        allMetrics = parsedMetrics;
                    }
                }
            } catch (IOException | JsonSyntaxException e) {
                log.warn("Failed to read existing metrics file, starting fresh: {}", e.getMessage());
                // Delete corrupted file to start fresh
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException deleteException) {
                    log.warn("Failed to delete corrupted metrics file", deleteException);
                }
            }
        }

        // Create metrics for current benchmark
        Map<String, Object> benchmarkMetrics = new LinkedHashMap<>();
        benchmarkMetrics.put("timestamp", ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));

        Map<String, Map<String, Object>> steps = new LinkedHashMap<>();

        // Export metrics for each measurement type
        for (MeasurementType type : monitor.getEnabledTypes()) {
            Optional<StripedRingBufferStatistics> statsOpt = monitor.getValidationMetrics(type);

            if (statsOpt.isPresent()) {
                StripedRingBufferStatistics stats = statsOpt.get();
                if (stats.sampleCount() > 0) {
                    Map<String, Object> stepMetrics = new LinkedHashMap<>();
                    stepMetrics.put("sample_count", stats.sampleCount());

                    // Convert Duration to microseconds and round appropriately
                    stepMetrics.put("p50_us", durationToRoundedMicros(stats.p50()));
                    stepMetrics.put("p95_us", durationToRoundedMicros(stats.p95()));
                    stepMetrics.put("p99_us", durationToRoundedMicros(stats.p99()));

                    steps.put(type.name().toLowerCase(), stepMetrics);
                }
            }
        }

        benchmarkMetrics.put("steps", steps);

        // Add or update benchmark data using benchmark name as top-level element
        allMetrics.put(benchmarkName, benchmarkMetrics);

        // Write all metrics to single JSON file
        try {
            String jsonContent = GSON.toJson(allMetrics);
            Files.writeString(outputFile, jsonContent);
            log.info("Exported metrics for {} to {}", benchmarkName, outputFile);
        } catch (IOException e) {
            log.error("Failed to export metrics to {}", outputFile, e);
            throw e;
        }
    }

    /**
     * Get current benchmark name using system property first, then fallback to runtime detection.
     * This approach is more robust and maintainable than parsing thread names and stack traces.
     */
    private static String getCurrentBenchmarkName() {
        // Use hardcoded benchmark context - no system property override

        // Fallback 1: Try to find benchmark class name from stack trace
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            String methodName = element.getMethodName();

            // Look for benchmark class names (more reliable than method names)
            if (className.contains("Benchmark") && !className.equals(SimplifiedMetricsExporter.class.getName())) {
                String simpleName = className.substring(className.lastIndexOf('.') + 1);
                // Remove "Benchmark" suffix if present for cleaner names
                if (simpleName.endsWith("Benchmark")) {
                    return simpleName.substring(0, simpleName.length() - 9).toLowerCase();
                }
                return simpleName.toLowerCase();
            }

            // Look for benchmark method patterns
            if (methodName.startsWith(MEASURE) || methodName.startsWith(VALIDATE) || methodName.startsWith(BENCHMARK)) {
                return methodName;
            }
        }

        // Fallback 2: Parse thread name with more generic patterns
        String threadName = Thread.currentThread().getName();
        // JMH typically includes benchmark information in thread name
        if (threadName.contains(MEASURE)) {
            return extractBenchmarkFromThread(threadName, MEASURE);
        }
        if (threadName.contains(VALIDATE)) {
            return extractBenchmarkFromThread(threadName, VALIDATE);
        }
        if (threadName.contains(BENCHMARK)) {
            return extractBenchmarkFromThread(threadName, BENCHMARK);
        }

        return null;
    }

    /**
     * Extract benchmark name from thread name using pattern matching
     */
    private static String extractBenchmarkFromThread(String threadName, String pattern) {
        int index = threadName.indexOf(pattern);
        if (index >= 0) {
            // Extract the method name part
            String remaining = threadName.substring(index);
            // Find word boundary or common separators
            int endIndex = remaining.length();
            for (int i = pattern.length(); i < remaining.length(); i++) {
                char c = remaining.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    endIndex = i;
                    break;
                }
            }
            return remaining.substring(0, endIndex);
        }
        return pattern;
    }

    /**
     * Convert Duration to microseconds with appropriate rounding.
     *
     * @param duration The duration to convert
     * @return Number (Long for values >= 10, Double for values < 10)
     */
    private static Number durationToRoundedMicros(Duration duration) {
        double micros = duration.toNanos() / 1000.0;
        if (micros >= 10.0) {
            return Math.round(micros);
        } else {
            return Math.round(micros * 10) / 10.0;
        }
    }

}