#!/bin/bash
# JWT benchmark runner wrapper for Maven execution
# This script runs the JWT benchmark and ensures metadata is captured in Maven's outputFile

set -e

# Verify required environment variables are set by Maven
: "${KEYCLOAK_URL:?ERROR: KEYCLOAK_URL environment variable is not set}"
: "${REALM:?ERROR: REALM environment variable is not set}"
: "${CLIENT_ID:?ERROR: CLIENT_ID environment variable is not set}"
: "${CLIENT_SECRET:?ERROR: CLIENT_SECRET environment variable is not set}"
: "${USERNAME:?ERROR: USERNAME environment variable is not set}"
: "${PASSWORD:?ERROR: PASSWORD environment variable is not set}"
: "${TOKEN_COUNT:?ERROR: TOKEN_COUNT environment variable is not set}"
: "${SERVICE_URL:?ERROR: SERVICE_URL environment variable is not set}"
: "${WRK_THREADS:?ERROR: WRK_THREADS environment variable is not set}"
: "${WRK_CONNECTIONS:?ERROR: WRK_CONNECTIONS environment variable is not set}"
: "${WRK_DURATION:?ERROR: WRK_DURATION environment variable is not set}"
: "${WRK_TIMEOUT:?ERROR: WRK_TIMEOUT environment variable is not set}"
BENCHMARK_NAME="jwtValidation"

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Record benchmark start time
BENCHMARK_START_TIME=$(date +%s)
BENCHMARK_START_ISO=$(date -Iseconds)

# Output metadata header to stdout (captured by Maven)
echo "=== BENCHMARK METADATA ==="
echo "benchmark_name: $BENCHMARK_NAME"
echo "start_time: $BENCHMARK_START_TIME"
echo "start_time_iso: $BENCHMARK_START_ISO"
echo "=== WRK OUTPUT ==="
echo ""

# Fetch tokens directly to environment variable (no file I/O)
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

TOKEN_LINE_COUNT=$(echo "$TOKEN_DATA" | wc -l | xargs)
echo "Successfully loaded $TOKEN_LINE_COUNT tokens in memory"
echo ""

# Export token data for the Lua script
export TOKEN_DATA

# Run wrk with the optimized script (output goes to stdout for Maven)
wrk \
    -t"$WRK_THREADS" \
    -c"$WRK_CONNECTIONS" \
    -d"$WRK_DURATION" \
    --timeout "$WRK_TIMEOUT" \
    --latency \
    -s "$SCRIPT_DIR/jwt_benchmark.lua" \
    "$SERVICE_URL/jwt/validate"

# Record benchmark end time and output to stdout
BENCHMARK_END_TIME=$(date +%s)
BENCHMARK_END_ISO=$(date -Iseconds)

echo ""
echo "=== BENCHMARK COMPLETE ==="
echo "end_time: $BENCHMARK_END_TIME"
echo "end_time_iso: $BENCHMARK_END_ISO"
echo "duration_seconds: $((BENCHMARK_END_TIME - BENCHMARK_START_TIME))"