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
package de.cuioss.jwt.quarkus.integration.config;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Configuration for a specific Keycloak realm used in token fetching.
 * This immutable data structure encapsulates all realm-specific settings
 * to avoid code duplication when working with multiple realms.
 * 
 * Design Rationale:
 * - Encapsulates realm-specific configuration to enable multi-realm support
 * - Immutable to ensure thread safety and prevent accidental modifications
 * - Uses Builder pattern for clean construction with optional parameters
 * - Avoids code duplication by parameterizing realm differences
 */
@Getter
@Builder
@ToString
public class RealmConfig {

    /**
     * The realm name (e.g., "benchmark", "integration")
     */
    private final String realmName;

    /**
     * The client ID for this realm
     */
    private final String clientId;

    /**
     * The client secret for this realm (if using confidential clients)
     */
    private final String clientSecret;

    /**
     * Username for token requests
     */
    private final String username;

    /**
     * Password for token requests
     */
    private final String password;

    /**
     * Optional display name for logging purposes
     */
    private final String displayName;

    /**
     * Builds the token endpoint URL for this realm.
     * 
     * @param keycloakBaseUrl The base Keycloak URL (e.g., "http://keycloak:8080")
     * @return The complete token endpoint URL
     */
    public String buildTokenUrl(String keycloakBaseUrl) {
        return keycloakBaseUrl + "/realms/" + realmName + "/protocol/openid-connect/token";
    }

    /**
     * Gets a display name for logging purposes.
     * Falls back to realm name if display name is not set.
     * 
     * @return A human-readable name for this realm configuration
     */
    public String getEffectiveDisplayName() {
        return displayName != null ? displayName : realmName;
    }
}