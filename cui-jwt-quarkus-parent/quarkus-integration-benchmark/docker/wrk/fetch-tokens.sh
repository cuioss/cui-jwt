#!/bin/bash

# Fetch real JWT tokens from Keycloak for wrk testing
# This script mimics the TestRealm.obtainValidToken() functionality
# Supports multiple realms: integration and benchmark

set -euo pipefail

KEYCLOAK_BASE_URL="https://localhost:1443"

# Realm configurations based on TestRealm.java
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

# Default realm (can be overridden with first parameter)
DEFAULT_REALM="benchmark"
REALM=${1:-$DEFAULT_REALM}

# Validate realm parameter
REALM_CONFIG_STRING=$(get_realm_config "$REALM")
if [[ -z "$REALM_CONFIG_STRING" ]]; then
    echo "❌ Invalid realm: $REALM"
    echo "Available realms: integration, benchmark"
    exit 1
fi

# Parse realm configuration
IFS=':' read -r CLIENT_ID CLIENT_SECRET USERNAME PASSWORD <<< "$REALM_CONFIG_STRING"

echo "🔑 Fetching JWT tokens from Keycloak..."
echo "  Realm: $REALM"
echo "  Client: $CLIENT_ID"
echo "  User: $USERNAME"

# Function to fetch tokens for a specific realm
fetch_tokens_for_realm() {
    local realm=$1
    local client_id=$2
    local client_secret=$3
    local username=$4
    local password=$5
    
    echo "📥 Requesting tokens for realm: $realm..."
    
    TOKEN_RESPONSE=$(curl -k -s -X POST \
        "$KEYCLOAK_BASE_URL/realms/$realm/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "client_id=$client_id" \
        -d "client_secret=$client_secret" \
        -d "username=$username" \
        -d "password=$password" \
        -d "grant_type=password" \
        -d "scope=openid profile email read")
    
    # Check if request was successful
    if echo "$TOKEN_RESPONSE" | jq -e '.access_token' >/dev/null 2>&1; then
        echo "✅ Successfully obtained tokens for realm: $realm"
        
        # Extract tokens
        ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
        ID_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.id_token // empty')
        REFRESH_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.refresh_token // empty')
        
        # Save tokens to files for wrk scripts
        mkdir -p "target/tokens/$realm"
        echo "$ACCESS_TOKEN" > "target/tokens/$realm/access_token.txt"
        echo "$ID_TOKEN" > "target/tokens/$realm/id_token.txt"
        echo "$REFRESH_TOKEN" > "target/tokens/$realm/refresh_token.txt"
        
        # Also create a JSON file with all tokens for easy access
        cat > "target/tokens/$realm/tokens.json" << EOF
{
    "realm": "$realm",
    "access_token": "$ACCESS_TOKEN",
    "id_token": "$ID_TOKEN",
    "refresh_token": "$REFRESH_TOKEN"
}
EOF
        
        echo "💾 Tokens saved to target/tokens/$realm/"
        echo "  Access token length: ${#ACCESS_TOKEN} characters"
        echo "  ID token length: ${#ID_TOKEN} characters"
        echo "  Refresh token length: ${#REFRESH_TOKEN} characters"
        
        # Test token validity with a quick request
        echo "🧪 Testing token validity for realm: $realm..."
        VALIDATION_RESPONSE=$(curl -k -s -X POST \
            "https://localhost:10443/jwt/validate" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            -d "{\"token\":\"$ACCESS_TOKEN\"}")
        
        if echo "$VALIDATION_RESPONSE" | jq -e '.valid' >/dev/null 2>&1; then
            IS_VALID=$(echo "$VALIDATION_RESPONSE" | jq -r '.valid')
            if [ "$IS_VALID" = "true" ]; then
                echo "✅ Token validation successful for realm: $realm"
            else
                echo "❌ Token validation failed for realm: $realm: $VALIDATION_RESPONSE"
            fi
        else
            echo "⚠️  Token validation response for realm: $realm: $VALIDATION_RESPONSE"
        fi
        
        return 0
    else
        echo "❌ Failed to obtain tokens for realm: $realm"
        echo "Response: $TOKEN_RESPONSE"
        return 1
    fi
}

# Fetch tokens for the specified realm
fetch_tokens_for_realm "$REALM" "$CLIENT_ID" "$CLIENT_SECRET" "$USERNAME" "$PASSWORD"

# Create backward compatibility files for existing wrk scripts
mkdir -p target/tokens
cp "target/tokens/$REALM/access_token.txt" target/tokens/access_token.txt
cp "target/tokens/$REALM/id_token.txt" target/tokens/id_token.txt
cp "target/tokens/$REALM/refresh_token.txt" target/tokens/refresh_token.txt
cp "target/tokens/$REALM/tokens.json" target/tokens/tokens.json

echo "🔄 Backward compatibility files created in target/tokens/"

# If running in multi-realm mode, fetch tokens for all realms
if [[ "${FETCH_ALL_REALMS:-false}" == "true" ]]; then
    echo "🌐 Fetching tokens for all realms..."
    ALL_REALMS="integration benchmark"
    for realm_name in $ALL_REALMS; do
        if [[ "$realm_name" != "$REALM" ]]; then
            realm_config=$(get_realm_config "$realm_name")
            IFS=':' read -r client_id client_secret username password <<< "$realm_config"
            fetch_tokens_for_realm "$realm_name" "$client_id" "$client_secret" "$username" "$password"
        fi
    done
    
    # Create a consolidated tokens file
    cat > target/tokens/all_realms.json << EOF
{
    "realms": {
EOF
    
    first=true
    for realm_name in $ALL_REALMS; do
        if [[ "$first" == "true" ]]; then
            first=false
        else
            echo "," >> target/tokens/all_realms.json
        fi
        
        echo "        \"$realm_name\": {" >> target/tokens/all_realms.json
        if [[ -f "target/tokens/$realm_name/tokens.json" ]]; then
            jq -r '.access_token' "target/tokens/$realm_name/tokens.json" | sed 's/^/            "access_token": "/' | sed 's/$/",/' >> target/tokens/all_realms.json
            jq -r '.id_token' "target/tokens/$realm_name/tokens.json" | sed 's/^/            "id_token": "/' | sed 's/$/",/' >> target/tokens/all_realms.json
            jq -r '.refresh_token' "target/tokens/$realm_name/tokens.json" | sed 's/^/            "refresh_token": "/' | sed 's/$/"/' >> target/tokens/all_realms.json
        fi
        echo "        }" >> target/tokens/all_realms.json
    done
    
    echo "    }" >> target/tokens/all_realms.json
    echo "}" >> target/tokens/all_realms.json
    
    echo "🗂️  Consolidated tokens saved to target/tokens/all_realms.json"
fi

echo "🎉 Token fetching complete!"
echo ""
echo "Usage examples:"
echo "  # Fetch tokens for benchmark realm (default)"
echo "  ./fetch-tokens.sh"
echo "  # Fetch tokens for integration realm"
echo "  ./fetch-tokens.sh integration"
echo "  # Fetch tokens for all realms"
echo "  FETCH_ALL_REALMS=true ./fetch-tokens.sh"