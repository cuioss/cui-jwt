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

import de.cuioss.test.juli.junit5.EnableTestLogger;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CuiJwtProcessor} build step methods.
 */
@EnableTestLogger
class CuiJwtProcessorBuildStepTest {

    private final CuiJwtProcessor processor = new CuiJwtProcessor();

    @Test
    void shouldCreateFeatureBuildItem() {
        // Act
        FeatureBuildItem featureItem = processor.feature();

        // Assert
        assertNotNull(featureItem);
        assertEquals("cui-jwt", featureItem.getName());
    }

    @Test
    void shouldRegisterJwtValidationConstructorClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtValidationConstructorClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.TokenValidator"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.IssuerConfigResolver"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.security.SecurityEventCounter"));
        assertFalse(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.IssuerConfig"));
        assertFalse(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.ParserConfig"));
    }

    @Test
    void shouldRegisterJwtConfigurationClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtConfigurationClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.IssuerConfig"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.ParserConfig"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig"));
        // These classes are in the constructor group
        assertFalse(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.TokenValidator"));
    }

    @Test
    void shouldRegisterJwtTokenContentClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtTokenContentClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.domain.token.AccessTokenContent"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.domain.claim.ClaimValue"));
        // Claim mappers are in separate group
        assertFalse(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.domain.claim.mapper.IdentityMapper"));
    }

    @Test
    void shouldRegisterJwtClaimMapperClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtClaimMapperClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.domain.claim.mapper.IdentityMapper"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.domain.claim.mapper.ScopeMapper"));
        // Token content classes are in separate group
        assertFalse(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.domain.token.AccessTokenContent"));
    }

    // REMOVED: registerBearerTokenClassesForReflection test
    // This follows the standard: application-level classes use annotations,
    // infrastructure/library classes use deployment processor

    @Test
    void shouldRegisterRuntimeInitializedClasses() {
        // Act
        RuntimeInitializedClassBuildItem runtimeItem = processor.runtimeInitializedClasses();

        // Assert
        assertNotNull(runtimeItem);
        assertEquals("de.cuioss.jwt.validation.jwks.http.HttpJwksLoader", runtimeItem.getClassName());
    }

    @Test
    void shouldCreateAdditionalBeans() {
        // Act
        AdditionalBeanBuildItem beanItem = processor.additionalBeans();

        // Assert
        assertNotNull(beanItem);
    }

    @Test
    void shouldCreateDevUICard() {
        // Act
        CardPageBuildItem cardItem = processor.createJwtDevUICard();

        // Assert
        assertNotNull(cardItem);
        assertFalse(cardItem.getPages().isEmpty());
        assertEquals(4, cardItem.getPages().size());
    }

    @Test
    void shouldCreateDevUIJsonRPCService() {
        // Act
        JsonRPCProvidersBuildItem jsonRpcItem = processor.createJwtDevUIJsonRPCService();

        // Assert
        assertNotNull(jsonRpcItem);
    }

    @Test
    void shouldRegisterUnremovableBeans() {
        // Arrange
        List<UnremovableBeanBuildItem> unremovableBeans = new ArrayList<>();
        BuildProducer<UnremovableBeanBuildItem> producer = unremovableBeans::add;

        // Act
        processor.registerUnremovableBeans(producer);

        // Assert - We have 3 core unremovable beans: TokenValidator, JwtMetricsCollector, MeterRegistry
        assertEquals(1, unremovableBeans.size());
    }
}
