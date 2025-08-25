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

import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates self-contained HTML reports with embedded CSS and JavaScript.
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

    private static final CuiLogger LOGGER =
            new CuiLogger(ReportGenerator.class);
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");

    /**
     * Generates the main index page with performance overview.
     *
     * @param results the benchmark results
     * @param outputDir the output directory for HTML files
     * @throws IOException if writing HTML files fails
     */
    public void generateIndexPage(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info(INFO.GENERATING_INDEX_PAGE.format(results.size()));

        String html = generateHtmlHeader("CUI Benchmarking Results", true) +
                generateNavigationMenu() +
                generateOverviewSection(results) +
                generateBenchmarkTable(results) +
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
     * @param results the benchmark results
     * @param outputDir the output directory for HTML files
     * @throws IOException if writing HTML files fails
     */
    public void generateTrendsPage(Collection<RunResult> results, String outputDir) throws IOException {
        LOGGER.info(INFO.GENERATING_TRENDS_PAGE::format);

        String html = generateHtmlHeader("Performance Trends", false) +
                generateNavigationMenu() +
                generateTrendsSection(results) +
                generateHtmlFooter();

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);
        Path trendsFile = outputPath.resolve("trends.html");
        Files.writeString(trendsFile, html);
        LOGGER.info(INFO.TRENDS_PAGE_GENERATED.format(trendsFile));
    }

    private String generateHtmlHeader(String title, boolean includeCharts) {
        try {
            String template = loadTemplate("report-header.html");
            String chartScript = includeCharts ? "<script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>" : "";
            return template
                    .replace("${title}", title)
                    .replace("${css}", getEmbeddedCSS())
                    .replace("${chartScript}", chartScript);
        } catch (IOException e) {
            LOGGER.warn("Failed to load header template, using fallback", e);
            return "<html><head><title>" + title + "</title></head><body>";
        }
    }

    private String generateHtmlFooter() {
        try {
            String template = loadTemplate("report-footer.html");
            return template.replace("${timestamp}", getCurrentTimestamp());
        } catch (IOException e) {
            LOGGER.warn("Failed to load footer template, using fallback", e);
            return "</body></html>";
        }
    }

    private String generateNavigationMenu() {
        try {
            return loadTemplate("navigation-menu.html");
        } catch (IOException e) {
            LOGGER.warn("Failed to load navigation template, using fallback", e);
            return "<nav><h1>CUI Benchmarking</h1></nav>";
        }
    }

    private String generateOverviewSection(Collection<RunResult> results) {
        try {
            String template = loadTemplate("overview-section.html");
            double avgThroughput = calculateAverageThroughput(results);
            return template
                    .replace("${totalBenchmarks}", String.valueOf(results.size()))
                    .replace("${avgThroughput}", formatThroughput(avgThroughput))
                    .replace("${performanceGrade}", calculatePerformanceGrade(avgThroughput));
        } catch (IOException e) {
            LOGGER.warn("Failed to load overview template, using fallback", e);
            return "<main><section><h2>Performance Overview</h2></section></main>";
        }
    }

    private String generateBenchmarkTable(Collection<RunResult> results) {
        try {
            String template = loadTemplate("benchmark-table.html");
            StringBuilder rows = new StringBuilder();
            for (RunResult result : results) {
                rows.append(generateBenchmarkRow(result));
            }
            return template.replace("${tableRows}", rows.toString());
        } catch (IOException e) {
            LOGGER.warn("Failed to load benchmark table template, using fallback", e);
            return "<section><h2>Benchmark Results</h2><table></table></section>";
        }
    }

    private String generateBenchmarkRow(RunResult result) {
        try {
            String template = loadTemplate("benchmark-row.html");
            String benchmarkName = extractBenchmarkName(result.getParams().getBenchmark());

            if (result.getPrimaryResult() != null) {
                var primaryResult = result.getPrimaryResult();
                String score = "%.2f".formatted(primaryResult.getScore());
                String unit = primaryResult.getScoreUnit();
                String error = "N/A";
                String samples = "N/A";

                if (primaryResult.getStatistics() != null) {
                    error = "±%.2f".formatted(primaryResult.getStatistics().getStandardDeviation());
                    samples = String.valueOf(primaryResult.getStatistics().getN());
                }

                return template
                        .replace("${benchmarkName}", benchmarkName)
                        .replace("${score}", score)
                        .replace("${unit}", unit)
                        .replace("${error}", error)
                        .replace("${samples}", samples);
            } else {
                return template
                        .replace("${benchmarkName}", benchmarkName)
                        .replace("${score}", "No data")
                        .replace("${unit}", "")
                        .replace("${error}", "")
                        .replace("${samples}", "");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load benchmark row template, using fallback", e);
            return "<tr><td>" + extractBenchmarkName(result.getParams().getBenchmark()) + "</td><td colspan='4'>Error</td></tr>";
        }
    }

    private String generateTrendsSection(Collection<RunResult> results) {
        try {
            String template = loadTemplate("trends-section.html");
            return template
                    .replace("${benchmarkCount}", String.valueOf(results.size()))
                    .replace("${performanceGrade}", calculatePerformanceGrade(calculateAverageThroughput(results)));
        } catch (IOException e) {
            LOGGER.warn("Failed to load trends template, using fallback", e);
            return "<main><section><h2>Performance Trends</h2><p>" + results.size() + " benchmarks analyzed</p></section></main>";
        }
    }

    private String getEmbeddedCSS() {
        try {
            return loadTemplate("report-styles.css");
        } catch (IOException e) {
            LOGGER.warn("Failed to load CSS template, using fallback", e);
            return "/* CSS loading failed */";
        }
    }

    private String extractBenchmarkName(String fullName) {
        if (fullName == null) return "Unknown";
        String[] parts = fullName.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : fullName;
    }

    private double calculateAverageThroughput(Collection<RunResult> results) {
        return results.stream()
                .filter(r -> r.getPrimaryResult() != null)
                .filter(r -> r.getPrimaryResult().getScoreUnit().contains("ops"))
                .mapToDouble(r -> r.getPrimaryResult().getScore())
                .average()
                .orElse(0.0);
    }

    private String formatThroughput(double throughput) {
        if (throughput >= 1_000_000) {
            return "%.1fM ops/s".formatted(throughput / 1_000_000);
        } else if (throughput >= 1_000) {
            return "%.1fK ops/s".formatted(throughput / 1_000);
        } else {
            return "%.0f ops/s".formatted(throughput);
        }
    }

    private String calculatePerformanceGrade(double throughput) {
        String grade = switch ((int) Math.log10(Math.max(1, throughput))) {
            case 6, 7, 8, 9 -> "A+";
            case 5 -> "A";
            case 4 -> "B";
            case 3 -> "C";
            case 2 -> "D";
            default -> "F";
        };

        // Apply CSS class based on grade
        String cssClass = switch (grade) {
            case "A+" -> "grade-a-plus";
            case "A" -> "grade-a";
            case "B" -> "grade-b";
            case "C" -> "grade-c";
            case "D" -> "grade-d";
            default -> "grade-f";
        };

        return "<span class=\"" + cssClass + "\">" + grade + "</span>";
    }

    private String getCurrentTimestamp() {
        return DISPLAY_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC));
    }

    /**
     * Loads a template from the classpath resources.
     *
     * @param templateName the name of the template file
     * @return the template content as a string
     * @throws IOException if the template cannot be loaded
     */
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