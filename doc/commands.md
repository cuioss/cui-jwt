# Command Configuration

## ./mvnw -Ppre-commit clean install

### Last Execution Duration
- **Duration**: 570000ms (9 minutes 30 seconds)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- `[INFO] /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/target/generated-sources/annotations/de/cuioss/sheriff/oauth/core/json/_JwkKey_DslJsonConverter.java: Einige Eingabedateien verwenden nicht geprüfte oder unsichere Vorgänge.` (unchecked operations warning in generated DSL-JSON code)
- `[INFO] /Users/oliver/git/OAuth-Sheriff/oauth-sheriff-core/target/generated-sources/annotations/de/cuioss/sheriff/oauth/core/json/_JwkKey_DslJsonConverter.java: Wiederholen Sie die Kompilierung mit -Xlint:unchecked, um Details zu erhalten.` (follow-up message for unchecked operations)

## ./mvnw clean verify -Pintegration-tests -pl oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests

### Last Execution Duration
- **Duration**: 180000ms (3 minutes)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- `WARN [io.mic.cor.ins.MeterRegistry] (vert.x-acceptor-thread-0) This Gauge has been already registered (MeterId{name='http.server.active.connections', tags=[]})` (Quarkus Micrometer warning about duplicate gauge registration)
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called` (Netty library using deprecated Unsafe methods)
- `WARNING: sun.misc.Unsafe::arrayBaseOffset` (Follow-up details for Unsafe deprecation)
- `WARNING: A restricted method in java.lang.System has been called` (Brotli4j native library access)
- `WARNING: java.lang.System::loadLibrary` (Follow-up details for restricted method)
- `WARNING [de.cui.she.oau.cor.IssuerConfig] JWTValidation-134: IssuerConfig for issuer ... has claimSubOptional=true` (Intentional test configuration for non-standard token validation)
- `WARNING [de.cui.she.oau.cor.pip.val.TokenClaimValidator] JWTValidation-112: Missing recommended element: expectedAudience` (Test configuration without audience validation)
- `WARN [org.keycloak.storage.datastore.DefaultExportImportManager] Referenced client scope ... doesn't exist` (Keycloak realm import warnings - expected during initial setup)
- `WARN [org.keycloak.models.utils.RepresentationToModel] Referenced client scope ... doesn't exist. Ignoring` (Keycloak realm import follow-up warnings)

## ./mvnw clean verify -pl benchmarking/benchmark-core -Pbenchmark

### Last Execution Duration
- **Duration**: 290000ms (4 minutes 50 seconds)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- (No acceptable warnings documented yet)

## ./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk

### Last Execution Duration
- **Duration**: 270000ms (4 minutes 30 seconds)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- `WARN [io.mic.cor.ins.MeterRegistry] (vert.x-acceptor-thread-0) This Gauge has been already registered (MeterId{name='http.server.active.connections', tags=[]})` (Quarkus Micrometer warning about duplicate gauge registration)
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called` (Netty library using deprecated Unsafe methods)
- `WARNING: sun.misc.Unsafe::arrayBaseOffset` (Follow-up details for Unsafe deprecation)
- `WARNING: A restricted method in java.lang.System has been called` (Brotli4j native library access)
- `WARNING: java.lang.System::loadLibrary` (Follow-up details for restricted method)
- `WARNING [de.cui.she.oau.cor.IssuerConfig] JWTValidation-134: IssuerConfig for issuer ... has claimSubOptional=true` (Intentional test configuration for non-standard token validation)
- `WARN [org.keycloak.storage.datastore.DefaultExportImportManager] Referenced client scope ... doesn't exist` (Keycloak realm import warnings - expected during initial setup)
- `WARN [org.keycloak.models.utils.RepresentationToModel] Referenced client scope ... doesn't exist. Ignoring` (Keycloak realm import follow-up warnings)
