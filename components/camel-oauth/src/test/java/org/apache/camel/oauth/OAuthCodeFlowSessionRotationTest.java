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

import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_COOKIE;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_REDIRECT_URI;
import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_SESSION_ID;
import static org.apache.camel.oauth.OAuthSession.OAUTH_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthCodeFlowSessionRotationTest {

    private static final String REDIRECT_URI = "https://app.example.test/auth";
    private static final String POST_LOGIN_URL = "https://app.example.test/dashboard";

    private DefaultCamelContext context;
    private StubOAuth oauth;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.getPropertiesComponent().addInitialProperty(CAMEL_OAUTH_REDIRECT_URI, REDIRECT_URI);
        oauth = new StubOAuth();
        context.getRegistry().bind(OAuth.class.getName(), oauth);
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    void inMemorySessionStoreRotateSessionReplacesIdAndTransfersValues() {
        Exchange exchange = new DefaultExchange(context);
        OAuthSession initialSession = oauth.createSession(exchange);
        String oldSessionId = initialSession.getSessionId();

        initialSession.putValue("customKey", "customValue");
        initialSession.putValue("anotherKey", 12345);

        OAuthSession rotatedSession = oauth.rotateSession(exchange, initialSession);
        String newSessionId = rotatedSession.getSessionId();

        assertNotEquals(oldSessionId, newSessionId);

        Optional<String> customKey = rotatedSession.getValue("customKey", String.class);
        assertTrue(customKey.isPresent());
        assertEquals("customValue", customKey.get());

        Optional<Integer> anotherKey = rotatedSession.getValue("anotherKey", Integer.class);
        assertTrue(anotherKey.isPresent());
        assertEquals(12345, anotherKey.get());

        // Verify the old session is no longer accessible in the store
        Exchange oldLookupExchange = new DefaultExchange(context);
        oldLookupExchange.getMessage().setHeader(CAMEL_OAUTH_SESSION_ID, oldSessionId);
        assertTrue(oauth.getSession(oldLookupExchange).isEmpty());

        // Verify the new session is retrievable
        Exchange newLookupExchange = new DefaultExchange(context);
        newLookupExchange.getMessage().setHeader(CAMEL_OAUTH_SESSION_ID, newSessionId);
        Optional<OAuthSession> retrieved = oauth.getSession(newLookupExchange);
        assertTrue(retrieved.isPresent());
        assertSame(rotatedSession, retrieved.get());

        // Verify the exchange response cookie and header were updated
        String setCookieHeader = exchange.getMessage().getHeader("Set-Cookie", String.class);
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains(CAMEL_OAUTH_COOKIE + "=" + newSessionId));
        assertFalse(setCookieHeader.contains(oldSessionId));
        assertEquals(newSessionId, exchange.getMessage().getHeader(CAMEL_OAUTH_SESSION_ID));
    }

    @Test
    void callbackRotatesSessionUponSuccessfulAuthentication() throws Exception {
        // Step 1: Pre-login initial request creates a pre-login session
        Exchange preLoginExchange = new DefaultExchange(context);
        OAuthSession preLoginSession = oauth.createSession(preLoginExchange);
        String preLoginSessionId = preLoginSession.getSessionId();

        String issuedState = "secure-random-state-1234";
        preLoginSession.putValue(OAUTH_STATE, issuedState);
        preLoginSession.putValue("OAuthPostLoginUrl", POST_LOGIN_URL);

        // Step 2: Callback completes with valid authorization code and state
        Exchange callbackExchange = new DefaultExchange(context);
        callbackExchange.getMessage().setHeader("Cookie", CAMEL_OAUTH_COOKIE + "=" + preLoginSessionId);
        callbackExchange.getMessage().setHeader("code", StubOAuth.BOB_CODE);
        callbackExchange.getMessage().setHeader("state", issuedState);

        new OAuthCodeFlowCallback().process(callbackExchange);

        // Verify redirect to post-login URL
        assertEquals(302, callbackExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(POST_LOGIN_URL, callbackExchange.getMessage().getHeader("Location"));

        // Verify new session cookie is emitted with rotated session ID
        String setCookieHeader = callbackExchange.getMessage().getHeader("Set-Cookie", String.class);
        assertNotNull(setCookieHeader);
        assertFalse(setCookieHeader.contains(preLoginSessionId));

        String prefix = CAMEL_OAUTH_COOKIE + "=";
        int start = setCookieHeader.indexOf(prefix) + prefix.length();
        int end = setCookieHeader.indexOf(';', start);
        String postLoginSessionId = end > 0 ? setCookieHeader.substring(start, end) : setCookieHeader.substring(start);

        assertNotNull(postLoginSessionId);
        assertFalse(postLoginSessionId.isEmpty());
        assertNotEquals(preLoginSessionId, postLoginSessionId);

        // Step 3: Verify the pre-login session ID cannot be reused to access protected routes
        Exchange replayExchange = new DefaultExchange(context);
        replayExchange.getMessage().setHeader("Cookie", CAMEL_OAUTH_COOKIE + "=" + preLoginSessionId);
        new OAuthCodeFlowProcessor().process(replayExchange);

        // The pre-login ID should not be authenticated, so OAuthCodeFlowProcessor redirects to IdP auth request
        assertEquals(302, replayExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        String replayLocation = replayExchange.getMessage().getHeader("Location", String.class);
        assertNotNull(replayLocation);
        assertTrue(replayLocation.contains("https://idp.example/auth?state="));
        assertTrue(replayExchange.isRouteStop());

        // Step 4: Verify the rotated post-login session ID successfully authenticates the user
        Exchange authExchange = new DefaultExchange(context);
        authExchange.getMessage().setHeader("Cookie", CAMEL_OAUTH_COOKIE + "=" + postLoginSessionId);
        new OAuthCodeFlowProcessor().process(authExchange);

        // The authenticated request should NOT be redirected and route should not be stopped
        assertNull(authExchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertFalse(authExchange.isRouteStop());

        // Stored session has bob's profile
        Exchange verifyExchange = new DefaultExchange(context);
        verifyExchange.getMessage().setHeader(CAMEL_OAUTH_SESSION_ID, postLoginSessionId);
        OAuthSession storedSession = oauth.getSession(verifyExchange).orElseThrow();
        assertTrue(storedSession.getUserProfile().isPresent());
        assertEquals("bob", storedSession.getUserProfile().get().subject());
    }
}
