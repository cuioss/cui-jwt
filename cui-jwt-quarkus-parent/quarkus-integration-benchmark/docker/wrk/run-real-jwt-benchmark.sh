#!/bin/bash

# Docker-based wrk benchmark runner with real JWT tokens from Keycloak
# This script fetches real tokens and runs performance tests

set -euo pipefail

# Configuration
WRK_IMAGE="cui-jwt-wrk:latest"
QUARKUS_URL="https://host.docker.internal:10443"
RESULTS_DIR="./target/wrk-results"
THREADS=${1:-200}
CONNECTIONS=${2:-200}
DURATION=${3:-30s}

echo "🏆 Real JWT Validation Benchmark"
echo "  Target: $QUARKUS_URL"
echo "  Threads: $THREADS"
echo "  Connections: $CONNECTIONS"
echo "  Duration: $DURATION"

# Step 1: Fetch real JWT tokens from Keycloak
echo ""
echo "🔑 Step 1: Fetching real JWT tokens from Keycloak..."
./docker/wrk/fetch-tokens.sh

# Step 2: Load tokens from files
echo ""
echo "📥 Step 2: Loading tokens for benchmark..."
if [ -f "target/tokens/access_token.txt" ]; then
    ACCESS_TOKEN=$(cat target/tokens/access_token.txt)
    echo "✅ Access token loaded (${#ACCESS_TOKEN} chars)"
else
    echo "❌ Access token not found"
    exit 1
fi

if [ -f "target/tokens/id_token.txt" ]; then
    ID_TOKEN=$(cat target/tokens/id_token.txt)
    echo "✅ ID token loaded (${#ID_TOKEN} chars)"
else
    echo "⚠️  ID token not found"
    ID_TOKEN=""
fi

if [ -f "target/tokens/refresh_token.txt" ]; then
    REFRESH_TOKEN=$(cat target/tokens/refresh_token.txt)
    echo "✅ Refresh token loaded (${#REFRESH_TOKEN} chars)"
else
    echo "⚠️  Refresh token not found"
    REFRESH_TOKEN=""
fi

# Step 3: Create results directory
mkdir -p "$RESULTS_DIR"

# Step 4: Build wrk Docker image if needed
if ! docker image inspect "$WRK_IMAGE" >/dev/null 2>&1; then
    echo ""
    echo "📦 Step 3: Building wrk Docker image..."
    docker build -t "$WRK_IMAGE" ./docker/wrk/
fi

# Step 5: Run wrk benchmark with real tokens
echo ""
echo "🚀 Step 4: Running wrk benchmark with real JWT tokens..."
docker run --rm \
    --network host \
    -v "$PWD/$RESULTS_DIR:/tmp" \
    -e ACCESS_TOKEN="$ACCESS_TOKEN" \
    -e ID_TOKEN="$ID_TOKEN" \
    -e REFRESH_TOKEN="$REFRESH_TOKEN" \
    "$WRK_IMAGE" \
    wrk \
    -t "$THREADS" \
    -c "$CONNECTIONS" \
    -d "$DURATION" \
    --latency \
    --script /benchmark/scripts/real-jwt-validation.lua \
    "$QUARKUS_URL/jwt/validate"

# Step 6: Check results and generate summary
echo ""
echo "📊 Step 5: Processing results..."
if [ -f "$RESULTS_DIR/wrk-results.json" ]; then
    echo "✅ Benchmark completed successfully!"
    echo "📄 Results saved to: $RESULTS_DIR/wrk-results.json"
    
    # Display performance summary
    echo ""
    echo "🎯 === PERFORMANCE SUMMARY ==="
    THROUGHPUT=$(jq -r '.throughput_rps' "$RESULTS_DIR/wrk-results.json")
    LATENCY_P95=$(jq -r '.latency.p95' "$RESULTS_DIR/wrk-results.json")
    LATENCY_P99=$(jq -r '.latency.p99' "$RESULTS_DIR/wrk-results.json")
    REQUESTS=$(jq -r '.requests' "$RESULTS_DIR/wrk-results.json")
    TOKENS_USED=$(jq -r '.tokens_used' "$RESULTS_DIR/wrk-results.json")
    
    echo "📈 Throughput: ${THROUGHPUT} req/sec"
    echo "⏱️  Latency P95: ${LATENCY_P95}ms"
    echo "⏱️  Latency P99: ${LATENCY_P99}ms"
    echo "📊 Total Requests: ${REQUESTS}"
    echo "🔑 Tokens Used: ${TOKENS_USED}"
    
    # Compare with JMH results if available
    if [ -f "./target/benchmark-results/integration-benchmark-result.json" ]; then
        echo ""
        echo "🔄 === COMPARISON WITH JMH ==="
        JMH_THROUGHPUT=$(jq -r '.[] | select(.benchmark | contains("measureThroughput")) | .primaryMetric.score' ./target/benchmark-results/integration-benchmark-result.json 2>/dev/null || echo "N/A")
        JMH_LATENCY=$(jq -r '.[] | select(.benchmark | contains("measureAverageTime")) | .primaryMetric.score' ./target/benchmark-results/integration-benchmark-result.json 2>/dev/null || echo "N/A")
        
        echo "🔧 JMH Results:"
        echo "   Throughput: ${JMH_THROUGHPUT} ops/s"
        echo "   Latency: ${JMH_LATENCY} ms"
        echo ""
        echo "⚡ wrk Results:"
        echo "   Throughput: ${THROUGHPUT} req/sec"
        echo "   Latency P95: ${LATENCY_P95}ms"
        
        if [ "$JMH_THROUGHPUT" != "N/A" ] && [ "$THROUGHPUT" != "null" ]; then
            THROUGHPUT_DIFF=$(echo "scale=1; ($THROUGHPUT - $JMH_THROUGHPUT) / $JMH_THROUGHPUT * 100" | bc -l 2>/dev/null || echo "N/A")
            echo ""
            echo "📊 Performance Difference:"
            echo "   Throughput: ${THROUGHPUT_DIFF}% (wrk vs JMH)"
        fi
    fi
    
else
    echo "❌ No results file generated"
    echo "   Check the benchmark logs above for errors"
    exit 1
fi

echo ""
echo "🎉 Real JWT validation benchmark complete!"
echo "📁 All results saved in: $RESULTS_DIR/"