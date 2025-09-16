# Clean Architecture Redesign (Option B) - Updated

## Overview

This option represents a complete architectural redesign that addresses fundamental issues through clean separation of concerns and centralized management. Since this is pre-1.0, breaking changes are acceptable for achieving optimal architecture.

## Architectural Principles

1. **Centralized State Management**: Single orchestrator owns all loader state (not "stateless")
2. **Leverage Existing Components**: Reuse ResilientHttpHandler for all HTTP resilience
3. **Clear Boundaries**: Each component has single, well-defined responsibility
4. **No Redundant Abstractions**: Use HttpHandler directly, no unnecessary wrappers
5. **Framework Independence**: All core logic in validation module
6. **Lock-Free Operations**: Status checks are instant atomic reads

## Core Components

### JwksLoadingOrchestrator

```java
// Central state management for all JWKS loading operations
public class JwksLoadingOrchestrator implements LoadingStatusProvider {
    private final Map<String, ManagedIssuer> issuers = new ConcurrentHashMap<>();
    private final JwksRepository repository;
    private final RefreshEngine refreshEngine;
    private final KeyRotationManager rotationManager;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<LoaderStatus> overallStatus;
    private final JwksHttpContentConverter contentConverter;

    public JwksLoadingOrchestrator(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        this.repository = new JwksRepository();
        this.refreshEngine = new RefreshEngine(scheduler);
        this.rotationManager = new KeyRotationManager();
        this.overallStatus = new AtomicReference<>(LoaderStatus.UNDEFINED);
        this.contentConverter = new JwksHttpContentConverter();
    }

    // Register issuer using existing HttpJwksLoaderConfig
    public void registerIssuer(HttpJwksLoaderConfig config) {
        String issuer = config.getIssuerIdentifier();

        // Create ResilientHttpHandler based on config type
        ResilientHttpHandler<Jwks> resilientHandler;

        if (config.getWellKnownConfig() != null) {
            // Well-known discovery configuration
            HttpWellKnownResolver resolver = config.getWellKnownConfig().createResolver();
            // Get JWKS URI from well-known endpoint
            String jwksUri = resolver.getJwksUri()
                .orElseThrow(() -> new IllegalStateException("Failed to resolve JWKS URI"));

            HttpHandler httpHandler = HttpHandler.builder()
                .url(jwksUri)
                .connectionTimeoutSeconds(config.getConnectionTimeout())
                .readTimeoutSeconds(config.getReadTimeout())
                .build();

            resilientHandler = new ResilientHttpHandler<>(httpHandler, contentConverter);
        } else {
            // Direct HTTP configuration
            resilientHandler = new ResilientHttpHandler<>(
                config.getHttpHandler(),
                contentConverter
            );
        }

        ManagedIssuer managedIssuer = new ManagedIssuer(issuer, resilientHandler, config);
        issuers.put(issuer, managedIssuer);

        // Initial load
        loadJwks(issuer);

        // Schedule refresh if configured
        if (config.getScheduledExecutorService() != null &&
            config.getRefreshIntervalSeconds() > 0) {
            refreshEngine.scheduleRefresh(
                issuer,
                () -> loadJwks(issuer),
                Duration.ofSeconds(config.getRefreshIntervalSeconds())
            );
        }
    }

    public Optional<KeyInfo> getKeyInfo(String issuer, String kid) {
        // Try current keys
        Optional<KeyInfo> current = repository.findKey(issuer, kid);
        if (current.isPresent()) return current;

        // Try retired keys (grace period)
        return rotationManager.findInRetiredKeys(issuer, kid);
    }

    // Lock-free status check - instant response
    public LoaderStatus getLoaderStatus() {
        return overallStatus.get();
    }

    public Map<String, LoaderStatus> getAllStatuses() {
        return issuers.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().status.get()
            ));
    }

    private CompletableFuture<LoadResult> loadJwks(String issuer) {
        ManagedIssuer managed = issuers.get(issuer);
        managed.status.set(LoaderStatus.LOADING);

        return CompletableFuture.supplyAsync(() -> {
            // ResilientHttpHandler handles ALL retry logic internally
            // No need for separate RetryEngine!
            HttpResultObject<Jwks> result = managed.httpHandler.load();

            if (result.isValid() && result.getResult() != null) {
                // Store new keys
                Jwks oldJwks = repository.get(issuer);
                repository.store(issuer, result.getResult());

                // Handle rotation
                if (oldJwks != null) {
                    rotationManager.onKeyRotation(issuer, oldJwks, result.getResult());
                }

                managed.status.set(LoaderStatus.OK);
                updateOverallStatus();
                return LoadResult.success(result.getResult());
            } else {
                // Keep existing keys if available
                if (repository.get(issuer) != null) {
                    managed.status.set(LoaderStatus.OK);
                } else {
                    managed.status.set(LoaderStatus.ERROR);
                }
                updateOverallStatus();

                return LoadResult.error(result.getResultDetail());
            }
        }, scheduler);
    }

    private void updateOverallStatus() {
        boolean anyOk = issuers.values().stream()
            .anyMatch(managed -> managed.status.get() == LoaderStatus.OK);
        overallStatus.set(anyOk ? LoaderStatus.OK : LoaderStatus.ERROR);
    }

    public void shutdown() {
        refreshEngine.shutdown();
        scheduler.shutdown();
    }

    // State for each managed issuer
    private static class ManagedIssuer {
        final String issuer;
        final ResilientHttpHandler<Jwks> httpHandler;
        final HttpJwksLoaderConfig config;
        final AtomicReference<LoaderStatus> status;

        ManagedIssuer(String issuer, ResilientHttpHandler<Jwks> httpHandler,
                     HttpJwksLoaderConfig config) {
            this.issuer = issuer;
            this.httpHandler = httpHandler;
            this.config = config;
            this.status = new AtomicReference<>(LoaderStatus.UNDEFINED);
        }
    }
}
```


