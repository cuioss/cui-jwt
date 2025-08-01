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
package de.cuioss.jwt.quarkus.benchmark.metrics;

import de.cuioss.tools.logging.CuiLogger;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fetches metrics from Quarkus /q/metrics endpoint.
 * 
 * @author Generated
 * @since 1.0
 */
public class QuarkusMetricsFetcher implements MetricsFetcher {

    private static final CuiLogger LOGGER = new CuiLogger(QuarkusMetricsFetcher.class);
    
    private final String quarkusUrl;

    public QuarkusMetricsFetcher(String quarkusUrl) {
        this.quarkusUrl = quarkusUrl;
    }

    @Override
    public Map<String, Double> fetchMetrics() {
        Map<String, Double> results = new HashMap<>();

        try {
            String metricsUrl = quarkusUrl + "/q/metrics";
            Response response = RestAssured.get(metricsUrl);

            if (response.getStatusCode() == 200) {
                String responseBody = response.getBody().asString();
                
                // Save raw metrics for development
                saveRawMetricsData(responseBody);
                
                parseQuarkusMetrics(responseBody, results);
            } else {
                LOGGER.warn("Failed to query Quarkus metrics: HTTP {}", response.getStatusCode());
            }
        } catch (Exception e) {
            LOGGER.warn("Error querying Quarkus metrics", e);
        }

        return results;
    }

    /**
     * Save raw metrics data for development purposes
     */
    private void saveRawMetricsData(String rawMetrics) {
        try {
            File metricsDownloadDir = new File("target/metrics-download");
            if (!metricsDownloadDir.exists()) {
                metricsDownloadDir.mkdirs();
            }
            
            String timestamp = Instant.now().toString().replace(":", "-");
            File outputFile = new File(metricsDownloadDir, "quarkus-metrics-" + timestamp + ".txt");
            
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(rawMetrics);
            }
            
            LOGGER.info("✅ Saved raw Quarkus metrics to: {}", outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            LOGGER.warn("Failed to save raw metrics data", e);
        }
    }

    /**
     * Parse Quarkus metrics in Prometheus format
     */
    private void parseQuarkusMetrics(String responseBody, Map<String, Double> results) {
        String[] lines = responseBody.split("\\n");
        for (String line : lines) {
            line = line.trim();

            // Skip comments and empty lines
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }

            // Parse metric line: metric_name{labels} value [timestamp]
            int spaceIndex = line.lastIndexOf(' ');
            if (spaceIndex > 0) {
                String metricPart = line.substring(0, spaceIndex);
                String valuePart = line.substring(spaceIndex + 1);

                try {
                    double value = Double.parseDouble(valuePart);
                    results.put(metricPart, value);
                } catch (NumberFormatException e) {
                    LOGGER.debug("Could not parse metric value: {} = {}", metricPart, valuePart);
                }
            }
        }
    }
}