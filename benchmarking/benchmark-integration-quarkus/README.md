# Benchmark Integration Quarkus

End-to-end integration benchmarks for JWT validation in Quarkus applications.

## Purpose

This module performs realistic performance testing against containerized Quarkus applications, measuring real-world performance including network overhead and service interactions.

## Key Features

- **End-to-End Testing**: Complete JWT validation flow in production-like environment
- **Container Management**: Automatic Docker container lifecycle management
- **Service Orchestration**: Manages Quarkus + Keycloak service stack
- **Metrics Integration**: Collects and processes Quarkus application metrics

## Benchmark Profiles

### Integration Benchmarks
```bash
./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark
```
- Tests against running containers
- Configuration: 5 iterations, 1 warmup, 24 threads

### Container Rebuild
```bash
./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Prebuild-container
```
- Rebuilds Quarkus container before testing

## Service Architecture

```
Quarkus Application (https://localhost:10443)
├── /jwt/validate     # JWT validation endpoint
├── /q/health         # Health check endpoint
└── /q/metrics        # Metrics endpoint

Keycloak Server (https://localhost:1443)
└── /auth/realms/...  # Token issuance
```

## Key Benchmarks

- `JwtValidationBenchmark` - JWT validation endpoint performance
- `JwtHealthBenchmark` - Health check baseline measurements

## Architecture

See [Architecture.adoc](../Architecture.adoc) for detailed module responsibilities and code placement guidelines.

## Results

Benchmark results are generated in:
```
target/benchmark-results/
├── badges/                      # Performance badges
├── reports/                     # HTML reports
├── gh-pages-ready/              # GitHub Pages deployment
└── integration-benchmark-result.json  # Raw results
```

## Prerequisites

- Docker must be running
- Ports 10443 and 1443 must be available
- Quarkus application container must be built

## Dependencies

- `cui-benchmarking-common` - Shared infrastructure
- Docker containers for Quarkus and Keycloak
- HTTP client for endpoint testing