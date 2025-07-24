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

import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.security.Key;
import java.security.spec.InvalidKeySpecException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JwkKeyHandler} with a focus on security aspects and potential attacks.
 */
class JwkKeyHandlerTest {

    private JsonObject createJsonObject(String jsonString) {
        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            return reader.readObject();
        }
    }

    private String extractSingleJwk(String jwksString) {
        try (JsonReader reader = Json.createReader(new StringReader(jwksString))) {
            JsonObject jwks = reader.readObject();
            return jwks.getJsonArray("keys").getJsonObject(0).toString();
        }
    }

    private JsonObject createRsaJwk() {
        String jwksString = InMemoryJWKSFactory.createDefaultJwks();
        String jwkString = extractSingleJwk(jwksString);
        return createJsonObject(jwkString);
    }

    private JsonObject createJsonObject(String n, String e) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("kty", "RSA");

        if (n != null) {
            builder.add("n", n);
        }

        if (e != null) {
            builder.add("e", e);
        }

        builder.add("kid", "test-key");

        return builder.build();
    }

    private JsonObject createEcJwk(String x, String y) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("kty", "EC")
                .add("crv", "P-256");

        if (x != null) {
            builder.add("x", x);
        }

        if (y != null) {
            builder.add("y", y);
        }

        builder.add("kid", "test-key");

        return builder.build();
    }

    @Test
    void shouldParseValidRsaKey() throws InvalidKeySpecException {
        JsonObject jwk = createRsaJwk();
        Key key = JwkKeyHandler.parseRsaKey(jwk);
        assertNotNull(key, "RSA key should not be null");
        assertEquals("RSA", key.getAlgorithm(), "Key algorithm should be RSA");
    }

    @Test
    void shouldValidateRsaKeyFields() {
        JsonObject jwk = createRsaJwk();
        assertDoesNotThrow(() -> JwkKeyHandler.parseRsaKey(jwk));
    }

    @Test
    void shouldRejectRsaKeyWithMissingModulus() {
        JsonObject jwk = createJsonObject(null, "AQAB");

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseRsaKey(jwk)
        );
        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'n'"), exception.getMessage());
    }

    @Test
    void shouldRejectRsaKeyWithMissingExponent() {
        JsonObject jwk = createJsonObject("someModulus", null);

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseRsaKey(jwk)
        );
        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'e'"));
    }

    @Test
    void shouldRejectRsaKeyWithInvalidBase64UrlModulus() {
        JsonObject jwk = createJsonObject("invalid!base64", "AQAB");

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseRsaKey(jwk)
        );
        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'n'"), exception.getMessage());
    }

    @Test
    void shouldRejectRsaKeyWithInvalidBase64UrlExponent() {
        JsonObject jwk = createJsonObject("validModulus", "invalid!base64");

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseRsaKey(jwk)
        );
        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'e'"));
    }

    @Test
    void shouldRejectEcKeyWithMissingXCoordinate() {
        JsonObject jwk = createEcJwk(null, "validYCoord");
        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseEcKey(jwk)
        );

        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'x'"), exception.getMessage());
    }

    @Test
    void shouldRejectEcKeyWithMissingYCoordinate() {
        JsonObject jwk = createEcJwk("validXCoord", null);

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseEcKey(jwk)
        );

        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'y'"), exception.getMessage());
    }

    @Test
    void shouldRejectEcKeyWithInvalidBase64UrlXCoordinate() {
        JsonObject jwk = createEcJwk("invalid!base64", "validYCoord");

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseEcKey(jwk)
        );

        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'x'"), "Actual message: " + exception.getMessage());
    }

    @Test
    void shouldRejectEcKeyWithInvalidBase64UrlYCoordinate() {
        JsonObject jwk = createEcJwk("validXCoord", "invalid!base64");

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseEcKey(jwk)
        );

        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'y'"), exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "P-256", "P-384", "P-521"
    })
    void shouldDetermineCorrectEcAlgorithm(String curve) {
        String algorithm = JwkKeyHandler.determineEcAlgorithm(curve);

        switch (curve) {
            case "P-256":
                assertEquals("ES256", algorithm, "Algorithm should be ES256 for P-256 curve");
                break;
            case "P-384":
                assertEquals("ES384", algorithm, "Algorithm should be ES384 for P-384 curve");
                break;
            case "P-521":
                assertEquals("ES512", algorithm, "Algorithm should be ES512 for P-521 curve");
                break;
            default:
                fail("Unexpected curve: " + curve);
        }
    }

    @Test
    void shouldReturnDefaultAlgorithmForUnknownCurve() {
        String curve = "unknown-curve";

        String algorithm = JwkKeyHandler.determineEcAlgorithm(curve);

        assertEquals("ES256", algorithm, "Default algorithm should be ES256 for unknown curve");
    }

    @Test
    void shouldParseValidEcKey() throws Exception {
        // These are example values for P-256 curve (secp256r1)
        String x = "f83OJ3D2xF4P4QJrL6Z4pWQ2vQKj6k1b6QJ6Qn6QJ6Q";
        String y = "x_FEzRu9QJ6Qn6QJ6QJ6Qn6QJ6Qn6QJ6Qn6QJ6Qn6Q";
        JsonObject jwk = Json.createObjectBuilder()
                .add("kty", "EC")
                .add("crv", "P-256")
                .add("x", x)
                .add("y", y)
                .add("kid", "test-key")
                .build();

        Key key = JwkKeyHandler.parseEcKey(jwk);

        assertNotNull(key, "EC key should not be null");
        assertEquals("EC", key.getAlgorithm(), "Key algorithm should be EC");
    }

    @Test
    void shouldRejectEcKeyWithMissingCurve() {
        JsonObject jwk = Json.createObjectBuilder()
                .add("kty", "EC")
                .add("x", "validXCoord")
                .add("y", "validYCoord")
                .add("kid", "test-key")
                .build();

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseEcKey(jwk)
        );

        assertTrue(exception.getMessage().contains("Invalid Base64 URL encoded value for 'crv'"),
                "Actual message: " + exception.getMessage());
    }

    @Test
    void shouldRejectEcKeyWithUnsupportedCurve() {
        JsonObject jwk = Json.createObjectBuilder()
                .add("kty", "EC")
                .add("crv", "P-192") // Unsupported curve
                .add("x", "validXCoord")
                .add("y", "validYCoord")
                .add("kid", "test-key")
                .build();

        InvalidKeySpecException exception = assertThrows(
                InvalidKeySpecException.class,
                () -> JwkKeyHandler.parseEcKey(jwk)
        );

        assertTrue(exception.getMessage().contains("EC curve P-192 is not supported"),
                "Actual message: " + exception.getMessage());
    }

}
