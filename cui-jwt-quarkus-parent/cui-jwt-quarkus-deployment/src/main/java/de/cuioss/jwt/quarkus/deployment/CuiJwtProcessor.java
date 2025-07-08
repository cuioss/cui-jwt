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

import de.cuioss.jwt.quarkus.producer.TokenValidatorProducer;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.IssuerConfigResolver;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
// Claim handling classes
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.ClaimValueType;
// Claim mappers
import de.cuioss.jwt.validation.domain.claim.mapper.IdentityMapper;
import de.cuioss.jwt.validation.domain.claim.mapper.JsonCollectionMapper;
import de.cuioss.jwt.validation.domain.claim.mapper.OffsetDateTimeMapper;
import de.cuioss.jwt.validation.domain.claim.mapper.ScopeMapper;
import de.cuioss.jwt.validation.domain.claim.mapper.StringSplitterMapper;
// Domain token classes
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.BaseTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.domain.token.MinimalTokenContent;
import de.cuioss.jwt.validation.domain.token.RefreshTokenContent;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoader;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig;
// JWKS classes
import de.cuioss.jwt.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.jwks.parser.JwksParser;
import de.cuioss.jwt.validation.pipeline.DecodedJwt;
// JWT validation pipeline classes
import de.cuioss.jwt.validation.pipeline.NonValidatingJwtParser;
import de.cuioss.jwt.validation.pipeline.TokenBuilder;
import de.cuioss.jwt.validation.pipeline.TokenClaimValidator;
import de.cuioss.jwt.validation.pipeline.TokenHeaderValidator;
import de.cuioss.jwt.validation.pipeline.TokenSignatureValidator;
import de.cuioss.jwt.validation.security.JwkAlgorithmPreferences;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
// Security and algorithm classes
import de.cuioss.jwt.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import lombok.NonNull;
import org.jboss.jandex.DotName;

import static de.cuioss.jwt.quarkus.deployment.CuiJwtQuarkusDeploymentLogMessages.INFO;

/**
 * Processor for the CUI JWT Quarkus extension.
 * <p>
 * This class handles the build-time processing for the extension, including
 * registering the feature, setting up reflection configuration, and providing
 * DevUI integration.
 * </p>
 */
public class CuiJwtProcessor {

    /**
     * The feature name for the CUI JWT extension.
     */
    private static final String FEATURE = "cui-jwt";

    /**
     * Logger for build-time processing.
     */
    private static final CuiLogger LOGGER = new CuiLogger(CuiJwtProcessor.class);

    /**
     * Register the CUI JWT feature.
     *
     * @return A {@link FeatureBuildItem} for the CUI JWT feature
     */
    @BuildStep
    @NonNull
    public FeatureBuildItem feature() {
        LOGGER.info(INFO.CUI_JWT_FEATURE_REGISTERED::format);
        return new FeatureBuildItem(FEATURE);
    }


