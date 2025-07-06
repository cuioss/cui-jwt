#!/bin/bash
# Benchmark with CPU/Memory Monitoring Script
# Captures system resource utilization during JWT validation benchmarks

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTEGRATION_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$INTEGRATION_DIR/../.." && pwd)"
BENCHMARK_DIR="$PROJECT_ROOT/cui-jwt-quarkus-parent/quarkus-integration-benchmark"
RESULTS_DIR="$BENCHMARK_DIR/monitoring-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo "🔍 JWT Validation Benchmark with Resource Monitoring"
echo "📁 Project root: $PROJECT_ROOT"
echo "📊 Results will be saved to: $RESULTS_DIR"
echo ""

# Create results directory
mkdir -p "$RESULTS_DIR"

# Function to get CPU and Memory usage
get_system_stats() {
    TIMESTAMP=$(date +%Y-%m-%d\ %H:%M:%S)
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS - more accurate resource usage
        CPU_COUNT=$(sysctl -n hw.ncpu)
        # Get total CPU usage as percentage (100% = 1 core fully used)
        CPU_USAGE=$(ps aux | awk '{sum+=$3} END {printf "%.1f", sum}')
        # Normalize to percentage of total available CPU
        CPU_USAGE_NORMALIZED=$(echo "scale=1; $CPU_USAGE / $CPU_COUNT" | bc)
        # Memory usage percentage
        MEM_INFO=$(vm_stat | perl -ne '/page size of (\d+)/ and $size=$1; /Pages\s+([^:]+):\s+(\d+)/ and $mem{$1}=$2; END { $total = $mem{"active"} + $mem{"inactive"} + $mem{"wired down"} + $mem{"compressed"}; $used = $mem{"active"} + $mem{"wired down"}; printf "%.1f", $used/$total * 100 if $total > 0 }')
        LOAD_AVG=$(sysctl -n vm.loadavg | awk '{print $2}')
    else
        # Linux
        CPU_COUNT=$(nproc)
        CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | awk '{print 100 - $1}')
        MEM_USAGE=$(free | grep Mem | awk '{printf "%.1f", ($3/$2) * 100.0}')
        LOAD_AVG=$(uptime | awk -F'load average:' '{print $2}' | awk -F',' '{print $1}' | xargs)
    fi
    echo "$TIMESTAMP,$CPU_USAGE_NORMALIZED,$MEM_INFO,$LOAD_AVG,$CPU_COUNT"
}

# Function to monitor container stats
monitor_container_stats() {
    local CONTAINER_NAME=$1
    local OUTPUT_FILE=$2
    
    echo "timestamp,cpu_percent,memory_usage_mb,memory_limit_mb,memory_percent" > "$OUTPUT_FILE"
    
    while kill -0 $BENCHMARK_PID 2>/dev/null; do
        if docker ps --format "{{.Names}}" | grep -q "$CONTAINER_NAME"; then
            STATS=$(docker stats --no-stream --format "{{.CPUPerc}},{{.MemUsage}}" "$CONTAINER_NAME" 2>/dev/null || echo "0%,0MiB / 0MiB")
            CPU_PERCENT=$(echo "$STATS" | cut -d',' -f1 | sed 's/%//')
            MEM_USAGE=$(echo "$STATS" | cut -d',' -f2 | awk '{print $1}' | sed 's/MiB//' | sed 's/GiB/*1024/' | bc 2>/dev/null || echo "0")
            MEM_LIMIT=$(echo "$STATS" | cut -d',' -f2 | awk '{print $3}' | sed 's/MiB//' | sed 's/GiB/*1024/' | bc 2>/dev/null || echo "0")
            MEM_PERCENT=$(echo "scale=2; $MEM_USAGE / $MEM_LIMIT * 100" | bc 2>/dev/null || echo "0")
            
            echo "$(date +%Y-%m-%d\ %H:%M:%S),$CPU_PERCENT,$MEM_USAGE,$MEM_LIMIT,$MEM_PERCENT" >> "$OUTPUT_FILE"
        fi
        sleep 2
    done
}

