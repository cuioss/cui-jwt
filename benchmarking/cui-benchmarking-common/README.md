# CUI Benchmarking Common

Shared infrastructure and utilities for JWT validation benchmarking.

## Purpose

This module provides the foundational framework for all benchmark operations, ensuring consistency and reusability across different benchmark types (micro-benchmarks and integration tests).

## Key Features

- **Benchmark Framework**: Abstract runners using Template Method pattern
- **Configuration Management**: Centralized configuration with builder pattern
- **Metrics Collection**: Standardized interfaces for various metrics sources
- **Report Generation**: Badges, HTML reports, and GitHub Pages artifacts
- **Token Management**: JWT token pool with Keycloak integration
- **HTTP Utilities**: Optimized clients with connection pooling

## Usage

This module is used as a dependency by:
- `benchmark-library` - For micro-benchmarks
- `benchmark-integration-quarkus` - For integration tests

## Architecture

See [Architecture.adoc](../Architecture.adoc) for detailed module responsibilities and code placement guidelines.

## Package Structure

```
de.cuioss.benchmarking.common/
├── config/          # Configuration and settings
├── http/            # HTTP client management
├── metrics/         # Metrics collection and processing
├── report/          # Report and artifact generation
├── repository/      # Token and data repositories
├── runner/          # Benchmark execution framework
├── util/            # Utility classes
└── validation/      # Result validation
```

## Building

```bash
./mvnw clean install -pl benchmarking/cui-benchmarking-common
```

## Dependencies

- JMH (Java Microbenchmark Harness)
- Gson for JSON processing
- Apache Commons IO for file operations
- SLF4J for logging