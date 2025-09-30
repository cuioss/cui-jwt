# Connection Handling Bottleneck Investigation Plan

## Problem Statement
At 300 concurrent connections, BOTH simple health checks and JWT validation endpoints experience massive latency spikes (P99: 877ms and 784ms respectively), despite CPU being at only 47% for health checks. This indicates a fundamental connection handling problem, not a processing bottleneck.

## Systematic Investigation Plan

### Phase 1: Find the Breaking Point
**Goal:** Identify exactly when performance degrades

#### Test Execution Table

**IMPORTANT: Run each benchmark sequentially (not in background). After each run:**
1. Inspect `cui-jwt-quarkus-parent/cui-jwt-quarkus-integration-tests/target/quarkus.log` for warnings/errors
2. Extract metrics from result files
3. Document any oddities (warnings, errors, unusual patterns)
4. Update the result tables (both, health and jwt) below before proceeding to next test

| Connections | Maven Command |
|------------|---------------|
| 10 | `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk -Dwrk.connections=10 -Dwrk.threads=2 -Dwrk.duration=30s` |
| 25 | `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk -Dwrk.connections=25 -Dwrk.threads=3 -Dwrk.duration=30s` |
| 50 | `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk -Dwrk.connections=50 -Dwrk.threads=5 -Dwrk.duration=30s` |
| 100 | `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk -Dwrk.connections=100 -Dwrk.threads=10 -Dwrk.duration=30s` |
| 150 | `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk -Dwrk.connections=150 -Dwrk.threads=10 -Dwrk.duration=30s` |
| 200 | `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk -Dwrk.connections=200 -Dwrk.threads=10 -Dwrk.duration=30s` |
| 250 | `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk -Dwrk.connections=250 -Dwrk.threads=10 -Dwrk.duration=30s` |
| 300 | `./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk -Dwrk.connections=300 -Dwrk.threads=10 -Dwrk.duration=30s` |

#### After Each Test - Extract Values From:
1. **Health Check Latencies**: `benchmarking/benchmark-integration-wrk/target/benchmark-results/gh-pages-ready/data/benchmark-data.json`
   - `.benchmarks[0].percentiles["50.0"]` (healthLiveCheck)
   - `.benchmarks[0].percentiles["75.0"]`
   - `.benchmarks[0].percentiles["90.0"]`
   - `.benchmarks[0].percentiles["99.0"]`

2. **JWT Validation Latencies**: Same file
   - `.benchmarks[1].percentiles["50.0"]` (jwtValidation)
   - `.benchmarks[1].percentiles["75.0"]`
   - `.benchmarks[1].percentiles["90.0"]`
   - `.benchmarks[1].percentiles["99.0"]`

3. **CPU/Thread Metrics**:
   - Health: `benchmarking/benchmark-integration-wrk/target/benchmark-results/prometheus/healthLiveCheck-server-metrics.json`
   - JWT: `benchmarking/benchmark-integration-wrk/target/benchmark-results/prometheus/jwtValidation-server-metrics.json`
   - Extract: `.resources.cpu.process.peak_percent` and `.resources.threads.peak`

4. **Timeouts**:
   - Health: `benchmarking/benchmark-integration-wrk/target/benchmark-results/wrk/wrk-health-results.txt`
   - JWT: `benchmarking/benchmark-integration-wrk/target/benchmark-results/wrk/wrk-jwt-results.txt`
   - Look for "Socket errors" line

#### Results Table (to be updated after each test):

**Health Check Endpoint (`/q/health/live`)**

| Connections | P50 (ms) | P75 (ms) | P90 (ms) | P99 (ms) | CPU Peak % | Threads | Timeouts | Oddities/Notes |
|------------|----------|----------|----------|----------|------------|---------|----------|----------------|
| 10         |          |          |          |          |            |         |          |                |
| 25         |          |          |          |          |            |         |          |                |
| 50         |          |          |          |          |            |         |          |                |
| 100        |          |          |          |          |            |         |          |                |
| 150        |          |          |          |          |            |         |          |                |
| 200        |          |          |          |          |            |         |          |                |
| 250        |          |          |          |          |            |         |          |                |
| 300        | 6.18     | 129      | 209      | 877      | 47.4       | 128     | 146      | Baseline from previous test |

**JWT Validation Endpoint (`/jwt/validate`)**

