#!/bin/bash

# Quarkus Container Log Dumping Script
# Usage: ./dump-quarkus-logs.sh <target-directory>
# Example: ./dump-quarkus-logs.sh target
# Example: ./dump-quarkus-logs.sh ../../benchmarking/benchmark-integration-quarkus/target

set -euo pipefail

# Configuration
CONTAINER_NAME="cui-jwt-quarkus-integration-tests-cui-jwt-integration-tests-1"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
LOG_FILENAME="cui-jwt-quarkus-logs-${TIMESTAMP}.txt"

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
LOG_FILE_PATH="${TARGET_ABS_PATH}/${LOG_FILENAME}"

echo "🚀 Dumping Quarkus container logs..."
echo "📦 Container: $CONTAINER_NAME"
echo "📝 Output file: $LOG_FILE_PATH"

# Check if container exists and is running
if ! docker ps --format "table {{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
    if docker ps -a --format "table {{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        echo "⚠️  Warning: Container $CONTAINER_NAME exists but is not running"
        echo "📋 Attempting to dump logs from stopped container..."
    else
        echo "❌ Error: Container $CONTAINER_NAME not found"
        echo "🔍 Available containers:"
        docker ps -a --format "table {{.Names}}\t{{.Status}}"
        exit 1
    fi
else
    echo "✅ Container is running"
fi

# Dump logs
echo "📥 Dumping logs to: $LOG_FILE_PATH"
if docker logs "$CONTAINER_NAME" > "$LOG_FILE_PATH" 2>&1; then
    LOG_SIZE=$(wc -l < "$LOG_FILE_PATH")
    FILE_SIZE=$(du -h "$LOG_FILE_PATH" | cut -f1)
    echo "✅ Successfully dumped $LOG_SIZE lines ($FILE_SIZE) to: $LOG_FILENAME"
    echo "📍 Full path: $LOG_FILE_PATH"
else
    echo "❌ Failed to dump logs from container: $CONTAINER_NAME"
    exit 1
fi