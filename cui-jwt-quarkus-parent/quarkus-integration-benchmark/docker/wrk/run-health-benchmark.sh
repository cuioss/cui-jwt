#!/bin/bash

# Docker-based wrk benchmark for health check endpoint
# Measures pure system latency without JWT validation overhead

set -euo pipefail

# Configuration
WRK_IMAGE="cui-jwt-wrk:latest"
QUARKUS_URL="https://cui-jwt-integration-tests:8443"
RESULTS_DIR="./target/benchmark-results"

# Performance settings optimized for health check endpoint
THREADS=${1:-4}
CONNECTIONS=${2:-80}
DURATION=${3:-30s}

echo "🏥 Starting health check benchmark..."
echo "  Target: $QUARKUS_URL/q/health/live"
echo "  Threads: $THREADS"
echo "  Connections: $CONNECTIONS"
echo "  Duration: $DURATION"
echo "  Purpose: Measure pure system latency (no JWT validation)"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Build wrk Docker image if it doesn't exist
if ! docker image inspect "$WRK_IMAGE" >/dev/null 2>&1; then
    echo "📦 Building wrk Docker image..."
    docker build -t "$WRK_IMAGE" ./docker/wrk/
fi

# Run health check benchmark
echo "🏃 Running health check benchmark..."
docker run --rm \
    --network cui-jwt-quarkus-integration-tests_jwt-integration \
    --cpus="6" \
    --memory="512m" \
    --ulimit nofile=32768:32768 \
    -v "$PWD/$RESULTS_DIR:/tmp" \
    "$WRK_IMAGE" \
    wrk \
    -t "$THREADS" \
    -c "$CONNECTIONS" \
    -d "$DURATION" \
    --latency \
    --script /benchmark/scripts/health-check.lua \
    "$QUARKUS_URL/q/health/live"

# Validate performance settings
echo ""
echo "🔧 Performance Configuration:"
echo "  Test Type: Health Check (system baseline)"
echo "  Docker CPUs: 6 cores allocated"
echo "  Docker Memory: 512MB allocated"
echo "  Threads: $THREADS"
echo "  Connections: $CONNECTIONS ($((CONNECTIONS / THREADS)) per thread)"
echo "  Duration: $DURATION"

# Check if results were generated
if [ -f "$RESULTS_DIR/health-check-results.json" ]; then
    echo "✅ Health check benchmark completed successfully!"
    echo "📊 Results saved to: $RESULTS_DIR/health-check-results.json"
    
    # Display summary
    echo ""
    echo "=== Health Check Performance Summary ==="
    if command -v jq >/dev/null 2>&1 && [ -s "$RESULTS_DIR/health-check-results.json" ]; then
        jq -r '
            "Throughput: " + (.throughput_rps | floor | tostring) + " req/sec",
            "Latency P95: " + (.latency_p95_ms | tostring) + "ms (system baseline)", 
            "Latency P99: " + (.latency_p99_ms | tostring) + "ms",
            "Errors: " + (.errors | tostring)
        ' "$RESULTS_DIR/health-check-results.json" 2>/dev/null || echo "Results file format issue"
    fi
    
    # Compare with JWT validation if available
    if [ -f "$RESULTS_DIR/jwt-validation-results.json" ]; then
        echo ""
        echo "=== Performance Comparison ==="
        echo "Comparing health check (system baseline) vs JWT validation:"
        
        HEALTH_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/health-check-results.json" 2>/dev/null || echo "0")
        JWT_P95=$(jq -r '.latency_p95_ms' "$RESULTS_DIR/jwt-validation-results.json" 2>/dev/null || echo "0")
        
        if [ "$HEALTH_P95" != "0" ] && [ "$JWT_P95" != "0" ]; then
            JWT_OVERHEAD=$(awk -v jwt="$JWT_P95" -v health="$HEALTH_P95" 'BEGIN {printf "%.1f", jwt - health}')
            echo "  Health Check P95: ${HEALTH_P95}ms (system baseline)"
            echo "  JWT Validation P95: ${JWT_P95}ms"
            echo "  JWT Processing Overhead: ${JWT_OVERHEAD}ms"
        fi
    fi
else
    echo "❌ No health check results file generated - check Docker logs for errors"
    exit 1
fi

echo "🎉 Health check benchmark complete!"