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
package de.cuioss.benchmarking.report;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generates HTML reports with embedded performance data for GitHub Pages deployment.
 * <p>
 * This generator creates self-contained HTML reports that can be deployed to GitHub Pages
 * without requiring external JavaScript or CSS dependencies.
 *
 * @since 1.0.0
 */
public class ReportGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(ReportGenerator.class);

    /**
     * Generates the main index page with current benchmark results.
     *
     * @param results the benchmark results
     * @param outputDir the output directory
     * @throws IOException if report generation fails
     */
    public void generateIndexPage(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating index page with %d benchmark results", results.size());
        
        StringBuilder html = new StringBuilder();
        html.append(getHtmlHeader("JWT Benchmark Results"));
        html.append(getNavigationHtml());
        html.append(generateBenchmarkResultsSection(results));
        html.append(getHtmlFooter());

        Path indexFile = Paths.get(outputDir, "index.html");
        Files.write(indexFile, html.toString().getBytes());
        
        LOGGER.info("Generated index page: %s", indexFile);
    }

    /**
     * Generates the trends page showing performance history.
     *
     * @param results the current benchmark results
     * @param outputDir the output directory
     * @throws IOException if report generation fails
     */
    public void generateTrendsPage(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating trends page");
        
        StringBuilder html = new StringBuilder();
        html.append(getHtmlHeader("Performance Trends"));
        html.append(getNavigationHtml());
        html.append(generateTrendsSection(results));
        html.append(getHtmlFooter());

        Path trendsFile = Paths.get(outputDir, "trends.html");
        Files.write(trendsFile, html.toString().getBytes());
        
        LOGGER.info("Generated trends page: %s", trendsFile);
    }

    /**
     * Generates the HTML header with embedded CSS.
     */
    private String getHtmlHeader(String title) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        line-height: 1.6;
                        margin: 0;
                        padding: 0;
                        background-color: #f5f5f5;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .header {
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        color: white;
                        padding: 30px 0;
                        margin-bottom: 30px;
                        text-align: center;
                    }
                    .nav {
                        background: white;
                        padding: 15px;
                        margin-bottom: 30px;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .nav a {
                        color: #667eea;
                        text-decoration: none;
                        margin-right: 20px;
                        font-weight: 500;
                    }
                    .nav a:hover {
                        text-decoration: underline;
                    }
                    .card {
                        background: white;
                        border-radius: 8px;
                        padding: 20px;
                        margin-bottom: 20px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .metric {
                        display: inline-block;
                        background: #f8f9fa;
                        padding: 10px 15px;
                        margin: 5px;
                        border-radius: 5px;
                        border-left: 4px solid #667eea;
                    }
                    .metric-value {
                        font-size: 1.5em;
                        font-weight: bold;
                        color: #333;
                    }
                    .metric-label {
                        font-size: 0.9em;
                        color: #666;
                    }
                    .timestamp {
                        color: #666;
                        font-size: 0.9em;
                    }
                    table {
                        width: 100%%;
                        border-collapse: collapse;
                        margin-top: 15px;
                    }
                    th, td {
                        text-align: left;
                        padding: 12px;
                        border-bottom: 1px solid #ddd;
                    }
                    th {
                        background-color: #f8f9fa;
                        font-weight: 600;
                    }
                    .footer {
                        text-align: center;
                        padding: 20px;
                        color: #666;
                        font-size: 0.9em;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="container">
                        <h1>%s</h1>
                        <p class="timestamp">Generated: %s</p>
                    </div>
                </div>
                <div class="container">
            """.formatted(title, title, getCurrentTimestamp());
    }

    /**
     * Generates navigation HTML.
     */
    private String getNavigationHtml() {
        return """
            <nav class="nav">
                <a href="index.html">Current Results</a>
                <a href="trends.html">Performance Trends</a>
                <a href="data/metrics.json">Raw Data (JSON)</a>
            </nav>
            """;
    }

    /**
     * Generates the benchmark results section.
     */
    private String generateBenchmarkResultsSection(Collection<RunResult> results) {
        StringBuilder html = new StringBuilder();
        
        html.append("<div class=\"card\">\n");
        html.append("<h2>Benchmark Summary</h2>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-value\">").append(results.size()).append("</div>\n");
        html.append("<div class=\"metric-label\">Benchmarks Executed</div>\n");
        html.append("</div>\n");

        // Calculate summary metrics
        double avgThroughput = calculateAverageThroughput(results);
        double avgLatency = calculateAverageLatency(results);

        if (avgThroughput > 0) {
            html.append("<div class=\"metric\">\n");
            html.append("<div class=\"metric-value\">").append(formatThroughput(avgThroughput)).append("</div>\n");
            html.append("<div class=\"metric-label\">Average Throughput (ops/s)</div>\n");
            html.append("</div>\n");
        }

        if (avgLatency > 0) {
            html.append("<div class=\"metric\">\n");
            html.append("<div class=\"metric-value\">").append(String.format("%.2f", avgLatency)).append(" ms</div>\n");
            html.append("<div class=\"metric-label\">Average Latency</div>\n");
            html.append("</div>\n");
        }

        html.append("</div>\n");

        // Add detailed results table
        html.append("<div class=\"card\">\n");
        html.append("<h2>Detailed Results</h2>\n");
        html.append("<table>\n");
        html.append("<thead>\n");
        html.append("<tr><th>Benchmark</th><th>Mode</th><th>Score</th><th>Unit</th><th>Samples</th></tr>\n");
        html.append("</thead>\n");
        html.append("<tbody>\n");

        for (RunResult result : results) {
            String benchmarkName = extractSimpleName(result.getParams().getBenchmark());
            String mode = result.getParams().getMode().shortLabel();
            
            if (result.getPrimaryResult() != null) {
                double score = result.getPrimaryResult().getScore();
                String unit = result.getPrimaryResult().getScoreUnit();
                long samples = result.getPrimaryResult().getStatistics() != null ? 
                               result.getPrimaryResult().getStatistics().getN() : 0;

                html.append("<tr>\n");
                html.append("<td>").append(benchmarkName).append("</td>\n");
                html.append("<td>").append(mode).append("</td>\n");
                html.append("<td>").append(String.format("%.2f", score)).append("</td>\n");
                html.append("<td>").append(unit).append("</td>\n");
                html.append("<td>").append(samples).append("</td>\n");
                html.append("</tr>\n");
            }
        }

        html.append("</tbody>\n");
        html.append("</table>\n");
        html.append("</div>\n");

        return html.toString();
    }

    /**
     * Generates the trends section.
     */
    private String generateTrendsSection(Collection<RunResult> results) {
        StringBuilder html = new StringBuilder();
        
        html.append("<div class=\"card\">\n");
        html.append("<h2>Performance Trends</h2>\n");
        html.append("<p>Historical performance data will be displayed here once multiple benchmark runs are available.</p>\n");
        html.append("<p>Current run provides the baseline for future trend analysis.</p>\n");
        html.append("</div>\n");

        return html.toString();
    }

    /**
     * Generates the HTML footer.
     */
    private String getHtmlFooter() {
        return """
                </div>
                <div class="footer">
                    <p>Generated by CUI Benchmarking Infrastructure</p>
                </div>
            </body>
            </html>
            """;
    }

    private String getCurrentTimestamp() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    private String extractSimpleName(String fullName) {
        if (fullName == null) return "unknown";
        String[] parts = fullName.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return fullName;
    }

    private double calculateAverageThroughput(Collection<RunResult> results) {
        return results.stream()
                .filter(result -> result.getPrimaryResult() != null)
                .filter(result -> isThroughputMetric(result.getPrimaryResult().getScoreUnit()))
                .mapToDouble(result -> convertToOpsPerSecond(result.getPrimaryResult().getScore(), 
                                                            result.getPrimaryResult().getScoreUnit()))
                .average()
                .orElse(0.0);
    }

    private double calculateAverageLatency(Collection<RunResult> results) {
        return results.stream()
                .filter(result -> result.getPrimaryResult() != null)
                .filter(result -> isLatencyMetric(result.getPrimaryResult().getScoreUnit()))
                .mapToDouble(result -> convertToMilliseconds(result.getPrimaryResult().getScore(), 
                                                           result.getPrimaryResult().getScoreUnit()))
                .average()
                .orElse(0.0);
    }

    private String formatThroughput(double throughput) {
        if (throughput >= 1_000_000) {
            return String.format("%.1fM", throughput / 1_000_000);
        } else if (throughput >= 1_000) {
            return String.format("%.1fK", throughput / 1_000);
        } else {
            return String.format("%.0f", throughput);
        }
    }

    private double convertToMilliseconds(double value, String unit) {
        switch (unit.toLowerCase()) {
            case "ns/op": return value / 1_000_000.0;
            case "us/op": return value / 1_000.0;
            case "ms/op": return value;
            case "s/op": return value * 1_000.0;
            default: return value;
        }
    }

    private double convertToOpsPerSecond(double value, String unit) {
        switch (unit.toLowerCase()) {
            case "ops/ns": return value * 1_000_000_000.0;
            case "ops/us": return value * 1_000_000.0;
            case "ops/ms": return value * 1_000.0;
            case "ops/s": return value;
            default: return value;
        }
    }

    private boolean isLatencyMetric(String unit) {
        return unit.contains("/op");
    }

    private boolean isThroughputMetric(String unit) {
        return unit.startsWith("ops/");
    }
}