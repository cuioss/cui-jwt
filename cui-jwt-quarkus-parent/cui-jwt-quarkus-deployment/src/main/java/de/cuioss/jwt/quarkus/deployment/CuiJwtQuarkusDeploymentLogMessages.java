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
package de.cuioss.jwt.quarkus.deployment;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Log messages for the CUI JWT Quarkus deployment module.
 * <p>
 * This class provides structured logging constants for build-time JWT extension functionality,
 * following the CUI logging standards with unique identifiers for each message.
 * </p>
 * <p>
 * Message ID ranges:
 * <ul>
 *   <li>001-099: INFO level messages</li>
 *   <li>100-199: WARN level messages</li>
 *   <li>200-299: ERROR level messages</li>
 * </ul>
 *
 * @see de.cuioss.tools.logging.LogRecord
 */
@UtilityClass
public final class CuiJwtQuarkusDeploymentLogMessages {

    /**
     * The prefix for all log messages in this module.
     */
    public static final String PREFIX = "CUI_JWT_QUARKUS_DEPLOYMENT";

    /**
     * INFO level log messages (001-099).
     */
    @UtilityClass
    public static final class INFO {

        // Deployment Messages (001-010)
        
        public static final LogRecord CUI_JWT_FEATURE_REGISTERED = LogRecordModel.builder()
                .template("CUI JWT feature registered")
                .prefix(PREFIX)
                .identifier(1)
                .build();
    }

    /**
     * WARN level log messages (100-199).
     * Currently no WARN level messages are used in the deployment module.
     */
    @UtilityClass
    public static final class WARN {
        // Reserved for future WARN level messages
    }

    /**
     * ERROR level log messages (200-299).
     * Currently no ERROR level messages are used in the deployment module.
     */
    @UtilityClass
    public static final class ERROR {
        // Reserved for future ERROR level messages
    }
}