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

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import io.jsonwebtoken.Jwts;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.time.Instant;
import java.util.*;

/**
 * Centralized repository for managing test tokens and pre-configured TokenValidator instances
 * for benchmarking purposes.
 * <p>
 * This class encapsulates the common token generation and validator setup logic used by
 * multiple benchmark classes, providing:
 * <ul>
 *   <li>Pre-generated token pools for multiple issuers</li>
 *   <li>Token metadata tracking (issuer, size, etc.)</li>
 *   <li>Pre-configured TokenValidator instances with monitoring</li>
 *   <li>Consistent token shuffling for randomized access patterns</li>
 * </ul>
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
@Getter
public class TokenRepository {
    
    /**
     * Default number of different issuers to simulate issuer config resolution overhead
     */
    public static final int DEFAULT_ISSUER_COUNT = 3;
    
    /**
     * Default shared token pool size for reduced setup overhead
     */
    public static final int DEFAULT_TOKEN_POOL_SIZE = 60; // 20 tokens per issuer
    
    /**
     * Default expected audience for benchmarking
     */
    public static final String DEFAULT_AUDIENCE = "benchmark-client";
    
    private final String[] tokenPool;
    private final Map<String, TokenMetadata> tokenMetadata;
    private final List<IssuerConfig> issuerConfigs;
    private final InMemoryKeyMaterialHandler.IssuerKeyMaterial[] issuers;
    private final Random random;
    
    /**
     * Metadata for a generated token
     */
    @Value
    @Builder
    public static class TokenMetadata {
        String issuerIdentifier;
        int tokenSize;
        String keyId;
    }
    
    /**
     * Configuration for TokenRepository
     */
    @Value
    @Builder
    public static class Config {
        @Builder.Default
        int issuerCount = DEFAULT_ISSUER_COUNT;
        
        @Builder.Default
        int tokenPoolSize = DEFAULT_TOKEN_POOL_SIZE;
        
        @Builder.Default
        String expectedAudience = DEFAULT_AUDIENCE;
        
        @Builder.Default
        long randomSeed = 42L; // Fixed seed for reproducibility
    }
    
    /**
     * Creates a new TokenRepository with default configuration
     */
    public TokenRepository() {
        this(Config.builder().build());
    }
    
    /**
     * Creates a new TokenRepository with the specified configuration
     */
    public TokenRepository(Config config) {
        this.random = new Random(config.randomSeed);
        this.tokenMetadata = new HashMap<>();
        
        // Generate multiple issuer key materials for benchmarking
        this.issuers = InMemoryKeyMaterialHandler.createMultipleIssuers(config.issuerCount);
        
        List<IssuerConfig> configs = new ArrayList<>();
        List<String> allTokens = new ArrayList<>();
        
        // Create issuer configs and generate tokens for each issuer
        for (InMemoryKeyMaterialHandler.IssuerKeyMaterial issuer : issuers) {
            // Create issuer config with the issuer's JWKS
            IssuerConfig issuerConfig = IssuerConfig.builder()
                    .issuerIdentifier(issuer.getIssuerIdentifier())
                    .jwksContent(issuer.getJwks())
                    .expectedAudience(config.expectedAudience)
                    .expectedClientId(config.expectedAudience) // azp claim validation
                    .build();
            
            configs.add(issuerConfig);
            
            // Generate tokens for this issuer
            int tokensPerIssuer = config.tokenPoolSize / config.issuerCount;
            for (int j = 0; j < tokensPerIssuer; j++) {
                String token = generateTokenForIssuer(issuer, config.expectedAudience);
                allTokens.add(token);
                
                // Store metadata
                tokenMetadata.put(token, TokenMetadata.builder()
                        .issuerIdentifier(issuer.getIssuerIdentifier())
                        .tokenSize(token.length())
                        .keyId(issuer.getKeyId())
                        .build());
            }
        }
        
        this.issuerConfigs = Collections.unmodifiableList(configs);
        
        // Convert token list to array and shuffle
        this.tokenPool = allTokens.toArray(new String[0]);
        shuffleArray(this.tokenPool);
    }
    
    /**
     * Creates a pre-configured TokenValidator with all monitoring enabled
     * 
     * @return A new TokenValidator instance configured for benchmarking
     */
    public TokenValidator createTokenValidator() {
        return createTokenValidator(TokenValidatorMonitorConfig.builder()
                .measurementTypes(TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES)
                .windowSize(10000) // Large window for benchmark stability
                .build());
    }
    
    /**
     * Creates a pre-configured TokenValidator with the specified monitor configuration
     * 
     * @param monitorConfig The monitor configuration to use
     * @return A new TokenValidator instance
     */
    public TokenValidator createTokenValidator(TokenValidatorMonitorConfig monitorConfig) {
        return TokenValidator.builder()
                .issuerConfigs(issuerConfigs)
                .monitorConfig(monitorConfig)
                .build();
    }
    
    /**
     * Gets a token from the pool at the specified index
     * 
     * @param index The index in the token pool
     * @return The token at the specified index
     */
    public String getToken(int index) {
        return tokenPool[index % tokenPool.length];
    }
    
    /**
     * Gets a random token from the pool
     * 
     * @return A randomly selected token
     */
    public String getRandomToken() {
        return tokenPool[random.nextInt(tokenPool.length)];
    }
    
    /**
     * Gets the primary validation token (first in the pool)
     * 
     * @return The primary token for validation
     */
    public String getPrimaryToken() {
        return tokenPool[0];
    }
    
    /**
     * Gets metadata for a specific token
     * 
     * @param token The token to get metadata for
     * @return The token metadata, or null if not found
     */
    public TokenMetadata getTokenMetadata(String token) {
        return tokenMetadata.get(token);
    }
    
    /**
     * Gets the issuer identifier for a specific token
     * 
     * @param token The token to get the issuer for
     * @return The issuer identifier, or null if not found
     */
    public String getTokenIssuer(String token) {
        TokenMetadata metadata = tokenMetadata.get(token);
        return metadata != null ? metadata.getIssuerIdentifier() : null;
    }
    
    /**
     * Gets the total number of tokens in the pool
     * 
     * @return The token pool size
     */
    public int getTokenPoolSize() {
        return tokenPool.length;
    }
    
    /**
     * Generates a valid JWT token for the given issuer
     */
    private String generateTokenForIssuer(InMemoryKeyMaterialHandler.IssuerKeyMaterial issuer, String audience) {
        Instant now = Instant.now();
        
        return Jwts.builder()
                .header()
                .keyId(issuer.getKeyId())
                .and()
                .issuer(issuer.getIssuerIdentifier())
                .subject("benchmark-user-" + UUID.randomUUID())
                .audience().add(audience).and()
                .expiration(Date.from(now.plusSeconds(3600)))
                .notBefore(Date.from(now.minusSeconds(60)))
                .issuedAt(Date.from(now))
                .id(UUID.randomUUID().toString())
                .claim("typ", "Bearer")
                .claim("azp", audience)
                .claim("scope", "openid profile email")
                .claim("email_verified", true)
                .claim("name", "Benchmark User")
                .claim("preferred_username", "benchmark")
                .claim("given_name", "Benchmark")
                .claim("family_name", "User")
                .claim("email", "benchmark@example.com")
                // Add some variable-size claims to create different token sizes
                .claim("custom_data", generateRandomData())
                .signWith(issuer.getPrivateKey())
                .compact();
    }
    
    /**
     * Generates random data for token size variation
     */
    private String generateRandomData() {
        int size = 50 + random.nextInt(200); // 50-250 characters
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }
    
    /**
     * Shuffles the array using Fisher-Yates algorithm
     */
    private void shuffleArray(String[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            String temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }
}