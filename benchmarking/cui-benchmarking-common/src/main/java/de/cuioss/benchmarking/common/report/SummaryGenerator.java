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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.cuioss.benchmarking.common.config.BenchmarkType;
import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO;

/**
 * Generates summary files for CI/CD pipeline consumption.
 * <p>
 * This generator creates machine-readable summary files that can be used by
 * continuous integration systems, monitoring tools, and automated deployment
 * pipelines to make decisions based on benchmark results.
 * <p>
 * Generated summaries include:
 * <ul>
 *   <li>Overall benchmark status (pass/fail)</li>
 *   <li>Performance regression detection</li>
 *   <li>Key metrics and thresholds</li>
 *   <li>Deployment readiness indicators</li>
 * </ul>
 */
public class SummaryGenerator {

    private static final CuiLogger LOGGER =
            new CuiLogger(SummaryGenerator.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    // Constants for JSON field names
    private static final String FIELD_THROUGHPUT = "throughput";
    private static final String FIELD_LATENCY = "latency";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PERFORMANCE = "performance";

    /**
     * Writes a summary file using pre-computed metrics.
     *
     * @param metrics the pre-computed benchmark metrics
     * @param type the benchmark type
     * @param outputFile the output file path
     * @throws IOException if writing fails
     */
    public void writeSummary(BenchmarkMetrics metrics, BenchmarkType type, Path outputFile)
            throws IOException {

        Map<String, Object> summary = new LinkedHashMap<>();

        // Basic metadata
        summary.put("benchmark_type", type);
        summary.put("timestamp", ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)));
        summary.put("throughputBenchmarkName", metrics.throughputBenchmarkName());
        summary.put("latencyBenchmarkName", metrics.latencyBenchmarkName());

        // Performance metrics
        summary.put(FIELD_THROUGHPUT, metrics.throughput());
        summary.put(FIELD_LATENCY, metrics.latency());
        summary.put("performance_score", metrics.performanceScore());
        summary.put("performance_grade", metrics.performanceGrade());

        // Formatted values
        summary.put("throughputFormatted", MetricConversionUtil.formatThroughput(metrics.throughput()));
        summary.put("latencyFormatted", MetricConversionUtil.formatLatency(metrics.latency()));

        // Status determination based on score
        String status = determineStatus(metrics.performanceScore());
        summary.put(FIELD_STATUS, status);

        // CI/CD readiness indicators
        summary.put("deployment_ready", isDeploymentReady(metrics.performanceScore()));
        summary.put("regression_detected", hasRegression(metrics.performanceScore()));

        Files.writeString(outputFile, GSON.toJson(summary));

        LOGGER.info(INFO.SUMMARY_FILE_GENERATED.format(outputFile));
    }

    /**
     * Determines the status based on performance score.
     */
    private String determineStatus(double score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 75) return "GOOD";
        if (score >= 60) return "FAIR";
        return "POOR";
    }

    /**
     * Determines if deployment is ready based on performance.
     */
    private boolean isDeploymentReady(double score) {
        return score >= 60; // Minimum acceptable score for deployment
    }

    /**
     * Checks for performance regression.
     */
    private boolean hasRegression(double score) {
        // TODO: Compare with baseline when historical data is available
        return false; // For now, no regression detection
    }
}