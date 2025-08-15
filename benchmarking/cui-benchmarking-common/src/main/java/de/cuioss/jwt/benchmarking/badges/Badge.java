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

/**
 * Shields.io compatible badge data structure.
 * <p>
 * This record represents a badge in the JSON format expected by the Shields.io
 * dynamic badge service. The format follows the Shields.io specification for
 * endpoint badges.
 * 
 * @param schemaVersion the schema version (should be 1)
 * @param label the text on the left side of the badge
 * @param message the text on the right side of the badge
 * @param color the background color of the right side
 * 
 * @author CUI-OpenSource-Software
 * @since 1.0.0
 * @see <a href="https://shields.io/badges/endpoint-badge">Shields.io Endpoint Badge Documentation</a>
 */
public record Badge(
    int schemaVersion,
    String label,
    String message,
    String color
) {

    /**
     * Creates a new badge builder.
     * 
     * @return a new badge builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing Badge instances.
     */
    public static class Builder {
        private int schemaVersion = 1;
        private String label;
        private String message;
        private String color;

        /**
         * Sets the schema version.
         * 
         * @param schemaVersion the schema version
         * @return this builder
         */
        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        /**
         * Sets the badge label.
         * 
         * @param label the label text
         * @return this builder
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the badge message.
         * 
         * @param message the message text
         * @return this builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Sets the badge color.
         * 
         * @param color the color name or hex code
         * @return this builder
         */
        public Builder color(String color) {
            this.color = color;
            return this;
        }

        /**
         * Builds the badge.
         * 
         * @return the constructed badge
         * @throws IllegalArgumentException if required fields are missing
         */
        public Badge build() {
            if (label == null || label.isEmpty()) {
                throw new IllegalArgumentException("Badge label cannot be null or empty");
            }
            if (message == null || message.isEmpty()) {
                throw new IllegalArgumentException("Badge message cannot be null or empty");
            }
            if (color == null || color.isEmpty()) {
                throw new IllegalArgumentException("Badge color cannot be null or empty");
            }
            
            return new Badge(schemaVersion, label, message, color);
        }
    }
}