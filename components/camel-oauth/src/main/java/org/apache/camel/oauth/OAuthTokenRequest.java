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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared utility for making OAuth 2.0 token requests using the client_credentials grant.
 * <p/>
 * Used by both {@link OAuthClientCredentialsProcessor} (SPI path) and
 * {@link org.apache.camel.oauth.jakarta.ServletOAuth} (full OIDC path) to avoid duplicating HTTP POST logic.
 */
public final class OAuthTokenRequest {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthTokenRequest.class);

    private OAuthTokenRequest() {
    }

    /**
     * Acquires an access token using the OAuth 2.0 Client Credentials grant.
     *
     * @param  tokenEndpoint  the token endpoint URL
     * @param  clientId       the client identifier
     * @param  clientSecret   the client secret
     * @param  scope          the requested scope (may be null)
     * @return                the parsed JSON response containing access_token, expires_in, etc.
     * @throws OAuthException if the token request fails
     */
    public static JsonObject clientCredentialsGrant(
            String tokenEndpoint, String clientId, String clientSecret, String scope) {
        try {
            String auth = clientId + ":" + clientSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            Request request = Request.post(tokenEndpoint)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Authorization", "Basic " + encodedAuth);

            List<BasicNameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "client_credentials"));
            if (scope != null && !scope.isBlank()) {
                params.add(new BasicNameValuePair("scope", scope));
            }
            request.bodyForm(params.toArray(new BasicNameValuePair[0]));

            String content = request.execute().returnContent().asString();
            LOG.debug("Token response from {}", tokenEndpoint);
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            throw new OAuthException("Failed to acquire OAuth token from " + tokenEndpoint, e);
        }
    }

    /**
     * Refreshes an access token using the refresh_token grant.
     *
     * @param  tokenEndpoint  the token endpoint URL
     * @param  clientId       the client identifier
     * @param  clientSecret   the client secret
     * @param  refreshToken   the refresh token
     * @return                the parsed JSON response containing the new access_token
     * @throws OAuthException if the refresh request fails
     */
    public static JsonObject refreshTokenGrant(
            String tokenEndpoint, String clientId, String clientSecret, String refreshToken) {
        try {
            String auth = clientId + ":" + clientSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            String content = Request.post(tokenEndpoint)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Authorization", "Basic " + encodedAuth)
                    .bodyForm(
                            new BasicNameValuePair("grant_type", "refresh_token"),
                            new BasicNameValuePair("token", refreshToken))
                    .execute().returnContent().asString();

            LOG.debug("Refresh token response from {}", tokenEndpoint);
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            throw new OAuthException("Failed to refresh OAuth token from " + tokenEndpoint, e);
        }
    }
}
