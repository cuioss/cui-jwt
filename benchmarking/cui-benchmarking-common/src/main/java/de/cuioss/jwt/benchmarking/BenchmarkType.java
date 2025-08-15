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
package de.cuioss.jwt.benchmarking;

/**
 * Enumeration of supported benchmark types.
 * <p>
 * This enum is used to categorize benchmarks and generate appropriate
 * badges, reports, and metrics for each type.
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 */
public enum BenchmarkType {
    
    /**
     * Micro benchmarks - focused on specific component performance.
     */
    MICRO("Micro", "performance-badge.json", "trend-badge.json"),
    
    /**
     * Integration benchmarks - full system integration tests.
     */
    INTEGRATION("Integration", "integration-performance-badge.json", "integration-trend-badge.json");

    private final String displayName;
    private final String performanceBadgeFile;
    private final String trendBadgeFile;

    BenchmarkType(String displayName, String performanceBadgeFile, String trendBadgeFile) {
        this.displayName = displayName;
        this.performanceBadgeFile = performanceBadgeFile;
        this.trendBadgeFile = trendBadgeFile;
    }

    /**
     * Gets the display name for this benchmark type.
     * 
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the filename for the performance badge.
     * 
     * @return the performance badge filename
     */
    public String getPerformanceBadgeFile() {
        return performanceBadgeFile;
    }

    /**
     * Gets the filename for the trend badge.
     * 
     * @return the trend badge filename
     */
    public String getTrendBadgeFile() {
        return trendBadgeFile;
    }

    /**
     * Gets the badge label for performance badges.
     * 
     * @return the badge label
     */
    public String getPerformanceLabel() {
        return switch (this) {
            case MICRO -> "Performance Score";
            case INTEGRATION -> "Integration Performance";
        };
    }
}