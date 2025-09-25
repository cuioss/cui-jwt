# Prometheus Real-Time Metrics Implementation Plan

## ⚠️ CRITICAL: Task Execution Rules

**BEFORE STARTING ANY TASK**: Read and completely understand [metrics-system.adoc](doc/metrics-system.adoc)
- Understand the architecture diagrams
- Understand the data flow
- Understand why we're replacing the current system
- Understand the integration points for WRK and JMH

**EXECUTION RULES**:
1. **COMPLETE ONE TASK BEFORE STARTING THE NEXT** - No parallel work
2. **MARK TASK AS COMPLETED** immediately when done ✅
3. **NO TASK MAY BE STARTED** before the previous one is fully implemented
4. **ALL BUILD ERRORS/WARNINGS MUST BE FIXED** before moving to next task

## Phase 1: Infrastructure Setup

- [ ] Add Prometheus service to Docker Compose
  - Add prometheus service definition
  - Configure scraping for Quarkus metrics endpoint
  - Set scrape interval to 2 seconds
  - Test that Prometheus can access Quarkus metrics

- [ ] Create prometheus.yml configuration
  - Configure job for quarkus-benchmark
  - Set target to quarkus-app:10443/q/metrics
  - Configure TLS for self-signed certificates
  - Verify configuration with docker-compose up

## Phase 2: PrometheusClient Implementation

- [ ] Create PrometheusClient class in cui-benchmarking-common
  - Implement HTTP client for Prometheus API
  - Add query_range endpoint support
  - Parse JSON response to TimeSeries objects
  - Add proper error handling

- [ ] Run pre-commit build for cui-benchmarking-common
  ```bash
  ./mvnw clean install -Ppre-commit -pl benchmarking/cui-benchmarking-common
  ```
  - Fix ALL SonarQube violations
  - Follow project code quality rules
  - Repeat until build passes with ZERO issues

- [ ] Create PrometheusClientTest with comprehensive tests
  - Use @EnableMockWebServer annotation (follow MetricsDownloaderTest pattern)
  - Use real Prometheus test data (fetch actual responses for verification)
  - Test query_range with authentic JSON responses
  - Test error conditions and timeout scenarios
  - Test time range calculations with realistic timestamps
  - Add final integration verification step with live Prometheus data

- [ ] Run pre-commit build again after tests
  ```bash
  ./mvnw clean install -Ppre-commit -pl benchmarking/cui-benchmarking-common
  ```
  - Fix ALL test failures
  - Fix ANY new violations introduced
  - Ensure 100% clean build

## Phase 3: MetricsOrchestrator Enhancement

- [ ] Add collectBenchmarkMetrics method to MetricsOrchestrator
  - Accept benchmarkName, startTime, endTime, outputDir parameters
  - Use PrometheusClient to fetch metrics for time window
  - Process time-series data (calculate avg, p50, p95, max)
  - Export to {benchmarkName}-metrics.json format

- [ ] Run pre-commit build for cui-benchmarking-common
  ```bash
  ./mvnw clean install -Ppre-commit -pl benchmarking/cui-benchmarking-common
  ```
  - Fix ALL SonarQube violations
  - Ensure backward compatibility
  - Verify existing tests still pass

- [ ] Create PrometheusModuleDispatcher for Prometheus API mocking
  - Create similar to MetricsModuleDispatcher pattern
  - Handle /api/v1/query_range endpoint
  - Return real Prometheus JSON responses from test resources
  - Support error scenarios (500, 503, timeout, invalid JSON)

- [ ] Create MetricsOrchestratorTest for new functionality
  - Use @EnableMockWebServer with PrometheusModuleDispatcher
  - Test collectBenchmarkMetrics with realistic Prometheus responses
  - Test metrics calculation logic with real time-series data
  - Test JSON export format matches expected structure
  - Verify file output structure and naming

- [ ] Run final pre-commit build for common module
  ```bash
  ./mvnw clean install -Ppre-commit -pl benchmarking/cui-benchmarking-common
  ```
  - Ensure ZERO violations
  - All tests must pass
  - No regression in existing functionality

