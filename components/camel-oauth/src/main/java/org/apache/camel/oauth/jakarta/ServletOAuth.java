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
package org.apache.camel.oauth.jakarta;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nimbusds.jose.jwk.JWKSet;
import org.apache.camel.CamelContext;
import org.apache.camel.oauth.AuthCodeCredentials;
import org.apache.camel.oauth.ClientCredentials;
import org.apache.camel.oauth.Credentials;
import org.apache.camel.oauth.JWTOptions;
import org.apache.camel.oauth.OAuth;
import org.apache.camel.oauth.OAuthCodeFlowParams;
import org.apache.camel.oauth.OAuthCodeFlowParams.AuthRequestResponseType;
import org.apache.camel.oauth.OAuthConfig;
import org.apache.camel.oauth.OAuthException;
import org.apache.camel.oauth.OAuthFlowType;
import org.apache.camel.oauth.OAuthLogoutParams;
import org.apache.camel.oauth.TokenCredentials;
import org.apache.camel.oauth.UserCredentials;
import org.apache.camel.oauth.UserProfile;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import static org.apache.camel.oauth.OAuthProperties.getRequiredProperty;

public class ServletOAuth extends OAuth {

    @Override
    public void discoverOAuthConfig(CamelContext ctx) throws OAuthException {
        if (config == null) {
            var baseUrl = getRequiredProperty(ctx, CAMEL_OAUTH_BASE_URI);
            var clientId = getRequiredProperty(ctx, CAMEL_OAUTH_CLIENT_ID);
            var clientSecret = getRequiredProperty(ctx, CAMEL_OAUTH_CLIENT_SECRET);

            var config = new OAuthConfig()
                    .setBaseUrl(baseUrl)
                    .setClientId(clientId)
                    .setClientSecret(clientSecret);

            try {
                var oidc_discovery_path = "/.well-known/openid-configuration";
                var content = Request.get(baseUrl + oidc_discovery_path).execute().returnContent().asString();
                var json = JsonParser.parseString(content).getAsJsonObject();

                config.setAuthorizationPath(json.get("authorization_endpoint").getAsString())
                        .setTokenPath(json.get("token_endpoint").getAsString())
                        .setRevocationPath(json.get("revocation_endpoint").getAsString())
                        .setLogoutPath(json.get("end_session_endpoint").getAsString())
                        .setUserInfoPath(json.get("userinfo_endpoint").getAsString())
                        .setIntrospectionPath(json.get("introspection_endpoint").getAsString())
                        .setJwksPath(json.get("jwks_uri").getAsString());
                if (json.has("issuer")) {
                    JWTOptions jwtOptions = config.getJWTOptions();
                    jwtOptions.setIssuer(json.get("issuer").getAsString());
                }
                var jwksPath = config.getJwksPath();
                if (!jwksPath.isBlank()) {
                    config.setJWKSet(JWKSet.load(new URL(jwksPath)));
                }
            } catch (Exception ex) {
                throw new OAuthException("Cannot discover OAuth config from: " + baseUrl, ex);
            }
            this.config = config;
        }
    }

    @Override
    public String buildCodeFlowAuthRequestUrl(OAuthCodeFlowParams params) {
        if (params.getScopes() == null) {
            params.setScope("openid");
        }
        if (params.getResponseType() == null) {
            params.setResponseType(AuthRequestResponseType.CODE);
        }
        if (params.getClientId() == null) {
            params.setClientId(config.getClientId());
        }
        try {
            var uriBuilder = new URIBuilder(config.getAuthorizationPath());
            uriBuilder.addParameter("flow", OAuthFlowType.AUTH_CODE.getGrantType());
            uriBuilder.addParameter("scope", String.join(" ", params.getScopes()));
            uriBuilder.addParameter("response_type", params.getResponseType().toString().toLowerCase());
            uriBuilder.addParameter("client_id", params.getClientId());
            uriBuilder.addParameter("redirect_uri", params.getRedirectUri());
            var requestUrl = uriBuilder.build().toString();
            return requestUrl;
        } catch (URISyntaxException ex) {
            throw new OAuthException(ex);
        }
    }

    @Override
    public UserProfile authenticate(Credentials creds) throws OAuthException {

        UserProfile userProfile;
        try {
            if (creds instanceof UserCredentials params) {

                userProfile = authenticateUserCredentials(params);

            } else if (creds instanceof AuthCodeCredentials params) {

                userProfile = authenticateAuthCodeCredentials(params);

            } else if (creds instanceof TokenCredentials params) {

                userProfile = authenticateTokenCredentials(params);

            } else if (creds instanceof ClientCredentials params) {

                userProfile = authenticateClientCredentials(params);

            } else {
                throw new OAuthException("Unsupported creds: " + creds.getClass().getName());
            }
        } catch (Exception ex) {
            throw new OAuthException("Authentication failed", ex);
        }

        return userProfile;
    }

