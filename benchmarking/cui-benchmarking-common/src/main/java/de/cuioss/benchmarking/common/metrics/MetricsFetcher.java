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
import java.util.Map;

/**
 * Interface for fetching metrics data from various sources.
 *
 * @since 1.0
 */
public interface MetricsFetcher {

    /**
     * Fetch metrics data as key-value pairs.
     *
     * @return map of metric names to values
     * @throws IOException if an I/O error occurs while fetching metrics
     */
    Map<String, Double> fetchMetrics() throws IOException;
}