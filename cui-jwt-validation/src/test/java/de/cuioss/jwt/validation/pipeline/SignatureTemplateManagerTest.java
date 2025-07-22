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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link SignatureTemplateManager}.
 * 
 * @author Oliver Wolff
 */
class SignatureTemplateManagerTest {

    private SignatureTemplateManager manager;

    @BeforeEach
    void setUp() {
        manager = new SignatureTemplateManager();
    }

    /**
     * Tests that all supported algorithms can create Signature instances.
     */
    @ParameterizedTest
    @ValueSource(strings = {"RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "PS256", "PS384", "PS512"})
    void getSignatureInstanceSupportedAlgorithms(String algorithm) throws Exception {
        // When
        Signature signature = manager.getSignatureInstance(algorithm);

        // Then
        assertNotNull(signature, "Signature should not be null for algorithm: " + algorithm);
        assertTrue(signature.getAlgorithm().contains("RSA") || signature.getAlgorithm().contains("ECDSA"),
                "Algorithm should be RSA or ECDSA based for: " + algorithm);
    }

    /**
     * Tests that unsupported algorithms throw IllegalArgumentException.
     */
    @Test
    void getSignatureInstanceUnsupportedAlgorithm() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> manager.getSignatureInstance("UNSUPPORTED"));

        assertTrue(exception.getMessage().contains("Unsupported algorithm"),
                "Exception message should mention unsupported algorithm");
    }

    /**
     * Tests that template caching works correctly by ensuring multiple calls return different instances
     * but with the same algorithm.
     */
    @Test
    void templateCaching() throws Exception {
        // When
        Signature signature1 = manager.getSignatureInstance("ES256");
        Signature signature2 = manager.getSignatureInstance("ES256");

        // Then
        assertNotSame(signature1, signature2, "Different Signature instances should be returned");
        assertEquals(signature1.getAlgorithm(), signature2.getAlgorithm(),
                "Same algorithm should be used for both instances");
    }

    /**
     * Tests that the manager is thread-safe by calling it concurrently from multiple threads.
     */
    @Test
    void concurrentAccess() throws InterruptedException, ExecutionException {
        // Given
        int numberOfThreads = 10;
        int operationsPerThread = 20;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    Assertions.assertDoesNotThrow(() -> {
                        Signature signature = manager.getSignatureInstance("ES256");
                        assertNotNull(signature);
                        assertEquals("SHA256withECDSA", signature.getAlgorithm());
                    }, "Exception during concurrent access: ");
                }
            });
            futures.add(future);
        }

        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        // Then
        assertDoesNotThrow(() -> allFutures.get(), "Concurrent access should not throw exceptions");
    }

    /**
     * Tests that PSS algorithms have proper parameters set.
     */
    @ParameterizedTest
    @ValueSource(strings = {"PS256", "PS384", "PS512"})
    void pssAlgorithms(String algorithm) throws Exception {
        // When
        Signature signature = manager.getSignatureInstance(algorithm);

        // Then
        assertNotNull(signature, "PSS signature should not be null for algorithm: " + algorithm);
        assertEquals("RSASSA-PSS", signature.getAlgorithm(),
                "PSS algorithms should use RSASSA-PSS");
    }

    /**
     * Tests that ECDSA algorithms work correctly.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ES256", "ES384", "ES512"})
    void ecdsaAlgorithms(String algorithm) throws Exception {
        // When
        Signature signature = manager.getSignatureInstance(algorithm);

        // Then
        assertNotNull(signature, "ECDSA signature should not be null for algorithm: " + algorithm);
        assertTrue(signature.getAlgorithm().contains("ECDSA"),
                "ECDSA algorithms should contain 'ECDSA' in name");
    }

    /**
     * Tests that RSA algorithms work correctly.
     */
    @ParameterizedTest
    @ValueSource(strings = {"RS256", "RS384", "RS512"})
    void rsaAlgorithms(String algorithm) throws Exception {
        // When
        Signature signature = manager.getSignatureInstance(algorithm);

        // Then
        assertNotNull(signature, "RSA signature should not be null for algorithm: " + algorithm);
        assertTrue(signature.getAlgorithm().contains("RSA"),
                "RSA algorithms should contain 'RSA' in name");
    }

    /**
     * Tests UnsupportedAlgorithmException behavior.
     */
    @Test
    void unsupportedAlgorithmException() {
        // Given
        String invalidAlgorithm = "INVALID_ALG";

        // When & Then
        SignatureTemplateManager.UnsupportedAlgorithmException exception =
                assertThrows(SignatureTemplateManager.UnsupportedAlgorithmException.class, () -> {
                    // Force the exception by simulating a failure in template creation
                    throw new SignatureTemplateManager.UnsupportedAlgorithmException(
                            "Test exception for: " + invalidAlgorithm,
                            new NoSuchAlgorithmException("Mock exception"));
                });

        assertNotNull(exception.getCause(), "Exception should have a cause");
        assertTrue(exception.getMessage().contains("Test exception for: INVALID_ALG"),
                "Exception message should contain algorithm name");
    }
}