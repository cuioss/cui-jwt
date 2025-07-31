#!/bin/bash

# Fetch multiple JWT tokens from Keycloak for rotation testing
# Generates 10 unique tokens with slight variations to ensure cache misses

set -euo pipefail

KEYCLOAK_BASE_URL="https://localhost:1443"
REALM=${1:-benchmark}
TOKEN_COUNT=${2:-10}

# Realm configurations
get_realm_config() {
    local realm=$1
    case $realm in
        "integration")
            echo "integration-client:integration-secret:integration-user:integration-password"
            ;;
        "benchmark")
            echo "benchmark-client:benchmark-secret:benchmark-user:benchmark-password"
            ;;
        *)
            echo ""
            ;;
    esac
}

# Validate realm
REALM_CONFIG_STRING=$(get_realm_config "$REALM")
if [[ -z "$REALM_CONFIG_STRING" ]]; then
    echo "❌ Invalid realm: $REALM"
    exit 1
fi

IFS=':' read -r CLIENT_ID CLIENT_SECRET USERNAME PASSWORD <<< "$REALM_CONFIG_STRING"

echo "🔑 Fetching $TOKEN_COUNT JWT tokens from Keycloak..."
echo "  Realm: $REALM"
echo "  Client: $CLIENT_ID"
echo "  User: $USERNAME"

# Create output directory
mkdir -p "target/tokens/$REALM/multiple"

# Function to add a nonce to force new token generation
generate_nonce() {
    echo $(date +%s%N)_$1
}

# Fetch multiple tokens
for i in $(seq 1 $TOKEN_COUNT); do
    echo "📥 Requesting token $i/$TOKEN_COUNT..."
    
    # Add a small delay to ensure different timestamps
    sleep 0.1
    
    # Request token with nonce to ensure uniqueness
    NONCE=$(generate_nonce $i)
    TOKEN_RESPONSE=$(curl -k -s -X POST \
        "$KEYCLOAK_BASE_URL/realms/$REALM/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "client_id=$CLIENT_ID" \
        -d "client_secret=$CLIENT_SECRET" \
        -d "username=$USERNAME" \
        -d "password=$PASSWORD" \
        -d "grant_type=password" \
        -d "scope=openid profile email" \
        -d "nonce=$NONCE")
    
    if echo "$TOKEN_RESPONSE" | jq -e '.access_token' >/dev/null 2>&1; then
        ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
        echo "$ACCESS_TOKEN" > "target/tokens/$REALM/multiple/access_token_$i.txt"
        echo "  ✅ Token $i saved (length: ${#ACCESS_TOKEN})"
        
        # Export as environment variable for the benchmark script
        export ACCESS_TOKEN_$i="$ACCESS_TOKEN"
    else
        echo "  ❌ Failed to obtain token $i"
        echo "  Response: $TOKEN_RESPONSE"
    fi
done

# Create a summary file
echo "📊 Creating token summary..."
cat > "target/tokens/$REALM/multiple/summary.txt" << EOF
Token Generation Summary
========================
Realm: $REALM
Tokens Generated: $TOKEN_COUNT
Timestamp: $(date)

Purpose: Multiple unique tokens for cache ratio testing
Target Cache Ratio: 10% (each token used 10 times)
Expected Validations: $((TOKEN_COUNT * 10))
EOF

echo "✅ Successfully generated $TOKEN_COUNT tokens"
echo "💾 Tokens saved to: target/tokens/$REALM/multiple/"
echo ""
echo "To use with rotating benchmark:"
echo "  export ACCESS_TOKEN_1=\$(cat target/tokens/$REALM/multiple/access_token_1.txt)"
echo "  export ACCESS_TOKEN_2=\$(cat target/tokens/$REALM/multiple/access_token_2.txt)"
echo "  ... etc ..."
echo "  ./docker/wrk/run-wrk-benchmark.sh 4 80 30s 0 $REALM jwt-rotating"