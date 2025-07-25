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
package de.cuioss.jwt.validation.domain.claim.mapper;

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.ClaimValueType;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests OffsetDateTimeMapper functionality")
class OffsetDateTimeMapperTest {

    private static final String CLAIM_NAME = "testDateTimeClaim";
    private final OffsetDateTimeMapper underTest = new OffsetDateTimeMapper();

    @Test
    @DisplayName("Map valid numeric timestamp as number (JWT NumericDate)")
    void shouldMapValidNumericTimestampAsNumber() {
        long epochSeconds = 1673785845;
        OffsetDateTime expected = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ZoneId.systemDefault()
        );

        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, epochSeconds)
                .build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertEquals(String.valueOf(epochSeconds), result.getOriginalString(), "Original string should be preserved");
        assertEquals(expected, result.getDateTime(), "DateTime should be correctly parsed");
        assertEquals(ClaimValueType.DATETIME, result.getType(), "Type should be DATETIME");
    }

    @Test
    @DisplayName("Throw exception for numeric timestamp as string (not compliant with JWT spec)")
    void shouldThrowExceptionForNumericTimestampAsString() {
        long epochSeconds = 1673785845;
        String validTimestamp = String.valueOf(epochSeconds);

        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, validTimestamp);

        assertThrows(IllegalArgumentException.class, () -> underTest.map(jsonObject, CLAIM_NAME),
                "Should throw IllegalArgumentException for string value (even if it's a valid numeric timestamp)");
    }

    @Test
    @DisplayName("Throw exception for ISO-8601 date-time string (not compliant with JWT spec)")
    void shouldMapValidIsoDateTime() {
        String validDateTime = "2023-01-15T12:30:45Z";

        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, validDateTime);

        assertThrows(IllegalArgumentException.class, () -> underTest.map(jsonObject, CLAIM_NAME),
                "Should throw IllegalArgumentException for string value (even if it's a valid ISO-8601 date-time)");
    }

    @Test
    @DisplayName("Handle null claim value")
    void shouldHandleNullClaimValue() {
        JsonObject jsonObject = createJsonObjectWithNullClaim(CLAIM_NAME);

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertNull(result.getOriginalString(), "Original string should be null");
        assertNull(result.getDateTime(), "DateTime should be null for null claim value");
        assertEquals(ClaimValueType.DATETIME, result.getType(), "Type should be DATETIME");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Throw exception for blank string inputs (not compliant with JWT spec)")
    void shouldThrowExceptionForBlankStringInputs(String blankInput) {
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, blankInput);

        assertThrows(IllegalArgumentException.class, () -> underTest.map(jsonObject, CLAIM_NAME),
                "Should throw IllegalArgumentException for string value (even if it's blank)");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-date",
            "not-a-number",
            "123abc", // Not a valid number
            "2023-13-01T12:30:45Z", // Invalid month
            "2023-01-32T12:30:45Z", // Invalid day
            "2023-01-01T25:30:45Z", // Invalid hour
            "2023-01-01T12:60:45Z", // Invalid minute
            "2023-01-01T12:30:60Z", // Invalid second
            "2023-01-01T12:30:45" // Missing timezone
    })
    @DisplayName("Throw exception for invalid date-time formats")
    void shouldThrowExceptionForInvalidFormats(String invalidDateTime) {
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, invalidDateTime);

        assertThrows(IllegalArgumentException.class, () -> underTest.map(jsonObject, CLAIM_NAME),
                "Should throw IllegalArgumentException for invalid date-time format");
    }

    @Test
    @DisplayName("Handle missing claim")
    void shouldHandleMissingClaim() {
        JsonObject jsonObject = Json.createObjectBuilder().build();

        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertNull(result.getOriginalString(), "Original string should be null");
        assertNull(result.getDateTime(), "DateTime should be null for missing claim");
        assertEquals(ClaimValueType.DATETIME, result.getType(), "Type should be DATETIME");
    }

    @Test
    @DisplayName("Handle empty JsonObject")
    void shouldHandleEmptyJsonObject() {
        JsonObject emptyJsonObject = Json.createObjectBuilder().build();

        ClaimValue result = underTest.map(emptyJsonObject, CLAIM_NAME);

        assertNotNull(result, "Result should not be null");
        assertNull(result.getOriginalString(), "Original string should be null");
        assertNull(result.getDateTime(), "DateTime should be null for empty JsonObject");
        assertEquals(ClaimValueType.DATETIME, result.getType(), "Type should be DATETIME");
    }

    @Test
    @DisplayName("Throw exception for unsupported JSON value types")
    void shouldThrowExceptionForUnsupportedTypes() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, Json.createObjectBuilder().build())
                .build();

        assertThrows(IllegalArgumentException.class, () -> underTest.map(jsonObject, CLAIM_NAME),
                "Should throw IllegalArgumentException for unsupported JSON value type");
    }

    // Helper methods

    private JsonObject createJsonObjectWithStringClaim(String claimName, String value) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (value != null) {
            builder.add(claimName, value);
        } else {
            builder.addNull(claimName);
        }
        return builder.build();
    }

    private JsonObject createJsonObjectWithNullClaim(String claimName) {
        return Json.createObjectBuilder()
                .addNull(claimName)
                .build();
    }
}
