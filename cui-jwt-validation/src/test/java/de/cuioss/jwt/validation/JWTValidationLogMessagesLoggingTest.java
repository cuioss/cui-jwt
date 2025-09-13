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
package de.cuioss.jwt.validation;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.cuioss.jwt.validation.JWTValidationLogMessages.*;

/**
 * Tests for proper logging of JWTValidationLogMessages LogRecords.
 * Verifies that all INFO/WARN/ERROR messages are properly formatted and logged.
 */
@EnableTestLogger
class JWTValidationLogMessagesLoggingTest {

    private static final CuiLogger LOGGER = new CuiLogger(JWTValidationLogMessagesLoggingTest.class);

    @BeforeEach
    void clearLogs() {
        // LogAsserts are automatically cleared between tests by @EnableTestLogger
    }

    @Test
    @DisplayName("INFO messages should be logged with correct format and identifier")
    void infoMessages() {
        // Test TOKEN_FACTORY_INITIALIZED
        LOGGER.info(INFO.TOKEN_FACTORY_INITIALIZED.format("test-config"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "JWTValidation-1");

        // Test JWKS_KEYS_UPDATED
        LOGGER.info(INFO.JWKS_KEYS_UPDATED.format("LOADED"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "JWTValidation-2");

        // Test JWKS_HTTP_LOADED
        LOGGER.info(INFO.JWKS_HTTP_LOADED.format());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "JWTValidation-3");

        // Test RETRY_OPERATION_SUCCEEDED_AFTER_ATTEMPTS
        LOGGER.info(INFO.RETRY_OPERATION_SUCCEEDED_AFTER_ATTEMPTS.format("testOp", 2, 3));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "JWTValidation-10");

        // Test RETRY_OPERATION_COMPLETED
        LOGGER.info(INFO.RETRY_OPERATION_COMPLETED.format("testOp", 3, 1500));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "JWTValidation-11");
    }

    @Test
    @DisplayName("WARN messages should be logged with correct format and identifier")
    void warnMessages() {
        // Test TOKEN_SIZE_EXCEEDED
        LOGGER.warn(WARN.TOKEN_SIZE_EXCEEDED.format(8192));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "JWTValidation-100");

        // Test TOKEN_IS_EMPTY
        LOGGER.warn(WARN.TOKEN_IS_EMPTY.format());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "JWTValidation-101");

        // Test KEY_NOT_FOUND
        LOGGER.warn(WARN.KEY_NOT_FOUND.format("test-key-id"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "JWTValidation-102");

        // Test ISSUER_MISMATCH
        LOGGER.warn(WARN.ISSUER_MISMATCH.format("actual-issuer", "expected-issuer"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "JWTValidation-103");

        // Test CLAIM_SUB_OPTIONAL_WARNING
        LOGGER.warn(WARN.CLAIM_SUB_OPTIONAL_WARNING.format("test-issuer"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "JWTValidation-151");

        // Test RETRY_OPERATION_FAILED
        LOGGER.warn(WARN.RETRY_OPERATION_FAILED.format("testOp", 5, 3000));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "JWTValidation-147");

        // Test RETRY_MAX_ATTEMPTS_REACHED
        LOGGER.warn(WARN.RETRY_MAX_ATTEMPTS_REACHED.format("testOp", 5, "timeout"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "JWTValidation-149");
    }

    @Test
    @DisplayName("ERROR messages should be logged with correct format and identifier")
    void errorMessages() {
        // Test SIGNATURE_VALIDATION_FAILED
        LOGGER.error(ERROR.SIGNATURE_VALIDATION_FAILED.format("Invalid signature"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                "JWTValidation-200");

        // Test JWKS_CONTENT_SIZE_EXCEEDED
        LOGGER.error(ERROR.JWKS_CONTENT_SIZE_EXCEEDED.format(1024, 2048));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                "JWTValidation-201");

        // Test JWKS_INVALID_JSON
        LOGGER.error(ERROR.JWKS_INVALID_JSON.format("parse error"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                "JWTValidation-202");

        // Test RETRY_INTERRUPTED
        LOGGER.error(ERROR.RETRY_INTERRUPTED.format("testOp"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                "JWTValidation-215");

        // Test RETRY_ALL_ATTEMPTS_FAILED
        LOGGER.error(ERROR.RETRY_ALL_ATTEMPTS_FAILED.format(5, "testOp"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                "JWTValidation-216");
    }

    @Test
    @DisplayName("Messages with exception should log correctly")
    void messagesWithException() {
        Exception testException = new Exception("Test exception");

        // Test WARN with exception
        LOGGER.warn(testException, WARN.HTTP_FETCH_FAILED.format("http://example.com"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "JWTValidation-138");

        // Test ERROR with exception
        LOGGER.error(testException, ERROR.SIGNATURE_VALIDATION_FAILED.format("test failure"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                "JWTValidation-200");
    }

    @Test
    @DisplayName("Verify no LogRecords exist for DEBUG level (CUI standard compliance)")
    void noDebugLogRecords() {
        // DEBUG messages should use direct logging, not LogRecords
        // This test verifies that DEBUG class has been removed
        try {
            Class<?> debugClass = Class.forName("de.cuioss.jwt.validation.JWTValidationLogMessages$DEBUG");
            throw new AssertionError("DEBUG class should not exist - violates CUI logging standards");
        } catch (ClassNotFoundException e) {
            // Expected - DEBUG class should not exist
        }
    }
}