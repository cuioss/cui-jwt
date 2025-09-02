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