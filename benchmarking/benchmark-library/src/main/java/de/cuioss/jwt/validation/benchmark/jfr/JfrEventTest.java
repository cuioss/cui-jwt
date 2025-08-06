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
package de.cuioss.jwt.validation.benchmark.jfr;

import de.cuioss.tools.logging.CuiLogger;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Simple test to verify JFR events are working properly.
 */
@SuppressWarnings("java:S2245") // ThreadLocalRandom is appropriate for test scenarios
public class JfrEventTest {

    private static final CuiLogger LOGGER = new CuiLogger(JfrEventTest.class);

    public static void main(String[] args) throws Exception {
        Path outputPath = Path.of("target/benchmark-results/test-jfr.jfr");

        // Start JFR recording
        try (Recording recording = new Recording()) {
            recording.enable("de.cuioss.jwt.Operation");
            recording.enable("de.cuioss.jwt.OperationStatistics");
            recording.enable("de.cuioss.jwt.BenchmarkPhase");
            recording.setDestination(outputPath);
            recording.start();

            LOGGER.info("JFR Recording started...");

            // Create instrumentation
            JfrInstrumentation instrumentation = new JfrInstrumentation();

            // Record phase event
            instrumentation.recordPhase("TestBenchmark", "warmup", 1, 3, 1, 4);

            // Simulate some operations
            ExecutorService executor = Executors.newFixedThreadPool(4);
            try {
                CountDownLatch latch = new CountDownLatch(100);

                for (int i = 0; i < 100; i++) {
                    final int index = i;
                    executor.submit(() -> {
                        try {
                            // Simulate JWT validation with varying latency
                            try (JfrInstrumentation.OperationRecorder recorder =
                                    instrumentation.recordOperation("TestBenchmark", "validation")) {
                                // ThreadLocalRandom is safe for benchmark/test simulation
                                recorder.withTokenSize(ThreadLocalRandom.current().nextInt(100, 500))
                                        .withIssuer("issuer-" + (index % 3))
                                        .withSuccess(index % 10 != 0); // 90% success rate

                                // Simulate processing time - ThreadLocalRandom is appropriate for test scenarios
                                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
                            }
                        } catch (InterruptedException e) {
                            // Restore interrupt status and exit
                            Thread.currentThread().interrupt();
                            LOGGER.error("Test operation interrupted", e);
                        } catch (RuntimeException e) {
                            LOGGER.error("Error during JFR event test operation", e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                // Wait for operations to complete
                latch.await();
                Thread.sleep(2000); // Wait for periodic statistics
            } finally {
                // Shutdown - ensures executor is always closed
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
            
            instrumentation.shutdown();

            recording.stop();
            LOGGER.info("JFR Recording stopped.");
        }

        // Verify the recording
        LOGGER.info("\nAnalyzing JFR recording...");
        analyzeRecording(outputPath);
    }

    private static void analyzeRecording(Path jfrFile) throws IOException {
        int operationCount = 0;
        int statisticsCount = 0;
        int phaseCount = 0;

        try (RecordingFile recordingFile = new RecordingFile(jfrFile)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                String eventName = event.getEventType().getName();

                switch (eventName) {
                    case "de.cuioss.jwt.Operation":
                        operationCount++;
                        if (operationCount == 1) {
                            LOGGER.info("\nFirst Operation Event:");
                            LOGGER.info("  Operation Type: %s", event.getString("operationType"));
                            LOGGER.info("  Benchmark: %s", event.getString("benchmarkName"));
                            LOGGER.info("  Duration: %s ms", event.getDuration().toMillis());
                            LOGGER.info("  Success: %s", event.getBoolean("success"));
                        }
                        break;
                    case "de.cuioss.jwt.OperationStatistics":
                        statisticsCount++;
                        if (statisticsCount == 1) {
                            LOGGER.info("\nFirst Statistics Event:");
                            LOGGER.info("  Sample Count: %s", event.getLong("sampleCount"));
                            LOGGER.info("  P50 Latency: %s ms", event.getDuration("p50Latency").toMillis());
                            LOGGER.info("  CV: %s%%", event.getDouble("coefficientOfVariation"));
                        }
                        break;
                    case "de.cuioss.jwt.BenchmarkPhase":
                        phaseCount++;
                        LOGGER.info("\nPhase Event:");
                        LOGGER.info("  Phase: %s", event.getString("phase"));
                        LOGGER.info("  Benchmark: %s", event.getString("benchmarkName"));
                        break;
                    default:
                        // Ignore other event types
                        break;
                }
            }
        }

        LOGGER.info("\nSummary:");
        LOGGER.info("  Operation Events: %s", operationCount);
        LOGGER.info("  Statistics Events: %s", statisticsCount);
        LOGGER.info("  Phase Events: %s", phaseCount);

        if (operationCount == 0 && statisticsCount == 0) {
            LOGGER.error("\nWARNING: No JWT events found in recording!");
        }
    }
}