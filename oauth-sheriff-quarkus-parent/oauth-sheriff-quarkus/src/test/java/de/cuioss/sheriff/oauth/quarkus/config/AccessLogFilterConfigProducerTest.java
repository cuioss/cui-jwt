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
package de.cuioss.sheriff.oauth.quarkus.config;

import de.cuioss.sheriff.oauth.quarkus.test.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccessLogFilterConfigProducer Tests")
class AccessLogFilterConfigProducerTest {

    @Test
    @DisplayName("should create producer with config")
    void shouldCreateProducerWithConfig() {
        TestConfig config = new TestConfig(Map.of());

        AccessLogFilterConfigProducer producer = new AccessLogFilterConfigProducer(config);

        assertNotNull(producer);
    }

    @Test
    @DisplayName("should produce AccessLogFilterConfigResolver")
    void shouldProduceAccessLogFilterConfigResolver() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESSLOG.ENABLED, "true",
                JwtPropertyKeys.ACCESSLOG.MIN_STATUS_CODE, "400"
        ));
        AccessLogFilterConfigProducer producer = new AccessLogFilterConfigProducer(config);

        AccessLogFilterConfigResolver resolver = producer.produceAccessLogFilterConfigResolver();

        assertNotNull(resolver);
        AccessLogFilterConfig resolvedConfig = resolver.resolveConfig();
        assertNotNull(resolvedConfig);
        assertTrue(resolvedConfig.isEnabled());
        assertEquals(400, resolvedConfig.getMinStatusCode());
    }

    @Test
    @DisplayName("should produce resolver with default configuration")
    void shouldProduceResolverWithDefaults() {
        TestConfig config = new TestConfig(Map.of());
        AccessLogFilterConfigProducer producer = new AccessLogFilterConfigProducer(config);

        AccessLogFilterConfigResolver resolver = producer.produceAccessLogFilterConfigResolver();

        assertNotNull(resolver);
        AccessLogFilterConfig resolvedConfig = resolver.resolveConfig();
        assertNotNull(resolvedConfig);
        assertFalse(resolvedConfig.isEnabled());
        assertEquals(400, resolvedConfig.getMinStatusCode());
        assertEquals(599, resolvedConfig.getMaxStatusCode());
    }

    @Test
    @DisplayName("should produce resolver that uses provided config")
    void shouldProduceResolverWithCustomConfig() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.ACCESSLOG.ENABLED, "true",
                JwtPropertyKeys.ACCESSLOG.MIN_STATUS_CODE, "200",
                JwtPropertyKeys.ACCESSLOG.MAX_STATUS_CODE, "599",
                JwtPropertyKeys.ACCESSLOG.PATTERN, "{method} {path}"
        ));
        AccessLogFilterConfigProducer producer = new AccessLogFilterConfigProducer(config);

        AccessLogFilterConfigResolver resolver = producer.produceAccessLogFilterConfigResolver();

        assertNotNull(resolver);
        AccessLogFilterConfig resolvedConfig = resolver.resolveConfig();
        assertNotNull(resolvedConfig);
        assertTrue(resolvedConfig.isEnabled());
        assertEquals(200, resolvedConfig.getMinStatusCode());
        assertEquals(599, resolvedConfig.getMaxStatusCode());
        assertEquals("{method} {path}", resolvedConfig.getPattern());
    }

}
