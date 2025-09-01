# Benchmark Library

Micro-benchmarks for JWT validation library performance testing.

## Purpose

This module executes direct performance tests against the JWT validation library, measuring raw performance characteristics without network or container overhead.

## Key Features

- **Library Performance Testing**: Direct benchmarking of JWT validation components
- **JFR Integration**: Java Flight Recorder support for detailed profiling
- **Multiple Benchmark Types**: Standard and JFR-instrumented variants
- **Performance Scoring**: Composite metrics for throughput, latency, and error resilience

## Benchmark Profiles

### Standard Benchmarks
```bash
./mvnw clean verify -pl benchmarking/benchmark-library -Pbenchmark
```
- Execution time: < 10 minutes
- Configuration: 5 iterations, 3 warmup, 100 threads

### JFR Profiling
```bash
./mvnw clean verify -pl benchmarking/benchmark-library -Pbenchmark-jfr
```
- Includes Java Flight Recorder profiling
- Generates detailed performance analysis

## Key Benchmarks

- `SimpleCoreValidationBenchmark` - Core JWT validation performance
- `SimpleErrorLoadBenchmark` - Error handling and resilience testing
- `TokenCacheBenchmark` - Token caching effectiveness

## Architecture

See [Architecture.adoc](../Architecture.adoc) for detailed module responsibilities and code placement guidelines.

## Results

Benchmark results are generated in:
```
target/benchmark-results/
├── badges/                  # Performance badges
├── reports/                 # HTML reports
└── micro-benchmark-result.json  # Raw JMH results
```

## Dependencies

- `cui-benchmarking-common` - Shared infrastructure
- `cui-jwt-validation` - Library being benchmarked
- JMH - Benchmark execution framework
- HdrHistogram - Accurate latency recording