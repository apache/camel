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

import java.security.SecureRandom;
import java.util.Base64;

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
        var postLoginUrl = getPostLoginUrl(msg);
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

    private String getPostLoginUrl(Message msg) {
        String postLoginUrl;
        var xProto = msg.getHeader("X-Forwarded-Proto", String.class);
        var xHost = msg.getHeader("X-Forwarded-Host", String.class);
        var xPort = msg.getHeader("X-Forwarded-Port", Integer.class);
        if (xProto != null && xHost != null) {
            postLoginUrl = xProto + "://" + xHost;
            if (xPort != null) {
                if (xProto.equals("https") && xPort != 443) {
                    postLoginUrl += ":" + xPort;
                }
                if (xProto.equals("http") && xPort != 80) {
                    postLoginUrl += ":" + xPort;
                }
            }
            var httpUri = msg.getHeader(Exchange.HTTP_URI, String.class);
            if (httpUri != null && !httpUri.isEmpty()) {
                postLoginUrl += httpUri;
            }
        } else {
            postLoginUrl = msg.getHeader(Exchange.HTTP_URL, String.class);
        }
        return postLoginUrl;
    }
}
