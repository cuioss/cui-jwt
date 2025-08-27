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
package de.cuioss.benchmarking.common.report;

import static de.cuioss.benchmarking.common.report.ReportConstants.*;

/**
 * Utility for converting benchmark metrics between different units.
 */
final class MetricConversionUtil {
    
    private MetricConversionUtil() {
        // Utility class
    }
    
    /**
     * Converts a benchmark score to operations per second.
     * 
     * @param score the raw score value
     * @param unit the unit string from JMH
     * @return throughput in operations per second
     */
    static double convertToOpsPerSecond(double score, String unit) {
        if (unit.contains(UNIT_OPS_PER_SEC) || unit.contains(UNIT_OPS_PER_SEC_ALT)) {
            return score;
        } else if (unit.contains(UNIT_OPS_PER_MS)) {
            return score * MILLIS_TO_SECONDS;
        } else if (unit.contains(UNIT_OPS_PER_US)) {
            return score * MICROS_TO_SECONDS;
        } else if (unit.contains(UNIT_OPS_PER_NS)) {
            return score * NANOS_TO_SECONDS;
        } else if (unit.contains(UNIT_SEC_PER_OP)) {
            return 1.0 / score;
        } else if (unit.contains(UNIT_MS_PER_OP)) {
            return MILLIS_TO_SECONDS / score;
        } else if (unit.contains(UNIT_US_PER_OP)) {
            return MICROS_TO_SECONDS / score;
        } else if (unit.contains(UNIT_NS_PER_OP)) {
            return NANOS_TO_SECONDS / score;
        }
        return score;
    }
    
    /**
     * Converts a benchmark score to milliseconds per operation.
     * 
     * @param score the raw score value
     * @param unit the unit string from JMH
     * @return latency in milliseconds per operation
     */
    static double convertToMilliseconds(double score, String unit) {
        if (unit.contains(UNIT_MS_PER_OP)) {
            return score;
        } else if (unit.contains(UNIT_SEC_PER_OP)) {
            return score * MILLIS_TO_SECONDS;
        } else if (unit.contains(UNIT_US_PER_OP)) {
            return score / MICROS_TO_MILLIS;
        } else if (unit.contains(UNIT_NS_PER_OP)) {
            return score / NANOS_TO_MILLIS;
        } else if (unit.contains(UNIT_OPS_PER_SEC) || unit.contains(UNIT_OPS_PER_SEC_ALT)) {
            return MILLIS_TO_SECONDS / score;
        } else if (unit.contains(UNIT_OPS_PER_MS)) {
            return 1.0 / score;
        } else if (unit.contains(UNIT_OPS_PER_US)) {
            return MICROS_TO_MILLIS / score;
        } else if (unit.contains(UNIT_OPS_PER_NS)) {
            return NANOS_TO_MILLIS / score;
        }
        return score;
    }
    
    /**
     * Calculates a performance grade based on throughput.
     * 
     * @param throughput operations per second
     * @return performance grade string
     */
    static String calculatePerformanceGrade(double throughput) {
        return switch ((int) Math.log10(Math.max(1, throughput))) {
            case 6, 7, 8, 9 -> GRADE_A_PLUS;
            case 5 -> GRADE_A;
            case 4 -> GRADE_B;
            case 3 -> GRADE_C;
            default -> GRADE_D;
        };
    }
}