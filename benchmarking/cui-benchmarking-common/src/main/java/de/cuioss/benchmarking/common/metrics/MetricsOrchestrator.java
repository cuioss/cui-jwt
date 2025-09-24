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
package de.cuioss.benchmarking.common.metrics;

import de.cuioss.tools.logging.CuiLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal orchestrator for metrics processing.
 * Coordinates the download, processing, and export of metrics using the three concrete classes.
 */
public class MetricsOrchestrator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsOrchestrator.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final String metricsURL;
    private final Path downloadsDirectory;
    private final Path targetDirectory;

    /**
     * Creates a new metrics orchestrator.
     *
     * @param metricsURL URL to download metrics from
     * @param downloadsDirectory Directory to store downloaded metrics files
     * @param targetDirectory Directory to write processed metrics JSON files
     */
    public MetricsOrchestrator(String metricsURL, Path downloadsDirectory, Path targetDirectory) {
        this.metricsURL = metricsURL;
        this.downloadsDirectory = downloadsDirectory;
        this.targetDirectory = targetDirectory;
    }

    /**
     * Downloads metrics and exports them to JSON.
     *
     * @param benchmarkName Name of the benchmark for file naming
     * @param timestamp Timestamp for the metrics
     * @return Map of processed metrics
     * @throws IOException if I/O operations fail
     */
    public Map<String, Double> downloadAndExportMetrics(String benchmarkName, Instant timestamp) throws IOException {
        LOGGER.info("Starting metrics orchestration for: {}", benchmarkName);

        // 1. Download metrics
        MetricsDownloader downloader = new MetricsDownloader(metricsURL, downloadsDirectory);
        Path downloadedFile = downloader.downloadMetrics(benchmarkName);
        LOGGER.info("Downloaded metrics to: {}", downloadedFile);

        // 2. Process metrics
        MetricsFileProcessor processor = new MetricsFileProcessor(downloadsDirectory);
        Map<String, Double> allMetrics = processor.processAllMetricsFiles();
        Map<String, Double> jwtMetrics = processor.extractJwtValidationMetrics(allMetrics);
        LOGGER.info("Processed {} total metrics, extracted {} JWT validation metrics",
                allMetrics.size(), jwtMetrics.size());

        // 3. Export to JSON
        MetricsJsonExporter exporter = new MetricsJsonExporter(targetDirectory);
        Map<String, Object> exportData = createExportData(benchmarkName, timestamp, jwtMetrics);
        exporter.updateAggregatedMetrics("jwt-validation-metrics.json", benchmarkName, exportData);
        LOGGER.info("Exported metrics to: {}", targetDirectory.resolve("jwt-validation-metrics.json"));

        return jwtMetrics;
    }

    /**
     * Downloads, processes and exports Quarkus metrics.
     * This is the main entry point for Quarkus integration metrics.
     *
     * @param prefix Prefix for downloaded file naming
     * @throws IOException if I/O operations fail
     */
    public void processQuarkusMetrics(String prefix) throws IOException {
        downloadAndExportMetrics(prefix, Instant.now());
    }

    private Map<String, Object> createExportData(String benchmarkName, Instant timestamp, Map<String, Double> metrics) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("benchmark", benchmarkName);
        data.put("timestamp", ISO_FORMATTER.format(timestamp.atOffset(ZoneOffset.UTC)));
        data.put("metrics", metrics);
        return data;
    }
}