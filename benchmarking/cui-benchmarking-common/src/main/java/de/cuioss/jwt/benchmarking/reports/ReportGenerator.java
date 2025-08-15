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
package de.cuioss.jwt.benchmarking.reports;

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generates self-contained HTML reports with embedded CSS and responsive design.
 * <p>
 * This generator creates comprehensive HTML reports that include:
 * <ul>
 *   <li>Index page with current benchmark results</li>
 *   <li>Trends page with historical performance data</li>
 *   <li>Embedded CSS for styling (no external dependencies)</li>
 *   <li>Responsive design for mobile and desktop viewing</li>
 * </ul>
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 */
public class ReportGenerator {

    private static final CuiLogger LOGGER = new CuiLogger(ReportGenerator.class);

    /**
     * Generates the main index page with current benchmark results.
     * 
     * @param results JMH benchmark results
     * @param outputDir directory to write the report
     * @throws IOException if report generation fails
     */
    public void generateIndexPage(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating index page with %d benchmark results", results.size());

        String html = generateIndexHtml(results);
        writeHtmlFile(html, outputDir + "/index.html");

        LOGGER.info("Index page generated successfully");
    }

    /**
     * Generates the trends page with historical performance data.
     * 
     * @param results JMH benchmark results
     * @param outputDir directory to write the report
     * @throws IOException if report generation fails
     */
    public void generateTrendsPage(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info("Generating trends page");

        String html = generateTrendsHtml(results);
        writeHtmlFile(html, outputDir + "/trends.html");

        LOGGER.info("Trends page generated successfully");
    }

    private String generateIndexHtml(Collection<RunResult> results) {
        StringBuilder html = new StringBuilder();
        
        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>JWT Validation Benchmark Results</title>
                <style>
            """);
        
        html.append(getEmbeddedCss());
        
        html.append("""
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>JWT Validation Benchmark Results</h1>
                        <p class="timestamp">Generated: """);
        
        html.append(getCurrentTimestamp());
        
        html.append("""
                        </p>
                    </header>
                    
                    <nav>
                        <a href="index.html" class="nav-link active">Current Results</a>
                        <a href="trends.html" class="nav-link">Performance Trends</a>
                    </nav>
                    
                    <main>
                        <section class="summary">
                            <h2>Summary</h2>
                            <div class="metrics-grid">
                                <div class="metric-card">
                                    <h3>Benchmarks</h3>
                                    <div class="metric-value">""");
        
        html.append(results.size());
        
        html.append("""
                                    </div>
                                </div>
                                <div class="metric-card">
                                    <h3>Average Score</h3>
                                    <div class="metric-value">""");
        
        html.append(formatAverageScore(results));
        
        html.append("""
                                    </div>
                                </div>
                            </div>
                        </section>
                        
                        <section class="results">
                            <h2>Detailed Results</h2>
                            <div class="results-table">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Benchmark</th>
                                            <th>Score</th>
                                            <th>Unit</th>
                                            <th>Mode</th>
                                        </tr>
                                    </thead>
                                    <tbody>
            """);
        
        for (RunResult result : results) {
            html.append(generateResultRow(result));
        }
        
        html.append("""
                                    </tbody>
                                </table>
                            </div>
                        </section>
                    </main>
                    
                    <footer>
                        <p>Generated by CUI JWT Benchmarking Infrastructure</p>
                    </footer>
                </div>
            </body>
            </html>
            """);
        
        return html.toString();
    }

    private String generateTrendsHtml(Collection<RunResult> results) {
        StringBuilder html = new StringBuilder();
        
        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>JWT Validation Performance Trends</title>
                <style>
            """);
        
        html.append(getEmbeddedCss());
        
        html.append("""
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>JWT Validation Performance Trends</h1>
                        <p class="timestamp">Generated: """);
        
        html.append(getCurrentTimestamp());
        
