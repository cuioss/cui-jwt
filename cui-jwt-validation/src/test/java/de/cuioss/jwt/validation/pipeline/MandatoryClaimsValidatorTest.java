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
package de.cuioss.jwt.validation.pipeline;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link MandatoryClaimsValidator}.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("MandatoryClaimsValidator")
class MandatoryClaimsValidatorTest {

    private SecurityEventCounter securityEventCounter;
    private MandatoryClaimsValidator validator;

    @BeforeEach
    void setup() {
        securityEventCounter = new SecurityEventCounter();

        // Create a default IssuerConfig for testing (claimSubOptional = false by default)
        IssuerConfig defaultIssuerConfig = IssuerConfig.builder()
            .issuerIdentifier("https://test-issuer.example.com")
            .jwksContent("{\"keys\":[]}")
            .build();

        validator = new MandatoryClaimsValidator(defaultIssuerConfig, securityEventCounter);
    }

    @Test
    @DisplayName("Should pass validation for access token with all mandatory claims")
    void shouldPassValidationForAccessTokenWithAllMandatoryClaims() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), "test@example.com");

        assertDoesNotThrow(() -> validator.validateMandatoryClaims(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should pass validation for ID token with all mandatory claims")
    void shouldPassValidationForIdTokenWithAllMandatoryClaims() {
        TestTokenHolder tokenHolder = TestTokenGenerators.idTokens().next();
        IdTokenContent token = new IdTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken());

        assertDoesNotThrow(() -> validator.validateMandatoryClaims(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should fail validation when subject claim is missing")
    void shouldFailValidationWhenSubjectClaimIsMissing() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.SUBJECT.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateMandatoryClaims(token));
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().contains("Missing mandatory claims"));
        assertTrue(exception.getMessage().contains(ClaimName.SUBJECT.getName()));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should fail validation when expiration time claim is missing")
    void shouldFailValidationWhenExpirationTimeClaimIsMissing() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.EXPIRATION.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateMandatoryClaims(token));
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().contains("Missing mandatory claims"));
        assertTrue(exception.getMessage().contains(ClaimName.EXPIRATION.getName()));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should fail validation when issued at claim is missing")
    void shouldFailValidationWhenIssuedAtClaimIsMissing() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.ISSUED_AT.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateMandatoryClaims(token));
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().contains("Missing mandatory claims"));
        assertTrue(exception.getMessage().contains(ClaimName.ISSUED_AT.getName()));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should pass validation when claim is present but empty string")
    void shouldPassValidationWhenClaimIsPresentButEmptyString() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.put(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(""));
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        assertDoesNotThrow(() -> validator.validateMandatoryClaims(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should fail validation when multiple mandatory claims are missing")
    void shouldFailValidationWhenMultipleMandatoryClaimsAreMissing() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.SUBJECT.getName());
        claims.remove(ClaimName.EXPIRATION.getName());
        claims.remove(ClaimName.ISSUED_AT.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateMandatoryClaims(token));
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().contains("Missing mandatory claims"));
        assertTrue(exception.getMessage().contains(ClaimName.SUBJECT.getName()));
        assertTrue(exception.getMessage().contains(ClaimName.EXPIRATION.getName()));
        assertTrue(exception.getMessage().contains(ClaimName.ISSUED_AT.getName()));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should validate different token types have different mandatory claims")
    void shouldValidateDifferentTokenTypesHaveDifferentMandatoryClaims() {
        TestTokenHolder accessTokenHolder = TestTokenGenerators.accessTokens().next();
        TestTokenHolder idTokenHolder = TestTokenGenerators.idTokens().next();

        AccessTokenContent accessToken = new AccessTokenContent(accessTokenHolder.getClaims(), accessTokenHolder.getRawToken(), "test@example.com");
        IdTokenContent idToken = new IdTokenContent(idTokenHolder.getClaims(), idTokenHolder.getRawToken());

        assertNotEquals(TokenType.ACCESS_TOKEN.getMandatoryClaims(), TokenType.ID_TOKEN.getMandatoryClaims());

        assertDoesNotThrow(() -> validator.validateMandatoryClaims(accessToken));
        assertDoesNotThrow(() -> validator.validateMandatoryClaims(idToken));
    }

    @Test
    @DisplayName("Should include available claims in error message")
    void shouldIncludeAvailableClaimsInErrorMessage() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.SUBJECT.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateMandatoryClaims(token));
        assertTrue(exception.getMessage().contains("Available claims:"));
        assertTrue(exception.getMessage().contains("Please ensure the token includes all required claims"));
    }

    @Test
    @DisplayName("Should skip subject claim validation when claimSubOptional is true")
    void shouldSkipSubjectClaimValidationWhenClaimSubOptionalIsTrue() {
        // Create IssuerConfig with claimSubOptional=true
        IssuerConfig issuerConfigWithOptionalSub = IssuerConfig.builder()
                .issuerIdentifier("https://test-issuer.example.com")
                .jwksContent("{\"keys\":[]}")
                .claimSubOptional(true)
                .build();

        MandatoryClaimsValidator validatorWithOptionalSub = new MandatoryClaimsValidator(issuerConfigWithOptionalSub, securityEventCounter);

        // Create token without subject claim
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.SUBJECT.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        // Should pass validation even without subject claim
        assertDoesNotThrow(() -> validatorWithOptionalSub.validateMandatoryClaims(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should still validate other mandatory claims when claimSubOptional is true")
    void shouldStillValidateOtherMandatoryClaimsWhenClaimSubOptionalIsTrue() {
        // Create IssuerConfig with claimSubOptional=true
        IssuerConfig issuerConfigWithOptionalSub = IssuerConfig.builder()
                .issuerIdentifier("https://test-issuer.example.com")
                .jwksContent("{\"keys\":[]}")
                .claimSubOptional(true)
                .build();

        MandatoryClaimsValidator validatorWithOptionalSub = new MandatoryClaimsValidator(issuerConfigWithOptionalSub, securityEventCounter);

        // Create token without subject claim AND expiration claim
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.SUBJECT.getName());
        claims.remove(ClaimName.EXPIRATION.getName()); // Remove non-optional claim
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        // Should fail validation due to missing expiration claim (even though subject is optional)
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validatorWithOptionalSub.validateMandatoryClaims(token));
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().contains("Missing mandatory claims"));
        assertTrue(exception.getMessage().contains(ClaimName.EXPIRATION.getName()));
        assertFalse(exception.getMessage().contains(ClaimName.SUBJECT.getName())); // Subject should not be in missing claims
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }

    @Test
    @DisplayName("Should enforce subject claim validation when claimSubOptional is false (default)")
    void shouldEnforceSubjectClaimValidationWhenClaimSubOptionalIsFalse() {
        // Explicitly create IssuerConfig with claimSubOptional=false to test configuration
        IssuerConfig issuerConfigWithMandatorySub = IssuerConfig.builder()
                .issuerIdentifier("https://test-issuer.example.com")
                .jwksContent("{\"keys\":[]}")
                .claimSubOptional(false)  // Explicitly set to false (though it's the default)
                .build();

        MandatoryClaimsValidator validatorWithMandatorySub = new MandatoryClaimsValidator(issuerConfigWithMandatorySub, securityEventCounter);

        // Create token without subject claim
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.SUBJECT.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        // Should fail validation due to missing subject claim when claimSubOptional=false
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validatorWithMandatorySub.validateMandatoryClaims(token));
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().contains("Missing mandatory claims"));
        assertTrue(exception.getMessage().contains(ClaimName.SUBJECT.getName()));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));

        // Verify that the same token would pass with claimSubOptional=true (contrast test)
        IssuerConfig issuerConfigWithOptionalSub = IssuerConfig.builder()
                .issuerIdentifier("https://test-issuer.example.com")
                .jwksContent("{\"keys\":[]}")
                .claimSubOptional(true)
                .build();
        MandatoryClaimsValidator validatorWithOptionalSub = new MandatoryClaimsValidator(issuerConfigWithOptionalSub, new SecurityEventCounter());

        // This should pass with claimSubOptional=true
        assertDoesNotThrow(() -> validatorWithOptionalSub.validateMandatoryClaims(token));
    }

    @Test
    @DisplayName("Should not affect ID token validation when claimSubOptional is true")
    void shouldNotAffectIdTokenValidationWhenClaimSubOptionalIsTrue() {
        // Create IssuerConfig with claimSubOptional=true
        IssuerConfig issuerConfigWithOptionalSub = IssuerConfig.builder()
                .issuerIdentifier("https://test-issuer.example.com")
                .jwksContent("{\"keys\":[]}")
                .claimSubOptional(true)
                .build();

        MandatoryClaimsValidator validatorWithOptionalSub = new MandatoryClaimsValidator(issuerConfigWithOptionalSub, securityEventCounter);

        // Create ID token without subject claim
        TestTokenHolder tokenHolder = TestTokenGenerators.idTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.SUBJECT.getName());
        IdTokenContent token = new IdTokenContent(claims, tokenHolder.getRawToken());

        // Should pass validation for ID tokens as well when claimSubOptional is true
        assertDoesNotThrow(() -> validatorWithOptionalSub.validateMandatoryClaims(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.MISSING_CLAIM));
    }
}