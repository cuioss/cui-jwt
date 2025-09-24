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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static jakarta.servlet.http.HttpServletResponse.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Handles the mocking of Prometheus metrics endpoints for testing metrics download functionality.
 * Based on the pattern from JwksResolveDispatcher.
 */
@SuppressWarnings("UnusedReturnValue") public class MetricsModuleDispatcher implements ModuleDispatcherElement {

    /**
     * "/metrics"
     */
    public static final String LOCAL_PATH = "/metrics";

    @Getter
    @Setter
    private int callCounter = 0;
    private ResponseStrategy responseStrategy = ResponseStrategy.DEFAULT;
    @Getter
    private String customResponse = null;

    public MetricsModuleDispatcher() {
        // No initialization needed
    }

    /**
     * Convenience method to set the response strategy to return a 500 error.
     *
     * @return this instance for method chaining
     */
    public MetricsModuleDispatcher returnInternalServerError() {
        this.responseStrategy = ResponseStrategy.INTERNAL_SERVER_ERROR;
        return this;
    }

    /**
     * Convenience method to set the response strategy to return a 503 error.
     *
     * @return this instance for method chaining
     */
    public MetricsModuleDispatcher returnServiceUnavailable() {
        this.responseStrategy = ResponseStrategy.SERVICE_UNAVAILABLE;
        return this;
    }

    /**
     * Convenience method to set the response strategy to return invalid content.
     *
     * @return this instance for method chaining
     */
    public MetricsModuleDispatcher returnInvalidContent() {
        this.responseStrategy = ResponseStrategy.INVALID_CONTENT;
        return this;
    }

    /**
     * Convenience method to set the response strategy to return empty metrics.
     *
     * @return this instance for method chaining
     */
    public MetricsModuleDispatcher returnEmptyMetrics() {
        this.responseStrategy = ResponseStrategy.EMPTY_METRICS;
        return this;
    }

    /**
     * Convenience method to reset the response strategy to the default.
     *
     * @return this instance for method chaining
     */
    public MetricsModuleDispatcher returnDefault() {
        this.responseStrategy = ResponseStrategy.DEFAULT;
        this.customResponse = null;
        return this;
    }

    /**
     * Set a custom response to be returned by the dispatcher.
     *
     * @param response the custom response content
     */
    public void setCustomResponse(String response) {
        this.customResponse = response;
    }

    @Override public Optional<MockResponse> handleGet(@NonNull RecordedRequest request) {
        callCounter++;

        // Return custom response if set
        if (customResponse != null) {
            return Optional.of(new MockResponse(
                    SC_OK,
                    Headers.of("Content-Type", "text/plain; version=0.0.4; charset=utf-8"),
                    customResponse));
        }

        switch (responseStrategy) {
            case INTERNAL_SERVER_ERROR:
                return Optional.of(new MockResponse(SC_INTERNAL_SERVER_ERROR, Headers.of(), ""));

            case SERVICE_UNAVAILABLE:
                return Optional.of(new MockResponse(SC_SERVICE_UNAVAILABLE, Headers.of(), ""));

            case INVALID_CONTENT:
                return Optional.of(new MockResponse(
                        SC_OK,
                        Headers.of("Content-Type", "text/plain; version=0.0.4; charset=utf-8"),
                        "This is not valid Prometheus metrics format"));

            case EMPTY_METRICS:
                return Optional.of(new MockResponse(
                        SC_OK,
                        Headers.of("Content-Type", "text/plain; version=0.0.4; charset=utf-8"),
                        "# EOF\n"));

            case DEFAULT:
            default:
                // Return the default Prometheus metrics from test resources
                String metricsContent = loadDefaultMetrics();
                return Optional.of(new MockResponse(
                        SC_OK,
                        Headers.of("Content-Type", "text/plain; version=0.0.4; charset=utf-8"),
                        metricsContent));
        }
    }

    private String loadDefaultMetrics() {
        try {
            Path metricsFile = Path.of("src/test/resources/metrics-test-data/quarkus-metrics.txt");
            return Files.readString(metricsFile);
        } catch (IOException e) {
            // Fallback to minimal valid metrics if file not found
            return """
                    # TYPE cui_jwt_validation_success_operations counter
                    # HELP cui_jwt_validation_success_operations Number of successful JWT operations by type
                    cui_jwt_validation_success_operations_total{event_type="ACCESS_TOKEN_CREATED",result="success"} 1000.0
                    # TYPE cui_jwt_bearer_token_validation_seconds summary
                    # HELP cui_jwt_bearer_token_validation_seconds Bearer token validation duration
                    cui_jwt_bearer_token_validation_seconds_count{class="de.cuioss.jwt.quarkus.producer.BearerTokenProducer",exception="none",method="getBearerTokenResult"} 1000.0
                    cui_jwt_bearer_token_validation_seconds_sum{class="de.cuioss.jwt.quarkus.producer.BearerTokenProducer",exception="none",method="getBearerTokenResult"} 0.5
                    # EOF
                    """;
        }
    }

    @Override public String getBaseUrl() {
        return LOCAL_PATH;
    }

    @Override public @NonNull Set<HttpMethodMapper> supportedMethods() {
        return Set.of(HttpMethodMapper.GET);
    }

    /**
     * Verifies whether this endpoint was called the given times
     *
     * @param expected count of calls
     */
    public void assertCallsAnswered(int expected) {
        assertEquals(expected, callCounter);
    }

    /**
     * Enum representing the different response strategies for the metrics dispatcher.
     */
    public enum ResponseStrategy {
        /**
         * Returns normal Prometheus metrics based on test data.
         */
        DEFAULT,

        /**
         * Returns an HTTP 500 error response.
         */
        INTERNAL_SERVER_ERROR,

        /**
         * Returns an HTTP 503 error response.
         */
        SERVICE_UNAVAILABLE,

        /**
         * Returns invalid content that is not Prometheus format.
         */
        INVALID_CONTENT,

        /**
         * Returns empty metrics (valid format but no actual metrics).
         */
        EMPTY_METRICS
    }
}