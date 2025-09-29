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
package de.cuioss.benchmarking.common.profiler;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JMH Profiler that captures precise timestamps for each iteration.
 * <p>
 * This profiler addresses the critical issue where Prometheus metrics are averaged
 * over the entire benchmark session (including warmup, setup, and idle time) rather
 * than just the actual measurement iterations.
 * <p>
 * It captures:
 * - Exact start and end time for each iteration (warmup and measurement)
 * - Fork identification
 * - Iteration type (warmup vs measurement)
 * - Duration of each iteration
 * <p>
 * The timestamps are written to a JSON file that can be used to query Prometheus
 * for metrics during the exact time windows when benchmarks were running.
 */
public class TimestampProfiler implements InternalProfiler {

    /**
     * Stores iteration timestamps for all benchmarks
     */
    private static final ConcurrentMap<String, List<IterationTimestamp>> TIMESTAMPS = new ConcurrentHashMap<>();

    /**
     * Path to write the timestamp file
     */
    private static final Path OUTPUT_DIR = Path.of(
            System.getProperty("benchmark.results.dir", "target/benchmark-results"));

    /**
     * Current iteration timestamp
     */
    private IterationTimestamp currentIteration;

    /**
     * Fork identifier - extracted from thread name or process ID
     */
    private final String forkId;

    public TimestampProfiler() {
        // Extract fork ID from thread name (JMH pattern: "benchmark-fork-N")
        String threadName = Thread.currentThread().getName();
        if (threadName.contains("fork-")) {
            this.forkId = threadName.substring(threadName.indexOf("fork-") + 5);
        } else {
            // Fallback to process ID
            this.forkId = String.valueOf(ProcessHandle.current().pid());
        }
    }

    @Override public String getDescription() {
        return "Captures precise timestamps for each benchmark iteration for accurate metrics correlation";
    }

    @Override public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        String benchmarkName = extractBenchmarkName(benchmarkParams.getBenchmark());

        currentIteration = new IterationTimestamp(
                benchmarkName,
                forkId,
                iterationParams.getType().name(),
                iterationParams.getCount(),
                Instant.now(),
                null
        );

        // Log to console for debugging
        System.err.printf("[TIMESTAMP] %s Fork-%s %s-%d START: %s%n",
                benchmarkName,
                forkId,
                iterationParams.getType(),
                iterationParams.getCount(),
                currentIteration.startTime);
    }

    @Override public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
            IterationParams iterationParams,
            IterationResult result) {
        if (currentIteration != null) {
            currentIteration = new IterationTimestamp(
                    currentIteration.benchmarkName,
                    currentIteration.forkId,
                    currentIteration.iterationType,
                    currentIteration.iterationNumber,
                    currentIteration.startTime,
                    Instant.now()
            );

            String key = currentIteration.benchmarkName;
            TIMESTAMPS.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(currentIteration);

            // Log to console
            System.err.printf("[TIMESTAMP] %s Fork-%s %s-%d END: %s (duration: %dms)%n",
                    currentIteration.benchmarkName,
                    currentIteration.forkId,
                    currentIteration.iterationType,
                    currentIteration.iterationNumber,
                    currentIteration.endTime,
                    currentIteration.getDurationMs());

            // Write to file immediately (append mode) to prevent data loss between forks
            writeTimestampToFile(currentIteration);

            // Return duration as a secondary result
            return List.of(
                    new ScalarResult("Iteration.Duration",
                            currentIteration.getDurationMs(),
                            "ms",
                            AggregationPolicy.AVG)
            );
        }

        return Collections.emptyList();
    }

    /**
     * Writes a single timestamp entry to the JSONL file (JSON Lines format)
     */
    private void writeTimestampToFile(IterationTimestamp timestamp) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Path timestampFile = OUTPUT_DIR.resolve("jmh-iteration-timestamps.jsonl");

            String json = String.format(
                    "{\"benchmark\":\"%s\",\"fork\":\"%s\",\"type\":\"%s\",\"iteration\":%d," +
                            "\"start\":\"%s\",\"end\":\"%s\",\"duration_ms\":%d}%n",
                    timestamp.benchmarkName,
                    timestamp.forkId,
                    timestamp.iterationType,
                    timestamp.iterationNumber,
                    timestamp.startTime,
                    timestamp.endTime,
                    timestamp.getDurationMs()
            );

            Files.writeString(timestampFile, json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

        } catch (IOException e) {
            System.err.println("Failed to write timestamp: " + e.getMessage());
        }
    }

    /**
     * Writes consolidated JSON file with all timestamps
     */
    public static void writeConsolidatedReport() {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Path reportFile = OUTPUT_DIR.resolve("jmh-iteration-timing-report.json");

            try (BufferedWriter writer = Files.newBufferedWriter(reportFile)) {
                writer.write("{\n");
                writer.write("  \"generated_at\": \"" + Instant.now() + "\",\n");
                writer.write("  \"benchmarks\": {\n");

                boolean firstBenchmark = true;
                for (var entry : TIMESTAMPS.entrySet()) {
                    if (!firstBenchmark) writer.write(",\n");
                    firstBenchmark = false;

                    writer.write("    \"" + entry.getKey() + "\": [\n");

                    boolean firstIteration = true;
                    for (var timestamp : entry.getValue()) {
                        if (!firstIteration) writer.write(",\n");
                        firstIteration = false;

                        writer.write(String.format(
                                "      {\"fork\":\"%s\",\"type\":\"%s\",\"iteration\":%d," +
                                        "\"start\":\"%s\",\"end\":\"%s\",\"duration_ms\":%d}",
                                timestamp.forkId,
                                timestamp.iterationType,
                                timestamp.iterationNumber,
                                timestamp.startTime,
                                timestamp.endTime,
                                timestamp.getDurationMs()
                        ));
                    }

                    writer.write("\n    ]");
                }

                writer.write("\n  }\n");
                writer.write("}\n");
            }

            System.err.println("[TIMESTAMP] Consolidated report written to: " + reportFile);

        } catch (IOException e) {
            System.err.println("Failed to write consolidated report: " + e.getMessage());
        }
    }

    /**
     * Extracts clean benchmark name from full class path
     */
    private String extractBenchmarkName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    /**
     * Data class for iteration timestamp
     */
    private record IterationTimestamp(
    String benchmarkName,
    String forkId,
    String iterationType,
    int iterationNumber,
    Instant startTime,
    Instant endTime
    ) {
        long getDurationMs() {
            if (startTime != null && endTime != null) {
                return endTime.toEpochMilli() - startTime.toEpochMilli();
            }
            return 0;
        }
    }
}