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
package de.cuioss.jwt.quarkus.servlet;

import de.cuioss.jwt.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.jwt.quarkus.config.JwtTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Quarkus integration tests for {@link VertxServletObjectsResolver}.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@QuarkusTest
@TestProfile(JwtTestProfile.class)
@DisplayName("VertxServletObjectsResolver Quarkus Integration Tests")
class VertxServletObjectsResolverQuarkusTest {

    @Inject
    @ServletObjectsResolver(ServletObjectsResolver.Variant.VERTX)
    HttpServletRequestResolver resolver;


    @Test
    @DisplayName("Should resolve HttpServletRequest during active REST request")
    void shouldResolveHttpServletRequestDuringActiveRestRequest() {
        // Make HTTP request to test endpoint
        String response = given()
                .header("Authorization", "Bearer vertx-test-token")
                .header("X-Test-Header", "test-value")
                .when()
                .get("/test/vertx-resolver")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .extract()
                .asString();

        // Verify that the resolver worked during the request
        assertTrue(response.contains("HttpServletRequest available: true"),
                "HttpServletRequest should be available during active REST request");
        assertTrue(response.contains("HeaderMap available: true"),
                "HeaderMap should be available during active REST request");
        assertTrue(response.contains("HeaderCount: "),
                "Header count should be reported");
    }

    /**
     * Test endpoint that uses the Vertx servlet resolver during HTTP request.
     * This is a proper JAX-RS resource with @RequestScoped to ensure it's in the right context.
     */
    @Path("/test")
    @RequestScoped
    public static class TestEndpoint {

        @Inject
        @ServletObjectsResolver(ServletObjectsResolver.Variant.VERTX)
        HttpServletRequestResolver resolver;

        @GET
        @Path("/vertx-resolver")
        @Produces(MediaType.TEXT_PLAIN)
        public Response testVertxResolver() {
            // Test HttpServletRequest resolution
            String result;
            try {
                HttpServletRequest httpServletRequest = resolver.resolveHttpServletRequest();

                // Test header map resolution
                Map<String, List<String>> headerMap = resolver.resolveHeaderMap();

                // Test specific header access from HttpServletRequest
                String authHeader = httpServletRequest.getHeader("Authorization");

                // Return simple text response with debug info
                result = "HttpServletRequest available: true, HeaderMap available: true, HeaderCount: %d, AuthHeader: %s".formatted(
                        headerMap.size(), authHeader != null ? authHeader : "null");
            } catch (IllegalStateException e) {
                // Return simple text response with debug info
                result = "HttpServletRequest available: false, HeaderMap available: false, HeaderCount: 0";
            }

            return Response.ok(result).build();
        }
    }
}