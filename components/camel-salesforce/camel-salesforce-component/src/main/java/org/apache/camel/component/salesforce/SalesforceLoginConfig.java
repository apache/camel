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
package org.apache.camel.component.salesforce;

import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.util.ObjectHelper;

/**
 * Configuration object for Salesforce login properties
 */
public class SalesforceLoginConfig {

    public static final String DEFAULT_LOGIN_URL = "https://login.salesforce.com";

    private AuthenticationType type;
    private String instanceUrl;
    private String loginUrl;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String userName;
    private String password;
    // allow lazy login into Salesforce
    // note that login issues may not surface until a message needs to be
    // processed
    private boolean lazyLogin;

    private KeyStoreParameters keystore;
    private String jwtAudience;

    public SalesforceLoginConfig() {
        loginUrl = DEFAULT_LOGIN_URL;
        lazyLogin = false;
    }

    private SalesforceLoginConfig(AuthenticationType type, String loginUrl, String clientId, String clientSecret,
                                  String refreshToken, String userName, String password,
                                  boolean lazyLogin, KeyStoreParameters keystore) {
        this.type = type;
        this.loginUrl = loginUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.userName = userName;
        this.password = password;
        this.lazyLogin = lazyLogin;
        this.keystore = keystore;
    }

    public SalesforceLoginConfig(String loginUrl, String clientId, String clientSecret, String userName, String password,
                                 boolean lazyLogin) {
        this(AuthenticationType.USERNAME_PASSWORD, loginUrl, clientId, clientSecret, null, userName, password, lazyLogin, null);
    }

    public SalesforceLoginConfig(String loginUrl, String clientId, String clientSecret, String refreshToken,
                                 boolean lazyLogin) {
        this(AuthenticationType.REFRESH_TOKEN, loginUrl, clientId, clientSecret, refreshToken, null, null, lazyLogin, null);
    }

    public SalesforceLoginConfig(String loginUrl, String clientId, String userName, KeyStoreParameters keystore,
                                 boolean lazyLogin) {
        this(AuthenticationType.JWT, loginUrl, clientId, null, null, userName, null, lazyLogin, keystore);
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }

    public void setInstanceUrl(final String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    /**
     * Salesforce login URL, defaults to https://login.salesforce.com
     */
    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
        if (loginUrl != null) {
            // strip trailing slash
            this.loginUrl = loginUrl.endsWith("/") ? loginUrl.substring(0, loginUrl.length() - 1) : loginUrl;
        }
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * Salesforce connected application Consumer Key
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Salesforce connected application Consumer Secret
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * Keystore parameters for keystore containing certificate and private key needed for OAuth 2.0 JWT Bearer Token
     * Flow.
     */
    public void setKeystore(final KeyStoreParameters keystore) {
        this.keystore = keystore;
    }

    public KeyStoreParameters getKeystore() {
        return keystore;
    }

    /**
     * If not null, used as Audience (aud) value for OAuth JWT flow
     */
    public void setJwtAudience(String jwtAudience) {
        this.jwtAudience = jwtAudience;
    }

    public String getJwtAudience() {
        return jwtAudience;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Salesforce connected application Consumer token
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public AuthenticationType getType() {
        if (type != null) {
            // use the user provided type
            return type;
        }

        final boolean hasPassword = ObjectHelper.isNotEmpty(password);
        final boolean hasRefreshToken = ObjectHelper.isNotEmpty(refreshToken);
        final boolean hasKeystore = keystore != null && ObjectHelper.isNotEmpty(keystore.getResource());
        final boolean hasClientCredentials = ObjectHelper.isNotEmpty(clientId) && ObjectHelper.isNotEmpty(clientSecret);

        if (hasPassword && !hasRefreshToken && !hasKeystore) {
            return AuthenticationType.USERNAME_PASSWORD;
        }

        if (!hasPassword && hasRefreshToken && !hasKeystore) {
            return AuthenticationType.REFRESH_TOKEN;
        }

        if (!hasPassword && !hasRefreshToken && hasKeystore) {
            return AuthenticationType.JWT;
        }

        if (!hasPassword && !hasRefreshToken && !hasKeystore && hasClientCredentials) {
            return AuthenticationType.CLIENT_CREDENTIALS;
        }

        if (hasPassword && hasRefreshToken || hasPassword && hasKeystore || hasRefreshToken && hasKeystore) {
            throw new IllegalArgumentException(
                    "The provided authentication configuration can be used in multiple ways"
                                               + " for instance both with username/password and refresh_token. Either remove some of the configuration"
                                               + " options, so that authentication method can be auto-determined or explicitly set the authentication"
                                               + " type.");
        }

        throw new IllegalArgumentException(
                "You must specify parameters aligned with one of the supported authentication methods:"
                                           + " for username and password authentication: userName, password, clientSecret;"
                                           + " for refresh token authentication: refreshToken (clientSecret optional);"
                                           + " for JWT: userName, keystore. And for every one of those loginUrl and clientId must be specified also.");
    }

    public void setType(AuthenticationType type) {
        this.type = type;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Salesforce account user name
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Salesforce account password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isLazyLogin() {
        return lazyLogin;
    }

    /**
     * Flag to enable/disable lazy OAuth, default is false. When enabled, OAuth token retrieval or generation is not
     * done until the first API call
     */
    public void setLazyLogin(boolean lazyLogin) {
        this.lazyLogin = lazyLogin;
    }

    public void validate() {
        if (lazyLogin) {
            return;
        }
        ObjectHelper.notNull(loginUrl, "loginUrl");
        ObjectHelper.notNull(clientId, "clientId");

        final AuthenticationType type = getType();

        switch (type) {
            case USERNAME_PASSWORD:
                ObjectHelper.notNull(userName, "userName (username/password authentication)");
                ObjectHelper.notNull(password, "password (username/password authentication)");
                ObjectHelper.notNull(clientSecret, "clientSecret (username/password authentication)");
                break;
            case REFRESH_TOKEN:
                ObjectHelper.notNull(refreshToken, "refreshToken (authentication with refresh token)");
                // clientSecret can be optional for refresh token flow
                break;
            case JWT:
                ObjectHelper.notNull(userName, "userName (JWT authentication)");
                ObjectHelper.notNull(keystore, "keystore (JWT authentication)");
                break;
            case CLIENT_CREDENTIALS:
                ObjectHelper.notNull(clientSecret, "clientSecret (Client Credentials authentication)");
                break;
            default:
                throw new IllegalArgumentException("Unknown authentication type: " + type);
        }
    }

    @Override
    public String toString() {
        return "SalesforceLoginConfig[" + "instanceUrl= '" + instanceUrl + "', loginUrl='" + loginUrl + '\'' + ","
               + "clientId='" + clientId + '\'' + ", clientSecret='********'"
               + ", refreshToken='" + refreshToken + '\'' + ", userName='" + userName + '\'' + ", password=********'"
               + ", keystore=********', audience='" + jwtAudience + '\'' + ","
               + ", lazyLogin=" + lazyLogin + ']';
    }
}
