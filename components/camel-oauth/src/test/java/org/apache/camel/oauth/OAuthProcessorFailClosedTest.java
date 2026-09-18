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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request the processors do not authenticate must stop the route, so that no subsequent step runs for it. These are
 * the paths that return before any identity provider is contacted, so they can be exercised without one.
 */
class OAuthProcessorFailClosedTest {

    @Test
    void missingAuthorizationHeaderStopsTheRoute() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);

            new OAuthBearerTokenProcessor().process(exchange);

            assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(401);
            assertThat(exchange.getMessage().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(exchange.isRouteStop()).isTrue();
        }
    }

    @Test
    void nonBearerAuthorizationHeaderStopsTheRoute() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("Authorization", "Basic c2NvdHQ6c2VjcmV0");

            new OAuthBearerTokenProcessor().process(exchange);

            assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(401);
            assertThat(exchange.getMessage().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(exchange.isRouteStop()).isTrue();
        }
    }

    @Test
    void missingAuthorizationCodeStopsTheRoute() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);

            new OAuthCodeFlowCallback().process(exchange);

            assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
            assertThat(exchange.isRouteStop()).isTrue();
        }
    }

    /**
     * The state check runs before the authorization code is redeemed, so these paths are reachable without an identity
     * provider. Without the check, an authorization code obtained in any browser could be redeemed at this callback and
     * bound to whichever session presented it.
     */
    @Test
    void aCallbackWithNoFlowInProgressStopsTheRoute() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("code", "an-authorization-code");
            exchange.getMessage().setHeader("state", "a-state-we-never-issued");

            bindTestOAuth(context);
            new OAuthCodeFlowCallback().process(exchange);

            assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
            assertThat(exchange.getMessage().getBody()).isEqualTo("No authorization code flow in progress");
            assertThat(exchange.isRouteStop()).isTrue();
        }
    }

    @Test
    void aCallbackWithTheWrongStateStopsTheRoute() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("code", "an-authorization-code");
            exchange.getMessage().setHeader("state", "not-the-issued-state");

            OAuth oauth = bindTestOAuth(context);
            oauth.getOrCreateSession(exchange).putValue(OAuthSession.OAUTH_STATE, "the-issued-state");
            new OAuthCodeFlowCallback().process(exchange);

            assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
            assertThat(exchange.getMessage().getBody()).isEqualTo("Authorization state mismatch");
            assertThat(exchange.isRouteStop()).isTrue();
        }
    }

    @Test
    void aCallbackWithNoStateAtAllStopsTheRoute() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader("code", "an-authorization-code");

            OAuth oauth = bindTestOAuth(context);
            oauth.getOrCreateSession(exchange).putValue(OAuthSession.OAUTH_STATE, "the-issued-state");
            new OAuthCodeFlowCallback().process(exchange);

            assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
            assertThat(exchange.getMessage().getBody()).isEqualTo("Authorization state mismatch");
            assertThat(exchange.isRouteStop()).isTrue();
        }
    }

    /**
     * The state check runs before the authorization code is redeemed, so none of the abstract operations are reached -
     * which is part of what these tests assert.
     */
    private static OAuth bindTestOAuth(DefaultCamelContext context) {
        OAuth oauth = new OAuth() {
            @Override
            public void discoverOAuthConfig(CamelContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UserProfile authenticate(Credentials creds) {
                throw new UnsupportedOperationException("the authorization code must not be redeemed");
            }

            @Override
            public String buildLogoutRequestUrl(OAuthLogoutParams params) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String buildCodeFlowAuthRequestUrl(OAuthCodeFlowParams params) {
                throw new UnsupportedOperationException();
            }
        };
        context.getRegistry().bind(OAuth.class.getName(), oauth);
        return oauth;
    }
}
