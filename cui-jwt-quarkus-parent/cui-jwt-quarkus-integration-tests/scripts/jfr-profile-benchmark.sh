#!/bin/bash
# JFR Profiling Script for JWT Validation Workload
# This script runs the benchmark with JFR recording to identify performance hotspots

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTEGRATION_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$INTEGRATION_DIR/../.." && pwd)"
BENCHMARK_DIR="$PROJECT_ROOT/cui-jwt-quarkus-parent/quarkus-integration-benchmark"
RESULTS_DIR="$BENCHMARK_DIR/jfr-results"

echo "🔍 Starting JFR-enabled JWT validation profiling"
echo "📁 Project root: $PROJECT_ROOT"
echo "📁 Integration dir: $INTEGRATION_DIR"
echo "📁 Benchmark dir: $BENCHMARK_DIR"

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

# Step 3: Run benchmark with JFR profiling (simplified approach)
echo ""
echo "📊 Step 3: Running benchmark with JFR-enabled native application..."
echo "⚠️  JFR recording will be handled by the native application startup"
echo "📝 Note: JFR files will be generated in the application working directory"

# Run benchmark for profiling (2+ minutes as required)
cd "$PROJECT_ROOT"
echo "🚀 Starting JWT validation benchmark..."

# Use timeout if available, otherwise rely on user cancellation
if command -v timeout >/dev/null 2>&1; then
    timeout 150s ./mvnw verify -pl cui-jwt-quarkus-parent/quarkus-integration-benchmark -Pintegration-benchmarks \
        2>&1 | tee "$RESULTS_DIR/benchmark-with-jfr.log" || true
else
    echo "⏰ Run for 2+ minutes, then press Ctrl+C to stop"
    ./mvnw verify -pl cui-jwt-quarkus-parent/quarkus-integration-benchmark -Pintegration-benchmarks \
        2>&1 | tee "$RESULTS_DIR/benchmark-with-jfr.log" || true
fi

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