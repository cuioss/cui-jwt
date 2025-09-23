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
WRK_SCRIPT="${WRK_SCRIPT:-jwt_optimized.lua}"

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
TOKEN_DATA=$(bash "$SCRIPT_DIR/fetch_tokens_optimized.sh" \
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

# Step 2: Run wrk benchmark with tokens in environment
echo "Starting WRK benchmark with in-memory tokens..."
echo "-------------------------------------------------------------------"

# Export token data for the Lua script
export TOKEN_DATA

# Run wrk with the optimized script
wrk \
    -t"$WRK_THREADS" \
    -c"$WRK_CONNECTIONS" \
    -d"$WRK_DURATION" \
    --timeout "$WRK_TIMEOUT" \
    --latency \
    -s "$SCRIPT_DIR/$WRK_SCRIPT" \
    "$SERVICE_URL/jwt/validate"

echo "-------------------------------------------------------------------"
echo "Benchmark complete!"