#!/bin/bash

# Docker-based wrk benchmark runner for JWT validation
# This script runs wrk in a Docker container to avoid local installation

set -euo pipefail

# Configuration - Optimized for Apple M4 (10 CPU cores)
WRK_IMAGE="cui-jwt-wrk:latest"
QUARKUS_URL="https://host.docker.internal:10443"
RESULTS_DIR="./target/wrk-results"

# Performance settings following wrk best practices  
# Threads: Optimized for stable performance without timeouts
THREADS=${1:-6}
# Connections: 30 per thread for optimal distribution (180/6=30)  
CONNECTIONS=${2:-180}
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

# Fetch JWT tokens if not using mock tokens
TOKENS_DIR="./target/tokens"
if [ -f "$TOKENS_DIR/access_token.txt" ]; then
    echo "🔑 Loading real JWT tokens..."
    ACCESS_TOKEN=$(cat "$TOKENS_DIR/access_token.txt" | tr -d '\n')
    TOKEN_ENV_VARS="-e ACCESS_TOKEN=$ACCESS_TOKEN"
    echo "  ✅ Real token mode: $(echo "$ACCESS_TOKEN" | cut -c1-20)..."
else
    echo "🧪 Using mock tokens (no real tokens found)"
    TOKEN_ENV_VARS=""
fi

# Run wrk benchmark in Docker container with conservative resources
echo "🏃 Running wrk benchmark..."
docker run --rm \
    --network host \
    --cpus="6" \
    --memory="512m" \
    --ulimit nofile=32768:32768 \
    -v "$PWD/$RESULTS_DIR:/tmp" \
    -e WRK_ERROR_RATE="$ERROR_RATE" \
    $TOKEN_ENV_VARS \
    "$WRK_IMAGE" \
    wrk \
    -t "$THREADS" \
    -c "$CONNECTIONS" \
    -d "$DURATION" \
    --latency \
    --script /benchmark/scripts/jwt-working.lua \
    "$QUARKUS_URL/jwt/validate"

# Validate performance settings
echo "🔧 Performance Configuration:"
echo "  Docker CPUs: 6 cores allocated"
echo "  Docker Memory: 512MB allocated"
echo "  File descriptors: 32768 (ulimit)"
echo "  Threads: $THREADS (conservative for stability)"
echo "  Connections: $CONNECTIONS ($(echo "scale=0; $CONNECTIONS / $THREADS" | bc) per thread)"
echo "  Duration: $DURATION"

# Check if results were generated
if [ -f "$RESULTS_DIR/wrk-results.json" ]; then
    echo "✅ Benchmark completed successfully!"
    echo "📊 Results saved to: $RESULTS_DIR/wrk-results.json"
    
    # Display summary with proper formatting
    echo ""
    echo "=== Performance Summary ==="
    if command -v jq >/dev/null 2>&1 && [ -s "$RESULTS_DIR/wrk-results.json" ]; then
        jq -r '
            "Throughput: " + (.throughput_rps | floor | tostring) + " req/sec",
            "Latency P95: " + (.latency_p95_ms | tostring) + "ms", 
            "Latency P99: " + (.latency_p99_ms | tostring) + "ms",
            "Errors: " + (.errors | tostring)
        ' "$RESULTS_DIR/wrk-results.json" 2>/dev/null || echo "Results file format issue - raw output above"
    else
        echo "📄 Results processing skipped (file empty or jq unavailable)"
    fi
else
    echo "❌ No results file generated - check Docker logs for errors"
    exit 1
fi

echo "🎉 Docker-based wrk benchmark complete!"