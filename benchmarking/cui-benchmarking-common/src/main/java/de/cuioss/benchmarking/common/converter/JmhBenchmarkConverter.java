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
package de.cuioss.benchmarking.common.converter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.benchmarking.common.model.BenchmarkData;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Converts JMH benchmark results to the central BenchmarkData model
 */
public class JmhBenchmarkConverter implements BenchmarkConverter {

    private static final CuiLogger LOGGER = new CuiLogger(JmhBenchmarkConverter.class);
    private static final Gson GSON = new Gson();

    private final BenchmarkType benchmarkType;

    public JmhBenchmarkConverter(BenchmarkType benchmarkType) {
        this.benchmarkType = benchmarkType;
    }

    @Override public BenchmarkData convert(Path sourcePath) throws IOException {
        String json = Files.readString(sourcePath);
        JsonArray jmhResults = GSON.fromJson(json, JsonArray.class);

        List<BenchmarkData.Benchmark> benchmarks = new ArrayList<>();

        for (JsonElement element : jmhResults) {
            JsonObject jmhBenchmark = element.getAsJsonObject();
            benchmarks.add(convertJmhBenchmark(jmhBenchmark));
        }

        return BenchmarkData.builder()
                .metadata(createMetadata())
                .overview(createOverview(benchmarks))
                .benchmarks(benchmarks)
                .build();
    }

    @Override public boolean canConvert(Path sourcePath) {
        return sourcePath.getFileName().toString().endsWith(".json") &&
                (sourcePath.getFileName().toString().contains("jmh") ||
                        sourcePath.getFileName().toString().contains("result"));
    }

    private BenchmarkData.Benchmark convertJmhBenchmark(JsonObject jmh) {
        String name = jmh.get("benchmark").getAsString();
        String mode = jmh.get("mode").getAsString();

        JsonObject primaryMetric = jmh.getAsJsonObject("primaryMetric");
        double score = primaryMetric.get("score").getAsDouble();
        String scoreUnit = primaryMetric.get("scoreUnit").getAsString();

        // Convert units for better readability
        double convertedScore = score;
        String convertedUnit = scoreUnit;

        // Convert ops/ms to ops/s for throughput benchmarks
        if ("thrpt".equals(mode) && "ops/ms".equals(scoreUnit)) {
            convertedScore = score * 1000; // Convert ops/ms to ops/s
            convertedUnit = "ops/s";
        }

        Map<String, Double> percentiles = new LinkedHashMap<>();
        if (primaryMetric.has("scorePercentiles")) {
            JsonObject scorePercentiles = primaryMetric.getAsJsonObject("scorePercentiles");
            for (Map.Entry<String, JsonElement> entry : scorePercentiles.entrySet()) {
                double percentileValue = entry.getValue().getAsDouble();
                // Apply same conversion to percentiles
                if ("thrpt".equals(mode) && "ops/ms".equals(scoreUnit)) {
                    percentileValue = percentileValue * 1000;
                }
                percentiles.put(entry.getKey(), percentileValue);
            }
        }

        return BenchmarkData.Benchmark.builder()
                .name(extractSimpleName(name))
                .fullName(name)
                .mode(mode)
                .rawScore(convertedScore)
                .score(formatScore(convertedScore, convertedUnit))
                .scoreUnit(convertedUnit)
                .throughput("thrpt".equals(mode) ? formatScore(convertedScore, convertedUnit) : null)
                .latency("avgt".equals(mode) || "sample".equals(mode) ? formatScore(score, scoreUnit) : null)
                .error(primaryMetric.has("scoreError") ? primaryMetric.get("scoreError").getAsDouble() : 0.0)
                .percentiles(percentiles)
                .build();
    }

    private BenchmarkData.Metadata createMetadata() {
        Instant now = Instant.now();
        return BenchmarkData.Metadata.builder()
                .timestamp(now.toString())
                .displayTimestamp(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                        .withZone(ZoneOffset.UTC)
                        .format(now))
                .benchmarkType(benchmarkType.getDisplayName())
                .reportVersion("2.0")
                .build();
    }

    private BenchmarkData.Overview createOverview(List<BenchmarkData.Benchmark> benchmarks) {
        // Find best throughput and latency benchmarks
        Optional<BenchmarkData.Benchmark> bestThroughput = benchmarks.stream()
                .filter(b -> "thrpt".equals(b.getMode()))
                .max(Comparator.comparing(BenchmarkData.Benchmark::getRawScore));

        Optional<BenchmarkData.Benchmark> bestLatency = benchmarks.stream()
                .filter(b -> "avgt".equals(b.getMode()) || "sample".equals(b.getMode()))
                .min(Comparator.comparing(BenchmarkData.Benchmark::getRawScore));

        double throughput = bestThroughput.map(BenchmarkData.Benchmark::getRawScore).orElse(0.0);
        double latency = bestLatency.map(BenchmarkData.Benchmark::getRawScore).orElse(0.0);

        int score = calculatePerformanceScore(throughput, latency);
        String grade = calculatePerformanceGrade(score);

        return BenchmarkData.Overview.builder()
                .throughput(bestThroughput.map(BenchmarkData.Benchmark::getScore).orElse("N/A"))
                .latency(bestLatency.map(BenchmarkData.Benchmark::getScore).orElse("N/A"))
                .throughputBenchmarkName(bestThroughput.map(BenchmarkData.Benchmark::getName).orElse(""))
                .latencyBenchmarkName(bestLatency.map(BenchmarkData.Benchmark::getName).orElse(""))
                .performanceScore(score)
                .performanceGrade(grade)
                .performanceGradeClass("grade-" + grade.toLowerCase())
                .build();
    }

    private String extractSimpleName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    private String formatScore(double score, String unit) {
        if (score >= 1000) {
            return String.format(Locale.GERMAN, "%.1fK %s", score / 1000, unit);
        }
        return String.format(Locale.GERMAN, "%.1f %s", score, unit);
    }

    private int calculatePerformanceScore(double throughput, double latency) {
        // Simple scoring: higher throughput and lower latency = better score
        double throughputScore = Math.min(100, throughput / 100);
        double latencyScore = Math.max(0, 100 - latency);
        return (int) ((throughputScore + latencyScore) / 2);
    }

    private String calculatePerformanceGrade(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }
}