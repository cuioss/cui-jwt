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

import java.time.Instant;


import lombok.Builder;
import lombok.Value;

/**
 * Complete metrics snapshot collected during benchmark execution.
 * 
 * @since 1.0
 */
@Value
@Builder
public class BenchmarkMetrics {

    /**
     * Timestamp when metrics were collected.
     */
    @SerializedName("timestamp")
    @Builder.Default
    Instant timestamp = Instant.now();

    /**
     * Benchmark name or identifier.
     */
    @SerializedName("benchmark_name")
    String benchmarkName;

    /**
     * JVM metrics at the time of collection.
     */
    @SerializedName("jvm_metrics")
    JvmMetrics jvmMetrics;

    /**
     * Application-specific metrics.
     */
    @SerializedName("application_metrics")
    ApplicationMetrics applicationMetrics;

    /**
     * Additional metadata about the benchmark run.
     */
    @SerializedName("metadata")
    BenchmarkMetadata metadata;

    /**
     * Metadata about the benchmark execution environment.
     */
    @Value
    @Builder
    public static class BenchmarkMetadata {

        /**
         * Number of threads used in the benchmark.
         */
        @SerializedName("thread_count")
        int threadCount;

        /**
         * Duration of warmup phase in seconds.
         */
        @SerializedName("warmup_duration_seconds")
        int warmupDurationSeconds;

        /**
         * Duration of measurement phase in seconds.
         */
        @SerializedName("measurement_duration_seconds")
        int measurementDurationSeconds;

        /**
         * Number of iterations performed.
         */
        @SerializedName("iterations")
        int iterations;

        /**
         * Target service URL being benchmarked.
         */
        @SerializedName("service_url")
        String serviceUrl;

        /**
         * JVM version and runtime information.
         */
        @SerializedName("jvm_info")
        String jvmInfo;

        /**
         * Host system information.
         */
        @SerializedName("system_info")
        String systemInfo;
    }
}