# Enhanced IssuerConfigResolver with Unified Async Loading

## Key Insight

`IssuerConfigResolver` is already the central management point. By enhancing `JwksLoader.initJWKSLoader()` to trigger async loading, we achieve a unified initialization pattern that works for ALL loader types (HTTP, memory, file).

## Core Principle

**Initialization should include loading.** The `initJWKSLoader()` method should not just set up dependencies but also trigger the actual loading of key material. Even memory-based loaders need time for cryptographic operations.

## The Solution

### 1. Enhanced JwksLoader Interface

```java
public interface JwksLoader {
    /**
     * Initialize loader and trigger async loading of key material.
     * @return CompletableFuture that completes when keys are loaded
     */
    CompletableFuture<LoaderStatus> initJWKSLoader(@NonNull SecurityEventCounter counter);

    /**
     * Get key by ID. No loading logic here - just retrieval.
     * Assumes keys are already loaded via initJWKSLoader().
     */
    Optional<KeyInfo> getKeyInfo(String kid);

    /**
     * Get current status (lock-free).
     */
    LoaderStatus getLoaderStatus();
}
```

### 2. Enhanced IssuerConfigResolver

```java
public class IssuerConfigResolver {
    private final ConcurrentHashMap<String, IssuerConfig> mutableCache;
    private final ConcurrentHashMap<String, CompletableFuture<LoaderStatus>> loadingFutures;
    private final ScheduledExecutorService executor;

    public IssuerConfigResolver(List<IssuerConfig> issuerConfigs,
                               SecurityEventCounter securityEventCounter,
                               ScheduledExecutorService executor) {
        this.securityEventCounter = securityEventCounter;
        this.mutableCache = new ConcurrentHashMap<>();
        this.loadingFutures = new ConcurrentHashMap<>();
        this.executor = executor;

        // Trigger ALL async loading in constructor
        for (IssuerConfig config : issuerConfigs) {
            if (config.isEnabled()) {
                String issuer = config.getIssuerIdentifier();

                // Initialize and start loading
                CompletableFuture<LoaderStatus> future =
                    config.getJwksLoader().initJWKSLoader(securityEventCounter);

                loadingFutures.put(issuer, future);

                // When loading completes successfully, cache the config
                future.thenAccept(status -> {
                    if (status == LoaderStatus.OK) {
                        mutableCache.put(issuer, config);
                        LOGGER.info(INFO.ISSUER_CONFIG_LOADED.format(issuer));
                    } else {
                        LOGGER.warn(WARN.ISSUER_CONFIG_LOAD_FAILED.format(issuer, status));
                    }
                });
            }
        }
    }

    /**
     * Resolve config - only returns configs with LoaderStatus.OK
     */
    IssuerConfig resolveConfig(@NonNull String issuer) {
        // Fast path - check cache for already loaded configs
        IssuerConfig cached = mutableCache.get(issuer);
        if (cached != null && cached.getJwksLoader().getLoaderStatus() == LoaderStatus.OK) {
            return cached;
        }

        // Check if loading in progress
        CompletableFuture<LoaderStatus> future = loadingFutures.get(issuer);
        if (future != null) {
            try {
                // Wait for loading to complete (with timeout)
                LoaderStatus status = future.get(5, TimeUnit.SECONDS);
                if (status == LoaderStatus.OK) {
                    return mutableCache.get(issuer);
                }
            } catch (TimeoutException e) {
                LOGGER.warn(WARN.JWKS_LOAD_TIMEOUT.format(issuer));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn(WARN.JWKS_LOAD_INTERRUPTED.format(issuer));
            } catch (ExecutionException e) {
                LOGGER.error(ERROR.JWKS_LOAD_EXECUTION_FAILED.format(e.getCause(), issuer));
            }
        }

        throw new TokenValidationException("No healthy JWKS configuration for issuer: " + issuer);
    }
}
```

### 3. Enhanced HttpJwksLoader

