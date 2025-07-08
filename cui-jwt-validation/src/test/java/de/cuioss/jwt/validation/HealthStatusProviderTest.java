/**
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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for {@link HealthStatusProvider} interface.
 * <p>
 * Tests the interface contract using concrete implementations.
 * 
 * @author Oliver Wolff
 */
@EnableGeneratorController
class HealthStatusProviderTest {

    @Test
    @DisplayName("Should return non-null status from healthy implementation")
    void shouldReturnNonNullStatusFromHealthyImplementation() {
        HealthStatusProvider provider = new TestHealthyProvider();

        LoaderStatus status = provider.isHealthy();
        assertNotNull(status);
        assertEquals(LoaderStatus.OK, status);
    }

    @Test
    @DisplayName("Should return non-null status from error implementation")
    void shouldReturnNonNullStatusFromErrorImplementation() {
        HealthStatusProvider provider = new TestErrorProvider();

        LoaderStatus status = provider.isHealthy();
        assertNotNull(status);
        assertEquals(LoaderStatus.ERROR, status);
    }

    @Test
    @DisplayName("Should return non-null status from undefined implementation")
    void shouldReturnNonNullStatusFromUndefinedImplementation() {
        HealthStatusProvider provider = new TestUndefinedProvider();

        LoaderStatus status = provider.isHealthy();
        assertNotNull(status);
        assertEquals(LoaderStatus.UNDEFINED, status);
    }

    @Test
    @DisplayName("Should be consistent across multiple calls")
    void shouldBeConsistentAcrossMultipleCalls() {
        HealthStatusProvider provider = new TestHealthyProvider();

        LoaderStatus firstCall = provider.isHealthy();
        LoaderStatus secondCall = provider.isHealthy();

        assertEquals(firstCall, secondCall);
    }

    @Test
    @DisplayName("Should handle thread safety")
    void shouldHandleThreadSafety() throws InterruptedException {
        HealthStatusProvider provider = new TestHealthyProvider();

        Thread[] threads = new Thread[10];
        LoaderStatus[] results = new LoaderStatus[10];

        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> results[index] = provider.isHealthy());
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All results should be the same and non-null
        for (LoaderStatus result : results) {
            assertNotNull(result);
            assertEquals(LoaderStatus.OK, result);
        }
    }

    @Test
    @DisplayName("Should handle state transitions")
    void shouldHandleStateTransitions() {
        TestTransitionProvider provider = new TestTransitionProvider();

        // Initially undefined
        assertEquals(LoaderStatus.UNDEFINED, provider.isHealthy());

        // Transition to healthy
        provider.transitionToHealthy();
        assertEquals(LoaderStatus.OK, provider.isHealthy());

        // Transition to error
        provider.transitionToError();
        assertEquals(LoaderStatus.ERROR, provider.isHealthy());

        // Transition back to healthy
        provider.transitionToHealthy();
        assertEquals(LoaderStatus.OK, provider.isHealthy());
    }

    // Test implementations for interface testing

    private static class TestHealthyProvider implements HealthStatusProvider {
        @Override
        public LoaderStatus isHealthy() {
            return LoaderStatus.OK;
        }
    }

    private static class TestErrorProvider implements HealthStatusProvider {
        @Override
        public LoaderStatus isHealthy() {
            return LoaderStatus.ERROR;
        }
    }

    private static class TestUndefinedProvider implements HealthStatusProvider {
        @Override
        public LoaderStatus isHealthy() {
            return LoaderStatus.UNDEFINED;
        }
    }

    private static class TestTransitionProvider implements HealthStatusProvider {
        private LoaderStatus currentStatus = LoaderStatus.UNDEFINED;

        @Override
        public LoaderStatus isHealthy() {
            return currentStatus;
        }

        public void transitionToHealthy() {
            this.currentStatus = LoaderStatus.OK;
        }

        public void transitionToError() {
            this.currentStatus = LoaderStatus.ERROR;
        }
    }
}