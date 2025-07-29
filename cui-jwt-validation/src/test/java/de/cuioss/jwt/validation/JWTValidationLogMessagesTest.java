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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.tools.logging.LogRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link JWTValidationLogMessages} utility class.
 *
 * @author Oliver Wolff
 */
@EnableGeneratorController
class JWTValidationLogMessagesTest {

    @Test
    void shouldProvideDebugLogRecords() {
        assertNotNull(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED);
        assertLogRecordProperties(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED,
                "JWTValidation", 500, "Successfully created access token");

        assertNotNull(JWTValidationLogMessages.DEBUG.ID_TOKEN_CREATED);
        assertLogRecordProperties(JWTValidationLogMessages.DEBUG.ID_TOKEN_CREATED,
                "JWTValidation", 501, "Successfully created ID-Token");

        assertNotNull(JWTValidationLogMessages.DEBUG.OPTIONAL_URL_FIELD_MISSING);
        assertLogRecordProperties(JWTValidationLogMessages.DEBUG.OPTIONAL_URL_FIELD_MISSING,
                "JWTValidation", 503, "Optional URL field '%s' is missing in discovery document from %s");
    }

    @Test
    void shouldProvideErrorLogRecords() {
        assertNotNull(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED);
        assertLogRecordProperties(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED,
                "JWTValidation", 200, "Failed to validate validation signature: %s");

        assertNotNull(JWTValidationLogMessages.ERROR.JWKS_CONTENT_SIZE_EXCEEDED);
        assertLogRecordProperties(JWTValidationLogMessages.ERROR.JWKS_CONTENT_SIZE_EXCEEDED,
                "JWTValidation", 201, "JWKS content size exceeds maximum allowed size (upperLimit=%s, actual=%s)");

        assertNotNull(JWTValidationLogMessages.ERROR.ISSUER_VALIDATION_FAILED);
        assertLogRecordProperties(JWTValidationLogMessages.ERROR.ISSUER_VALIDATION_FAILED,
                "JWTValidation", 203);

        // Test the new cache-related ERROR messages
        assertNotNull(JWTValidationLogMessages.ERROR.CACHE_TOKEN_NO_EXPIRATION);
        assertLogRecordProperties(JWTValidationLogMessages.ERROR.CACHE_TOKEN_NO_EXPIRATION,
                "JWTValidation", 211, "Token passed validation but has no expiration time - this indicates a validation bug");

        assertNotNull(JWTValidationLogMessages.ERROR.CACHE_TOKEN_STORE_FAILED);
        assertLogRecordProperties(JWTValidationLogMessages.ERROR.CACHE_TOKEN_STORE_FAILED,
                "JWTValidation", 212, "Unexpected error while caching token");

        assertNotNull(JWTValidationLogMessages.ERROR.CACHE_VALIDATION_FUNCTION_NULL);
        assertLogRecordProperties(JWTValidationLogMessages.ERROR.CACHE_VALIDATION_FUNCTION_NULL,
                "JWTValidation", 213, "Validation function returned null instead of throwing exception");

        assertNotNull(JWTValidationLogMessages.ERROR.CACHE_EVICTION_FAILED);
        assertLogRecordProperties(JWTValidationLogMessages.ERROR.CACHE_EVICTION_FAILED,
                "JWTValidation", 214, "Error during cache eviction");
    }

    @Test
    void shouldProvideInfoLogRecords() {
        assertNotNull(JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED);

        assertNotNull(JWTValidationLogMessages.INFO.JWKS_KEYS_UPDATED);
        assertLogRecordProperties(JWTValidationLogMessages.INFO.JWKS_KEYS_UPDATED,
                "JWTValidation", 2, "Keys updated due to data change - load state: %s");

        assertNotNull(JWTValidationLogMessages.INFO.ISSUER_CONFIG_SKIPPED);
        assertLogRecordProperties(JWTValidationLogMessages.INFO.ISSUER_CONFIG_SKIPPED,
                "JWTValidation", 6, "Skipping disabled issuer configuration %s");
    }

    @Test
    void shouldProvideWarnLogRecords() {
        assertNotNull(JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED);
        assertLogRecordProperties(JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED,
                "JWTValidation", 100, "Token exceeds maximum size limit of %s bytes, validation will be rejected");

        assertNotNull(JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY);
        assertLogRecordProperties(JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY,
                "JWTValidation", 101, "The given validation was empty, request will be rejected");

        assertNotNull(JWTValidationLogMessages.WARN.KEY_NOT_FOUND);
        assertLogRecordProperties(JWTValidationLogMessages.WARN.KEY_NOT_FOUND,
                "JWTValidation", 102, "No key found with ID: %s");
    }

    @Test
    void shouldUseConsistentIdentifierRanges() {
        // DEBUG: 500-599
        assertTrue(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED.getIdentifier() >= 500);
        assertTrue(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED.getIdentifier() < 600);

        // INFO: 1-99
        assertTrue(JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED.getIdentifier() >= 1);
        assertTrue(JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED.getIdentifier() < 100);

        // WARN: 100-199
        assertTrue(JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED.getIdentifier() >= 100);
        assertTrue(JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED.getIdentifier() < 200);

        // ERROR: 200-299
        assertTrue(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.getIdentifier() >= 200);
        assertTrue(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.getIdentifier() < 300);
    }

    @Test
    void shouldHaveConsistentPrefixAcrossAllRecords() {
        assertEquals("JWTValidation", JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED.getPrefix());
        assertEquals("JWTValidation", JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED.getPrefix());
        assertEquals("JWTValidation", JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED.getPrefix());
        assertEquals("JWTValidation", JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.getPrefix());
    }

    @Test
    void shouldProvideNonEmptyTemplatesForAllRecords() {
        assertFalse(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED.getTemplate().isEmpty());
        assertFalse(JWTValidationLogMessages.INFO.TOKEN_FACTORY_INITIALIZED.getTemplate().isEmpty());
        assertFalse(JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED.getTemplate().isEmpty());
        assertFalse(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.getTemplate().isEmpty());
    }

    @Test
    void shouldHaveUniqueIdentifiersWithinCategory() {
        // Test a few key identifiers to ensure they're unique within their range
        assertNotEquals(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED.getIdentifier(),
                JWTValidationLogMessages.DEBUG.ID_TOKEN_CREATED.getIdentifier());

        assertNotEquals(JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED.getIdentifier(),
                JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY.getIdentifier());

        assertNotEquals(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.getIdentifier(),
                JWTValidationLogMessages.ERROR.JWKS_CONTENT_SIZE_EXCEEDED.getIdentifier());
    }

    private void assertLogRecordProperties(LogRecord logRecord, String expectedPrefix,
            int expectedIdentifier, String expectedTemplate) {
        assertEquals(expectedPrefix, logRecord.getPrefix());
        assertEquals(expectedIdentifier, logRecord.getIdentifier());
        assertEquals(expectedTemplate, logRecord.getTemplate());
    }

    private void assertLogRecordProperties(LogRecord logRecord, String expectedPrefix,
            int expectedIdentifier) {
        assertEquals(expectedPrefix, logRecord.getPrefix());
        assertEquals(expectedIdentifier, logRecord.getIdentifier());
        assertNotNull(logRecord.getTemplate());
        assertFalse(logRecord.getTemplate().isEmpty());
    }
}