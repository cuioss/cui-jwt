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

import java.util.Optional;

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
String kty,    // Key type: "RSA", "EC"
String kid,   // Key ID (optional)
String alg,   // Algorithm: "RS256", "ES256", etc. (optional)
String n,     // RSA modulus (Base64url-encoded, RSA only)
String e,     // RSA exponent (Base64url-encoded, RSA only)
String crv,   // EC curve: "P-256", "P-384", "P-521" (EC only)
String x,     // EC x coordinate (Base64url-encoded, EC only)
String y      // EC y coordinate (Base64url-encoded, EC only)
) {

    /**
     * Gets the key type as Optional.
     * 
     * @return Optional containing the key type, empty if null
     */
    public Optional<String> getKty() {
        return Optional.ofNullable(kty);
    }

    /**
     * Gets the key ID as Optional.
     * 
     * @return Optional containing the key ID, empty if null
     */
    public Optional<String> getKid() {
        return Optional.ofNullable(kid);
    }

    /**
     * Gets the algorithm as Optional.
     * 
     * @return Optional containing the algorithm, empty if null
     */
    public Optional<String> getAlg() {
        return Optional.ofNullable(alg);
    }

    /**
     * Gets the RSA modulus as Optional.
     * 
     * @return Optional containing the RSA modulus, empty if null
     */
    public Optional<String> getN() {
        return Optional.ofNullable(n);
    }

    /**
     * Gets the RSA exponent as Optional.
     * 
     * @return Optional containing the RSA exponent, empty if null
     */
    public Optional<String> getE() {
        return Optional.ofNullable(e);
    }

    /**
     * Gets the EC curve as Optional.
     * 
     * @return Optional containing the EC curve, empty if null
     */
    public Optional<String> getCrv() {
        return Optional.ofNullable(crv);
    }

    /**
     * Gets the EC x coordinate as Optional.
     * 
     * @return Optional containing the x coordinate, empty if null
     */
    public Optional<String> getX() {
        return Optional.ofNullable(x);
    }

    /**
     * Gets the EC y coordinate as Optional.
     * 
     * @return Optional containing the y coordinate, empty if null
     */
    public Optional<String> getY() {
        return Optional.ofNullable(y);
    }
}