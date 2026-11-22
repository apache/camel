/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf.platform.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

/**
 * The servlet request that CXF reads, over a Vert.x request. Only what CXF uses is supported: the request line,
 * headers, query parameters, the body as a stream, the addresses, and the user that the authentication of the
 * platform-http server has set. There are no sessions, no dispatchers, no form parameters and no parts.
 */
final class VertxServletRequest implements HttpServletRequest {

    private static final AtomicLong REQUEST_IDS = new AtomicLong();

    private final RoutingContext rc;
    private final HttpServerRequest request;
    private final ServletInputStream in;
    private final String contextPath;
    private final String servletPath;
    private final String pathInfo;
    private final boolean useXForwardedHeaders;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final String requestId = Long.toString(REQUEST_IDS.incrementAndGet());
    private String characterEncoding;
    private BufferedReader reader;

    VertxServletRequest(RoutingContext rc, ServletInputStream in, String contextPath, String servletPath,
                        String pathInfo, boolean useXForwardedHeaders) {
        this.rc = rc;
        this.request = rc.request();
        this.in = in;
        this.contextPath = contextPath;
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
        this.useXForwardedHeaders = useXForwardedHeaders;
    }

    // attributes

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public void setAttribute(String name, Object o) {
        if (o == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, o);
        }
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    // body

    @Override
    public String getCharacterEncoding() {
        if (characterEncoding == null) {
            String type = getContentType();
            if (type != null) {
                int pos = type.toLowerCase(Locale.ROOT).indexOf("charset=");
                if (pos >= 0) {
                    String charset = type.substring(pos + 8).trim();
                    int end = charset.indexOf(';');
                    characterEncoding = (end >= 0 ? charset.substring(0, end) : charset).trim().replace("\"", "");
                }
            }
        }
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        if (reader == null) {
            if (!Charset.isSupported(env)) {
                throw new UnsupportedEncodingException(env);
            }
            this.characterEncoding = env;
        }
    }

    @Override
    public int getContentLength() {
        long len = getContentLengthLong();
        return len > Integer.MAX_VALUE ? -1 : (int) len;
    }

    @Override
    public long getContentLengthLong() {
        String value = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String getContentType() {
        return request.getHeader(HttpHeaders.CONTENT_TYPE);
    }

    @Override
    public ServletInputStream getInputStream() {
        return in;
    }

    @Override
    public BufferedReader getReader() {
        if (reader == null) {
            String charset = getCharacterEncoding();
            reader = new BufferedReader(
                    new InputStreamReader(in, charset != null ? Charset.forName(charset) : StandardCharsets.ISO_8859_1));
        }
        return reader;
    }

    // parameters: only those of the query, as a form body is the SOAP message

    @Override
    public String getParameter(String name) {
        return rc.queryParams().get(name);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(rc.queryParams().names());
    }

    @Override
    public String[] getParameterValues(String name) {
        List<String> values = rc.queryParams().getAll(name);
        return values.isEmpty() ? null : values.toArray(new String[0]);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> answer = new LinkedHashMap<>();
        MultiMap params = rc.queryParams();
        for (String name : params.names()) {
            answer.put(name, params.getAll(name).toArray(new String[0]));
        }
        return answer;
    }

    // the request line and the addresses

    @Override
    public String getProtocol() {
        HttpVersion version = request.version();
        if (version == HttpVersion.HTTP_2) {
            return "HTTP/2.0";
        }
        return version == HttpVersion.HTTP_1_0 ? "HTTP/1.0" : "HTTP/1.1";
    }

    @Override
    public String getScheme() {
        String proto = forwarded("X-Forwarded-Proto");
        return proto != null ? proto.toLowerCase(Locale.ROOT) : request.scheme();
    }

    @Override
    public String getServerName() {
        String host = forwarded("X-Forwarded-Host");
        if (host != null) {
            return HostAndPort.parseAuthority(host, -1) != null ? HostAndPort.parseAuthority(host, -1).host() : host;
        }
        HostAndPort authority = request.authority();
        if (authority != null) {
            return authority.host();
        }
        SocketAddress local = request.localAddress();
        return local != null ? local.host() : "localhost";
    }

    @Override
    public int getServerPort() {
        if (useXForwardedHeaders) {
            String port = forwarded("X-Forwarded-Port");
            if (port != null) {
                try {
                    return Integer.parseInt(port.trim());
                } catch (NumberFormatException e) {
                    // use the host or the scheme
                }
            }
            String host = forwarded("X-Forwarded-Host");
            if (host != null) {
                HostAndPort hp = HostAndPort.parseAuthority(host, -1);
                if (hp != null && hp.port() > 0) {
                    return hp.port();
                }
                return defaultPort(getScheme());
            }
            if (forwarded("X-Forwarded-Proto") != null) {
                return defaultPort(getScheme());
            }
        }
        HostAndPort authority = request.authority();
        if (authority != null) {
            return authority.port() > 0 ? authority.port() : defaultPort(getScheme());
        }
        return getLocalPort();
    }

    @Override
    public String getRemoteAddr() {
        String forwardedFor = forwarded("X-Forwarded-For");
        if (forwardedFor != null) {
            return forwardedFor;
        }
        SocketAddress remote = request.remoteAddress();
        return remote != null ? remote.hostAddress() : null;
    }

    @Override
    public String getRemoteHost() {
        return getRemoteAddr();
    }

    @Override
    public int getRemotePort() {
        SocketAddress remote = request.remoteAddress();
        return remote != null ? remote.port() : -1;
    }

    @Override
    public String getLocalName() {
        SocketAddress local = request.localAddress();
        return local != null ? local.host() : null;
    }

    @Override
    public String getLocalAddr() {
        SocketAddress local = request.localAddress();
        return local != null ? local.hostAddress() : null;
    }

    @Override
    public int getLocalPort() {
        SocketAddress local = request.localAddress();
        return local != null ? local.port() : -1;
    }

    @Override
    public boolean isSecure() {
        return "https".equals(getScheme());
    }

    @Override
    public String getMethod() {
        return request.method().name();
    }

    @Override
    public String getRequestURI() {
        return request.path();
    }

    @Override
    public StringBuffer getRequestURL() {
        String scheme = getScheme();
        int port = getServerPort();
        StringBuffer url = new StringBuffer();
        url.append(scheme).append("://");
        String host = getServerName();
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            // an IPv6 address
            url.append('[').append(host).append(']');
        } else {
            url.append(host);
        }
        if (port > 0 && port != defaultPort(scheme)) {
            url.append(':').append(port);
        }
        url.append(getRequestURI());
        return url;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getServletPath() {
        return servletPath;
    }

    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getQueryString() {
        return request.query();
    }

    // headers

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return Collections.enumeration(request.headers().getAll(name));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(request.headers().names());
    }

