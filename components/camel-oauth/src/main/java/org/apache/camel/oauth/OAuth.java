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

public abstract class OAuth {

    // Camel OAuth Properties
    //
    public static final String CAMEL_OAUTH_PROVIDER_BASE_URI = "camel.oauth.baseUri";
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

    // Provider Config -------------------------------------------------------------------------------------------------

    public abstract OAuthConfig discoverOAuthConfig(Exchange exchange) throws OAuthException;

    // OAuth & OIDC user authentication --------------------------------------------------------------------------------

    public abstract UserProfile authenticate(Credentials creds) throws OAuthException;

    public abstract UserProfile refresh(UserProfile user) throws OAuthException;

    public abstract UserProfile tokenRequest(AuthCodeCredentials params) throws OAuthException;

    // OAuth Logout ----------------------------------------------------------------------------------------------------

    public abstract String buildLogoutRequestUrl(OAuthLogoutParams params);

    // OIDC Authorization Code Flow ------------------------------------------------------------------------------------

    public abstract String buildCodeFlowAuthRequestUrl(OAuthCodeFlowParams params);

    // Session management ----------------------------------------------------------------------------------------------

    public Optional<OAuthSession> getSession(Exchange exchange) {
        return getSessionStore().getSession(exchange);
    }

    public OAuthSession createSession(Exchange exchange) {
        return getSessionStore().createSession(exchange);
    }

    public abstract OAuthSessionStore getSessionStore();
}
