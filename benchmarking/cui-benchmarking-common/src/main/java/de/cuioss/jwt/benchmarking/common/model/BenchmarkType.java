/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.benchmarking.common.model;

/**
 * Enumeration of benchmark types supported by the CUI benchmarking infrastructure.
 * This classification drives badge generation, report formatting, and performance 
 * scoring algorithms.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public enum BenchmarkType {
    
    /**
     * Micro benchmarks focusing on individual method or component performance.
     * Typically high throughput (millions of ops/sec) with very low latency.
     */
    MICRO("Micro", "performance-badge.json", "trend-badge.json", "Performance Score"),
    
    /**
     * Integration benchmarks testing complete workflows and system interactions.
     * Typically lower throughput (thousands of ops/sec) with higher latency but
     * more realistic performance characteristics.
     */
    INTEGRATION("Integration", "integration-performance-badge.json", 
                "integration-trend-badge.json", "Integration Performance");
    
    private final String displayName;
    private final String badgeFilename;
    private final String trendBadgeFilename;
    private final String badgeLabel;
    
    BenchmarkType(String displayName, String badgeFilename, String trendBadgeFilename, String badgeLabel) {
        this.displayName = displayName;
        this.badgeFilename = badgeFilename;
        this.trendBadgeFilename = trendBadgeFilename;
        this.badgeLabel = badgeLabel;
    }
    
    /**
     * Get the display name for this benchmark type.
     * 
     * @return human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the filename for the performance badge.
     * 
     * @return badge filename
     */
    public String getBadgeFilename() {
        return badgeFilename;
    }
    
    /**
     * Get the filename for the trend badge.
     * 
     * @return trend badge filename
     */
    public String getTrendBadgeFilename() {
        return trendBadgeFilename;
    }
    
    /**
     * Get the label text for badges.
     * 
     * @return badge label text
     */
    public String getBadgeLabel() {
        return badgeLabel;
    }
    
    /**
     * Get the metrics filename prefix for this benchmark type.
     * 
     * @return metrics filename prefix
     */
    public String getMetricsPrefix() {
        return this == MICRO ? "jwt-validation" : "integration";
    }
}