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
package de.cuioss.jwt.quarkus.benchmark.metrics;

/**
 * Common test data constants for MetricsPostProcessor tests.
 * Centralizes test data to reduce duplication across test classes.
 */
public final class MetricsTestDataConstants {

    private MetricsTestDataConstants() {
        // Utility class
    }

    /**
     * Standard benchmark result with health endpoint
     */
    public static final String STANDARD_HEALTH_BENCHMARK = """
        [
            {
                "jmhVersion": "1.37",
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                "mode": "sample",
                "threads": 1,
                "forks": 1,
                "primaryMetric": {
                    "score": 8.297886677685952,
                    "scorePercentiles": {
                        "0.0": 4.58752,
                        "50.0": 7.6513279999999995,
                        "90.0": 11.327897599999998,
                        "95.0": 13.4529024,
                        "99.0": 29.980098560000016,
                        "99.9": 33.325056,
                        "100.0": 33.325056
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [4.58752, 1],
                                [7.6513279999999995, 60],
                                [33.325056, 1]
                            ]
                        ]
                    ]
                }
            }
        ]
        """;

    /**
     * Standard benchmark result with JWT validation endpoint
     */
    public static final String STANDARD_JWT_VALIDATION_BENCHMARK = """
        [
            {
                "jmhVersion": "1.37",
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtValidationBenchmark.validateAccessTokenThroughput",
                "mode": "sample",
                "threads": 1,
                "forks": 1,
                "primaryMetric": {
                    "score": 15.5,
                    "scorePercentiles": {
                        "0.0": 12.1,
                        "50.0": 15.2,
                        "90.0": 18.7,
                        "95.0": 21.3,
                        "99.0": 35.8,
                        "99.9": 42.1,
                        "100.0": 42.1
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [12.1, 1],
                                [15.2, 40],
                                [42.1, 1]
                            ]
                        ]
                    ]
                }
            }
        ]
        """;

    /**
     * Combined benchmark with both health and JWT validation
     */
    public static final String COMBINED_BENCHMARK = """
        [
            {
                "jmhVersion": "1.37",
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                "mode": "sample",
                "primaryMetric": {
                    "scorePercentiles": {
                        "50.0": 7.6513279999999995,
                        "95.0": 13.4529024,
                        "99.0": 29.980098560000016
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [7.6513279999999995, 60]
                            ]
                        ]
                    ]
                }
            },
            {
                "jmhVersion": "1.37",
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtValidationBenchmark.validateAccessTokenThroughput",
                "mode": "sample",
                "primaryMetric": {
                    "scorePercentiles": {
                        "50.0": 15.2,
                        "95.0": 21.3,
                        "99.0": 35.8
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [15.2, 40]
                            ]
                        ]
                    ]
                }
            }
        ]
        """;

    /**
     * Multi-iteration benchmark result
     */
    public static final String MULTI_ITERATION_BENCHMARK = """
        [
            {
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                "mode": "sample",
                "primaryMetric": {
                    "score": 7.5,
                    "scorePercentiles": {
                        "50.0": 7.3,
                        "95.0": 13.7,
                        "99.0": 25.9
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [7.3, 150]
                            ],
                            [
                                [7.5, 250]
                            ]
                        ]
                    ]
                }
            }
        ]
        """;

    /**
     * Mixed mode benchmark (includes both sample and throughput)
     */
    public static final String MIXED_MODE_BENCHMARK = """
        [
            {
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                "mode": "thrpt",
                "primaryMetric": {
                    "score": 1000.0,
                    "scoreUnit": "ops/s"
                }
            },
            {
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                "mode": "sample",
                "primaryMetric": {
                    "score": 8.5,
                    "scorePercentiles": {
                        "50.0": 8.3,
                        "95.0": 14.7,
                        "99.0": 26.9
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [8.3, 30]
                            ]
                        ]
                    ]
                }
            }
        ]
        """;

