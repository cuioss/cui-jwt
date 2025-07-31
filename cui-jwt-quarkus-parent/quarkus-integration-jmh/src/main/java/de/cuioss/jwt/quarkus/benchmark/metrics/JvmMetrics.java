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
 * JVM metrics collected from Prometheus for performance analysis.
 * 
 * @author Generated
 * @since 1.0
 */
@Value
@Builder
public class JvmMetrics {

    /**
     * Heap memory used in bytes.
     */
    @SerializedName("heap_used_bytes")
    @Builder.Default
    long heapUsedBytes = 0L;

    /**
     * Heap memory committed in bytes.
     */
    @SerializedName("heap_committed_bytes")
    @Builder.Default
    long heapCommittedBytes = 0L;

    /**
     * Heap memory maximum in bytes.
     */
    @SerializedName("heap_max_bytes")
    @Builder.Default
    long heapMaxBytes = 0L;

    /**
     * Non-heap memory used in bytes.
     */
    @SerializedName("non_heap_used_bytes")
    @Builder.Default
    long nonHeapUsedBytes = 0L;

    /**
     * Non-heap memory committed in bytes.
     */
    @SerializedName("non_heap_committed_bytes")
    @Builder.Default
    long nonHeapCommittedBytes = 0L;

    /**
     * Non-heap memory maximum in bytes.
     */
    @SerializedName("non_heap_max_bytes")
    @Builder.Default
    long nonHeapMaxBytes = 0L;

    /**
     * Total GC collection count.
     */
    @SerializedName("gc_collections_total")
    @Builder.Default
    long gcCollectionsTotal = 0L;

    /**
     * Total GC collection time in seconds.
     */
    @SerializedName("gc_collection_seconds_total")
    @Builder.Default
    double gcCollectionSecondsTotal = 0.0;

    /**
     * Current thread count.
     */
    @SerializedName("threads_current")
    @Builder.Default
    int threadsCurrent = 0;

    /**
     * Peak thread count.
     */
    @SerializedName("threads_peak")
    @Builder.Default
    int threadsPeak = 0;

    /**
     * CPU usage percentage (0.0 to 1.0).
     */
    @SerializedName("cpu_usage")
    @Builder.Default
    double cpuUsage = 0.0;

    /**
     * System load average (1 minute).
     */
    @SerializedName("system_load_average_1m")
    @Builder.Default
    double systemLoadAverage1m = 0.0;
}