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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuarkusMetricsFetcherTest {

    @TempDir
    Path tempDir;

    private QuarkusMetricsFetcher fetcher;

    @BeforeEach
    void setUp() {
        // Use localhost URL that won't actually connect
        fetcher = new QuarkusMetricsFetcher("https://localhost:8443");
    }

    @Test
    void testConstructor() {
        assertNotNull(fetcher);
        QuarkusMetricsFetcher fetcher2 = new QuarkusMetricsFetcher("http://localhost:8080");
        assertNotNull(fetcher2);
    }

    @Test
    void testFetchMetricsWithUnreachableServer() {
        // Should return empty map when server is unreachable
        Map<String, Double> metrics = fetcher.fetchMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.isEmpty());
    }

    @Test
    void testFetchMetricsMultipleTimes() {
        // Test that multiple calls don't cause issues
        Map<String, Double> metrics1 = fetcher.fetchMetrics();
        Map<String, Double> metrics2 = fetcher.fetchMetrics();
        
        assertNotNull(metrics1);
        assertNotNull(metrics2);
    }


    @Test
    void testFetchMetricsWithDifferentUrls() {
        // Test with different URL formats
        QuarkusMetricsFetcher httpFetcher = new QuarkusMetricsFetcher("http://localhost:8080");
        Map<String, Double> httpMetrics = httpFetcher.fetchMetrics();
        assertNotNull(httpMetrics);
        
        QuarkusMetricsFetcher httpsFetcher = new QuarkusMetricsFetcher("https://localhost:8443");
        Map<String, Double> httpsMetrics = httpsFetcher.fetchMetrics();
        assertNotNull(httpsMetrics);
        
        QuarkusMetricsFetcher customPortFetcher = new QuarkusMetricsFetcher("http://localhost:9999");
        Map<String, Double> customPortMetrics = customPortFetcher.fetchMetrics();
        assertNotNull(customPortMetrics);
    }
}