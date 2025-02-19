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

public class OAuthConfig {

    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private String authorizationPath;
    private String tokenPath;
    private String revocationPath;
    private String logoutPath;
    private String userInfoPath;
    private String introspectionPath;

    public String getBaseUrl() {
        return baseUrl;
    }

    public OAuthConfig setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public OAuthConfig setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public OAuthConfig setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public String getAuthorizationPath() {
        return authorizationPath;
    }

    public OAuthConfig setAuthorizationPath(String authorizationPath) {
        this.authorizationPath = authorizationPath;
        return this;
    }

    public String getTokenPath() {
        return tokenPath;
    }

    public OAuthConfig setTokenPath(String tokenPath) {
        this.tokenPath = tokenPath;
        return this;
    }

    public String getRevocationPath() {
        return revocationPath;
    }

    public OAuthConfig setRevocationPath(String revocationPath) {
        this.revocationPath = revocationPath;
        return this;
    }

    public String getLogoutPath() {
        return logoutPath;
    }

    public OAuthConfig setLogoutPath(String logoutPath) {
        this.logoutPath = logoutPath;
        return this;
    }

    public String getUserInfoPath() {
        return userInfoPath;
    }

    public OAuthConfig setUserInfoPath(String userInfoPath) {
        this.userInfoPath = userInfoPath;
        return this;
    }

    public String getIntrospectionPath() {
        return introspectionPath;
    }

    public OAuthConfig setIntrospectionPath(String introspectionPath) {
        this.introspectionPath = introspectionPath;
        return this;
    }
}
