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
package org.apache.camel.component.linkedin.api;

import java.util.Arrays;

/**
 * Parameters for OAuth 2.0 flow used by {@link LinkedInOAuthRequestFilter}.
*/
public class OAuthParams {

    private String userName;
    private String userPassword;

    private OAuthSecureStorage secureStorage;

    private String clientId;
    private String clientSecret;

    private OAuthScope[] scopes;
    private String redirectUri;

    public OAuthParams() {
    }

    public OAuthParams(String userName, String userPassword, OAuthSecureStorage secureStorage,
                       String clientId, String clientSecret,
                       String redirectUri, OAuthScope... scopes) {
        this.userName = userName;
        this.userPassword = userPassword;
        this.secureStorage = secureStorage;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = scopes != null ? Arrays.copyOf(scopes, scopes.length) : null;
        this.redirectUri = redirectUri;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public OAuthScope[] getScopes() {
        return scopes;
    }

    public void setScopes(OAuthScope[] scopes) {
        this.scopes = scopes;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public OAuthSecureStorage getSecureStorage() {
        return secureStorage;
    }

    public void setSecureStorage(OAuthSecureStorage secureStorage) {
        this.secureStorage = secureStorage;
    }
}
