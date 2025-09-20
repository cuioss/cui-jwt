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

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Common interface for exporting benchmark metrics to various formats.
 * Implementations handle specific types of metrics (library benchmarks, integration benchmarks, etc.)
 * and export them in a standardized way.
 * 
 * @since 1.0
 */
public interface MetricsExporter {

    /**
     * Export metrics for a specific benchmark.
     * 
     * @param benchmarkMethodName The name of the benchmark method
     * @param timestamp The timestamp when the benchmark was executed
     * @param metricsData The metrics data to export (format depends on implementation)
     * @throws IOException if export fails
     */
    void exportMetrics(String benchmarkMethodName, Instant timestamp, Object metricsData) throws IOException;

    /**
     * Export metrics to a specific file.
     * 
     * @param filepath The target file path
     * @param metricsData The metrics data to export
     * @throws IOException if export fails
     */
    void exportToFile(String filepath, Map<String, Object> metricsData) throws IOException;

    /**
     * Get the export format supported by this exporter.
     * 
     * @return The export format (e.g., "json", "csv", "html")
     */
    ExportFormat getExportFormat();

    /**
     * Supported export formats for metrics.
     */
    enum ExportFormat {
        JSON("json"),
        CSV("csv"),
        HTML("html"),
        MARKDOWN("md");

        private final String extension;

        ExportFormat(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }
}