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

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_REDIRECT_URI;
import static org.apache.camel.oauth.OAuthProperties.getRequiredProperty;
import static org.apache.camel.oauth.OAuthSession.OAUTH_STATE;

public class OAuthCodeFlowProcessor extends AbstractOAuthProcessor {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Logger log = LoggerFactory.getLogger(getClass());

    // every candidate origin is caller controlled, so warn at most once and keep further mismatches at DEBUG -
    // otherwise a forged Host or X-Forwarded-Host header would let anyone flood the diagnostic log
    private final AtomicBoolean foreignOriginWarned = new AtomicBoolean();

    @Override
    public void process(Exchange exchange) {
        var context = exchange.getContext();
        var msg = exchange.getMessage();

        logRequestHeaders(procName, msg);

        // Find or create the OAuth instance
        //
        var oauth = findOAuth(context).orElseGet(() -> {
            var factory = OAuthFactory.lookupFactory(context);
            return factory.createOAuth();
        });

        // Get or create the OAuthSession
        //
        var session = oauth.getOrCreateSession(exchange);

        // Authenticate an existing UserProfile from the OAuthSession
        //
        if (session.getUserProfile().isPresent()) {
            var userProfile = session.removeUserProfile().orElseThrow();
            try {
                userProfile = authenticateExistingUserProfile(oauth, userProfile);
                session.putUserProfile(userProfile);
                return;
            } catch (OAuthException ex) {
                log.error("Failed to authenticate: {}", userProfile.subject(), ex);
            }
        }

        // Fallback to the authorization code flow
        //
        var postLoginUrl = getPostLoginUrl(exchange);
        log.info("Register post login url: {}", postLoginUrl);
        session.putValue("OAuthPostLoginUrl", postLoginUrl);

        // RFC 6749 section 10.12: bind the callback to a flow this session actually started, so an
        // authorization code obtained elsewhere cannot be replayed into this session's callback.
        var state = newState();
        session.putValue(OAUTH_STATE, state);

        var redirectUri = getRequiredProperty(exchange.getContext(), CAMEL_OAUTH_REDIRECT_URI);
        var params = new OAuthCodeFlowParams().setRedirectUri(redirectUri).setState(state);
        var authRequestUrl = oauth.buildCodeFlowAuthRequestUrl(params);

        sendRedirect(msg, authRequestUrl);

        // The caller is not authenticated: the redirect is the whole response, so the protected route must not run
        exchange.setRouteStop(true);
    }

