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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DecodedJwt}.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests for DecodedJwt")
class DecodedJwtTest {

    private static final String ISSUER = "https://test-issuer.com";
    private static final String KID = "test-key-id";
    private static final String ALG = "RS256";
    private static final String SIGNATURE = "test-signature";
    private static final String RAW_TOKEN = "header.payload.signature";
    private static final String[] PARTS = {"header", "payload", "signature"};

    @Test
    @DisplayName("Should create DecodedJwt with all values")
    void shouldCreateDecodedJwtWithAllValues() {

        JsonObject header = createTestHeader();
        JsonObject body = createTestBody();
        DecodedJwt jwt = new DecodedJwt(header, body, SIGNATURE, PARTS, RAW_TOKEN);
        assertTrue(jwt.getHeader().isPresent());
        assertEquals(header, jwt.getHeader().get());

        assertTrue(jwt.getBody().isPresent());
        assertEquals(body, jwt.getBody().get());

        assertTrue(jwt.getSignature().isPresent());
        assertEquals(SIGNATURE, jwt.getSignature().get());

        assertTrue(jwt.getIssuer().isPresent());
        assertEquals(ISSUER, jwt.getIssuer().get());

        assertTrue(jwt.getKid().isPresent());
        assertEquals(KID, jwt.getKid().get());

        assertTrue(jwt.getAlg().isPresent());
        assertEquals(ALG, jwt.getAlg().get());

        assertEquals(PARTS, jwt.parts());
        assertEquals(RAW_TOKEN, jwt.rawToken());
    }

    @Test
    @DisplayName("Should create DecodedJwt with null values")
    void shouldCreateDecodedJwtWithNullValues() {

        DecodedJwt jwt = new DecodedJwt(null, null, null, PARTS, RAW_TOKEN);
        assertFalse(jwt.getHeader().isPresent());
        assertFalse(jwt.getBody().isPresent());
        assertFalse(jwt.getSignature().isPresent());
        assertFalse(jwt.getIssuer().isPresent());
        assertFalse(jwt.getKid().isPresent());
        assertFalse(jwt.getAlg().isPresent());
        assertEquals(PARTS, jwt.parts());
        assertEquals(RAW_TOKEN, jwt.rawToken());
    }

    @Test
    @DisplayName("Should create DecodedJwt with empty header and body")
    void shouldCreateDecodedJwtWithEmptyHeaderAndBody() {

        JsonObject emptyHeader = Json.createObjectBuilder().build();
        JsonObject emptyBody = Json.createObjectBuilder().build();
        DecodedJwt jwt = new DecodedJwt(emptyHeader, emptyBody, SIGNATURE, PARTS, RAW_TOKEN);
        assertTrue(jwt.getHeader().isPresent());
        assertEquals(emptyHeader, jwt.getHeader().get());

        assertTrue(jwt.getBody().isPresent());
        assertEquals(emptyBody, jwt.getBody().get());

        assertTrue(jwt.getSignature().isPresent());
        assertEquals(SIGNATURE, jwt.getSignature().get());

        assertFalse(jwt.getIssuer().isPresent());
        assertFalse(jwt.getKid().isPresent());
        assertFalse(jwt.getAlg().isPresent());

        assertEquals(PARTS, jwt.parts());
        assertEquals(RAW_TOKEN, jwt.rawToken());
    }

    @Test
    @DisplayName("Should create DecodedJwt using builder")
    void shouldCreateDecodedJwtUsingBuilder() {

        JsonObject header = createTestHeader();
        JsonObject body = createTestBody();
        DecodedJwt jwt = DecodedJwt.builder()
                .header(header)
                .body(body)
                .signature(SIGNATURE)
                .parts(PARTS)
                .rawToken(RAW_TOKEN)
                .build();
        assertTrue(jwt.getHeader().isPresent());
        assertEquals(header, jwt.getHeader().get());

        assertTrue(jwt.getBody().isPresent());
        assertEquals(body, jwt.getBody().get());

        assertTrue(jwt.getSignature().isPresent());
        assertEquals(SIGNATURE, jwt.getSignature().get());

        assertTrue(jwt.getIssuer().isPresent());
        assertEquals(ISSUER, jwt.getIssuer().get());

        assertTrue(jwt.getKid().isPresent());
        assertEquals(KID, jwt.getKid().get());

        assertTrue(jwt.getAlg().isPresent());
        assertEquals(ALG, jwt.getAlg().get());

        assertEquals(PARTS, jwt.parts());
        assertEquals(RAW_TOKEN, jwt.rawToken());
    }

    @Test
    @DisplayName("Should have proper equals and hashCode")
    void shouldHaveProperEqualsAndHashCode() {

        JsonObject header1 = createTestHeader();
        JsonObject body1 = createTestBody();
        DecodedJwt jwt1 = new DecodedJwt(header1, body1, SIGNATURE, PARTS, RAW_TOKEN);

        JsonObject header2 = createTestHeader();
        JsonObject body2 = createTestBody();
        DecodedJwt jwt2 = new DecodedJwt(header2, body2, SIGNATURE, PARTS, RAW_TOKEN);
        assertEquals(jwt1, jwt2);
        assertEquals(jwt1.hashCode(), jwt2.hashCode());
    }

    @Test
    @DisplayName("Should properly compare array fields in equals")
    void shouldProperlyCompareArrayFieldsInEquals() {
        JsonObject header = createTestHeader();
        JsonObject body = createTestBody();

        // Create two identical arrays with same content but different references
        String[] parts1 = {"header", "payload", "signature"};
        String[] parts2 = {"header", "payload", "signature"};

        DecodedJwt jwt1 = new DecodedJwt(header, body, SIGNATURE, parts1, RAW_TOKEN);
        DecodedJwt jwt2 = new DecodedJwt(header, body, SIGNATURE, parts2, RAW_TOKEN);

        // Should be equal based on content, not reference
        assertEquals(jwt1, jwt2, "DecodedJwt objects should be equal when arrays have same content");
        assertEquals(jwt1.hashCode(), jwt2.hashCode(), "Hash codes should be equal for equal objects");

        // Test with different array content
        String[] differentParts = {"different", "content", "here"};
        DecodedJwt jwt3 = new DecodedJwt(header, body, SIGNATURE, differentParts, RAW_TOKEN);

        assertNotEquals(jwt1, jwt3, "DecodedJwt objects should not be equal when arrays have different content");
    }

    @Test
    @DisplayName("Should have proper toString")
    void shouldHaveProperToString() {

        JsonObject header = createTestHeader();
        JsonObject body = createTestBody();
        DecodedJwt jwt = new DecodedJwt(header, body, SIGNATURE, PARTS, RAW_TOKEN);
        String toString = jwt.toString();
        assertNotNull(toString, "toString() result should not be null");
        assertTrue(toString.contains(ISSUER));
        assertTrue(toString.contains(KID));
        assertTrue(toString.contains(ALG));
        assertTrue(toString.contains(SIGNATURE));

        // Verify array is properly represented in toString
        assertTrue(toString.contains("[header, payload, signature]"),
                "toString should include array content representation");
        assertTrue(toString.contains("DecodedJwt["),
                "toString should follow expected format");
    }

    private JsonObject createTestHeader() {
        return Json.createObjectBuilder()
                .add("alg", ALG)
                .add("kid", KID)
                .build();
    }

    private JsonObject createTestBody() {
        return Json.createObjectBuilder()
                .add("iss", ISSUER)
                .add("sub", "test-subject")
                .build();
    }
}