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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation;
import de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer;

import jdk.jfr.Recording;
import java.nio.file.Path;

/**
 * Demonstration of JFR instrumentation and variance analysis.
 */
public class JfrDemo {

    private static final CuiLogger log = new CuiLogger(JfrDemo.class);

    public static void main(String[] args) throws Exception {
        Path outputPath = Path.of("target/benchmark-results/demo-jfr.jfr");

        // Start JFR recording
        try (Recording recording = new Recording()) {
            recording.enable("de.cuioss.jwt.Operation");
            recording.enable("de.cuioss.jwt.OperationStatistics");
            recording.enable("de.cuioss.jwt.BenchmarkPhase");
            recording.setDestination(outputPath);
            recording.start();

            log.info("Starting JFR Demo - simulating JWT validation operations...\n");

            // Run the demo
            runDemo();

            recording.stop();
            log.info("\nJFR Recording completed: " + outputPath);
        }

        // Analyze the recording
        log.info("\nAnalyzing JFR data...");
        JfrVarianceAnalyzer analyzer = new JfrVarianceAnalyzer();
        JfrVarianceAnalyzer.VarianceReport report = analyzer.analyze(outputPath);
        report.printSummary();
    }

    private static void runDemo() throws Exception {
        JfrInstrumentation instrumentation = new JfrInstrumentation();

        // Record initial phase
        instrumentation.recordPhase("JfrDemo", "setup", 1, 1, 1, 1);

        // Simulate single-threaded operations
        log.info("Phase 1: Single-threaded operations");
        for (int i = 0; i < 50; i++) {
            simulateOperation(instrumentation, "single-thread", i);
        }

        Thread.sleep(1100); // Wait for statistics event
        
        // Record concurrent phase
        instrumentation.recordPhase("JfrDemo", "concurrent", 1, 1, 1, 4);

        // Simulate concurrent operations
        log.info("Phase 2: Multi-threaded operations");
        Thread[] threads = new Thread[4];
        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 25; i++) {
                    try {
                        simulateOperation(instrumentation, "multi-thread", threadId * 25 + i);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "Worker-" + t);
            threads[t].start();
        }

        // Wait for all threads
        for (Thread t : threads) {
            t.join();
        }

        Thread.sleep(1100); // Wait for final statistics
        
        // Shutdown
        instrumentation.shutdown();
    }

    private static void simulateOperation(JfrInstrumentation instrumentation, String type, int index)
            throws InterruptedException {

        try (JfrInstrumentation.OperationRecorder recorder =
                instrumentation.recordOperation("JfrDemo", type)) {

            // Set metadata
            recorder.withTokenSize(200 + index % 100)
                    .withIssuer("demo-issuer-" + (index % 3))
                    .withSuccess(index % 10 != 0); // 90% success rate
            
            // Simulate varying processing time (creates variance)
            if (index % 5 == 0) {
                Thread.sleep(10); // Occasional slow operation
            } else {
                Thread.sleep(1 + index % 3); // Normal variation
            }
        }
    }
}