# CUI JWT Benchmarking

Comprehensive performance testing infrastructure for JWT validation.

## Modules

| Module | Purpose | Execution Time |
|--------|---------|----------------|
| [cui-benchmarking-common](cui-benchmarking-common/) | Shared infrastructure and utilities | N/A (library) |
| [benchmark-library](benchmark-library/) | Micro-benchmarks for JWT library | < 10 minutes |
| [benchmark-integration-quarkus](benchmark-integration-quarkus/) | Integration tests with Quarkus | < 15 minutes |

## Quick Start

### Run All Benchmarks
```bash
# Library benchmarks
./mvnw clean verify -pl benchmarking/benchmark-library -Pbenchmark

# Integration benchmarks (requires Docker)
./mvnw clean verify -pl benchmarking/benchmark-integration-quarkus -Pbenchmark
```

### Run with Profiling
```bash
# Library benchmarks with JFR
./mvnw clean verify -pl benchmarking/benchmark-library -Pbenchmark-jfr
```

## Architecture Documentation

- [Architecture.adoc](Architecture.adoc) - Module responsibilities and code organization
- [plan.adoc](plan.adoc) - Refactoring and improvement plan

## Results Location

All benchmark results are generated in:
```
target/benchmark-results/
├── badges/           # SVG badges for README/dashboards
├── reports/          # Interactive HTML reports
├── data/            # JSON metrics data
└── gh-pages-ready/  # GitHub Pages deployment
```

## Development

When adding new benchmarks or utilities:
1. Review [Architecture.adoc](Architecture.adoc) for code placement guidelines
2. Follow the decision tree to determine the correct module
3. Use existing patterns and base classes
4. Ensure proper metrics collection and reporting

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker (for integration tests)
- Available ports: 10443, 1443 (for integration tests)