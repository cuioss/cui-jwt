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
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.tools.logging.CuiLogger;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Comprehensive benchmark comparing ES256 vs RS256 JWT validation pipeline performance.
 * 
 * <p>This benchmark addresses the critical ES256 performance issue where ES256 JWT validation
 * shows 587ms P95 overhead compared to RS256's ~170ms overhead. Initial investigation revealed
 * that isolated components (signature format conversion, ECParameterSpec initialization) are
 * fast, indicating the bottleneck exists in the complete validation pipeline.</p>
 * 
 * <p><strong>Performance Investigation Status:</strong></p>
 * <ul>
 *   <li>EcdsaSignatureFormatConverter: 2.8 μs (NOT the bottleneck)</li>
 *   <li>ECParameterSpec initialization: 3.1 μs (NOT the bottleneck)</li>
 *   <li>ES256 complete validation: 587ms P95 (bottleneck source unknown)</li>
 * </ul>
 * 
 * <p><strong>Benchmark Objectives:</strong></p>
 * <ul>
 *   <li>Compare ES256 vs RS256 end-to-end validation performance</li>
 *   <li>Identify pipeline stages causing ES256 performance degradation</li>
 *   <li>Measure validation throughput under concurrent load</li>
 *   <li>Provide performance baseline for optimization efforts</li>
 * </ul>
 * 
 * <p><strong>Expected Results:</strong></p>
 * <ul>
 *   <li>ES256 should be 5-10x faster than RS256 (cryptographically)</li>
 *   <li>Target ES256 performance: &lt;50ms processing overhead</li>
 *   <li>Current ES256 performance: 587ms P95 (12x slower than target)</li>
 * </ul>
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
@State(Scope.Benchmark)
@SuppressWarnings("java:S112")
public class ES256ValidationPipelineBenchmark {

    private static final CuiLogger LOGGER = new CuiLogger(ES256ValidationPipelineBenchmark.class);

    // RS256 validation components
    private TokenValidator rs256TokenValidator;
    private String rs256AccessToken;
    private String[] rs256TokenPool;

    // ES256 validation components  
    private TokenValidator es256TokenValidator;
    private String es256AccessToken;
    private String[] es256TokenPool;

    private static final int TOKEN_POOL_SIZE = 20;
    private int tokenIndex = 0;

    @Setup(Level.Trial)
    public void setup() {
        LOGGER.info("Initializing ES256 vs RS256 validation pipeline benchmark");
        
        try {
            // Setup RS256 validation pipeline
            setupRs256ValidationPipeline();
            
            // Setup ES256 validation pipeline
            setupEs256ValidationPipeline();
            
            LOGGER.info("Benchmark initialization complete - RS256 and ES256 validators ready");
            
        } catch (Exception e) {
            LOGGER.error("Failed to initialize benchmark setup", e);
            throw new RuntimeException("Benchmark setup failed", e);
        }
    }

    /**
     * Initializes RS256 JWT validation pipeline with test tokens and validator.
     * Uses default RS256 algorithm configuration for baseline performance measurement.
     */
    private void setupRs256ValidationPipeline() {
        LOGGER.debug("Setting up RS256 validation pipeline");
        
        // Generate RS256 test tokens using existing test infrastructure (RS256 is default)
        TestTokenHolder baseRs256Token = TestTokenGenerators.accessTokens().next();
        IssuerConfig rs256IssuerConfig = baseRs256Token.getIssuerConfig();
        
        rs256TokenValidator = new TokenValidator(rs256IssuerConfig);
        rs256AccessToken = baseRs256Token.getRawToken();
        
        // Generate RS256 token pool for concurrent testing
        rs256TokenPool = new String[TOKEN_POOL_SIZE];
        for (int i = 0; i < TOKEN_POOL_SIZE; i++) {
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            rs256TokenPool[i] = tokenHolder.getRawToken();
        }
        
        LOGGER.debug("RS256 validation pipeline setup complete - {} RS256 tokens generated", TOKEN_POOL_SIZE + 1);
    }

    /**
     * Initializes ES256 JWT validation pipeline with test tokens and validator.
     * Uses ES256 algorithm configuration to generate ECDSA tokens for performance testing.
     */
    private void setupEs256ValidationPipeline() {
        LOGGER.debug("Setting up ES256 validation pipeline");
        
        // Generate ES256 test tokens using TestTokenHolder with ES256 configuration
        TestTokenHolder baseEs256Token = TestTokenGenerators.accessTokens().next()
                .withES256IeeeP1363Format(); // Configure for ES256 ECDSA signing
        
        IssuerConfig es256IssuerConfig = baseEs256Token.getIssuerConfig();
        
        es256TokenValidator = new TokenValidator(es256IssuerConfig);
        es256AccessToken = baseEs256Token.getRawToken();
        
        // Generate ES256 token pool for concurrent testing
        es256TokenPool = new String[TOKEN_POOL_SIZE];
        for (int i = 0; i < TOKEN_POOL_SIZE; i++) {
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next()
                    .withES256IeeeP1363Format(); // Ensure each token uses ES256 algorithm
            es256TokenPool[i] = tokenHolder.getRawToken();
        }
        
        LOGGER.debug("ES256 validation pipeline setup complete - {} ES256 tokens generated", TOKEN_POOL_SIZE + 1);
    }

