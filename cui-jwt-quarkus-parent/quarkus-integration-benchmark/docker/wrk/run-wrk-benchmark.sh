#!/bin/bash

# Docker-based wrk benchmark runner for JWT validation
# This script runs wrk in a Docker container to avoid local installation

set -euo pipefail

# Configuration
WRK_IMAGE="cui-jwt-wrk:latest"
QUARKUS_URL="http://host.docker.internal:10443"
RESULTS_DIR="./target/wrk-results"
THREADS=${1:-200}
CONNECTIONS=${2:-200}
DURATION=${3:-30s}
ERROR_RATE=${4:-0}

echo "🚀 Starting Docker-based wrk benchmark..."
echo "  Target: $QUARKUS_URL"
echo "  Threads: $THREADS"
echo "  Connections: $CONNECTIONS"
echo "  Duration: $DURATION"
echo "  Error rate: $ERROR_RATE%"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Build wrk Docker image if it doesn't exist
if ! docker image inspect "$WRK_IMAGE" >/dev/null 2>&1; then
    echo "📦 Building wrk Docker image..."
    docker build -t "$WRK_IMAGE" ./docker/wrk/
fi

# Run wrk benchmark in Docker container
echo "🏃 Running wrk benchmark..."
docker run --rm \
    --network host \
    -v "$PWD/$RESULTS_DIR:/tmp" \
    -e WRK_ERROR_RATE="$ERROR_RATE" \
    "$WRK_IMAGE" \
    wrk \
    -t "$THREADS" \
    -c "$CONNECTIONS" \
    -d "$DURATION" \
    --latency \
    --script /benchmark/scripts/jwt-validation.lua \
    "$QUARKUS_URL/jwt/validate"

# Check if results were generated
if [ -f "$RESULTS_DIR/wrk-results.json" ]; then
    echo "✅ Benchmark completed successfully!"
    echo "📊 Results saved to: $RESULTS_DIR/wrk-results.json"
    
    # Display summary
    echo ""
    echo "=== Summary ==="
    jq -r '
        "Throughput: " + (.throughput_rps | tostring) + " req/sec",
        "Latency P95: " + (.latency.p95 | tostring) + "ms",
        "Latency P99: " + (.latency.p99 | tostring) + "ms",
        "Total Requests: " + (.requests | tostring),
        "Duration: " + (.duration_ms | tostring) + "ms"
    ' "$RESULTS_DIR/wrk-results.json"
else
    echo "❌ No results file generated"
    exit 1
fi

echo "🎉 Docker-based wrk benchmark complete!"