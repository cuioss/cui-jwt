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
package de.cuioss.jwt.validation.domain.token;

import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import lombok.NonNull;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Base interface for JWT Token content.
 * <p>
 * This interface defines the common contract for all JWT token objects,
 * providing structured access to token claims and metadata. It extends
 * {@link MinimalTokenContent} to include basic token information.
 * <p>
 * The interface provides:
 * <ul>
 *   <li>Access to all claims in the token via {@link #getClaims()}</li>
 *   <li>Convenience methods for common claims (subject, issuer, expiration time)</li>
 *   <li>Type-safe claim retrieval</li>
 *   <li>Expiration checking</li>
 * </ul>
 * <p>
 * JWT tokens implementing this interface follow the standards defined in
 * <a href="https://tools.ietf.org/html/rfc7519">RFC 7519</a> for claim names
 * and semantic behavior.
 * <p>
 * For more details on token structure, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#token-structure">Token Structure</a>
 * specification.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public interface TokenContent extends MinimalTokenContent {

    /**
     * Gets all claims in this token.
     *
     * @return a map of claim names to claim objects
     */
    @NonNull
    Map<String, ClaimValue> getClaims();

    /**
     * Gets a specific claim by name.
     *
     * @param name the claim name
     * @return an Optional containing the claim if present, or empty otherwise
     */
    default Optional<ClaimValue> getClaimOption(ClaimName name) {
        return Optional.ofNullable(getClaims().get(name.getName()));
    }

    /**
     * Gets the issuer claim value.
     * <p>
     * Since 'iss' is a mandatory claim, this method will never return null.
     *
     * @return the issuer
     * @throws IllegalStateException if the issuer claim is not present (should never happen
     *                               for a properly constructed validation)
     */
    @NonNull
    default String getIssuer() {
        return getClaimOption(ClaimName.ISSUER)
                .map(ClaimValue::getOriginalString)
                .orElseThrow(() -> new IllegalStateException("Issuer claim not presentin token"));
    }

    /**
     * Gets the subject claim value.
     * <p>
     * Since 'sub' is a mandatory claim for most validation types, this method will never return null.
     *
     * @return the subject, or throws exception if it's not present
     * @throws IllegalStateException if the subject claim is not present
     */
    @NonNull
    default String getSubject() {
        return getClaimOption(ClaimName.SUBJECT)
                .map(ClaimValue::getOriginalString)
                .orElseThrow(() -> new IllegalStateException("Subject claim not presentin token"));
    }

    /**
     * Gets the expiration time claim value.
     * <p>
     * Since 'exp' is a mandatory claim, this method will never return null.
     *
     * @return the expiration time
     * @throws IllegalStateException if the expiration claim is not present (should never happen
     *                               for a properly constructed validation)
     */
    @NonNull
    default OffsetDateTime getExpirationTime() {
        return getClaimOption(ClaimName.EXPIRATION)
                .map(ClaimValue::getDateTime)
                .orElseThrow(() -> new IllegalStateException("ExpirationTime claim not presentin token"));
    }

    /**
     * Gets the issued at time claim value.
     * <p>
     * Since 'iat' is a mandatory claim, this method will never return null.
     *
     * @return the issued at time
     * @throws IllegalStateException if the issued at claim is not present (should never happen
     *                               for a properly constructed validation)
     */
    @NonNull
    default OffsetDateTime getIssuedAtTime() {
        return getClaimOption(ClaimName.ISSUED_AT)
                .map(ClaimValue::getDateTime)
                .orElseThrow(() -> new IllegalStateException("issued at time claim claim not presentin token"));
    }

    /**
     * Gets the optional not before claim value.
     * <p>
     * Since 'nbf' is optional, this method may return an empty Optional.
     *
     * @return the 'not before time''
     */
    default Optional<OffsetDateTime> getNotBefore() {
        return getClaimOption(ClaimName.NOT_BEFORE)
                .map(ClaimValue::getDateTime);
    }

    /**
     * Checks if the validation has expired.
     *
     * @return true if the validation has expired, false otherwise
     */
    default boolean isExpired() {
        return getExpirationTime().isBefore(OffsetDateTime.now());
    }
}