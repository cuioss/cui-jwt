#!/bin/bash

# Docker-based wrk benchmark runner for JWT validation
# This script runs wrk in a Docker container to avoid local installation

set -euo pipefail

# Configuration - Optimized for Apple M4 (10 CPU cores)
WRK_IMAGE="cui-jwt-wrk:latest"
QUARKUS_URL="https://cui-jwt-integration-tests:8443"
RESULTS_DIR="./target/benchmark-results"

# Performance settings following wrk best practices  
# Threads: Reduced for stability and to avoid overwhelming the service
THREADS=${1:-4}
# Connections: 20 per thread for optimal distribution (80/4=20)  
CONNECTIONS=${2:-80}
DURATION=${3:-30s}
ERROR_RATE=${4:-0}
REALM=${5:-benchmark}

echo "🚀 Starting Docker-based wrk benchmark..."
echo "  Target: $QUARKUS_URL"
echo "  Threads: $THREADS"
echo "  Connections: $CONNECTIONS"
echo "  Duration: $DURATION"
echo "  Error rate: $ERROR_RATE%"
echo "  Realm: $REALM"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Build wrk Docker image if it doesn't exist
if ! docker image inspect "$WRK_IMAGE" >/dev/null 2>&1; then
    echo "📦 Building wrk Docker image..."
    docker build -t "$WRK_IMAGE" ./docker/wrk/
fi

# Multi-realm token support
REALM=${5:-benchmark}
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
    echo "🧪 Using mock tokens (no real tokens found)"
    echo "  Searched in: $REALM_TOKENS_DIR/ and $TOKENS_DIR/"
    TOKEN_ENV_VARS=""
fi

# Run wrk benchmark in Docker container with conservative resources
echo "🏃 Running wrk benchmark..."
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
    "$QUARKUS_URL/jwt/validate" | grep -v "Non-2xx or 3xx responses:"

# Validate performance settings
echo "🔧 Performance Configuration:"
echo "  Docker CPUs: 6 cores allocated"
echo "  Docker Memory: 512MB allocated"
echo "  File descriptors: 32768 (ulimit)"
echo "  Threads: $THREADS (conservative for stability)"
echo "  Connections: $CONNECTIONS ($(echo "scale=0; $CONNECTIONS / $THREADS" | bc) per thread)"
echo "  Duration: $DURATION"

# Check if results were generated
if [ -f "$RESULTS_DIR/jwt-validation-results.json" ]; then
    echo "✅ JWT validation benchmark completed successfully!"
    echo "📊 Results saved to: $RESULTS_DIR/jwt-validation-results.json"
    
    # Display summary
    echo ""
    echo "=== JWT Validation Performance Summary ==="
    if command -v jq >/dev/null 2>&1 && [ -s "$RESULTS_DIR/jwt-validation-results.json" ]; then
        jq -r '
            "Throughput: " + (.throughput_rps | floor | tostring) + " req/sec",
            "Latency P95: " + (.latency_p95_ms | tostring) + "ms (JWT validation)", 
            "Latency P99: " + (.latency_p99_ms | tostring) + "ms",
            "Errors: " + (.errors | tostring)
        ' "$RESULTS_DIR/jwt-validation-results.json" 2>/dev/null || echo "Results file format issue"
    fi
    
    # Compare with health check baseline if available
    if [ -f "$RESULTS_DIR/health-check-results.json" ]; then
        echo ""
        echo "=== Performance Comparison ==="
        echo "Comparing JWT validation vs health check (system baseline):"
        
        HEALTH_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/health-check-results.json" 2>/dev/null || echo "0")
        JWT_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/jwt-validation-results.json" 2>/dev/null || echo "0")
        
        if [ "$HEALTH_P95" != "0" ] && [ "$JWT_P95" != "0" ]; then
            JWT_OVERHEAD=$(echo "scale=1; $JWT_P95 - $HEALTH_P95" | bc)
            echo "  Health Check P95: ${HEALTH_P95}ms (system baseline)"
            echo "  JWT Validation P95: ${JWT_P95}ms"
            echo "  JWT Processing Overhead: ${JWT_OVERHEAD}ms"
        fi
    fi
else
    echo "❌ No benchmark results file generated - check Docker logs for errors"
    exit 1
fi

echo "🎉 Docker-based wrk benchmark complete!"