        html.append("""
                        </p>
                    </header>
                    
                    <nav>
                        <a href="index.html" class="nav-link">Current Results</a>
                        <a href="trends.html" class="nav-link active">Performance Trends</a>
                    </nav>
                    
                    <main>
                        <section class="trends">
                            <h2>Performance Trends</h2>
                            <p>Historical performance data and trend analysis will be displayed here.</p>
                            <p>This feature requires multiple benchmark runs to show meaningful trends.</p>
                        </section>
                    </main>
                    
                    <footer>
                        <p>Generated by CUI JWT Benchmarking Infrastructure</p>
                    </footer>
                </div>
            </body>
            </html>
            """);
        
        return html.toString();
    }

    private String getEmbeddedCss() {
        return """
            * {
                box-sizing: border-box;
                margin: 0;
                padding: 0;
            }
            
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                line-height: 1.6;
                color: #333;
                background-color: #f5f5f5;
            }
            
            .container {
                max-width: 1200px;
                margin: 0 auto;
                padding: 20px;
                background-color: white;
                min-height: 100vh;
                box-shadow: 0 0 10px rgba(0,0,0,0.1);
            }
            
            header {
                text-align: center;
                margin-bottom: 30px;
                padding-bottom: 20px;
                border-bottom: 2px solid #007acc;
            }
            
            h1 {
                color: #007acc;
                font-size: 2.5em;
                margin-bottom: 10px;
            }
            
            .timestamp {
                color: #666;
                font-style: italic;
            }
            
            nav {
                display: flex;
                justify-content: center;
                margin-bottom: 30px;
                gap: 20px;
            }
            
            .nav-link {
                padding: 10px 20px;
                text-decoration: none;
                color: #007acc;
                border: 2px solid #007acc;
                border-radius: 5px;
                transition: all 0.3s ease;
            }
            
            .nav-link:hover,
            .nav-link.active {
                background-color: #007acc;
                color: white;
            }
            
            .metrics-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 20px;
                margin-bottom: 30px;
            }
            
            .metric-card {
                background-color: #f8f9fa;
                padding: 20px;
                border-radius: 8px;
                text-align: center;
                border: 1px solid #dee2e6;
            }
            
            .metric-card h3 {
                color: #007acc;
                margin-bottom: 10px;
            }
            
            .metric-value {
                font-size: 2em;
                font-weight: bold;
                color: #28a745;
            }
            
            .results-table {
                overflow-x: auto;
            }
            
            table {
                width: 100%;
                border-collapse: collapse;
                margin-bottom: 30px;
            }
            
            th, td {
                padding: 12px;
                text-align: left;
                border-bottom: 1px solid #dee2e6;
            }
            
            th {
                background-color: #007acc;
                color: white;
                font-weight: 600;
            }
            
            tr:hover {
                background-color: #f8f9fa;
            }
            
            footer {
                text-align: center;
                margin-top: 40px;
                padding-top: 20px;
                border-top: 1px solid #dee2e6;
                color: #666;
            }
            
            @media (max-width: 768px) {
                .container {
                    padding: 10px;
                }
                
                h1 {
                    font-size: 2em;
                }
                
                .nav-link {
                    padding: 8px 15px;
                    font-size: 0.9em;
                }
                
                .metrics-grid {
                    grid-template-columns: 1fr;
                }
            }
            """;
    }

    private String getCurrentTimestamp() {
        return Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));
    }

    private String formatAverageScore(Collection<RunResult> results) {
        double average = results.stream()
                .mapToDouble(result -> result.getPrimaryResult().getScore())
                .average()
                .orElse(0.0);
        
        return String.format("%.2f", average);
    }

    private String generateResultRow(RunResult result) {
        String name = extractBenchmarkName(result.getParams().getBenchmark());
        double score = result.getPrimaryResult().getScore();
        String unit = result.getPrimaryResult().getUnit();
        String mode = result.getPrimaryResult().getLabel();
        
        return String.format("""
                <tr>
                    <td>%s</td>
                    <td>%.2f</td>
                    <td>%s</td>
                    <td>%s</td>
                </tr>
                """, name, score, unit, mode);
    }

    private String extractBenchmarkName(String fullBenchmarkName) {
        String[] parts = fullBenchmarkName.split("\\.");
        return parts[parts.length - 1];
    }

    private void writeHtmlFile(String html, String filepath) throws IOException {
        Path path = Paths.get(filepath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, html);
        
        LOGGER.debug("HTML report written to: %s", filepath);
    }
}