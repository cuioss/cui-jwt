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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;
import de.cuioss.tools.logging.CuiLogger;

import java.io.FileWriter;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SimplifiedMetricsExporter() {
        // Utility class
    }


    /**
     * Export metrics from a TokenValidatorMonitor directly to JSON file.
     *
     * @param monitor The monitor containing the metrics
     * @throws IOException if writing fails
     */
    public static void exportMetrics(TokenValidatorMonitor monitor) throws IOException {
        if (monitor == null) {
            log.debug("No monitor provided, skipping metrics export");
            return;
        }
        
        // Get current benchmark name from thread or stack trace
        String benchmarkName = getCurrentBenchmarkName();
        if (benchmarkName == null) {
            benchmarkName = "unknown_benchmark";
        }

        String outputDir = System.getProperty("benchmark.results.dir", "target/benchmark-results");
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        Map<String, Object> metricsJson = new LinkedHashMap<>();
        metricsJson.put("timestamp", ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));
        metricsJson.put("benchmark", benchmarkName);

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

        metricsJson.put("steps", steps);

        // Write JSON file - one per benchmark
        Path outputFile = outputPath.resolve("jwt-validation-metrics-" + benchmarkName + ".json");
        try (FileWriter writer = new FileWriter(outputFile.toFile())) {
            GSON.toJson(metricsJson, writer);
            log.info("Exported metrics to {}", outputFile);
        } catch (IOException e) {
            log.error("Failed to export metrics to {}", outputFile, e);
            throw e;
        }
    }
    
    /**
     * Get current benchmark name from thread or stack trace
     */
    private static String getCurrentBenchmarkName() {
        String threadName = Thread.currentThread().getName();
        // JMH typically includes benchmark method name in thread name
        if (threadName.contains("measureThroughput")) return "measureThroughput";
        if (threadName.contains("measureAverageTime")) return "measureAverageTime";
        if (threadName.contains("measureConcurrentValidation")) return "measureConcurrentValidation";
        if (threadName.contains("validateMixedTokens0")) return "validateMixedTokens0";
        if (threadName.contains("validateMixedTokens50")) return "validateMixedTokens50";
        
        // Fallback: check stack trace
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String methodName = element.getMethodName();
            if (methodName.startsWith("measure") || methodName.startsWith("validate")) {
                return methodName;
            }
        }
        
        return null;
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