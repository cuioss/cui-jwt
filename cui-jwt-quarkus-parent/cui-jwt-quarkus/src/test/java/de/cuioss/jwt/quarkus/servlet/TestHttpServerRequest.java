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

import io.netty.handler.codec.DecoderResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLSession;
import java.util.Collections;
import java.util.Set;

/**
 * Minimal implementation of HttpServerRequest for testing.
 * This provides just enough implementation to satisfy the constructor requirements
 * for {@link VertxHttpServletRequestAdapter}.
 *
 * <p>This implementation is designed to be used in unit tests where a real
 * Vertx environment is not available but a HttpServerRequest is needed.</p>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@SuppressWarnings({"removal"})
public class TestHttpServerRequest implements HttpServerRequest {
    @Override
    public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
        return this;
    }

    @Override
    public HttpServerRequest handler(Handler<Buffer> handler) {
        return this;
    }

    @Override
    public HttpServerRequest pause() {
        return this;
    }

    @Override
    public HttpServerRequest resume() {
        return this;
    }

    @Override
    public HttpServerRequest fetch(long amount) {
        return this;
    }

    @Override
    public HttpServerRequest endHandler(Handler<Void> endHandler) {
        return this;
    }

    @Override
    public HttpVersion version() {
        return HttpVersion.HTTP_1_1;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.GET;
    }

    @Override
    public boolean isSSL() {
        return false;
    }

    @Override
    public String scheme() {
        return "http";
    }

    @Override
    public String uri() {
        return "/test";
    }

    @Override
    public String path() {
        return "/test";
    }

    @Override
    public String query() {
        return null;
    }

    @Override
    public String host() {
        return "localhost";
    }

    @Override
    public long bytesRead() {
        return 0;
    }

    @Override
    public HostAndPort authority() {
        return null;
    }

    @Override
    public MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap();
    }

    @Override
    public String getHeader(String headerName) {
        return null;
    }

    @Override
    public String getHeader(CharSequence headerName) {
        return null;
    }

    @Override
    public HttpServerRequest setParamsCharset(String charset) {
        return this;
    }

    @Override
    public String getParamsCharset() {
        return "";
    }

    @Override
    public MultiMap params() {
        return MultiMap.caseInsensitiveMultiMap();
    }

    @Override
    public MultiMap params(boolean semicolonIsNormalChar) {
        return MultiMap.caseInsensitiveMultiMap();
    }

    @Override
    public String getParam(String paramName) {
        return null;
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    public SocketAddress localAddress() {
        return null;
    }

    @Override
    public SSLSession sslSession() {
        return null;
    }

    @Override
    public javax.security.cert.X509Certificate[] peerCertificateChain() {
        return new javax.security.cert.X509Certificate[0];
    }

    @Override
    public String absoluteURI() {
        return "http://localhost/test";
    }

    @Override
    public HttpServerRequest bodyHandler(Handler<Buffer> bodyHandler) {
        return this;
    }

    @Override
    public Future<Buffer> body() {
        return null;
    }

    @Override
    public Future<Void> end() {
        return null;
    }

    @Override
    public Future<NetSocket> toNetSocket() {
        return null;
    }

    @Override
    public HttpServerResponse response() {
        return null;
    }

    @Override
    public HttpServerRequest setExpectMultipart(boolean expect) {
        return this;
    }

    @Override
    public boolean isExpectMultipart() {
        return false;
    }

    @Override
    public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> uploadHandler) {
        return this;
    }

    @Override
    public MultiMap formAttributes() {
        return MultiMap.caseInsensitiveMultiMap();
    }

    @Override
    public String getFormAttribute(String attributeName) {
        return null;
    }

    @Override
    public int streamId() {
        return 0;
    }

    @Override
    public Future<ServerWebSocket> toWebSocket() {
        return null;
    }

    @Override
    public boolean isEnded() {
        return false;
    }

    @Override
    public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
        return this;
    }

    @Override
    public HttpConnection connection() {
        return null;
    }

    @Override
    public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
        return this;
    }

    @Override
    public DecoderResult decoderResult() {
        return null;
    }

    @Override
    public Cookie getCookie(String name) {
        return null;
    }

    @Override
    public Cookie getCookie(String name, String domain, String path) {
        return null;
    }

    @Override
    public Set<Cookie> cookies() {
        return Collections.emptySet();
    }

    @Override
    public Set<Cookie> cookies(String name) {
        return Collections.emptySet();
    }
}
