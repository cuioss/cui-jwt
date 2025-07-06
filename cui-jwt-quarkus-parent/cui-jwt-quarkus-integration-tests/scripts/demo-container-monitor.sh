#!/bin/bash
# Demo Container Monitor - Test monitoring on any running container

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/cui-jwt-quarkus-parent/quarkus-integration-benchmark/monitoring-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo "🐳 Demo Container Resource Monitor"
echo "=================================="
echo ""

mkdir -p "$RESULTS_DIR"

# Check available containers
CONTAINERS=$(docker ps --format "{{.Names}}" | head -2)

if [[ -z "$CONTAINERS" ]]; then
    echo "❌ No containers are running"
    echo "Start some containers first to test monitoring"
    exit 1
fi

echo "Available containers for monitoring:"
echo "$CONTAINERS"
echo ""

# Select first container for demo
DEMO_CONTAINER=$(echo "$CONTAINERS" | head -1)
echo "📊 Monitoring container: $DEMO_CONTAINER (30 second demo)"
echo ""

# Monitor for 30 seconds
DURATION=30
START_TIME=$(date +%s)
echo "timestamp,container,cpu_percent,mem_usage,mem_limit,mem_percent,net_io,block_io" > "$RESULTS_DIR/demo-container-$TIMESTAMP.csv"

echo "Starting monitoring..."
while true; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [[ $ELAPSED -ge $DURATION ]]; then
        break
    fi
    
    # Get container stats
    STATS_LINE=$(docker stats --no-stream --format "{{.Container}},{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}" "$DEMO_CONTAINER" 2>/dev/null || echo "")
    
    if [[ -n "$STATS_LINE" ]]; then
        # Add timestamp and save
        echo "$(date +%Y-%m-%d\ %H:%M:%S),$STATS_LINE" >> "$RESULTS_DIR/demo-container-$TIMESTAMP.csv"
        
        # Display current stats
        CPU=$(echo "$STATS_LINE" | cut -d',' -f2)
        MEM=$(echo "$STATS_LINE" | cut -d',' -f3)
        NET=$(echo "$STATS_LINE" | cut -d',' -f4)
        
        printf "\r[%2ds] CPU: %8s | Memory: %15s | Net I/O: %20s" "$ELAPSED" "$CPU" "$MEM" "$NET"
    fi
    
    sleep 2
done

echo ""
echo ""

# Generate summary
echo "📊 Demo Complete - Container Resource Summary:"
echo "=============================================="

CSV_FILE="$RESULTS_DIR/demo-container-$TIMESTAMP.csv"
if [[ -f "$CSV_FILE" ]] && [[ $(wc -l < "$CSV_FILE") -gt 1 ]]; then
    echo "Container: $DEMO_CONTAINER"
    echo "Duration: ${DURATION}s"
    echo "Samples: $(( $(wc -l < "$CSV_FILE") - 1 ))"
    echo ""
    
    # Calculate simple averages
    echo "Resource Usage Analysis:"
    echo "========================"
    
    # Extract CPU values (remove % sign)
    CPU_VALUES=$(tail -n +2 "$CSV_FILE" | cut -d',' -f3 | sed 's/%//')
    if [[ -n "$CPU_VALUES" ]]; then
        AVG_CPU=$(echo "$CPU_VALUES" | awk '{sum+=$1; count++} END {printf "%.1f", sum/count}')
        MAX_CPU=$(echo "$CPU_VALUES" | awk 'BEGIN{max=0} {if($1>max) max=$1} END {printf "%.1f", max}')
        echo "CPU Usage:"
        echo "  Average: ${AVG_CPU}%"
        echo "  Maximum: ${MAX_CPU}%"
        echo "  Status: $(if (( $(echo "$AVG_CPU < 90" | bc -l) )); then echo "❌ Underutilized (<90%)"; else echo "✅ Well utilized"; fi)"
    fi
    
    echo ""
    echo "Memory Analysis:"
    # Extract memory values (just show raw data for demo)
    echo "Sample memory usage:"
    tail -n +2 "$CSV_FILE" | cut -d',' -f4 | head -3 | while read mem; do
        echo "  $mem"
    done
    
    echo ""
    echo "📄 Full data saved to: $CSV_FILE"
    echo ""
    echo "For JWT validation containers, this monitoring would help determine:"
    echo "  1. Whether CPU utilization reaches 90% target during benchmarks"
    echo "  2. Memory pressure and allocation patterns"  
    echo "  3. Network I/O during JWKS fetching"
    echo "  4. Resource competition between JWT service and Keycloak"
else
    echo "❌ No data was collected"
fi

echo ""
echo "🔧 To monitor JWT containers:"
echo "  1. Build native image: mvn clean package -Pintegration-tests"
echo "  2. Start containers: docker compose up -d"
echo "  3. Run monitoring: ./container-resource-monitor.sh"