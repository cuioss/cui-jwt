#!/bin/bash

# Docker-based wrk benchmark with JFR profiling
# This script captures JFR data while running performance tests

set -euo pipefail

# Configuration - Optimized for Docker containers
WRK_IMAGE="cui-jwt-wrk:latest"
QUARKUS_URL="https://host.docker.internal:10443"
RESULTS_DIR="./target/benchmark-results"
JFR_DIR="./target/jfr-results"

# Performance settings following wrk best practices
THREADS=${1:-4}  # Conservative thread count to reduce load
CONNECTIONS=${2:-80}  # Reduced connections to avoid timeouts
DURATION=${3:-60s}  # Longer duration for stable JFR profiling
ERROR_RATE=${4:-0}
REALM=${5:-benchmark}

echo "🚀 Starting Docker-based wrk benchmark with JFR profiling..."
echo "  Target: $QUARKUS_URL"
echo "  Threads: $THREADS"
echo "  Connections: $CONNECTIONS" 
echo "  Duration: $DURATION"
echo "  JFR profiling: ENABLED"

# Create results directories
mkdir -p "$RESULTS_DIR"
mkdir -p "$JFR_DIR"

# Build wrk Docker image if it doesn't exist
if ! docker image inspect "$WRK_IMAGE" >/dev/null 2>&1; then
    echo "📦 Building wrk Docker image..."
    docker build -t "$WRK_IMAGE" ./docker/wrk/
fi

# Multi-realm token support
TOKENS_DIR="./target/tokens"
REALM_TOKENS_DIR="$TOKENS_DIR/$REALM"

# Check for realm-specific tokens first, then fall back to default location
if [ -f "$REALM_TOKENS_DIR/access_token.txt" ]; then
    echo "🔑 Loading real JWT tokens from realm: $REALM..."
    ACCESS_TOKEN=$(cat "$REALM_TOKENS_DIR/access_token.txt" | tr -d '\n')
    ID_TOKEN=$(cat "$REALM_TOKENS_DIR/id_token.txt" | tr -d '\n' 2>/dev/null || echo "")
    REFRESH_TOKEN=$(cat "$REALM_TOKENS_DIR/refresh_token.txt" | tr -d '\n' 2>/dev/null || echo "")
    
    TOKEN_ENV_VARS="-e ACCESS_TOKEN=$ACCESS_TOKEN -e ID_TOKEN=$ID_TOKEN -e REFRESH_TOKEN=$REFRESH_TOKEN -e REALM=$REALM"
    echo "  ✅ Real token mode (realm: $REALM): $(echo "$ACCESS_TOKEN" | cut -c1-20)..."
elif [ -f "$TOKENS_DIR/access_token.txt" ]; then
    echo "🔑 Loading real JWT tokens from default location..."
    ACCESS_TOKEN=$(cat "$TOKENS_DIR/access_token.txt" | tr -d '\n')
    ID_TOKEN=$(cat "$TOKENS_DIR/id_token.txt" | tr -d '\n' 2>/dev/null || echo "")
    REFRESH_TOKEN=$(cat "$TOKENS_DIR/refresh_token.txt" | tr -d '\n' 2>/dev/null || echo "")
    
    TOKEN_ENV_VARS="-e ACCESS_TOKEN=$ACCESS_TOKEN -e ID_TOKEN=$ID_TOKEN -e REFRESH_TOKEN=$REFRESH_TOKEN -e REALM=$REALM"
    echo "  ✅ Real token mode (default): $(echo "$ACCESS_TOKEN" | cut -c1-20)..."
else
    echo "❌ No real tokens found - JFR profiling requires real tokens"
    echo "  Searched in: $REALM_TOKENS_DIR/ and $TOKENS_DIR/"
    exit 1
fi

