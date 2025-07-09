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
    void shouldRegisterJwtValidationClassesForReflection() {
        // Act
        ReflectiveClassBuildItem reflectiveItem = processor.registerJwtValidationClassesForReflection();

        // Assert
        assertNotNull(reflectiveItem);
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.TokenValidator"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.IssuerConfig"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.ParserConfig"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.validation.security.SecurityEventCounter"));
        assertTrue(reflectiveItem.getClassNames().contains("de.cuioss.jwt.quarkus.producer.TokenValidatorProducer"));
    }

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
        assertTrue(beanItem.getBeanClasses().contains("de.cuioss.jwt.quarkus.producer.TokenValidatorProducer"));
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

        // Assert - We now have 3 unremovable beans: TokenValidator, TokenValidatorProducer, BearerTokenProducer
        assertEquals(3, unremovableBeans.size());
    }
}
