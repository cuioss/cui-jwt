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
package de.cuioss.jwt.validation.jwks.key;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utility class for handling JWK (JSON Web Key) constants and operations.
 * <p>
 * This class provides constants for various JWK parameters and nested classes for key operations.
 * Each nested class contains methods to retrieve the key and parse the corresponding value from a given {@link JsonObject}.
 * <p>
 * For more details on the JWK specification, see the
 * <a href="https://datatracker.ietf.org/doc/html/rfc7517">RFC 7517 - JSON Web Key (JWK)</a>.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@UtilityClass
public class JwkKeyConstants {

    private static final CuiLogger LOGGER = new CuiLogger(JwkKeyConstants.class);
    private static final Pattern BASE64_URL_PATTERN = Pattern.compile("^[A-Za-z0-9\\-_]*=*$");

    /**
     * The "kty" (key type) parameter identifies the cryptographic algorithm family used with the key.
     * It is a required parameter.
     */
    @UtilityClass
    public static class KeyType {
        public static final String KEY = "kty";

        /**
         * Returns the key type as a string, if present
         *
         * @param jsonHolder the JSON object containing the JWK
         * @return an Optional containing the key type string if present, otherwise empty
         */
        public Optional<String> getString(@NonNull JsonObject jsonHolder) {
            return extractStringContentFrom(KEY, jsonHolder);
        }

        /**
         * Returns whether the key type is present in the JSON object
         *
         * @param jsonHolder the JSON object containing the JWK
         * @return whether the key type is present in the JSON object
         */
        public boolean isPresent(@NonNull JsonObject jsonHolder) {
            return jsonHolder.containsKey(KEY);
        }
    }

    /**
     * The "keys" (keys) parameter identifies a number of keys transported as a JSON array.
     **/
    @UtilityClass
    public static class Keys {
        public static final String KEY = "keys";

        /**
         * Returns a boolean indicating whether there are multiple keys present
         *
         * @param jsonHolder the JSON object containing the JWK
         * @return flag indicating whether multiple keys are present
         */
        public boolean isPresent(@NonNull JsonObject jsonHolder) {
            return jsonHolder.containsKey(KEY);
        }

        /**
         * Returns a JsonArray containing the keys, if present
         *
         * @param jsonHolder the JSON object containing the JWK
         * @return a JsonArray containing the keys, if present
         */
        public Optional<JsonArray> extract(@NonNull JsonObject jsonHolder) {
            if (!isPresent(jsonHolder)) {
                return Optional.empty();
            }
            return Optional.ofNullable(jsonHolder.getJsonArray(KEY));
        }
    }

    /**
     * The "alg" (algorithm) parameter identifies the algorithm intended for use with the key.
     * It is an optional parameter.
     */
    @UtilityClass
    public static class Algorithm {
        public static final String KEY = "alg";

        public static Optional<String> from(@NonNull JsonObject jsonHolder) {
            return extractStringContentFrom(KEY, jsonHolder);
        }
    }

    /**
     * The "kid" (key ID) parameter is used to match a specific key.
     * It is an optional parameter.
     */
    @UtilityClass
    public static class KeyId {
        public static final String KEY = "kid";

        public static Optional<String> from(@NonNull JsonObject jsonHolder) {
            return extractStringContentFrom(KEY, jsonHolder);
        }
    }

    /**
     * The "x" parameter contains the x coordinate for the elliptic curve point.
     * It is a required parameter for EC keys.
     */
    @UtilityClass
    public static class XCoordinate {
        public static final String KEY = "x";

        public static Optional<BigInteger> from(@NonNull JsonObject jsonHolder) {
            return from64EncodedBigInteger(KEY, jsonHolder);
        }
    }

    /**
     * The "y" parameter contains the y coordinate for the elliptic curve point.
     * It is a required parameter for EC keys.
     */
    @UtilityClass
    public static class YCoordinate {
        public static final String KEY = "y";

