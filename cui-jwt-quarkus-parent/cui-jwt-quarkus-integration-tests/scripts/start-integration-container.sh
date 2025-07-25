#!/bin/bash
# Start JWT Integration Tests using Docker Compose

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$(dirname "$PROJECT_DIR")")"

echo "🚀 Starting JWT Integration Tests with Docker Compose"
echo "Project directory: ${PROJECT_DIR}"
echo "Root directory: ${ROOT_DIR}"

cd "${PROJECT_DIR}"

# Check build mode - Docker build vs Maven build
RUNNER_FILE=$(find target/ -name "*-runner" -type f 2>/dev/null | head -n 1)
if [[ -n "$RUNNER_FILE" ]]; then
    echo "📦 Using Maven-built native image from target directory: $RUNNER_FILE"
    COMPOSE_FILE="docker-compose.yml"
    MODE="native (Maven-built)"
else
    echo "❌ No pre-built native executable found in target directory"
    echo "The integration-tests profile should have built the native executable during the package phase"
    echo "This script is called from the integration-tests profile which builds the native executable first"
    exit 1
fi

# Start with Docker Compose (includes Keycloak)
echo "🐳 Starting Docker containers (Quarkus $MODE + Keycloak)..."
docker compose -f "$COMPOSE_FILE" up -d

# Wait for Keycloak to be ready first
echo "⏳ Waiting for Keycloak to be ready..."
for i in {1..60}; do
    if curl -k -s https://localhost:1090/health/ready > /dev/null 2>&1; then
        echo "✅ Keycloak is ready!"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "❌ Keycloak failed to start within 60 seconds"
        echo "Check logs with: docker compose logs keycloak"
        exit 1
    fi
    echo "⏳ Waiting for Keycloak... (attempt $i/60)"
    sleep 1
done

# Wait for Quarkus service to be ready and measure startup time
echo "⏳ Waiting for Quarkus service to be ready..."
START_TIME=$(date +%s)
for i in {1..30}; do
    if curl -k -s https://localhost:10443/q/health/live > /dev/null 2>&1; then
        END_TIME=$(date +%s)
        TOTAL_TIME=$((END_TIME - START_TIME))
        echo "✅ Quarkus service is ready!"
        echo "📈 Actual startup time: ${TOTAL_TIME}s (container + application)"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Quarkus service failed to start within 30 seconds"
        echo "Check logs with: docker compose logs cui-jwt-integration-tests"
        exit 1
    fi
    echo "⏳ Waiting for Quarkus... (attempt $i/30)"
    sleep 1
done

# Extract native startup time from logs
NATIVE_STARTUP=$(docker compose logs cui-jwt-integration-tests 2>/dev/null | grep "started in" | sed -n 's/.*started in \([0-9.]*\)s.*/\1/p' | tail -1)
if [ ! -z "$NATIVE_STARTUP" ]; then
    echo "⚡ Native app startup: ${NATIVE_STARTUP}s (application only)"
fi

# Show actual image size
IMAGE_SIZE=$(docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}" | grep cui-jwt-integration-tests | awk '{print $2}' | head -1)
if [ ! -z "$IMAGE_SIZE" ]; then
    echo "📦 Image size: ${IMAGE_SIZE} (distroless native)"
fi

echo ""
echo "🎉 JWT Integration Benchmark Environment is running!"
echo ""
echo "📱 Application URLs:"
echo "  🔍 Health Check:   https://localhost:10443/q/health"
echo "  📊 Metrics:        https://localhost:10443/q/metrics"
echo "  🔑 Keycloak:       https://localhost:1443/auth"
echo ""
echo "🧪 Quick test commands:"
echo "  curl -k https://localhost:10443/q/health/live"
echo "  curl -k https://localhost:1090/health/ready"
echo ""
echo "🛑 To stop: ./scripts/stop-integration-container.sh"
echo "📋 To view logs: docker compose logs -f"
