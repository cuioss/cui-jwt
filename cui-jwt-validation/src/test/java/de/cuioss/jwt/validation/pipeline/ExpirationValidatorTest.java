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

import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link ExpirationValidator}.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("ExpirationValidator")
class ExpirationValidatorTest {

    private SecurityEventCounter securityEventCounter;
    private ExpirationValidator validator;

    @BeforeEach
    void setup() {
        securityEventCounter = new SecurityEventCounter();
        validator = new ExpirationValidator(securityEventCounter);
    }

    @Test
    @DisplayName("Should pass validation for non-expired token")
    void shouldPassValidationForNonExpiredToken() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime futureExpiration = OffsetDateTime.now().plusHours(1);
        tokenHolder.withClaim(ClaimName.EXPIRATION.getName(),
                ClaimValue.forDateTime(String.valueOf(futureExpiration.toEpochSecond()), futureExpiration));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), "test@example.com");

        assertDoesNotThrow(() -> validator.validateNotExpired(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("Should fail validation for expired token")
    void shouldFailValidationForExpiredToken() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime pastExpiration = OffsetDateTime.now().minusHours(1);
        tokenHolder.withClaim(ClaimName.EXPIRATION.getName(),
                ClaimValue.forDateTime(String.valueOf(pastExpiration.toEpochSecond()), pastExpiration));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), "test@example.com");

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateNotExpired(token));
        assertEquals(SecurityEventCounter.EventType.TOKEN_EXPIRED, exception.getEventType());
        assertTrue(exception.getMessage().contains("Token is expired"));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("Should pass validation when no not-before claim present")
    void shouldPassValidationWhenNoNotBeforeClaimPresent() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        Map<String, ClaimValue> claims = new HashMap<>(tokenHolder.getClaims());
        claims.remove(ClaimName.NOT_BEFORE.getName());
        AccessTokenContent token = new AccessTokenContent(claims, tokenHolder.getRawToken(), "test@example.com");

        assertDoesNotThrow(() -> validator.validateNotBefore(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should pass validation for not-before time in the past")
    void shouldPassValidationForNotBeforeTimeInThePast() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime pastNotBefore = OffsetDateTime.now().minusHours(1);
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(pastNotBefore.toEpochSecond()), pastNotBefore));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), "test@example.com");

        assertDoesNotThrow(() -> validator.validateNotBefore(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should pass validation for not-before time within clock skew tolerance")
    void shouldPassValidationForNotBeforeTimeWithinClockSkewTolerance() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime nearFutureNotBefore = OffsetDateTime.now().plusSeconds(30);
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(nearFutureNotBefore.toEpochSecond()), nearFutureNotBefore));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), "test@example.com");

        assertDoesNotThrow(() -> validator.validateNotBefore(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should fail validation for not-before time beyond clock skew tolerance")
    void shouldFailValidationForNotBeforeTimeBeyondClockSkewTolerance() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime farFutureNotBefore = OffsetDateTime.now().plusSeconds(120);
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(farFutureNotBefore.toEpochSecond()), farFutureNotBefore));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), "test@example.com");

        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateNotBefore(token));
        assertEquals(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE, exception.getEventType());
        assertTrue(exception.getMessage().contains("Token not valid yet"));
        assertTrue(exception.getMessage().contains("more than 60 seconds in the future"));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should pass validation at exact clock skew boundary")
    void shouldPassValidationAtExactClockSkewBoundary() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime exactBoundaryNotBefore = OffsetDateTime.now().plusSeconds(60);
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(exactBoundaryNotBefore.toEpochSecond()), exactBoundaryNotBefore));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), "test@example.com");

        assertDoesNotThrow(() -> validator.validateNotBefore(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }

    @Test
    @DisplayName("Should handle edge case of current time as not-before")
    void shouldHandleEdgeCaseOfCurrentTimeAsNotBefore() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        OffsetDateTime currentTime = OffsetDateTime.now();
        tokenHolder.withClaim(ClaimName.NOT_BEFORE.getName(),
                ClaimValue.forDateTime(String.valueOf(currentTime.toEpochSecond()), currentTime));
        AccessTokenContent token = new AccessTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), "test@example.com");

        assertDoesNotThrow(() -> validator.validateNotBefore(token));
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE));
    }
}