| Connections | P50 (ms) | P75 (ms) | P90 (ms) | P99 (ms) | CPU Peak % | Threads | Timeouts | Oddities/Notes |
|------------|----------|----------|----------|----------|------------|---------|----------|----------------|
| 10         |          |          |          |          |            |         |          |                |
| 25         |          |          |          |          |            |         |          |                |
| 50         |          |          |          |          |            |         |          |                |
| 100        |          |          |          |          |            |         |          |                |
| 150        |          |          |          |          |            |         |          |                |
| 200        |          |          |          |          |            |         |          |                |
| 250        |          |          |          |          |            |         |          |                |
| 300        | 13.77    | 142      | 222      | 784      | 78.5       | 124     | 146      | Baseline from previous test |

**Expected outcome:** Identify connection count where P99 jumps above 100ms for each endpoint. Compare to see if JWT adds overhead or if both degrade similarly.

### Phase 2: Isolate the Layer
**Goal:** Determine if it's Vert.x, Virtual Threads, or Quarkus HTTP layer

#### Test 2.1: Bypass Docker Network
```bash
# Run WRK inside container network to eliminate Docker bridge
docker run --rm --network jwt-integration \
  williamyeh/wrk -t 10 -c 300 -d 30s https://cui-jwt-integration-tests:8443/q/health/live
```

#### Test 2.2: Platform Threads vs Virtual Threads
```properties
# Disable virtual threads in application.properties
quarkus.virtual-threads=false
# Rebuild and test
```

#### Test 2.3: Different Event Loop Configurations
```properties
# Test 1: Increase event loops (default is core count)
quarkus.vertx.event-loops-pool-size=20

# Test 2: Increase worker pool
quarkus.vertx.worker-pool-size=200

# Test 3: Reduce max event loop execute time (detect blocking)
quarkus.vertx.max-event-loop-execute-time=500ms
```

### Phase 3: Profile the Bottleneck
**Goal:** Identify exactly what's blocking/slowing

#### Test 3.1: Thread Dump During High Latency
```bash
# Start load test
wrk -t 10 -c 300 -d 120s https://localhost:10443/q/health/live &

# After 30 seconds, take thread dumps
for i in {1..5}; do
  docker exec cui-jwt-integration-tests jstack 1 > thread-dump-$i.txt
  sleep 5
done
```

#### Test 3.2: Vert.x Metrics
```bash
# Enable Vert.x metrics in application.properties
quarkus.vertx.metrics.enabled=true

# During load, check event loop utilization
curl -k https://localhost:10443/q/metrics | grep vertx
```

#### Test 3.3: Connection Accept Monitoring
```bash
# Monitor accept() system calls
docker exec cui-jwt-integration-tests strace -p 1 -e accept,accept4 -c
```

### Phase 4: Test Hypotheses

#### Hypothesis 1: Event Loop Blocking
**Test:** If event loop is blocked, warnings should appear in logs
```bash
# Check for blocked thread warnings during load
docker logs cui-jwt-integration-tests | grep -i "blocked\|slow"
```

#### Hypothesis 2: Virtual Thread Scheduling Overhead
**Test:** Compare with platform threads
```bash
# Run same test with platform threads vs virtual threads
# Compare P99 latencies
```

#### Hypothesis 3: Connection Accept Backlog
**Test:** Increase accept backlog
```properties
quarkus.http.accept-backlog=2048  # Default might be 128
```

#### Hypothesis 4: SSL/TLS Handshake Overhead
**Test:** Compare HTTPS vs HTTP
```bash
# Test with HTTP (if possible to disable TLS)
wrk -t 10 -c 300 -d 30s http://localhost:10080/q/health/live
```

### Phase 5: Minimal Reproduction
**Goal:** Create smallest possible test case

1. Create a new Quarkus app with just a health endpoint
2. No JWT, no additional dependencies
3. Test with 300 connections
4. If problem persists, it's core Quarkus/Vert.x issue
5. If problem disappears, add components back one by one

## Execution Order

1. **First:** Phase 1 - Find breaking point (15 minutes)
2. **Second:** Phase 2.1 - Bypass Docker (5 minutes)
3. **Third:** Phase 3.1 - Thread dumps (10 minutes)
4. **Fourth:** Based on findings, select relevant tests from Phase 2-4

## Success Criteria

- Identify connection count where degradation starts
- Determine which layer causes the bottleneck
- Find configuration that maintains <10ms P99 at 300 connections
- Document root cause with evidence

## Tools Needed

- wrk (already available)
- jstack (in JDK container)
- strace (might need to install)
- curl for metrics
- grep/awk for log analysis

## Expected Deliverable

A clear diagnosis stating:
1. At X connections, latency degrades from Yms to Zms
2. The bottleneck is in [specific component]
3. Evidence: [thread dumps/metrics/logs]
4. Solution: [configuration change/code fix]