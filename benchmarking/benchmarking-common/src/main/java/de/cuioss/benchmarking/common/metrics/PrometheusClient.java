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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import de.cuioss.benchmarking.common.http.HttpClientFactory;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.WARN.FAILED_QUERY_METRIC;

/**
 * HTTP client for querying Prometheus API endpoints.
 * Supports query_range operations for time-series data retrieval during benchmark execution.
 *
 * @since 1.0
 */
public class PrometheusClient {

    private static final CuiLogger LOGGER = new CuiLogger(PrometheusClient.class);
    private static final int HTTP_OK = 200;
    private static final Gson GSON = new Gson();

    private final String prometheusUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    /**
     * Creates a new Prometheus client with default timeout.
     *
     * @param prometheusUrl Base URL of Prometheus server (e.g., {@code http://localhost:9090})
     */
    public PrometheusClient(String prometheusUrl) {
        this(prometheusUrl, Duration.ofSeconds(30));
    }

    /**
     * Creates a new Prometheus client with custom timeout.
     *
     * @param prometheusUrl Base URL of Prometheus server
     * @param timeout       HTTP request timeout
     */
    public PrometheusClient(String prometheusUrl, Duration timeout) {
        this.prometheusUrl = prometheusUrl.endsWith("/") ? prometheusUrl.substring(0, prometheusUrl.length() - 1) : prometheusUrl;
        this.timeout = timeout;
        this.httpClient = HttpClientFactory.getInsecureClient();
    }

    /**
     * Query Prometheus for time-series data in a specific time range.
     *
     * @param metricNames List of metric names to query
     * @param startTime   Start of time range (inclusive)
     * @param endTime     End of time range (inclusive)
     * @param step        Step size between data points
     * @return Map of metric name to time series data
     * @throws PrometheusException if query fails or Prometheus unavailable
     */
    public Map<String, TimeSeries> queryRange(List<String> metricNames, Instant startTime, Instant endTime, Duration step) throws PrometheusException {
        Map<String, TimeSeries> results = new HashMap<>();

        for (String metricName : metricNames) {
            try {
                TimeSeries timeSeries = queryRangeForMetric(metricName, startTime, endTime, step);
                results.put(metricName, timeSeries);
            } catch (PrometheusException e) {
                LOGGER.warn(FAILED_QUERY_METRIC, metricName, e.getMessage());
                throw e;
            }
        }

        return results;
    }

    private TimeSeries queryRangeForMetric(String metricName, Instant startTime, Instant endTime, Duration step) throws PrometheusException {
        String stepSeconds = step.getSeconds() + "s";
        String startEpoch = String.valueOf(startTime.getEpochSecond());
        String endEpoch = String.valueOf(endTime.getEpochSecond());

        String queryUrl = "%s/api/v1/query_range?query=%s&start=%s&end=%s&step=%s".formatted(
                prometheusUrl,
                URLEncoder.encode(metricName, StandardCharsets.UTF_8),
                startEpoch,
                endEpoch,
                stepSeconds);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(queryUrl))
                .timeout(timeout)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HTTP_OK) {
                throw new PrometheusException(
                        response.statusCode(),
                        "HTTP %d from Prometheus API".formatted(response.statusCode())
                );
            }

            return parsePrometheusResponse(metricName, response.body());

        } catch (IOException e) {
            throw new PrometheusException("Network error connecting to Prometheus: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrometheusException("Request interrupted", e);
        }
    }

    private TimeSeries parsePrometheusResponse(String metricName, String responseBody) throws PrometheusException {
        try {
            JsonObject root = GSON.fromJson(responseBody, JsonObject.class);
            validateStatus(root);

            JsonObject data = root.getAsJsonObject("data");
            JsonArray resultArray = data.getAsJsonArray("result");

            if (resultArray == null || resultArray.isEmpty()) {
                // Metric may not be exported by the application - return empty series silently
                return new TimeSeries(metricName, Map.of(), List.of());
            }

            JsonObject firstResult = resultArray.get(0).getAsJsonObject();
            Map<String, String> labels = extractLabels(firstResult.getAsJsonObject("metric"));
            List<DataPoint> dataPoints = extractDataPoints(firstResult.getAsJsonArray("values"));

            return new TimeSeries(metricName, labels, dataPoints);

        } catch (JsonParseException | IllegalStateException | NullPointerException e) {
            // JsonParseException: malformed JSON
            // IllegalStateException: JSON element is wrong type (e.g., not an object/array when expected)
            // NullPointerException: missing expected JSON fields
            throw new PrometheusException("Unexpected error parsing response: " + e.getMessage(), e);
        }
    }

    private void validateStatus(JsonObject root) throws PrometheusException {
        JsonElement statusElement = root.get("status");
        if (statusElement == null || !"success".equals(statusElement.getAsString())) {
            JsonElement errorElement = root.get("error");
            String error = errorElement != null ? errorElement.getAsString() : "Unknown error";
            throw new PrometheusException("Prometheus query failed: " + error);
        }
    }

    private Map<String, String> extractLabels(JsonObject metric) {
        Map<String, String> labels = new HashMap<>();
        if (metric != null) {
            for (Map.Entry<String, JsonElement> entry : metric.entrySet()) {
                labels.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return labels;
    }

    private List<DataPoint> extractDataPoints(JsonArray values) {
        List<DataPoint> dataPoints = new ArrayList<>();
        if (values != null) {
            for (JsonElement valueElement : values) {
                if (valueElement.isJsonArray()) {
                    JsonArray valueArray = valueElement.getAsJsonArray();
                    if (valueArray.size() >= 2) {
                        long timestamp = valueArray.get(0).getAsLong();
                        double val = Double.parseDouble(valueArray.get(1).getAsString());
                        dataPoints.add(new DataPoint(Instant.ofEpochSecond(timestamp), val));
                    }
                }
            }
        }
        return dataPoints;
    }

    /**
     * Represents time-series data for a metric.
     *
     * @param metricName name of the metric
     * @param labels metric labels as key-value pairs
     * @param values list of data points
     */
    public record TimeSeries(String metricName, Map<String, String> labels, List<DataPoint> values) {
        public TimeSeries {
            labels = Map.copyOf(labels);
            values = List.copyOf(values);
        }
    }

    /**
     * Represents a single data point in time-series data.
     *
     * @param timestamp point in time for this measurement
     * @param value measured value at this timestamp
     */
    public record DataPoint(Instant timestamp, double value) {
    }

    /**
     * Exception thrown when Prometheus operations fail.
     */
    @Getter
    public static class PrometheusException extends Exception {
        private final int statusCode;

        public PrometheusException(String message) {
            this(message, null);
        }

        public PrometheusException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = -1;
        }

        public PrometheusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}