#!/bin/bash
# Optimized script to fetch JWT tokens from Keycloak for high-performance WRK benchmarking
# This version outputs tokens in a format that can be directly passed via environment variable

set -e

KEYCLOAK_URL="${1:-https://localhost:1443}"
REALM="${2:-benchmark}"
CLIENT_ID="${3:-benchmark-client}"
CLIENT_SECRET="${4:-benchmark-secret}"
USERNAME="${5:-benchmark-user}"
PASSWORD="${6:-benchmark-password}"
TOKEN_COUNT="${7:-100}"
OUTPUT_FORMAT="${8:-env}"  # 'env' for environment variable, 'file' for file output
OUTPUT_FILE="${9:-tokens.txt}"

echo "Fetching $TOKEN_COUNT tokens from Keycloak..." >&2
echo "URL: $KEYCLOAK_URL/realms/$REALM" >&2
echo "Output format: $OUTPUT_FORMAT" >&2

# Function to fetch a single token
fetch_token() {
    local response=$(curl -sk -X POST \
        "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=$CLIENT_ID" \
        -d "client_secret=$CLIENT_SECRET" \
        -d "username=$USERNAME" \
        -d "password=$PASSWORD" \
        2>/dev/null)

    echo "$response" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4
}

# Parallel token fetching for better performance
fetch_tokens_parallel() {
    local tokens=()
    local pids=()
    local temp_dir=$(mktemp -d)

    # Launch parallel token fetch jobs
    for i in $(seq 1 $TOKEN_COUNT); do
        (
            token=$(fetch_token)
            if [ -n "$token" ]; then
                echo "$token" > "$temp_dir/token_$i"
            fi
        ) &
        pids+=($!)

        # Limit concurrent requests to avoid overwhelming Keycloak
        if [ ${#pids[@]} -ge 10 ]; then
            wait "${pids[0]}"
            pids=("${pids[@]:1}")
        fi

        # Progress indicator
        if [ $((i % 10)) -eq 0 ]; then
            echo "Launched $i token fetch jobs..." >&2
        fi
    done

    # Wait for all remaining jobs
    for pid in "${pids[@]}"; do
        wait "$pid"
    done

    # Collect all tokens
    for i in $(seq 1 $TOKEN_COUNT); do
        if [ -f "$temp_dir/token_$i" ]; then
            tokens+=("$(cat "$temp_dir/token_$i")")
        fi
    done

    # Cleanup
    rm -rf "$temp_dir"

    # Output tokens based on format
    case "$OUTPUT_FORMAT" in
        env)
            # Output as newline-separated string for environment variable
            # This can be directly passed to wrk via TOKEN_DATA env var
            printf "%s\n" "${tokens[@]}"
            ;;
        file)
            # Traditional file output
            printf "%s\n" "${tokens[@]}" > "$OUTPUT_FILE"
            echo "Saved ${#tokens[@]} tokens to $OUTPUT_FILE" >&2
            ;;
        lua)
            # Output as Lua code that can be embedded
            echo "-- Auto-generated token pool"
            echo "local tokens = {"
            for token in "${tokens[@]}"; do
                echo "    \"$token\","
            done
            echo "}"
            echo "return tokens"
            ;;
        *)
            echo "Unknown output format: $OUTPUT_FORMAT" >&2
            exit 1
            ;;
    esac

    echo "Successfully fetched ${#tokens[@]} tokens" >&2
}

# Main execution
fetch_tokens_parallel