    @Override
    public String buildLogoutRequestUrl(OAuthLogoutParams params) {
        var userProfile = params.getUserProfile();
        try {
            var uriBuilder = new URIBuilder(config.getLogoutPath());
            if (userProfile.idToken().isPresent()) {
                uriBuilder.addParameter("id_token_hint", userProfile.idToken().get());
            }
            if (params.getRedirectUri() != null) {
                uriBuilder.addParameter("post_logout_redirect_uri", params.getRedirectUri());
            }
            var endSessionURL = uriBuilder.build().toString();
            return endSessionURL;
        } catch (URISyntaxException ex) {
            throw new OAuthException(ex);
        }
    }

    private UserProfile authenticateAuthCodeCredentials(AuthCodeCredentials creds) throws Exception {
        if (creds.getCode() == null || creds.getCode().isBlank()) {
            throw new OAuthException("code cannot be null or empty");
        }
        if (creds.getRedirectUri() != null && creds.getRedirectUri().isBlank()) {
            throw new OAuthException("redirectUri cannot be null or empty");
        }

        // Encode username and password in Base64 for Basic Auth
        String auth = config.getClientId() + ":" + config.getClientSecret();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        var grantType = creds.getFlowType().getGrantType();
        var content = Request.post(config.getTokenPath())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Authorization", "Basic " + encodedAuth)
                .bodyForm(
                        new BasicNameValuePair("grant_type", grantType),
                        new BasicNameValuePair("code", creds.getCode()),
                        new BasicNameValuePair("redirect_uri", creds.getRedirectUri()))
                .execute().returnContent().asString();

        var json = JsonParser.parseString(content).getAsJsonObject();
        return UserProfile.fromJson(config, json);
    }

    private UserProfile authenticateUserCredentials(UserCredentials creds) throws Exception {
        var userProfile = creds.getUserProfile();
        if (userProfile.expired() && userProfile.refreshToken().isPresent()) {

            // Encode username and password in Base64 for Basic Auth
            String auth = config.getClientId() + ":" + config.getClientSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            // Refreshing an Access Token
            // https://datatracker.ietf.org/doc/html/rfc6749#page-47
            var refreshToken = userProfile.refreshToken().get();
            var content = Request.post(config.getIntrospectionPath())
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Authorization", "Basic " + encodedAuth)
                    .bodyForm(new BasicNameValuePair("grant_type", "refresh_token"),
                            new BasicNameValuePair("token", refreshToken))
                    .execute().returnContent().asString();

            var json = JsonParser.parseString(content).getAsJsonObject();
            userProfile = UserProfile.fromJson(config, json);
        }
        if (userProfile.expired()) {
            throw new OAuthException("user access has expired");
        }
        return userProfile;
    }

    /*
     * Client Credentials Grant
     *
     *    The client can request an access token using only its client
     *    credentials (or other supported means of authentication) when the
     *    client is requesting access to the protected resources under its
     *    control, or those of another resource owner that have been previously
     *    arranged with the authorization server.
     *
     * https://datatracker.ietf.org/doc/html/rfc6749#section-4.4
     */
    private UserProfile authenticateClientCredentials(ClientCredentials creds) throws Exception {

        // Encode username and password in Base64 for Basic Auth
        String auth = creds.getClientId() + ":" + creds.getClientSecret();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        var grantType = creds.getFlowType().getGrantType();
        var content = Request.post(config.getTokenPath())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Authorization", "Basic " + encodedAuth)
                .bodyForm(new BasicNameValuePair("grant_type", grantType))
                .execute().returnContent().asString();

        var json = JsonParser.parseString(content).getAsJsonObject();
        return UserProfile.fromJson(config, json);
    }

    private UserProfile authenticateTokenCredentials(TokenCredentials creds) throws Exception {

        var auxObj = new JsonObject();
        auxObj.add("access_token", new JsonPrimitive(creds.getToken()));
        var userProfile = UserProfile.fromJson(config, auxObj);

        if (userProfile.expired()) {

            // Encode username and password in Base64 for Basic Auth
            String auth = config.getClientId() + ":" + config.getClientSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            var content = Request.post(config.getIntrospectionPath())
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Authorization", "Basic " + encodedAuth)
                    .bodyForm(new BasicNameValuePair("token", creds.getToken()))
                    .execute().returnContent().asString();

            var json = JsonParser.parseString(content).getAsJsonObject();
            userProfile = UserProfile.fromJson(config, json);
        }
        if (userProfile.expired()) {
            throw new OAuthException("user access has expired");
        }

        return userProfile;
    }
}
