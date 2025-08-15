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

/**
 * Represents a Shields.io badge in JSON format.
 * <p>
 * This class provides a simple model for creating badge JSON files that can be
 * consumed by Shields.io endpoint for dynamic badge generation.
 *
 * @since 1.0.0
 */
public final class Badge {

    private final int schemaVersion;
    private final String label;
    private final String message;
    private final String color;

    private Badge(Builder builder) {
        this.schemaVersion = builder.schemaVersion;
        this.label = builder.label;
        this.message = builder.message;
        this.color = builder.color;
    }

    /**
     * Gets the badge schema version.
     *
     * @return the schema version
     */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Gets the badge label (left side).
     *
     * @return the badge label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Gets the badge message (right side).
     *
     * @return the badge message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the badge color.
     *
     * @return the badge color
     */
    public String getColor() {
        return color;
    }

    /**
     * Creates a new badge builder.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating Badge instances.
     */
    public static final class Builder {
        private int schemaVersion = 1;
        private String label;
        private String message;
        private String color;

        /**
         * Sets the schema version (defaults to 1).
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
         * @param label the badge label
         * @return this builder
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the badge message.
         *
         * @param message the badge message
         * @return this builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Sets the badge color.
         *
         * @param color the badge color
         * @return this builder
         */
        public Builder color(String color) {
            this.color = color;
            return this;
        }

        /**
         * Builds the Badge instance.
         *
         * @return the created Badge
         * @throws IllegalArgumentException if required fields are missing
         */
        public Badge build() {
            if (label == null || label.trim().isEmpty()) {
                throw new IllegalArgumentException("Badge label cannot be null or empty");
            }
            if (message == null || message.trim().isEmpty()) {
                throw new IllegalArgumentException("Badge message cannot be null or empty");
            }
            if (color == null || color.trim().isEmpty()) {
                throw new IllegalArgumentException("Badge color cannot be null or empty");
            }
            return new Badge(this);
        }
    }
}