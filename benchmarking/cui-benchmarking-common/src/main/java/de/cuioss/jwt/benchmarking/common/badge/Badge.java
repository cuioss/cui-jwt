/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.benchmarking.common.badge;

/**
 * Represents a Shields.io compatible badge for JSON endpoint consumption.
 * Follows the schema version 1 specification for dynamic badges.
 * 
 * @author CUI Benchmarking Infrastructure
 */
public class Badge {
    
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
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters for JSON serialization
    public int getSchemaVersion() { return schemaVersion; }
    public String getLabel() { return label; }
    public String getMessage() { return message; }
    public String getColor() { return color; }
    
    /**
     * Builder for constructing Badge instances.
     */
    public static class Builder {
        private int schemaVersion = 1;
        private String label;
        private String message;
        private String color;
        
        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }
        
        public Builder label(String label) {
            this.label = label;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder color(String color) {
            this.color = color;
            return this;
        }
        
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