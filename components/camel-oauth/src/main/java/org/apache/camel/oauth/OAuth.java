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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OAuth {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // Camel OAuth Properties
    //
    public static final String CAMEL_OAUTH_BASE_URI = "camel.oauth.baseUri";
    public static final String CAMEL_OAUTH_CLIENT_ID = "camel.oauth.clientId";
    public static final String CAMEL_OAUTH_CLIENT_SECRET = "camel.oauth.clientSecret";
    public static final String CAMEL_OAUTH_LOGOUT_REDIRECT_URI = "camel.oauth.logout.redirectUri";
    public static final String CAMEL_OAUTH_REDIRECT_URI = "camel.oauth.redirectUri";

    // Camel OAuth Headers
    //
    public static final String CAMEL_OAUTH_SESSION_ID = "CamelOAuthSessionId";

    // Camel OAuth Cookies
    //
    public static final String CAMEL_OAUTH_COOKIE = "camel.oauth.session";

    protected OAuthConfig config;
    protected OAuthSessionStore sessionStore;

    public OAuth() {
        this.sessionStore = new InMemorySessionStore();
    }

    // Provider Config -------------------------------------------------------------------------------------------------

    public abstract void discoverOAuthConfig(CamelContext ctx) throws OAuthException;

    public OAuthConfig getOAuthConfig() {
        return config;
    }

    // OAuth & OIDC user authentication --------------------------------------------------------------------------------

    public abstract UserProfile authenticate(Credentials creds) throws OAuthException;

    // OAuth Logout ----------------------------------------------------------------------------------------------------

    public abstract String buildLogoutRequestUrl(OAuthLogoutParams params);

    // OIDC Authorization Code Flow ------------------------------------------------------------------------------------

    public abstract String buildCodeFlowAuthRequestUrl(OAuthCodeFlowParams params);

    // Session management ----------------------------------------------------------------------------------------------

    public OAuthSessionStore getSessionStore() {
        return sessionStore;
    }

    public Optional<OAuthSession> getSession(Exchange exchange) {
        return getSessionStore().getSession(exchange);
    }

    public OAuthSession createSession(Exchange exchange) {
        return getSessionStore().createSession(exchange);
    }
}