        public static Optional<BigInteger> from(@NonNull JsonObject jsonHolder) {
            return from64EncodedBigInteger(KEY, jsonHolder);
        }
    }

    /**
     * The "crv" (curve) parameter identifies the curve used with the EC key.
     * It is a required parameter for EC keys.
     */
    @UtilityClass
    public static class Curve {
        public static final String KEY = "crv";

        public static Optional<String> from(@NonNull JsonObject jsonHolder) {
            return extractStringContentFrom(KEY, jsonHolder);
        }
    }

    /**
     * The "n" parameter contains the modulus value for RSA keys.
     * It is a required parameter for RSA keys.
     */
    @UtilityClass
    public static class Modulus {
        public static final String KEY = "n";

        public static Optional<BigInteger> from(@NonNull JsonObject jsonHolder) {
            return from64EncodedBigInteger(KEY, jsonHolder);
        }
    }

    /**
     * The "e" parameter contains the exponent value for RSA keys.
     * It is a required parameter for RSA keys.
     */
    @UtilityClass
    public static class Exponent {
        public static final String KEY = "e";

        public static Optional<BigInteger> from(@NonNull JsonObject jsonHolder) {
            return from64EncodedBigInteger(KEY, jsonHolder);
        }

    }

    /**
     * Helper-method for extracting Strings from {@link JsonObject}.
     *
     * @param contentKey the key for looking up a certain content that is presumably a String-Content within the contained {@link JsonObject}
     *                   must not be null or blank.
     * @param jsonHolder providing the content, must not be null
     * @return an {@link Optional} containing the string if the content could be extracted.
     */
    static Optional<String> extractStringContentFrom(@NonNull String contentKey, @NonNull JsonObject jsonHolder) {
        if (!jsonHolder.containsKey(contentKey)) {
            LOGGER.debug("given contentKey does not represent an object within given JsonObject, returning empty Optional");
            return Optional.empty();
        }
        String contentAsString = jsonHolder.getString(contentKey, "");
        if (MoreStrings.isBlank(contentAsString)) {
            LOGGER.debug("given contentKey '%s' does not resolve to a non empty String, returning empty Optional", contentKey);
            return Optional.empty();
        }
        return Optional.of(contentAsString);
    }

    /**
     * Helper-method for extracting Base64-decoded Strings from a given {@link JsonObject}.
     *
     * @param contentKey the key for looking up a certain content that is presumably Base64 Content within the contained {@link JsonObject}
     *                   must not be null or blank.
     * @param jsonHolder providing the content, must not be null
     * @return an {@link Optional} containing the base64-decoded byte[] if the content could be extracted.
     */
    static Optional<byte[]> from64EncodedContent(@NonNull String contentKey, @NonNull JsonObject jsonHolder) {
        var contentOption = extractStringContentFrom(contentKey, jsonHolder);
        if (contentOption.isEmpty()) {
            return Optional.empty();
        }
        String contentAsString = contentOption.get();
        if (!BASE64_URL_PATTERN.matcher(contentAsString).matches()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.INVALID_BASE64_CONTENT.format(contentKey, contentAsString));
            return Optional.empty();
        }
        return Optional.ofNullable(Base64.getUrlDecoder().decode(contentAsString));
    }

    /**
     * Factory method for creating a new instance of {@link JsonObject}.
     *
     * @param contentKey the key for looking up a certain content that is presumably Base64 Content within the contained {@link JsonObject}
     *                   must not be null or blank.
     * @param jsonHolder providing the content, must not be null
     * @return an {@link Optional} containing the base64-decoded BigInteger representation.
     */
    static Optional<BigInteger> from64EncodedBigInteger(@NonNull String contentKey, @NonNull JsonObject jsonHolder) {
        var base64Option = from64EncodedContent(contentKey, jsonHolder);
        return base64Option.map(bytes -> new BigInteger(1, bytes));
    }
}