    @Override
    public long getDateHeader(String name) {
        String value = request.getHeader(name);
        if (value == null) {
            return -1;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Header " + name + " is not a date: " + value, e);
        }
    }

    @Override
    public int getIntHeader(String name) {
        String value = request.getHeader(name);
        return value == null ? -1 : Integer.parseInt(value.trim());
    }

    @Override
    public Locale getLocale() {
        return getLocales().nextElement();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        String value = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (value != null) {
            try {
                List<Locale> locales = Locale.LanguageRange.parse(value).stream()
                        .map(range -> Locale.forLanguageTag(range.getRange()))
                        .toList();
                if (!locales.isEmpty()) {
                    return Collections.enumeration(locales);
                }
            } catch (IllegalArgumentException e) {
                // use the default
            }
        }
        return Collections.enumeration(List.of(Locale.getDefault()));
    }

    @Override
    public Cookie[] getCookies() {
        Collection<io.vertx.core.http.Cookie> cookies = request.cookies();
        if (cookies.isEmpty()) {
            return null;
        }
        return cookies.stream().map(c -> new Cookie(c.getName(), c.getValue())).toArray(Cookie[]::new);
    }

    // the user of the authentication of the platform-http server

    @Override
    public String getAuthType() {
        return rc.user() != null ? HttpServletRequest.BASIC_AUTH : null;
    }

    @Override
    public String getRemoteUser() {
        User user = rc.user();
        return user != null ? user.subject() : null;
    }

    @Override
    public Principal getUserPrincipal() {
        String name = getRemoteUser();
        return name != null ? new PlatformHttpPrincipal(name) : null;
    }

    @Override
    public boolean isUserInRole(String role) {
        // the roles of the user are not known here
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) {
        return rc.user() != null;
    }

    @Override
    public void login(String username, String password) {
        throw new UnsupportedOperationException("Login is done by the authentication of the platform-http server");
    }

    @Override
    public void logout() {
        throw new UnsupportedOperationException("Logout is not supported");
    }

    // sessions are not supported

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public HttpSession getSession(boolean create) {
        if (create) {
            throw new UnsupportedOperationException("HTTP sessions are not supported");
        }
        return null;
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    @Override
    public String changeSessionId() {
        throw new IllegalStateException("There is no HTTP session");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    // not supported: parts, upgrades, dispatchers and asynchronous processing

    @Override
    public Collection<Part> getParts() {
        throw new UnsupportedOperationException("Parts are not supported");
    }

    @Override
    public Part getPart(String name) {
        throw new UnsupportedOperationException("Parts are not supported");
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
        throw new UnsupportedOperationException("Upgrades are not supported");
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public AsyncContext startAsync() {
        throw new IllegalStateException("Asynchronous processing is not supported");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        throw new IllegalStateException("Asynchronous processing is not supported");
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        // CXF processes the request on this (worker) thread
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new IllegalStateException("Asynchronous processing is not supported");
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public String getProtocolRequestId() {
        return "";
    }

    @Override
    public ServletConnection getServletConnection() {
        return null;
    }

    private String forwarded(String header) {
        if (!useXForwardedHeaders) {
            return null;
        }
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            return null;
        }
        // the first value is the one of the client
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private record PlatformHttpPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
