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
 * Common benchmarking infrastructure with JMH integration for badge generation and artifact processing.
 * <p>
 * This package provides a comprehensive benchmarking framework that integrates with JMH (Java Microbenchmark Harness)
 * to generate all required artifacts during benchmark execution, including:
 * </p>
 * <ul>
 *   <li><strong>Performance badges</strong> - Shields.io compatible badges with scoring and metrics</li>
 *   <li><strong>Trend badges</strong> - Historical performance analysis and trend visualization</li>
 *   <li><strong>HTML reports</strong> - Self-contained reports with embedded CSS and responsive design</li>
 *   <li><strong>JSON metrics</strong> - Structured data for API consumption and analysis</li>
 *   <li><strong>GitHub Pages structure</strong> - Complete deployment-ready directory layout</li>
 * </ul>
 * 
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link de.cuioss.jwt.benchmarking.common.BenchmarkRunner} - Standardized JMH execution with artifact generation</li>
 *   <li>{@link de.cuioss.jwt.benchmarking.common.BenchmarkResultProcessor} - Complete pipeline for result processing</li>
 *   <li>{@link de.cuioss.jwt.benchmarking.common.BadgeGenerator} - Shields.io badge generation with smart scoring</li>
 *   <li>{@link de.cuioss.jwt.benchmarking.common.ReportGenerator} - Self-contained HTML report generation</li>
 *   <li>{@link de.cuioss.jwt.benchmarking.common.MetricsGenerator} - Structured JSON metrics for APIs</li>
 *   <li>{@link de.cuioss.jwt.benchmarking.common.GitHubPagesGenerator} - Deployment structure preparation</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Run benchmarks with complete artifact generation
 * Options options = new OptionsBuilder()
 *     .include(".*Benchmark.*")
 *     .resultFormat(ResultFormatType.JSON)
 *     .result("target/benchmark-results/raw-result.json")
 *     .build();
 *     
 * Collection<RunResult> results = new Runner(options).run();
 * 
 * // Process results to generate all artifacts
 * BenchmarkResultProcessor processor = new BenchmarkResultProcessor();
 * processor.processResults(results, "target/benchmark-results");
 * }</pre>
 * 
 * <h2>Benefits</h2>
 * <ul>
 *   <li><strong>Testability</strong> - All logic in Java with full unit test coverage</li>
 *   <li><strong>Portability</strong> - Works for any project without modifications</li>
 *   <li><strong>Reliability</strong> - Badge generation happens during benchmark run</li>
 *   <li><strong>Simplicity</strong> - Shell scripts reduced to simple copy operations</li>
 *   <li><strong>Maintainability</strong> - Single source of truth in Java code</li>
 * </ul>
 * 
 * @since 1.0
 */
package de.cuioss.jwt.benchmarking.common;