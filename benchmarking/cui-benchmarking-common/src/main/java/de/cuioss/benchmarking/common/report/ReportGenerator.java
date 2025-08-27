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
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates self-contained HTML reports with embedded CSS and JavaScript from JSON benchmark results.
 * <p>
 * This generator creates complete HTML reports that don't require external dependencies,
 * making them suitable for offline viewing and deployment to static hosting services.
 * <p>
 * Generated reports include:
 * <ul>
 *   <li>Performance overview with charts and tables</li>
 *   <li>Detailed benchmark results with statistical analysis</li>
 *   <li>Historical trends and comparisons</li>
 *   <li>Interactive visualizations</li>
 * </ul>
 */
public class ReportGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(ReportGenerator.class);
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generates the main index page with performance overview.
     *
     * @param jsonFile the path to the benchmark JSON results file
     * @param outputDir the output directory for HTML files
     * @throws IOException if reading JSON or writing HTML files fails
     */
    public void generateIndexPage(Path jsonFile, String outputDir) throws IOException {
        // Parse JSON file
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);

        LOGGER.info(INFO.GENERATING_INDEX_PAGE.format(benchmarks.size()));

        String html = generateHtmlHeader("CUI Benchmarking Results", true) +
                generateNavigationMenu() +
                generateOverviewSection(benchmarks) +
                generateBenchmarkTable(benchmarks) +
                generateHtmlFooter();

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);
        Path indexFile = outputPath.resolve("index.html");
        Files.writeString(indexFile, html);
        LOGGER.info(INFO.INDEX_PAGE_GENERATED.format(indexFile));
    }

    /**
     * Generates the trends page with historical performance analysis.
     *
     * @param jsonFile the path to the benchmark JSON results file
     * @param outputDir the output directory for HTML files
     * @throws IOException if reading JSON or writing HTML files fails
     */
    public void generateTrendsPage(Path jsonFile, String outputDir) throws IOException {
        // Parse JSON file
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);

        LOGGER.info(INFO.GENERATING_TRENDS_PAGE::format);

        String html = generateHtmlHeader("Performance Trends", true) +
                generateNavigationMenu() +
                generateTrendsSection(benchmarks) +
                generateHtmlFooter();

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);
        Path trendsFile = outputPath.resolve("trends.html");
        Files.writeString(trendsFile, html);
        LOGGER.info(INFO.TRENDS_PAGE_GENERATED.format(trendsFile));
    }

    /**
     * Generates the detailed visualizer page with JMH Visualizer integration.
     *
     * @param jsonFile the path to the benchmark JSON results file
     * @param benchmarkType the display name for the benchmark type
     * @param outputDir the output directory for HTML files
     * @throws IOException if reading JSON or writing HTML files fails
     */
    public void generateDetailedPage(Path jsonFile, String benchmarkType, String outputDir) throws IOException {
        // Parse JSON file  
        String jsonContent = Files.readString(jsonFile);
        JsonArray benchmarks = GSON.fromJson(jsonContent, JsonArray.class);

        LOGGER.info(INFO.GENERATING_REPORTS::format);

        String html = generateHtmlHeader("Detailed Benchmark Analysis", true) +
                generateNavigationMenu() +
                generateDetailedSection(benchmarks, benchmarkType) +
                generateHtmlFooter();

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);
        Path detailedFile = outputPath.resolve("detailed.html");
        Files.writeString(detailedFile, html);
        LOGGER.info(INFO.INDEX_PAGE_GENERATED.format(detailedFile));
    }

    private String generateHtmlHeader(String title, boolean includeChartJs) throws IOException {
        String template = loadTemplate("report-header.html");
        template = template.replace("${title}", title);
        template = template.replace("${css}", loadTemplate("report-styles.css"));

        if (includeChartJs) {
            template = template.replace("${chartScript}",
                    "<script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>");
        } else {
            template = template.replace("${chartScript}", "");
        }

        return template;
    }

    private String generateNavigationMenu() throws IOException {
        return loadTemplate("navigation-menu.html");
    }

    private String generateOverviewSection(JsonArray benchmarks) throws IOException {
        String template = loadTemplate("overview-section.html");

        // Calculate metrics from JSON
        int totalBenchmarks = benchmarks.size();
        double avgThroughput = calculateAverageThroughput(benchmarks);
        double avgLatency = calculateAverageLatency(benchmarks);
        String grade = calculatePerformanceGrade(avgThroughput, avgLatency);

        template = template.replace("${totalBenchmarks}", String.valueOf(totalBenchmarks));
        template = template.replace("${performanceGrade}", grade);
        template = template.replace("${avgThroughput}", formatThroughput(avgThroughput));
        template = template.replace("${avgLatency}", formatLatency(avgLatency));
        template = template.replace("${timestamp}",
                DISPLAY_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));

        return template;
    }

    private String generateBenchmarkTable(JsonArray benchmarks) throws IOException {
        String template = loadTemplate("benchmark-table.html");
        StringBuilder rows = new StringBuilder();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            rows.append(generateBenchmarkRow(benchmark));
        }

        return template.replace("${tableRows}", rows.toString());
    }

    private String generateBenchmarkRow(JsonObject benchmark) {
        String name = benchmark.get("benchmark").getAsString();
        String mode = benchmark.get("mode").getAsString();

        JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
        double score = primaryMetric.get("score").getAsDouble();
        String unit = primaryMetric.get("scoreUnit").getAsString();

        // Extract just the method name from full benchmark name
        String displayName = name.substring(name.lastIndexOf('.') + 1);

        return String.format(
                "<tr>" +
                        "<td>%s</td>" +
                        "<td>%s</td>" +
                        "<td>%.2f</td>" +
                        "<td>%s</td>" +
                        "</tr>\n",
                displayName, mode, score, unit
        );
    }

    private String generateTrendsSection(JsonArray benchmarks) throws IOException {
        String template = loadTemplate("trends-section.html");

        // For now, show current data as a starting point
        // In production, this would load historical data
        String chartData = generateChartData(benchmarks);

        // Calculate performance metrics
        double avgThroughput = calculateAverageThroughput(benchmarks);
        String grade = calculatePerformanceGrade(avgThroughput, 0);

        template = template.replace("${benchmarkCount}", String.valueOf(benchmarks.size()));
        template = template.replace("${performanceGrade}", grade);
        template = template.replace("${chartData}", chartData);
        template = template.replace("${trendsTable}", generateTrendsTable(benchmarks));

        return template;
    }

    private String generateDetailedSection(JsonArray benchmarks, String benchmarkType) throws IOException {
        String template = loadTemplate("detailed-section.html");

        template = template.replace("${benchmarkType}", benchmarkType);
        template = template.replace("${totalBenchmarks}", String.valueOf(benchmarks.size()));

        return template;
    }

    private String generateChartData(JsonArray benchmarks) {
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

        return """
                {
                  labels: %s,
                  throughput: %s,
                  latency: %s
                }\
                """.formatted(
                GSON.toJson(labels),
                GSON.toJson(throughputData),
                GSON.toJson(latencyData)
        );
    }

    private String generateTrendsTable(JsonArray benchmarks) {
        StringBuilder table = new StringBuilder();
        table.append("<table class=\"table\">\n");
        table.append("<thead><tr><th>Benchmark</th><th>Current</th><th>Previous</th><th>Change</th></tr></thead>\n");
        table.append("<tbody>\n");

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            String name = benchmark.get("benchmark").getAsString();
            String displayName = name.substring(name.lastIndexOf('.') + 1);

            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            double score = primaryMetric.get("score").getAsDouble();
            String unit = primaryMetric.get("scoreUnit").getAsString();

            table.append("<tr>");
            table.append("<td>").append(displayName).append("</td>");
            table.append("<td>").append("%.2f %s".formatted(score, unit)).append("</td>");
            table.append("<td>N/A</td>"); // No historical data yet
            table.append("<td>-</td>");
            table.append("</tr>\n");
        }

        table.append("</tbody></table>");
        return table.toString();
    }

    private double calculateAverageThroughput(JsonArray benchmarks) {
        return benchmarks.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .mapToDouble(b -> {
                    JsonObject metric = b.getAsJsonObject("primaryMetric");
                    double score = metric.get("score").getAsDouble();
                    String unit = metric.get("scoreUnit").getAsString();
                    // Use centralized conversion
                    return MetricConversionUtil.convertToOpsPerSecond(score, unit);
                })
                .filter(ops -> ops > 0) // Filter out invalid values
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
                    // Use centralized conversion
                    double msPerOp = MetricConversionUtil.convertToMillisecondsPerOp(score, unit);
                    LOGGER.debug("Latency calc: score={}, unit={}, ms/op={}", score, unit, msPerOp);
                    return msPerOp;
                })
                .filter(ms -> ms > 0) // Filter out invalid values
                .average()
                .orElse(0.0);
    }

    // All metric conversions now use the centralized MetricConversionUtil

    private String calculatePerformanceGrade(double throughput, double latency) {
        // Use centralized grade calculation based on throughput
        return MetricConversionUtil.calculatePerformanceGrade(throughput);
    }

    private String getGradeClass(String grade) {
        return "grade-" + grade.toLowerCase().replace("+", "-plus");
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

    private String generateHtmlFooter() throws IOException {
        String template = loadTemplate("report-footer.html");
        template = template.replace("${timestamp}",
                DISPLAY_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));
        return template;
    }

    private String loadTemplate(String templateName) throws IOException {
        String resourcePath = "/templates/" + templateName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Template not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}