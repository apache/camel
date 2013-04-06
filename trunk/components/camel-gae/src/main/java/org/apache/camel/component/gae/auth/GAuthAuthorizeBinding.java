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

import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;

import org.apache.camel.Exchange;
import org.apache.camel.component.gae.bind.OutboundBinding;

/**
 * Binds {@link GoogleOAuthParameters} to a Camel {@link Exchange}. This binding
 * is used by <code>gauth:authorize</code> endpoints by default.
 */
public class GAuthAuthorizeBinding implements OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> {

    /**
     * Name of the Camel header defining the access scope. Overrides the scope
     * parameter defined in a <code>gauth:authorize</code> endpoint URI.
     */
    public static final String GAUTH_SCOPE = "CamelGauthScope";

    /**
     * Name of the Camel header containing a callback URL. Overrides the
     * callback parameter defined in a <code>gauth:authorize</code> endpoint
     * URI.
     */
    public static final String GAUTH_CALLBACK = "CamelGauthCallback";

    /**
     * Creates a {@link GoogleOAuthParameters} object from endpoint and
     * <code>exchange.getIn()</code> data. The created parameter object is
     * used to fetch an unauthorized request token from Google.
     * 
     * @param endpoint
     * @param exchange
     * @param request
     *            ignored.
     * @return
     */
    public GoogleOAuthParameters writeRequest(GAuthEndpoint endpoint, Exchange exchange, GoogleOAuthParameters request) {
        String callback = exchange.getIn().getHeader(GAUTH_CALLBACK, String.class);
        if (callback == null) {
            callback = endpoint.getCallback();
        }
        String scope = exchange.getIn().getHeader(GAUTH_SCOPE, String.class);
        if (scope == null) {
            scope = endpoint.getScope();
        }
        request = new GoogleOAuthParameters();
        request.setOAuthConsumerKey(endpoint.getConsumerKey());
        request.setOAuthConsumerSecret(endpoint.getConsumerSecret());
        request.setOAuthCallback(callback);
        request.setScope(scope);
        return request;
    }

    /**
     * Creates an <code>exchange.getOut()</code> message that represents an HTTP
     * redirect to Google's OAuth confirmation page. Additionally, if the
     * {@link GAuthComponent} is configured to use the HMAC_SHA1 signature
     * method, a cookie is created containing the request token secret. It is
     * needed later to upgrade an authorized request token to an access token.
     */
    public Exchange readResponse(GAuthEndpoint endpoint, Exchange exchange, GoogleOAuthParameters response) throws Exception {
        String authrUrl = endpoint.newOAuthHelper().createUserAuthorizationUrl(response);
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 302);
        exchange.getOut().setHeader("Location", authrUrl);

        if (endpoint.getComponent().getKeyLoader() == null) {
            // HMAC_SHA1 signature is used and this requires a
            // token secret. Add it to a cookie because it is
            // later needed for getting an access token.
            String secret = response.getOAuthTokenSecret();
            String cookie = new GAuthTokenSecret(secret).toCookie();
            exchange.getOut().setHeader("Set-Cookie", cookie);
        }
        return exchange;
    }

}
