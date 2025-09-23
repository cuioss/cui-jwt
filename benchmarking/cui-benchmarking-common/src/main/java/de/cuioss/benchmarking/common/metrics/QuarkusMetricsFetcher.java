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

import de.cuioss.benchmarking.common.http.HttpClientFactory;
import de.cuioss.tools.logging.CuiLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Fetches metrics from Quarkus /q/metrics endpoint.
 * 
 * @since 1.0
 */
public class QuarkusMetricsFetcher implements MetricsFetcher {

    private static final CuiLogger LOGGER = new CuiLogger(QuarkusMetricsFetcher.class);
    private static final int HTTP_OK = 200;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final String quarkusUrl;
    private final HttpClient httpClient;

    public QuarkusMetricsFetcher(String quarkusUrl) {
        this.quarkusUrl = quarkusUrl;
        this.httpClient = HttpClientFactory.getInsecureClient();
    }

    @Override public Map<String, Double> fetchMetrics() throws IOException {
        Map<String, Double> results = new HashMap<>();
        String metricsUrl = quarkusUrl + "/q/metrics";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(metricsUrl))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HTTP_OK) {
                String responseBody = response.body();

                // Save raw metrics for development
                saveRawMetricsData(responseBody);

                parseQuarkusMetrics(responseBody, results);
            } else {
                LOGGER.error("Failed to query Quarkus metrics: HTTP {} from {}",
                        response.statusCode(), metricsUrl);
                String errorBody = response.body();
                if (errorBody != null && !errorBody.isEmpty()) {
                    LOGGER.error("Error response body: {}", errorBody);
                }
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Exception querying Quarkus metrics from {}", metricsUrl, e);
        }

        return results;
    }

    /**
     * Save raw metrics data to a fixed file name
     */
    private void saveRawMetricsData(String rawMetrics) {
        try {
            // Use absolute path based on current working directory
            File currentDir = new File(System.getProperty("user.dir"));
            File metricsDir = new File(currentDir, "target/metrics-download");

            if (!metricsDir.exists()) {
                metricsDir.mkdirs();
            }

            // Use fixed file name without timestamp
            File outputFile = new File(metricsDir, "quarkus-metrics.txt");

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(rawMetrics);
            }

            LOGGER.debug("Saved raw Quarkus metrics to: {}", outputFile.getAbsolutePath());

        } catch (IOException e) {
            LOGGER.error("Failed to save raw metrics data", e);
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