# Start system monitoring
echo "📊 Starting system resource monitoring..."
SYSTEM_LOG="$RESULTS_DIR/system-stats-$TIMESTAMP.csv"
echo "timestamp,cpu_usage_percent,memory_usage_percent,load_average,cpu_count" > "$SYSTEM_LOG"

# Background system monitoring
(
    while true; do
        get_system_stats >> "$SYSTEM_LOG"
        sleep 2
    done
) &
SYSTEM_MONITOR_PID=$!

# Build if necessary
echo ""
echo "🔨 Ensuring integration tests are built..."
cd "$PROJECT_ROOT"
./mvnw clean install -pl cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests -q || {
    echo "❌ Build failed"
    kill $SYSTEM_MONITOR_PID 2>/dev/null
    exit 1
}

# Start benchmark
echo ""
echo "🚀 Starting JWT validation benchmark..."
BENCHMARK_LOG="$RESULTS_DIR/benchmark-output-$TIMESTAMP.log"

# Run benchmark in background
./mvnw verify -pl cui-jwt-quarkus-parent/quarkus-integration-benchmark -Pintegration-benchmarks > "$BENCHMARK_LOG" 2>&1 &
BENCHMARK_PID=$!

# Wait for containers to start
echo "⏳ Waiting for containers to start..."
sleep 10

# Monitor Docker containers
KEYCLOAK_LOG="$RESULTS_DIR/keycloak-stats-$TIMESTAMP.csv"
JWT_SERVICE_LOG="$RESULTS_DIR/jwt-service-stats-$TIMESTAMP.csv"

monitor_container_stats "cui-jwt-quarkus-integration-tests-keycloak-1" "$KEYCLOAK_LOG" &
KEYCLOAK_MONITOR_PID=$!

monitor_container_stats "cui-jwt-quarkus-integration-tests-cui-jwt-integration-tests-1" "$JWT_SERVICE_LOG" &
JWT_MONITOR_PID=$!

# Function to display live stats
display_stats() {
    clear
    echo "🔄 JWT Validation Benchmark - Live Monitoring"
    echo "=============================================="
    echo ""
    
    # System stats
    if [[ -f "$SYSTEM_LOG" ]]; then
        LATEST_SYSTEM=$(tail -1 "$SYSTEM_LOG")
        CPU_USE=$(echo "$LATEST_SYSTEM" | cut -d',' -f2)
        MEM_USE=$(echo "$LATEST_SYSTEM" | cut -d',' -f3)
        LOAD=$(echo "$LATEST_SYSTEM" | cut -d',' -f4)
        echo "📊 System Resources:"
        echo "   CPU Usage: ${CPU_USE}%"
        echo "   Memory Usage: ${MEM_USE}%"
        echo "   Load Average: $LOAD"
        echo ""
    fi
    
    # Container stats
    if [[ -f "$JWT_SERVICE_LOG" ]] && [[ $(wc -l < "$JWT_SERVICE_LOG") -gt 1 ]]; then
        LATEST_JWT=$(tail -1 "$JWT_SERVICE_LOG")
        JWT_CPU=$(echo "$LATEST_JWT" | cut -d',' -f2)
        JWT_MEM=$(echo "$LATEST_JWT" | cut -d',' -f3)
        JWT_MEM_PCT=$(echo "$LATEST_JWT" | cut -d',' -f5)
        echo "🐳 JWT Service Container:"
        echo "   CPU: ${JWT_CPU}%"
        echo "   Memory: ${JWT_MEM}MB (${JWT_MEM_PCT}%)"
        echo ""
    fi
    
    # Benchmark progress
    if [[ -f "$BENCHMARK_LOG" ]]; then
        OPS_COUNT=$(grep -c "ops/s" "$BENCHMARK_LOG" 2>/dev/null || echo "0")
        LATEST_OPS=$(grep "ops/s" "$BENCHMARK_LOG" 2>/dev/null | tail -1 | grep -oE '[0-9]+\.[0-9]+ ops/s' || echo "N/A")
        echo "📈 Benchmark Progress:"
        echo "   Iterations: $OPS_COUNT"
        echo "   Latest: $LATEST_OPS"
    fi
}

