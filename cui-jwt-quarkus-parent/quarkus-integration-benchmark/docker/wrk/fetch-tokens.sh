#!/bin/bash

# Fetch real JWT tokens from Keycloak for wrk testing
# This script mimics the TestRealm.obtainValidToken() functionality

set -euo pipefail

KEYCLOAK_BASE_URL="https://localhost:1443"
REALM="benchmark"
CLIENT_ID="benchmark-client"
CLIENT_SECRET="benchmark-secret"
USERNAME="benchmark-user"
PASSWORD="benchmark-password"

echo "🔑 Fetching JWT tokens from Keycloak..."
echo "  Realm: $REALM"
echo "  Client: $CLIENT_ID"
echo "  User: $USERNAME"

# Fetch valid token
echo "📥 Requesting access token..."
TOKEN_RESPONSE=$(curl -k -s -X POST \
    "$KEYCLOAK_BASE_URL/realms/$REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=$CLIENT_ID" \
    -d "client_secret=$CLIENT_SECRET" \
    -d "username=$USERNAME" \
    -d "password=$PASSWORD" \
    -d "grant_type=password" \
    -d "scope=openid profile email")

# Check if request was successful
if echo "$TOKEN_RESPONSE" | jq -e '.access_token' >/dev/null 2>&1; then
    echo "✅ Successfully obtained tokens"
    
    # Extract tokens
    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
    ID_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.id_token // empty')
    REFRESH_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.refresh_token // empty')
    
    # Save tokens to files for wrk scripts
    mkdir -p target/tokens
    echo "$ACCESS_TOKEN" > target/tokens/access_token.txt
    echo "$ID_TOKEN" > target/tokens/id_token.txt
    echo "$REFRESH_TOKEN" > target/tokens/refresh_token.txt
    
    echo "💾 Tokens saved to target/tokens/"
    echo "  Access token length: ${#ACCESS_TOKEN} characters"
    echo "  ID token length: ${#ID_TOKEN} characters"
    echo "  Refresh token length: ${#REFRESH_TOKEN} characters"
    
    # Also create a JSON file with all tokens for easy access
    cat > target/tokens/tokens.json << EOF
{
    "access_token": "$ACCESS_TOKEN",
    "id_token": "$ID_TOKEN",
    "refresh_token": "$REFRESH_TOKEN"
}
EOF
    
    echo "📄 Combined tokens saved to target/tokens/tokens.json"
    
    # Test token validity with a quick request
    echo "🧪 Testing token validity..."
    VALIDATION_RESPONSE=$(curl -k -s -X POST \
        "https://localhost:10443/jwt/validate" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -d "{\"token\":\"$ACCESS_TOKEN\"}")
    
    if echo "$VALIDATION_RESPONSE" | jq -e '.valid' >/dev/null 2>&1; then
        IS_VALID=$(echo "$VALIDATION_RESPONSE" | jq -r '.valid')
        if [ "$IS_VALID" = "true" ]; then
            echo "✅ Token validation successful"
        else
            echo "❌ Token validation failed: $VALIDATION_RESPONSE"
        fi
    else
        echo "⚠️  Token validation response: $VALIDATION_RESPONSE"
    fi
    
else
    echo "❌ Failed to obtain tokens"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
fi

echo "🎉 Token fetching complete!"