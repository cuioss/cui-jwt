#!/bin/bash

# Comprehensive benchmark comparison script
# Runs both JMH and wrk benchmarks for performance comparison

set -euo pipefail

BENCHMARK_TYPE="${1:-both}"  # jmh, wrk, or both
THREADS="${2:-200}"
DURATION="${3:-30s}"

echo "🏆 JWT Integration Benchmark Comparison"
echo "  Type: $BENCHMARK_TYPE"
echo "  Threads: $THREADS"
echo "  Duration: $DURATION"
echo ""

# Ensure Quarkus container is running
if ! curl -s -f http://localhost:10443/q/health/live >/dev/null 2>&1; then
    echo "❌ Quarkus container not running on port 10443"
    echo "   Please start the integration test environment first"
    exit 1
fi

# Create results directory
mkdir -p target/benchmark-results

# Run JMH benchmark
if [ "$BENCHMARK_TYPE" = "jmh" ] || [ "$BENCHMARK_TYPE" = "both" ]; then
    echo "🚀 Running JMH Integration Benchmark..."
    
    # Start JMH benchmark (existing implementation)
    ./mvnw clean compile exec:java \
        -Dexec.mainClass="org.openjdk.jmh.Main" \
        -Dexec.args="CoreIntegrationBenchmark -f 1 -i 2 -wi 1 -r 2s -w 2s -t $THREADS" \
        -Djmh.result.format=JSON \
        -Djmh.result.filePrefix=target/benchmark-results/integration-benchmark-result \
        -q || true
    
    echo "✅ JMH benchmark completed"
fi

# Run wrk benchmark
if [ "$BENCHMARK_TYPE" = "wrk" ] || [ "$BENCHMARK_TYPE" = "both" ]; then
    echo "🚀 Running wrk Integration Benchmark..."
    
    # Run Docker-based wrk benchmark
    ./docker/wrk/run-wrk-benchmark.sh "$THREADS" "$THREADS" "$DURATION"
    
    # Process wrk results to JMH format
    ./docker/wrk/process-wrk-results.sh
    
    echo "✅ wrk benchmark completed"
fi

# Generate comparison report
if [ "$BENCHMARK_TYPE" = "both" ]; then
    echo ""
    echo "📊 === BENCHMARK COMPARISON REPORT ==="
    echo ""
    
    # JMH Results
    if [ -f "target/benchmark-results/integration-benchmark-result.json" ]; then
        echo "🔧 JMH Integration Results:"
        JMH_THROUGHPUT=$(jq -r '.[] | select(.benchmark | contains("measureThroughput")) | .primaryMetric.score' target/benchmark-results/integration-benchmark-result.json)
        JMH_LATENCY=$(jq -r '.[] | select(.benchmark | contains("measureAverageTime")) | .primaryMetric.score' target/benchmark-results/integration-benchmark-result.json)
        echo "  Throughput: ${JMH_THROUGHPUT} ops/s"
        echo "  Latency: ${JMH_LATENCY} ms"
    fi
    
    # wrk Results
    if [ -f "target/benchmark-results/wrk-benchmark-result.json" ]; then
        echo ""
        echo "⚡ wrk Integration Results:"
        WRK_THROUGHPUT=$(jq -r '.[] | select(.benchmark | contains("measureThroughput")) | .primaryMetric.score' target/benchmark-results/wrk-benchmark-result.json)
        WRK_LATENCY=$(jq -r '.[] | select(.benchmark | contains("measureAverageTime")) | .primaryMetric.score' target/benchmark-results/wrk-benchmark-result.json)
        echo "  Throughput: ${WRK_THROUGHPUT} ops/s"
        echo "  Latency: ${WRK_LATENCY} ms"
        
        # Calculate differences
        if [ -n "${JMH_THROUGHPUT:-}" ] && [ -n "${WRK_THROUGHPUT:-}" ]; then
            echo ""
            echo "📈 Performance Difference:"
            THROUGHPUT_DIFF=$(echo "scale=2; ($WRK_THROUGHPUT - $JMH_THROUGHPUT) / $JMH_THROUGHPUT * 100" | bc -l)
            LATENCY_DIFF=$(echo "scale=2; ($WRK_LATENCY - $JMH_LATENCY) / $JMH_LATENCY * 100" | bc -l)
            echo "  Throughput: ${THROUGHPUT_DIFF}% difference"
            echo "  Latency: ${LATENCY_DIFF}% difference"
        fi
    fi
    
    echo ""
    echo "🎯 Tool Recommendation:"
    echo "  - JMH: Microbenchmarking, CPU-bound operations"
    echo "  - wrk: HTTP benchmarking, network-bound operations"
    echo "  - For integration tests: wrk is more appropriate"
fi

echo ""
echo "🎉 Benchmark comparison complete!"
echo "📁 Results saved in: target/benchmark-results/"