# Start JFR recording on Quarkus container
echo "📊 Starting JFR recording on Quarkus container..."
QUARKUS_CONTAINER=$(docker ps --filter "publish=10443" --format "{{.Names}}" | head -n1)
if [ -z "$QUARKUS_CONTAINER" ]; then
    echo "❌ Quarkus container not found. Please start integration tests first."
    exit 1
fi

echo "  Container: $QUARKUS_CONTAINER"

# Start JFR recording via jcmd
JFR_FILENAME="jwt-benchmark-$(date +%Y%m%d_%H%M%S).jfr"
echo "  Starting JFR recording: $JFR_FILENAME"

# Get the process ID of the Quarkus application
QUARKUS_PID=$(docker exec "$QUARKUS_CONTAINER" sh -c "pgrep -f 'cui-jwt-quarkus-integration-tests' || echo '1'")
echo "  Quarkus PID: $QUARKUS_PID"

# Start JFR recording
docker exec "$QUARKUS_CONTAINER" sh -c "jcmd $QUARKUS_PID JFR.start name=benchmark duration=${DURATION} filename=/tmp/$JFR_FILENAME" || true

# Run wrk benchmark with reduced load to avoid overwhelming the application
echo "🏃 Running wrk benchmark with JFR profiling..."
docker run --rm \
    --network cui-jwt-quarkus-integration-tests_jwt-integration \
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
    --script /benchmark/scripts/jwt-benchmark.lua \
    "https://cui-jwt-integration-tests:8443/jwt/validate"

# Stop JFR recording and copy results
echo "📊 Stopping JFR recording..."
docker exec "$QUARKUS_CONTAINER" sh -c "jcmd $QUARKUS_PID JFR.stop name=benchmark" || true

# Copy JFR file from container
echo "📥 Copying JFR results..."
docker cp "$QUARKUS_CONTAINER:/tmp/$JFR_FILENAME" "$JFR_DIR/" || echo "⚠️ Could not copy JFR file"

# Validate results
echo ""
echo "🔧 Performance Configuration:"
echo "  Docker CPUs: 6 cores allocated (reduced load)"
echo "  Docker Memory: 512MB allocated"
echo "  Threads: $THREADS"
echo "  Connections: $CONNECTIONS ($(echo "scale=0; $CONNECTIONS / $THREADS" | bc) per thread)"
echo "  Duration: $DURATION"
echo "  JFR file: $JFR_DIR/$JFR_FILENAME"

# Check if results were generated
if [ -f "$RESULTS_DIR/jwt-validation-results.json" ]; then
    echo "✅ Benchmark completed successfully!"
    echo "📊 Results saved to: $RESULTS_DIR/jwt-validation-results.json"
    
    # Display summary
    echo ""
    echo "=== Performance Summary ==="
    if command -v jq >/dev/null 2>&1 && [ -s "$RESULTS_DIR/jwt-validation-results.json" ]; then
        jq -r '
            "Throughput: " + (.throughput_rps | floor | tostring) + " req/sec",
            "Latency P95: " + (.latency_p95_ms | tostring) + "ms", 
            "Latency P99: " + (.latency_p99_ms | tostring) + "ms",
            "Errors: " + (.errors | tostring)
        ' "$RESULTS_DIR/jwt-validation-results.json" 2>/dev/null || echo "Results file format issue"
    fi
else
    echo "❌ No results file generated - check Docker logs for errors"
    exit 1
fi

# JFR Analysis instructions
echo ""
echo "=== JFR Analysis Instructions ==="
echo "1. JFR file location: $JFR_DIR/$JFR_FILENAME"
echo "2. Open with JDK Mission Control:"
echo "   jmc $JFR_DIR/$JFR_FILENAME"
echo "3. Key areas to analyze:"
echo "   - Method profiling (CPU hotspots)"
echo "   - Memory allocation"
echo "   - Garbage collection"
echo "   - I/O operations"
echo "   - Thread contention"

echo "🎉 JFR-enabled benchmark complete!"