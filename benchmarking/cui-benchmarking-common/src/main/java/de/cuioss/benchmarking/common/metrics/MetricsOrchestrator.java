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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Orchestrator for metrics processing.
 * Coordinates the download, transformation, and export of metrics.
 * Delegates all transformation logic to MetricsTransformer.
 */
public class MetricsOrchestrator {

    private static final CuiLogger LOGGER = new CuiLogger(MetricsOrchestrator.class);

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
     * Downloads, processes and exports Quarkus metrics.
     * This is the main entry point for Quarkus integration metrics.
     *
     * @param prefix Prefix for downloaded file naming
     * @throws IOException if I/O operations fail
     */
    public void processQuarkusMetrics(String prefix) throws IOException {
        LOGGER.info("Starting processQuarkusMetrics for prefix: {}", prefix);

        // 1. Download metrics
        MetricsDownloader downloader = new MetricsDownloader(metricsURL, downloadsDirectory);
        Path downloadedFile = downloader.downloadMetrics(prefix);
        LOGGER.info("Downloaded metrics to: {}", downloadedFile);

        // 2. Process metrics
        MetricsFileProcessor processor = new MetricsFileProcessor(downloadsDirectory);
        Map<String, Double> allMetrics = processor.processAllMetricsFiles();
        LOGGER.info("Processed {} total metrics", allMetrics.size());

        // 3. Create gh-pages-ready/data directory and export directly there
        Path ghPagesDataDir = targetDirectory.resolve("gh-pages-ready").resolve("data");
        Files.createDirectories(ghPagesDataDir);
        LOGGER.info("Created gh-pages data directory: {}", ghPagesDataDir);

        // 4. Transform raw metrics to structured format
        MetricsTransformer transformer = new MetricsTransformer();
        Map<String, Object> structuredMetrics = transformer.transformToQuarkusRuntimeMetrics(allMetrics);

        // 5. Wrap in the expected structure for export
        Map<String, Object> exportData = Map.of("quarkus-runtime-metrics", structuredMetrics);

        // 6. Export to JSON directly in the target location
        MetricsJsonExporter exporter = new MetricsJsonExporter(ghPagesDataDir);
        exporter.exportToFile("quarkus-metrics.json", exportData);

        Path ghPagesMetricsFile = ghPagesDataDir.resolve("quarkus-metrics.json");
        LOGGER.info("Exported Quarkus metrics directly to: {}", ghPagesMetricsFile);
    }
}