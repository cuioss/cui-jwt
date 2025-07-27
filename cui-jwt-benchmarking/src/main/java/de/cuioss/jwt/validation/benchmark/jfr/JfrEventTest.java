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

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple test to verify JFR events are working properly.
 */
public class JfrEventTest {

    public static void main(String[] args) throws Exception {
        Path outputPath = Path.of("target/benchmark-results/test-jfr.jfr");

        // Start JFR recording
        try (Recording recording = new Recording()) {
            recording.enable("de.cuioss.jwt.Operation");
            recording.enable("de.cuioss.jwt.OperationStatistics");
            recording.enable("de.cuioss.jwt.BenchmarkPhase");
            recording.setDestination(outputPath);
            recording.start();

            System.out.println("JFR Recording started...");

            // Create instrumentation
            JfrInstrumentation instrumentation = new JfrInstrumentation();

            // Record phase event
            instrumentation.recordPhase("TestBenchmark", "warmup", 1, 3, 1, 4);

            // Simulate some operations
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch latch = new CountDownLatch(100);

            for (int i = 0; i < 100; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        // Simulate JWT validation with varying latency
                        try (JfrInstrumentation.OperationRecorder recorder =
                                instrumentation.recordOperation("TestBenchmark", "validation")) {
                            recorder.withTokenSize(ThreadLocalRandom.current().nextInt(100, 500))
                                    .withIssuer("issuer-" + (index % 3))
                                    .withSuccess(index % 10 != 0); // 90% success rate
                            
                            // Simulate processing time
                            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait for operations to complete
            latch.await();
            Thread.sleep(2000); // Wait for periodic statistics
            
            // Shutdown
            executor.shutdown();
            instrumentation.shutdown();

            recording.stop();
            System.out.println("JFR Recording stopped.");
        }

        // Verify the recording
        System.out.println("\nAnalyzing JFR recording...");
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
                            System.out.println("\nFirst Operation Event:");
                            System.out.println("  Operation Type: " + event.getString("operationType"));
                            System.out.println("  Benchmark: " + event.getString("benchmarkName"));
                            System.out.println("  Duration: " + event.getDuration().toMillis() + " ms");
                            System.out.println("  Success: " + event.getBoolean("success"));
                        }
                        break;
                    case "de.cuioss.jwt.OperationStatistics":
                        statisticsCount++;
                        if (statisticsCount == 1) {
                            System.out.println("\nFirst Statistics Event:");
                            System.out.println("  Sample Count: " + event.getLong("sampleCount"));
                            System.out.println("  P50 Latency: " + event.getDuration("p50Latency").toMillis() + " ms");
                            System.out.println("  CV: " + event.getDouble("coefficientOfVariation") + "%");
                        }
                        break;
                    case "de.cuioss.jwt.BenchmarkPhase":
                        phaseCount++;
                        System.out.println("\nPhase Event:");
                        System.out.println("  Phase: " + event.getString("phase"));
                        System.out.println("  Benchmark: " + event.getString("benchmarkName"));
                        break;
                }
            }
        }

        System.out.println("\nSummary:");
        System.out.println("  Operation Events: " + operationCount);
        System.out.println("  Statistics Events: " + statisticsCount);
        System.out.println("  Phase Events: " + phaseCount);

        if (operationCount == 0 && statisticsCount == 0) {
            System.err.println("\nWARNING: No JWT events found in recording!");
        }
    }
}