    /**
     * Benchmark with missing percentiles
     */
    public static final String INCOMPLETE_PERCENTILES_BENCHMARK = """
        [
            {
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                "mode": "sample",
                "primaryMetric": {
                    "scorePercentiles": {
                        "50.0": 8.3
                    },
                    "scoreUnit": "ms/op",
                    "rawDataHistogram": [
                        [
                            [
                                [8.3, 30]
                            ]
                        ]
                    ]
                }
            }
        ]
        """;

    /**
     * Standard Prometheus metrics for Quarkus
     */
    public static final String STANDARD_PROMETHEUS_METRICS = """
        # TYPE system_cpu_usage gauge
        # HELP system_cpu_usage The "recent cpu usage" of the system the application is running in
        system_cpu_usage 0.13922521857923498
        # TYPE process_cpu_usage gauge
        # HELP process_cpu_usage The "recent cpu usage" for the Java Virtual Machine process
        process_cpu_usage 0.1377049180327869
        # TYPE system_cpu_count gauge
        # HELP system_cpu_count The number of processors available to the Java virtual machine
        system_cpu_count 2.0
        # TYPE system_load_average_1m gauge
        # HELP system_load_average_1m The sum of the number of runnable entities queued to available processors
        system_load_average_1m 3.77783203125
        # TYPE jvm_memory_used_bytes gauge
        # HELP jvm_memory_used_bytes The amount of used memory
        jvm_memory_used_bytes{area="heap",id="old generation space"} 7864320.0
        jvm_memory_used_bytes{area="heap",id="eden space"} 5242880.0
        jvm_memory_used_bytes{area="nonheap",id="runtime code cache (code and data)"} 0.0
        jvm_memory_used_bytes{area="heap",id="survivor space"} 0.0
        # TYPE jvm_memory_committed_bytes gauge
        # HELP jvm_memory_committed_bytes The amount of memory in bytes that is committed for the Java virtual machine to use
        jvm_memory_committed_bytes{area="heap",id="old generation space"} 7864320.0
        jvm_memory_committed_bytes{area="heap",id="eden space"} 4718592.0
        jvm_memory_committed_bytes{area="nonheap",id="runtime code cache (code and data)"} 0.0
        jvm_memory_committed_bytes{area="heap",id="survivor space"} 0.0
        # TYPE jvm_memory_max_bytes gauge
        # HELP jvm_memory_max_bytes The maximum amount of memory in bytes that can be used for memory management
        jvm_memory_max_bytes{area="heap",id="old generation space"} -1.0
        jvm_memory_max_bytes{area="heap",id="eden space"} -1.0
        jvm_memory_max_bytes{area="nonheap",id="runtime code cache (code and data)"} -1.0
        jvm_memory_max_bytes{area="heap",id="survivor space"} -1.0
        """;

    /**
     * Minimal Prometheus metrics
     */
    public static final String MINIMAL_PROMETHEUS_METRICS = """
        system_cpu_usage 0.12
        process_cpu_usage 0.10
        system_cpu_count 4.0
        system_load_average_1m 2.5
        jvm_memory_used_bytes{area="heap",id="eden space"} 8388608.0
        jvm_memory_used_bytes{area="heap",id="old generation space"} 12582912.0
        jvm_memory_committed_bytes{area="heap",id="eden space"} 8388608.0
        jvm_memory_committed_bytes{area="heap",id="old generation space"} 12582912.0
        """;

    /**
     * Empty benchmark result
     */
    public static final String EMPTY_BENCHMARK = "[]";

    /**
     * Invalid JSON
     */
    public static final String INVALID_JSON = "{ this is not valid json }";

    /**
     * Benchmark without sample mode
     */
    public static final String NO_SAMPLE_MODE_BENCHMARK = """
        [
            {
                "benchmark": "de.cuioss.jwt.quarkus.benchmark.benchmarks.JwtHealthBenchmark.healthCheckAll",
                "mode": "thrpt",
                "primaryMetric": {
                    "score": 1000.0,
                    "scoreUnit": "ops/s"
                }
            }
        ]
        """;
}