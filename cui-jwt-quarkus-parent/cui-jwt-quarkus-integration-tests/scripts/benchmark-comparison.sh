#!/bin/bash
set -e

# Focused benchmark comparison script for blocking vs reactive vs NOOP JWT validation

PROJECT_ROOT=$(cd ../../.. && pwd)
BENCHMARK_DIR="${PROJECT_ROOT}/cui-jwt-quarkus-parent/quarkus-integration-benchmark"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
OUTPUT_FILE="${BENCHMARK_DIR}/monitoring-results/comparison-${TIMESTAMP}.txt"

echo "📊 JWT Validation Performance Comparison"
echo "========================================"
echo "Comparing: Blocking (Virtual Threads) vs Reactive (Mutiny) vs NOOP (Baseline)"
echo ""

# Start containers
cd ${PROJECT_ROOT}/cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests
echo "🚀 Starting containers..."
./scripts/start-integration-container.sh

# Wait for containers to be ready
echo "⏳ Waiting for containers to be ready..."
sleep 10

# Run focused benchmarks
cd ${BENCHMARK_DIR}
echo "🔬 Running performance comparison benchmarks..."

# Define benchmark methods to compare
BLOCKING_BENCH="benchmarkValidTokenValidation"
REACTIVE_BENCH="benchmarkReactiveValidTokenValidation"
NOOP_BENCH="benchmarkNoopValidTokenValidation"

# Run each benchmark individually for clear comparison
echo "" | tee -a ${OUTPUT_FILE}
echo "=== BLOCKING (Virtual Threads) ===" | tee -a ${OUTPUT_FILE}
mvn exec:java -Dexec.args="-i 3 -wi 2 -f 1 -t 200 -r 5s -w 5s ${BLOCKING_BENCH}" 2>&1 | grep -E "(Throughput|ops/s|Result)" | tee -a ${OUTPUT_FILE}

echo "" | tee -a ${OUTPUT_FILE}
echo "=== REACTIVE (Mutiny) ===" | tee -a ${OUTPUT_FILE}
mvn exec:java -Dexec.args="-i 3 -wi 2 -f 1 -t 200 -r 5s -w 5s ${REACTIVE_BENCH}" 2>&1 | grep -E "(Throughput|ops/s|Result)" | tee -a ${OUTPUT_FILE}

echo "" | tee -a ${OUTPUT_FILE}
echo "=== NOOP (Baseline) ===" | tee -a ${OUTPUT_FILE}
mvn exec:java -Dexec.args="-i 3 -wi 2 -f 1 -t 200 -r 5s -w 5s ${NOOP_BENCH}" 2>&1 | grep -E "(Throughput|ops/s|Result)" | tee -a ${OUTPUT_FILE}

# Stop containers
cd ${PROJECT_ROOT}/cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests
echo ""
echo "🛑 Stopping containers..."
./scripts/stop-integration-container.sh

echo ""
echo "✅ Comparison complete! Results saved to:"
echo "   ${OUTPUT_FILE}"
echo ""
echo "📊 Summary:"
tail -20 ${OUTPUT_FILE} | grep -E "Result|ops/s"