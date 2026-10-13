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

import com.google.gson.JsonObject;
import org.apache.camel.CamelContext;

/**
 * An identity provider stand-in: it knows the access tokens of alice and bob, and one authorization code that logs bob
 * in. Everything else is rejected.
 */
class StubOAuth extends OAuth {

    static final String ALICE_TOKEN = "alice-token";
    static final String BOB_TOKEN = "bob-token";
    static final String BOB_CODE = "bob-code";

    @Override
    public void discoverOAuthConfig(CamelContext ctx) {
        // nothing to discover
    }

    @Override
    public UserProfile authenticate(Credentials creds) {
        String token;
        if (creds instanceof TokenCredentials tokenCreds) {
            token = tokenCreds.getToken();
        } else if (creds instanceof UserCredentials userCreds) {
            token = userCreds.getUserProfile().accessToken().orElseThrow();
        } else if (creds instanceof AuthCodeCredentials codeCreds && BOB_CODE.equals(codeCreds.getCode())) {
            token = BOB_TOKEN;
        } else {
            throw new OAuthException("Unsupported credentials");
        }
        String user;
        if (ALICE_TOKEN.equals(token)) {
            user = "alice";
        } else if (BOB_TOKEN.equals(token)) {
            user = "bob";
        } else {
            throw new OAuthException("Unknown token");
        }
        var principal = new JsonObject();
        principal.addProperty("access_token", token);
        principal.addProperty("username", user);
        var attributes = new JsonObject();
        attributes.addProperty("exp", System.currentTimeMillis() / 1000L + 3600);
        return new UserProfile(principal, attributes);
    }

    @Override
    public String buildLogoutRequestUrl(OAuthLogoutParams params) {
        return "https://idp.example/logout";
    }

    @Override
    public String buildCodeFlowAuthRequestUrl(OAuthCodeFlowParams params) {
        return "https://idp.example/auth?state=" + params.getState();
    }
}
