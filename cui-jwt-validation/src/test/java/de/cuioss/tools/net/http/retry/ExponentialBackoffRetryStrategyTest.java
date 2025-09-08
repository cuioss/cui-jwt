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
package de.cuioss.tools.net.http.retry;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
class ExponentialBackoffRetryStrategyTest {

    private ExponentialBackoffRetryStrategy strategy;
    private RetryContext context;

    @BeforeEach
    void setUp() {
        strategy = ExponentialBackoffRetryStrategy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofMillis(10))
                .maxDelay(Duration.ofMillis(100))
                .backoffMultiplier(2.0)
                .jitterFactor(0.0) // Disable jitter for predictable testing
                .build();

        context = RetryContext.initial("test-operation");
    }

    @Nested
    @DisplayName("Successful operations")
    class SuccessfulOperations {

        @Test
        @DisplayName("Should return result on first attempt success")
        void shouldReturnResultOnFirstAttemptSuccess() throws Exception {
            HttpOperation<String> operation = () -> "success";

            String result = strategy.execute(operation, context);

            assertEquals("success", result, "Strategy should return operation result on first attempt success");
        }

        @Test
        @DisplayName("Should return result after retries succeed")
        void shouldReturnResultAfterRetriesSucceed() throws Exception {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw new ConnectException("Connection failed on attempt " + attempt);
                }
                return "success-on-attempt-" + attempt;
            };

            String result = strategy.execute(operation, context);

            assertEquals("success-on-attempt-3", result, "Strategy should return result from successful retry attempt");
            assertEquals(3, attempts.get(), "Strategy should have made exactly 3 attempts before success");
        }
    }

    @Nested
    @DisplayName("Retryable exceptions")
    class RetryableExceptions {

        @Test
        @DisplayName("Should retry ConnectException")
        void shouldRetryConnectException() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                throw new ConnectException("Connection refused");
            };

            RetryException exception = assertThrows(RetryException.class,
                    () -> strategy.execute(operation, context));

            assertEquals("All 3 retry attempts failed for test-operation", exception.getMessage(), "RetryException should contain descriptive failure message");
            assertTrue(exception.getCause() instanceof ConnectException, "Exception cause should be original ConnectException");
            assertEquals(3, attempts.get(), "Strategy should have exhausted all 3 retry attempts");
        }

        @Test
        @DisplayName("Should retry SocketTimeoutException")
        void shouldRetrySocketTimeoutException() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                throw new SocketTimeoutException("Read timeout");
            };

            RetryException exception = assertThrows(RetryException.class,
                    () -> strategy.execute(operation, context));

            assertTrue(exception.getCause() instanceof SocketTimeoutException, "Exception cause should be original SocketTimeoutException");
            assertEquals(3, attempts.get(), "Strategy should have exhausted all 3 retry attempts for timeout exception");
        }

        @Test
        @DisplayName("Should retry HttpConnectTimeoutException")
        void shouldRetryHttpConnectTimeoutException() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                throw new HttpConnectTimeoutException("HTTP timeout");
            };

            RetryException exception = assertThrows(RetryException.class,
                    () -> strategy.execute(operation, context));

            assertTrue(exception.getCause() instanceof HttpConnectTimeoutException, "Exception cause should be original HttpConnectTimeoutException");
            assertEquals(3, attempts.get(), "Strategy should have exhausted all 3 retry attempts for HTTP timeout");
        }

        @Test
        @DisplayName("Should retry general IOException")
        void shouldRetryGeneralIOException() {
            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                throw new IOException("General IO error");
            };

            RetryException exception = assertThrows(RetryException.class,
                    () -> strategy.execute(operation, context));

            assertTrue(exception.getCause() instanceof IOException, "Exception cause should be original IOException");
            assertEquals(3, attempts.get(), "Strategy should have exhausted all 3 retry attempts for IO exception");
        }
    }

    @Nested
    @DisplayName("Delay calculation")
    class DelayCalculation {

        @Test
        @DisplayName("Should respect maximum delay")
        void shouldRespectMaximumDelay() {
            ExponentialBackoffRetryStrategy longDelayStrategy = ExponentialBackoffRetryStrategy.builder()
                    .maxAttempts(5)
                    .initialDelay(Duration.ofMillis(1000))
                    .maxDelay(Duration.ofMillis(2000)) // Cap at 2 seconds
                    .backoffMultiplier(10.0) // Very aggressive multiplier
                    .jitterFactor(0.0)
                    .build();

            AtomicInteger attempts = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();

            HttpOperation<String> operation = () -> {
                attempts.incrementAndGet();
                throw new ConnectException("Test exception");
            };

            assertThrows(RetryException.class,
                    () -> longDelayStrategy.execute(operation, context));

            long totalTime = System.currentTimeMillis() - startTime;

            // With max delay of 2000ms and 4 retries (5 attempts total), 
            // total time should be less than 4 * 2000 = 8000ms
            assertTrue(totalTime < 8000, "Total retry time should respect max delay constraint (< 8000ms), was: " + totalTime);
            assertEquals(5, attempts.get(), "Strategy should have made exactly 5 attempts as configured");
        }
    }

    @Nested
    @DisplayName("Configuration validation")
    class ConfigurationValidation {

        @Test
        @DisplayName("Should validate positive max attempts")
        void shouldValidatePositiveMaxAttempts() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> ExponentialBackoffRetryStrategy.builder().maxAttempts(0));

            assertEquals("maxAttempts must be positive, got: 0", exception.getMessage(), "Builder should validate maxAttempts parameter positivity");
        }

        @Test
        @DisplayName("Should validate non-negative initial delay")
        void shouldValidateNonNegativeInitialDelay() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> ExponentialBackoffRetryStrategy.builder().initialDelay(Duration.ofMillis(-1)));

            assertEquals("initialDelay cannot be negative", exception.getMessage(), "Builder should validate initialDelay parameter non-negativity");
        }

        @Test
        @DisplayName("Should validate backoff multiplier >= 1.0")
        void shouldValidateBackoffMultiplier() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> ExponentialBackoffRetryStrategy.builder().backoffMultiplier(0.5));

            assertEquals("backoffMultiplier must be >= 1.0, got: 0.5", exception.getMessage(), "Builder should validate backoffMultiplier is >= 1.0");
        }

        @Test
        @DisplayName("Should validate jitter factor range")
        void shouldValidateJitterFactorRange() {
            IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class,
                    () -> ExponentialBackoffRetryStrategy.builder().jitterFactor(-0.1));

            assertEquals("jitterFactor must be between 0.0 and 1.0, got: -0.1", exception1.getMessage(), "Builder should validate jitterFactor lower bound");

            IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class,
                    () -> ExponentialBackoffRetryStrategy.builder().jitterFactor(1.1));

            assertEquals("jitterFactor must be between 0.0 and 1.0, got: 1.1", exception2.getMessage(), "Builder should validate jitterFactor upper bound");
        }

        @Test
        @DisplayName("Should validate required parameters are not null")
        void shouldValidateRequiredParametersNotNull() {
            assertThrows(NullPointerException.class,
                    () -> ExponentialBackoffRetryStrategy.builder().initialDelay(null));

            assertThrows(NullPointerException.class,
                    () -> ExponentialBackoffRetryStrategy.builder().maxDelay(null));
        }
    }


    @Nested
    @DisplayName("Thread interruption")
    class ThreadInterruption {

        @Test
        @DisplayName("Should handle thread interruption during retry delay")
        void shouldHandleThreadInterruptionDuringRetryDelay() {
            ExponentialBackoffRetryStrategy interruptableStrategy = ExponentialBackoffRetryStrategy.builder()
                    .maxAttempts(3)
                    .initialDelay(Duration.ofSeconds(10)) // Long delay to allow interruption
                    .build();

            AtomicInteger attempts = new AtomicInteger(0);
            HttpOperation<String> operation = () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    // Interrupt the thread after the first failure
                    Thread.currentThread().interrupt();
                }
                throw new ConnectException("Test exception");
            };

            RetryException exception = assertThrows(RetryException.class,
                    () -> interruptableStrategy.execute(operation, context));

            assertEquals("Retry interrupted for test-operation", exception.getMessage(), "RetryException should indicate operation was interrupted");
            assertTrue(exception.getCause() instanceof InterruptedException, "Exception cause should be InterruptedException");

            // Should preserve interrupt status
            assertTrue(Thread.interrupted(), "Thread interrupt status should be preserved after retry interruption");
        }
    }

    @Nested
    @DisplayName("Static factory methods")
    class StaticFactoryMethods {

        @Test
        @DisplayName("Should create retry strategy with exponentialBackoff()")
        void shouldCreateRetryStrategyWithExponentialBackoff() {
            RetryStrategy strategy = RetryStrategy.exponentialBackoff();

            assertTrue(strategy instanceof ExponentialBackoffRetryStrategy, "exponentialBackoff() factory should create ExponentialBackoffRetryStrategy");
        }

        @Test
        @DisplayName("Should create no-op strategy with none()")
        void shouldCreateNoOpStrategyWithNone() {
            RetryStrategy noRetryStrategy = RetryStrategy.none();
            AtomicInteger attempts = new AtomicInteger(0);

            HttpOperation<String> operation = () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    throw new ConnectException("Should not retry");
                }
                return "should-not-reach";
            };

            RetryException exception = assertThrows(RetryException.class,
                    () -> noRetryStrategy.execute(operation, context));

            assertEquals("Single attempt failed for test-operation", exception.getMessage(), "RetryException should contain descriptive failure message");
            assertTrue(exception.getCause() instanceof ConnectException, "Exception cause should be original ConnectException");
            assertEquals("Should not retry", exception.getCause().getMessage(), "Original exception message should be preserved");
            assertEquals(1, attempts.get(), "No-op strategy should make exactly 1 attempt without retrying");
        }
    }
}