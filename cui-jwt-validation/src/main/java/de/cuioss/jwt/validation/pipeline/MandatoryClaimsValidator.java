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
package de.cuioss.jwt.validation.pipeline;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Validator for mandatory JWT claims based on token type.
 * <p>
 * This class validates that all mandatory claims for a specific token type are present and properly set.
 * The mandatory claims are defined by the {@link de.cuioss.jwt.validation.TokenType} and vary between
 * access tokens, ID tokens, and refresh tokens.
 * <p>
 * The validator checks both claim presence and claim value validity.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor
public class MandatoryClaimsValidator {

    private static final CuiLogger LOGGER = new CuiLogger(MandatoryClaimsValidator.class);

    @NonNull
    private final SecurityEventCounter securityEventCounter;

    /**
     * Validates whether all mandatory claims for the current token type are present and set.
     *
     * @param tokenContent the token content to validate
     * @throws TokenValidationException if any mandatory claims are missing
     */
    public void validateMandatoryClaims(TokenContent tokenContent) {
        var mandatoryNames = tokenContent.getTokenType().getMandatoryClaims().stream()
                .map(ClaimName::getName)
                .collect(Collectors.toSet());

        LOGGER.debug("%s, verifying mandatory claims: %s", tokenContent.getTokenType(), mandatoryNames);

        SortedSet<String> missingClaims = new TreeSet<>();

        for (var claimName : mandatoryNames) {
            if (!tokenContent.getClaims().containsKey(claimName)) {
                missingClaims.add(claimName);
            } else {
                ClaimValue claimValue = tokenContent.getClaims().get(claimName);
                if (!claimValue.isPresent()) {
                    var claimNameEnum = ClaimName.fromString(claimName);
                    if (claimNameEnum.isPresent()) {
                        LOGGER.debug("Claim %s is present but not set as expected: %s. Specification: %s",
                                claimName, claimValue, claimNameEnum.get().getSpec());
                    } else {
                        LOGGER.debug("Claim %s is present but not set as expected: %s", claimName, claimValue);
                    }
                    missingClaims.add(claimName);
                }
            }
        }

        if (!missingClaims.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format(missingClaims));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);

            // Build enhanced error message with claim specifications
            StringBuilder errorMessage = new StringBuilder("Missing mandatory claims: ").append(missingClaims);
            StringBuilder claimSpecs = new StringBuilder();

            for (String missingClaim : missingClaims) {
                var claimNameEnum = ClaimName.fromString(missingClaim);
                if (claimNameEnum.isPresent()) {
                    claimSpecs.append("\n- ").append(missingClaim).append(": ").append(claimNameEnum.get().getSpec());
                }
            }

            if (claimSpecs.length() > 0) {
                errorMessage.append("\n\nClaim specifications:").append(claimSpecs);
            }

            errorMessage.append("\n\nAvailable claims: ").append(tokenContent.getClaims().keySet())
                    .append(". Please ensure the token includes all required claims.");

            throw new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    errorMessage.toString()
            );
        } else {
            LOGGER.debug("All mandatory claims are present and set as expected");
        }
    }
}