## Phase 4: WRK Integration

- [ ] Update WrkBenchmarkConverter to capture timestamps
  - Parse benchmark start/end times from output
  - Store timestamps in BenchmarkResult
  - Ensure backward compatibility

- [ ] Modify WrkResultPostProcessor to use MetricsOrchestrator
  - Call collectBenchmarkMetrics for each benchmark
  - Pass correct timestamps and benchmark names
  - Store results in target/prometheus/ directory

- [ ] Run pre-commit build for benchmark-integration-wrk
  ```bash
  ./mvnw clean install -Ppre-commit -pl benchmarking/benchmark-integration-wrk
  ```
  - Fix ALL SonarQube violations
  - Ensure integration tests pass
  - Verify no regressions

## Phase 5: JMH Integration

- [ ] Update QuarkusIntegrationRunner to capture timestamps
  - Record start time before benchmark execution
  - Record end time after benchmark completion
  - Store per-benchmark timestamps

- [ ] Modify processResults to call MetricsOrchestrator
  - Extract timestamps from RunResult
  - Call collectBenchmarkMetrics for each benchmark
  - Use same output format as WRK

- [ ] Run pre-commit build for benchmark-integration-quarkus
  ```bash
  ./mvnw clean install -Ppre-commit -pl benchmarking/benchmark-integration-quarkus
  ```
  - Fix ALL SonarQube violations
  - Verify benchmark execution still works
  - Check metrics are collected correctly

## Phase 6: Remove Deprecated Code

- [ ] Remove post-benchmark metrics collection from QuarkusIntegrationRunner
  - Delete processQuarkusMetrics method (lines 135-153)
  - Remove call from processResults
  - Clean up unused imports

- [ ] Remove post-benchmark metrics from WrkResultPostProcessor
  - Delete processQuarkusMetrics method (lines 196-227)
  - Remove "download-after" mode handling
  - Clean up related code

- [ ] Delete deprecated metrics processing code
  - Remove MetricsDownloader if only used for post-benchmark
  - Clean up MetricsFileProcessor if obsolete
  - Update MetricsTransformer to handle new format

- [ ] Run pre-commit build for ALL modified modules
  ```bash
  ./mvnw clean install -Ppre-commit -pl benchmarking/benchmark-integration-wrk
  ./mvnw clean install -Ppre-commit -pl benchmarking/benchmark-integration-quarkus
  ./mvnw clean install -Ppre-commit -pl benchmarking/cui-benchmarking-common
  ```
  - Fix ALL SonarQube violations in each module
  - Ensure no broken dependencies
  - Verify all tests pass

- [ ] Remove deprecated artifacts from file system
  - Delete target/metrics-download directories
  - Remove system-metrics.log references
  - Clean up unused configuration properties

## Phase 7: Integration Testing

- [ ] Run complete benchmark suite with Prometheus
  - Start Docker Compose with Prometheus
  - Execute WRK benchmarks
  - Verify prometheus/{benchmark}-metrics.json created
  - Check metrics contain real-time CPU data

- [ ] Validate JMH integration
  - Run JMH benchmarks
  - Verify metrics collection during execution
  - Compare with WRK metrics format
  - Ensure consistency

- [ ] Run full build with all tests
  ```bash
  ./mvnw clean install -Ppre-commit
  ```
  - Must pass with ZERO violations
  - All integration tests must succeed
  - No deprecation warnings

## Phase 8: Documentation Updates

- [ ] Update metrics-system.adoc
  - Remove all "deprecated" mentions
  - Update diagrams with final architecture
  - Add troubleshooting section

- [ ] Create user guide for new metrics system
  - How to run benchmarks with Prometheus
  - How to interpret metrics output
  - Migration guide from old system

## Completion Checklist

- [ ] All tasks above completed and marked ✅
- [ ] Zero build violations in pre-commit profile
- [ ] All tests passing (unit and integration)
- [ ] Prometheus metrics successfully collected
- [ ] Old metrics system completely removed
- [ ] Documentation fully updated