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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricsFetcherTest {

    @Test void interfaceImplementation() {
        // Test that we can implement the interface
        MetricsFetcher fetcher = new MetricsFetcher() {
            @Override public Map<String, Double> fetchMetrics() {
                Map<String, Double> metrics = new HashMap<>();
                metrics.put("test.metric", 42.0);
                return metrics;
            }
        };

        Map<String, Double> result = fetcher.fetchMetrics();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(42.0, result.get("test.metric"));
    }

    @Test void emptyMetricsImplementation() {
        MetricsFetcher emptyFetcher = () -> new HashMap<>();

        Map<String, Double> result = emptyFetcher.fetchMetrics();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test void nullReturnImplementation() {
        MetricsFetcher nullFetcher = () -> null;

        Map<String, Double> result = nullFetcher.fetchMetrics();
        assertNull(result);
    }

    @Test void multipleMetricsImplementation() {
        MetricsFetcher multipleFetcher = () -> {
            Map<String, Double> metrics = new HashMap<>();
            metrics.put("cpu.usage", 75.5);
            metrics.put("memory.usage", 60.2);
            metrics.put("disk.usage", 45.8);
            metrics.put("network.latency", 12.3);
            return metrics;
        };

        Map<String, Double> result = multipleFetcher.fetchMetrics();
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(75.5, result.get("cpu.usage"));
        assertEquals(60.2, result.get("memory.usage"));
        assertEquals(45.8, result.get("disk.usage"));
        assertEquals(12.3, result.get("network.latency"));
    }
}