    private static String newState() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Rebuilds the absolute url the browser should be sent back to once the login completed.
     *
     * The result is later emitted as the Location header of the post login redirect, so it has to stay on the
     * deployment's own origin. Every candidate the request offers - the X-Forwarded-* headers, and the Host header
     * behind {@link Exchange#HTTP_URL} - comes from the untrusted caller, so none of them is used to build the url. The
     * origin is always taken from the operator controlled redirect uri and only the request path is carried over, which
     * means the redirect can never leave the deployment.
     *
     * This preserves CAMEL-21899: behind an ingress or an OpenShift Route the internally observed request url is not
     * the externally reachable one, and the configured redirect uri names the external address - it is the url the
     * identity provider sends the browser back to - so the post login redirect still targets the external address.
     */
    String getPostLoginUrl(Exchange exchange) {
        var msg = exchange.getMessage();
        var redirectUri = getRequiredProperty(exchange.getContext(), CAMEL_OAUTH_REDIRECT_URI);
        var expectedOrigin = originOf(redirectUri);
        if (expectedOrigin == null) {
            throw new IllegalStateException(
                    "Cannot derive an origin from " + CAMEL_OAUTH_REDIRECT_URI + ": " + redirectUri);
        }

        warnOnForeignOrigin(msg, expectedOrigin);

        return expectedOrigin + requestPath(msg);
    }

    /**
     * Warns when the origin the caller announces is not the configured one. This is purely diagnostic - the post login
     * url is built from the configured origin either way - but behind an ingress or an OpenShift Route a mismatch is
     * the usual symptom of {@link OAuth#CAMEL_OAUTH_REDIRECT_URI} not naming the address the browser actually reaches.
     * <p>
     * Every candidate origin is caller controlled, so the warning fires at most once and any further mismatch drops to
     * DEBUG - otherwise a forged Host or X-Forwarded-Host header would let anyone flood the log.
     */
    private void warnOnForeignOrigin(Message msg, String expectedOrigin) {
        var observedOrigin = forwardedOrigin(msg);
        if (observedOrigin == null) {
            // No usable X-Forwarded-* headers, fall back to the request url as observed by this instance
            observedOrigin = originOf(msg.getHeader(Exchange.HTTP_URL, String.class));
        }
        if (observedOrigin != null && !expectedOrigin.equals(observedOrigin)) {
            if (foreignOriginWarned.compareAndSet(false, true)) {
                log.warn("Post login origin {} does not match the configured {}, now using: {}."
                         + " Further mismatches are logged at DEBUG.",
                        observedOrigin, CAMEL_OAUTH_REDIRECT_URI, expectedOrigin);
            } else if (log.isDebugEnabled()) {
                log.debug("Post login origin {} does not match the configured {}, now using: {}",
                        observedOrigin, CAMEL_OAUTH_REDIRECT_URI, expectedOrigin);
            }
        }
    }

    /**
     * The origin (scheme://host[:port]) the X-Forwarded-* headers describe, or null when they are absent or unusable.
     */
    private static String forwardedOrigin(Message msg) {
        var xProto = msg.getHeader("X-Forwarded-Proto", String.class);
        var xHost = msg.getHeader("X-Forwarded-Host", String.class);
        var xPort = msg.getHeader("X-Forwarded-Port", Integer.class);
        if (xProto == null || xHost == null) {
            return null;
        }
        // Chained proxies append to these headers, the client facing entry is the first one
        var firstHost = xHost.split(",", 2)[0].trim();
        var firstProto = xProto.split(",", 2)[0].trim();
        if (firstHost.isEmpty() || firstProto.isEmpty()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(firstProto + "://" + firstHost);
        } catch (URISyntaxException ex) {
            return null;
        }
        // X-Forwarded-Host may already carry the port, in which case it wins over X-Forwarded-Port
        var port = uri.getPort() > 0 ? uri.getPort() : (xPort != null ? xPort : -1);
        return originOf(uri.getScheme(), uri.getHost(), port);
    }

    /**
     * The origin (scheme://host[:port]) of the given url, with a default port omitted, or null when the url is not
     * absolute or cannot be parsed.
     */
    private static String originOf(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException ex) {
            return null;
        }
        return originOf(uri.getScheme(), uri.getHost(), uri.getPort());
    }

    private static String originOf(String scheme, String host, int port) {
        if (scheme == null || host == null) {
            return null;
        }
        var lcScheme = scheme.toLowerCase(Locale.ROOT);
        var origin = lcScheme + "://" + host.toLowerCase(Locale.ROOT);
        if (port > 0 && !(port == 443 && lcScheme.equals("https")) && !(port == 80 && lcScheme.equals("http"))) {
            origin += ":" + port;
        }
        return origin;
    }

    /**
     * The path (and query) of the current request, never an absolute or protocol relative url, so that appending it to
     * an origin cannot move the redirect to another host.
     */
    private static String requestPath(Message msg) {
        var httpUri = msg.getHeader(Exchange.HTTP_URI, String.class);
        if (httpUri == null || httpUri.isEmpty()) {
            httpUri = msg.getHeader(Exchange.HTTP_URL, String.class);
        }
        if (httpUri == null || httpUri.isEmpty()) {
            return "";
        }
        URI uri;
        try {
            uri = new URI(httpUri);
        } catch (URISyntaxException ex) {
            return "";
        }
        var path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }
        var query = uri.getRawQuery();
        return query != null ? path + "?" + query : path;
    }
}