### JwksRepository

```java
// Thread-safe storage for JWKS data
public class JwksRepository {
    private final ConcurrentHashMap<String, VersionedJwks> storage = new ConcurrentHashMap<>();

    public void store(String issuer, Jwks jwks) {
        storage.put(issuer, new VersionedJwks(jwks, Instant.now()));
    }

    public Jwks get(String issuer) {
        VersionedJwks versioned = storage.get(issuer);
        return versioned != null ? versioned.jwks : null;
    }

    public Optional<KeyInfo> findKey(String issuer, String kid) {
        Jwks jwks = get(issuer);
        if (jwks == null) return Optional.empty();

        return jwks.getKeys().stream()
            .filter(key -> kid.equals(key.getKid()))
            .findFirst();
    }

    private static class VersionedJwks {
        final Jwks jwks;
        final Instant timestamp;

        VersionedJwks(Jwks jwks, Instant timestamp) {
            this.jwks = jwks;
            this.timestamp = timestamp;
        }
    }
}
```

### RefreshEngine

```java
// Manages all scheduled refresh tasks
public class RefreshEngine {
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public RefreshEngine(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public void scheduleRefresh(String issuer,
                               Supplier<CompletableFuture<LoadResult>> loader,
                               Duration interval) {
        // Cancel existing task if any
        cancelRefresh(issuer);

        // Schedule new task
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
            () -> loader.get().exceptionally(ex -> {
                // Log error but continue scheduling
                // ResilientHttpHandler already handled retry!
                return LoadResult.error(ex.getMessage());
            }),
            interval.toMillis(),
            interval.toMillis(),
            TimeUnit.MILLISECONDS
        );

        tasks.put(issuer, task);
    }

    public void cancelRefresh(String issuer) {
        ScheduledFuture<?> existing = tasks.remove(issuer);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    public void shutdown() {
        tasks.values().forEach(task -> task.cancel(false));
        tasks.clear();
    }
}
```

### KeyRotationManager

```java
// Manages retired keys with configurable grace period (Issue #110)
public class KeyRotationManager {
    private final Duration gracePeriod;
    private final int maxRetiredKeySets;
    private final ConcurrentHashMap<String, RetiredKeys> retiredByIssuer;

    public KeyRotationManager(Duration gracePeriod, int maxRetiredKeySets) {
        this.gracePeriod = gracePeriod;
        this.maxRetiredKeySets = maxRetiredKeySets;
        this.retiredByIssuer = new ConcurrentHashMap<>();
    }

    public void onKeyRotation(String issuer, Jwks oldJwks, Jwks newJwks) {
        retiredByIssuer.compute(issuer, (k, v) -> {
            if (v == null) v = new RetiredKeys();
            v.add(oldJwks, Instant.now());
            v.cleanupExpired(gracePeriod, maxRetiredKeySets);
            return v;
        });
    }

    public Optional<KeyInfo> findInRetiredKeys(String issuer, String kid) {
        RetiredKeys retired = retiredByIssuer.get(issuer);
        if (retired != null) {
            return retired.findKey(kid, gracePeriod);
        }
        return Optional.empty();
    }

    private static class RetiredKeys {
        private final LinkedList<TimestampedJwks> jwksList = new LinkedList<>();

        void add(Jwks jwks, Instant timestamp) {
            jwksList.addFirst(new TimestampedJwks(jwks, timestamp));
        }

        Optional<KeyInfo> findKey(String kid, Duration gracePeriod) {
            Instant cutoff = Instant.now().minus(gracePeriod);
            return jwksList.stream()
                .filter(tj -> tj.timestamp.isAfter(cutoff))
                .flatMap(tj -> tj.jwks.getKeys().stream())
                .filter(key -> kid.equals(key.getKid()))
                .findFirst();
        }

        void cleanupExpired(Duration gracePeriod, int maxCount) {
            Instant cutoff = Instant.now().minus(gracePeriod);
            jwksList.removeIf(tj -> tj.timestamp.isBefore(cutoff));
            while (jwksList.size() > maxCount) {
                jwksList.removeLast();
            }
        }
    }

    record TimestampedJwks(Jwks jwks, Instant timestamp) {}
}
```

