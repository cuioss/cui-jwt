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

import de.cuioss.benchmarking.common.config.BenchmarkConfiguration;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.results.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.ERROR.FAILED_COLLECT_PROMETHEUS_BENCHMARK;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.ERROR.PROMETHEUS_CONNECTIVITY_FAILED;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.INFO.*;
import static de.cuioss.benchmarking.common.util.BenchmarkingLogMessages.WARN.*;

/**
 * Centralized manager for Prometheus metrics collection during benchmark execution.
 * This class provides a unified approach to collecting real-time metrics from Prometheus
 * for both JMH and WRK benchmarks.
 *
 * <p>The manager tracks benchmark execution timestamps and collects metrics
 * at the end of benchmark runs. This ensures consistent metrics collection
 * across different benchmark types.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Thread-safe timestamp tracking for concurrent benchmarks</li>
 *   <li>Automatic Prometheus client configuration from system properties</li>
 *   <li>Unified metrics collection for different benchmark result types</li>
 *   <li>Integration with MetricsOrchestrator for actual metrics retrieval</li>
 * </ul>
 */
public class PrometheusMetricsManager {

    private static final CuiLogger LOGGER = new CuiLogger(PrometheusMetricsManager.class);

    public static final String PROMETHEUS_DIR_NAME = "prometheus";
    public static final String PROMETHEUS_URL_PROPERTY = "prometheus.url";
    public static final String PROMETHEUS_URL_DEFAULT = "http://localhost:9090";
    public static final String METRICS_FILE_SUFFIX = "-metrics.json";

    private final Map<String, BenchmarkTimestamps> timestampTracker = new ConcurrentHashMap<>();
    private final String prometheusUrl;
    private final boolean metricsEnabled;

    /**
     * Data class to hold benchmark execution timestamps.
     */
    private record BenchmarkTimestamps(Instant startTime, Instant endTime) {
        boolean isValid() {
            return startTime != null && endTime != null;
        }
    }

    /**
     * Creates a new PrometheusMetricsManager.
     * Prometheus URL is read from system property or defaults to localhost:9090.
     */
    public PrometheusMetricsManager() {
        this.prometheusUrl = System.getProperty(PROMETHEUS_URL_PROPERTY, PROMETHEUS_URL_DEFAULT);
        this.metricsEnabled = isPrometheusAvailable();

        if (metricsEnabled) {
            LOGGER.info(PROMETHEUS_ENABLED, prometheusUrl);
        } else {
            LOGGER.info(PROMETHEUS_DISABLED, prometheusUrl);
        }
    }

    /**
     * Records the start time for a benchmark execution.
     * This should be called just before benchmark execution begins.
     *
     * @param benchmarkName the name of the benchmark
     */
    public void recordBenchmarkStart(String benchmarkName) {
        Instant startTime = Instant.now();
        timestampTracker.compute(benchmarkName, (key, existing) -> {
            if (existing != null) {
                LOGGER.warn(OVERWRITING_START_TIME, benchmarkName);
            }
            return new BenchmarkTimestamps(startTime, null);
        });
        LOGGER.debug("Benchmark '%s' started at: %s", benchmarkName, startTime);
    }

    /**
     * Records both start and end timestamps for a benchmark.
     * Used when timestamps are provided externally (e.g., from WRK output).
     *
     * @param benchmarkName the name of the benchmark
     * @param startTime the benchmark start time
     * @param endTime the benchmark end time
     */
    public void recordBenchmarkTimestamps(String benchmarkName, Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            LOGGER.warn(INVALID_TIMESTAMPS, benchmarkName, startTime, endTime);
            return;
        }

