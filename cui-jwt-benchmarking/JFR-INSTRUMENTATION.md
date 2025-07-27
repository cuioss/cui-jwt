# JFR Instrumentation for JWT Benchmark Variance Analysis

## Overview

This module includes Java Flight Recorder (JFR) instrumentation to measure operation time variance under concurrent load during JWT validation benchmarks. JFR provides production-ready, low-overhead profiling that captures detailed timing information for analyzing performance variance.

## Quick Start

### Running Standard Benchmarks

```bash
# Run standard benchmarks (without JFR instrumentation)
mvn verify -Pbenchmark
```

### Running JFR-Instrumented Benchmarks

```bash
# Run JFR-instrumented benchmarks with variance analysis
mvn verify -Pbenchmark-jfr

# JFR recording will be saved to:
# target/benchmark-results/jfr-benchmark.jfr
```

### Analyzing Results

```bash
# View variance analysis summary
java -cp target/classes:target/dependency/* \
  de.cuioss.jwt.validation.benchmark.jfr.JfrVarianceAnalyzer \
  target/benchmark-results/jfr-benchmark.jfr
```

## What Gets Measured

### Individual Operations
- **Duration**: Precise timing of each JWT validation operation
- **Metadata**: Token size, issuer, thread information
- **Concurrency**: Number of concurrent operations at execution time
- **Success/Failure**: Operation outcome with error details

### Periodic Statistics (1-second intervals)
- **Latency Percentiles**: P50, P95, P99
- **Variance Metrics**: Standard deviation, coefficient of variation
- **Throughput**: Operations per second
- **Concurrency**: Active thread count

## Understanding Variance

The **Coefficient of Variation (CV)** is the key metric for understanding performance consistency:

| CV Range | Interpretation | Typical Cause |
|----------|----------------|---------------|
| < 25% | Low variance, consistent performance | Well-optimized code path |
| 25-50% | Moderate variance, acceptable | Normal concurrent contention |
| > 50% | High variance, investigate | Lock contention, GC pauses |

## JFR Events Reference

### `de.cuioss.jwt.Operation`
Records each individual JWT operation with timing and metadata.

**Fields:**
- `operationType`: Type of operation (validation, parsing, etc.)
- `benchmarkName`: Name of the benchmark
- `duration`: Operation duration (auto-captured)
- `tokenSize`: Size of JWT token in bytes
- `issuer`: Token issuer identifier
- `success`: Operation success status
- `concurrentOperations`: Number of concurrent ops

### `de.cuioss.jwt.OperationStatistics`
Periodic statistics snapshot (emitted every second).

**Fields:**
- `sampleCount`: Operations in this period
- `p50Latency`, `p95Latency`, `p99Latency`: Latency percentiles
- `variance`: Statistical variance
- `coefficientOfVariation`: CV percentage
- `concurrentThreads`: Active thread count

## Advanced Analysis

### Using JDK Tools

```bash
# Print all JWT operation events
jfr print --events de.cuioss.jwt.Operation benchmark.jfr

# Export to JSON for custom analysis
jfr print --json --events de.cuioss.jwt.* benchmark.jfr > jwt-events.json

# View event summary
jfr summary benchmark.jfr
```

### Custom Analysis

```java
// Programmatic analysis example
Path jfrFile = Path.of("benchmark.jfr");
JfrVarianceAnalyzer analyzer = new JfrVarianceAnalyzer();
VarianceReport report = analyzer.analyze(jfrFile);

// Access detailed metrics
Map<String, Object> metrics = report.toJson();
```

### Visualizing with JDK Mission Control

1. Download [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)
2. Open the JFR file: `File > Open File > benchmark.jfr`
3. Navigate to "Event Browser" tab
4. Filter for `de.cuioss.jwt.*` events
5. Use the "Latency" view to visualize operation timing

## Benchmark Profiles

### Standard Profile (`benchmark`)
- Runs all performance benchmarks (PerformanceIndicatorBenchmark, ErrorLoadBenchmark)
- Optimized for fast execution (<10 minutes)
- Standard JMH metrics without JFR overhead
- Use when: You need quick performance metrics

### JFR Profile (`benchmark-jfr`)
- Runs only JFR-instrumented benchmarks
- Uses the same JMH settings as the standard profile
- Captures detailed timing and variance metrics
- Automatic JFR recording generation
- Use when: You need to analyze performance variance or debug inconsistent performance

## Implementation Details

### Low Overhead Design
- Event allocation minimized through object pooling
- Conditional commit based on duration thresholds
- Efficient percentile tracking with HdrHistogram
- Periodic aggregation reduces event volume

### Integration Points
- Separate benchmark runner for JFR mode
- Compatible with existing metrics collection
- No impact on standard benchmarks
- Profile-based activation

## Troubleshooting

### No JFR File Generated
- Ensure Java 11+ is used (JFR is built-in)
- Check Maven output for JFR-related errors
- Verify `-XX:StartFlightRecording` in process arguments

### High Overhead Observed
- Reduce event frequency with custom JFC configuration
- Increase statistics period (default: 1 second)
- Disable stack traces if not needed

### Analysis Errors
- Ensure JFR file is not corrupted
- Check Java version compatibility
- Verify all dependencies are on classpath

## Configuration

### Custom JFR Settings

Create a custom JFC file to control event collection:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <event name="de.cuioss.jwt.Operation">
    <setting name="enabled">true</setting>
    <setting name="threshold">0 ms</setting>
  </event>
  <event name="de.cuioss.jwt.OperationStatistics">
    <setting name="enabled">true</setting>
    <setting name="period">1 s</setting>
  </event>
</configuration>
```

### Maven Configuration

The JFR recording is configured in the JfrBenchmarkRunner class:

```java
.jvmArgs("-XX:+UnlockDiagnosticVMOptions", 
        "-XX:+DebugNonSafepoints",
        "-XX:StartFlightRecording=filename=target/benchmark-results/jfr-benchmark.jfr,settings=profile");
```

To run with custom settings:

```bash
mvn verify -Pbenchmark-jfr \
  -Djmh.iterations=10 \
  -Djmh.threads=16 \
  -Djmh.time=10s
```

## Best Practices

1. **Baseline First**: Run benchmarks without JFR to establish baseline
2. **Multiple Runs**: Collect multiple recordings for statistical validity
3. **Analyze Trends**: Look for variance patterns over time
4. **Correlate Events**: Cross-reference with GC and system events
5. **Production Ready**: Same instrumentation can be used in production with minimal overhead