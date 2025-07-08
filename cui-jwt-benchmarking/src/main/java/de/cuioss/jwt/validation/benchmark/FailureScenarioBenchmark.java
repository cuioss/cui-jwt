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
package de.cuioss.jwt.validation.benchmark;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.ClaimControlParameter;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class FailureScenarioBenchmark {

    private TokenValidator tokenValidator;

    private String validAccessToken;
    private String expiredToken;
    private String wrongIssuerToken;
    private String wrongAudienceToken;
    private String invalidSignatureToken;
    private String malformedToken;

    @Setup
    public void setup() {
        // Create a base token holder using TestTokenGenerators
        TestTokenHolder baseTokenHolder = TestTokenGenerators.accessTokens().next();

        // Get the issuer config from the token holder
        IssuerConfig issuerConfig = baseTokenHolder.getIssuerConfig();

        // Create a token validator with the issuer config
        tokenValidator = new TokenValidator(issuerConfig);

        // Valid Access Token
        validAccessToken = baseTokenHolder.getRawToken();

        // Expired Token
        TestTokenHolder expiredTokenHolder = TestTokenGenerators.accessTokens().next();
        ClaimControlParameter expiredParams = ClaimControlParameter.builder()
                .expiredToken(true)
                .build();
        expiredToken = new TestTokenHolder(expiredTokenHolder.getTokenType(), expiredParams).getRawToken();

        // Wrong Issuer Token
        TestTokenHolder wrongIssuerTokenHolder = baseTokenHolder.regenerateClaims()
                .withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("rogue-issuer"));
        wrongIssuerToken = wrongIssuerTokenHolder.getRawToken();

        // Wrong Audience Token
        TestTokenHolder wrongAudienceTokenHolder = baseTokenHolder.regenerateClaims()
                .withClaim(ClaimName.AUDIENCE.getName(), ClaimValue.forList("rogue-audience", List.of("rogue-audience")));
        wrongAudienceToken = wrongAudienceTokenHolder.getRawToken();

        // Invalid Signature Token - use a different key ID that doesn't match the issuer config
        TestTokenHolder invalidSignatureTokenHolder = baseTokenHolder.regenerateClaims()
                .withKeyId("invalid-key-id");
        invalidSignatureToken = invalidSignatureTokenHolder.getRawToken();

        // Malformed Token
        malformedToken = "this.is.not.a.valid.jwt";
    }

    @Benchmark
    public Object validateValidAccessToken() {
        try {
            return tokenValidator.createAccessToken(validAccessToken);
        } catch (TokenValidationException e) {
            return e; // Should not happen for this benchmark
        }
    }

    @Benchmark
    public Object validateExpiredToken() {
        try {
            return tokenValidator.createAccessToken(expiredToken);
        } catch (TokenValidationException e) {
            return e;
        }
    }

    @Benchmark
    public Object validateWrongIssuerToken() {
        try {
            return tokenValidator.createAccessToken(wrongIssuerToken);
        } catch (TokenValidationException e) {
            return e;
        }
    }

    @Benchmark
    public Object validateWrongAudienceToken() {
        try {
            return tokenValidator.createAccessToken(wrongAudienceToken);
        } catch (TokenValidationException e) {
            return e;
        }
    }

    @Benchmark
    public Object validateInvalidSignatureToken() {
        try {
            return tokenValidator.createAccessToken(invalidSignatureToken);
        } catch (TokenValidationException e) {
            return e;
        }
    }

    @Benchmark
    public Object validateMalformedToken() {
        try {
            return tokenValidator.createAccessToken(malformedToken);
        } catch (TokenValidationException e) {
            return e;
        }
    }
}