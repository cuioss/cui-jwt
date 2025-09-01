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
package de.cuioss.benchmarking.common.jfr;

import de.cuioss.tools.logging.CuiLogger;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Analyzes JFR recordings to extract operation variance metrics.
 * <p>
 * This utility reads JFR files generated during benchmarks and calculates:
 * <ul>
 *   <li>Time variance across different operations</li>
 *   <li>Percentile distributions (P50, P95, P99)</li>
 *   <li>Coefficient of variation for operation times</li>
 *   <li>Concurrent load impact on variance</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * Path jfrFile = Path.of("target/benchmark-results/benchmark.jfr");
 * JfrVarianceAnalyzer analyzer = new JfrVarianceAnalyzer();
 * VarianceReport report = analyzer.analyze(jfrFile);
 * report.printSummary();
 * }</pre>
 */
public class JfrVarianceAnalyzer {

    private static final CuiLogger log = new CuiLogger(JfrVarianceAnalyzer.class);

    /**
     * Analyzes a JFR recording file and generates a variance report.
     *
     * @param jfrFile path to the JFR recording file
     * @return variance analysis report
     * @throws IOException if the file cannot be read
     */
    public VarianceReport analyze(Path jfrFile) throws IOException {
        VarianceReport report = new VarianceReport();

        try (RecordingFile recordingFile = new RecordingFile(jfrFile)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();

                if ("de.cuioss.benchmark.Operation".equals(event.getEventType().getName())) {
                    processOperationEvent(event, report);
                } else if ("de.cuioss.benchmark.OperationStatistics".equals(event.getEventType().getName())) {
                    processStatisticsEvent(event, report);
                }
            }
        }

