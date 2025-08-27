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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

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
    private static final String DATA_FILE_NAME = "benchmark-data.json";
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

        // Add trend data (placeholder for now - would load historical data in production)
        dataObject.add("trends", generateTrendData(benchmarks));

        // Write the consolidated data file
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);
        Path dataFile = outputPath.resolve(DATA_FILE_NAME);
        Files.writeString(dataFile, GSON.toJson(dataObject));
        
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
        String grade = MetricConversionUtil.calculatePerformanceGrade(avgThroughput);
        
        overview.addProperty("totalBenchmarks", totalBenchmarks);
        overview.addProperty("avgThroughput", avgThroughput);
        overview.addProperty("avgThroughputFormatted", formatThroughput(avgThroughput));
        overview.addProperty("avgLatency", avgLatency);
        overview.addProperty("avgLatencyFormatted", formatLatency(avgLatency));
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

    private JsonObject generateTrendData(JsonArray currentBenchmarks) {
        JsonObject trendData = new JsonObject();
        
        // This would normally load historical data from previous runs
        // For now, we'll create placeholder data structure
        JsonArray historicalRuns = new JsonArray();
        
        // Current run
        JsonObject currentRun = new JsonObject();
        currentRun.addProperty("timestamp", Instant.now().toString());
        currentRun.addProperty("totalBenchmarks", currentBenchmarks.size());
        currentRun.addProperty("avgThroughput", calculateAverageThroughput(currentBenchmarks));
        currentRun.addProperty("avgLatency", calculateAverageLatency(currentBenchmarks));
        
        historicalRuns.add(currentRun);
        
        trendData.add("runs", historicalRuns);
        trendData.addProperty("trendDirection", "stable"); // Would be calculated from historical data
        trendData.addProperty("changePercent", 0.0);
        
        return trendData;
    }

    private double calculateAverageThroughput(JsonArray benchmarks) {
        return benchmarks.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .mapToDouble(b -> {
                    JsonObject metric = b.getAsJsonObject("primaryMetric");
                    double score = metric.get("score").getAsDouble();
                    String unit = metric.get("scoreUnit").getAsString();
                    return MetricConversionUtil.convertToOpsPerSecond(score, unit);
                })
                .filter(ops -> ops > 0)
                .average()
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
            return "%.1fM %s".formatted(score / 1_000_000, unit);
        } else if (score >= 1000) {
            return "%.1fK %s".formatted(score / 1000, unit);
        } else if (score >= 1) {
            return "%.1f %s".formatted(score, unit);
        }
        return "%.3f %s".formatted(score, unit);
    }

    private String formatThroughput(double throughput) {
        if (throughput == 0) return "N/A";
        if (throughput >= 1_000_000) {
            return "%.1fM ops/s".formatted(throughput / 1_000_000);
        } else if (throughput >= 1000) {
            return "%.1fK ops/s".formatted(throughput / 1000);
        }
        return "%.1f ops/s".formatted(throughput);
    }

    private String formatLatency(double latency) {
        if (latency == 0) return "N/A";
        if (latency >= 1000) {
            return "%.1f s".formatted(latency / 1000);
        } else if (latency >= 1) {
            return "%.1f ms".formatted(latency);
        } else {
            return "%.2f ms".formatted(latency);
        }
    }
}