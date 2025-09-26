#!/bin/bash
# Health live check benchmark runner with metadata embedding
# This script runs the health/live endpoint benchmark and embeds metadata for Prometheus integration

set -e

# Verify required environment variables are set by Maven
: "${SERVICE_URL:?ERROR: SERVICE_URL environment variable is not set}"
: "${WRK_THREADS:?ERROR: WRK_THREADS environment variable is not set}"
: "${WRK_CONNECTIONS:?ERROR: WRK_CONNECTIONS environment variable is not set}"
: "${WRK_DURATION:?ERROR: WRK_DURATION environment variable is not set}"
: "${WRK_TIMEOUT:?ERROR: WRK_TIMEOUT environment variable is not set}"
BENCHMARK_NAME="healthLiveCheck"

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Record benchmark start time
BENCHMARK_START_TIME=$(date +%s)
BENCHMARK_START_ISO=$(date -Iseconds)

# Output metadata header (this goes to stdout which Maven captures)
echo "=== BENCHMARK METADATA ==="
echo "benchmark_name: $BENCHMARK_NAME"
echo "start_time: $BENCHMARK_START_TIME"
echo "start_time_iso: $BENCHMARK_START_ISO"
echo "=== WRK OUTPUT ==="
echo ""

# Run wrk health check benchmark
wrk \
    -t"$WRK_THREADS" \
    -c"$WRK_CONNECTIONS" \
    -d"$WRK_DURATION" \
    --timeout "$WRK_TIMEOUT" \
    --latency \
    -s "$SCRIPT_DIR/health_live_check.lua" \
    "$SERVICE_URL/q/health/live"

# Record benchmark end time
BENCHMARK_END_TIME=$(date +%s)
BENCHMARK_END_ISO=$(date -Iseconds)

# Output metadata footer
echo ""
echo "=== BENCHMARK COMPLETE ==="
echo "end_time: $BENCHMARK_END_TIME"
echo "end_time_iso: $BENCHMARK_END_ISO"
echo "duration_seconds: $((BENCHMARK_END_TIME - BENCHMARK_START_TIME))"