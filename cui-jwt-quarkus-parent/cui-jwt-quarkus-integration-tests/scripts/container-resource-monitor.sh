#!/bin/bash
# Container Resource Monitor - Independent container metrics collection

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/cui-jwt-quarkus-parent/quarkus-integration-benchmark/monitoring-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo "🐳 Container Resource Monitor"
echo "============================="
echo ""

mkdir -p "$RESULTS_DIR"

# Output files
CONTAINER_STATS="$RESULTS_DIR/container-metrics-$TIMESTAMP.csv"
CONTAINER_REPORT="$RESULTS_DIR/container-report-$TIMESTAMP.txt"

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker is not running or not accessible"
    exit 1
fi

# Function to monitor specific container
monitor_container() {
    local CONTAINER_NAME=$1
    local DURATION=${2:-120}  # Default 2 minutes
    local INTERVAL=${3:-2}    # Default 2 second intervals
    
    echo "📊 Monitoring container: $CONTAINER_NAME"
    echo "Duration: ${DURATION}s, Interval: ${INTERVAL}s"
    echo ""
    
    # CSV header
    echo "timestamp,container,cpu_percent,mem_usage_mb,mem_limit_mb,mem_percent,net_io,block_io" > "$CONTAINER_STATS"
    
    local START_TIME=$(date +%s)
    local ITERATION=0
    
    while true; do
        local CURRENT_TIME=$(date +%s)
        local ELAPSED=$((CURRENT_TIME - START_TIME))
        
        if [[ $ELAPSED -ge $DURATION ]]; then
            break
        fi
        
        # Get container stats
        if docker ps --format "{{.Names}}" | grep -q "$CONTAINER_NAME"; then
            # Get detailed stats
            local STATS=$(docker stats --no-stream --format "{{.Container}},{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}" "$CONTAINER_NAME" 2>/dev/null || echo "")
            
            if [[ -n "$STATS" ]]; then
                # Parse memory usage
                local MEM_USAGE=$(echo "$STATS" | cut -d',' -f3 | awk '{print $1}' | sed 's/MiB//' | sed 's/GiB/*1024/' | bc 2>/dev/null || echo "0")
                local MEM_LIMIT=$(echo "$STATS" | cut -d',' -f3 | awk '{print $3}' | sed 's/MiB//' | sed 's/GiB/*1024/' | bc 2>/dev/null || echo "0")
                local CPU_PCT=$(echo "$STATS" | cut -d',' -f2 | sed 's/%//')
                local NET_IO=$(echo "$STATS" | cut -d',' -f4)
                local BLOCK_IO=$(echo "$STATS" | cut -d',' -f5)
                
                # Calculate memory percentage
                if [[ "$MEM_LIMIT" != "0" ]]; then
                    local MEM_PCT=$(echo "scale=2; $MEM_USAGE / $MEM_LIMIT * 100" | bc 2>/dev/null || echo "0")
                else
                    local MEM_PCT="0"
                fi
                
                # Write to CSV
                echo "$(date +%Y-%m-%d\ %H:%M:%S),$CONTAINER_NAME,$CPU_PCT,$MEM_USAGE,$MEM_LIMIT,$MEM_PCT,$NET_IO,$BLOCK_IO" >> "$CONTAINER_STATS"
                
                # Display current stats
                printf "\r[%3ds] CPU: %6s%% | Memory: %6.0fMB/%6.0fMB (%5.1f%%) | Net I/O: %-20s" \
                    "$ELAPSED" "$CPU_PCT" "$MEM_USAGE" "$MEM_LIMIT" "$MEM_PCT" "$NET_IO"
                
                ITERATION=$((ITERATION + 1))
            fi
        else
            printf "\r[%3ds] Container not running                                                    " "$ELAPSED"
        fi
        
        sleep "$INTERVAL"
    done
    
    echo ""  # New line after progress
    echo "✅ Monitoring completed - $ITERATION samples collected"
}

