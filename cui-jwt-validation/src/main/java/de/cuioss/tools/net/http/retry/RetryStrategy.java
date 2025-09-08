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

import java.io.IOException;

/**
 * HTTP-specific retry strategy interface.
 * Designed specifically for retrying HTTP operations that throw IOException and InterruptedException.
 */
@FunctionalInterface
public interface RetryStrategy {

    /**
     * Executes the given HTTP operation with retry logic.
     *
     * @param <T> the type of result returned by the operation
     * @param operation the HTTP operation to retry
     * @param context retry context with operation name and attempt info
     * @return result of successful operation
     * @throws RetryException if all retry attempts fail or retry logic fails
     * @throws InterruptedException if the operation is interrupted
     */
    <T> T execute(HttpOperation<T> operation, RetryContext context) throws RetryException, InterruptedException;

    /**
     * Creates a no-op retry strategy (single attempt only).
     * Useful for disabling retry in specific scenarios or configurations.
     * 
     * @return a retry strategy that executes the operation exactly once
     */
    static RetryStrategy none() {
        return new RetryStrategy() {
            @Override
            public <T> T execute(HttpOperation<T> operation, RetryContext context) throws RetryException, InterruptedException {
                try {
                    return operation.execute();
                } catch (IOException e) {
                    throw new RetryException("Single attempt failed for " + context.operationName(), e);
                }
            }
        };
    }

    /**
     * Creates exponential backoff retry strategy with sensible defaults.
     * This is the recommended strategy for most HTTP operations requiring retry.
     * 
     * Default configuration:
     * - Maximum attempts: 5
     * - Initial delay: 1 second
     * - Backoff multiplier: 2.0
     * - Maximum delay: 1 minute  
     * - Jitter factor: 0.1 (±10% randomization)
     * 
     * @return a retry strategy with exponential backoff and jitter
     */
    static RetryStrategy exponentialBackoff() {
        return ExponentialBackoffRetryStrategy.builder().build();
    }
}