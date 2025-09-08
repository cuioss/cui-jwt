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
package de.cuioss.jwt.validation.util;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.HttpStatusFamily;
import de.cuioss.tools.net.http.result.HttpErrorCategory;
import de.cuioss.tools.net.http.result.HttpResultObject;
import de.cuioss.uimodel.nameprovider.DisplayName;
import de.cuioss.uimodel.result.ResultDetail;
import de.cuioss.uimodel.result.ResultState;
import lombok.NonNull;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ETag-aware HTTP handler with stateful caching capabilities.
 * <p>
 * This component provides HTTP-based caching using ETags and "If-None-Match" headers.
 * It tracks whether content was loaded from cache (304 Not Modified) or freshly fetched (200 OK).
 * <p>
 * Thread-safe implementation using volatile fields and ReentrantLock for virtual thread compatibility.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class ETagAwareHttpHandler {

    private static final CuiLogger LOGGER = new CuiLogger(ETagAwareHttpHandler.class);


    private final HttpHandler httpHandler;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedContent;
    private volatile String cachedETag;

    /**
     * Creates a new ETag-aware HTTP handler for cache validation.
     *
     * @param httpHandler the HTTP handler for making requests
     */
    public ETagAwareHttpHandler(@NonNull HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
    }

    /**
     * Loads HTTP content, using ETag-based HTTP caching when supported.
     * 
     * <h2>Result States</h2>
     * <ul>
     *   <li><strong>VALID + 200</strong>: Content freshly loaded from server (equivalent to LOADED_FROM_SERVER)</li>
     *   <li><strong>VALID + 304</strong>: Content unchanged, using cached version (equivalent to CACHE_ETAG)</li>
     *   <li><strong>VALID + no HTTP status</strong>: Content unchanged, using local cache (equivalent to CACHE_CONTENT)</li>
     *   <li><strong>WARNING + cached content</strong>: Error occurred but using cached data (equivalent to ERROR_WITH_CACHE)</li>
     *   <li><strong>ERROR + no content</strong>: Error occurred with no fallback (equivalent to ERROR_NO_CACHE)</li>
     * </ul>
     *
     * @return HttpResultObject containing content and detailed state information, never null
     */
    public HttpResultObject<String> load() {
        lock.lock();
        try {
            HttpFetchResult result = fetchJwksContentWithCache();

            if (result.error) {
                return handleErrorResult();
            }

            if (hasCachedContentWithETag() && result.notModified) {
                return handleNotModifiedResult();
            }

            return handleSuccessResult(result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forces a reload of HTTP content, optionally clearing cache completely.
     *
     * @param clearCache if true, clears all cached content; if false, only bypasses ETag validation
     * @return HttpResultObject with fresh content or error state, never null
     */
    public HttpResultObject<String> reload(boolean clearCache) {
        lock.lock();
        try {
            if (clearCache) {
                LOGGER.debug("Clearing HTTP cache and reloading from %s", httpHandler.getUrl());
                this.cachedContent = null;
            } else {
                LOGGER.debug("Bypassing ETag validation and reloading from %s", httpHandler.getUrl());
            }
            this.cachedETag = null;

            return loadInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Internal load method that assumes the lock is already held.
     * Used by reload() to avoid recursive locking.
     */
    private HttpResultObject<String> loadInternal() {
        HttpFetchResult result = fetchJwksContentWithCache();

        if (result.error) {
            return handleErrorResult();
        }

        if (hasCachedContentWithETag() && result.notModified) {
            return handleNotModifiedResult();
        }

        return handleSuccessResult(result);
    }

    /**
     * Checks if we have both cached content and ETag available.
     */
    private boolean hasCachedContentWithETag() {
        return cachedContent != null && cachedETag != null;
    }

    /**
     * Handles error results by returning cached content if available.
     */
    private HttpResultObject<String> handleErrorResult() {
        if (cachedContent != null) {
            return new HttpResultObject<>(
                    cachedContent,
                    ResultState.WARNING, // Using cached content but with error condition
                    new ResultDetail(
                            new DisplayName("HTTP request failed, using cached content from " + httpHandler.getUrl()),
                            new Exception("HTTP request failed")),
                    HttpErrorCategory.NETWORK_ERROR,
                    cachedETag,
                    null // No HTTP status for error cases
            );
        } else {
            return HttpResultObject.error(
                    "", // Empty string as fallback when no cached content available
                    HttpErrorCategory.NETWORK_ERROR,
                    new ResultDetail(
                            new DisplayName("HTTP request failed with no cached content available from " + httpHandler.getUrl()),
                            new Exception("No cached content available"))
            );
        }
    }

    /**
     * Handles 304 Not Modified response by returning cached content.
     */
    private HttpResultObject<String> handleNotModifiedResult() {
        LOGGER.debug("HTTP content not modified (304), using cached version");
        return HttpResultObject.success(cachedContent, cachedETag, 304);
    }

    /**
     * Handles successful response by checking for content changes and updating cache.
     */
    private HttpResultObject<String> handleSuccessResult(HttpFetchResult result) {
        // Check if content actually changed despite new response
        if (cachedContent != null && cachedContent.equals(result.content)) {
            LOGGER.debug("HTTP content unchanged despite 200 OK response");
            return new HttpResultObject<>(
                    cachedContent,
                    ResultState.VALID,
                    null, // No error details for successful cache hit
                    null, // No error category
                    cachedETag,
                    null // No HTTP status for local cache operations
            );
        }

        // Update cache with fresh content
        this.cachedContent = result.content;
        this.cachedETag = result.etag; // May be null if server doesn't support ETags

        LOGGER.info(JWTValidationLogMessages.INFO.HTTP_CONTENT_LOADED.format(httpHandler.getUrl()));
        return HttpResultObject.success(result.content, result.etag, 200);
    }

    /**
     * Internal result for HTTP fetch operations.
     */
    private record HttpFetchResult(String content, String etag, boolean notModified, boolean error) {
    }

    /**
     * Fetches HTTP content from the endpoint with ETag support.
     *
     * @return HttpFetchResult with error flag set if request fails
     */
    @SuppressWarnings("java:S2095") // owolff False positive for HttpResponse since it is closed automatically
    private HttpFetchResult fetchJwksContentWithCache() {
        // Build request with conditional headers
        HttpRequest.Builder requestBuilder = httpHandler.requestBuilder();

        // Add If-None-Match header if we have a cached ETag
        if (cachedETag != null) {
            requestBuilder.header("If-None-Match", cachedETag);
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpClient client = httpHandler.createHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            HttpStatusFamily statusFamily = HttpStatusFamily.fromStatusCode(response.statusCode());

            if (response.statusCode() == 304) {
                // Not Modified - content hasn't changed
                LOGGER.debug("Received 304 Not Modified from %s", httpHandler.getUrl());
                return new HttpFetchResult(null, null, true, false);
            } else if (statusFamily == HttpStatusFamily.SUCCESS) {
                // 2xx Success - fresh content
                String content = response.body();
                String etag = response.headers().firstValue("ETag").orElse(null);

                LOGGER.debug("Received %s %s from %s with ETag: %s", response.statusCode(), statusFamily, httpHandler.getUrl(), etag);
                return new HttpFetchResult(content, etag, false, false);
            } else {
                LOGGER.warn(JWTValidationLogMessages.WARN.HTTP_STATUS_WARNING.format(response.statusCode(), statusFamily, httpHandler.getUrl()));
                return new HttpFetchResult(null, null, false, true);
            }

        } catch (IOException e) {
            LOGGER.warn(e, JWTValidationLogMessages.WARN.HTTP_FETCH_FAILED.format(httpHandler.getUrl()));
            return new HttpFetchResult(null, null, false, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn(JWTValidationLogMessages.WARN.HTTP_FETCH_INTERRUPTED.format(httpHandler.getUrl()));
            return new HttpFetchResult(null, null, false, true);
        }
    }
}