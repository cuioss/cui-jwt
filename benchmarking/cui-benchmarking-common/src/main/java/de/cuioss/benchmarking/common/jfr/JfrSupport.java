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
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

/**
 * Utility class for detecting and verifying JFR (Java Flight Recorder) support.
 * <p>
 * Provides methods to check if JFR is available, enabled, and properly configured
 * in the current JVM runtime.
 */
public final class JfrSupport {

    private static final CuiLogger log = new CuiLogger(JfrSupport.class);

    private static final boolean JFR_AVAILABLE = checkJfrAvailability();

    private JfrSupport() {
        // Utility class
    }

    /**
     * Checks if JFR is available in the current JVM.
     * 
     * @return true if JFR is available, false otherwise
     */
    public static boolean isAvailable() {
        return JFR_AVAILABLE;
    }

    /**
     * Checks if JFR is available and can be used for recording.
     * This performs a more thorough check by attempting to create a recording.
     * 
     * @return true if JFR can be used for recording, false otherwise
     */
    public static boolean isRecordingSupported() {
        if (!JFR_AVAILABLE) {
            return false;
        }

        try {
            // Try to create a test recording
            try (Recording testRecording = new Recording()) {
                testRecording.enable("jdk.CPULoad");
                testRecording.start();
                testRecording.stop();
                return true;
            }
        } catch (Exception e) {
            log.debug("JFR recording test failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a specific event type is available.
     * 
     * @param eventName the fully qualified name of the event (e.g., "de.cuioss.benchmark.Operation")
     * @return true if the event type is registered and available
     */
    public static boolean isEventAvailable(String eventName) {
        if (!JFR_AVAILABLE) {
            return false;
        }

        try {
            return FlightRecorder.getFlightRecorder()
                    .getEventTypes()
                    .stream()
                    .anyMatch(eventType -> eventType.getName().equals(eventName));
        } catch (Exception e) {
            log.debug("Failed to check event availability for %s: %s", eventName, e.getMessage());
            return false;
        }
    }

    /**
     * Gets information about JFR support status.
     * 
     * @return a descriptive string about JFR support status
     */
    public static String getSupportInfo() {
        StringBuilder info = new StringBuilder();
        info.append("JFR Support Status:\n");
        info.append("  Available: ").append(isAvailable()).append("\n");
        info.append("  Recording Supported: ").append(isRecordingSupported()).append("\n");

        if (isAvailable()) {
            info.append("  JDK Version: ").append(System.getProperty("java.version")).append("\n");
            info.append("  JVM Vendor: ").append(System.getProperty("java.vendor")).append("\n");

            try {
                long eventTypes = FlightRecorder.getFlightRecorder().getEventTypes().size();
                info.append("  Registered Event Types: ").append(eventTypes).append("\n");
            } catch (Exception e) {
                info.append("  Event Types: Unable to determine\n");
            }
        } else {
            info.append("  Reason: JFR classes not found or not accessible\n");
            info.append("  Note: JFR requires JDK 11+ with commercial features enabled\n");
        }

        return info.toString();
    }

    /**
     * Logs JFR support information at INFO level.
     */
    public static void logSupportInfo() {
        log.info(getSupportInfo());
    }

    /**
     * Validates that JFR is available and throws an exception if not.
     * 
     * @throws IllegalStateException if JFR is not available
     */
    public static void requireJfrSupport() {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "JFR (Java Flight Recorder) is not available in this JVM. " +
                            "Please use JDK 11+ with JFR support enabled."
            );
        }

        if (!isRecordingSupported()) {
            throw new IllegalStateException(
                    "JFR is available but recording is not supported. " +
                            "Please check JVM flags and permissions."
            );
        }
    }

    private static boolean checkJfrAvailability() {
        try {
            // Check if JFR classes are available
            Class.forName("jdk.jfr.FlightRecorder");
            Class.forName("jdk.jfr.Recording");
            Class.forName("jdk.jfr.Event");

            // Check if FlightRecorder is accessible
            FlightRecorder.getFlightRecorder();

            return true;
        } catch (ClassNotFoundException e) {
            log.debug("JFR classes not found: %s", e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("JFR not available: %s", e.getMessage());
            return false;
        }
    }
}