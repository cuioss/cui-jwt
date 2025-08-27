#!/bin/bash
# HTTP server for viewing generated benchmark reports locally
# Usage: 
#   ./serve-reports.sh [port]    - Start server on specified port (default: 8080)
#   ./serve-reports.sh stop      - Stop running server

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORTS_DIR="$PROJECT_ROOT/benchmarking/cui-benchmarking-common/target/benchmark-reports-preview"
DEFAULT_PORT=8080

# Function to stop the server
stop_server() {
    echo "🔍 Looking for running benchmark report servers..."
    
    # Find Python HTTP server processes
    PIDS=$(ps aux | grep -E "python.*http.server|python.*SimpleHTTPServer" | grep -v grep | awk '{print $2}')
    
    if [ -z "$PIDS" ]; then
        echo "ℹ️  No HTTP server found running."
        exit 0
    fi
    
    echo "🛑 Stopping HTTP server(s)..."
    for PID in $PIDS; do
        # Get process info for confirmation
        PROCESS_INFO=$(ps -p $PID -o command= 2>/dev/null)
        if [ -n "$PROCESS_INFO" ]; then
            echo "   Stopping PID $PID: $PROCESS_INFO"
            kill $PID 2>/dev/null
            if [ $? -eq 0 ]; then
                echo "   ✅ Stopped PID $PID"
            else
                echo "   ⚠️  Could not stop PID $PID (may require sudo)"
            fi
        fi
    done
    
    echo "✨ Done!"
    exit 0
}

# Handle "stop" parameter
if [ "$1" = "stop" ]; then
    stop_server
fi

# Start server mode
PORT="${1:-$DEFAULT_PORT}"

# Check if reports directory exists
if [ ! -d "$REPORTS_DIR" ]; then
    echo "❌ Reports directory not found: $REPORTS_DIR"
    echo ""
    echo "Please generate reports first by running:"
    echo "  cd benchmarking/cui-benchmarking-common"
    echo "  mvn test-compile"
    echo "  mvn exec:java -Dexec.mainClass=\"de.cuioss.benchmarking.common.LocalReportGeneratorTest\" -Dexec.classpathScope=\"test\""
    echo ""
    echo "Or run directly from IDE: LocalReportGeneratorTest.main()"
    exit 1
fi

# Check if server is already running on the specified port
EXISTING_PID=$(lsof -ti:$PORT 2>/dev/null)
if [ -n "$EXISTING_PID" ]; then
    echo "⚠️  Port $PORT is already in use by process $EXISTING_PID"
    echo ""
    echo "You can:"
    echo "  1. Stop the existing server: ./serve-reports.sh stop"
    echo "  2. Use a different port: ./serve-reports.sh 8081"
    echo ""
    exit 1
fi

echo "🚀 Starting local HTTP server for benchmark reports"
echo "📁 Serving from: $REPORTS_DIR"
echo "🌐 URL: http://localhost:$PORT"
echo ""
echo "Available reports:"
echo "  - http://localhost:$PORT                          (Combined Index)"
echo "  - http://localhost:$PORT/micro/index.html         (Micro Benchmark Overview)"
echo "  - http://localhost:$PORT/micro/trends.html        (Micro Benchmark Trends)"
echo "  - http://localhost:$PORT/micro/detailed.html      (Micro Benchmark Details)"
echo "  - http://localhost:$PORT/integration/index.html   (Integration Overview)"
echo "  - http://localhost:$PORT/integration/trends.html  (Integration Trends)"
echo "  - http://localhost:$PORT/integration/detailed.html (Integration Details)"
echo ""
echo "Badge endpoints:"
echo "  - http://localhost:$PORT/micro/badges/performance-badge.json"
echo "  - http://localhost:$PORT/integration/badges/integration-performance-badge.json"
echo ""
echo "Data endpoints:"
echo "  - http://localhost:$PORT/micro/benchmark-data.json"
echo "  - http://localhost:$PORT/integration/benchmark-data.json"
echo ""
echo "📌 To stop the server:"
echo "   Press Ctrl+C or run: ./serve-reports.sh stop"
echo ""

# Change to reports directory
cd "$REPORTS_DIR"

# Try Python 3 first, then Python 2
if command -v python3 &> /dev/null; then
    python3 -m http.server "$PORT"
elif command -v python &> /dev/null; then
    python -m SimpleHTTPServer "$PORT"
else
    echo "❌ Error: Python is not installed. Please install Python to use this script."
    echo "Alternative: Use any other HTTP server like 'npx http-server' or 'php -S localhost:$PORT'"
    exit 1
fi