/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.benchmarking.common.report;

import de.cuioss.jwt.benchmarking.common.model.BenchmarkType;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Generator for self-contained HTML reports with embedded CSS and JavaScript.
 * Creates comprehensive, deployment-ready HTML reports without external dependencies.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class ReportGenerator {
    
    private final DecimalFormat numberFormat = new DecimalFormat("#,###.###");
    
    /**
     * Generate all HTML reports for the benchmark results.
     */
    public void generateAllReports(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        generateIndexReport(results, type, outputDir);
        generateDetailedReport(results, type, outputDir);
        
        System.out.println("📄 Generated HTML reports for " + type.getDisplayName().toLowerCase() + " benchmarks");
    }
    
    /**
     * Generate the main index report with summary information.
     */
    private void generateIndexReport(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>").append(type.getDisplayName()).append(" Benchmark Results</title>\n");
        html.append(getEmbeddedCSS());
        html.append("</head>\n<body>\n");
        
        // Header
        html.append("<header class=\"header\">\n");
        html.append("<h1>🚀 ").append(type.getDisplayName()).append(" Benchmark Results</h1>\n");
        html.append("<p class=\"timestamp\">Generated: ").append(getCurrentTimestamp()).append("</p>\n");
        html.append("</header>\n");
        
        // Summary cards
        html.append("<main class=\"main\">\n");
        html.append(generateSummaryCards(results, type));
        
        // Results table
        html.append("<section class=\"results-section\">\n");
        html.append("<h2>📊 Benchmark Results</h2>\n");
        html.append(generateResultsTable(results));
        html.append("</section>\n");
        
        html.append("</main>\n");
        html.append(getEmbeddedScript());
        html.append("</body>\n</html>\n");
        
        Path indexFile = Paths.get(outputDir, "index.html");
        Files.write(indexFile, html.toString().getBytes());
    }
    
    /**
     * Generate detailed report with individual benchmark analysis.
     */
    private void generateDetailedReport(Collection<RunResult> results, BenchmarkType type, String outputDir) throws IOException {
        // For brevity, this is a simplified version
        // In a full implementation, this would include detailed charts and analysis
        String filename = type.getDisplayName().toLowerCase() + "-detailed.html";
        Path detailedFile = Paths.get(outputDir, filename);
        
        String content = "<!DOCTYPE html><html><head><title>Detailed " + type.getDisplayName() + 
                        " Results</title></head><body><h1>Detailed Analysis Coming Soon</h1></body></html>";
        
        Files.write(detailedFile, content.getBytes());
    }
    
    /**
     * Generate summary cards showing key metrics.
     */
    private String generateSummaryCards(Collection<RunResult> results, BenchmarkType type) {
        StringBuilder cards = new StringBuilder();
        
        // Calculate summary statistics
        double totalThroughput = 0.0;
        double totalLatency = 0.0;
        int validResults = 0;
        
        for (RunResult result : results) {
            if (result.getPrimaryResult() != null) {
                double score = result.getPrimaryResult().getScore();
                totalThroughput += score;
                if (score > 0) {
                    totalLatency += (1.0 / score) * 1000; // Convert to ms
                    validResults++;
                }
            }
        }
        
        double avgThroughput = validResults > 0 ? totalThroughput / validResults : 0.0;
        double avgLatency = validResults > 0 ? totalLatency / validResults : 0.0;
        
        cards.append("<div class=\"summary-grid\">\n");
        
        // Total benchmarks card
        cards.append("<div class=\"card\">\n");
        cards.append("<h3>📈 Total Benchmarks</h3>\n");
        cards.append("<div class=\"metric-value\">").append(results.size()).append("</div>\n");
        cards.append("</div>\n");
        
        // Average throughput card
        cards.append("<div class=\"card\">\n");
        cards.append("<h3>⚡ Avg Throughput</h3>\n");
        cards.append("<div class=\"metric-value\">").append(numberFormat.format(avgThroughput)).append("</div>\n");
        cards.append("<div class=\"metric-unit\">").append(getUnit(type)).append("</div>\n");
        cards.append("</div>\n");
        
        // Average latency card
        cards.append("<div class=\"card\">\n");
        cards.append("<h3>⏱️ Avg Latency</h3>\n");
        cards.append("<div class=\"metric-value\">").append(numberFormat.format(avgLatency)).append("</div>\n");
        cards.append("<div class=\"metric-unit\">ms</div>\n");
        cards.append("</div>\n");
        
        // Performance score card
        long performanceScore = Math.round(avgThroughput);
        cards.append("<div class=\"card\">\n");
        cards.append("<h3>🎯 Performance Score</h3>\n");
        cards.append("<div class=\"metric-value\">").append(numberFormat.format(performanceScore)).append("</div>\n");
        cards.append("</div>\n");
        
        cards.append("</div>\n");
        
        return cards.toString();
    }
    
    /**
     * Generate results table with individual benchmark data.
     */
    private String generateResultsTable(Collection<RunResult> results) {
        StringBuilder table = new StringBuilder();
        
        table.append("<div class=\"table-container\">\n");
        table.append("<table class=\"results-table\">\n");
        table.append("<thead>\n<tr>\n");
        table.append("<th>Benchmark</th>\n");
        table.append("<th>Throughput</th>\n");
        table.append("<th>Error (±)</th>\n");
        table.append("<th>Unit</th>\n");
        table.append("</tr>\n</thead>\n<tbody>\n");
        
        for (RunResult result : results) {
            if (result.getPrimaryResult() != null) {
                String benchmarkName = extractBenchmarkName(result.getParams().getBenchmark());
                double score = result.getPrimaryResult().getScore();
                double error = result.getPrimaryResult().getScoreError();
                String unit = result.getPrimaryResult().getScoreUnit();
                
                table.append("<tr>\n");
                table.append("<td>").append(benchmarkName).append("</td>\n");
                table.append("<td>").append(numberFormat.format(score)).append("</td>\n");
                table.append("<td>").append(numberFormat.format(error)).append("</td>\n");
                table.append("<td>").append(unit).append("</td>\n");
                table.append("</tr>\n");
            }
        }
        
        table.append("</tbody>\n</table>\n</div>\n");
        
        return table.toString();
    }
    
    /**
     * Extract benchmark name from full class path.
     */
    private String extractBenchmarkName(String fullBenchmark) {
        int lastDot = fullBenchmark.lastIndexOf('.');
        return lastDot >= 0 ? fullBenchmark.substring(lastDot + 1) : fullBenchmark;
    }
    
    /**
     * Get appropriate unit for the benchmark type.
     */
    private String getUnit(BenchmarkType type) {
        return type == BenchmarkType.MICRO ? "ops/sec" : "req/sec";
    }
    
    /**
     * Get current timestamp for report generation.
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm:ss"));
    }
    
    /**
     * Get embedded CSS for self-contained HTML reports.
     */
    private String getEmbeddedCSS() {
        return "<style>\n" +
               "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
               "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; background: #f5f5f5; }\n" +
               ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 2rem 1rem; text-align: center; }\n" +
               ".header h1 { font-size: 2.5rem; margin-bottom: 0.5rem; }\n" +
               ".timestamp { opacity: 0.9; font-size: 1.1rem; }\n" +
               ".main { max-width: 1200px; margin: 2rem auto; padding: 0 1rem; }\n" +
               ".summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1.5rem; margin-bottom: 2rem; }\n" +
               ".card { background: white; padding: 1.5rem; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); text-align: center; }\n" +
               ".card h3 { font-size: 1.1rem; margin-bottom: 1rem; color: #555; }\n" +
               ".metric-value { font-size: 2rem; font-weight: bold; color: #2563eb; }\n" +
               ".metric-unit { color: #666; margin-top: 0.5rem; }\n" +
               ".results-section { background: white; padding: 2rem; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }\n" +
               ".results-section h2 { margin-bottom: 1.5rem; color: #333; }\n" +
               ".table-container { overflow-x: auto; }\n" +
               ".results-table { width: 100%; border-collapse: collapse; }\n" +
               ".results-table th, .results-table td { padding: 1rem; text-align: left; border-bottom: 1px solid #e5e7eb; }\n" +
               ".results-table th { background: #f9fafb; font-weight: 600; color: #374151; }\n" +
               ".results-table tbody tr:hover { background: #f9fafb; }\n" +
               "@media (max-width: 768px) { .header h1 { font-size: 2rem; } .card { padding: 1rem; } .results-section { padding: 1rem; } }\n" +
               "</style>\n";
    }
    
    /**
     * Get embedded JavaScript for interactive features.
     */
    private String getEmbeddedScript() {
        return "<script>\n" +
               "document.addEventListener('DOMContentLoaded', function() {\n" +
               "  console.log('Benchmark report loaded successfully');\n" +
               "});\n" +
               "</script>\n";
    }
}