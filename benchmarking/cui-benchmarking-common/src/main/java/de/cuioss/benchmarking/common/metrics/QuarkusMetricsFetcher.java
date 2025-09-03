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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
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
    private static final int REQUEST_TIMEOUT_MS = 10000;

    private final String quarkusUrl;
    private final OkHttpClient httpClient;

    public QuarkusMetricsFetcher(String quarkusUrl) {
        this.quarkusUrl = quarkusUrl;
        this.httpClient = HttpClientFactory.getInsecureClient();
    }

    @Override public Map<String, Double> fetchMetrics() {
        Map<String, Double> results = new HashMap<>();

        try {
            String metricsUrl = quarkusUrl + "/q/metrics";

            Request request = new Request.Builder()
                    .url(metricsUrl)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == HTTP_OK && response.body() != null) {
                    String responseBody = response.body().string();

                    // Save raw metrics for development
                    saveRawMetricsData(responseBody);

                    parseQuarkusMetrics(responseBody, results);
                } else {
                    LOGGER.warn("Failed to query Quarkus metrics: HTTP {}", response.code());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Error querying Quarkus metrics", e);
        }

        return results;
    }

    /**
     * Save raw metrics data with intelligent benchmark context
     */
    private void saveRawMetricsData(String rawMetrics) {
        try {
            // Create simple timestamp-based filename
            String timestamp = Instant.now().toString().replace(":", "-").replace(".", "-");
            File metricsDir = new File("target/metrics-download");
            metricsDir.mkdirs();
            File outputFile = new File(metricsDir, "quarkus-metrics-" + timestamp + ".txt");

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(rawMetrics);
            }

            LOGGER.debug("Saved raw Quarkus metrics to: {}", outputFile.getAbsolutePath());

        } catch (IOException e) {
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