# Function to analyze container metrics
analyze_container_metrics() {
    local CONTAINER_NAME=$1
    
    echo "" >> "$CONTAINER_REPORT"
    echo "=== Container: $CONTAINER_NAME ===" >> "$CONTAINER_REPORT"
    
    if [[ -f "$CONTAINER_STATS" ]] && grep -q "$CONTAINER_NAME" "$CONTAINER_STATS"; then
        # Calculate averages and maximums
        local CPU_DATA=$(grep "$CONTAINER_NAME" "$CONTAINER_STATS" | cut -d',' -f3 | grep -v "^$")
        local MEM_PCT_DATA=$(grep "$CONTAINER_NAME" "$CONTAINER_STATS" | cut -d',' -f6 | grep -v "^$")
        local MEM_MB_DATA=$(grep "$CONTAINER_NAME" "$CONTAINER_STATS" | cut -d',' -f4 | grep -v "^$")
        
        if [[ -n "$CPU_DATA" ]]; then
            local AVG_CPU=$(echo "$CPU_DATA" | awk '{sum+=$1; count++} END {if(count>0) printf "%.1f", sum/count; else print "0"}')
            local MAX_CPU=$(echo "$CPU_DATA" | awk 'BEGIN{max=0} {if($1>max) max=$1} END {printf "%.1f", max}')
            
            local AVG_MEM_PCT=$(echo "$MEM_PCT_DATA" | awk '{sum+=$1; count++} END {if(count>0) printf "%.1f", sum/count; else print "0"}')
            local MAX_MEM_PCT=$(echo "$MEM_PCT_DATA" | awk 'BEGIN{max=0} {if($1>max) max=$1} END {printf "%.1f", max}')
            
            local AVG_MEM_MB=$(echo "$MEM_MB_DATA" | awk '{sum+=$1; count++} END {if(count>0) printf "%.0f", sum/count; else print "0"}')
            local MAX_MEM_MB=$(echo "$MEM_MB_DATA" | awk 'BEGIN{max=0} {if($1>max) max=$1} END {printf "%.0f", max}')
            
            echo "CPU Usage:" >> "$CONTAINER_REPORT"
            echo "  Average: ${AVG_CPU}%" >> "$CONTAINER_REPORT"
            echo "  Maximum: ${MAX_CPU}%" >> "$CONTAINER_REPORT"
            echo "  Target:  90%" >> "$CONTAINER_REPORT"
            
            echo "" >> "$CONTAINER_REPORT"
            echo "Memory Usage:" >> "$CONTAINER_REPORT"
            echo "  Average: ${AVG_MEM_MB}MB (${AVG_MEM_PCT}%)" >> "$CONTAINER_REPORT"
            echo "  Maximum: ${MAX_MEM_MB}MB (${MAX_MEM_PCT}%)" >> "$CONTAINER_REPORT"
            echo "  Target:  90%" >> "$CONTAINER_REPORT"
            
            # Check against targets
            echo "" >> "$CONTAINER_REPORT"
            echo "Status:" >> "$CONTAINER_REPORT"
            if (( $(echo "$AVG_CPU < 90" | bc -l) )); then
                echo "  ❌ CPU underutilized (${AVG_CPU}% < 90%)" >> "$CONTAINER_REPORT"
            else
                echo "  ✅ CPU well utilized (${AVG_CPU}%)" >> "$CONTAINER_REPORT"
            fi
            
            if (( $(echo "$AVG_MEM_PCT < 90" | bc -l) )); then
                echo "  ❌ Memory underutilized (${AVG_MEM_PCT}% < 90%)" >> "$CONTAINER_REPORT"
            else
                echo "  ✅ Memory well utilized (${AVG_MEM_PCT}%)" >> "$CONTAINER_REPORT"
            fi
        else
            echo "No data collected for this container" >> "$CONTAINER_REPORT"
        fi
    else
        echo "Container was not monitored" >> "$CONTAINER_REPORT"
    fi
}

# Main monitoring logic
echo "🔍 Checking for running containers..."
CONTAINERS=$(docker ps --format "{{.Names}}" | grep -E "(jwt|keycloak)" || echo "")

if [[ -z "$CONTAINERS" ]]; then
    echo "❌ No JWT or Keycloak containers are running"
    echo ""
    echo "To start containers:"
    echo "1. Build the integration tests"
    echo "2. Run the benchmark or integration tests"
    exit 1
fi

echo "Found containers:"
echo "$CONTAINERS"
echo ""

# Generate report header
echo "Container Resource Utilization Report" > "$CONTAINER_REPORT"
echo "====================================" >> "$CONTAINER_REPORT"
echo "Timestamp: $TIMESTAMP" >> "$CONTAINER_REPORT"
echo "Duration: 120 seconds" >> "$CONTAINER_REPORT"
echo "" >> "$CONTAINER_REPORT"

# Monitor each container
for CONTAINER in $CONTAINERS; do
    monitor_container "$CONTAINER" 120 2
    analyze_container_metrics "$CONTAINER"
    echo ""
done

# Display report
echo ""
echo "📊 Container Resource Analysis:"
echo "==============================="
cat "$CONTAINER_REPORT"

echo ""
echo "📄 Files generated:"
echo "  - Metrics CSV: $CONTAINER_STATS"
echo "  - Analysis Report: $CONTAINER_REPORT"

# Create summary for performance log
SUMMARY_FILE="$RESULTS_DIR/container-summary-$TIMESTAMP.adoc"
echo "=== Container Resource Utilization Summary" > "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"
echo "[cols=\"3,2,2,2,3\"]" >> "$SUMMARY_FILE"
echo "|===" >> "$SUMMARY_FILE"
echo "|Container |Avg CPU |Max CPU |Avg Memory |Status" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"

for CONTAINER in $CONTAINERS; do
    if grep -q "$CONTAINER" "$CONTAINER_REPORT"; then
        AVG_CPU=$(grep -A1 "Average:" "$CONTAINER_REPORT" | grep "Average:" | head -1 | awk '{print $2}')
        MAX_CPU=$(grep -A1 "Maximum:" "$CONTAINER_REPORT" | grep "Maximum:" | head -1 | awk '{print $2}')
        AVG_MEM=$(grep -A2 "Memory Usage:" "$CONTAINER_REPORT" | grep "Average:" | awk '{print $3}')
        
        echo "|$CONTAINER" >> "$SUMMARY_FILE"
        echo "|$AVG_CPU" >> "$SUMMARY_FILE"
        echo "|$MAX_CPU" >> "$SUMMARY_FILE"
        echo "|$AVG_MEM" >> "$SUMMARY_FILE"
        
        if [[ "$AVG_CPU" < "90%" ]]; then
            echo "|❌ Underutilized" >> "$SUMMARY_FILE"
        else
            echo "|✅ Good" >> "$SUMMARY_FILE"
        fi
        echo "" >> "$SUMMARY_FILE"
    fi
done
echo "|===" >> "$SUMMARY_FILE"

echo ""
echo "📊 Summary saved to: $SUMMARY_FILE"