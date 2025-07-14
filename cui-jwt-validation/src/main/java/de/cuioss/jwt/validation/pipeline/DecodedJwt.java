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
import jakarta.json.JsonObject;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Record representing a decoded JWT token.
 * <p>
 * This record holds the parsed components of a JWT token after Base64 decoding and JSON parsing,
 * but before any validation occurs. It contains:
 * <ul>
 *   <li>The decoded header as a JsonObject</li>
 *   <li>The decoded payload (body) as a JsonObject</li>
 *   <li>The signature part as a String</li>
 *   <li>Convenience methods for accessing common JWT fields</li>
 *   <li>The original token parts and raw token string</li>
 * </ul>
 * <p>
 * <strong>Security Note:</strong> This record is not guaranteed to contain a validated token.
 * It is usually created by {@link NonValidatingJwtParser} and should be passed to
 * {@link TokenHeaderValidator}, {@link TokenSignatureValidator}, and {@link TokenClaimValidator}
 * for proper validation.
 * <p>
 * The record provides immutability guarantees and value-based equality by default, making it
 * ideal for representing decoded JWT data in the validation pipeline.
 * <p>
 * For more details on the token validation process, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#token-validation-pipeline">Token Validation Pipeline</a>
 *
 * @param header the decoded header as a JsonObject
 * @param body the decoded payload (body) as a JsonObject
 * @param signature the signature part as a String
 * @param parts the original token parts (header.payload.signature)
 * @param rawToken the original raw token string
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public record DecodedJwt(
JsonObject header,
JsonObject body,
String signature,
String[] parts,
String rawToken
) {
    /**
     * Gets the header of the JWT token.
     *
     * @return an Optional containing the header if present
     */
    public Optional<JsonObject> getHeader() {
        return Optional.ofNullable(header);
    }

    /**
     * Gets the body of the JWT token.
     *
     * @return an Optional containing the body if present
     */
    public Optional<JsonObject> getBody() {
        return Optional.ofNullable(body);
    }

    /**
     * Gets the signature of the JWT token.
     *
     * @return an Optional containing the signature if present
     */
    public Optional<String> getSignature() {
        return Optional.ofNullable(signature);
    }

    /**
     * Gets the issuer of the JWT token extracted from the body.
     *
     * @return an Optional containing the issuer if present
     */
    public Optional<String> getIssuer() {
        return body != null && body.containsKey(ClaimName.ISSUER.getName())
                ? Optional.of(body.getString(ClaimName.ISSUER.getName()))
                : Optional.empty();
    }

    /**
     * Gets the kid (key ID) from the JWT token header.
     *
     * @return an Optional containing the kid if present
     */
    public Optional<String> getKid() {
        return header != null && header.containsKey("kid")
                ? Optional.of(header.getString("kid"))
                : Optional.empty();
    }

    /**
     * Gets the alg (algorithm) from the JWT token header.
     *
     * @return an Optional containing the algorithm if present
     */
    public Optional<String> getAlg() {
        return header != null && header.containsKey("alg")
                ? Optional.of(header.getString("alg"))
                : Optional.empty();
    }

    /**
     * Overrides equals to properly handle array comparison for the parts field.
     * Uses Arrays.equals() for proper content-based equality comparison of the array.
     *
     * @param obj the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DecodedJwt that = (DecodedJwt) obj;
        return Objects.equals(header, that.header) &&
                Objects.equals(body, that.body) &&
                Objects.equals(signature, that.signature) &&
                Arrays.equals(parts, that.parts) &&
                Objects.equals(rawToken, that.rawToken);
    }

    /**
     * Overrides hashCode to properly handle array hashing for the parts field.
     * Uses Arrays.hashCode() for consistent hash generation with the equals method.
     *
     * @return the hash code of this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(header, body, signature, Arrays.hashCode(parts), rawToken);
    }

    /**
     * Overrides toString to properly handle array representation for the parts field.
     * Uses Arrays.toString() for proper array content representation.
     *
     * @return a string representation of this object
     */
    @Override
    public @NonNull String toString() {
        return "DecodedJwt[" +
                "header=" + header +
                ", body=" + body +
                ", signature=" + signature +
                ", parts=" + Arrays.toString(parts) +
                ", rawToken=" + rawToken +
                ']';
    }

    /**
     * Creates a builder for constructing DecodedJwt instances.
     * This method provides backward compatibility with the previous builder pattern.
     *
     * @return a new DecodedJwtBuilder instance
     */
    public static DecodedJwtBuilder builder() {
        return new DecodedJwtBuilder();
    }

    /**
     * Builder class for creating DecodedJwt instances.
     * Provides a fluent API for constructing DecodedJwt records.
     */
    public static class DecodedJwtBuilder {
        private JsonObject header;
        private JsonObject body;
        private String signature;
        private String[] parts;
        private String rawToken;

        public DecodedJwtBuilder header(JsonObject header) {
            this.header = header;
            return this;
        }

        public DecodedJwtBuilder body(JsonObject body) {
            this.body = body;
            return this;
        }

        public DecodedJwtBuilder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public DecodedJwtBuilder parts(String[] parts) {
            this.parts = parts;
            return this;
        }

        public DecodedJwtBuilder rawToken(String rawToken) {
            this.rawToken = rawToken;
            return this;
        }

        public DecodedJwt build() {
            return new DecodedJwt(header, body, signature, parts, rawToken);
        }
    }
}
