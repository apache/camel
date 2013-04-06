/**
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
package org.apache.camel.component.gae.auth;

import java.io.IOException;

import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;

import org.apache.camel.Exchange;
import org.apache.camel.component.gae.bind.OutboundBinding;

/**
 * Binds {@link GoogleOAuthParameters} to a Camel {@link Exchange}. This binding
 * is used by <code>gauth:upgrade</code> endpoints by default.
 */
public class GAuthUpgradeBinding implements OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> {

    /**
     * Name of the Camel header containing an access token. 
     */
    public static final String GAUTH_ACCESS_TOKEN = "CamelGauthAccessToken";

    /**
     * Name of the Camel header containing an access token secret. 
     */
    public static final String GAUTH_ACCESS_TOKEN_SECRET = "CamelGauthAccessTokenSecret";

    /**
     * Default value for access token and access token secret in GoogleOAuthParameters
     */
    private static final String EMPTY_TOKEN = "";

    /**
     * Creates a {@link GoogleOAuthParameters} object from endpoint and
     * <code>exchange.getIn()</code> data. The created parameter object is used
     * to upgrade an authorized request token to an access token. If the
     * {@link GAuthComponent} is configured to use the HMAC_SHA1 signature
     * method, a request token secret is obtained from a
     * {@link GAuthTokenSecret#COOKIE_NAME} cookie.
     *
     * @throws GAuthException if the {@link GAuthComponent} is configured to use the
     *             HMAC_SHA1 signature method but there's no cookie with the
     *             request token secret.
     */
    public GoogleOAuthParameters writeRequest(GAuthEndpoint endpoint, Exchange exchange, GoogleOAuthParameters request) throws Exception {
        request = new GoogleOAuthParameters();
        request.setOAuthConsumerKey(endpoint.getConsumerKey());
        request.setOAuthConsumerSecret(endpoint.getConsumerSecret());
        request.setOAuthToken(exchange.getIn().getHeader("oauth_token", String.class));
        request.setOAuthVerifier(exchange.getIn().getHeader("oauth_verifier", String.class));

        if (endpoint.getComponent().getKeyLoader() == null) {
            // HMAC_SHA signature is used for getting an access token.
            // The required token secret has been previously stored as cookie.
            String cookie = exchange.getIn().getHeader("Cookie", String.class);
            GAuthTokenSecret tokenSecret = GAuthTokenSecret.fromCookie(cookie);
            if (tokenSecret == null) {
                throw new GAuthException(GAuthTokenSecret.COOKIE_NAME + " cookie doesn't exist");
            }
            request.setOAuthTokenSecret(tokenSecret.getValue());
        }
        return request;
    }

    /**
     * Creates an <code>exchange.getOut()</code> message that containing the 
     * access token and the access token secret in the message header.
     *
     * @see #GAUTH_ACCESS_TOKEN
     * @see #GAUTH_ACCESS_TOKEN_SECRET
     */
    public Exchange readResponse(GAuthEndpoint endpoint, Exchange exchange, GoogleOAuthParameters response) throws IOException {
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        exchange.getOut().setHeader(GAUTH_ACCESS_TOKEN, canonicalizeToken(response.getOAuthToken()));
        exchange.getOut().setHeader(GAUTH_ACCESS_TOKEN_SECRET, canonicalizeToken(response.getOAuthTokenSecret()));
        return exchange;
    }

    private static String canonicalizeToken(String token) {
        if (token == null) {
            return null;
        } else if (EMPTY_TOKEN.equals(token)) {
            return null;
        } else {
            return token;
        }
    }

}
