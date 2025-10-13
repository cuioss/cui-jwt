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
package de.cuioss.sheriff.oauth.library.domain.claim;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests ClaimValue functionality")
class ClaimValueTest implements ShouldHandleObjectContracts<ClaimValue> {

    private static final String TEST_STRING = "test-value";
    private static final OffsetDateTime TEST_DATE = OffsetDateTime.now();

    @Test
    @DisplayName("Create ClaimValue for plain string")
    void shouldCreateForPlainString() {
        ClaimValue value = ClaimValue.forPlainString(TEST_STRING);

        assertEquals(TEST_STRING, value.getOriginalString());
        assertEquals(ClaimValueType.STRING, value.getType());
        assertTrue(value.getAsList().isEmpty());
        assertNull(value.getDateTime());
        assertTrue(value.isPresent());
        assertFalse(value.isEmpty());
    }

    @Test
    @DisplayName("Create ClaimValue for sorted set")
    void shouldCreateForSortedSet() {
        SortedSet<String> set = new TreeSet<>(List.of("value1", "value2", "value3"));
        List<String> expectedList = new ArrayList<>(set);

        ClaimValue value = ClaimValue.forSortedSet(TEST_STRING, set);

        assertEquals(TEST_STRING, value.getOriginalString());
        assertEquals(ClaimValueType.STRING_LIST, value.getType());
        assertEquals(expectedList, value.getAsList());
        assertNull(value.getDateTime());
        assertTrue(value.isPresent());
        assertFalse(value.isEmpty());
    }

    @Test
    @DisplayName("Create ClaimValue for list")
    void shouldCreateForList() {
        List<String> list = List.of("value1", "value2", "value3");

        ClaimValue value = ClaimValue.forList(TEST_STRING, list);

        assertEquals(TEST_STRING, value.getOriginalString());
        assertEquals(ClaimValueType.STRING_LIST, value.getType());
        assertEquals(list, value.getAsList());
        assertNull(value.getDateTime());
        assertTrue(value.isPresent());
        assertFalse(value.isEmpty());
    }

    @Test
    @DisplayName("Create ClaimValue for date time")
    void shouldCreateForDateTime() {
        ClaimValue value = ClaimValue.forDateTime(TEST_STRING, TEST_DATE);

        assertEquals(TEST_STRING, value.getOriginalString());
        assertEquals(ClaimValueType.DATETIME, value.getType());
        assertTrue(value.getAsList().isEmpty());
        assertEquals(TEST_DATE, value.getDateTime());
        assertTrue(value.isPresent());
        assertFalse(value.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("Handle null and empty strings")
    void shouldHandleNullAndEmptyStrings(String input) {
        ClaimValue value = ClaimValue.forPlainString(input);

        assertEquals(input, value.getOriginalString());
        if (input == null) {
            assertTrue(value.isEmpty());
            assertFalse(value.isPresent());
        } else {
            assertFalse(value.isEmpty());
            assertTrue(value.isPresent());
        }
    }

    @Test
    @DisplayName("Create default claim values")
    void shouldCreateEmptyClaimValues() {
        ClaimValue stringValue = ClaimValue.createEmptyClaimValue(ClaimValueType.STRING);
        assertNull(stringValue.getOriginalString());
        assertEquals(ClaimValueType.STRING, stringValue.getType());

        ClaimValue listValue = ClaimValue.createEmptyClaimValue(ClaimValueType.STRING_LIST);
        assertNull(listValue.getOriginalString());
        assertEquals(ClaimValueType.STRING_LIST, listValue.getType());
        assertTrue(listValue.getAsList().isEmpty());

        ClaimValue dateTimeValue = ClaimValue.createEmptyClaimValue(ClaimValueType.DATETIME);
        assertNull(dateTimeValue.getOriginalString());
        assertEquals(ClaimValueType.DATETIME, dateTimeValue.getType());
        assertNull(dateTimeValue.getDateTime());
    }

    @Test
    @DisplayName("Check if value is present for claim value type")
    void shouldCheckIfValueIsPresentForClaimValueType() {
        ClaimValue presentString = ClaimValue.forPlainString(TEST_STRING);
        ClaimValue nullString = ClaimValue.forPlainString(null);

        SortedSet<String> nonEmptySet = new TreeSet<>(Set.of("value"));
        ClaimValue nonEmptySetValue = ClaimValue.forSortedSet("original", nonEmptySet);

        List<String> emptyList = Collections.emptyList();
        List<String> nonEmptyList = List.of("value");
        ClaimValue emptyListValue = ClaimValue.forList(null, emptyList);
        ClaimValue nonEmptyListValue = ClaimValue.forList("original", nonEmptyList);

        ClaimValue nullDateTime = ClaimValue.forDateTime(null, null);
        ClaimValue nonNullDateTime = ClaimValue.forDateTime("original", TEST_DATE);

        assertFalse(presentString.isNotPresentForClaimValueType());
        assertTrue(nullString.isNotPresentForClaimValueType());
        assertFalse(nonEmptySetValue.isNotPresentForClaimValueType());
        assertTrue(emptyListValue.isNotPresentForClaimValueType());
        assertFalse(nonEmptyListValue.isNotPresentForClaimValueType());
        assertTrue(nullDateTime.isNotPresentForClaimValueType());
        assertFalse(nonNullDateTime.isNotPresentForClaimValueType());
    }

    @Test
    @DisplayName("Throw NullPointerException for null claim value type")
    void shouldThrowExceptionForNullType() {
        assertThrows(NullPointerException.class, () -> ClaimValue.createEmptyClaimValue(null),
                "Should throw NullPointerException when null is passed to createEmptyClaimValue");
    }

    @Override
    public ClaimValue getUnderTest() {
        return ClaimValue.forPlainString("test-value");
    }
}