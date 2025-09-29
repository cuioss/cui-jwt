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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses JMH iteration timestamp files to extract precise timing windows
 * for Prometheus metrics collection.
 * <p>
 * Supports both:
 * - JSONL format (one JSON object per line) from TimestampProfiler
 * - Consolidated JSON format with all benchmarks
 */
public class IterationTimestampParser {

    private static final CuiLogger LOGGER = new CuiLogger(IterationTimestampParser.class);
    private static final Gson GSON = new Gson();

    /**
     * Represents a time window for a benchmark iteration
     */
    public record IterationWindow(
    String benchmarkName,
    String forkId,
    String iterationType,
    int iterationNumber,
    Instant startTime,
    Instant endTime,
    long durationMs
    ) {
        public boolean isMeasurement() {
            return "MEASUREMENT".equalsIgnoreCase(iterationType);
        }

        public boolean isWarmup() {
            return "WARMUP".equalsIgnoreCase(iterationType);
        }
    }

    /**
     * Parses JSONL timestamp file (one JSON object per line)
     */
    public static List<IterationWindow> parseJsonlFile(Path timestampFile) throws IOException {
        if (!Files.exists(timestampFile)) {
            LOGGER.warn("Timestamp file does not exist: {}", timestampFile);
            return Collections.emptyList();
        }

        List<IterationWindow> windows = new ArrayList<>();

        Files.lines(timestampFile).forEach(line -> {
            if (line.trim().isEmpty()) return;

            try {
                JsonObject node = JsonParser.parseString(line).getAsJsonObject();
                windows.add(parseIterationNode(node));
            } catch (Exception e) {
                LOGGER.warn("Failed to parse timestamp line: {}", line, e);
            }
        });

        LOGGER.info("Parsed {} iteration windows from {}", windows.size(), timestampFile);
        return windows;
    }

    /**
     * Parses consolidated JSON report file
     */
    public static Map<String, List<IterationWindow>> parseConsolidatedReport(Path reportFile) throws IOException {
        if (!Files.exists(reportFile)) {
            LOGGER.warn("Consolidated report file does not exist: {}", reportFile);
            return Collections.emptyMap();
        }

        Map<String, List<IterationWindow>> result = new HashMap<>();
        String json = Files.readString(reportFile);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        JsonObject benchmarks = root.getAsJsonObject("benchmarks");
        if (benchmarks != null) {
            for (var entry : benchmarks.entrySet()) {
                String benchmarkName = entry.getKey();
                List<IterationWindow> windows = new ArrayList<>();
                JsonElement iterations = entry.getValue();

                if (iterations.isJsonArray()) {
                    for (JsonElement iteration : iterations.getAsJsonArray()) {
                        try {
                            IterationWindow window = parseIterationNode(iteration.getAsJsonObject(), benchmarkName);
                            windows.add(window);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse iteration for benchmark {}", benchmarkName, e);
                        }
                    }
                }

                result.put(benchmarkName, windows);
            }
        }

        LOGGER.info("Parsed {} benchmarks with total {} iterations from {}",
                result.size(),
                result.values().stream().mapToInt(List::size).sum(),
                reportFile);

        return result;
    }

    /**
     * Gets measurement windows only (excludes warmup)
     */
    public static List<IterationWindow> getMeasurementWindows(List<IterationWindow> windows) {
        return windows.stream()
                .filter(IterationWindow::isMeasurement)
                .collect(Collectors.toList());
    }

    /**
     * Calculates aggregated time window for all measurement iterations
     */
    public static Optional<IterationWindow> getAggregatedMeasurementWindow(
            String benchmarkName, List<IterationWindow> windows) {

        List<IterationWindow> measurements = getMeasurementWindows(windows);
        if (measurements.isEmpty()) {
            return Optional.empty();
        }

        // Find earliest start and latest end
        Instant start = measurements.stream()
                .map(IterationWindow::startTime)
                .min(Instant::compareTo)
                .orElse(null);

        Instant end = measurements.stream()
                .map(IterationWindow::endTime)
                .max(Instant::compareTo)
                .orElse(null);

        if (start != null && end != null) {
            long duration = end.toEpochMilli() - start.toEpochMilli();
            return Optional.of(new IterationWindow(
                    benchmarkName,
                    "aggregated",
                    "MEASUREMENT",
                    -1,
                    start,
                    end,
                    duration
            ));
        }

        return Optional.empty();
    }

    /**
     * Groups windows by fork ID
     */
    public static Map<String, List<IterationWindow>> groupByFork(List<IterationWindow> windows) {
        return windows.stream()
                .collect(Collectors.groupingBy(IterationWindow::forkId));
    }

    /**
     * Parses a single iteration node
     */
    private static IterationWindow parseIterationNode(JsonObject node) {
        return parseIterationNode(node, node.get("benchmark").getAsString());
    }

    /**
     * Parses a single iteration node with explicit benchmark name
     */
    private static IterationWindow parseIterationNode(JsonObject node, String benchmarkName) {
        return new IterationWindow(
                benchmarkName,
                node.get("fork").getAsString(),
                node.get("type").getAsString(),
                node.get("iteration").getAsInt(),
                Instant.parse(node.get("start").getAsString()),
                Instant.parse(node.get("end").getAsString()),
                node.get("duration_ms").getAsLong()
        );
    }

    /**
     * Finds the timestamp files in the results directory
     */
    public static Path findTimestampFile(Path resultsDir) {
        // Try JSONL format first
        Path jsonlFile = resultsDir.resolve("jmh-iteration-timestamps.jsonl");
        if (Files.exists(jsonlFile)) {
            return jsonlFile;
        }

        // Try consolidated report
        Path reportFile = resultsDir.resolve("jmh-iteration-timing-report.json");
        if (Files.exists(reportFile)) {
            return reportFile;
        }

        LOGGER.warn("No JMH timestamp files found in {}", resultsDir);
        return null;
    }
}