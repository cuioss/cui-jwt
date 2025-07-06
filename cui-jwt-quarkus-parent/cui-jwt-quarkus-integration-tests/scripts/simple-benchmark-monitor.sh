#!/bin/bash
# Simple Benchmark Monitor - captures resource usage during JWT benchmarks

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Navigate from scripts -> cui-jwt-quarkus-integration-tests -> cui-jwt-quarkus-parent -> cui-jwt
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/cui-jwt-quarkus-parent/quarkus-integration-benchmark/monitoring-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo "🔍 JWT Benchmark Resource Monitor (Simplified)"
echo "📁 Results directory: $RESULTS_DIR"
echo ""

mkdir -p "$RESULTS_DIR"

# Output files
STATS_FILE="$RESULTS_DIR/resource-stats-$TIMESTAMP.txt"
BENCHMARK_LOG="$RESULTS_DIR/benchmark-$TIMESTAMP.log"

# Function to get simple resource stats
get_stats() {
    echo "=== Resource Stats at $(date) ===" >> "$STATS_FILE"
    
    # System info
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "CPU Cores: $(sysctl -n hw.ncpu)" >> "$STATS_FILE"
        echo "Load Average: $(uptime | awk -F'load averages:' '{print $2}')" >> "$STATS_FILE"
        # Use top for CPU usage
        echo "CPU Usage:" >> "$STATS_FILE"
        top -l 1 | grep "CPU usage" >> "$STATS_FILE"
        # Memory info
        echo "Memory Info:" >> "$STATS_FILE"
        vm_stat | grep -E "Pages (free|active|inactive|wired)" >> "$STATS_FILE"
    else
        echo "CPU Cores: $(nproc)" >> "$STATS_FILE"
        echo "Load Average: $(uptime)" >> "$STATS_FILE"
        echo "CPU Usage:" >> "$STATS_FILE"
        top -bn1 | head -5 >> "$STATS_FILE"
        echo "Memory Info:" >> "$STATS_FILE"
        free -h >> "$STATS_FILE"
    fi
    
    # Docker container stats
    echo "Container Stats:" >> "$STATS_FILE"
    docker stats --no-stream 2>/dev/null >> "$STATS_FILE" || echo "No containers running" >> "$STATS_FILE"
    
    echo "" >> "$STATS_FILE"
}

# Build integration tests
echo "🔨 Building integration tests..."
cd "$PROJECT_ROOT"
if [[ ! -f "./mvnw" ]]; then
    echo "❌ Error: mvnw not found in $PROJECT_ROOT"
    exit 1
fi
./mvnw clean install -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests -q

echo "🚀 Starting benchmark with resource monitoring..."
echo "📊 Stats will be captured every 10 seconds"
echo ""

# Start benchmark in background
./mvnw verify -pl cui-jwt-quarkus-parent/quarkus-integration-benchmark -Pintegration-benchmarks > "$BENCHMARK_LOG" 2>&1 &
BENCHMARK_PID=$!

# Wait for startup
echo "⏳ Waiting for containers to start..."
sleep 15

# Monitor for 2 minutes
DURATION=120
START_TIME=$(date +%s)
ITERATION=0

echo "📊 Monitoring started (2 minutes)..."
while kill -0 $BENCHMARK_PID 2>/dev/null; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [[ $ELAPSED -ge $DURATION ]]; then
        echo "⏰ 2-minute monitoring period completed"
        break
    fi
    
    # Capture stats
    get_stats
    ITERATION=$((ITERATION + 1))
    echo "  Captured snapshot #$ITERATION (${ELAPSED}s elapsed)"
    
    # Show latest throughput
    if [[ -f "$BENCHMARK_LOG" ]]; then
        LATEST_OPS=$(grep "ops/s" "$BENCHMARK_LOG" 2>/dev/null | tail -1 || echo "")
        if [[ -n "$LATEST_OPS" ]]; then
            echo "  Latest: $LATEST_OPS"
        fi
    fi
    
    sleep 10
done

# Final stats
echo ""
echo "📊 Capturing final statistics..."
get_stats

# Stop benchmark if still running
if kill -0 $BENCHMARK_PID 2>/dev/null; then
    echo "🛑 Stopping benchmark..."
    kill $BENCHMARK_PID 2>/dev/null || true
fi

# Wait for containers to stop
sleep 5

# Generate summary
SUMMARY_FILE="$RESULTS_DIR/summary-$TIMESTAMP.txt"
echo "=== Benchmark Resource Utilization Summary ===" > "$SUMMARY_FILE"
echo "Timestamp: $TIMESTAMP" >> "$SUMMARY_FILE"
echo "Duration: ${ELAPSED}s" >> "$SUMMARY_FILE"
echo "Snapshots: $ITERATION" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

# Extract key metrics
echo "=== Performance Metrics ===" >> "$SUMMARY_FILE"
if [[ -f "$BENCHMARK_LOG" ]]; then
    echo "Throughput measurements:" >> "$SUMMARY_FILE"
    grep "ops/s" "$BENCHMARK_LOG" | tail -10 >> "$SUMMARY_FILE" || echo "No throughput data found" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"
echo "=== Resource Usage Observations ===" >> "$SUMMARY_FILE"

# Analyze CPU usage from captured data
if [[ "$OSTYPE" == "darwin"* ]]; then
    CPU_VALUES=$(grep -A1 "CPU usage" "$STATS_FILE" | grep -v "CPU usage" | awk '{print $3}' | sed 's/%//')
    if [[ -n "$CPU_VALUES" ]]; then
        echo "CPU Usage samples: $CPU_VALUES" >> "$SUMMARY_FILE"
    fi
else
    CPU_IDLE=$(grep "Cpu(s):" "$STATS_FILE" | awk -F',' '{print $4}' | awk '{print $1}' | sed 's/%id//')
    if [[ -n "$CPU_IDLE" ]]; then
        echo "CPU Idle percentages: $CPU_IDLE" >> "$SUMMARY_FILE"
    fi
fi

# Container analysis
echo "" >> "$SUMMARY_FILE"
echo "=== Container Resource Usage ===" >> "$SUMMARY_FILE"
grep -A10 "CONTAINER ID" "$STATS_FILE" | grep -E "jwt|keycloak" >> "$SUMMARY_FILE" || echo "No container data captured" >> "$SUMMARY_FILE"

echo "" >> "$SUMMARY_FILE"
echo "=== Recommendations ===" >> "$SUMMARY_FILE"
echo "Review $STATS_FILE for detailed resource snapshots" >> "$SUMMARY_FILE"
echo "Check $BENCHMARK_LOG for complete benchmark output" >> "$SUMMARY_FILE"

# Display summary
echo ""
echo "✅ Monitoring completed!"
echo ""
cat "$SUMMARY_FILE"
echo ""
echo "📄 Files generated:"
echo "  - Resource snapshots: $STATS_FILE"
echo "  - Benchmark log: $BENCHMARK_LOG"
echo "  - Summary: $SUMMARY_FILE"