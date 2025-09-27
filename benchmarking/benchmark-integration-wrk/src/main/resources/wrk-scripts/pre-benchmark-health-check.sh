#!/bin/bash
# Pre-benchmark health check script
# Verifies all required services are available before running benchmarks

set -e

# Verify required environment variables are set
if [ -z "$INTEGRATION_SERVICE_URL" ]; then
    echo "ERROR: INTEGRATION_SERVICE_URL not set by Maven"
    exit 4
fi
if [ -z "$PROMETHEUS_URL" ]; then
    echo "ERROR: PROMETHEUS_URL not set by Maven"
    exit 4
fi
if [ -z "$KEYCLOAK_URL" ]; then
    echo "ERROR: KEYCLOAK_URL not set by Maven"
    exit 4
fi

# Check Quarkus
if curl -k -s -f -o /dev/null "$INTEGRATION_SERVICE_URL/q/health/live" 2>/dev/null; then
    echo "Quarkus: OK at $INTEGRATION_SERVICE_URL"
else
    echo "Quarkus: FAILED at $INTEGRATION_SERVICE_URL"
    exit 1
fi

# Check Prometheus
if curl -k -s -f -o /dev/null "$PROMETHEUS_URL/api/v1/query?query=up" 2>/dev/null; then
    echo "Prometheus: OK at $PROMETHEUS_URL"
else
    echo "Prometheus: FAILED at $PROMETHEUS_URL"
    exit 2
fi

# Check Keycloak
if curl -k -s -f -o /dev/null "$KEYCLOAK_URL" 2>/dev/null; then
    echo "Keycloak: OK at $KEYCLOAK_URL"
else
    echo "Keycloak: FAILED at $KEYCLOAK_URL"
    exit 3
fi