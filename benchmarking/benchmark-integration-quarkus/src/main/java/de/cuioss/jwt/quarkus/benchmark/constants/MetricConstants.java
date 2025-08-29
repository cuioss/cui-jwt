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
package de.cuioss.jwt.quarkus.benchmark.constants;

import lombok.experimental.UtilityClass;

/**
 * Local copy of metric identifier constants from cui-jwt-quarkus.
 * This avoids the dependency on the full cui-jwt-quarkus module
 * which can cause LogManager initialization issues in JMH benchmarks.
 */
@UtilityClass public final class MetricConstants {

    /**
     * The common prefix for all JWT metrics.
     */
    public static final String PREFIX = "cui.jwt";

    /**
     * Metrics related to bearer token operations.
     */
    @UtilityClass public static final class BEARERTOKEN {
        /**
         * Base path for bearer token metrics.
         */
        public static final String BASE = PREFIX + ".bearer.token";

        /**
         * Metric identifier for bearer token validation duration.
         */
        public static final String VALIDATION = BASE + ".validation";
    }

}