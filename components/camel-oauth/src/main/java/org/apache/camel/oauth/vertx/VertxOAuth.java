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
package org.apache.camel.oauth.vertx;

import java.net.URL;

import com.nimbusds.jose.jwk.JWKSet;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2AuthorizationURL;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import org.apache.camel.CamelContext;
import org.apache.camel.oauth.OAuth;
import org.apache.camel.oauth.OAuthCodeFlowParams;
import org.apache.camel.oauth.OAuthConfig;
import org.apache.camel.oauth.OAuthException;
import org.apache.camel.oauth.OAuthLogoutParams;
import org.apache.camel.oauth.UserCredentials;
import org.apache.camel.oauth.UserProfile;

import static org.apache.camel.oauth.OAuthProperties.getRequiredProperty;

public class VertxOAuth extends OAuth {

    private final Vertx vertx;
    private OAuth2Auth oauth2;

    public VertxOAuth(Vertx vertx) {
        this.vertx = vertx;
    }

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

            OAuth2Options opts = new OAuth2Options()
                    .setSite(baseUrl)
                    .setClientId(config.getClientId())
                    .setClientSecret(config.getClientSecret());

            try {
                oauth2 = OpenIDConnectAuth.discover(vertx, opts)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get();
                config.setAuthorizationPath(opts.getAuthorizationPath())
                        .setTokenPath(opts.getTokenPath())
                        .setRevocationPath(opts.getRevocationPath())
                        .setLogoutPath(opts.getLogoutPath())
                        .setUserInfoPath(opts.getUserInfoPath())
                        .setIntrospectionPath(opts.getIntrospectionPath())
                        .setJwksPath(opts.getJwkPath());
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
        return oauth2.authorizeURL(new OAuth2AuthorizationURL()
                .setRedirectUri(params.getRedirectUri())
                .setScopes(params.getScopes()));
    }

    @Override
    public UserProfile authenticate(org.apache.camel.oauth.Credentials creds) throws OAuthException {

        Credentials vtxCreds;
        if (creds instanceof org.apache.camel.oauth.UserCredentials) {

            var userProfile = ((UserCredentials) creds).getUserProfile();
            log.info("Authenticate userProfile: {}", userProfile.subject());
            var scope = (String) userProfile.principal().get("scope");
            vtxCreds = new TokenCredentials()
                    .setToken(userProfile.accessToken().orElseThrow())
                    .addScope(scope);

        } else if (creds instanceof org.apache.camel.oauth.AuthCodeCredentials params) {

            vtxCreds = new Oauth2Credentials()
                    .setFlow(OAuth2FlowType.AUTH_CODE)
                    .setRedirectUri(params.getRedirectUri())
                    .setCode(params.getCode());

        } else if (creds instanceof org.apache.camel.oauth.TokenCredentials params) {

            vtxCreds = new TokenCredentials()
                    .setToken(params.getToken());

        } else if (creds instanceof org.apache.camel.oauth.ClientCredentials params) {

            vtxCreds = new Oauth2Credentials()
                    .setFlow(OAuth2FlowType.CLIENT)
                    .setUsername(params.getClientId())
                    .setPassword(params.getClientSecret());

        } else {
            throw new OAuthException("Unsupported creds: " + creds.getClass().getName());
        }

        try {
            User vtxUser = oauth2.authenticate(vtxCreds)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get();
            return new VertxUserProfile(vtxUser);
        } catch (Exception ex) {
            throw new OAuthException("Cannot authenticate user", ex);
        }
    }

    @Override
    public String buildLogoutRequestUrl(OAuthLogoutParams params) {

        var user = ((VertxUserProfile) params.getUserProfile()).getVertxUser();
        String endSessionURL = oauth2.endSessionURL(user);

        var postLogoutUrl = params.getRedirectUri();
        if (postLogoutUrl != null) {
            endSessionURL += "&post_logout_redirect_uri=" + postLogoutUrl;
        }

        return endSessionURL;
    }
}
