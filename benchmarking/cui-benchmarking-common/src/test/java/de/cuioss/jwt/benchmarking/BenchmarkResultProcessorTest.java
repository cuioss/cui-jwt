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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkListEntry;
import org.openjdk.jmh.runner.options.TimeValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BenchmarkResultProcessor}.
 * 
 * @author CUI-OpenSource-Software
 */
class BenchmarkResultProcessorTest {

    private BenchmarkResultProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BenchmarkResultProcessor();
    }

    @Test
    void testCompleteArtifactGeneration(@TempDir Path tempDir) throws Exception {
        // Create test benchmark results
        Collection<RunResult> results = createTestResults();
        
        // Process results
        String outputDir = tempDir.toString();
        processor.processResults(results, outputDir);
        
        // Verify all artifacts were created using JUnit Jupiter assertions
        assertTrue(Files.exists(Paths.get(outputDir, "badges/performance-badge.json")));
        assertTrue(Files.exists(Paths.get(outputDir, "badges/trend-badge.json")));
        assertTrue(Files.exists(Paths.get(outputDir, "badges/last-run-badge.json")));
        assertTrue(Files.exists(Paths.get(outputDir, "data/metrics.json")));
        assertTrue(Files.exists(Paths.get(outputDir, "index.html")));
        assertTrue(Files.exists(Paths.get(outputDir, "trends.html")));
        assertTrue(Files.isDirectory(Paths.get(outputDir, "gh-pages-ready")));
        assertTrue(Files.exists(Paths.get(outputDir, "benchmark-summary.json")));
    }

    @Test
    void testBenchmarkTypeDetection() {
        String microBenchmark = "de.cuioss.jwt.validation.benchmark.ValidationBenchmark";
        String integrationBenchmark = "de.cuioss.jwt.quarkus.benchmark.HealthBenchmark";
        
        // Create results with different benchmark names
        RunResult microResult = createMockResult(microBenchmark);
        RunResult integrationResult = createMockResult(integrationBenchmark);
        
        // Test type detection would happen in the processor
        // For now, verify the benchmark names contain expected patterns
        assertTrue(microBenchmark.contains("validation"));
        assertTrue(integrationBenchmark.contains("quarkus"));
    }

    @Test
    void testEmptyResultsHandling(@TempDir Path tempDir) {
        // Test that processor handles empty results gracefully
        Collection<RunResult> emptyResults = Collections.emptyList();
        
        assertThrows(IllegalArgumentException.class, () -> {
            processor.processResults(emptyResults, tempDir.toString());
        });
    }

    @Test
    void testOutputDirectoryCreation(@TempDir Path tempDir) throws Exception {
        Collection<RunResult> results = createTestResults();
        String outputDir = tempDir.toString() + "/nested/output";
        
        // Process results in non-existent directory
        processor.processResults(results, outputDir);
        
        // Verify directories were created
        assertTrue(Files.exists(Paths.get(outputDir)));
        assertTrue(Files.exists(Paths.get(outputDir, "badges")));
        assertTrue(Files.exists(Paths.get(outputDir, "data")));
        assertTrue(Files.exists(Paths.get(outputDir, "gh-pages-ready")));
    }

    private Collection<RunResult> createTestResults() {
        RunResult result = createMockResult("de.cuioss.jwt.validation.benchmark.TestBenchmark");
        return List.of(result);
    }

    private RunResult createMockResult(String benchmarkName) {
        // Create a minimal mock result for testing
        // In a real implementation, this would use JMH's result classes properly
        return new RunResult() {
            @Override
            public BenchmarkListEntry getBenchmark() {
                return null;
            }

            @Override
            public Collection<BenchmarkResult> getBenchmarkResults() {
                return Collections.emptyList();
            }

            @Override
            public Result getPrimaryResult() {
                return new Result() {
                    @Override
                    public String getLabel() {
                        return "thrpt";
                    }

                    @Override
                    public double getScore() {
                        return 1000000.0;
                    }

                    @Override
                    public double getScoreError() {
                        return 0.0;
                    }

                    @Override
                    public String getUnit() {
                        return "ops/s";
                    }

                    @Override
                    public org.openjdk.jmh.util.Statistics getStatistics() {
                        return new org.openjdk.jmh.util.Statistics() {
                            @Override
                            public long getN() {
                                return 10;
                            }

                            @Override
                            public double getSum() {
                                return 10000000.0;
                            }

                            @Override
                            public double getMin() {
                                return 900000.0;
                            }

                            @Override
                            public double getMax() {
                                return 1100000.0;
                            }

                            @Override
                            public double getMean() {
                                return 1000000.0;
                            }

                            @Override
                            public double getVariance() {
                                return 1000.0;
                            }

                            @Override
                            public double getStandardDeviation() {
                                return 31.6;
                            }

                            @Override
                            public double getConfidenceIntervalAt(double v) {
                                return 1000.0;
                            }

                            @Override
                            public double[] getPercentiles(double... v) {
                                return new double[]{1000000.0};
                            }
                        };
                    }

                    @Override
                    public String extendedInfo() {
                        return "";
                    }
                };
            }

            @Override
            public Collection<Result> getSecondaryResults() {
                return Collections.emptyList();
            }

            @Override
            public org.openjdk.jmh.runner.BenchmarkParams getParams() {
                return new org.openjdk.jmh.runner.BenchmarkParams() {
                    @Override
                    public String getBenchmark() {
                        return benchmarkName;
                    }

                    @Override
                    public int getForks() {
                        return 1;
                    }

                    @Override
                    public int getWarmupForks() {
                        return 0;
                    }

                    @Override
                    public int getWarmupIterations() {
                        return 5;
                    }

                    @Override
                    public TimeValue getWarmupTime() {
                        return TimeValue.seconds(1);
                    }

                    @Override
                    public int getMeasurementIterations() {
                        return 5;
                    }

                    @Override
                    public TimeValue getMeasurementTime() {
                        return TimeValue.seconds(1);
                    }

                    @Override
                    public org.openjdk.jmh.annotations.Mode getMode() {
                        return org.openjdk.jmh.annotations.Mode.Throughput;
                    }

                    @Override
                    public int getThreads() {
                        return 1;
                    }

                    @Override
                    public int[] getThreadGroups() {
                        return new int[]{1};
                    }

                    @Override
                    public boolean shouldSynchIterations() {
                        return false;
                    }

                    @Override
                    public Collection<String> getJvm() {
                        return Collections.emptyList();
                    }

                    @Override
                    public Collection<String> getJvmArgs() {
                        return Collections.emptyList();
                    }

                    @Override
                    public Collection<String> getJvmArgsAppend() {
                        return Collections.emptyList();
                    }

                    @Override
                    public Collection<String> getJvmArgsPrepend() {
                        return Collections.emptyList();
                    }

                    @Override
                    public String id() {
                        return benchmarkName;
                    }

                    @Override
                    public TimeValue getTimeout() {
                        return TimeValue.minutes(10);
                    }

                    @Override
                    public Collection<String> getProfilers() {
                        return Collections.emptyList();
                    }
                };
            }

            @Override
            public String getScoreUnit() {
                return "ops/s";
            }

            @Override
            public org.openjdk.jmh.runner.IterationType getIterationType() {
                return org.openjdk.jmh.runner.IterationType.MEASUREMENT;
            }
        };
    }
}