# HttpJwksLoader - Clean Redesign from Scratch

## Core Principle

Each HttpJwksLoader instance handles ONE issuer. Constructor stays simple, all I/O happens in async init.

## Complete Implementation

```java
public class HttpJwksLoader implements JwksLoader, LoadingStatusProvider, AutoCloseable {

    private final HttpJwksLoaderConfig config;
    private final AtomicReference<LoaderStatus> status = new AtomicReference<>(LoaderStatus.UNDEFINED);
    private final AtomicReference<JWKSKeyLoader> currentKeys = new AtomicReference<>();
    private final ConcurrentLinkedDeque<RetiredKeySet> retiredKeys = new ConcurrentLinkedDeque<>();
    private final AtomicReference<ResilientHttpHandler<Jwks>> httpHandler = new AtomicReference<>();
    private volatile ScheduledFuture<?> refreshTask;
    private SecurityEventCounter securityEventCounter;

    public HttpJwksLoader(@NonNull HttpJwksLoaderConfig config) {
        this.config = config;
        // That's it! No I/O in constructor
    }

    @Override
    public CompletableFuture<LoaderStatus> initJWKSLoader(@NonNull SecurityEventCounter counter) {
        this.securityEventCounter = counter;

        return CompletableFuture.supplyAsync(() -> {
            status.set(LoaderStatus.LOADING);

            // Resolve the handler (may involve well-known discovery)
            Optional<ResilientHttpHandler<Jwks>> handlerOpt = resolveJWKSHandler();
            if (handlerOpt.isEmpty()) {
                status.set(LoaderStatus.ERROR);
                String errorDetail = config.getWellKnownConfig() != null
                    ? "Well-known discovery failed"
                    : "No HTTP handler configured";
                LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED.format(errorDetail, config.getIssuerIdentifier()));
                return LoaderStatus.ERROR;
            }

            ResilientHttpHandler<Jwks> handler = handlerOpt.get();
            httpHandler.set(handler);

            // Load JWKS via ResilientHttpHandler
            HttpResultObject<Jwks> result = handler.load();

            if (result.isValid() && result.getResult() != null) {
                updateKeys(result.getResult());

                // Start background refresh if configured
                if (config.isBackgroundRefreshEnabled()) {
                    startBackgroundRefresh();
                }

                status.set(LoaderStatus.OK);
                LOGGER.info(INFO.JWKS_LOADED.format(config.getIssuerIdentifier()));
                return LoaderStatus.OK;
            }

            status.set(LoaderStatus.ERROR);
            LOGGER.error(ERROR.JWKS_LOAD_FAILED.format(result.getResultDetail(), config.getIssuerIdentifier()));
            return LoaderStatus.ERROR;
        });
    }

    /**
     * Resolves the JWKS handler based on configuration.
     * For well-known: performs discovery to get JWKS URL
     * For direct: uses the configured HTTP handler
     * @return Optional containing the handler, or empty if resolution failed
     */
    private Optional<ResilientHttpHandler<Jwks>> resolveJWKSHandler() {
        try {
            HttpHandler handler;

            if (config.getWellKnownConfig() != null) {
                // Well-known discovery - the resolver itself uses ResilientHttpHandler for retry!
                HttpWellKnownResolver resolver = config.getWellKnownConfig().createResolver();

                // This call may block but we're in async context
                Optional<String> jwksUri = resolver.getJwksUri();
                if (jwksUri.isEmpty()) {
                    return Optional.empty();
                }

                // Use overloaded method to create handler for discovered JWKS URL
                handler = config.getHttpHandler(jwksUri.get());
            } else {
                // Direct HTTP configuration - use existing handler from config
                handler = config.getHttpHandler();
            }

            if (handler == null) {
                return Optional.empty();
            }

            return Optional.of(new ResilientHttpHandler<>(handler, new JwksHttpContentConverter()));

        } catch (IllegalArgumentException e) {
            // Invalid URL format
            return Optional.empty();
        }
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        // Check current keys
        JWKSKeyLoader current = currentKeys.get();
        if (current != null) {
            Optional<KeyInfo> key = current.getKeyInfo(kid);
            if (key.isPresent()) return key;
        }

        // Check retired keys (grace period for Issue #110)
        Instant cutoff = Instant.now().minus(config.getKeyRotationGracePeriod());
        for (RetiredKeySet retired : retiredKeys) {
            if (retired.retiredAt.isAfter(cutoff)) {
                Optional<KeyInfo> key = retired.loader.getKeyInfo(kid);
                if (key.isPresent()) return key;
            }
        }

        return Optional.empty();
    }

    @Override
    public LoaderStatus getLoaderStatus() {
        return status.get(); // Pure atomic read
    }

    @Override
    public JwksType getJwksType() {
        return config.getJwksType(); // Delegate to config
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        return Optional.of(config.getIssuerIdentifier());
    }

    private void updateKeys(Jwks newJwks) {
        JWKSKeyLoader newLoader = JWKSKeyLoader.builder()
            .jwksContent(newJwks)
            .jwksType(config.getJwksType())
            .build();
        newLoader.initJWKSLoader(securityEventCounter);

        // Retire old keys with grace period
        JWKSKeyLoader oldLoader = currentKeys.getAndSet(newLoader);
        if (oldLoader != null) {
            retiredKeys.addFirst(new RetiredKeySet(oldLoader, Instant.now()));

            // Clean up expired retired keys
            Instant cutoff = Instant.now().minus(config.getKeyRotationGracePeriod());
            retiredKeys.removeIf(retired -> retired.retiredAt.isBefore(cutoff));

            // Keep max N retired sets
            while (retiredKeys.size() > config.getMaxRetiredKeySets()) {
                retiredKeys.removeLast();
            }
        }
    }

    private void startBackgroundRefresh() {
        refreshTask = config.getScheduledExecutorService().scheduleAtFixedRate(() -> {
            try {
                ResilientHttpHandler<Jwks> handler = httpHandler.get();
                if (handler == null) {
                    LOGGER.warn(WARN.BACKGROUND_REFRESH_NO_HANDLER.format());
                    return;
                }

                HttpResultObject<Jwks> result = handler.load();

                if (result.isValid() && result.getHttpStatus().map(s -> s == 200).orElse(false)) {
                    updateKeys(result.getResult());
                    LOGGER.debug("Background refresh updated keys");
                } else if (result.getHttpStatus().map(s -> s == 304).orElse(false)) {
                    LOGGER.debug("Background refresh: keys unchanged (304)");
                } else {
                    LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED.format(result.getState()));
                }
            } catch (IOException e) {
                LOGGER.warn(WARN.BACKGROUND_REFRESH_IO_ERROR.format(e.getMessage(), config.getIssuerIdentifier()));
            } catch (JwksParseException e) {
                LOGGER.warn(WARN.BACKGROUND_REFRESH_PARSE_ERROR.format(e.getMessage(), config.getIssuerIdentifier()));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                LOGGER.warn(WARN.BACKGROUND_REFRESH_KEY_ERROR.format(e.getMessage(), config.getIssuerIdentifier()));
            }
        },
        config.getRefreshIntervalSeconds(),
        config.getRefreshIntervalSeconds(),
        TimeUnit.SECONDS);

        LOGGER.info(INFO.BACKGROUND_REFRESH_STARTED.format(config.getIssuerIdentifier()));
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        currentKeys.set(null);
        retiredKeys.clear();
        httpHandler.set(null);
        status.set(LoaderStatus.UNDEFINED);
    }

    private static class RetiredKeySet {
        final JWKSKeyLoader loader;
        final Instant retiredAt;

        RetiredKeySet(JWKSKeyLoader loader, Instant retiredAt) {
            this.loader = loader;
            this.retiredAt = retiredAt;
        }
    }
}
```