        timestampTracker.put(benchmarkName, new BenchmarkTimestamps(startTime, endTime));
        LOGGER.debug("Recorded timestamps for benchmark '%s': %s to %s",
                benchmarkName, startTime, endTime);
    }

    /**
     * Collects Prometheus metrics for completed benchmarks.
     * This should be called after all benchmarks have completed execution.
     *
     * @param results the benchmark results
     * @param config the benchmark configuration
     */
    public void collectMetricsForResults(Collection<RunResult> results, BenchmarkConfiguration config) {
        if (!metricsEnabled) {
            LOGGER.debug("Skipping Prometheus metrics collection - not enabled");
            return;
        }

        if (!config.hasIntegrationConfig()) {
            LOGGER.debug("Skipping Prometheus metrics collection - no integration config");
            return;
        }

        String outputDirectory = config.resultsDirectory();

        try {
            Path prometheusDir = Path.of(outputDirectory, PROMETHEUS_DIR_NAME);
            Files.createDirectories(prometheusDir);

            LOGGER.info(COLLECTING_PROMETHEUS_METRICS, prometheusUrl);

            MetricsOrchestrator orchestrator = new MetricsOrchestrator(
                    new PrometheusClient(prometheusUrl)
            );

            for (RunResult result : results) {
                String benchmarkName = extractBenchmarkName(result);
                collectMetricsForBenchmark(benchmarkName, orchestrator, prometheusDir);
            }

        } catch (IOException e) {
            LOGGER.warn(FAILED_COLLECT_PROMETHEUS, e.getMessage());
        }
    }

    /**
     * Collects Prometheus metrics for WRK benchmark results.
     * This method handles the specific case of WRK benchmarks where
     * timestamps are extracted from the output files.
     *
     * @param benchmarkName the name of the benchmark
     * @param startTime the benchmark start time
     * @param endTime the benchmark end time
     * @param outputDirectory the directory to save metrics
     */
    public void collectMetricsForWrkBenchmark(String benchmarkName, Instant startTime,
            Instant endTime, String outputDirectory) {
        if (!metricsEnabled) {
            LOGGER.warn(SKIPPING_PROMETHEUS_COLLECTION, prometheusUrl);
            LOGGER.debug("To enable metrics collection, ensure Prometheus is running and accessible at the configured URL");
            return;
        }

        if (startTime == null || endTime == null) {
            LOGGER.warn(MISSING_TIMESTAMPS_FOR_COLLECTION, benchmarkName);
            return;
        }

        try {
            Path prometheusDir = Path.of(outputDirectory, PROMETHEUS_DIR_NAME);
            Files.createDirectories(prometheusDir);

            LOGGER.info(COLLECTING_WRK_PROMETHEUS_METRICS, benchmarkName);

            MetricsOrchestrator orchestrator = new MetricsOrchestrator(
                    new PrometheusClient(prometheusUrl)
            );

            orchestrator.collectBenchmarkMetrics(
                    benchmarkName,
                    startTime,
                    endTime,
                    prometheusDir
            );

            LOGGER.info(PROMETHEUS_METRICS_SAVED, prometheusDir, benchmarkName, METRICS_FILE_SUFFIX);

        } catch (IOException e) {
            LOGGER.error(e, FAILED_COLLECT_PROMETHEUS_BENCHMARK, benchmarkName, prometheusUrl);
            LOGGER.debug("Attempted to query metrics for time range: %s to %s", startTime, endTime);
        }
    }

    private void collectMetricsForBenchmark(String benchmarkName, MetricsOrchestrator orchestrator,
            Path prometheusDir) {
        LOGGER.debug("Collecting metrics for benchmark: '%s', available keys: %s",
                benchmarkName, timestampTracker.keySet());

        // Get the specific timestamps for this benchmark
        BenchmarkTimestamps timestamps = timestampTracker.get(benchmarkName);

        if (timestamps == null || !timestamps.isValid()) {
            LOGGER.warn(NO_VALID_TIMESTAMPS, benchmarkName, timestampTracker.keySet());
            return;
        }

        LOGGER.info(USING_SESSION_TIMESTAMPS, benchmarkName, timestamps.startTime(), timestamps.endTime());

        try {
            LOGGER.info(COLLECTING_BENCHMARK_METRICS, benchmarkName);

            orchestrator.collectBenchmarkMetrics(
                    benchmarkName,
                    timestamps.startTime(),
                    timestamps.endTime(),
                    prometheusDir
            );

            LOGGER.info(PROMETHEUS_METRICS_SAVED, prometheusDir, benchmarkName, METRICS_FILE_SUFFIX);

        } catch (IOException e) {
            LOGGER.warn(FAILED_COLLECT_BENCHMARK_METRICS, benchmarkName, e.getMessage());
        }
    }

    private String extractBenchmarkName(RunResult result) {
        String label = result.getPrimaryResult().getLabel();
        int lastDot = label.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < label.length() - 1) {
            return label.substring(lastDot + 1);
        }
        return label;
    }

    private boolean isPrometheusAvailable() {
        try {
            LOGGER.debug("Checking Prometheus availability at URL: %s", prometheusUrl);
            PrometheusClient client = new PrometheusClient(prometheusUrl);
            client.queryRange(List.of("up"), Instant.now().minusSeconds(60), Instant.now(), Duration.ofSeconds(10));
            LOGGER.debug("Prometheus is available and responding at: %s", prometheusUrl);
            return true;
        } catch (PrometheusClient.PrometheusException e) {
            LOGGER.error(PROMETHEUS_CONNECTIVITY_FAILED, prometheusUrl, e.getMessage(), e.getClass().getSimpleName());
            LOGGER.info(PROMETHEUS_CONNECTIVITY_ADVICE, prometheusUrl);
            return false;
        }
    }

    /**
     * Clears all tracked timestamps.
     * Useful for testing or when reusing the manager for multiple benchmark runs.
     */
    public void clear() {
        timestampTracker.clear();
    }

}