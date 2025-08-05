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
/**
 * JFR (Java Flight Recorder) instrumentation for JWT benchmark variance analysis.
 * <p>
 * This package provides comprehensive JFR instrumentation for measuring operation time variance
 * under concurrent load during JWT validation benchmarks. The instrumentation captures:
 * </p>
 * 
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.cuioss.jwt.validation.benchmark.jfr.JwtOperationEvent} - Records individual JWT operations</li>
 *   <li>{@link de.cuioss.jwt.validation.benchmark.jfr.JwtOperationStatisticsEvent} - Periodic statistics snapshots</li>
 *   <li>{@link de.cuioss.jwt.validation.benchmark.jfr.JwtBenchmarkPhaseEvent} - Benchmark lifecycle events</li>
 *   <li>{@link de.cuioss.jwt.validation.benchmark.jfr.JfrInstrumentation} - Central instrumentation manager</li>
 *   <li>{@link de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer} - Post-benchmark analysis tool</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <h3>Running Benchmarks with JFR</h3>
 * <pre>{@code
 * # Run benchmarks with JFR recording enabled (automatic with Maven profile)
 * mvn verify -Pbenchmark
 * 
 * # JFR file will be saved to: target/benchmark-results/benchmark.jfr
 * }</pre>
 * 
 * <h3>Analyzing JFR Data</h3>
 * <pre>{@code
 * // Programmatic analysis
 * Path jfrFile = Path.of("target/benchmark-results/benchmark.jfr");
 * JfrVarianceAnalyzer analyzer = new JfrVarianceAnalyzer();
 * VarianceReport report = analyzer.analyze(jfrFile);
 * report.printSummary();
 * 
 * // Command-line analysis
 * java -cp target/classes:target/dependency/* \
 *   de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer \
 *   target/benchmark-results/benchmark.jfr
 * }</pre>
 * 
 * <h3>JFR Event Analysis with JDK Tools</h3>
 * <pre>{@code
 * # View all JWT operation events
 * jfr print --events de.cuioss.jwt.Operation benchmark.jfr
 * 
 * # Export to JSON for custom analysis
 * jfr print --json --events de.cuioss.jwt.* benchmark.jfr > jwt-events.json
 * 
 * # Summary of all events
 * jfr summary benchmark.jfr
 * }</pre>
 * 
 * <h2>Metrics Captured</h2>
 * <h3>Per-Operation Metrics</h3>
 * <ul>
 *   <li>Operation duration (nanosecond precision)</li>
 *   <li>Operation type (validation, parsing, signature verification)</li>
 *   <li>Token metadata (size, issuer)</li>
 *   <li>Success/failure status with error types</li>
 *   <li>Concurrent operation count</li>
 *   <li>Cache hit information</li>
 * </ul>
 * 
 * <h3>Periodic Statistics (1-second intervals)</h3>
 * <ul>
 *   <li>Sample count per interval</li>
 *   <li>Latency percentiles (P50, P95, P99)</li>
 *   <li>Variance and standard deviation</li>
 *   <li>Coefficient of variation (CV)</li>
 *   <li>Concurrent thread count</li>
 *   <li>Cache hit rate</li>
 * </ul>
 * 
 * <h2>Variance Analysis</h2>
 * <p>
 * The coefficient of variation (CV) is the primary metric for understanding operation time variance:
 * </p>
 * <ul>
 *   <li><strong>CV &lt; 25%</strong>: Low variance, consistent performance</li>
 *   <li><strong>CV 25-50%</strong>: Moderate variance, acceptable for concurrent workloads</li>
 *   <li><strong>CV &gt; 50%</strong>: High variance, potential performance issues</li>
 * </ul>
 * 
 * <h2>Performance Impact</h2>
 * <p>
 * JFR instrumentation is designed for minimal overhead (&lt;1% in production mode).
 * The implementation uses:
 * </p>
 * <ul>
 *   <li>Thread-local event objects to minimize allocation</li>
 *   <li>Conditional commit based on event thresholds</li>
 *   <li>Efficient HdrHistogram for accurate percentile tracking</li>
 *   <li>Striped statistics to reduce contention</li>
 * </ul>
 * 
 * @since 1.0
 */
package de.cuioss.jwt.validation.benchmark.jfr;