# WRK Benchmark Timeout Investigation

## Problem Description

During WRK benchmark runs with 300 concurrent connections, we observe 78-146 socket timeout errors reported by WRK that do not appear in the Quarkus application logs. These timeouts occur at the TCP socket level before the connection reaches the application layer.

## Problem Level Analysis

### Where the timeouts occur in the stack:

```
[WRK Load Driver] --> [Host OS] --> [Docker Network] --> [Container OS] --> [Quarkus App]
       |                  |              |                    |                  |
    Reports           TCP SYN        Bridge           Accept Queue          Never sees
    timeouts           sent          forwarding         overflow?           the connection
```

**What We Know (Facts Only):**

1. **WRK Load Driver Level**:
   - Reports: `connect 0, read 7, write 0, timeout 146`
   - Generated 1.7M requests in 3 minutes
   - 146 timeouts = 0.008% error rate

2. **Host OS Level**:
   - Unknown - no measurements taken

3. **Docker Network Level**:
   - Uses bridge network with port forwarding (host:10443 → container:8443)
   - No measurements taken yet

4. **Container OS Level**:
   - No TCP statistics collected during benchmark
   - `netstat -s` not run inside container during load

5. **Quarkus Application Level**:
   - No timeout/error logs in application logs
   - Processed 9,426 req/sec for successful connections
   - Application logs show normal operation

### Observed Behavior - The Critical Clue: Latency Explosion

**WRK Latency Distribution Shows Severe Degradation:**
```
Thread Stats   Avg      Stdev     Max   +/- Stdev
  Latency    96.45ms  164.51ms   1.79s    91.66%
Latency Distribution
   50%   13.77ms  ← Already 3-7x slower than low-load baseline (2-5ms)
   75%  142.14ms  ← 28-70x slower than baseline
   90%  221.58ms  ← 44-110x slower than baseline
   99%  783.68ms  ← 156-391x slower than baseline
```

**Comparison with Low-Load Performance:**
- Baseline (low load): 2-5ms response time
- Under 300 connections: Even the MEDIAN is already degraded to 13.77ms
- This means the system is struggling from the start, not just at the tail

**This pattern indicates the application itself is overwhelmed:**
- If the app can't keep up with request processing, it creates backpressure
- Backpressure propagates backwards: app → container OS → Docker network → host OS
- TCP accept queue fills because app isn't accepting connections fast enough
- Eventually, new connections timeout waiting in queue

**The cascade effect:**
1. Application processes requests slower than they arrive (13.77ms vs 2-5ms baseline)
2. Unprocessed requests queue up in the application
3. Application stops accepting new connections while busy
4. TCP accept queue fills in the container OS
5. New SYN packets get dropped or timeout
6. WRK sees timeouts and massive latency variance

### Key Finding

The 3-7x degradation in MEDIAN latency (from 2-5ms to 13.77ms) suggests the application itself is the root cause. When the app can't keep up, it creates backpressure that cascades through the entire stack, ultimately causing TCP-level timeouts. The timeouts are a symptom, not the cause.

## Root Cause Investigation

Since the application appears to be the bottleneck (3-7x slower median response time), focus should be on application-level diagnostics:

### Application Performance Analysis from Existing Metrics

**Critical Discovery - BOTH endpoints show latency explosion:**

| Endpoint | P50 | P75 | P90 | P99 | CPU Peak |
|----------|-----|-----|-----|-----|----------|
| Health Check | 6.18ms | 129ms | 209ms | **877ms** | 47.4% |
| JWT Validation | 13.77ms | 142ms | 222ms | 784ms | 78.5% |

**This reveals the real problem:**
- Health check (simple endpoint, 47% CPU) has WORSE P99 latency (877ms) than JWT validation!
- Even at low CPU, massive latency spikes occur
- The problem exists BEFORE JWT processing

**Thread and CPU Analysis:**

| Metric | Health Check | JWT Validation | Analysis |
|--------|-------------|----------------|----------|
| Threads (avg) | 123 | 120 | Similar thread counts |
| CPU (avg) | 39.7% | 68.4% | JWT adds CPU load |
| CPU (peak) | 47.4% | 78.5% | Neither is at 100% |
| GC overhead | 0.0% | 0.0% | GC not the issue |

