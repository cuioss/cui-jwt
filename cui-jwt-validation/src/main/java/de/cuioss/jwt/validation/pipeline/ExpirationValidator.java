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

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Validator for JWT expiration and time-based claims.
 * <p>
 * This class validates:
 * <ul>
 *   <li>Expiration time (exp) - tokens must not be expired</li>
 *   <li>Not before time (nbf) - tokens must not be used before their valid time</li>
 * </ul>
 * <p>
 * The validator includes a 60-second clock skew tolerance for the not-before validation
 * to account for time differences between token issuer and validator.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor
public class ExpirationValidator {

    private static final CuiLogger LOGGER = new CuiLogger(ExpirationValidator.class);
    private static final int CLOCK_SKEW_SECONDS = 60;

    @NonNull
    private final SecurityEventCounter securityEventCounter;

    /**
     * Validates that the token is not expired.
     *
     * @param token the token to validate
     * @throws TokenValidationException if the token is expired
     */
    public void validateNotExpired(TokenContent token) {
        LOGGER.debug("validate expiration. Can be done directly, because ", token);
        if (token.isExpired()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_EXPIRED::format);
            securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EXPIRED);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.TOKEN_EXPIRED,
                    "Token is expired. Current time: " + OffsetDateTime.now() + " (with " + CLOCK_SKEW_SECONDS + "s clock skew tolerance)"
            );
        }
        LOGGER.debug("Token is not expired");
    }

    /**
     * Validates the "not before time" claim.
     * <p>
     * The "nbf" (not before) claim identifies the time before which the JWT must not be accepted for processing.
     * This claim is optional, so if it's not present, the validation passes.
     * <p>
     * If the claim is present, this method checks if the token's not-before time is more than 60 seconds
     * in the future. This 60-second window allows for clock skew between the token issuer and the token validator.
     * If the not-before time is more than 60 seconds in the future, the token is considered invalid.
     * If the not-before time is in the past or less than 60 seconds in the future, the token is considered valid.
     *
     * @param token the JWT claims
     * @throws TokenValidationException if the "not before" time is invalid
     */
    public void validateNotBefore(TokenContent token) {
        var notBefore = token.getNotBefore();
        if (notBefore.isEmpty()) {
            LOGGER.debug("Not before claim is optional, so if it's not present, validation passes");
            return;
        }

        if (notBefore.get().isAfter(OffsetDateTime.now().plusSeconds(CLOCK_SKEW_SECONDS))) {
            LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_NBF_FUTURE::format);
            securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_NBF_FUTURE);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.TOKEN_NBF_FUTURE,
                    "Token not valid yet: not before time is more than 60 seconds in the future. Not before time: " + notBefore.get() + ", Current time: " + OffsetDateTime.now() + " (with " + CLOCK_SKEW_SECONDS + "s clock skew tolerance)"
            );
        }
        LOGGER.debug("Not before claim is present, and not more than 60 seconds in the future");
    }
}