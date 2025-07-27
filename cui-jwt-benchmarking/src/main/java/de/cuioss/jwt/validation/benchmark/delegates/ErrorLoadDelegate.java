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
package de.cuioss.jwt.validation.benchmark.delegates;

import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.benchmark.TokenRepository;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import io.jsonwebtoken.Jwts;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Delegate for error load benchmarks that test validation behavior under various error conditions.
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
public class ErrorLoadDelegate extends BenchmarkDelegate {
    
    private final String validToken;
    private final String expiredToken;
    private final String malformedToken;
    private final String invalidSignatureToken;
    private final int errorPercentage;
    
    public ErrorLoadDelegate(TokenValidator tokenValidator, TokenRepository tokenRepository, int errorPercentage) {
        super(tokenValidator, tokenRepository);
        this.errorPercentage = errorPercentage;
        
        // Initialize tokens
        this.validToken = tokenRepository.getPrimaryToken();
        this.expiredToken = createExpiredToken();
        this.malformedToken = "not.a.valid.jwt.token";
        this.invalidSignatureToken = createInvalidSignatureToken();
    }
    
    /**
     * Validates a valid token.
     * 
     * @return the validated access token content
     * @throws RuntimeException if validation fails unexpectedly
     */
    public AccessTokenContent validateValid() {
        try {
            return validateToken(validToken);
        } catch (TokenValidationException e) {
            throw new RuntimeException("Unexpected validation failure for valid token", e);
        }
    }
    
    /**
     * Validates an expired token.
     * 
     * @return the exception or null if validation unexpectedly succeeds
     */
    public Object validateExpired() {
        try {
            return validateToken(expiredToken);
        } catch (TokenValidationException e) {
            return e; // Expected
        }
    }
    
    /**
     * Validates a malformed token.
     * 
     * @return the exception or null if validation unexpectedly succeeds
     */
    public Object validateMalformed() {
        try {
            return validateToken(malformedToken);
        } catch (Exception e) {
            return e; // Expected - could be various exceptions
        }
    }
    
    /**
     * Validates a token with invalid signature.
     * 
     * @return the exception or null if validation unexpectedly succeeds
     */
    public Object validateInvalidSignature() {
        try {
            return validateToken(invalidSignatureToken);
        } catch (TokenValidationException e) {
            return e; // Expected
        }
    }
    
    /**
     * Validates mixed tokens based on error percentage.
     * 
     * @param blackhole JMH blackhole to consume results
     * @return the validation result (token content or exception)
     */
    public Object validateMixed(Blackhole blackhole) {
        String token = selectToken();
        try {
            AccessTokenContent result = validateToken(token);
            if (blackhole != null) {
                blackhole.consume(result);
            }
            return result;
        } catch (Exception e) {
            if (blackhole != null) {
                blackhole.consume(e);
            }
            return e;
        }
    }
    
    /**
     * Selects a token based on the error percentage.
     * 
     * @return the selected token
     */
    public String selectToken() {
        int random = ThreadLocalRandom.current().nextInt(100);
        
        if (random < errorPercentage) {
            // Select an error token based on distribution
            int errorType = ThreadLocalRandom.current().nextInt(3);
            switch (errorType) {
                case 0:
                    return expiredToken;
                case 1:
                    return malformedToken;
                case 2:
                    return invalidSignatureToken;
            }
        }
        
        return validToken;
    }
    
    /**
     * Gets the error type for a given token.
     * 
     * @param token the token to check
     * @return the error type or "valid" if no error
     */
    public String getErrorType(String token) {
        if (token.equals(expiredToken)) {
            return "expired";
        } else if (token.equals(malformedToken)) {
            return "malformed";
        } else if (token.equals(invalidSignatureToken)) {
            return "invalid_signature";
        }
        return "valid";
    }
    
    private String createExpiredToken() {
        Instant past = Instant.now().minusSeconds(3600);
        
        return Jwts.builder()
                .issuer("benchmark-issuer")
                .subject("benchmark-user")
                .audience().add("benchmark-client").and()
                .expiration(Date.from(past)) // Already expired
                .notBefore(Date.from(past.minusSeconds(60)))
                .issuedAt(Date.from(past.minusSeconds(120)))
                .id(UUID.randomUUID().toString())
                .claim("typ", "Bearer")
                .claim("azp", "benchmark-client")
                .signWith(Jwts.SIG.HS256.key().build())
                .compact();
    }
    
    private String createInvalidSignatureToken() {
        // Take the valid token and corrupt the signature
        String[] parts = validToken.split("\\.");
        if (parts.length == 3) {
            // Modify the signature part
            return parts[0] + "." + parts[1] + ".invalidSignature123";
        }
        return "invalid.signature.token";
    }
}