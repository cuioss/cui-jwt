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
package de.cuioss.jwt.benchmarking.common;

/**
 * Represents the type of benchmark being executed.
 * 
 * @since 1.0
 */
public enum BenchmarkType {
    
    /**
     * Micro benchmarks - testing individual components or methods.
     */
    MICRO("Performance Score", "performance-badge.json", "trend-badge.json"),
    
    /**
     * Integration benchmarks - testing full application workflows.
     */
    INTEGRATION("Integration Performance", "integration-performance-badge.json", "integration-trend-badge.json");

    private final String labelText;
    private final String performanceBadgeFilename;
    private final String trendBadgeFilename;

    BenchmarkType(String labelText, String performanceBadgeFilename, String trendBadgeFilename) {
        this.labelText = labelText;
        this.performanceBadgeFilename = performanceBadgeFilename;
        this.trendBadgeFilename = trendBadgeFilename;
    }

    /**
     * Gets the display label for this benchmark type.
     *
     * @return the label text for badges and reports
     */
    public String getLabelText() {
        return labelText;
    }

    /**
     * Gets the filename for the performance badge.
     *
     * @return the performance badge filename
     */
    public String getPerformanceBadgeFilename() {
        return performanceBadgeFilename;
    }

    /**
     * Gets the filename for the trend badge.
     *
     * @return the trend badge filename
     */
    public String getTrendBadgeFilename() {
        return trendBadgeFilename;
    }
}