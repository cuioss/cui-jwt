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
package de.cuioss.jwt.validation.json;

import com.dslplatform.json.CompiledJson;
import org.jspecify.annotations.Nullable;

/**
 * Represents a JSON Web Key (JWK) for DSL-JSON mapping.
 * <p>
 * This class uses DSL-JSON's compile-time code generation for high-performance
 * JSON parsing without reflection. It supports both RSA and EC key types.
 * <p>
 * RSA keys have: kty, kid, alg, n (modulus), e (exponent)
 * EC keys have: kty, kid, alg, crv (curve), x, y (coordinates)
 * <p>
 * The kty field is nullable to support permissive JSON parsing - business rule
 * validation should happen later in the validation chain.
 * 
 * @author Generated
 * @since 1.0
 */
@CompiledJson
public record JwkKey(
@Nullable String kty,    // Key type: "RSA", "EC"
@Nullable String kid,   // Key ID (optional)
@Nullable String alg,   // Algorithm: "RS256", "ES256", etc. (optional)
@Nullable String n,     // RSA modulus (Base64url-encoded, RSA only)
@Nullable String e,     // RSA exponent (Base64url-encoded, RSA only)
@Nullable String crv,   // EC curve: "P-256", "P-384", "P-521" (EC only)
@Nullable String x,     // EC x coordinate (Base64url-encoded, EC only)
@Nullable String y      // EC y coordinate (Base64url-encoded, EC only)
) {

    /**
     * Checks if this is an RSA key.
     * 
     * @return true if key type is "RSA", false if kty is null or different
     */
    public boolean isRsa() {
        return "RSA".equals(kty);
    }

    /**
     * Checks if this is an EC key.
     * 
     * @return true if key type is "EC", false if kty is null or different
     */
    public boolean isEc() {
        return "EC".equals(kty);
    }

    /**
     * Checks if this key has a key ID.
     * 
     * @return true if kid is not null and not empty
     */
    public boolean hasKeyId() {
        return kid != null && !kid.trim().isEmpty();
    }

    /**
     * Checks if this key has an algorithm specified.
     * 
     * @return true if alg is not null and not empty
     */
    public boolean hasAlgorithm() {
        return alg != null && !alg.trim().isEmpty();
    }
}