## HttpJwksLoaderConfig - Enhanced with Overloaded Method

```java
public class HttpJwksLoaderConfig {
    String getIssuerIdentifier();

    // Existing method - returns handler for direct mode or well-known endpoint
    HttpHandler getHttpHandler();

    // NEW OVERLOADED METHOD - creates handler for any URL with configured timeouts
    HttpHandler getHttpHandler(@NonNull String url) {
        // Creates a new HttpHandler for the given URL using configured timeouts
        // This avoids duplicating HttpHandler.builder() logic everywhere
        return HttpHandler.builder()
            .url(url)
            .connectionTimeoutSeconds(getConnectionTimeout())
            .readTimeoutSeconds(getReadTimeout())
            .build();
    }

    // Background refresh decision - encapsulates the logic
    boolean isBackgroundRefreshEnabled() {
        return getScheduledExecutorService() != null &&
               getRefreshIntervalSeconds() > 0;
    }

    WellKnownConfig getWellKnownConfig();  // For well-known discovery
    JwksType getJwksType();                // Returns WELL_KNOWN or HTTP

    int getConnectionTimeout();
    int getReadTimeout();
    int getRefreshIntervalSeconds();
    Duration getKeyRotationGracePeriod();
    int getMaxRetiredKeySets();
    ScheduledExecutorService getScheduledExecutorService();
}
```

### Why the Overloaded Method?

1. **Line 140 issue**: `wellKnownConfig.getHttpHandler()` returns handler for `.well-known/openid-configuration` endpoint, NOT the JWKS endpoint
2. **Avoid duplication**: Creating HttpHandler with timeouts is duplicated in multiple places
3. **Consistent configuration**: All HTTP handlers use the same timeout settings

## Key Design Decisions

1. **Simple constructor** - No I/O, no blocking operations
2. **resolveJWKSHandler()** - Clean separation of well-known vs direct logic
3. **Async resolution** - Well-known discovery happens in async init
4. **Overloaded getHttpHandler(url)** - Centralizes HttpHandler creation with consistent timeouts
5. **Single responsibility** - Each method does one thing

## What's Different from Previous Attempt

### Wrong
- Well-known resolution in constructor (can block!)
- Duplicating config methods
- Complex initialization

### Right
- Well-known resolution in async init
- Delegate to existing config methods
- Simple constructor, complex work in init

This is the complete, clean HttpJwksLoader that properly handles async initialization without blocking the constructor.