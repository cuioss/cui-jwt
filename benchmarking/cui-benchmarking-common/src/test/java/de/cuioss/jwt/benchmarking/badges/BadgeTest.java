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
package de.cuioss.jwt.benchmarking.badges;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Badge}.
 * 
 * @author CUI-OpenSource-Software
 */
class BadgeTest {

    @Test
    void testBadgeCreation() {
        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label("Test Label")
                .message("Test Message")
                .color("green")
                .build();

        assertEquals(1, badge.schemaVersion());
        assertEquals("Test Label", badge.label());
        assertEquals("Test Message", badge.message());
        assertEquals("green", badge.color());
    }

    @Test
    void testBadgeBuilderValidation() {
        Badge.Builder builder = Badge.builder();

        // Test missing label
        assertThrows(IllegalArgumentException.class, () -> {
            builder.message("message").color("green").build();
        });

        // Test empty label
        assertThrows(IllegalArgumentException.class, () -> {
            builder.label("").message("message").color("green").build();
        });

        // Test missing message
        assertThrows(IllegalArgumentException.class, () -> {
            builder.label("label").color("green").build();
        });

        // Test empty message
        assertThrows(IllegalArgumentException.class, () -> {
            builder.label("label").message("").color("green").build();
        });

        // Test missing color
        assertThrows(IllegalArgumentException.class, () -> {
            builder.label("label").message("message").build();
        });

        // Test empty color
        assertThrows(IllegalArgumentException.class, () -> {
            builder.label("label").message("message").color("").build();
        });
    }

    @Test
    void testDefaultSchemaVersion() {
        Badge badge = Badge.builder()
                .label("Test")
                .message("Message")
                .color("blue")
                .build();

        assertEquals(1, badge.schemaVersion());
    }

    @Test
    void testCustomSchemaVersion() {
        Badge badge = Badge.builder()
                .schemaVersion(2)
                .label("Test")
                .message("Message")
                .color("blue")
                .build();

        assertEquals(2, badge.schemaVersion());
    }

    @Test
    void testBuilderChaining() {
        Badge badge = Badge.builder()
                .label("Performance")
                .message("95% pass rate")
                .color("brightgreen")
                .schemaVersion(1)
                .build();

        assertEquals("Performance", badge.label());
        assertEquals("95% pass rate", badge.message());
        assertEquals("brightgreen", badge.color());
        assertEquals(1, badge.schemaVersion());
    }
}