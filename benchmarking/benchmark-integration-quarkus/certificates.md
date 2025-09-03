# SSL Certificate Management for Benchmarks

## Current State

The benchmark system currently uses `trustAllCerts` configuration which accepts ANY certificate from ANY server. While this works for testing, it's not secure even in test environments as it's vulnerable to man-in-the-middle attacks.

## Security Issue with Current Approach

```java
// Current insecure approach - accepts ALL certificates
TrustManager[] trustAllCerts = new TrustManager[] {
    new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
};
```

**Problems:**
- No certificate validation whatsoever
- Vulnerable to man-in-the-middle attacks
- Accepts malicious certificates
- Not following security best practices

## Improved Approach: Custom TrustManager

Replace the trust-all approach with a **Custom TrustManager** that only trusts specific test certificates.

### Benefits

1. **Security**: Only trusts known test certificates (e.g., localhost self-signed cert)
2. **Flexibility**: Falls back to standard SSL validation if test cert not found
3. **Maintainability**: Easy to update when test certificates change
4. **Production-ready**: Can be configured for real certificates

### Implementation Strategy

#### Option 1: Specific Certificate Trust (Recommended)
- Load the specific `localhost.crt` certificate used by benchmark services
- Create a custom TrustManager that only accepts this certificate
- Maintain hostname verification limited to `localhost`

#### Option 2: Certificate Pinning
- Pin the SHA256 fingerprint of the test certificate
- Use OkHttp's built-in certificate pinning feature
- More resilient to certificate changes

#### Option 3: Environment-Based Configuration
- Allow switching between security modes via system properties
- `benchmark.ssl.mode`: `secure`, `custom`, `pinned`, or `insecure` (fallback)
- Default to secure mode, explicitly opt-in to test modes

### Certificate Locations

Current test certificates are located at:
- Docker: `/app/certificates/localhost.crt`, `/app/certificates/localhost.key`
- Source: `cui-jwt-quarkus-integration-tests/src/main/docker/certificates/`

### Hostname Verification

Instead of accepting all hostnames:
```java
.hostnameVerifier((hostname, session) -> true) // Insecure - accepts ANY hostname
```

Use specific hostname validation:
```java
.hostnameVerifier((hostname, session) -> "localhost".equals(hostname)) // Only localhost
```

### Configuration Properties

Proposed system properties for SSL configuration:
- `benchmark.ssl.mode` - SSL validation mode (`secure`, `custom`, `pinned`, `insecure`)
- `benchmark.ssl.cert.path` - Path to custom certificate file
- `benchmark.ssl.cert.pin` - SHA256 pin for certificate pinning

## Migration Plan

1. ✅ Document current state and security issues
2. 🔄 Implement custom TrustManager with specific certificate trust
3. 🔄 Add fallback to standard SSL validation
4. 🔄 Update HttpClientFactory to use improved SSL configuration
5. 🔄 Test with benchmark infrastructure
6. 🔄 Update documentation with new approach

## Testing

The improved SSL configuration should be tested with:
1. Valid localhost certificate (should succeed)
2. Invalid/expired certificate (should fail)
3. No certificate file present (should fall back to standard validation)
4. Different hostname (should fail hostname verification)

This ensures both security and functionality are maintained.