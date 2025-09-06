#!/bin/bash

# JWT Integration Tests Container Log Dumping Script
# Usage: ./dump-quarkus-logs.sh <target-directory>
# Example: ./dump-quarkus-logs.sh target
# Example: ./dump-quarkus-logs.sh ../../benchmarking/benchmark-integration-quarkus/target

set -euo pipefail

# Configuration
QUARKUS_CONTAINER_NAME="cui-jwt-quarkus-integration-tests-cui-jwt-integration-tests-1"
KEYCLOAK_CONTAINER_NAME="cui-jwt-quarkus-integration-tests-keycloak-1"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
QUARKUS_LOG_FILENAME="cui-jwt-quarkus-logs-${TIMESTAMP}.txt"
KEYCLOAK_LOG_FILENAME="cui-jwt-keycloak-logs-${TIMESTAMP}.txt"

# Parameter validation
if [ $# -ne 1 ]; then
    echo "❌ Error: Target directory parameter required"
    echo "Usage: $0 <target-directory>"
    echo "Example: $0 target"
    echo "Example: $0 ../../benchmarking/benchmark-integration-quarkus/target"
    exit 1
fi

TARGET_DIR="$1"

# Create target directory if it doesn't exist
if [ ! -d "$TARGET_DIR" ]; then
    echo "📁 Creating target directory: $TARGET_DIR"
    mkdir -p "$TARGET_DIR"
fi

# Resolve absolute path for clarity
TARGET_ABS_PATH=$(cd "$TARGET_DIR" && pwd)
QUARKUS_LOG_FILE_PATH="${TARGET_ABS_PATH}/${QUARKUS_LOG_FILENAME}"
KEYCLOAK_LOG_FILE_PATH="${TARGET_ABS_PATH}/${KEYCLOAK_LOG_FILENAME}"

echo "🚀 Dumping JWT Integration Tests container logs..."
echo "📦 Quarkus container: $QUARKUS_CONTAINER_NAME"
echo "📦 Keycloak container: $KEYCLOAK_CONTAINER_NAME"
echo "📝 Output files: $TARGET_ABS_PATH/"

# Function to check and dump container logs
dump_container_logs() {
    local container_name="$1"
    local log_file_path="$2"
    local service_name="$3"
    
    echo ""
    echo "📋 Processing $service_name container..."
    
    # Check if container exists and is running
    if ! docker ps --format "table {{.Names}}" | grep -q "^${container_name}$"; then
        if docker ps -a --format "table {{.Names}}" | grep -q "^${container_name}$"; then
            echo "⚠️  Warning: Container $container_name exists but is not running"
            echo "📋 Attempting to dump logs from stopped container..."
        else
            echo "❌ Error: Container $container_name not found"
            echo "🔍 Available containers:"
            docker ps -a --format "table {{.Names}}\t{{.Status}}"
            return 1
        fi
    else
        echo "✅ Container is running"
    fi
    
    # Dump logs
    echo "📥 Dumping $service_name logs to: $(basename "$log_file_path")"
    if docker logs "$container_name" > "$log_file_path" 2>&1; then
        LOG_SIZE=$(wc -l < "$log_file_path")
        FILE_SIZE=$(du -h "$log_file_path" | cut -f1)
        echo "✅ Successfully dumped $LOG_SIZE lines ($FILE_SIZE) to: $(basename "$log_file_path")"
        echo "📍 Full path: $log_file_path"
        return 0
    else
        echo "❌ Failed to dump logs from container: $container_name"
        return 1
    fi
}

# Dump Quarkus container logs
QUARKUS_SUCCESS=true
dump_container_logs "$QUARKUS_CONTAINER_NAME" "$QUARKUS_LOG_FILE_PATH" "Quarkus" || QUARKUS_SUCCESS=false

# Dump Keycloak container logs
KEYCLOAK_SUCCESS=true
dump_container_logs "$KEYCLOAK_CONTAINER_NAME" "$KEYCLOAK_LOG_FILE_PATH" "Keycloak" || KEYCLOAK_SUCCESS=false

# Summary
echo ""
echo "📊 Log Dump Summary:"
if [ "$QUARKUS_SUCCESS" = true ]; then
    echo "✅ Quarkus logs: $(basename "$QUARKUS_LOG_FILENAME")"
else
    echo "❌ Quarkus logs: FAILED"
fi

if [ "$KEYCLOAK_SUCCESS" = true ]; then
    echo "✅ Keycloak logs: $(basename "$KEYCLOAK_LOG_FILENAME")"
else
    echo "❌ Keycloak logs: FAILED"
fi

# Exit with error if any dump failed
if [ "$QUARKUS_SUCCESS" = false ] || [ "$KEYCLOAK_SUCCESS" = false ]; then
    echo "⚠️  Some log dumps failed - see details above"
    exit 1
else
    echo "🎉 All container logs successfully dumped to: $TARGET_ABS_PATH"
fi