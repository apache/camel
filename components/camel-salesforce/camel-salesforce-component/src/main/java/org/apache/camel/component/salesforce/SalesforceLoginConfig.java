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
package org.apache.camel.component.salesforce;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.KeyStoreParameters;

/**
 * Configuration object for Salesforce login properties
 */
public class SalesforceLoginConfig {

    public enum Type {
        USERNAME_PASSWORD, REFRESH_TOKEN, JWT
    }

    public static final String DEFAULT_LOGIN_URL = "https://login.salesforce.com";

    private Type type;
    private String loginUrl;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String userName;
    private String password;
    // allow lazy login into Salesforce
    // note that login issues may not surface until a message needs to be processed
    private boolean lazyLogin;

    private KeyStoreParameters keystore;

    public SalesforceLoginConfig() {
        loginUrl = DEFAULT_LOGIN_URL;
        lazyLogin = false;
    }

    private SalesforceLoginConfig(Type type, String loginUrl, String clientId, String clientSecret, String refreshToken,
        String userName, String password, boolean lazyLogin, KeyStoreParameters keystore) {
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

    public SalesforceLoginConfig(String loginUrl, String clientId, String clientSecret, String userName,
        String password, boolean lazyLogin) {
        this(Type.USERNAME_PASSWORD, loginUrl, clientId, clientSecret, null, userName, password, lazyLogin, null);
    }

    public SalesforceLoginConfig(String loginUrl, String clientId, String clientSecret, String refreshToken,
        boolean lazyLogin) {
        this(Type.REFRESH_TOKEN, loginUrl, clientId, clientSecret, refreshToken, null, null, lazyLogin, null);
    }

    public SalesforceLoginConfig(String loginUrl, String clientId, String userName, KeyStoreParameters keystore, boolean lazyLogin) {
        this(Type.JWT, loginUrl, clientId, null, null, userName, null, lazyLogin, keystore);
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    /**
     * Salesforce login URL, defaults to https://login.salesforce.com
     */
    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
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
        this.type = Type.JWT;
    }

    public KeyStoreParameters getKeystore() {
        return keystore;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Salesforce connected application Consumer token
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        this.type = Type.REFRESH_TOKEN;
    }

    public Type getType() {
        return type;
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
        this.type = Type.USERNAME_PASSWORD;
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
        ObjectHelper.notNull(loginUrl, "loginUrl");
        ObjectHelper.notNull(clientId, "clientId");

        final boolean hasRefreshToken = ObjectHelper.isNotEmpty(refreshToken);

        if (!hasRefreshToken && keystore == null) {
            ObjectHelper.notNull(userName, "userName (username/password authentication)");
            ObjectHelper.notNull(password, "password (username/password authentication)");
            ObjectHelper.notNull(clientSecret, "clientSecret (username/password authentication)");
            type = Type.USERNAME_PASSWORD;
        } else if (hasRefreshToken && keystore == null) {
            ObjectHelper.notNull(refreshToken, "refreshToken (authentication with refresh token)");
            ObjectHelper.notNull(clientSecret, "clientSecret (authentication with refresh token)");
            type = Type.REFRESH_TOKEN;
        } else if (keystore != null) {
            ObjectHelper.notNull(userName, "userName (JWT authentication)");
            ObjectHelper.notNull(keystore, "keystore (JWT authentication)");
            type = Type.JWT;
        } else {
            throw new IllegalArgumentException(
                "You must specify parameters aligned with one of the supported authentication methods:"
                    + " for username and password authentication: userName, password, clientSecret;"
                    + " for refresh token authentication: refreshToken, clientSecret;"
                    + " for JWT: userName, keystore. And for every one of those loginUrl and clientId must be specified also.");
        }
    }

    @Override
    public String toString() {
        return "SalesforceLoginConfig[" + "loginUrl='" + loginUrl + '\'' + ", clientId='" + clientId + '\''
            + ", clientSecret='********'" + ", refreshToken='" + refreshToken + '\'' + ", userName='" + userName + '\''
            + ", password=********'" + password + '\'' + ", keystore=********'" + keystore + '\'' + ", lazyLogin="
            + lazyLogin + ']';
    }
}