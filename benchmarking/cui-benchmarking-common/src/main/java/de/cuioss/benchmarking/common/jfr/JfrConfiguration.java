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

import jdk.jfr.Recording;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for JFR recordings in benchmark scenarios.
 * <p>
 * This class provides a fluent API for configuring JFR recordings with
 * common settings for benchmark analysis.
 */
public class JfrConfiguration {

    private Path destination;
    private Duration maxAge;
    private long maxSize = 0; // 0 means no limit
    private boolean dumpOnExit = false;
    private String name = "Benchmark Recording";
    private final List<String> enabledEvents = new ArrayList<>();
    private final Map<String, String> eventSettings = new HashMap<>();

    /**
     * Creates a default configuration for benchmark recordings.
     */
    public static JfrConfiguration benchmarkDefault() {
        return new JfrConfiguration()
                .enableEvent("de.cuioss.benchmark.Operation")
                .enableEvent("de.cuioss.benchmark.OperationStatistics")
                .enableEvent("de.cuioss.benchmark.BenchmarkPhase")
                .enableEvent("jdk.CPULoad")
                .enableEvent("jdk.GarbageCollection")
                .enableEvent("jdk.GCHeapSummary")
                .enableEvent("jdk.ThreadAllocationStatistics")
                .setDumpOnExit(true);
    }

    /**
     * Creates a minimal configuration with only benchmark events.
     */
    public static JfrConfiguration minimal() {
        return new JfrConfiguration()
                .enableEvent("de.cuioss.benchmark.Operation")
                .enableEvent("de.cuioss.benchmark.OperationStatistics")
                .enableEvent("de.cuioss.benchmark.BenchmarkPhase");
    }

    /**
     * Creates a comprehensive configuration with all available events.
     */
    public static JfrConfiguration comprehensive() {
        return benchmarkDefault()
                .enableEvent("jdk.ObjectAllocationInNewTLAB")
                .enableEvent("jdk.ObjectAllocationOutsideTLAB")
                .enableEvent("jdk.ThreadPark")
                .enableEvent("jdk.JavaMonitorWait")
                .enableEvent("jdk.JavaMonitorEnter")
                .enableEvent("jdk.BiasedLockRevocation")
                .enableEvent("jdk.BiasedLockSelfRevocation")
                .enableEvent("jdk.BiasedLockClassRevocation");
    }

    /**
     * Sets the output file for the recording.
     */
    public JfrConfiguration setDestination(Path destination) {
        this.destination = destination;
        return this;
    }

    /**
     * Sets the maximum age of recorded data.
     */
    public JfrConfiguration setMaxAge(Duration maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    /**
     * Sets the maximum size of the recording in bytes.
     */
    public JfrConfiguration setMaxSize(long maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    /**
     * Sets whether to dump the recording on JVM exit.
     */
    public JfrConfiguration setDumpOnExit(boolean dumpOnExit) {
        this.dumpOnExit = dumpOnExit;
        return this;
    }

    /**
     * Sets the name of the recording.
     */
    public JfrConfiguration setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Enables a specific event type.
     */
    public JfrConfiguration enableEvent(String eventName) {
        this.enabledEvents.add(eventName);
        return this;
    }

    /**
     * Adds a setting for a specific event.
     */
    public JfrConfiguration setEventSetting(String eventName, String setting, String value) {
        this.eventSettings.put(eventName + "#" + setting, value);
        return this;
    }

    /**
     * Applies this configuration to a recording.
     * 
     * @throws RuntimeException if setting the destination fails
     */
    public void applyTo(Recording recording) {
        recording.setName(name);

        if (destination != null) {
            try {
                recording.setDestination(destination);
            } catch (IOException e) {
                throw new RuntimeException("Failed to set recording destination: " + destination, e);
            }
        }

        if (maxAge != null) {
            recording.setMaxAge(maxAge);
        }

        if (maxSize > 0) {
            recording.setMaxSize(maxSize);
        }

        recording.setDumpOnExit(dumpOnExit);

        // Enable events
        for (String eventName : enabledEvents) {
            try {
                recording.enable(eventName);
            } catch (Exception e) {
                // Event might not be available, log and continue
                System.err.println("Warning: Could not enable event " + eventName + ": " + e.getMessage());
            }
        }

        // Apply event settings
        for (Map.Entry<String, String> entry : eventSettings.entrySet()) {
            String[] parts = entry.getKey().split("#");
            if (parts.length == 2) {
                try {
                    recording.enable(parts[0]).with(parts[1], entry.getValue());
                } catch (Exception e) {
                    System.err.println("Warning: Could not set event setting " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Creates and starts a new recording with this configuration.
     */
    public Recording createAndStart() {
        JfrSupport.requireJfrSupport();

        Recording recording = new Recording();
        applyTo(recording);
        recording.start();
        return recording;
    }

    /**
     * Gets the configured destination path.
     */
    public Path getDestination() {
        return destination;
    }

    /**
     * Gets the list of enabled events.
     */
    public List<String> getEnabledEvents() {
        return new ArrayList<>(enabledEvents);
    }
}