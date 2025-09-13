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
package de.cuioss.tools.net.http;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.cuioss.tools.net.http.HttpLogMessages.WARN;

/**
 * Tests for proper logging of HttpLogMessages LogRecords.
 * Verifies that all WARN messages are properly formatted and logged.
 */
@EnableTestLogger
class HttpLogMessagesLoggingTest {

    private static final CuiLogger LOGGER = new CuiLogger(HttpLogMessagesLoggingTest.class);

    @BeforeEach
    void clearLogs() {
        // LogAsserts are automatically cleared between tests by @EnableTestLogger
    }

    @Test
    @DisplayName("WARN messages should be logged with correct format and identifier")
    void warnMessages() {
        // Test CONTENT_CONVERSION_FAILED
        LOGGER.warn(WARN.CONTENT_CONVERSION_FAILED.format("http://example.com"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "HTTP-100");
    }

    @Test
    @DisplayName("Verify message format contains expected content")
    void messageContent() {
        String testUrl = "http://test.example.com/api";
        LOGGER.warn(WARN.CONTENT_CONVERSION_FAILED.format(testUrl));

        // Verify the message contains both the identifier and the URL
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "HTTP-100");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, testUrl);
    }
}