## Framework Integration

### Quarkus Integration

```java
@ApplicationScoped
public class QuarkusJwksOrchestratorProducer {

    @Inject
    ManagedExecutor managedExecutor;

    @Inject
    Config config;

    @Produces
    @ApplicationScoped
    public JwksLoadingOrchestrator produceOrchestrator() {
        // Adapt ManagedExecutor to ScheduledExecutorService
        ScheduledExecutorService scheduler = new ManagedExecutorAdapter(managedExecutor);

        return JwksLoadingOrchestrator.builder()
            .executorService(scheduler)
            .defaultRefreshInterval(resolveRefreshInterval(config))
            .keyRotationGracePeriod(resolveGracePeriod(config))
            .build();
    }

    @Produces
    @ApplicationScoped
    public TokenValidator produceTokenValidator(
            JwksLoadingOrchestrator orchestrator,
            List<IssuerConfig> issuerConfigs) {

        // Register all issuers with orchestrator using HttpJwksLoaderConfig
        for (IssuerConfig config : issuerConfigs) {
            orchestrator.registerIssuer(config.getHttpJwksLoaderConfig());
        }

        // Create validator with orchestrator-backed loaders
        return TokenValidator.builder()
            .jwksProvider((issuer, kid) -> orchestrator.getKeyInfo(issuer, kid))
            .issuerConfigs(issuerConfigs)
            .build();
    }
}
```

### NiFi Integration

```java
public class MultiIssuerJWTTokenAuthenticator {

    private JwksLoadingOrchestrator orchestrator;

    @OnScheduled
    public void onScheduled(ProcessContext context) {
        // Create orchestrator with NiFi's executor
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

        orchestrator = JwksLoadingOrchestrator.builder()
            .executorService(executor)
            .defaultRefreshInterval(parseRefreshInterval(context))
            .keyRotationGracePeriod(parseGracePeriod(context))
            .build();

        // Parse and register issuers using HttpJwksLoaderConfig
        List<IssuerConfig> issuerConfigs = parseIssuerConfigs(context);
        for (IssuerConfig config : issuerConfigs) {
            orchestrator.registerIssuer(config.getHttpJwksLoaderConfig());
        }

        // Create validator
        TokenValidator validator = TokenValidator.builder()
            .jwksProvider((issuer, kid) -> orchestrator.getKeyInfo(issuer, kid))
            .issuerConfigs(issuerConfigs)
            .build();
    }

    @OnStopped
    public void onStopped() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }
}
```

## Key Benefits of This Design

1. **No Redundancy**: ResilientHttpHandler handles all HTTP resilience - no separate RetryEngine
2. **Honest Architecture**: Acknowledges that HTTP operations are stateful, centralizes state management
3. **Direct Usage**: Uses HttpHandler directly without unnecessary abstractions
4. **Proven Components**: Leverages battle-tested ResilientHttpHandler
5. **Clear Responsibilities**: Each component has a single, well-defined purpose
6. **Lock-Free Health Checks**: Status checks are simple atomic reads
7. **Framework Independence**: Core logic has no framework dependencies

## Summary

This clean architecture design:
- Centralizes all state management in the orchestrator
- Leverages existing HttpJwksLoaderConfig for configuration
- Uses proven ResilientHttpHandler for all HTTP resilience
- Provides lock-free health checks via atomic operations
- Supports key rotation with configurable grace periods
- Maintains framework independence with thin integration layers

The result is a simpler, cleaner architecture that achieves all goals without unnecessary complexity.

## Idea Graveyard

### Why No Separate RetryEngine?

We don't need a separate RetryEngine because ResilientHttpHandler already provides comprehensive retry functionality with:
- Exponential backoff
- Configurable retry strategies
- Smart retry decisions (no retry on 4xx, retry on 5xx/network errors)
- Integration with caching layer

Creating a separate retry layer would duplicate functionality and create confusion about which component handles retries.

### Why No "Stateless" Loaders?

We eliminated individual loader classes entirely because:
- HTTP operations with caching and retry are inherently stateful
- "Stateless" loaders that use stateful HTTP handlers are a contradiction
- Centralizing state management in the orchestrator is cleaner
- Pre-1.0 status means no backward compatibility constraints

### Why No JwksSource Abstraction?

We use HttpHandler and HttpJwksLoaderConfig directly because:
- HttpHandler is already a good abstraction for HTTP operations
- HttpJwksLoaderConfig already encapsulates configuration needs
- Additional abstractions add complexity without value
- Direct usage is clearer and simpler