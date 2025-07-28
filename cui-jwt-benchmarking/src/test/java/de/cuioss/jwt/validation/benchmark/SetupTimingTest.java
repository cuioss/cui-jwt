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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that benchmark setup isolation is working correctly.
 * This test measures the time taken for various operations to ensure
 * RSA key generation is properly isolated.
 */
class SetupTimingTest {
    
    @Test
    void testKeyGenerationIsCached() {
        // First, ensure the cache is initialized
        BenchmarkKeyCache.initialize();
        
        // Measure time to create TokenRepository with cached keys
        long startTime = System.nanoTime();
        TokenRepository repository = new TokenRepository();
        long setupDuration = System.nanoTime() - startTime;
        
        System.out.printf("TokenRepository setup with cached keys: %.2f ms%n", 
                setupDuration / 1_000_000.0);
        
        // Setup should be fast with cached keys (< 100ms)
        assertTrue(setupDuration < 100_000_000L, 
                "Setup took too long: " + (setupDuration / 1_000_000.0) + " ms");
    }
    
    @Test
    void testValidationPerformance() {
        // Setup
        TokenRepository repository = new TokenRepository();
        TokenValidator validator = repository.createTokenValidator();
        String token = repository.getPrimaryToken();
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            validator.createAccessToken(token);
        }
        
        // Measure validation time
        long startTime = System.nanoTime();
        AccessTokenContent result = validator.createAccessToken(token);
        long validationDuration = System.nanoTime() - startTime;
        
        assertNotNull(result);
        System.out.printf("Single validation: %.2f μs%n", 
                validationDuration / 1_000.0);
        
        // Validation should be fast (< 1ms)
        assertTrue(validationDuration < 1_000_000L, 
                "Validation took too long: " + (validationDuration / 1_000.0) + " μs");
    }
    
    @Test
    void testCacheHitRates() {
        // Test that common issuer counts are cached
        for (int i = 1; i <= 5; i++) {
            assertTrue(BenchmarkKeyCache.isCached(i), 
                    "Keys for " + i + " issuers should be cached");
        }
    }
    
    @Test
    void compareSetupTimes() {
        // Force cache initialization
        BenchmarkKeyCache.initialize();
        
        // Measure multiple repository creations
        long totalTime = 0;
        int iterations = 10;
        
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            new TokenRepository();
            totalTime += System.nanoTime() - start;
        }
        
        double avgTimeMs = (totalTime / iterations) / 1_000_000.0;
        System.out.printf("Average TokenRepository creation time: %.2f ms%n", avgTimeMs);
        
        // Average should be very fast with caching
        assertTrue(avgTimeMs < 50.0, 
                "Average setup time too high: " + avgTimeMs + " ms");
    }
}