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
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.collect.MoreCollections;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;
import lombok.NonNull;

import java.util.Set;

/**
 * Validator for JWT claims as defined in RFC 7519.
 * <p>
 * This class validates the following mandatory claims:
 * <ul>
 *   <li>Subject (sub)</li>
 *   <li>Expiration Time (exp)</li>
 *   <li>Issued At (iat)</li>
 *   <li>Not Before (nbf) - if present</li>
 *   <li>Audience (aud) - if expected audience is provided</li>
 *   <li>Authorized Party (azp) - if expected client ID is provided</li>
 * </ul>
 * <p>
 * The validator logs appropriate warning messages for validation failures.
 * <p>
 * The azp claim validation is an important security measure to prevent client confusion attacks
 * where tokens issued for one client are used with a different client.
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/security.adoc">Security Specification</a>
 * <p>
 * Note: Issuer (iss) validation is handled by {@link TokenHeaderValidator}.
 * <p>
 * This class uses composition to delegate specific validation responsibilities to specialized validators:
 * <ul>
 *   <li>{@link AudienceValidator} - for audience claim validation</li>
 *   <li>{@link ExpirationValidator} - for expiration and not-before validation</li>
 *   <li>{@link MandatoryClaimsValidator} - for mandatory claims validation</li>
 *   <li>{@link AuthorizedPartyValidator} - for authorized party validation</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class TokenClaimValidator {

    private static final CuiLogger LOGGER = new CuiLogger(TokenClaimValidator.class);

    @Getter
    private final Set<String> expectedAudience;

    @Getter
    private final Set<String> expectedClientId;

    private final AudienceValidator audienceValidator;
    private final ExpirationValidator expirationValidator;
    private final MandatoryClaimsValidator mandatoryClaimsValidator;
    private final AuthorizedPartyValidator authorizedPartyValidator;

    /**
     * Constructs a TokenClaimValidator with the specified IssuerConfig.
     *
     * @param issuerConfig the issuer configuration containing expected audience and client ID
     * @param securityEventCounter the counter for security events
     */
    public TokenClaimValidator(@NonNull IssuerConfig issuerConfig, @NonNull SecurityEventCounter securityEventCounter) {
        this(issuerConfig.getExpectedAudience(), issuerConfig.getExpectedClientId(), securityEventCounter);
    }

    /**
     * Constructs a TokenClaimValidator with the specified expected audience and client ID.
     *
     * @param expectedAudience the expected audience values
     * @param expectedClientId the expected client ID values
     * @param securityEventCounter the counter for security events
     */
    public TokenClaimValidator(Set<String> expectedAudience, Set<String> expectedClientId, @NonNull SecurityEventCounter securityEventCounter) {
        this.expectedAudience = expectedAudience;
        this.expectedClientId = expectedClientId;

        this.audienceValidator = new AudienceValidator(expectedAudience, securityEventCounter);
        this.expirationValidator = new ExpirationValidator(securityEventCounter);
        this.mandatoryClaimsValidator = new MandatoryClaimsValidator(securityEventCounter);
        this.authorizedPartyValidator = new AuthorizedPartyValidator(expectedClientId, securityEventCounter);

        if (MoreCollections.isEmpty(expectedAudience)) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_RECOMMENDED_ELEMENT.format("expectedAudience"));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_RECOMMENDED_ELEMENT);
        }

        if (MoreCollections.isEmpty(expectedClientId)) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_RECOMMENDED_ELEMENT.format("azp claim validation (expectedClientId)"));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_RECOMMENDED_ELEMENT);
        }
    }

    /**
     * Validates a token against expected values for issuer, audience, and client ID.
     *
     * @param token the token to validate
     * @return The validated token content
     * @throws TokenValidationException if validation fails
     */
    public TokenContent validate(@NonNull TokenContent token) {
        LOGGER.trace("Validating token: %s", token);
        mandatoryClaimsValidator.validateMandatoryClaims(token);
        audienceValidator.validateAudience(token);
        authorizedPartyValidator.validateAuthorizedParty(token);
        expirationValidator.validateNotBefore(token);
        expirationValidator.validateNotExpired(token);
        LOGGER.debug("Token is valid");
        return token;
    }

}
