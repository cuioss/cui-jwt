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
package de.cuioss.benchmarking.common.report;

import com.google.gson.*;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates a standardized JSON data file containing all report data
 * that can be consumed by HTML templates via JavaScript.
 * <p>
 * This generator consolidates all benchmark data and metrics into a single
 * JSON file named "benchmark-data.json" that templates load and process.
 * <p>
 * The generated JSON structure includes:
 * <ul>
 *   <li>Metadata (timestamp, benchmark type, etc.)</li>
 *   <li>Overview metrics (totals, averages, grades)</li>
 *   <li>Detailed benchmark results</li>
 *   <li>Chart data for visualizations</li>
 *   <li>Trend data for historical analysis</li>
 * </ul>
 */
public class ReportDataGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(ReportDataGenerator.class);
    private static final String DATA_FILE_NAME = "data/benchmark-data.json";
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();

    /**
     * Generates the consolidated benchmark data JSON file.
     *
     * @param jsonFile the path to the benchmark JSON results file
     * @param benchmarkType the type of benchmark
     * @param outputDir the output directory for the data file
     * @throws IOException if reading JSON or writing data file fails
     */
    public void generateDataFile(Path jsonFile, BenchmarkType benchmarkType, String outputDir) throws IOException {
        // Parse input JSON file
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);

        LOGGER.info(INFO.GENERATING_INDEX_PAGE.format(benchmarks.size()));

        // Build comprehensive data structure
        JsonObject dataObject = new JsonObject();

        // Add metadata
        dataObject.add("metadata", generateMetadata(benchmarkType));

        // Add overview metrics
        dataObject.add("overview", generateOverviewData(benchmarks));

        // Add formatted benchmark results
        dataObject.add("benchmarks", generateBenchmarkData(benchmarks));

        // Add chart data
        dataObject.add("chartData", generateChartData(benchmarks));

        // Add percentiles chart data
        dataObject.add("percentilesData", generatePercentilesData(benchmarks));

        // Add trend data (placeholder for now - would load historical data in production)
        dataObject.add("trends", generateTrendData(benchmarks));

        // Write the consolidated data file to data subdirectory
        Path outputPath = Path.of(outputDir);
        Path dataDir = outputPath.resolve("data");
        Files.createDirectories(dataDir);
        Path dataFile = dataDir.resolve("benchmark-data.json");
        Files.writeString(dataFile, GSON.toJson(dataObject));

        // Also copy the original benchmark result JSON to data directory
        Path originalJsonDest = dataDir.resolve("benchmark-result.json");
        Files.copy(jsonFile, originalJsonDest, StandardCopyOption.REPLACE_EXISTING);

        LOGGER.info(INFO.INDEX_PAGE_GENERATED.format(dataFile));
    }

    private JsonObject generateMetadata(BenchmarkType benchmarkType) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("timestamp", Instant.now().toString());
        metadata.addProperty("displayTimestamp",
                DISPLAY_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));
        metadata.addProperty("benchmarkType", benchmarkType.getDisplayName());
        metadata.addProperty("benchmarkTypeId", benchmarkType.name());
        metadata.addProperty("version", "1.0.0");
        return metadata;
    }

    private JsonObject generateOverviewData(JsonArray benchmarks) {
        JsonObject overview = new JsonObject();

        int totalBenchmarks = benchmarks.size();
        double avgThroughput = calculateAverageThroughput(benchmarks);
        double avgLatency = calculateAverageLatency(benchmarks);

        // Calculate performance score using the new formula
        SummaryGenerator summaryGen = new SummaryGenerator();
        double performanceScore = summaryGen.calculatePerformanceScore(avgThroughput, avgLatency);
        String grade = summaryGen.getPerformanceGrade(performanceScore);

        overview.addProperty("avgThroughput", avgThroughput);
        overview.addProperty("avgThroughputFormatted", formatThroughput(avgThroughput));
        overview.addProperty("avgLatency", avgLatency);
        overview.addProperty("avgLatencyFormatted", formatLatency(avgLatency));
        overview.addProperty("performanceScore", Math.round(performanceScore));
        overview.addProperty("performanceGrade", grade);
        overview.addProperty("performanceGradeClass", getGradeClass(grade));

        return overview;
    }

    private JsonArray generateBenchmarkData(JsonArray benchmarks) {
        JsonArray formattedBenchmarks = new JsonArray();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            JsonObject formatted = new JsonObject();

            String fullName = benchmark.get("benchmark").getAsString();
            String displayName = fullName.substring(fullName.lastIndexOf('.') + 1);
            String mode = benchmark.get("mode").getAsString();

            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            double score = primaryMetric.get("score").getAsDouble();
            String unit = primaryMetric.get("scoreUnit").getAsString();

            formatted.addProperty("name", displayName);
            formatted.addProperty("fullName", fullName);
            formatted.addProperty("mode", mode);
            formatted.addProperty("score", score);
            formatted.addProperty("scoreFormatted", formatScore(score, unit));
            formatted.addProperty("unit", unit);

            // Add normalized values for comparison
            if ("thrpt".equals(mode) || unit.contains("ops")) {
                double opsPerSec = MetricConversionUtil.convertToOpsPerSecond(score, unit);
                formatted.addProperty("throughput", opsPerSec);
                formatted.addProperty("throughputFormatted", formatThroughput(opsPerSec));
            } else if (unit.contains("/op")) {
                double ms = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                formatted.addProperty("latency", ms);
                formatted.addProperty("latencyFormatted", formatLatency(ms));
            }

            // Add confidence interval if available
            if (primaryMetric.has("scoreConfidence")) {
                JsonArray confidence = primaryMetric.getAsJsonArray("scoreConfidence");
                formatted.add("confidence", confidence);
            }

            // Add percentiles if available
            if (primaryMetric.has("scorePercentiles")) {
                formatted.add("percentiles", primaryMetric.get("scorePercentiles"));
            }

            formattedBenchmarks.add(formatted);
        }

        return formattedBenchmarks;
    }

    private JsonObject generateChartData(JsonArray benchmarks) {
        JsonObject chartData = new JsonObject();

        List<String> labels = new ArrayList<>();
        List<Double> throughputData = new ArrayList<>();
        List<Double> latencyData = new ArrayList<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String name = benchmark.get("benchmark").getAsString();
            String mode = benchmark.get("mode").getAsString();

            // Extract method name
            String label = name.substring(name.lastIndexOf('.') + 1);

            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            double score = primaryMetric.get("score").getAsDouble();
            String unit = primaryMetric.get("scoreUnit").getAsString();

            if ("thrpt".equals(mode) || unit.contains("ops")) {
                labels.add(label);
                double opsPerSec = MetricConversionUtil.convertToOpsPerSecond(score, unit);
                throughputData.add(opsPerSec);
                latencyData.add(0.0); // No latency for throughput benchmarks
            } else if (unit.contains("/op")) {
                if (!labels.contains(label)) {
                    labels.add(label);
                    throughputData.add(0.0); // No throughput for latency benchmarks
                }
                double ms = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                int index = labels.indexOf(label);
                if (index >= 0 && index < latencyData.size()) {
                    latencyData.set(index, ms);
                } else {
                    latencyData.add(ms);
                }
            }
        }

        chartData.add("labels", GSON.toJsonTree(labels));
        chartData.add("throughput", GSON.toJsonTree(throughputData));
        chartData.add("latency", GSON.toJsonTree(latencyData));

        return chartData;
    }

    private JsonObject generatePercentilesData(JsonArray benchmarks) {
        JsonObject percentilesData = new JsonObject();

        List<String> labels = new ArrayList<>();
        Map<String, List<Double>> percentilesByBenchmark = new LinkedHashMap<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String name = benchmark.get("benchmark").getAsString();
            String label = name.substring(name.lastIndexOf('.') + 1);
            String mode = benchmark.get("mode").getAsString();

            // Only include latency benchmarks (avgt, sample, ss) in percentiles chart
            // Skip throughput benchmarks (thrpt) as their percentiles are in ops/s not latency
            if ("thrpt".equals(mode)) {
                continue; // Skip throughput benchmarks for percentiles chart
            }

            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");

            // Check if percentiles data exists
            if (primaryMetric.has("scorePercentiles")) {
                labels.add(label);
                JsonObject percentiles = primaryMetric.getAsJsonObject("scorePercentiles");
                String unit = primaryMetric.get("scoreUnit").getAsString();

                List<Double> values = new ArrayList<>();
                // Extract common percentiles: 0.0, 50.0, 90.0, 95.0, 99.0, 99.9, 100.0
                // Convert to milliseconds for consistent display
                values.add(convertPercentileToMs(percentiles, "0.0", unit));
                values.add(convertPercentileToMs(percentiles, "50.0", unit));
                values.add(convertPercentileToMs(percentiles, "90.0", unit));
                values.add(convertPercentileToMs(percentiles, "95.0", unit));
                values.add(convertPercentileToMs(percentiles, "99.0", unit));
                values.add(convertPercentileToMs(percentiles, "99.9", unit));
                values.add(convertPercentileToMs(percentiles, "100.0", unit));

                percentilesByBenchmark.put(label, values);
            }
        }

        percentilesData.add("benchmarks", GSON.toJsonTree(labels));
        percentilesData.add("percentileLabels", GSON.toJsonTree(
                List.of("Min", "P50", "P90", "P95", "P99", "P99.9", "Max")));
        percentilesData.add("data", GSON.toJsonTree(percentilesByBenchmark));

        return percentilesData;
    }

    private double convertPercentileToMs(JsonObject percentiles, String percentileKey, String unit) {
        if (!percentiles.has(percentileKey)) {
            return 0.0;
        }
        double value = percentiles.get(percentileKey).getAsDouble();
        return MetricConversionUtil.convertToMillisecondsPerOp(value, unit);
    }

    private JsonObject generateTrendData(JsonArray currentBenchmarks) {
        JsonObject trendData = new JsonObject();

        // This would normally load historical data from previous runs
        // For now, we'll create placeholder data structure
        JsonArray historicalRuns = new JsonArray();

        // Current run
        JsonObject currentRun = new JsonObject();
        currentRun.addProperty("timestamp", Instant.now().toString());
        currentRun.addProperty("avgThroughput", calculateAverageThroughput(currentBenchmarks));
        currentRun.addProperty("avgLatency", calculateAverageLatency(currentBenchmarks));

        historicalRuns.add(currentRun);

        trendData.add("runs", historicalRuns);
        trendData.addProperty("trendDirection", "stable"); // Would be calculated from historical data
        trendData.addProperty("changePercent", 0.0);

        return trendData;
    }

    private double calculateAverageThroughput(JsonArray benchmarks) {
        // Find the leading throughput measurement (measureThroughput)
        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String name = benchmark.get("benchmark").getAsString();
            if (name.contains("measureThroughput")) {
                JsonObject metric = benchmark.getAsJsonObject("primaryMetric");
                double score = metric.get("score").getAsDouble();
                String unit = metric.get("scoreUnit").getAsString();
                return MetricConversionUtil.convertToOpsPerSecond(score, unit);
            }
        }
        
        // Fallback to first throughput benchmark if no measureThroughput found
        return benchmarks.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(b -> {
                    String mode = b.get("mode").getAsString();
                    return "thrpt".equals(mode);
                })
                .mapToDouble(b -> {
                    JsonObject metric = b.getAsJsonObject("primaryMetric");
                    double score = metric.get("score").getAsDouble();
                    String unit = metric.get("scoreUnit").getAsString();
                    return MetricConversionUtil.convertToOpsPerSecond(score, unit);
                })
                .findFirst()
                .orElse(0.0);
    }

    private double calculateAverageLatency(JsonArray benchmarks) {
        return benchmarks.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .mapToDouble(b -> {
                    JsonObject metric = b.getAsJsonObject("primaryMetric");
                    double score = metric.get("score").getAsDouble();
                    String unit = metric.get("scoreUnit").getAsString();
                    return MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                })
                .filter(ms -> ms > 0)
                .average()
                .orElse(0.0);
    }

    private String getGradeClass(String grade) {
        return "grade-" + grade.toLowerCase().replace("+", "-plus");
    }

    private String formatScore(double score, String unit) {
        if (score >= 1_000_000) {
            return String.format(Locale.US, "%.1fM %s", score / 1_000_000, unit);
        } else if (score >= 1000) {
            return String.format(Locale.US, "%.1fK %s", score / 1000, unit);
        } else if (score >= 1) {
            return String.format(Locale.US, "%.1f %s", score, unit);
        }
        return String.format(Locale.US, "%.3f %s", score, unit);
    }

    private String formatThroughput(double throughput) {
        if (throughput == 0) return "N/A";
        if (throughput >= 1_000_000) {
            double value = throughput / 1_000_000;
            return "%sM ops/s".formatted(MetricConversionUtil.formatForDisplay(value));
        } else if (throughput >= 1000) {
            double value = throughput / 1000;
            return "%sK ops/s".formatted(MetricConversionUtil.formatForDisplay(value));
        }
        return "%s ops/s".formatted(MetricConversionUtil.formatForDisplay(throughput));
    }

    private String formatLatency(double latency) {
        if (latency == 0) return "N/A";
        if (latency >= 1000) {
            double value = latency / 1000;
            return "%s s".formatted(MetricConversionUtil.formatForDisplay(value));
        } else if (latency >= 1) {
            return "%s ms".formatted(MetricConversionUtil.formatForDisplay(latency));
        } else {
            return "%s ms".formatted(MetricConversionUtil.formatForDisplay(latency));
        }
    }
}