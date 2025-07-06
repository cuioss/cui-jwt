#!/bin/bash
# Simple resource baseline measurement

echo "📊 Resource Baseline Measurement"
echo "================================"
echo ""

# System information
echo "System Information:"
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "- CPU Cores: $(sysctl -n hw.ncpu)"
    echo "- CPU Model: $(sysctl -n machdep.cpu.brand_string)"
    echo "- Total Memory: $(( $(sysctl -n hw.memsize) / 1024 / 1024 / 1024 )) GB"
else
    echo "- CPU Cores: $(nproc)"
    echo "- CPU Model: $(grep "model name" /proc/cpuinfo | head -1 | cut -d: -f2)"
    echo "- Total Memory: $(free -h | grep Mem | awk '{print $2}')"
fi
echo ""

echo "Current Resource Usage (idle system):"
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "- Load Average: $(uptime | awk -F'load averages:' '{print $2}')"
    top -l 1 | grep -E "CPU usage|PhysMem" | head -2
else
    echo "- Load Average: $(uptime | awk -F'load average:' '{print $2}')"
    echo "- CPU: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)% used"
    echo "- Memory: $(free | grep Mem | awk '{printf "%.1f%%", ($3/$2) * 100.0}')"
fi
echo ""

echo "Docker Status:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "Docker not running or no containers"
echo ""

echo "Recommendations for achieving 90% utilization:"
echo "1. Current JMH threads: 8 (from pom.xml configuration)"
echo "2. To increase CPU usage:"
echo "   - Increase JMH threads to match CPU cores"
echo "   - Run multiple benchmark instances"
echo "   - Reduce sleep/wait times in tests"
echo "3. To increase memory usage:"
echo "   - Reduce container memory limits"
echo "   - Increase JWT token size/complexity"
echo "   - Add more concurrent operations"
echo ""

echo "Next steps:"
echo "1. Fix Maven wrapper issue or use system Maven"
echo "2. Ensure Docker is running"
echo "3. Build native image first"
echo "4. Run benchmark with monitoring"