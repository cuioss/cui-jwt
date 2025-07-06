#!/bin/bash
# Test script to start containers and monitor their resource usage

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🧪 Testing Container Resource Monitoring"
echo "========================================"
echo ""

# Start integration tests in background to get containers running
echo "🚀 Starting integration tests to create containers..."
cd "$SCRIPT_DIR/.."

# Use docker compose directly to start containers
echo "🐳 Starting containers with docker compose..."
docker compose up -d

# Wait for containers to start
echo "⏳ Waiting for containers to initialize..."
sleep 15

# Check container status
echo ""
echo "📊 Container Status:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "(jwt|keycloak|NAME)"

echo ""
echo "🔍 Starting container resource monitoring..."
"$SCRIPT_DIR/container-resource-monitor.sh"

echo ""
echo "🛑 Stopping containers..."
docker compose down

echo "✅ Container monitoring test completed!"