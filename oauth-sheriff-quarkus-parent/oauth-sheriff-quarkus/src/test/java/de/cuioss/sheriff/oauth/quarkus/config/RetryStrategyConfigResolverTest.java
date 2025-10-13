/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.quarkus.config;

import de.cuioss.http.client.retry.ExponentialBackoffRetryStrategy;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.jwt.quarkus.test.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetryStrategyConfigResolver Tests")
class RetryStrategyConfigResolverTest {

    @Test
    @DisplayName("should create resolver with config")
    void shouldCreateResolverWithConfig() {
        TestConfig config = new TestConfig(Map.of());

        RetryStrategyConfigResolver resolver = new RetryStrategyConfigResolver(config);

        assertNotNull(resolver);
    }

    @Test
    @DisplayName("should resolve retry strategy with default values")
    void shouldResolveWithDefaults() {
        TestConfig config = new TestConfig(Map.of());
        RetryStrategyConfigResolver resolver = new RetryStrategyConfigResolver(config);

        RetryStrategy result = resolver.resolveRetryStrategy();

        assertNotNull(result);
        assertInstanceOf(ExponentialBackoffRetryStrategy.class, result);
    }

    @Test
    @DisplayName("should resolve retry strategy with custom values")
    void shouldResolveWithCustomValues() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.RETRY.MAX_ATTEMPTS, "3",
                JwtPropertyKeys.RETRY.INITIAL_DELAY_MS, "500",
                JwtPropertyKeys.RETRY.MAX_DELAY_MS, "10000",
                JwtPropertyKeys.RETRY.BACKOFF_MULTIPLIER, "1.5",
                JwtPropertyKeys.RETRY.JITTER_FACTOR, "0.2"
        ));
        RetryStrategyConfigResolver resolver = new RetryStrategyConfigResolver(config);

        RetryStrategy result = resolver.resolveRetryStrategy();

        assertNotNull(result);
        assertInstanceOf(ExponentialBackoffRetryStrategy.class, result);
    }

    @Test
    @DisplayName("should return no-op strategy when retry is disabled")
    void shouldReturnNoOpWhenDisabled() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.RETRY.ENABLED, "false"
        ));
        RetryStrategyConfigResolver resolver = new RetryStrategyConfigResolver(config);

        RetryStrategy result = resolver.resolveRetryStrategy();

        assertNotNull(result);
        assertFalse(result instanceof ExponentialBackoffRetryStrategy, "Should return no-op strategy, not ExponentialBackoffRetryStrategy");
    }

    @Test
    @DisplayName("should enable retry by default when enabled flag is not set")
    void shouldEnableRetryByDefault() {
        TestConfig config = new TestConfig(Map.of());
        RetryStrategyConfigResolver resolver = new RetryStrategyConfigResolver(config);

        RetryStrategy result = resolver.resolveRetryStrategy();

        assertNotNull(result);
        assertInstanceOf(ExponentialBackoffRetryStrategy.class, result);
        assertNotEquals(RetryStrategy.none(), result);
    }

    @Test
    @DisplayName("should enable retry when explicitly set to true")
    void shouldEnableRetryWhenExplicitlyTrue() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.RETRY.ENABLED, "true",
                JwtPropertyKeys.RETRY.MAX_ATTEMPTS, "2"
        ));
        RetryStrategyConfigResolver resolver = new RetryStrategyConfigResolver(config);

        RetryStrategy result = resolver.resolveRetryStrategy();

        assertNotNull(result);
        assertInstanceOf(ExponentialBackoffRetryStrategy.class, result);
    }

    @Test
    @DisplayName("should handle partial configuration")
    void shouldHandlePartialConfiguration() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.RETRY.MAX_ATTEMPTS, "10",
                JwtPropertyKeys.RETRY.INITIAL_DELAY_MS, "2000"
        ));
        RetryStrategyConfigResolver resolver = new RetryStrategyConfigResolver(config);

        RetryStrategy result = resolver.resolveRetryStrategy();

        assertNotNull(result);
        assertInstanceOf(ExponentialBackoffRetryStrategy.class, result);
    }

}
