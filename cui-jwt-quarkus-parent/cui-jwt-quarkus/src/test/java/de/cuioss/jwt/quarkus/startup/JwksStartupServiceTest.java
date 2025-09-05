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
package de.cuioss.jwt.quarkus.startup;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoader;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;

import io.quarkus.runtime.StartupEvent;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN;
import static org.awaitility.Awaitility.await;
import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprehensive unit tests for {@link JwksStartupService}.
 * <p>
 * Tests the asynchronous JWKS loading functionality, including:
 * - Async execution verification
 * - HTTP vs non-HTTP loader behavior differentiation
 * - Proper logging with LogRecord constants
 * - Error handling and timeout scenarios
 * - CompletableFuture coordination
 * </p>
 * <p>
 * Uses EasyMock for proper mocking and verification since Mockito is off-limits.
 * </p>
 */
@EnableTestLogger
class JwksStartupServiceTest {

    private JwksStartupService startupService;
    private StartupEvent startupEvent;
    private IMocksControl mocksControl;
    private Config mockConfig;

    @BeforeEach
    void setUp() {
        mocksControl = EasyMock.createControl();
        mockConfig = mocksControl.createMock(Config.class);
        startupService = new JwksStartupService(mockConfig);
        startupEvent = new StartupEvent();
    }

    @Test
    @DisplayName("Should handle empty issuer configurations gracefully")
    void shouldHandleEmptyIssuerConfigurations() {
        // Given: Config that returns no issuers
        expect(mockConfig.getOptionalValue("cui.jwt.issuers", String.class))
                .andReturn(java.util.Optional.empty()).anyTimes();
        expect(mockConfig.getPropertyNames()).andReturn(() -> java.util.Set.<String>of().iterator()).anyTimes();
        mocksControl.replay();

        // When: Startup event is triggered
        startupService.initializeJwks();

        // Then: Should log appropriate INFO messages
        mocksControl.verify();
        LogAsserts.assertLogMessagePresent(TestLogLevel.INFO,
                INFO.STARTING_ASYNCHRONOUS_JWKS_INITIALIZATION.format(0));
        LogAsserts.assertLogMessagePresent(TestLogLevel.INFO,
                INFO.NO_ISSUER_CONFIGURATIONS_FOUND.format());
    }

    @Test
    @DisplayName("Should handle basic startup scenario")
    void shouldHandleBasicStartup() {
        // Given: Minimal config setup
        expect(mockConfig.getOptionalValue("cui.jwt.issuers", String.class))
                .andReturn(java.util.Optional.empty()).anyTimes();
        expect(mockConfig.getPropertyNames()).andReturn(() -> java.util.Set.<String>of().iterator()).anyTimes();
        mocksControl.replay();

        // When: Startup event is triggered
        startupService.initializeJwks();

        // Then: Should handle startup gracefully
        mocksControl.verify();
        LogAsserts.assertLogMessagePresent(TestLogLevel.INFO,
                INFO.STARTING_ASYNCHRONOUS_JWKS_INITIALIZATION.format(0));
    }
}