    /**
     * Register JWT validation classes for reflection.
     *
     * @return A {@link ReflectiveClassBuildItem} for the JWT validation classes
     */
    @BuildStep
    @NonNull
    public ReflectiveClassBuildItem registerJwtValidationClassesForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Core validation components
                TokenValidator.class,
                IssuerConfig.class,
                IssuerConfigResolver.class,
                ParserConfig.class,
                HttpJwksLoaderConfig.class,
                SecurityEventCounter.class,
                TokenValidatorProducer.class)
                .methods(true)
                .fields(true)
                .constructors(true)
                .build();
    }

    /**
     * Register JWT validation pipeline classes for reflection.
     * These are the performance-critical classes in the validation pipeline.
     *
     * @return A {@link ReflectiveClassBuildItem} for JWT validation pipeline classes
     */
    @BuildStep
    @NonNull
    public ReflectiveClassBuildItem registerJwtPipelineClassesForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Critical validation pipeline classes (50-60% of processing time)
                NonValidatingJwtParser.class,
                TokenSignatureValidator.class,
                TokenHeaderValidator.class,
                TokenClaimValidator.class,
                TokenBuilder.class,
                DecodedJwt.class,
                // JWKS loading classes (10-15% of processing time)
                HttpJwksLoader.class,
                JWKSKeyLoader.class,
                KeyInfo.class,
                JwksParser.class,
                // Security and algorithm classes
                SignatureAlgorithmPreferences.class,
                JwkAlgorithmPreferences.class)
                .methods(true)
                .fields(true)
                .constructors(true)
                .build();
    }

    /**
     * Register JWT domain and token classes for reflection.
     * These classes are used for token content processing and claim mapping.
     *
     * @return A {@link ReflectiveClassBuildItem} for JWT domain classes
     */
    @BuildStep
    @NonNull
    public ReflectiveClassBuildItem registerJwtDomainClassesForReflection() {
        return ReflectiveClassBuildItem.builder(
                // Token content classes
                AccessTokenContent.class,
                IdTokenContent.class,
                RefreshTokenContent.class,
                TokenContent.class,
                BaseTokenContent.class,
                MinimalTokenContent.class,
                // Claim handling classes  
                ClaimValue.class,
                ClaimName.class,
                ClaimValueType.class,
                // Claim mappers for performance optimization
                IdentityMapper.class,
                JsonCollectionMapper.class,
                OffsetDateTimeMapper.class,
                ScopeMapper.class,
                StringSplitterMapper.class)
                .methods(true)
                .fields(true)
                .constructors(true)
                .build();
    }


    /**
     * Register classes that need to be initialized at runtime.
     *
     * @return A {@link RuntimeInitializedClassBuildItem} for classes that need runtime initialization
     */
    @BuildStep
    @NonNull
    public RuntimeInitializedClassBuildItem runtimeInitializedClasses() {
        return new RuntimeInitializedClassBuildItem(HttpJwksLoader.class.getName());
    }

    /**
     * Register native image resources that JWT validation needs access to.
     * This ensures configuration files and other resources are included in the native image.
     */
    @BuildStep
    public void registerJwtValidationResources(BuildProducer<NativeImageResourceBuildItem> resourceProducer) {
        // Include any JWT validation configuration files that might exist
        resourceProducer.produce(new NativeImageResourceBuildItem("META-INF/services/de.cuioss.jwt.validation.jwks.JwksLoader"));
        resourceProducer.produce(new NativeImageResourceBuildItem("META-INF/services/de.cuioss.jwt.validation.domain.claim.mapper.ClaimMapper"));
    }


    /**
     * Register additional CDI beans for JWT validation.
     *
     * @return A {@link AdditionalBeanBuildItem} for CDI beans that need explicit registration
     */
    @BuildStep
    @NonNull
    public AdditionalBeanBuildItem additionalBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(TokenValidatorProducer.class)
                .setUnremovable()
                .build();
    }

    /**
     * Register TokenValidator as an unremovable bean to ensure it's available for injection.
     * This is critical for native image compilation where CDI discovery can be limited.
     *
     * @param unremovableBeans producer for unremovable bean build items
     */
    @BuildStep
    public void registerUnremovableBeans(BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        // Ensure TokenValidator is never removed from the CDI container
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(
                DotName.createSimple(TokenValidator.class.getName())
        ));

        // Ensure the producer is never removed
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(
                DotName.createSimple(TokenValidatorProducer.class.getName())
        ));

    }


    /**
     * Create DevUI card page for JWT validation monitoring and debugging.
     *
     * @return A {@link CardPageBuildItem} for the JWT DevUI card
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    @NonNull
    public CardPageBuildItem createJwtDevUICard() {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();

        // JWT Validation Status page
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:shield-check")
                .title("JWT Validation Status")
                .componentLink("components/qwc-jwt-validation-status.js")
                .staticLabel("View Status"));

        // JWKS Endpoint Monitoring page
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:key")
                .title("JWKS Endpoints")
                .componentLink("components/qwc-jwks-endpoints.js")
                .dynamicLabelJsonRPCMethodName("getJwksStatus"));

        // Token Debugging Tools page
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:bug")
                .title("Token Debugger")
                .componentLink("components/qwc-jwt-debugger.js")
                .staticLabel("Debug Tokens"));

        // Configuration Overview page
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:cog")
                .title("Configuration")
                .componentLink("components/qwc-jwt-config.js")
                .staticLabel("View Config"));

        return cardPageBuildItem;
    }

    /**
     * Register JSON-RPC providers for DevUI runtime data access.
     *
     * @return A {@link JsonRPCProvidersBuildItem} for JWT DevUI JSON-RPC methods
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    @NonNull
    public JsonRPCProvidersBuildItem createJwtDevUIJsonRPCService() {
        return new JsonRPCProvidersBuildItem("CuiJwtDevUI", CuiJwtDevUIJsonRPCService.class);
    }

}
