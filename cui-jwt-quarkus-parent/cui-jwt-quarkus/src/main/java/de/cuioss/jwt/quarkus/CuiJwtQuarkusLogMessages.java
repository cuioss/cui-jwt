/**
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
package de.cuioss.jwt.quarkus;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Log messages for the CUI JWT Quarkus runtime module.
 * <p>
 * This class provides structured logging constants for runtime JWT validation functionality,
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
public final class CuiJwtQuarkusLogMessages {

    /**
     * The prefix for all log messages in this module.
     */
    public static final String PREFIX = "CUI_JWT_QUARKUS";

    /**
     * INFO level log messages (001-099).
     */
    @UtilityClass
    public static final class INFO {

        // Configuration Messages (001-010)
        
        public static final LogRecord RESOLVING_ISSUER_CONFIGURATIONS = LogRecordModel.builder()
                .template("Resolving issuer configurations from properties")
                .prefix(PREFIX)
                .identifier(1)
                .build();

        public static final LogRecord RESOLVED_ISSUER_CONFIGURATION = LogRecordModel.builder()
                .template("Resolved issuer configuration: %s")
                .prefix(PREFIX)
                .identifier(2)
                .build();

        public static final LogRecord RESOLVED_ENABLED_ISSUER_CONFIGURATIONS = LogRecordModel.builder()
                .template("Resolved %s enabled issuer configurations")
                .prefix(PREFIX)
                .identifier(3)
                .build();

        public static final LogRecord RESOLVED_PARSER_CONFIG = LogRecordModel.builder()
                .template("Resolved ParserConfig: maxTokenSize=%s bytes, maxPayloadSize=%s bytes, maxStringSize=%s, maxArraySize=%s, maxDepth=%s")
                .prefix(PREFIX)
                .identifier(4)
                .build();

        // Validation Messages (011-020)
        
        public static final LogRecord INITIALIZING_JWT_VALIDATION_COMPONENTS = LogRecordModel.builder()
                .template("Initializing JWT validation components from configuration")
                .prefix(PREFIX)
                .identifier(11)
                .build();

        public static final LogRecord JWT_VALIDATION_COMPONENTS_INITIALIZED = LogRecordModel.builder()
                .template("JWT validation components initialized successfully with %s issuers")
                .prefix(PREFIX)
                .identifier(12)
                .build();

        // Metrics Messages (021-030)
        
        public static final LogRecord INITIALIZING_JWT_METRICS_COLLECTOR = LogRecordModel.builder()
                .template("Initializing JwtMetricsCollector")
                .prefix(PREFIX)
                .identifier(21)
                .build();

        public static final LogRecord JWT_METRICS_COLLECTOR_INITIALIZED = LogRecordModel.builder()
                .template("JwtMetricsCollector initialized with %s event types")
                .prefix(PREFIX)
                .identifier(22)
                .build();
    }

    /**
     * WARN level log messages (100-199).
     */
    @UtilityClass
    public static final class WARN {

        // Health Check Warnings (100-110)
        
        public static final LogRecord ERROR_CHECKING_JWKS_LOADER = LogRecordModel.builder()
                .template("Error checking JWKS loader for issuer %s: %s")
                .prefix(PREFIX)
                .identifier(100)
                .build();

        // Metrics Warnings (111-120)
        
        public static final LogRecord SECURITY_EVENT_COUNTER_NOT_AVAILABLE = LogRecordModel.builder()
                .template("SecurityEventCounter not available, metrics will not be collected")
                .prefix(PREFIX)
                .identifier(111)
                .build();
    }

    /**
     * ERROR level log messages (200-299).
     * Currently no ERROR level messages are used in the runtime module.
     */
    @UtilityClass
    public static final class ERROR {
        // Reserved for future ERROR level messages
    }
}