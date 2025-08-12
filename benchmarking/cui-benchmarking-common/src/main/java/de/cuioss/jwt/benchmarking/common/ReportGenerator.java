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
package de.cuioss.jwt.benchmarking.common;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generates self-contained HTML reports with embedded CSS and data.
 * <p>
 * Creates responsive HTML reports with embedded styling and JavaScript
 * for visualization of benchmark results without external dependencies.
 * </p>
 * 
 * @since 1.0
 */
public final class ReportGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(ReportGenerator.class);

    /**
     * Generates the main index page with benchmark results.
     *
     * @param results   the benchmark results
     * @param outputDir the output directory
     * @throws IOException if file operations fail
     */
    public void generateIndexPage(Collection<RunResult> results, String outputDir) throws IOException {
        var html = createIndexHtml(results);
        var filePath = outputDir + "/index.html";
        Files.writeString(Paths.get(filePath), html);
        LOGGER.info("Generated index page: %s", filePath);
    }

    /**
     * Generates the trends page with historical analysis.
     *
     * @param results   the benchmark results
     * @param outputDir the output directory
     * @throws IOException if file operations fail
     */
    public void generateTrendsPage(Collection<RunResult> results, String outputDir) throws IOException {
        var html = createTrendsHtml(results);
        var filePath = outputDir + "/trends.html";
        Files.writeString(Paths.get(filePath), html);
        LOGGER.info("Generated trends page: %s", filePath);
    }

    private String createIndexHtml(Collection<RunResult> results) {
        var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Benchmark Results</title>
                <style>
                    %s
                </style>
            </head>
            <body>
                <header>
                    <h1>JWT Validation Benchmark Results</h1>
                    <p>Generated on %s</p>
                </header>
                
                <main>
                    <section class="summary">
                        <h2>Summary</h2>
                        %s
                    </section>
                    
                    <section class="results">
                        <h2>Detailed Results</h2>
                        %s
                    </section>
                </main>
                
                <script>
                    %s
                </script>
            </body>
            </html>
            """.formatted(
                getEmbeddedCss(),
                timestamp,
                createSummarySection(results),
                createResultsSection(results),
                getEmbeddedJavaScript()
            );
    }

    private String createTrendsHtml(Collection<RunResult> results) {
        var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Performance Trends</title>
                <style>
                    %s
                </style>
            </head>
            <body>
                <header>
                    <h1>Performance Trends</h1>
                    <p>Generated on %s</p>
                </header>
                
                <main>
                    <section class="trends">
                        <h2>Trend Analysis</h2>
                        %s
                    </section>
                </main>
                
                <script>
                    %s
                </script>
            </body>
            </html>
            """.formatted(
                getEmbeddedCss(),
                timestamp,
                createTrendsSection(results),
                getEmbeddedJavaScript()
            );
    }

    private String createSummarySection(Collection<RunResult> results) {
        if (results.isEmpty()) {
            return "<p>No benchmark results available.</p>";
        }

        var avgScore = results.stream()
            .mapToDouble(r -> r.getPrimaryResult().getScore())
            .average()
            .orElse(0.0);

        return """
            <div class="summary-grid">
                <div class="metric">
                    <h3>Benchmarks</h3>
                    <span class="value">%d</span>
                </div>
                <div class="metric">
                    <h3>Average Score</h3>
                    <span class="value">%.2f ops/μs</span>
                </div>
                <div class="metric">
                    <h3>Performance Grade</h3>
                    <span class="value grade-%s">%s</span>
                </div>
            </div>
            """.formatted(
                results.size(),
                avgScore,
                getPerformanceGrade(avgScore).toLowerCase(),
                getPerformanceGrade(avgScore)
            );
    }

    private String createResultsSection(Collection<RunResult> results) {
        if (results.isEmpty()) {
            return "<p>No detailed results available.</p>";
        }

        var tableRows = results.stream()
            .map(this::createResultRow)
            .reduce("", String::concat);

        return """
            <table class="results-table">
                <thead>
                    <tr>
                        <th>Benchmark</th>
                        <th>Score</th>
                        <th>Error</th>
                        <th>Unit</th>
                        <th>Samples</th>
                    </tr>
                </thead>
                <tbody>
                    %s
                </tbody>
            </table>
            """.formatted(tableRows);
    }

    private String createResultRow(RunResult result) {
        var primaryResult = result.getPrimaryResult();
        var benchmarkName = result.getParams().getBenchmark()
            .replaceAll(".*\\.", ""); // Keep only class name
        
        return """
            <tr>
                <td>%s</td>
                <td>%.2f</td>
                <td>±%.2f</td>
                <td>%s</td>
                <td>%d</td>
            </tr>
            """.formatted(
                benchmarkName,
                primaryResult.getScore(),
                primaryResult.getScoreError(),
                primaryResult.getScoreUnit(),
                primaryResult.getSampleCount()
            );
    }

    private String createTrendsSection(Collection<RunResult> results) {
        return """
            <div class="trends-content">
                <p>Performance trend analysis will be available after multiple benchmark runs.</p>
                <p>Current run establishes baseline metrics for future comparisons.</p>
            </div>
            """;
    }

    private String getPerformanceGrade(double score) {
        if (score >= 100.0) return "A+";
        if (score >= 50.0) return "A";
        if (score >= 25.0) return "B";
        if (score >= 10.0) return "C";
        return "D";
    }

    private String getEmbeddedCss() {
        return """
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                line-height: 1.6;
                color: #333;
                max-width: 1200px;
                margin: 0 auto;
                padding: 20px;
                background-color: #f5f5f5;
            }
            
            header {
                text-align: center;
                margin-bottom: 40px;
                padding: 20px;
                background: white;
                border-radius: 8px;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            
            h1 { color: #2c3e50; margin: 0; }
            h2 { color: #34495e; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
            
            .summary-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 20px;
                margin-bottom: 40px;
            }
            
            .metric {
                background: white;
                padding: 20px;
                border-radius: 8px;
                text-align: center;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            
            .metric h3 { margin: 0 0 10px 0; color: #7f8c8d; font-size: 14px; }
            .value { font-size: 24px; font-weight: bold; color: #2c3e50; }
            
            .grade-a\\+ { color: #27ae60; }
            .grade-a { color: #2ecc71; }
            .grade-b { color: #f39c12; }
            .grade-c { color: #e67e22; }
            .grade-d { color: #e74c3c; }
            
            .results-table {
                width: 100%;
                border-collapse: collapse;
                background: white;
                border-radius: 8px;
                overflow: hidden;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            
            .results-table th,
            .results-table td {
                padding: 12px;
                text-align: left;
                border-bottom: 1px solid #ecf0f1;
            }
            
            .results-table th {
                background-color: #3498db;
                color: white;
                font-weight: 600;
            }
            
            .results-table tr:hover {
                background-color: #f8f9fa;
            }
            
            .trends-content {
                background: white;
                padding: 40px;
                border-radius: 8px;
                text-align: center;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            
            @media (max-width: 768px) {
                .summary-grid { grid-template-columns: 1fr; }
                .results-table { font-size: 14px; }
                .results-table th, .results-table td { padding: 8px; }
            }
            """;
    }

    private String getEmbeddedJavaScript() {
        return """
            // Add interactive features if needed
            document.addEventListener('DOMContentLoaded', function() {
                console.log('Benchmark report loaded');
            });
            """;
    }
}