        report.computeFinalStatistics();
        return report;
    }

    private void processOperationEvent(RecordedEvent event, VarianceReport report) {
        String benchmarkName = event.getString("benchmarkName");
        String operationType = event.getString("operationType");
        Duration duration = event.getDuration();
        boolean success = event.getBoolean("success");
        int concurrentOps = event.getInt("concurrentOperations");

        OperationMetrics metrics = report.getOrCreateMetrics(benchmarkName, operationType);
        metrics.addOperation(duration.toNanos(), success, concurrentOps);
    }

    private void processStatisticsEvent(RecordedEvent event, VarianceReport report) {
        String benchmarkName = event.getString("benchmarkName");
        String operationType = event.getString("operationType");

        StatisticsSnapshot snapshot = new StatisticsSnapshot();
        snapshot.sampleCount = event.getLong("sampleCount");
        snapshot.p50Latency = event.getDuration("p50Latency").toNanos();
        snapshot.p95Latency = event.getDuration("p95Latency").toNanos();
        snapshot.p99Latency = event.getDuration("p99Latency").toNanos();
        snapshot.variance = event.getDouble("variance");
        snapshot.coefficientOfVariation = event.getDouble("coefficientOfVariation");
        snapshot.concurrentThreads = event.getInt("concurrentThreads");

        OperationMetrics metrics = report.getOrCreateMetrics(benchmarkName, operationType);
        metrics.addStatisticsSnapshot(snapshot);
    }

    /**
     * Variance analysis report containing metrics for all operations.
     */
    public static class VarianceReport {
        private final Map<String, OperationMetrics> operationMetrics = new HashMap<>();

        OperationMetrics getOrCreateMetrics(String benchmarkName, String operationType) {
            String key = benchmarkName + ":" + operationType;
            return operationMetrics.computeIfAbsent(key, k -> new OperationMetrics(benchmarkName, operationType));
        }

        void computeFinalStatistics() {
            operationMetrics.values().forEach(OperationMetrics::computeStatistics);
        }

        /**
         * Prints a summary of the variance analysis to stdout.
         */
        public void printSummary() {
            log.info("\n=== JFR Variance Analysis Report ===\n");

            operationMetrics.values().stream()
                    .sorted(Comparator.comparing(m -> m.benchmarkName))
                    .forEach(metrics -> {
                        log.info("Benchmark: %s - Operation: %s%n",
                                metrics.benchmarkName, metrics.operationType);
                        log.info("  Total Operations: %d (Success: %d, Failed: %d)%n",
                                metrics.totalOperations, metrics.successfulOperations, metrics.failedOperations);
                        log.info("  Latency (μs) - P50: %.2f, P95: %.2f, P99: %.2f, Max: %.2f%n",
                                metrics.p50Latency / 1000.0, metrics.p95Latency / 1000.0,
                                metrics.p99Latency / 1000.0, metrics.maxLatency / 1000.0);
                        log.info("  Variance: %.2e ns² (StdDev: %.2f μs)%n",
                                metrics.variance, metrics.standardDeviation / 1000.0);
                        log.info("  Coefficient of Variation: %.2f%%%n", metrics.coefficientOfVariation);
                        log.info("  Max Concurrent Operations: %d%n", metrics.maxConcurrentOperations);

                        if (!metrics.statisticsSnapshots.isEmpty()) {
                            log.info("  Periodic Statistics:");
                            log.info("    Average CV over time: %.2f%%%n", metrics.averageCV);
                            log.info("    CV Range: %.2f%% - %.2f%%%n", metrics.minCV, metrics.maxCV);
                        }

                        log.info("");
                    });
        }

        /**
         * Returns the collected operation metrics map.
         */
        public Map<String, OperationMetrics> getOperationMetrics() {
            return new HashMap<>(operationMetrics);
        }
    }

    /**
     * Metrics for a specific operation type within a benchmark.
     */
    public static class OperationMetrics {
        final String benchmarkName;
        final String operationType;

        // Raw operation data
        final List<Long> operationDurations = new ArrayList<>();
        long totalOperations = 0;
        long successfulOperations = 0;
        long failedOperations = 0;
        int maxConcurrentOperations = 0;

        // Computed statistics
        double p50Latency = 0;
        double p95Latency = 0;
        double p99Latency = 0;
        double maxLatency = 0;
        double variance = 0;
        double standardDeviation = 0;
        double coefficientOfVariation = 0;

        // Periodic statistics
        final List<StatisticsSnapshot> statisticsSnapshots = new ArrayList<>();
        double averageCV = 0;
        double minCV = Double.MAX_VALUE;
        double maxCV = 0;

        OperationMetrics(String benchmarkName, String operationType) {
            this.benchmarkName = benchmarkName;
            this.operationType = operationType;
        }

        void addOperation(long durationNanos, boolean success, int concurrentOps) {
            operationDurations.add(durationNanos);
            totalOperations++;
            if (success) {
                successfulOperations++;
            } else {
                failedOperations++;
            }
            maxConcurrentOperations = Math.max(maxConcurrentOperations, concurrentOps);
        }

        void addStatisticsSnapshot(StatisticsSnapshot snapshot) {
            statisticsSnapshots.add(snapshot);
            minCV = Math.min(minCV, snapshot.coefficientOfVariation);
            maxCV = Math.max(maxCV, snapshot.coefficientOfVariation);
        }

        void computeStatistics() {
            if (operationDurations.isEmpty()) {
                return;
            }

            // Sort durations for percentile calculation
            Collections.sort(operationDurations);

            // Calculate percentiles
            int size = operationDurations.size();
            p50Latency = operationDurations.get((int) (size * 0.50));
            p95Latency = operationDurations.get((int) (size * 0.95));
            p99Latency = operationDurations.get((int) (size * 0.99));
            maxLatency = operationDurations.get(size - 1);

            // Calculate mean
            double mean = operationDurations.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);

            // Calculate variance and standard deviation
            variance = operationDurations.stream()
                    .mapToDouble(d -> Math.pow(d - mean, 2))
                    .average()
                    .orElse(0);

            standardDeviation = Math.sqrt(variance);

            // Calculate coefficient of variation
            coefficientOfVariation = (mean > 0) ? (standardDeviation / mean * 100) : 0;

            // Calculate average CV from periodic snapshots
            if (!statisticsSnapshots.isEmpty()) {
                averageCV = statisticsSnapshots.stream()
                        .mapToDouble(s -> s.coefficientOfVariation)
                        .average()
                        .orElse(0);
            }
        }
    }

    /**
     * Snapshot of statistics from a periodic event.
     */
    static class StatisticsSnapshot {
        long sampleCount;
        long p50Latency;
        long p95Latency;
        long p99Latency;
        double variance;
        double coefficientOfVariation;
        int concurrentThreads;
    }

    /**
     * Main method for command-line usage.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            log.error("Usage: JfrVarianceAnalyzer <path-to-jfr-file>");
            System.exit(1);
        }

        Path jfrFile = Path.of(args[0]);
        JfrVarianceAnalyzer analyzer = new JfrVarianceAnalyzer();
        VarianceReport report = analyzer.analyze(jfrFile);
        report.printSummary();
    }
}