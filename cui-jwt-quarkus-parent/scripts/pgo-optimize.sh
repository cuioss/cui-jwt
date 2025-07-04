#!/bin/bash
# Script to build PGO-optimized native image for Quarkus JWT validation

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
INTEGRATION_TESTS_DIR="$PROJECT_ROOT/cui-jwt-quarkus-integration-tests"

echo "🚀 Starting PGO optimization workflow for Quarkus JWT validation"
echo "📁 Project root: $PROJECT_ROOT"

# Step 1: Build instrumented native image
echo ""
echo "📊 Step 1: Building instrumented native image..."
cd "$PROJECT_ROOT"
./mvnw clean package -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests -Ppgo-instrument

# Step 2: Run instrumented application to collect profile data
echo ""
echo "📊 Step 2: Running instrumented application to collect profile data..."
echo "⚠️  Note: The application needs to process representative workload"

# Start the containers
cd "$INTEGRATION_TESTS_DIR"
./scripts/start-integration-container.sh

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 10

# Run a representative workload (benchmark for 60 seconds)
echo "🏃 Running representative workload..."
cd "$PROJECT_ROOT"
timeout 60 ./mvnw verify -pl cui-jwt-quarkus-parent/quarkus-integration-benchmark -Pintegration-benchmarks || true

# Stop containers to ensure profile is saved
echo "💾 Stopping containers to save profile data..."
cd "$INTEGRATION_TESTS_DIR"
./scripts/stop-integration-container.sh

# Check if profile was generated
if [ ! -f "$INTEGRATION_TESTS_DIR/default.iprof" ]; then
    echo "❌ Error: Profile file default.iprof not found!"
    echo "   The instrumented application may not have saved the profile correctly."
    exit 1
fi

echo "✅ Profile data collected: $INTEGRATION_TESTS_DIR/default.iprof"

# Step 3: Build optimized native image using profile
echo ""
echo "🔨 Step 3: Building PGO-optimized native image..."
cd "$PROJECT_ROOT"
./mvnw clean package -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests -Ppgo-optimize

echo ""
echo "✅ PGO optimization complete!"
echo "📦 Optimized native image: $INTEGRATION_TESTS_DIR/target/cui-jwt-quarkus-integration-tests-*-runner"
echo ""
echo "🚀 To run benchmarks with the optimized image:"
echo "   ./mvnw verify -pl cui-jwt-quarkus-parent/quarkus-integration-benchmark -Pintegration-benchmarks"