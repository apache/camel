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
package org.apache.camel.oauth;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_COOKIE;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_SESSION_ID;

public class InMemorySessionStore implements OAuthSessionStore {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<String, OAuthSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<OAuthSession> getSession(Exchange exchange) {
        var msg = exchange.getMessage();

        // First, try the CamelOAuthSessionId header
        var sessionId = msg.getHeader(CAMEL_OAUTH_SESSION_ID, String.class);

        // Fallback to our session cookie
        if (sessionId == null) {
            var cookies = getCookies(exchange);
            if (cookies.get(CAMEL_OAUTH_COOKIE) == null) {
                log.warn("No '{}' Cookie in HTTP request", CAMEL_OAUTH_COOKIE);
                return Optional.empty();
            }
            sessionId = cookies.get(CAMEL_OAUTH_COOKIE);
        }

        var maybeSession = Optional.ofNullable(sessions.get(sessionId));
        if (maybeSession.isEmpty()) {
            log.warn("No OAuthSession found for cookie");
        }

        return maybeSession;
    }

    @Override
    public OAuthSession createSession(Exchange exchange) {

        var sessionId = UUID.randomUUID().toString();
        var session = new InMemorySession(sessionId);
        sessions.put(sessionId, session);

        var msg = exchange.getMessage();
        log.debug("Created OAuthSession");

        setSessionCookie(msg, session);
        msg.setHeader(OAuth.CAMEL_OAUTH_SESSION_ID, sessionId);

        return session;
    }

    @Override
    public OAuthSession rotateSession(Exchange exchange, OAuthSession session) {
        if (session == null) {
            return createSession(exchange);
        }
        var oldSessionId = session.getSessionId();
        sessions.remove(oldSessionId);

        var newSessionId = UUID.randomUUID().toString();
        var newSession = new InMemorySession(newSessionId);
        if (session instanceof InMemorySession inMemorySession) {
            newSession.putAll(inMemorySession.getValues());
        }
        sessions.put(newSessionId, newSession);

        var msg = exchange.getMessage();
        log.debug("Rotated OAuthSession");

        setSessionCookie(msg, newSession);
        msg.setHeader(OAuth.CAMEL_OAUTH_SESSION_ID, newSessionId);

        return newSession;
    }

    private Map<String, String> getCookies(Exchange exchange) {

        var msg = exchange.getMessage();
        var maybeCookie = getCookieHeader(msg);
        if (maybeCookie.isEmpty()) {
            log.warn("No Cookie in HTTP request");
            return Map.of();
        }

        var value = maybeCookie.get();
        var cookieMap = Arrays.stream(value.split(";"))
                .map(String::trim)
                .map(s -> s.split("=", 2))
                .collect(Collectors.toMap(
                        arr -> arr[0],
                        arr -> arr.length > 1 ? arr[1] : ""));

        return cookieMap;
    }

    /**
     * The Cookie header of the request, read with {@link HttpRequestHeaders} because the message headers also hold the
     * query and form parameters of the request.
     */
    private static Optional<String> getCookieHeader(Message msg) {
        var values = HttpRequestHeaders.values(msg, "Cookie");
        // HTTP/2 may send the cookies in several Cookie headers (RFC 9113 section 8.2.3)
        return values.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", values));
    }

    private void setSessionCookie(Message msg, OAuthSession session) {
        var existing = msg.getHeader("Set-Cookie", String.class);
        if (existing != null && !existing.startsWith(CAMEL_OAUTH_COOKIE + "=")) {
            throw new IllegalStateException("Duplicate 'Set-Cookie' header");
        }
        var sessionId = session.getSessionId();
        var cookieId = "%s=%s".formatted(CAMEL_OAUTH_COOKIE, sessionId);
        var cookie = cookieId + "; Path=/; HttpOnly; SameSite=None; Secure";
        msg.setHeader("Set-Cookie", cookie);
        log.debug("Set {} cookie", CAMEL_OAUTH_COOKIE);
    }
}
