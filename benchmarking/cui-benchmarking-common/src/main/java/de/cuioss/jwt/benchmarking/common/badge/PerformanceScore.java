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
package de.cuioss.jwt.benchmarking.common.badge;

/**
 * Performance score calculation result for badge generation.
 * Encapsulates throughput, latency, and composite scoring metrics.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class PerformanceScore {
    
    private final double throughput;
    private final double latency;
    private final long compositeScore;
    private final String scoreLabel;
    
    public PerformanceScore(double throughput, double latency, long compositeScore, String scoreLabel) {
        this.throughput = throughput;
        this.latency = latency;
        this.compositeScore = compositeScore;
        this.scoreLabel = scoreLabel;
    }
    
    public double getThroughput() { return throughput; }
    public double getLatency() { return latency; }
    public long getCompositeScore() { return compositeScore; }
    public String getScoreLabel() { return scoreLabel; }
}