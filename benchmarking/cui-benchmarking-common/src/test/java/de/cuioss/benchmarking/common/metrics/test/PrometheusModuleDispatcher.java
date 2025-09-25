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
package de.cuioss.benchmarking.common.metrics.test;

import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;

import java.util.Optional;
import java.util.Set;

import static jakarta.servlet.http.HttpServletResponse.*;

/**
 * Handles the mocking of Prometheus API endpoints for testing PrometheusClient functionality.
 * Supports /api/v1/query_range endpoint with real Prometheus JSON responses.
 */
@SuppressWarnings("UnusedReturnValue") public class PrometheusModuleDispatcher implements ModuleDispatcherElement {

    /**
     * "/api/v1/query_range"
     */
    public static final String PROMETHEUS_QUERY_RANGE_PATH = "/api/v1/query_range";

    @Getter
    @Setter
    private int callCounter = 0;
    private ResponseStrategy responseStrategy = ResponseStrategy.DEFAULT;
    @Getter
    private String customResponse = null;

    public PrometheusModuleDispatcher() {
        // No initialization needed
    }

    /**
     * Convenience method to set the response strategy to return a 500 error.
     */
    public PrometheusModuleDispatcher setServerError() {
        this.responseStrategy = ResponseStrategy.SERVER_ERROR;
        return this;
    }

    /**
     * Convenience method to set the response strategy to return a 503 error.
     */
    public PrometheusModuleDispatcher setServiceUnavailable() {
        this.responseStrategy = ResponseStrategy.SERVICE_UNAVAILABLE;
        return this;
    }

    /**
     * Convenience method to set the response strategy to timeout.
     */
    public PrometheusModuleDispatcher setTimeout() {
        this.responseStrategy = ResponseStrategy.TIMEOUT;
        return this;
    }

    /**
     * Convenience method to set the response strategy to return invalid JSON.
     */
    public PrometheusModuleDispatcher setInvalidJson() {
        this.responseStrategy = ResponseStrategy.INVALID_JSON;
        return this;
    }

    /**
     * Set a custom JSON response.
     */
    public PrometheusModuleDispatcher setCustomResponse(String jsonResponse) {
        this.customResponse = jsonResponse;
        this.responseStrategy = ResponseStrategy.CUSTOM;
        return this;
    }

    @Override public Optional<MockResponse> handleGet(@NonNull RecordedRequest recordedRequest) {
        callCounter++;

        String path = recordedRequest.getPath();
        if (path == null || !path.startsWith(PROMETHEUS_QUERY_RANGE_PATH)) {
            return Optional.empty();
        }

        // Return custom response if set
        if (customResponse != null) {
            return Optional.of(new MockResponse(
                    SC_OK,
                    Headers.of("Content-Type", "application/json"),
                    customResponse));
        }

        return Optional.of(switch (responseStrategy) {
            case SERVER_ERROR -> new MockResponse(SC_INTERNAL_SERVER_ERROR, Headers.of(), "Internal Server Error");
            case SERVICE_UNAVAILABLE -> new MockResponse(SC_SERVICE_UNAVAILABLE, Headers.of(), "Service Unavailable");
            case TIMEOUT -> new MockResponse(SC_REQUEST_TIMEOUT, Headers.of(), "Request Timeout");
            case INVALID_JSON -> new MockResponse(SC_OK, Headers.of("Content-Type", "application/json"), "{ invalid json }");
            default -> new MockResponse(SC_OK, Headers.of("Content-Type", "application/json"), getDefaultPrometheusResponse());
        });
    }

    /**
     * Returns a realistic Prometheus query_range response with CPU metrics data.
     */
    private String getDefaultPrometheusResponse() {
        return """
        {
          "status": "success",
          "data": {
            "resultType": "matrix",
            "result": [
              {
                "metric": {
                  "__name__": "process_cpu_usage",
                  "instance": "cui-jwt-integration-tests:8443",
                  "job": "quarkus-benchmark"
                },
                "values": [
                  [1758792520, "0.0125"],
                  [1758792580, "0.0125"],
                  [1758792640, "0.016666666666666666"],
                  [1758792700, "0.00625"],
                  [1758792760, "0.008333333333333333"]
                ]
              }
            ]
          }
        }
        """;
    }

    /**
     * Returns an empty result response (no data found).
     */
    public String getEmptyResultResponse() {
        return """
        {
          "status": "success",
          "data": {
            "resultType": "matrix",
            "result": []
          }
        }
        """;
    }

    /**
     * Returns a Prometheus error response.
     */
    public String getErrorResponse() {
        return """
        {
          "status": "error",
          "error": "invalid parameter 'query': parse error at char 1: no expression found in input"
        }
        """;
    }

    @Override public @NonNull Set<HttpMethodMapper> supportedMethods() {
        return Set.of(HttpMethodMapper.GET);
    }

    @Override public String getBaseUrl() {
        return PROMETHEUS_QUERY_RANGE_PATH;
    }

    private enum ResponseStrategy {
        DEFAULT,
        SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        TIMEOUT,
        INVALID_JSON,
        CUSTOM
    }
}