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

import com.google.gson.annotations.SerializedName;


import lombok.Builder;
import lombok.Value;

/**
 * Application-specific metrics collected from Prometheus.
 * 
 * @since 1.0
 */
@Value
@Builder
public class ApplicationMetrics {

    /**
     * Total number of HTTP requests processed.
     */
    @SerializedName("http_requests_total")
    @Builder.Default
    long httpRequestsTotal = 0L;

    /**
     * Total number of successful HTTP requests (2xx responses).
     */
    @SerializedName("http_requests_success_total")
    @Builder.Default
    long httpRequestsSuccessTotal = 0L;

    /**
     * Total number of failed HTTP requests (4xx, 5xx responses).
     */
    @SerializedName("http_requests_error_total")
    @Builder.Default
    long httpRequestsErrorTotal = 0L;

    /**
     * Average HTTP request duration in seconds.
     */
    @SerializedName("http_request_duration_seconds_mean")
    @Builder.Default
    double httpRequestDurationSecondsMean = 0.0;

    /**
     * P50 HTTP request duration in seconds.
     */
    @SerializedName("http_request_duration_seconds_50p")
    @Builder.Default
    double httpRequestDurationSeconds50p = 0.0;

    /**
     * P95 HTTP request duration in seconds.
     */
    @SerializedName("http_request_duration_seconds_95p")
    @Builder.Default
    double httpRequestDurationSeconds95p = 0.0;

    /**
     * P99 HTTP request duration in seconds.
     */
    @SerializedName("http_request_duration_seconds_99p")
    @Builder.Default
    double httpRequestDurationSeconds99p = 0.0;

    /**
     * Total number of JWT validations performed.
     */
    @SerializedName("jwt_validations_total")
    @Builder.Default
    long jwtValidationsTotal = 0L;

    /**
     * Total number of successful JWT validations.
     */
    @SerializedName("jwt_validations_success_total")
    @Builder.Default
    long jwtValidationsSuccessTotal = 0L;

    /**
     * Total number of failed JWT validations.
     */
    @SerializedName("jwt_validations_error_total")
    @Builder.Default
    long jwtValidationsErrorTotal = 0L;

    /**
     * JWT validation cache hit count.
     */
    @SerializedName("jwt_cache_hits_total")
    @Builder.Default
    long jwtCacheHitsTotal = 0L;

    /**
     * JWT validation cache miss count.
     */
    @SerializedName("jwt_cache_misses_total")
    @Builder.Default
    long jwtCacheMissesTotal = 0L;

    /**
     * Average JWT validation duration in seconds.
     */
    @SerializedName("jwt_validation_duration_seconds_mean")
    @Builder.Default
    double jwtValidationDurationSecondsMean = 0.0;
}