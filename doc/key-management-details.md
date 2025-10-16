# Key Management - Detailed Documentation

This document provides detailed information about the key management implementation that complements the simplified sequence diagram in `plantuml/key-management.puml`.

## Overview

OAuth-Sheriff implements a sophisticated key management system that handles:
- Remote JWKS fetching with HTTP resilience
- Automatic key rotation with grace periods
- Multiple key loader types (HTTP, file, in-memory)
- Thread-safe concurrent key access

## Components

### HttpJwksLoader
The main component for loading keys from remote JWKS endpoints.

**Features:**
- Async initialization with `CompletableFuture<LoaderStatus>`
- Uses `ResilientHttpHandler` for HTTP caching & retry logic
- Optional background refresh at configured intervals
- Key rotation with grace period support (Issue #110)
- Retired keys tracking (configurable max N sets)
- Security event tracking via `SecurityEventCounter`

### JWKSKeyLoader
Handles parsing and processing of JWKS data.

**Responsibilities:**
- Parse JWKS JSON structure
- Convert public keys to Java objects
- Validate key algorithms
- Apply algorithm preferences
- Create `KeyInfo` objects

### Key Storage

#### Current Keys
- **Implementation:** `ConcurrentHashMap<String, KeyInfo>` indexed by Key ID (kid)
- **Thread-safety:** Full concurrent access support
- **Content:** Currently active public keys from JWKS endpoint

#### Retired Keys
- **Implementation:** `ConcurrentLinkedDeque` with timestamps
- **Purpose:** Grace period support (Issue #110)
- **Behavior:**
  - Keeps retired keys for configurable duration
  - Allows validation of tokens signed with old keys during rotation
  - Automatic cleanup of keys older than grace period
  - Configurable maximum number of retired key sets

### JwksLoaderFactory
Factory for creating different types of key loaders.

**Loader Types:**
1. **HttpJwksLoader:** Remote JWKS with automatic refresh
   - Created with `createHttpLoader(config)`
   - Supports background refresh and key rotation

2. **JWKSKeyLoader (file):** Local JWKS file
   - Created with `createFileLoader(path)`
   - File content loaded at build/startup time
   - No automatic refresh

3. **JWKSKeyLoader (memory):** Embedded JWKS
   - Created with `createInMemoryLoader(content)`
   - Useful for testing and static configurations
   - No automatic refresh

## Detailed Flows

### Initialization Flow (Detailed)

```
1. IssuerConfig requests loader from JwksLoaderFactory
2. Factory creates HttpJwksLoader with configuration
3. IssuerConfig calls initJWKSLoader(securityEventCounter)
4. HttpJwksLoader creates ResilientHttpHandler
5. Initial JWKS fetch via HTTP GET with retry logic
6. Endpoint returns JWKS data
7. HttpLoader calls updateKeys(jwks)
8. Creates JWKSKeyLoader with JWKS data
9. JWKSKeyLoader parses and processes keys:
   - Validates key structure
   - Converts to Java PublicKey objects
   - Validates algorithms
   - Applies preferences
10. Stores JWKSKeyLoader in CurrentKeys
11. If background refresh enabled:
    - Schedules periodic refresh at configured interval
12. Returns CompletableFuture<LoaderStatus>
```

### Key Retrieval (Detailed)

```
1. IssuerConfig requests key via getKeyInfo(kid)
2. HttpLoader looks up kid in CurrentKeys
3. If found:
   - Return Optional<KeyInfo> immediately
4. If not found:
   - Check RetiredKeys within grace period
   - Search through retired key sets by timestamp
   - If found and within grace period:
     - Return Optional<KeyInfo>
   - Else:
     - Return Optional.empty()
```

### Automatic Key Rotation (Detailed)

```
1. Background scheduler triggers after refreshInterval
2. HttpLoader calls HttpHandler.load() with cache support
3. HTTP GET with "If-Modified-Since" header
4. Endpoint responds:
   - 304 Not Modified: Skip rotation
   - 200 OK with new data: Proceed with rotation
5. If rotation needed:
   a. Create new JWKSKeyLoader with new JWKS
   b. Parse and process new keys
   c. Move current keys to RetiredKeys with timestamp
   d. Update CurrentKeys with new keys
   e. Cleanup RetiredKeys:
      - Remove keys older than grace period
      - Limit to max N retired key sets
   f. Track rotation event in SecurityEventCounter
```

## Configuration

### HTTP Loader Configuration
- **JWKS Endpoint URL:** Remote JWKS URL
- **Refresh Interval:** How often to check for key updates (optional)
- **Grace Period Duration:** How long to keep retired keys (Issue #110)
- **Max Retired Sets:** Maximum number of historical key sets to retain
- **HTTP Retry:** Configured via ResilientHttpHandler
- **Cache:** HTTP cache headers support

### File Loader Configuration
- **File Path:** Local JWKS file location
- **Load Time:** Build time or startup

### In-Memory Loader Configuration
- **JWKS Content:** Embedded JWKS string

## Security Considerations

### Grace Period (Issue #110)
The grace period mechanism is critical for zero-downtime key rotation:
- Prevents token validation failures during rotation
- Allows tokens signed with "old" keys to remain valid temporarily
- Configurable duration based on token lifetime and rotation frequency
- Automatic cleanup prevents unlimited key accumulation

### Thread Safety
- All key storage uses concurrent data structures
- Safe for high-throughput token validation
- No blocking operations during key lookup

### HTTP Resilience
- Retry logic for transient failures
- HTTP caching to reduce endpoint load
- If-Modified-Since support to avoid unnecessary transfers

## Monitoring

The system tracks security events via `SecurityEventCounter`:
- Key rotation events
- JWKS fetch failures
- Key validation errors
- Grace period usage

## References

- **Issue #110:** Grace period implementation for key rotation
- **ResilientHttpHandler:** HTTP client with retry and caching
- **IssuerConfig:** Per-issuer configuration and key management