**Key Finding:**
The latency explosion happens even with simple health checks at moderate CPU usage. This suggests the bottleneck is **NOT CPU or JWT processing**, but something more fundamental in the request handling pipeline - likely the connection accept/dispatch mechanism itself under 300 concurrent connections.

### Secondary: TCP Queue Monitoring
While TCP metrics are symptoms not causes, they can confirm the backpressure theory:

```bash
# Quick check for queue overflows (symptom of app slowness)
docker exec cui-jwt-integration-tests netstat -s | grep -i "overflow"
```

## Things to Consider

### Connection and Thread Pool Tuning

The following configurations may help reduce socket timeouts under high load. These were tested but require further validation:

#### Thread Pool Configuration
```properties
# Thread Pool Configuration for high concurrency (300 connections)
quarkus.thread-pool.core-threads=100
quarkus.thread-pool.max-threads=500
quarkus.thread-pool.queue-size=1000
quarkus.thread-pool.growth-resistance=0
quarkus.thread-pool.shutdown-timeout=10
quarkus.thread-pool.shutdown-interrupt=0
quarkus.thread-pool.shutdown-check-interval=5
quarkus.thread-pool.prefill=true
```

#### Vert.x Worker Pool
```properties
# Vert.x worker pool for blocking operations
quarkus.vertx.worker-pool-size=200
quarkus.vertx.max-worker-execute-time=120
```

#### Event Loop Configuration
```properties
# Event loop configuration for 300 concurrent connections
quarkus.vertx.event-loops-pool-size=10
```

#### TCP Accept Backlog
```properties
# Accept backlog for incoming connections - increased to reduce timeouts
quarkus.http.accept-backlog=1024
```

#### Connection Idle Timeout
```properties
# Idle timeout for connections (in seconds)
quarkus.http.idle-timeout=30s
```

### Docker vs Bare Metal Differences

Research indicates that Docker handles TCP backlog differently than bare metal systems:
- **Physical hardware**: SYN_RECV queue fills quickly, causing "Connection refused" errors
- **Docker containers**: Despite receiving SYN floods, the backlog doesn't saturate the same way
- The Docker networking stack (bridge network) may filter or handle half-open connections differently

This fundamental difference may explain why standard TCP tuning parameters have limited effect in containerized environments.

## Diagnostic Tests for Application Bottleneck

### Test 1: Reduce Concurrent Connections
```bash
# Test with progressively fewer connections to find the breaking point
wrk -t 10 -c 50 ...   # Does this maintain 2-5ms latency?
wrk -t 10 -c 100 ...  # When does degradation start?
wrk -t 10 -c 150 ...  # Is it linear or sudden?
```

### Test 2: Profile During Load
```bash
# Collect JFR recording during benchmark (if JFR image available)
docker exec <container> jcmd 1 JFR.start duration=30s filename=/tmp/profile.jfr
# Run benchmark
docker cp <container>:/tmp/profile.jfr .
```

### Test 3: CPU Profiling During Load
```bash
# Since we know CPU is at 78.5%, profile to find hot spots
# Use async-profiler or JFR to identify CPU-intensive methods
docker exec <container> jcmd 1 JFR.start settings=profile
```

### Test 4: Isolate JWT Validation Overhead
```bash
# Compare with health endpoint (no JWT validation)
wrk -t 10 -c 300 https://localhost:10443/q/health/live
# vs
wrk -t 10 -c 300 https://localhost:10443/jwt/validate
```

## Future Investigation

1. **Find the connection limit threshold** - At what point does the application start degrading?
2. **Profile the application under load** - Use JFR or async-profiler to identify bottlenecks
3. **Test without JWT validation** - Compare simple health endpoint vs JWT validation endpoint
4. **Monitor GC activity** - Check if garbage collection causes latency spikes
5. **Analyze virtual thread behavior** - Are virtual threads causing scheduling overhead at high concurrency?
6. **Test with traditional thread pool** - Compare virtual threads vs platform threads performance