```java
public class HttpJwksLoader implements JwksLoader, LoadingStatusProvider {
    private final AtomicReference<LoaderStatus> status =
        new AtomicReference<>(LoaderStatus.UNDEFINED);
    private final AtomicReference<JWKSKeyLoader> keyLoader = new AtomicReference<>();

    @Override
    public CompletableFuture<LoaderStatus> initJWKSLoader(@NonNull SecurityEventCounter counter) {
        this.securityEventCounter = counter;

        // Trigger async loading immediately
        return CompletableFuture.supplyAsync(() -> {
            try {
                status.set(LoaderStatus.LOADING);

                // Create ResilientHttpHandler and load
                ResilientHttpHandler<Jwks> handler = createHttpHandler();
                HttpResultObject<Jwks> result = handler.load();

                if (result.isValid() && result.getResult() != null) {
                    JWKSKeyLoader loader = JWKSKeyLoader.builder()
                        .jwksContent(result.getResult())
                        .jwksType(getJwksType())
                        .build();
                    loader.initJWKSLoader(counter); // Initialize the key loader
                    keyLoader.set(loader);

                    // Start background refresh if configured
                    if (config.getScheduledExecutorService() != null) {
                        startBackgroundRefresh();
                    }

                    status.set(LoaderStatus.OK);
                    return LoaderStatus.OK;
                }

                status.set(LoaderStatus.ERROR);
                return LoaderStatus.ERROR;

            } catch (IOException e) {
                LOGGER.error(ERROR.JWKS_HTTP_LOAD_FAILED.format(e, config.getIssuerIdentifier()));
                status.set(LoaderStatus.ERROR);
                return LoaderStatus.ERROR;
            } catch (JwksParseException e) {
                LOGGER.error(ERROR.JWKS_PARSE_FAILED.format(e, config.getIssuerIdentifier()));
                status.set(LoaderStatus.ERROR);
                return LoaderStatus.ERROR;
            }
        });
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        // NO ensureLoaded() - just retrieve!
        JWKSKeyLoader loader = keyLoader.get();
        if (loader == null) {
            return Optional.empty();
        }

        // Check current keys
        Optional<KeyInfo> key = loader.getKeyInfo(kid);
        if (key.isPresent()) return key;

        // Check retired keys (grace period)
        return keyRotationManager.findInRetiredKeys(getIssuerIdentifier(), kid);
    }

    @Override
    public LoaderStatus getLoaderStatus() {
        return status.get(); // Lock-free!
    }
}
```

### 4. Enhanced JWKSKeyLoader (Memory-based)

```java
public class JWKSKeyLoader implements JwksLoader {
    private final AtomicReference<LoaderStatus> status =
        new AtomicReference<>(LoaderStatus.UNDEFINED);

    @Override
    public CompletableFuture<LoaderStatus> initJWKSLoader(@NonNull SecurityEventCounter counter) {
        this.securityEventCounter = counter;

        // Even memory-based loading takes time (crypto operations)
        return CompletableFuture.supplyAsync(() -> {
            try {
                status.set(LoaderStatus.LOADING);
                initializeKeys(); // Parse and validate keys
                status.set(LoaderStatus.OK);
                return LoaderStatus.OK;
            } catch (InvalidKeySpecException e) {
                LOGGER.error(ERROR.JWKS_KEY_SPEC_INVALID.format(e, "memory-loader"));
                status.set(LoaderStatus.ERROR);
                return LoaderStatus.ERROR;
            } catch (NoSuchAlgorithmException e) {
                LOGGER.error(ERROR.JWKS_ALGORITHM_UNSUPPORTED.format(e, "memory-loader"));
                status.set(LoaderStatus.ERROR);
                return LoaderStatus.ERROR;
            }
        });
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        // NO checking - just retrieve!
        return Optional.ofNullable(keyMap.get(kid));
    }
}
```

## Benefits

### 1. **Unified Pattern**
All loader types use the same async initialization pattern:
- HTTP loaders fetch from network
- Memory loaders parse and validate keys
- File loaders read and parse files

### 2. **Clean Separation**
- `initJWKSLoader()` - Initialize and load
- `getKeyInfo()` - Just retrieve (no loading logic)
- `getLoaderStatus()` - Just return status (lock-free)

### 3. **No More ensureLoaded()**
The scattered `ensureLoaded()` pattern is eliminated. IssuerConfigResolver ensures only ready configs are used.

### 4. **Early Loading**
All JWKS loading starts immediately in the constructor - keys are ready when the first JWT arrives.

### 5. **Simple Health Checks**
```java
boolean isHealthy = issuerConfigs.stream()
    .allMatch(c -> c.getJwksLoader().getLoaderStatus() == LoaderStatus.OK);
```

## Migration Path

1. **Update JwksLoader interface** - Change return type of initJWKSLoader
2. **Update implementations** - HttpJwksLoader, JWKSKeyLoader, etc.
3. **Enhance IssuerConfigResolver** - Add async loading management
4. **Remove ensureLoaded()** patterns throughout codebase
5. **Remove JwksStartupService** - No longer needed

## Comparison with Previous Proposals

| Aspect | Orchestrator Pattern | Enhanced Resolver |
|--------|---------------------|-------------------|
| Core Change | New orchestrator class | Enhanced initJWKSLoader |
| Lines of Code | ~1000+ | ~200 |
| Complexity | High | Low |
| Pattern | New abstraction | Use existing interface |
| Loading Trigger | Complex coordination | Simple constructor call |

## Complete HttpJwksLoader Implementation

See `httpjwksloader-clean.md` for the full clean redesign that includes:
- Single HTTP handler created in constructor (no ensureHttpCache garbage)
- Built-in key rotation with grace period (simple deque, no complex manager)
- Lock-free status via single AtomicReference
- Clean separation: init loads, getKeyInfo just retrieves
- Leverages ResilientHttpHandler (no reimplementing retry!)

## Conclusion

By making `initJWKSLoader()` actually initialize AND load, we achieve a clean, unified pattern that works for all loader types. The IssuerConfigResolver becomes the natural gatekeeper, ensuring only healthy configurations are used. This is simpler, cleaner, and more intuitive than creating new orchestration layers.