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
package de.cuioss.jwt.quarkus.config;

import de.cuioss.tools.logging.CuiLogger;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.eclipse.microprofile.config.Config;

import static de.cuioss.jwt.quarkus.config.JwtPropertyKeys.ISSUERS.KEYCLOAK_DEFAULT_GROUPS_ENABLED;
import static de.cuioss.jwt.quarkus.config.JwtPropertyKeys.ISSUERS.KEYCLOAK_DEFAULT_ROLES_ENABLED;

/**
 * Configuration resolver for Keycloak mapper settings per issuer.
 * <p>
 * This class handles the resolution of Keycloak-specific mapper configuration
 * properties that control the activation of default claim mappers for compatibility
 * with Keycloak's standard token structure on a per-issuer basis.
 * </p>
 * <p>
 * The resolver provides configuration for:
 * <ul>
 *   <li>Default roles mapper - maps {@code realm_access.roles} to {@code roles}</li>
 *   <li>Default groups mapper - processes Keycloak's standard {@code groups} claim</li>
 * </ul>
 *
 *
 * @since 1.0
 */
public class KeycloakMapperConfigResolver {

    private static final CuiLogger LOGGER = new CuiLogger(KeycloakMapperConfigResolver.class);
    private final Config config;

    /**
     * Creates a new KeycloakMapperConfigResolver with the specified configuration.
     *
     * @param config the configuration instance to use for property resolution
     */
    public KeycloakMapperConfigResolver(@NonNull Config config) {
        this.config = config;
    }

    /**
     * Resolves Keycloak mapper configuration from properties for a specific issuer.
     *
     * @param issuerName the name of the issuer configuration
     * @return the resolved KeycloakMapperConfig for this issuer
     */
    public KeycloakMapperConfig resolve(String issuerName) {
        String rolesKey = KEYCLOAK_DEFAULT_ROLES_ENABLED.formatted(issuerName);
        String groupsKey = KEYCLOAK_DEFAULT_GROUPS_ENABLED.formatted(issuerName);

        LOGGER.debug("Resolving Keycloak mapper config for issuer: %s", issuerName);
        LOGGER.debug("Looking for roles mapper config at key: %s", rolesKey);
        LOGGER.debug("Looking for groups mapper config at key: %s", groupsKey);

        boolean defaultRolesEnabled = config.getOptionalValue(rolesKey, Boolean.class)
                .orElse(false);

        boolean defaultGroupsEnabled = config.getOptionalValue(groupsKey, Boolean.class)
                .orElse(false);

        LOGGER.debug("Resolved roles mapper enabled: %s for issuer: %s", defaultRolesEnabled, issuerName);
        LOGGER.debug("Resolved groups mapper enabled: %s for issuer: %s", defaultGroupsEnabled, issuerName);

        return KeycloakMapperConfig.builder()
                .defaultRolesEnabled(defaultRolesEnabled)
                .defaultGroupsEnabled(defaultGroupsEnabled)
                .build();
    }

    /**
     * Configuration holder for Keycloak mapper settings per issuer.
     */
    @Value
    @Builder
    public static class KeycloakMapperConfig {

        /**
         * Whether the default roles mapper is enabled for this issuer.
         * When true, enables mapping from {@code realm_access.roles} to {@code roles}.
         */
        boolean defaultRolesEnabled;

        /**
         * Whether the default groups mapper is enabled for this issuer.
         * When true, enables processing of Keycloak's standard {@code groups} claim.
         */
        boolean defaultGroupsEnabled;
    }
}
