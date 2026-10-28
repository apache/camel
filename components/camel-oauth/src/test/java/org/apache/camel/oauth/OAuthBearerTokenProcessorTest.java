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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bearer authentication on a message that carries no HTTP request, so the processor reads the message headers.
 */
class OAuthBearerTokenProcessorTest {

    @Test
    void authenticatedProfileIsSetOnTheExchange() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind(OAuth.class.getName(), new StubOAuth());
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("Authorization", "Bearer " + StubOAuth.ALICE_TOKEN);

            new OAuthBearerTokenProcessor().process(exchange);

            UserProfile profile = exchange.getProperty(OAuth.CAMEL_OAUTH_USER_PROFILE, UserProfile.class);
            assertNotNull(profile);
            assertEquals("alice", profile.subject());
            assertFalse(exchange.isRouteStop());
        }
    }

    @Test
    void noSessionIsCreated() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            OAuth oauth = new StubOAuth();
            context.getRegistry().bind(OAuth.class.getName(), oauth);
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("Authorization", "Bearer " + StubOAuth.ALICE_TOKEN);

            new OAuthBearerTokenProcessor().process(exchange);

            assertTrue(oauth.getSession(exchange).isEmpty());
            assertNull(exchange.getMessage().getHeader("Set-Cookie"));
            assertNull(exchange.getMessage().getHeader(OAuth.CAMEL_OAUTH_SESSION_ID));
        }
    }

    @Test
    void existingSessionIsLeftUnchanged() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            OAuth oauth = new StubOAuth();
            context.getRegistry().bind(OAuth.class.getName(), oauth);
            OAuthSession session = oauth.createSession(new DefaultExchange(context));
            session.putUserProfile(oauth.authenticate(new TokenCredentials(StubOAuth.BOB_TOKEN)));

            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("Cookie", OAuth.CAMEL_OAUTH_COOKIE + "=" + session.getSessionId());
            exchange.getMessage().setHeader("Authorization", "Bearer " + StubOAuth.ALICE_TOKEN);

            new OAuthBearerTokenProcessor().process(exchange);

            assertEquals("alice", exchange.getProperty(OAuth.CAMEL_OAUTH_USER_PROFILE, UserProfile.class).subject());
            assertEquals("bob", session.getUserProfile().orElseThrow().subject());
        }
    }
}
