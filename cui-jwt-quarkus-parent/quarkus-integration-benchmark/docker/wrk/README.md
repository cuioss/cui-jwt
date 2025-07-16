# Docker-based wrk JWT Benchmark

Optimized wrk implementation for JWT validation performance testing following industry best practices.

## Architecture

### Components
- **Docker Container**: `cui-jwt-wrk:latest` with Alpine Linux + wrk + dependencies
- **Shell Script**: `run-wrk-benchmark.sh` - Orchestration and result processing
- **Lua Script**: `jwt-benchmark.lua` - High-performance JWT token handling
- **Token Management**: Real JWT tokens from Keycloak or mock tokens for testing

### Performance Optimizations

#### Thread Configuration
- **Threads**: 10 (matches Apple M4 CPU cores)
- **Connections**: 200 (20x threads for HTTP/1.1 keep-alive efficiency)
- **Rationale**: Optimal thread-to-connection ratio for maximum throughput

#### Request Pool Pre-generation
- **Pool Size**: 1,000 requests pre-generated
- **Memory Usage**: Minimal overhead while eliminating runtime request creation
- **Token Rotation**: Automatic token cycling with error injection support

#### Network Configuration
- **Host Networking**: `--network host` eliminates Docker bridge overhead
- **Keep-alive**: HTTP/1.1 connection reuse for realistic performance
- **SSL/TLS**: Full HTTPS support for production-like testing

## Usage

### Basic Usage
```bash
./run-wrk-benchmark.sh
```

### Custom Configuration
```bash
./run-wrk-benchmark.sh [threads] [connections] [duration] [error_rate]

# Example: 12 threads, 240 connections, 60 seconds, 5% error rate
./run-wrk-benchmark.sh 12 240 60s 5
```

### Real JWT Tokens
Place real tokens in `target/tokens/access_token.txt` to use actual Keycloak tokens instead of mock tokens.

## Performance Targets

### Performance Results (Apple M4 + Quarkus Native)
- **Throughput**: 6,240 req/sec (10 threads, 200 connections)
- **Latency P50**: 15ms
- **Latency P90**: 75ms  
- **Latency P99**: 764ms
- **Infrastructure**: Docker host networking, HTTPS with SSL

### Performance Analysis
- **Excellent Throughput**: 6,240 req/sec exceeds 1,000 req/sec target
- **Mixed Latency Results**: P50 (15ms) meets target, P99 (764ms) shows tail latency issues
- **Network Optimization**: Host networking eliminated Docker bridge overhead
- **Thread Efficiency**: 10 threads optimal for Apple M4 (10 CPU cores)

## Best Practices Implementation

### wrk Configuration
✅ **CPU-optimized threading**: Threads = CPU cores  
✅ **Connection multiplexing**: 10-20x connections per thread  
✅ **Pre-generated requests**: Eliminates runtime overhead  
✅ **Realistic token rotation**: Simulates production token usage  
✅ **Comprehensive metrics**: Latency distribution + throughput  

### Docker Integration
✅ **Host networking**: Eliminates bridge networking overhead  
✅ **Volume mounting**: Results persistence for CI/CD  
✅ **Environment variables**: Flexible configuration  
✅ **Alpine Linux**: Minimal container footprint  

### Token Management
✅ **Real token support**: Keycloak integration via environment variables  
✅ **Mock token fallback**: Development testing without external dependencies  
✅ **Error injection**: Configurable invalid token percentage  
✅ **Token validation**: Automatic token format checking  

## Results Format

### JSON Output
```json
{
    "timestamp": "2024-01-15T10:30:00Z",
    "benchmark_type": "jwt_validation",
    "token_mode": "real",
    "requests": 10000,
    "throughput_rps": 1250.50,
    "latency": {
        "p50": 15.2,
        "p95": 28.7,
        "p99": 45.1
    },
    "errors": {
        "total": 12
    },
    "performance_stats": {
        "success_rate": 99.88
    }
}
```

### Console Output
```
🚀 Starting Docker-based wrk benchmark...
🔧 Performance Configuration:
  CPU-optimized threads: 10 (recommended: CPU cores)
  HTTP/1.1 connections: 200 (recommended: 10-20x threads)
  Connection efficiency: 20.0x multiplier
🏆 === JWT Validation Benchmark Results ===
⚡ Throughput: 1250 req/sec
📈 Latency P95: 29ms
```

## Troubleshooting

### Common Issues
- **No results file**: Check Docker container logs for Lua script errors
- **Low throughput**: Verify Quarkus container is running and accessible
- **Connection errors**: Ensure `host.docker.internal` resolves correctly
- **Token errors**: Validate JWT token format and expiration

### Performance Debugging
```bash
# Test container connectivity
curl -k https://host.docker.internal:10443/jwt/validate

# Basic wrk test without Lua script
docker run --rm --network host cui-jwt-wrk:latest wrk -t10 -c200 -d10s --latency https://host.docker.internal:10443/jwt/validate

# Check Docker networking
docker run --rm --network host alpine ping host.docker.internal

# Validate JWT tokens  
cat target/tokens/access_token.txt | head -c 50
```

## Performance Verification Results

### Actual Test Results (December 2024)
```
Running 10s test @ https://host.docker.internal:10443/jwt/validate
  10 threads and 200 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    52.55ms  121.75ms   1.35s    95.96%
    Req/Sec   719.04    183.78     1.76k    75.20%
  Latency Distribution
     50%   15.33ms
     75%   60.59ms  
     90%   74.56ms
     99%  764.49ms
  63000 requests in 10.10s, 4.57MB read
Requests/sec:   6239.55
Transfer/sec:    463.09KB
```

### Key Performance Insights
- **Throughput Achievement**: 6,240 req/sec (524% of 1,000 req/sec target)
- **Latency Target Met**: P50 at 15ms meets <20ms target
- **Tail Latency Challenge**: P99 at 764ms indicates optimization opportunities  
- **Thread Efficiency**: 10 threads = CPU cores provides optimal resource utilization
- **Connection Scaling**: 20:1 connection-to-thread ratio maximizes HTTP/1.1 efficiency