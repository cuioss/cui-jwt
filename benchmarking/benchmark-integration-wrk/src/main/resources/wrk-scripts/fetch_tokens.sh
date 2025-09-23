#!/bin/bash
# Script to fetch real JWT tokens from Keycloak for WRK benchmarking

KEYCLOAK_URL="${1:-https://localhost:1443}"
REALM="${2:-benchmark}"
CLIENT_ID="${3:-benchmark-client}"
CLIENT_SECRET="${4:-benchmark-secret}"
USERNAME="${5:-benchmark-user}"
PASSWORD="${6:-benchmark-password}"
TOKEN_COUNT="${7:-1000}"
OUTPUT_FILE="${8:-tokens.txt}"

echo "Fetching $TOKEN_COUNT tokens from Keycloak..."
echo "URL: $KEYCLOAK_URL/realms/$REALM"

# Clear output file
> "$OUTPUT_FILE"

for i in $(seq 1 $TOKEN_COUNT); do
    # Fetch token from Keycloak
    RESPONSE=$(curl -sk -X POST \
        "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=$CLIENT_ID" \
        -d "client_secret=$CLIENT_SECRET" \
        -d "username=$USERNAME" \
        -d "password=$PASSWORD" \
        2>/dev/null)

    # Extract access token from JSON response
    TOKEN=$(echo "$RESPONSE" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

    if [ -n "$TOKEN" ]; then
        echo "$TOKEN" >> "$OUTPUT_FILE"
        if [ $((i % 100)) -eq 0 ]; then
            echo "Fetched $i tokens..."
        fi
    else
        echo "Failed to fetch token $i"
    fi

    # Small delay to avoid overwhelming Keycloak
    sleep 0.01
done

echo "Successfully fetched $(wc -l < "$OUTPUT_FILE") tokens to $OUTPUT_FILE"