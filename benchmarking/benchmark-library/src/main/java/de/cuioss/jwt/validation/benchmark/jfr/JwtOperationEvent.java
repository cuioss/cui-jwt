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
package de.cuioss.jwt.validation.benchmark.jfr;

import jdk.jfr.*;

/**
 * JFR event for tracking individual JWT operation performance.
 * This event captures timing and metadata for each JWT validation operation,
 * enabling analysis of operation variance under concurrent load.
 */
@Name("de.cuioss.jwt.Operation")
@Label("JWT Operation")
@Description("Tracks individual JWT operation performance including validation, parsing, and signature verification")
@Category({"JWT", "Performance", "Benchmark"})
@StackTrace(false)
public class JwtOperationEvent extends Event {

    @Label("Operation Type")
    @Description("Type of JWT operation (validation, parsing, signature_verification)")
    public String operationType;

    @Label("Benchmark Name")
    @Description("Name of the benchmark executing this operation")
    public String benchmarkName;

    @Label("Thread Name")
    @Description("Name of the thread executing this operation")
    public String threadName;

    @Label("Token Size")
    @Description("Size of the JWT token in bytes")
    @DataAmount
    public long tokenSize;

    @Label("Issuer")
    @Description("JWT token issuer")
    public String issuer;

    @Label("Success")
    @Description("Whether the operation completed successfully")
    public boolean success;

    @Label("Error Type")
    @Description("Type of error if operation failed")
    public String errorType;

    @Label("Cached")
    @Description("Whether the result was served from cache")
    public boolean cached;

    @Label("Concurrent Operations")
    @Description("Number of concurrent operations at the time of execution")
    public int concurrentOperations;
}