#!/bin/bash
# High-performance JWT benchmark runner with in-memory token management
# This script fetches tokens and runs wrk without file I/O

set -e

# Default configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-https://localhost:1443}"
REALM="${REALM:-benchmark}"
CLIENT_ID="${CLIENT_ID:-benchmark-client}"
CLIENT_SECRET="${CLIENT_SECRET:-benchmark-secret}"
USERNAME="${USERNAME:-benchmark-user}"
PASSWORD="${PASSWORD:-benchmark-password}"
TOKEN_COUNT="${TOKEN_COUNT:-100}"
SERVICE_URL="${SERVICE_URL:-https://localhost:10443}"

# WRK configuration
WRK_THREADS="${WRK_THREADS:-4}"
WRK_CONNECTIONS="${WRK_CONNECTIONS:-20}"
WRK_DURATION="${WRK_DURATION:-30s}"
WRK_TIMEOUT="${WRK_TIMEOUT:-2s}"
WRK_SCRIPT="${WRK_SCRIPT:-jwt_benchmark.lua}"

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "==================================================================="
echo "High-Performance JWT Benchmark with In-Memory Tokens"
echo "==================================================================="
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Service URL: $SERVICE_URL"
echo "Token Count: $TOKEN_COUNT"
echo "WRK Configuration: $WRK_THREADS threads, $WRK_CONNECTIONS connections, $WRK_DURATION duration"
echo ""

# Step 1: Fetch tokens directly to environment variable (no file I/O)
echo "Fetching $TOKEN_COUNT tokens from Keycloak (in-memory)..."
TOKEN_DATA=$(bash "$SCRIPT_DIR/fetch_tokens.sh" \
    "$KEYCLOAK_URL" \
    "$REALM" \
    "$CLIENT_ID" \
    "$CLIENT_SECRET" \
    "$USERNAME" \
    "$PASSWORD" \
    "$TOKEN_COUNT" \
    "env" 2>/dev/null)

if [ -z "$TOKEN_DATA" ]; then
    echo "ERROR: Failed to fetch tokens from Keycloak"
    exit 1
fi

# Count tokens
TOKEN_LINE_COUNT=$(echo "$TOKEN_DATA" | grep -c '^' || true)
echo "Successfully loaded $TOKEN_LINE_COUNT tokens in memory"
echo ""

# Step 2: Run wrk benchmark with tokens in environment and system monitoring
echo "Starting WRK benchmark with in-memory tokens and system monitoring..."
echo "-------------------------------------------------------------------"

# Export token data for the Lua script
export TOKEN_DATA

# Start system monitoring in background
MONITORING_LOG="${SCRIPT_DIR}/../../target/benchmark-results/system-metrics.log"
TIMESTAMP_FILE="${SCRIPT_DIR}/../../target/benchmark-results/benchmark-timestamps.txt"
mkdir -p "$(dirname "$MONITORING_LOG")"

# Record benchmark start time
BENCHMARK_START_TIME=$(date +%s)
echo "benchmark_start_time=$BENCHMARK_START_TIME" > "$TIMESTAMP_FILE"
echo "benchmark_start_iso=$(date -Iseconds)" >> "$TIMESTAMP_FILE"

echo "Starting system monitoring (CPU, memory, disk I/O)..."
(
    echo "timestamp,cpu_percent,memory_percent,memory_used_mb,processes,load_avg_1min"
    while true; do
        # Get current timestamp
        TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

        # Get CPU and memory stats using ps and vm_stat on macOS
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS monitoring
            CPU_PERCENT=$(ps -A -o %cpu | awk '{s+=$1} END {printf "%.1f", s}')
            MEMORY_PRESSURE=$(memory_pressure | grep "System-wide memory free percentage" | awk '{print $6}' | tr -d '%')
            MEMORY_PERCENT=$(echo "100 - $MEMORY_PRESSURE" | bc -l 2>/dev/null || echo "0")
            VM_STATS=$(vm_stat | head -6)
            FREE_PAGES=$(echo "$VM_STATS" | grep "Pages free" | awk '{print $3}' | tr -d '.')
            MEMORY_USED_MB=$(echo "($FREE_PAGES * 4096) / 1048576" | bc -l 2>/dev/null || echo "0")
            PROCESSES=$(ps aux | wc -l)
            LOAD_AVG=$(uptime | awk -F'load averages: ' '{print $2}' | awk '{print $1}' | tr -d ',')
        else
            # Linux monitoring
            CPU_PERCENT=$(grep 'cpu ' /proc/stat | awk '{usage=($2+$4)*100/($2+$3+$4+$5)} END {print usage}')
            MEMORY_PERCENT=$(free | grep Mem | awk '{printf("%.1f", $3/$2 * 100.0)}')
            MEMORY_USED_MB=$(free -m | grep Mem | awk '{print $3}')
            PROCESSES=$(ps aux | wc -l)
            LOAD_AVG=$(cat /proc/loadavg | awk '{print $1}')
        fi

        echo "$TIMESTAMP,$CPU_PERCENT,$MEMORY_PERCENT,$MEMORY_USED_MB,$PROCESSES,$LOAD_AVG"
        sleep 2
    done
) > "$MONITORING_LOG" &
MONITORING_PID=$!

echo "System monitoring started (PID: $MONITORING_PID)"

# Run wrk with the optimized script
wrk \
    -t"$WRK_THREADS" \
    -c"$WRK_CONNECTIONS" \
    -d"$WRK_DURATION" \
    --timeout "$WRK_TIMEOUT" \
    --latency \
    -s "$SCRIPT_DIR/$WRK_SCRIPT" \
    "$SERVICE_URL/jwt/validate"

# Stop system monitoring
if [ -n "$MONITORING_PID" ] && kill -0 $MONITORING_PID 2>/dev/null; then
    kill $MONITORING_PID 2>/dev/null || true
    # Don't wait for the killed process as it may hang
    sleep 1
    echo "System monitoring stopped"
fi

# Record benchmark end time
BENCHMARK_END_TIME=$(date +%s)
echo "benchmark_end_time=$BENCHMARK_END_TIME" >> "$TIMESTAMP_FILE"
echo "benchmark_end_iso=$(date -Iseconds)" >> "$TIMESTAMP_FILE"
echo "duration_seconds=$((BENCHMARK_END_TIME - BENCHMARK_START_TIME))" >> "$TIMESTAMP_FILE"

echo "System metrics saved to: $MONITORING_LOG"
echo "Benchmark timestamps saved to: $TIMESTAMP_FILE"

echo "-------------------------------------------------------------------"
echo "Benchmark complete!"