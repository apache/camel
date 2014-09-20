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
package org.apache.camel.component.linkedin;

import java.util.Arrays;
import java.util.Map;

import org.apache.camel.component.linkedin.api.OAuthScope;
import org.apache.camel.component.linkedin.api.OAuthSecureStorage;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Component configuration for LinkedIn component.
 */
@UriParams
public class LinkedInConfiguration {

    private String userName;
    private String userPassword;

    private OAuthSecureStorage secureStorage;

    private String clientId;
    private String clientSecret;

    private OAuthScope[] scopes;
    private String redirectUri;


    private Map<String, Object> httpParams;

    private boolean lazyAuth = true;

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

    public OAuthSecureStorage getSecureStorage() {
        return secureStorage;
    }

    public void setSecureStorage(OAuthSecureStorage secureStorage) {
        this.secureStorage = secureStorage;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
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

    public Map<String, Object> getHttpParams() {
        return httpParams;
    }

    public void setHttpParams(Map<String, Object> httpParams) {
        this.httpParams = httpParams;
    }

    public boolean isLazyAuth() {
        return lazyAuth;
    }

    public void setLazyAuth(boolean lazyAuth) {
        this.lazyAuth = lazyAuth;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LinkedInConfiguration) {
            final LinkedInConfiguration other = (LinkedInConfiguration) obj;
            return (userName == null ? other.userName == null : userName.equals(other.userName))
                && (userPassword == null ? other.userPassword == null : userPassword.equals(other.userPassword))
                && secureStorage == other.secureStorage
                && (clientId == null ? other.clientId == null : clientId.equals(other.clientId))
                && (clientSecret == null ? other.clientSecret == null : clientSecret.equals(other.clientSecret))
                && (redirectUri == null ? other.redirectUri == null : redirectUri.equals(other.redirectUri))
                && Arrays.equals(scopes, other.scopes)
                && (httpParams == null ? other.httpParams == null : httpParams.equals(other.httpParams))
                && (lazyAuth == other.lazyAuth);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(userName).append(userPassword).append(secureStorage)
            .append(clientId).append(clientSecret)
            .append(redirectUri).append(scopes).append(httpParams).append(lazyAuth).toHashCode();
    }

    public void validate() throws IllegalArgumentException {
        ObjectHelper.notEmpty(userName, "userName");
        if (ObjectHelper.isEmpty(userPassword) && secureStorage == null) {
            throw new IllegalArgumentException("Property userPassword or secureStorage is required");
        }
        ObjectHelper.notEmpty(clientId, "clientId");
        ObjectHelper.notEmpty(clientSecret, "clientSecret");
        ObjectHelper.notEmpty(redirectUri, "redirectUri");
    }
}
