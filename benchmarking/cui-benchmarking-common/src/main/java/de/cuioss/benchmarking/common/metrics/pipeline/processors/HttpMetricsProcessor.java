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
package de.cuioss.benchmarking.common.metrics.pipeline.processors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsContext;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessingException;
import de.cuioss.benchmarking.common.metrics.pipeline.MetricsProcessor;
import de.cuioss.tools.logging.CuiLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Processor that extracts HTTP metrics from JMH benchmark results.
 * This processor handles parsing of benchmark JSON data and extraction
 * of percentiles, throughput, and sample counts for HTTP endpoints.
 *
 * @since 1.0
 */
public class HttpMetricsProcessor implements MetricsProcessor {

    private static final CuiLogger LOGGER = new CuiLogger(HttpMetricsProcessor.class);

    private static final double MICROSECONDS_PER_MILLISECOND = 1000.0;

    @Override public MetricsContext process(MetricsContext context) throws MetricsProcessingException {
        LOGGER.debug("Processing HTTP metrics from source: {}", context.getSource());

        // Get raw data as JSON string
        Object rawData = context.getRawData();
        if (rawData == null) {
            throw new MetricsProcessingException(getName(), "No raw data available in context");
        }

        JsonArray benchmarks;
        try {
            if (rawData instanceof String string) {
                benchmarks = JsonParser.parseString(string).getAsJsonArray();
            } else if (rawData instanceof JsonArray array) {
                benchmarks = array;
            } else {
                throw new MetricsProcessingException(getName(),
                        "Raw data must be JSON string or JsonArray, got: " + rawData.getClass());
            }
        } catch (Exception e) {
            throw new MetricsProcessingException(getName(), "Failed to parse JSON data", e);
        }

        Map<String, HttpEndpointMetrics> endpointMetrics = parseBenchmarkResults(benchmarks);

        // Add parsed metrics to context
        for (Map.Entry<String, HttpEndpointMetrics> entry : endpointMetrics.entrySet()) {
            String endpointType = entry.getKey();
            HttpEndpointMetrics metrics = entry.getValue();

            // Add endpoint-specific metrics
            String prefix = endpointType + "_";
            context.addMetric(prefix + "name", metrics.displayName);
            context.addMetric(prefix + "sample_count", metrics.sampleCount);

            if (metrics.throughput > 0) {
                context.addMetric(prefix + "throughput_ops_per_sec", formatNumber(metrics.throughput));
            }

            // Add percentiles in microseconds
            context.addMetric(prefix + "p50_us", formatNumber(metrics.p50 * MICROSECONDS_PER_MILLISECOND));
            context.addMetric(prefix + "p95_us", formatNumber(metrics.p95 * MICROSECONDS_PER_MILLISECOND));
            context.addMetric(prefix + "p99_us", formatNumber(metrics.p99 * MICROSECONDS_PER_MILLISECOND));

            // Add source information
            context.addMetric(prefix + "source", "JMH benchmark - " + metrics.sourceBenchmark);
        }

        LOGGER.debug("Processed HTTP metrics for {} endpoints", endpointMetrics.size());
        return context;
    }

    private Map<String, HttpEndpointMetrics> parseBenchmarkResults(JsonArray benchmarks) {
        Map<String, HttpEndpointMetrics> endpointMetrics = new LinkedHashMap<>();

        for (JsonElement element : benchmarks) {
            JsonObject benchmark = element.getAsJsonObject();
            processBenchmark(benchmark, endpointMetrics);
        }

        return endpointMetrics;
    }

