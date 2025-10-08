#!/bin/bash
# WRK Docker Wrapper - Multi-architecture support (ARM64 + x86_64)
# Lua scripts are embedded in the Docker image at /scripts/
# Communication: All parameters passed through, stdout/stderr captured by caller

set -e

# Get project root to find Dockerfile
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Navigate from wrk-scripts -> resources -> main -> src -> benchmark-integration-wrk -> benchmarking -> project-root
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../../.." && pwd)"
DOCKERFILE="$PROJECT_ROOT/benchmarking/benchmark-integration-wrk/Dockerfile.wrk"
IMAGE_NAME="wrk:local"

# Build image if it doesn't exist (one-time build)
if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
    echo "Building multi-arch WRK image (first run only)..." >&2
    docker build -t "$IMAGE_NAME" -f "$DOCKERFILE" "$PROJECT_ROOT" >&2
    echo "WRK image built successfully" >&2
fi

# Docker network (defaults to host for external access)
DOCKER_NETWORK="${WRK_DOCKER_NETWORK:-host}"

# Pass through TOKEN_DATA if set (for JWT benchmark Lua script)
TOKEN_DATA_FLAG=""
if [ -n "$TOKEN_DATA" ]; then
    TOKEN_DATA_FLAG="-e TOKEN_DATA"
fi

# Run WRK with all arguments passed through
# Output goes directly to stdout (captured by Maven)
docker run --rm \
    --network "$DOCKER_NETWORK" \
    $TOKEN_DATA_FLAG \
    "$IMAGE_NAME" \
    "$@"