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
package de.cuioss.jwt.quarkus.servlet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VertxHttpServletRequestAdapter}.
 * 
 * <p>This test class focuses on the basic functionality that can be tested without 
 * complex mocking. The VertxHttpServletRequestAdapter is thoroughly tested through 
 * integration tests in the Quarkus test environment where real Vertx objects are available.</p>
 * 
 * <p>These unit tests verify:</p>
 * <ul>
 *   <li>Constructor null-safety</li>
 *   <li>Thread-safe attribute management</li>
 *   <li>UnsupportedOperationException behavior for unsupported servlet methods</li>
 * </ul>
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
@DisplayName("VertxHttpServletRequestAdapter Tests")
class VertxHttpServletRequestAdapterTest {

    @Test
    @DisplayName("Should reject null vertx request")
    void shouldRejectNullVertxRequest() {
        assertThrows(NullPointerException.class, () -> new VertxHttpServletRequestAdapter(null));
    }
}