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
package de.cuioss.jwt.quarkus.producer;

import de.cuioss.jwt.quarkus.annotation.BearerToken;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BearerTokenProducer}.
 * <p>
 * Basic tests to verify the BearerToken annotation structure and basic functionality.
 * Integration testing with real tokens is handled in the integration test module.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@EnableTestLogger
class BearerTokenProducerTest {

    @Test
    @DisplayName("BearerToken annotation should have correct structure")
    void bearerTokenAnnotationStructure() {
        // Test that we can create the annotation
        BearerToken annotation = createBearerTokenAnnotation();
        
        assertNotNull(annotation);
        assertEquals(BearerToken.class, annotation.annotationType());
        assertArrayEquals(new String[0], annotation.requiredScopes());
        assertArrayEquals(new String[0], annotation.requiredRoles());
        assertArrayEquals(new String[0], annotation.requiredGroups());
    }

    @Test
    @DisplayName("BearerToken annotation should support required scopes")
    void bearerTokenAnnotationWithScopes() {
        BearerToken annotation = createBearerTokenAnnotation("read", "write");
        
        assertNotNull(annotation);
        assertArrayEquals(new String[]{"read", "write"}, annotation.requiredScopes());
        assertArrayEquals(new String[0], annotation.requiredRoles());
        assertArrayEquals(new String[0], annotation.requiredGroups());
    }

    @Test
    @DisplayName("BearerToken annotation should support required roles")
    void bearerTokenAnnotationWithRoles() {
        BearerToken annotation = createBearerTokenAnnotationWithRoles("admin", "user");
        
        assertNotNull(annotation);
        assertArrayEquals(new String[0], annotation.requiredScopes());
        assertArrayEquals(new String[]{"admin", "user"}, annotation.requiredRoles());
        assertArrayEquals(new String[0], annotation.requiredGroups());
    }

    @Test
    @DisplayName("BearerToken annotation should support required groups")
    void bearerTokenAnnotationWithGroups() {
        BearerToken annotation = createBearerTokenAnnotationWithGroups("managers", "developers");
        
        assertNotNull(annotation);
        assertArrayEquals(new String[0], annotation.requiredScopes());
        assertArrayEquals(new String[0], annotation.requiredRoles());
        assertArrayEquals(new String[]{"managers", "developers"}, annotation.requiredGroups());
    }

    @Test
    @DisplayName("BearerToken annotation should support all requirements")
    void bearerTokenAnnotationWithAllRequirements() {
        BearerToken annotation = createBearerTokenAnnotationWithAll(
                new String[]{"read"}, new String[]{"user"}, new String[]{"employees"});
        
        assertNotNull(annotation);
        assertArrayEquals(new String[]{"read"}, annotation.requiredScopes());
        assertArrayEquals(new String[]{"user"}, annotation.requiredRoles());
        assertArrayEquals(new String[]{"employees"}, annotation.requiredGroups());
    }

    @Test
    @DisplayName("BearerTokenProducer should be instantiable")
    void bearerTokenProducerInstantiation() {
        BearerTokenProducer producer = new BearerTokenProducer();
        assertNotNull(producer);
    }

    // Helper methods for creating mock annotations

    private BearerToken createBearerTokenAnnotation(String... requiredScopes) {
        return new BearerToken() {
            @Override
            public String[] requiredScopes() { return requiredScopes; }
            @Override
            public String[] requiredRoles() { return new String[0]; }
            @Override
            public String[] requiredGroups() { return new String[0]; }
            @Override
            public Class<? extends Annotation> annotationType() { return BearerToken.class; }
        };
    }

    private BearerToken createBearerTokenAnnotationWithRoles(String... requiredRoles) {
        return new BearerToken() {
            @Override
            public String[] requiredScopes() { return new String[0]; }
            @Override
            public String[] requiredRoles() { return requiredRoles; }
            @Override
            public String[] requiredGroups() { return new String[0]; }
            @Override
            public Class<? extends Annotation> annotationType() { return BearerToken.class; }
        };
    }

    private BearerToken createBearerTokenAnnotationWithGroups(String... requiredGroups) {
        return new BearerToken() {
            @Override
            public String[] requiredScopes() { return new String[0]; }
            @Override
            public String[] requiredRoles() { return new String[0]; }
            @Override
            public String[] requiredGroups() { return requiredGroups; }
            @Override
            public Class<? extends Annotation> annotationType() { return BearerToken.class; }
        };
    }

    private BearerToken createBearerTokenAnnotationWithAll(String[] scopes, String[] roles, String[] groups) {
        return new BearerToken() {
            @Override
            public String[] requiredScopes() { return scopes; }
            @Override
            public String[] requiredRoles() { return roles; }
            @Override
            public String[] requiredGroups() { return groups; }
            @Override
            public Class<? extends Annotation> annotationType() { return BearerToken.class; }
        };
    }
}