    /**
     * Measures RS256 JWT validation average time.
     * 
     * <p>This provides the baseline performance for comparison with ES256.
     * Expected performance: ~170ms processing overhead based on integration tests.</p>
     * 
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public AccessTokenContent measureRS256AverageTime() {
        try {
            return rs256TokenValidator.createAccessToken(rs256AccessToken);
        } catch (TokenValidationException e) {
            LOGGER.error("RS256 validation failed during average time measurement", e);
            throw new RuntimeException("Unexpected RS256 validation failure", e);
        }
    }

    /**
     * Measures ES256 JWT validation average time.
     * 
     * <p>This is the critical measurement to identify the ES256 performance bottleneck.
     * Current performance: 587ms processing overhead (3x slower than RS256).
     * Target performance: &lt;50ms processing overhead.</p>
     * 
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public AccessTokenContent measureES256AverageTime() {
        try {
            return es256TokenValidator.createAccessToken(es256AccessToken);
        } catch (TokenValidationException e) {
            LOGGER.error("ES256 validation failed during average time measurement", e);
            throw new RuntimeException("Unexpected ES256 validation failure", e);
        }
    }

    /**
     * Measures RS256 JWT validation throughput under concurrent load.
     * 
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureRS256Throughput() {
        try {
            return rs256TokenValidator.createAccessToken(rs256AccessToken);
        } catch (TokenValidationException e) {
            LOGGER.error("RS256 validation failed during throughput measurement", e);
            throw new RuntimeException("Unexpected RS256 validation failure", e);
        }
    }

    /**
     * Measures ES256 JWT validation throughput under concurrent load.
     * 
     * <p>This measurement will reveal the throughput impact of the ES256 performance issue.
     * Expected to be significantly lower than RS256 due to the 587ms average processing time.</p>
     * 
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public AccessTokenContent measureES256Throughput() {
        try {
            return es256TokenValidator.createAccessToken(es256AccessToken);
        } catch (TokenValidationException e) {
            LOGGER.error("ES256 validation failed during throughput measurement", e);
            throw new RuntimeException("Unexpected ES256 validation failure", e);
        }
    }

    /**
     * Measures RS256 concurrent validation performance with token rotation.
     * 
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public AccessTokenContent measureRS256ConcurrentValidation() {
        try {
            String token = rs256TokenPool[tokenIndex++ % TOKEN_POOL_SIZE];
            return rs256TokenValidator.createAccessToken(token);
        } catch (TokenValidationException e) {
            LOGGER.error("RS256 concurrent validation failed", e);
            throw new RuntimeException("Unexpected RS256 concurrent validation failure", e);
        }
    }

    /**
     * Measures ES256 concurrent validation performance with token rotation.
     * 
     * <p>This test will help determine if the ES256 performance issue is consistent
     * across different tokens or if it's related to specific token characteristics.</p>
     * 
     * @return validated access token content
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public AccessTokenContent measureES256ConcurrentValidation() {
        try {
            String token = es256TokenPool[tokenIndex++ % TOKEN_POOL_SIZE];
            return es256TokenValidator.createAccessToken(token);
        } catch (TokenValidationException e) {
            LOGGER.error("ES256 concurrent validation failed", e);
            throw new RuntimeException("Unexpected ES256 concurrent validation failure", e);
        }
    }

    /**
     * Calculates relative performance score comparing ES256 to RS256.
     * 
     * <p>Performance ratio calculation:
     * <ul>
     *   <li>Ratio &lt; 1.0: ES256 is faster than RS256 (expected)</li>
     *   <li>Ratio &gt; 1.0: ES256 is slower than RS256 (current problem)</li>
     *   <li>Current observed ratio: ~3.4x (ES256 3.4x slower than RS256)</li>
     * </ul></p>
     * 
     * @param es256AvgTimeMs ES256 average validation time in milliseconds
     * @param rs256AvgTimeMs RS256 average validation time in milliseconds
     * @return performance ratio (ES256/RS256)
     */
    public static double calculateES256PerformanceRatio(double es256AvgTimeMs, double rs256AvgTimeMs) {
        if (rs256AvgTimeMs <= 0) {
            throw new IllegalArgumentException("RS256 average time must be positive");
        }
        return es256AvgTimeMs / rs256AvgTimeMs;
    }

    /**
     * Analyzes benchmark results to identify performance bottleneck characteristics.
     * 
     * @param es256ThroughputOps ES256 operations per second
     * @param rs256ThroughputOps RS256 operations per second
     * @param es256AvgTimeMs ES256 average time in milliseconds
     * @param rs256AvgTimeMs RS256 average time in milliseconds
     * @return performance analysis summary
     */
    public static String analyzePerformanceResults(double es256ThroughputOps, double rs256ThroughputOps, 
                                                  double es256AvgTimeMs, double rs256AvgTimeMs) {
        double performanceRatio = calculateES256PerformanceRatio(es256AvgTimeMs, rs256AvgTimeMs);
        double throughputRatio = rs256ThroughputOps / es256ThroughputOps;
        
        StringBuilder analysis = new StringBuilder();
        analysis.append("Performance Analysis:\n");
        analysis.append(String.format("ES256 avg time: %.2f ms\n", es256AvgTimeMs));
        analysis.append(String.format("RS256 avg time: %.2f ms\n", rs256AvgTimeMs));
        analysis.append(String.format("Performance ratio (ES256/RS256): %.2fx\n", performanceRatio));
        analysis.append(String.format("Throughput ratio (RS256/ES256): %.2fx\n", throughputRatio));
        
        if (performanceRatio > 2.0) {
            analysis.append("CRITICAL: ES256 is significantly slower than RS256 (>2x)\n");
            analysis.append("This contradicts cryptographic expectations - ES256 should be faster\n");
        } else if (performanceRatio > 1.2) {
            analysis.append("WARNING: ES256 is slower than RS256\n");
        } else if (performanceRatio < 0.5) {
            analysis.append("EXCELLENT: ES256 is significantly faster than RS256 (as expected)\n");
        } else {
            analysis.append("ACCEPTABLE: ES256 and RS256 performance are comparable\n");
        }
        
        return analysis.toString();
    }
}