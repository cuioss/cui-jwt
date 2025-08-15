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
package de.cuioss.benchmarking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Badge Model Tests")
class BadgeTest {

    @Test
    @DisplayName("Should create badge with all properties")
    void shouldCreateBadgeWithAllProperties() {
        Badge badge = Badge.builder()
                .schemaVersion(1)
                .label("Performance")
                .message("100 ops/s")
                .color("green")
                .build();

        assertEquals(1, badge.getSchemaVersion());
        assertEquals("Performance", badge.getLabel());
        assertEquals("100 ops/s", badge.getMessage());
        assertEquals("green", badge.getColor());
    }

    @Test
    @DisplayName("Should use default schema version when not specified")
    void shouldUseDefaultSchemaVersion() {
        Badge badge = Badge.builder()
                .label("Test")
                .message("Value")
                .color("blue")
                .build();

        assertEquals(1, badge.getSchemaVersion());
    }

    @Test
    @DisplayName("Should throw exception when label is null")
    void shouldThrowExceptionWhenLabelIsNull() {
        Badge.Builder builder = Badge.builder()
                .message("Test message")
                .color("green");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge label cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when label is empty")
    void shouldThrowExceptionWhenLabelIsEmpty() {
        Badge.Builder builder = Badge.builder()
                .label("")
                .message("Test message")
                .color("green");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge label cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when label is whitespace only")
    void shouldThrowExceptionWhenLabelIsWhitespaceOnly() {
        Badge.Builder builder = Badge.builder()
                .label("   ")
                .message("Test message")
                .color("green");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge label cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when message is null")
    void shouldThrowExceptionWhenMessageIsNull() {
        Badge.Builder builder = Badge.builder()
                .label("Test label")
                .color("green");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge message cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when message is empty")
    void shouldThrowExceptionWhenMessageIsEmpty() {
        Badge.Builder builder = Badge.builder()
                .label("Test label")
                .message("")
                .color("green");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge message cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when message is whitespace only")
    void shouldThrowExceptionWhenMessageIsWhitespaceOnly() {
        Badge.Builder builder = Badge.builder()
                .label("Test label")
                .message("   ")
                .color("green");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge message cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when color is null")
    void shouldThrowExceptionWhenColorIsNull() {
        Badge.Builder builder = Badge.builder()
                .label("Test label")
                .message("Test message");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge color cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when color is empty")
    void shouldThrowExceptionWhenColorIsEmpty() {
        Badge.Builder builder = Badge.builder()
                .label("Test label")
                .message("Test message")
                .color("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge color cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when color is whitespace only")
    void shouldThrowExceptionWhenColorIsWhitespaceOnly() {
        Badge.Builder builder = Badge.builder()
                .label("Test label")
                .message("Test message")
                .color("   ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                builder::build);
        
        assertEquals("Badge color cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should allow custom schema version")
    void shouldAllowCustomSchemaVersion() {
        Badge badge = Badge.builder()
                .schemaVersion(2)
                .label("Test")
                .message("Value")
                .color("blue")
                .build();

        assertEquals(2, badge.getSchemaVersion());
    }

    @Test
    @DisplayName("Should create badge with valid Shields.io colors")
    void shouldCreateBadgeWithValidShieldsIoColors() {
        String[] validColors = {"brightgreen", "green", "yellow", "orange", "red", "blue", "lightgrey"};
        
        for (String color : validColors) {
            Badge badge = Badge.builder()
                    .label("Test")
                    .message("Value")
                    .color(color)
                    .build();
            
            assertEquals(color, badge.getColor());
        }
    }

    @Test
    @DisplayName("Should create badge with performance score format")
    void shouldCreateBadgeWithPerformanceScoreFormat() {
        Badge badge = Badge.builder()
                .label("Performance Score")
                .message("1000000 (1.0M ops/s, 1ms)")
                .color("brightgreen")
                .build();

        assertEquals("Performance Score", badge.getLabel());
        assertEquals("1000000 (1.0M ops/s, 1ms)", badge.getMessage());
        assertEquals("brightgreen", badge.getColor());
    }

    @Test
    @DisplayName("Should create trend badge")
    void shouldCreateTrendBadge() {
        Badge badge = Badge.builder()
                .label("Performance Trend")
                .message("improving")
                .color("brightgreen")
                .build();

        assertEquals("Performance Trend", badge.getLabel());
        assertEquals("improving", badge.getMessage());
        assertEquals("brightgreen", badge.getColor());
    }

    @Test
    @DisplayName("Should create last run badge with timestamp")
    void shouldCreateLastRunBadgeWithTimestamp() {
        Badge badge = Badge.builder()
                .label("Last Benchmark Run")
                .message("2025-08-10 20:45 UTC")
                .color("blue")
                .build();

        assertEquals("Last Benchmark Run", badge.getLabel());
        assertEquals("2025-08-10 20:45 UTC", badge.getMessage());
        assertEquals("blue", badge.getColor());
    }
}