    private void processBenchmark(JsonObject benchmark, Map<String, HttpEndpointMetrics> endpointMetrics) {
        String benchmarkName = benchmark.get("benchmark").getAsString();
        String mode = benchmark.get("mode").getAsString();

        String endpointType = determineEndpointType(benchmarkName);
        if (endpointType == null) {
            return;
        }

        HttpEndpointMetrics metrics = endpointMetrics.computeIfAbsent(endpointType,
                k -> new HttpEndpointMetrics(getEndpointDisplayName(k), benchmarkName));

        // Process throughput mode benchmarks
        if ("thrpt".equals(mode)) {
            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            if (primaryMetric != null) {
                double score = primaryMetric.get("score").getAsDouble();
                String scoreUnit = primaryMetric.get("scoreUnit").getAsString();

                // Convert to ops/s if necessary
                double throughputOpsPerSec = score;
                if ("ops/ms".equals(scoreUnit)) {
                    throughputOpsPerSec = score * 1000;
                } else if ("ops/us".equals(scoreUnit)) {
                    throughputOpsPerSec = score * 1000000;
                }

                metrics.updateThroughput(throughputOpsPerSec);
                LOGGER.debug("Processed {} throughput: {} ops/s", endpointType, throughputOpsPerSec);
            }
        } else if ("sample".equals(mode)) {
            // Extract percentiles from primaryMetric scorePercentiles
            JsonObject primaryMetric = benchmark.getAsJsonObject("primaryMetric");
            if (primaryMetric == null) {
                return;
            }

            JsonObject scorePercentiles = primaryMetric.getAsJsonObject("scorePercentiles");
            if (scorePercentiles == null) {
                return;
            }

            // Extract sample count
            int sampleCount = extractSampleCount(primaryMetric);

            // Update metrics with percentile data
            double p50 = scorePercentiles.get("50.0").getAsDouble();
            double p95 = scorePercentiles.get("95.0").getAsDouble();
            double p99 = scorePercentiles.get("99.0").getAsDouble();

            metrics.updateMetrics(sampleCount, p50, p95, p99);

            LOGGER.debug("Processed {} - samples: {}, p50: {}ms, p95: {}ms, p99: {}ms",
                    endpointType, sampleCount, p50, p95, p99);
        }
    }

    private String determineEndpointType(String benchmarkName) {
        if (benchmarkName.contains("JwtValidationBenchmark")) {
            return "jwt_validation";
        } else if (benchmarkName.contains("JwtHealthBenchmark")) {
            return "health";
        }
        return null;
    }

    private String getEndpointDisplayName(String endpointType) {
        return switch (endpointType) {
            case "jwt_validation" -> "JWT Validation";
            case "health" -> "Health Check";
            default -> endpointType;
        };
    }

    private int extractSampleCount(JsonObject primaryMetric) {
        JsonArray rawDataHistogram = primaryMetric.getAsJsonArray("rawDataHistogram");
        if (rawDataHistogram == null || rawDataHistogram.isEmpty()) {
            return 0;
        }

        int totalCount = 0;
        for (JsonElement forkElement : rawDataHistogram) {
            JsonArray fork = forkElement.getAsJsonArray();
            if (fork != null) {
                for (JsonElement iterationElement : fork) {
                    JsonArray iteration = iterationElement.getAsJsonArray();
                    if (iteration != null) {
                        for (JsonElement measurement : iteration) {
                            JsonArray pair = measurement.getAsJsonArray();
                            if (pair != null && pair.size() >= 2) {
                                totalCount += pair.get(1).getAsInt();
                            }
                        }
                    }
                }
            }
        }
        return totalCount;
    }

    private Object formatNumber(double value) {
        if (value < 10.0) {
            return Math.round(value * 10.0) / 10.0;
        } else {
            return Math.round(value);
        }
    }

    @Override public String getName() {
        return "HttpMetricsProcessor";
    }

    /**
     * Internal class to track metrics for an HTTP endpoint
     */
    private static class HttpEndpointMetrics {
        final String displayName;
        final String sourceBenchmark;
        int sampleCount;
        double p50;
        double p95;
        double p99;
        double throughput;

        HttpEndpointMetrics(String displayName, String sourceBenchmark) {
            this.displayName = displayName;
            this.sourceBenchmark = sourceBenchmark;
        }

        void updateMetrics(int samples, double p50, double p95, double p99) {
            this.sampleCount += samples;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
        }

        void updateThroughput(double throughput) {
            this.throughput = throughput;
        }
    }
}