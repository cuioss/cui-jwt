package de.cuioss.benchmarking.common.metrics;

import com.google.gson.Gson;
import de.cuioss.benchmarking.common.gson.GsonProvider;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.io.Writer;
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
 */
public class MetricsGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsGenerator.class);
    private static final Gson GSON = GsonProvider.getGson();

    /**
     * Generates comprehensive metrics JSON from benchmark results.
     *
     * @param results   JMH benchmark results
     * @param outputDir directory to write metrics files
     */
    public void generateMetricsJson(Collection<RunResult> results, String outputDir) {
        LOGGER.info("Generating metrics JSON for %d benchmark results", results.size());

        try {
            // Generate main metrics file
            PerformanceMetrics metrics = createPerformanceMetrics(results);
            writeMetricsFile(metrics, outputDir + "/metrics.json");

            // Generate individual benchmark metrics
            generateIndividualMetrics(results, outputDir);

            LOGGER.info("Metrics generation completed");
        } catch (IOException e) {
            LOGGER.error("Failed to generate metrics", e);
            throw new RuntimeException(e);
        }
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
        String unit = result.getPrimaryResult().getScoreUnit();

        // Convert to throughput/latency based on unit
        double throughput = unit.contains("ops") ? score : 1.0 / score;
        double latency = unit.contains("ops") ? 1.0 / score : score;

        Map<String, Object> details = Map.of(
                "unit", unit,
                "mode", result.getPrimaryResult().getLabel(),
                "forks", result.getParams().getForks(),
                "threads", result.getParams().getThreads(),
                "warmupIterations", result.getParams().getWarmup().getCount(),
                "measurementIterations", result.getParams().getMeasurement().getCount()
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
            LOGGER.info("Writing individual metrics to: %s", filename);
            writeMetricsFile(metric, filename);
        }

        LOGGER.info("Generated individual metrics for %d benchmarks", results.size());
    }

    private void writeMetricsFile(Object metrics, String filepath) throws IOException {
        Path path = Paths.get(filepath);
        Files.createDirectories(path.getParent());

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(metrics, writer);
        }

        LOGGER.debug("Metrics written to: %s", filepath);
    }
}
