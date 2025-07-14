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
package de.cuioss.jwt.quarkus.servlet;

import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

import java.util.HashSet;
import java.util.Set;

/**
 * Custom implementation of TestHttpServerRequest that allows modifying properties for testing.
 */
public class CustomTestHttpServerRequest extends TestHttpServerRequest {
    private HttpVersion httpVersion = HttpVersion.HTTP_1_1;
    private String scheme = "http";
    private String query = null;
    private MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    private MultiMap params = MultiMap.caseInsensitiveMultiMap();
    private Set<Cookie> cookieSet = new HashSet<>();
    private HttpMethod httpMethod = HttpMethod.GET;
    private String uri = "/test";
    private String path = "/test";
    private String absoluteUri = "http://localhost/test";

    @Override
    public HttpVersion version() {
        return httpVersion;
    }

    @Override
    public HttpMethod method() {
        return httpMethod;
    }

    @Override
    public String scheme() {
        return scheme;
    }

    @Override
    public String uri() {
        return uri;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String query() {
        return query;
    }

    @Override
    public String absoluteURI() {
        return absoluteUri;
    }

    @Override
    public MultiMap headers() {
        return headers;
    }

    @Override
    public MultiMap params() {
        return params;
    }

    @Override
    public Set<Cookie> cookies() {
        return cookieSet;
    }

    // Setters for modifying properties
    public void setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setHeaders(MultiMap headers) {
        this.headers = headers;
    }

    public void setParams(MultiMap params) {
        this.params = params;
    }

    public void setCookieSet(Set<Cookie> cookieSet) {
        this.cookieSet = cookieSet;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setAbsoluteUri(String absoluteUri) {
        this.absoluteUri = absoluteUri;
    }
}