# Display live stats every 5 seconds
echo ""
echo "📊 Displaying live statistics (press Ctrl+C to stop)..."
echo ""

trap cleanup INT TERM

cleanup() {
    echo ""
    echo "🛑 Stopping monitoring..."
    kill $SYSTEM_MONITOR_PID 2>/dev/null || true
    kill $KEYCLOAK_MONITOR_PID 2>/dev/null || true
    kill $JWT_MONITOR_PID 2>/dev/null || true
    kill $BENCHMARK_PID 2>/dev/null || true
    
    # Generate summary report
    generate_summary_report
    
    exit 0
}

# Monitor for 2 minutes or until benchmark completes
DURATION=120
START_TIME=$(date +%s)

while kill -0 $BENCHMARK_PID 2>/dev/null; do
    display_stats
    
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [[ $ELAPSED -ge $DURATION ]]; then
        echo ""
        echo "⏰ 2-minute monitoring period completed"
        break
    fi
    
    sleep 5
done

# Stop monitoring
kill $SYSTEM_MONITOR_PID 2>/dev/null || true
kill $KEYCLOAK_MONITOR_PID 2>/dev/null || true
kill $JWT_MONITOR_PID 2>/dev/null || true

# Wait a moment for final data
sleep 2

# Function to generate summary report
generate_summary_report() {
    local REPORT_FILE="$RESULTS_DIR/monitoring-report-$TIMESTAMP.adoc"
    
    echo "= Resource Utilization Report - $TIMESTAMP" > "$REPORT_FILE"
    echo ":toc: left" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "== Summary" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    
    # System stats analysis
    if [[ -f "$SYSTEM_LOG" ]] && [[ $(wc -l < "$SYSTEM_LOG") -gt 1 ]]; then
        AVG_CPU=$(awk -F',' 'NR>1 {sum+=$2; count++} END {if(count>0) printf "%.1f", sum/count; else print "0"}' "$SYSTEM_LOG")
        MAX_CPU=$(awk -F',' 'NR>1 {if($2>max) max=$2} END {printf "%.1f", max}' "$SYSTEM_LOG")
        AVG_MEM=$(awk -F',' 'NR>1 {sum+=$3; count++} END {if(count>0) printf "%.1f", sum/count; else print "0"}' "$SYSTEM_LOG")
        MAX_MEM=$(awk -F',' 'NR>1 {if($3>max) max=$3} END {printf "%.1f", max}' "$SYSTEM_LOG")
        
        echo "=== System Resource Utilization" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        echo "[cols=\"2,1,1,1\"]" >> "$REPORT_FILE"
        echo "|===" >> "$REPORT_FILE"
        echo "|Metric |Average |Maximum |Target" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        echo "|CPU Usage" >> "$REPORT_FILE"
        printf "|%.1f%% |%.1f%% |90%%\n" "$AVG_CPU" "$MAX_CPU" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        echo "|Memory Usage" >> "$REPORT_FILE"
        printf "|%.1f%% |%.1f%% |90%%\n" "$AVG_MEM" "$MAX_MEM" >> "$REPORT_FILE"
        echo "|===" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        
        # Check if we're hitting targets
        if (( $(echo "$AVG_CPU < 80" | bc -l) )); then
            echo "WARNING: Average CPU usage (${AVG_CPU}%) is below 80% - system may be underutilized" >> "$REPORT_FILE"
            echo "" >> "$REPORT_FILE"
        fi
        
        if (( $(echo "$AVG_MEM < 80" | bc -l) )); then
            echo "WARNING: Average memory usage (${AVG_MEM}%) is below 80% - system may be underutilized" >> "$REPORT_FILE"
            echo "" >> "$REPORT_FILE"
        fi
    fi
    
    # Container stats analysis
    if [[ -f "$JWT_SERVICE_LOG" ]] && [[ $(wc -l < "$JWT_SERVICE_LOG") -gt 1 ]]; then
        AVG_JWT_CPU=$(awk -F',' 'NR>1 {sum+=$2; count++} END {print sum/count}' "$JWT_SERVICE_LOG")
        MAX_JWT_CPU=$(awk -F',' 'NR>1 {if($2>max) max=$2} END {print max}' "$JWT_SERVICE_LOG")
        AVG_JWT_MEM=$(awk -F',' 'NR>1 {sum+=$5; count++} END {print sum/count}' "$JWT_SERVICE_LOG")
        MAX_JWT_MEM=$(awk -F',' 'NR>1 {if($5>max) max=$5} END {print max}' "$JWT_SERVICE_LOG")
        
        echo "=== JWT Service Container Utilization" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        echo "[cols=\"2,1,1,1\"]" >> "$REPORT_FILE"
        echo "|===" >> "$REPORT_FILE"
        echo "|Metric |Average |Maximum |Target" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        echo "|Container CPU" >> "$REPORT_FILE"
        printf "|%.1f%% |%.1f%% |90%%\n" "$AVG_JWT_CPU" "$MAX_JWT_CPU" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        echo "|Container Memory" >> "$REPORT_FILE"
        printf "|%.1f%% |%.1f%% |90%%\n" "$AVG_JWT_MEM" "$MAX_JWT_MEM" >> "$REPORT_FILE"
        echo "|===" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    fi
    
    # Benchmark results
    if [[ -f "$BENCHMARK_LOG" ]]; then
        echo "=== Benchmark Performance" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        
        # Extract throughput numbers
        grep "ops/s" "$BENCHMARK_LOG" | tail -5 >> "$REPORT_FILE" 2>/dev/null || echo "No benchmark results found" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    fi
    
    echo "=== Recommendations" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    
    # Generate recommendations based on utilization
    if (( $(echo "$AVG_CPU < 80" | bc -l) )); then
        echo "* Increase JMH threads or concurrent load to improve CPU utilization" >> "$REPORT_FILE"
    fi
    
    if (( $(echo "$AVG_MEM < 80" | bc -l) )); then
        echo "* Consider increasing workload complexity or reducing memory limits" >> "$REPORT_FILE"
    fi
    
    if (( $(echo "$AVG_JWT_CPU < 80" | bc -l) )); then
        echo "* JWT service CPU underutilized - increase concurrent requests" >> "$REPORT_FILE"
    fi
    
    echo "" >> "$REPORT_FILE"
    echo "== Raw Data Files" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "* System stats: link:system-stats-$TIMESTAMP.csv[]" >> "$REPORT_FILE"
    echo "* JWT service stats: link:jwt-service-stats-$TIMESTAMP.csv[]" >> "$REPORT_FILE"
    echo "* Keycloak stats: link:keycloak-stats-$TIMESTAMP.csv[]" >> "$REPORT_FILE"
    echo "* Benchmark output: link:benchmark-output-$TIMESTAMP.log[]" >> "$REPORT_FILE"
    
    echo ""
    echo "📄 Report generated: $REPORT_FILE"
}

# Generate final report
generate_summary_report

echo ""
echo "✅ Monitoring completed!"
echo "📊 Results saved to: $RESULTS_DIR"
echo ""
echo "Key files:"
echo "  - Monitoring report: monitoring-report-$TIMESTAMP.adoc"
echo "  - System stats: system-stats-$TIMESTAMP.csv"
echo "  - Container stats: jwt-service-stats-$TIMESTAMP.csv"
echo "  - Benchmark log: benchmark-output-$TIMESTAMP.log"