# Dockerfile for wrk HTTP benchmarking tool
FROM alpine:latest

# Install wrk and dependencies
RUN apk add --no-cache \
    wrk \
    curl \
    bash \
    jq

# Create working directory
WORKDIR /benchmark

# Copy Lua scripts
COPY scripts/ ./scripts/

# Default command
CMD ["wrk", "--help"]