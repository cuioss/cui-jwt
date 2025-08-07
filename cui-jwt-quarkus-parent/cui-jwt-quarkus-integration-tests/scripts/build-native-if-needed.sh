#!/bin/bash
set -e

# Script to build native executable if it doesn't exist

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Check if native executable exists
RUNNER_FILE=$(find "${PROJECT_DIR}/target" -name "*-runner" -type f 2>/dev/null | head -n 1)

if [[ -z "$RUNNER_FILE" ]]; then
    echo "🔨 Native executable not found, building it now..."
    echo "This will take approximately 2 minutes..."
    
    # Detect which profile to use
    # MAVEN_PROFILE can be set by the exec-maven-plugin configuration
    if [[ -n "$MAVEN_PROFILE" ]]; then
        PROFILE="$MAVEN_PROFILE"
        echo "Using profile from environment: $PROFILE"
    else
        # Default to integration-tests profile
        PROFILE="-Pintegration-tests"
        echo "Using default profile: integration-tests"
    fi
    
    # Build native executable using Quarkus Maven plugin
    # Find the root directory with mvnw
    ROOT_DIR=$(cd "${PROJECT_DIR}/../.." && pwd)
    cd "${ROOT_DIR}"
    ./mvnw --no-transfer-progress ${PROFILE} quarkus:build -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests
    
    # Verify it was created
    RUNNER_FILE=$(find "${PROJECT_DIR}/target" -name "*-runner" -type f 2>/dev/null | head -n 1)
    if [[ -z "$RUNNER_FILE" ]]; then
        echo "❌ Failed to build native executable"
        exit 1
    fi
    echo "✅ Native executable built: $(basename "$RUNNER_FILE")"
else
    echo "✅ Native executable already exists: $(basename "$RUNNER_FILE")"
fi