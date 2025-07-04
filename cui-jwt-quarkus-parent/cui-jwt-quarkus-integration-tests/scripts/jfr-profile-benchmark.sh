#!/bin/bash
# JFR Profiling Script for JWT Validation Workload
# This script runs the benchmark with JFR recording to identify performance hotspots

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BENCHMARK_DIR="$PROJECT_ROOT/quarkus-integration-benchmark"
RESULTS_DIR="$BENCHMARK_DIR/jfr-results"

echo "🔍 Starting JFR-enabled JWT validation profiling"
echo "📁 Project root: $PROJECT_ROOT"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Step 1: Build integration tests with JFR support
echo ""
echo "🔨 Step 1: Building integration tests with JFR support (~6 seconds)..."
cd "$PROJECT_ROOT"
./mvnw clean install -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests

# Step 2: Build native executable with JFR monitoring enabled
echo ""
echo "🔨 Step 2: Building native executable with JFR monitoring (~1m 30s)..."
./mvnw clean package -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests -Pintegration-tests

# Step 3: Run benchmark with JFR recording
echo ""
echo "📊 Step 3: Running benchmark with JFR recording (2+ minutes)..."
echo "⚠️  JFR will record CPU hotspots, allocations, and I/O operations"

# Set JFR system properties for the benchmark JVM
export JFR_ARGS="-XX:+FlightRecorder -XX:StartFlightRecording=duration=120s,filename=$RESULTS_DIR/jwt-validation-profile.jfr,settings=profile"

# Run benchmark with JFR recording
cd "$PROJECT_ROOT"
timeout 150s ./mvnw clean verify -pl cui-jwt-quarkus-parent/quarkus-integration-benchmark -Pintegration-benchmarks \
    -Djvm.args="$JFR_ARGS" \
    2>&1 | tee "$RESULTS_DIR/benchmark-with-jfr.log" || true

echo ""
echo "✅ JFR profiling completed!"
echo "📊 Results available in: $RESULTS_DIR"
echo ""
echo "📁 Generated files:"
if [ -f "$RESULTS_DIR/jwt-validation-profile.jfr" ]; then
    echo "  ✅ JFR recording: $RESULTS_DIR/jwt-validation-profile.jfr"
    echo "     Size: $(du -h "$RESULTS_DIR/jwt-validation-profile.jfr" | cut -f1)"
else
    echo "  ❌ JFR recording not found - check if native image supports JFR"
fi

if [ -f "$RESULTS_DIR/benchmark-with-jfr.log" ]; then
    echo "  ✅ Benchmark log: $RESULTS_DIR/benchmark-with-jfr.log"
    
    # Extract performance metrics from log
    echo ""
    echo "📈 Performance Summary:"
    grep -E "ops/s" "$RESULTS_DIR/benchmark-with-jfr.log" | tail -5 || echo "  No performance metrics found in log"
fi

echo ""
echo "🔍 Next steps:"
echo "  1. Analyze JFR file with JDK Mission Control or jfr command"
echo "  2. Look for CPU hotspots in JWT validation pipeline"
echo "  3. Identify allocation-heavy methods"
echo "  4. Check for I/O blocking operations"
echo ""
echo "📝 Analysis commands:"
echo "  jfr summary $RESULTS_DIR/jwt-validation-profile.jfr"
echo "  jfr print --events CPUSample $RESULTS_DIR/jwt-validation-profile.jfr"
echo "  jfr print --events ObjectAllocationInNewTLAB $RESULTS_DIR/jwt-validation-profile.jfr"