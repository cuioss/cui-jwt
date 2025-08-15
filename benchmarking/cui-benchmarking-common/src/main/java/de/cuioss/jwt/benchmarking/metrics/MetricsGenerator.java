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
package de.cuioss.jwt.benchmarking.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.jwt.benchmarking.BenchmarkSummary;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates structured performance metrics in JSON format.
 * <p>
 * This generator creates comprehensive metrics data that can be consumed by
 * monitoring systems, dashboards, and API endpoints. The metrics include:
 * <ul>
 *   <li>Performance metrics with throughput and latency data</li>
 *   <li>Historical tracking data for trend analysis</li>
 *   <li>Summary statistics for CI integration</li>
 * </ul>
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 */
public class MetricsGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsGenerator.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Generates comprehensive metrics JSON from benchmark results.
     * 
     * @param results JMH benchmark results
     * @param outputDir directory to write metrics files
     * @throws IOException if metrics generation fails
     */
    public void generateMetricsJson(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating metrics JSON for %d benchmark results", results.size());

        // Generate main metrics file
        PerformanceMetrics metrics = createPerformanceMetrics(results);
        writeMetricsFile(metrics, outputDir + "/metrics.json");

        // Generate individual benchmark metrics
        generateIndividualMetrics(results, outputDir);

        LOGGER.info("Metrics generation completed");
    }

    /**
     * Writes a benchmark summary file for CI integration.
     * 
     * @param summary benchmark execution summary
     * @param filepath path to write the summary file
     * @throws IOException if writing fails
     */
    public void writeSummaryFile(BenchmarkSummary summary, String filepath) throws IOException {
        LOGGER.info("Writing benchmark summary to: %s", filepath);

        Path path = Paths.get(filepath);
        Files.createDirectories(path.getParent());
        
        String json = GSON.toJson(summary);
        Files.writeString(path, json);

        LOGGER.info("Summary file written successfully");
    }

    private PerformanceMetrics createPerformanceMetrics(Collection<RunResult> results) {
        Instant timestamp = Instant.now();
        
        List<BenchmarkMetric> benchmarks = results.stream()
                .map(this::createBenchmarkMetric)
                .collect(Collectors.toList());

        double averageThroughput = benchmarks.stream()
                .mapToDouble(BenchmarkMetric::throughput)
                .average()
                .orElse(0.0);

        double averageLatency = benchmarks.stream()
                .mapToDouble(BenchmarkMetric::latency)
                .average()
                .orElse(0.0);

        return new PerformanceMetrics(
                timestamp,
                benchmarks.size(),
                averageThroughput,
                averageLatency,
                benchmarks
        );
    }

    private BenchmarkMetric createBenchmarkMetric(RunResult result) {
        String name = extractBenchmarkName(result.getParams().getBenchmark());
        double score = result.getPrimaryResult().getScore();
        String unit = result.getPrimaryResult().getUnit();
        
        // Convert to throughput/latency based on unit
        double throughput = unit.contains("ops") ? score : 1.0 / score;
        double latency = unit.contains("ops") ? 1.0 / score : score;

        Map<String, Object> details = Map.of(
                "unit", unit,
                "mode", result.getPrimaryResult().getLabel(),
                "forks", result.getParams().getForks(),
                "threads", result.getParams().getThreads(),
                "warmupIterations", result.getParams().getWarmupIterations(),
                "measurementIterations", result.getParams().getMeasurementIterations()
        );

        return new BenchmarkMetric(name, throughput, latency, details);
    }

    private String extractBenchmarkName(String fullBenchmarkName) {
        String[] parts = fullBenchmarkName.split("\\.");
        return parts[parts.length - 1];
    }

    private void generateIndividualMetrics(Collection<RunResult> results, String outputDir) throws IOException {
        for (RunResult result : results) {
            String benchmarkName = extractBenchmarkName(result.getParams().getBenchmark());
            BenchmarkMetric metric = createBenchmarkMetric(result);
            
            String filename = outputDir + "/" + benchmarkName + "-metrics.json";
            writeMetricsFile(metric, filename);
        }
        
        LOGGER.info("Generated individual metrics for %d benchmarks", results.size());
    }

    private void writeMetricsFile(Object metrics, String filepath) throws IOException {
        Path path = Paths.get(filepath);
        Files.createDirectories(path.getParent());
        
        String json = GSON.toJson(metrics);
        Files.writeString(path, json);
        
        LOGGER.debug("Metrics written to: %s", filepath);
    }

    /**
     * Complete performance metrics data structure.
     * 
     * @param timestamp when metrics were generated
     * @param benchmarkCount number of benchmarks executed
     * @param averageThroughput average throughput across all benchmarks
     * @param averageLatency average latency across all benchmarks
     * @param benchmarks individual benchmark metrics
     */
    public record PerformanceMetrics(
            Instant timestamp,
            int benchmarkCount,
            double averageThroughput,
            double averageLatency,
            List<BenchmarkMetric> benchmarks
    ) {}

    /**
     * Individual benchmark metric data.
     * 
     * @param name benchmark name
     * @param throughput operations per second
     * @param latency average latency in seconds
     * @param details additional benchmark details
     */
    public record BenchmarkMetric(
            String name,
            double throughput,
            double latency,
            Map<String, Object> details
    ) {}
}